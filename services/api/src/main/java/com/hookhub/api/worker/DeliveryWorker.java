package com.hookhub.api.worker;

import com.hookhub.api.model.Event;
import com.hookhub.api.model.Webhook;
import com.hookhub.api.queue.EventQueue;
import com.hookhub.api.repository.EventRepository;
import com.hookhub.api.repository.WebhookRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;

import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * DeliveryWorker continuously dequeues events from the queue and delivers them to webhook URLs.
 * 
 * This is a multi-threaded background worker that:
 * - Polls the EventQueue for new events
 * - Fetches webhook details from the database
 * - Sends HTTP POST requests to webhook URLs
 * - Implements retry logic with exponential backoff
 * - Updates event status in the database
 * 
 * The worker uses a thread pool to process multiple events concurrently.
 */
@Component
public class DeliveryWorker {
    
    private static final Logger logger = LoggerFactory.getLogger(DeliveryWorker.class);
    
    private final EventQueue eventQueue;
    private final WebhookRepository webhookRepository;
    private final EventRepository eventRepository;
    private final WebhookDeliveryClient deliveryClient;
    private final RetryPolicy retryPolicy;
    
    private volatile boolean running = false;
    private ExecutorService executorService;
    private Thread workerThread;
    
    /**
     * Number of worker threads for concurrent processing
     */
    private static final int WORKER_THREADS = 5;
    
    /**
     * Polling interval in milliseconds when queue is empty
     */
    private static final long POLL_INTERVAL_MS = 100;
    
    public DeliveryWorker(EventQueue eventQueue,
                         WebhookRepository webhookRepository,
                         EventRepository eventRepository,
                         WebhookDeliveryClient deliveryClient,
                         RetryPolicy retryPolicy) {
        this.eventQueue = eventQueue;
        this.webhookRepository = webhookRepository;
        this.eventRepository = eventRepository;
        this.deliveryClient = deliveryClient;
        this.retryPolicy = retryPolicy;
    }
    
    /**
     * Starts the delivery worker after Spring context initialization.
     */
    @PostConstruct
    public void start() {
        if (running) {
            logger.warn("DeliveryWorker is already running");
            return;
        }
        
        running = true;
        executorService = Executors.newFixedThreadPool(WORKER_THREADS);
        workerThread = new Thread(this::workerLoop, "DeliveryWorker-Thread");
        workerThread.setDaemon(true);
        workerThread.start();
        logger.info("DeliveryWorker started with {} worker threads", WORKER_THREADS);
    }
    
    /**
     * Stops the delivery worker gracefully before application shutdown.
     */
    @PreDestroy
    public void stop() {
        logger.info("Stopping DeliveryWorker...");
        running = false;
        
        if (workerThread != null) {
            try {
                workerThread.interrupt();
                workerThread.join(5000);
            } catch (InterruptedException e) {
                logger.warn("Interrupted while waiting for DeliveryWorker to stop", e);
                Thread.currentThread().interrupt();
            }
        }
        
        if (executorService != null) {
            shutdownExecutorService();
        }
        
        logger.info("DeliveryWorker stopped");
    }
    
    /**
     * Main worker loop that continuously polls the queue for events.
     */
    private void workerLoop() {
        logger.info("DeliveryWorker thread started, polling queue every {}ms", POLL_INTERVAL_MS);
        
        while (running) {
            try {
                if (!eventQueue.isEmpty()) {
                    Event event = eventQueue.dequeue();
                    if (event != null) {
                        // Submit event processing to thread pool
                        executorService.submit(() -> processEvent(event));
                    }
                } else {
                    // Queue is empty, sleep to avoid busy-waiting
                    Thread.sleep(POLL_INTERVAL_MS);
                }
            } catch (InterruptedException e) {
                logger.info("DeliveryWorker thread interrupted, shutting down");
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                logger.error("Error in DeliveryWorker loop", e);
            }
        }
        
        logger.info("DeliveryWorker thread stopped");
    }
    
    /**
     * Processes a single event: fetches webhook, delivers payload, handles retries.
     * 
     * @param event The event to process
     */
    private void processEvent(Event event) {
        Long eventId = event.getId();
        Long webhookId = event.getWebhookId();
        
        logger.info("Processing event: id={}, webhookId={}, retryCount={}", 
                eventId, webhookId, event.getRetryCount());
        
        try {
            // Fetch webhook from database
            Optional<Webhook> webhookOpt = webhookRepository.findById(webhookId);
            if (webhookOpt.isEmpty()) {
                logger.error("Webhook not found: id={}, marking event as FAILURE", webhookId);
                markEventAsFailure(event, "Webhook not found");
                return;
            }
            
            Webhook webhook = webhookOpt.get();
            
            // Update event status to PROCESSING
            event.setStatus(Event.EventStatus.PROCESSING);
            eventRepository.save(event);
            
            // Deliver webhook
            WebhookDeliveryClient.DeliveryResult result = deliveryClient.deliver(webhook, event.getPayload());
            
            if (result.isSuccess()) {
                // Delivery successful
                logger.info("Event delivered successfully: id={}, statusCode={}", eventId, result.getStatusCode());
                markEventAsSuccess(event);
                
            } else if (result.isRetryable() && retryPolicy.shouldRetry(event.getRetryCount())) {
                // Retryable failure - schedule retry
                logger.warn("Event delivery failed (retryable): id={}, retryCount={}, will retry", 
                        eventId, event.getRetryCount());
                scheduleRetry(event, result);
                
            } else {
                // Non-retryable failure or max retries reached
                logger.error("Event delivery failed permanently: id={}, retryCount={}, retryable={}", 
                        eventId, event.getRetryCount(), result.isRetryable());
                markEventAsFailure(event, result.getErrorMessage());
            }
            
        } catch (Exception e) {
            logger.error("Error processing event: id={}", eventId, e);
            // Mark as failure on unexpected error
            markEventAsFailure(event, "Unexpected error: " + e.getMessage());
        }
    }
    
    /**
     * Marks an event as successfully delivered.
     */
    private void markEventAsSuccess(Event event) {
        event.setStatus(Event.EventStatus.SUCCESS);
        eventRepository.save(event);
        logger.info("Event marked as SUCCESS: id={}", event.getId());
    }
    
    /**
     * Marks an event as failed (non-retryable or max retries reached).
     */
    private void markEventAsFailure(Event event, String errorMessage) {
        event.setStatus(Event.EventStatus.FAILURE);
        eventRepository.save(event);
        logger.error("Event marked as FAILURE: id={}, error={}", event.getId(), errorMessage);
        // TODO: Send alert/notification for failed events
    }
    
    /**
     * Schedules a retry for a failed event using exponential backoff.
     */
    private void scheduleRetry(Event event, WebhookDeliveryClient.DeliveryResult result) {
        int currentRetryCount = event.getRetryCount();
        int newRetryCount = currentRetryCount + 1;
        
        // Calculate delay before retry
        long delayMs = retryPolicy.calculateDelay(currentRetryCount);
        
        logger.info("Scheduling retry for event: id={}, retryCount={}, delay={}ms", 
                event.getId(), newRetryCount, delayMs);
        
        // Update event status and retry count
        event.setStatus(Event.EventStatus.RETRY_PENDING);
        event.setRetryCount(newRetryCount);
        eventRepository.save(event);
        
        // Schedule retry after delay
        executorService.submit(() -> {
            try {
                Thread.sleep(delayMs);
                // Re-enqueue the event for retry
                if (running) {
                    eventQueue.enqueue(event);
                    logger.info("Event re-enqueued for retry: id={}, retryCount={}", 
                            event.getId(), event.getRetryCount());
                }
            } catch (InterruptedException e) {
                logger.warn("Retry delay interrupted for event: id={}", event.getId());
                Thread.currentThread().interrupt();
            }
        });
    }
    
    /**
     * Gracefully shuts down the executor service.
     */
    private void shutdownExecutorService() {
        executorService.shutdown();
        try {
            if (!executorService.awaitTermination(10, TimeUnit.SECONDS)) {
                logger.warn("ExecutorService did not terminate gracefully, forcing shutdown");
                executorService.shutdownNow();
                if (!executorService.awaitTermination(5, TimeUnit.SECONDS)) {
                    logger.error("ExecutorService did not terminate");
                }
            }
        } catch (InterruptedException e) {
            logger.warn("Interrupted while shutting down ExecutorService", e);
            executorService.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
    
    /**
     * Checks if the worker is currently running.
     */
    public boolean isRunning() {
        return running;
    }
}

