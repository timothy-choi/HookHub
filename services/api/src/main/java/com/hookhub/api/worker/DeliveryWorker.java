package com.hookhub.api.worker;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.hookhub.api.circuitbreaker.CircuitBreaker;
import com.hookhub.api.circuitbreaker.CircuitBreakerState;
import com.hookhub.api.error.DiagnosticsService;
import com.hookhub.api.error.ErrorClassifier;
import com.hookhub.api.error.ErrorDecision;
import com.hookhub.api.model.ErrorClassification;
import com.hookhub.api.model.Event;
import com.hookhub.api.model.Webhook;
import com.hookhub.api.queue.EventQueue;
import com.hookhub.api.repository.ErrorClassificationRepository;
import com.hookhub.api.repository.EventRepository;
import com.hookhub.api.repository.WebhookRepository;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;

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
    private final ErrorClassificationRepository errorClassificationRepository;
    private final WebhookDeliveryClient deliveryClient;
    private final RetryPolicy retryPolicy;
    private final ErrorClassifier errorClassifier;
    private final CircuitBreaker circuitBreaker;
    private final DiagnosticsService diagnosticsService;
    
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
                         ErrorClassificationRepository errorClassificationRepository,
                         WebhookDeliveryClient deliveryClient,
                         RetryPolicy retryPolicy,
                         ErrorClassifier errorClassifier,
                         CircuitBreaker circuitBreaker,
                         DiagnosticsService diagnosticsService) {
        this.eventQueue = eventQueue;
        this.webhookRepository = webhookRepository;
        this.eventRepository = eventRepository;
        this.errorClassificationRepository = errorClassificationRepository;
        this.deliveryClient = deliveryClient;
        this.retryPolicy = retryPolicy;
        this.errorClassifier = errorClassifier;
        this.circuitBreaker = circuitBreaker;
        this.diagnosticsService = diagnosticsService;
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
     * Phase 4: Enhanced with error classification, circuit breaker, and smart retry.
     * 
     * @param event The event to process
     */
    @Transactional
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
            
            // Check if webhook is disabled or paused
            if (Boolean.TRUE.equals(webhook.getIsDisabled())) {
                logger.warn("Webhook is disabled: id={}, skipping event", webhookId);
                event.setStatus(Event.EventStatus.PAUSED);
                eventRepository.save(event);
                return;
            }
            
            if (webhook.getPausedUntil() != null && webhook.getPausedUntil().isAfter(LocalDateTime.now())) {
                logger.warn("Webhook is paused until: {}, skipping event", webhook.getPausedUntil());
                event.setStatus(Event.EventStatus.PAUSED);
                eventRepository.save(event);
                return;
            }
            
            // Check circuit breaker state
            CircuitBreaker.WebhookCircuitState circuitState = getOrCreateCircuitState(webhook);
            if (!circuitBreaker.allowRequest(circuitState)) {
                logger.warn("Circuit breaker is {} for webhook: id={}, blocking request", 
                        circuitState.getState(), webhookId);
                // Update webhook state in database
                updateWebhookCircuitState(webhook, circuitState);
                event.setStatus(Event.EventStatus.RETRY_PENDING);
                eventRepository.save(event);
                // Re-enqueue after cooldown
                scheduleRetryAfterCooldown(event, webhook);
                return;
            }
            
            // Update event status to PROCESSING
            event.setStatus(Event.EventStatus.PROCESSING);
            eventRepository.save(event);
            
            // Deliver webhook
            WebhookDeliveryClient.DeliveryResult result = deliveryClient.deliver(webhook, event.getPayload());
            
            if (result.isSuccess()) {
                // Delivery successful
                logger.info("Event delivered successfully: id={}, statusCode={}", eventId, result.getStatusCode());
                
                // Record success in circuit breaker
                circuitBreaker.recordSuccess(circuitState);
                updateWebhookCircuitState(webhook, circuitState);
                
                // Update webhook health metrics
                webhook.setTotalSuccesses(webhook.getTotalSuccesses() + 1);
                webhookRepository.save(webhook);
                
                markEventAsSuccess(event);
                
            } else {
                // Delivery failed - classify the error
                ErrorDecision decision = errorClassifier.classify(result);
                String explanation = diagnosticsService.generateExplanation(
                        result.getStatusCode(), 
                        result.getErrorMessage(), 
                        decision
                );
                
                // Record error classification
                recordErrorClassification(event, webhook, result, decision, explanation);
                
                // Record failure in circuit breaker
                circuitBreaker.recordFailure(circuitState);
                updateWebhookCircuitState(webhook, circuitState);
                
                // Update webhook health metrics
                webhook.setTotalFailures(webhook.getTotalFailures() + 1);
                webhook.setLastFailureTime(LocalDateTime.now());
                webhookRepository.save(webhook);
                
                // Apply decision
                applyErrorDecision(event, webhook, result, decision, explanation, circuitState);
            }
            
        } catch (Exception e) {
            logger.error("Error processing event: id={}", eventId, e);
            // Mark as failure on unexpected error
            markEventAsFailure(event, "Unexpected error: " + e.getMessage());
        }
    }
    
    /**
     * Gets or creates circuit breaker state for a webhook.
     * 
     * @param webhook The webhook
     * @return Circuit breaker state
     */
    private CircuitBreaker.WebhookCircuitState getOrCreateCircuitState(Webhook webhook) {
        CircuitBreaker.WebhookCircuitState state = new CircuitBreaker.WebhookCircuitState();
        
        // Initialize from webhook entity
        if (webhook.getCircuitBreakerState() != null) {
            state.setState(webhook.getCircuitBreakerState());
        } else {
            state.setState(CircuitBreakerState.CLOSED);
        }
        
        state.setConsecutiveFailures(webhook.getConsecutiveFailures() != null ? webhook.getConsecutiveFailures() : 0);
        state.setCircuitOpenedAt(webhook.getCircuitOpenedAt());
        state.setLastFailureTime(webhook.getLastFailureTime());
        
        return state;
    }
    
    /**
     * Updates webhook entity with circuit breaker state.
     * 
     * @param webhook The webhook to update
     * @param circuitState The circuit breaker state
     */
    private void updateWebhookCircuitState(Webhook webhook, CircuitBreaker.WebhookCircuitState circuitState) {
        webhook.setCircuitBreakerState(circuitState.getState());
        webhook.setConsecutiveFailures(circuitState.getConsecutiveFailures());
        webhook.setCircuitOpenedAt(circuitState.getCircuitOpenedAt());
        webhook.setLastFailureTime(circuitState.getLastFailureTime());
        webhookRepository.save(webhook);
    }
    
    /**
     * Applies the error decision from ErrorClassifier.
     * 
     * @param event The event
     * @param webhook The webhook
     * @param result Delivery result
     * @param decision Error decision
     * @param explanation Human-readable explanation
     * @param circuitState Circuit breaker state
     */
    private void applyErrorDecision(Event event, Webhook webhook, 
                                   WebhookDeliveryClient.DeliveryResult result,
                                   ErrorDecision decision, String explanation,
                                   CircuitBreaker.WebhookCircuitState circuitState) {
        switch (decision) {
            case RETRY:
                if (retryPolicy.shouldRetry(event.getRetryCount())) {
                    logger.info("Error decision: RETRY - scheduling retry for event: id={}", event.getId());
                    scheduleRetry(event, result);
                } else {
                    logger.warn("Error decision: RETRY but max retries reached - marking as FAILURE");
                    markEventAsFailure(event, explanation);
                }
                break;
                
            case FAIL_PERMANENT:
                logger.warn("Error decision: FAIL_PERMANENT - marking event as permanently failed");
                markEventAsFailure(event, explanation);
                break;
                
            case PAUSE_WEBHOOK:
                logger.warn("Error decision: PAUSE_WEBHOOK - pausing webhook temporarily");
                pauseWebhook(webhook, explanation);
                event.setStatus(Event.EventStatus.PAUSED);
                eventRepository.save(event);
                break;
                
            case ESCALATE:
                logger.error("Error decision: ESCALATE - requires manual intervention");
                // TODO: Send alert/notification
                markEventAsFailure(event, explanation + " (Escalated for manual review)");
                break;
        }
    }
    
    /**
     * Records error classification in database for audit and diagnostics.
     * 
     * @param event The event
     * @param webhook The webhook
     * @param result Delivery result
     * @param decision Error decision
     * @param explanation Human-readable explanation
     */
    private void recordErrorClassification(Event event, Webhook webhook,
                                          WebhookDeliveryClient.DeliveryResult result,
                                          ErrorDecision decision, String explanation) {
        String errorType = determineErrorType(result.getStatusCode());
        
        ErrorClassification classification = new ErrorClassification(
                event.getId(),
                webhook.getId(),
                result.getStatusCode(),
                result.getErrorMessage(),
                decision,
                explanation,
                errorType,
                result.getRetryAfterSeconds()
        );
        
        errorClassificationRepository.save(classification);
        logger.debug("Recorded error classification: eventId={}, decision={}, errorType={}", 
                event.getId(), decision, errorType);
    }
    
    /**
     * Determines error type from HTTP status code.
     * 
     * @param statusCode HTTP status code
     * @return Error type string
     */
    private String determineErrorType(int statusCode) {
        if (statusCode == 429) {
            return "RATE_LIMIT";
        }
        if (statusCode >= 500) {
            return "SERVER_ERROR";
        }
        if (statusCode == 401 || statusCode == 403) {
            return "AUTH_ERROR";
        }
        if (statusCode >= 400 && statusCode < 500) {
            return "CLIENT_ERROR";
        }
        if (statusCode == 0 || statusCode < 0) {
            return "NETWORK_ERROR";
        }
        return "UNKNOWN_ERROR";
    }
    
    /**
     * Pauses a webhook temporarily.
     * 
     * @param webhook The webhook to pause
     * @param reason Reason for pausing
     */
    private void pauseWebhook(Webhook webhook, String reason) {
        // Pause for 1 hour by default
        webhook.setPausedUntil(LocalDateTime.now().plusHours(1));
        webhookRepository.save(webhook);
        logger.warn("Webhook paused until: {}, reason: {}", webhook.getPausedUntil(), reason);
    }
    
    /**
     * Schedules retry after circuit breaker cooldown period.
     * 
     * @param event The event
     * @param webhook The webhook
     */
    private void scheduleRetryAfterCooldown(Event event, Webhook webhook) {
        if (webhook.getCircuitOpenedAt() != null) {
            // Calculate when cooldown ends (60 seconds default)
            LocalDateTime cooldownEnd = webhook.getCircuitOpenedAt().plusSeconds(60);
            long delayMs = java.time.Duration.between(LocalDateTime.now(), cooldownEnd).toMillis();
            
            if (delayMs > 0) {
                executorService.submit(() -> {
                    try {
                        Thread.sleep(delayMs);
                        if (running) {
                            eventQueue.enqueue(event);
                            logger.info("Event re-enqueued after circuit breaker cooldown: id={}", event.getId());
                        }
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                });
            }
        }
    }
    
    /**
     * Marks an event as successfully delivered.
     */
    @Transactional
    private void markEventAsSuccess(Event event) {
        event.setStatus(Event.EventStatus.SUCCESS);
        eventRepository.save(event);
        logger.info("Event marked as SUCCESS: id={}", event.getId());
    }
    
    /**
     * Marks an event as failed (non-retryable or max retries reached).
     */
    @Transactional
    private void markEventAsFailure(Event event, String errorMessage) {
        event.setStatus(Event.EventStatus.FAILURE);
        eventRepository.save(event);
        logger.error("Event marked as FAILURE: id={}, error={}", event.getId(), errorMessage);
        // TODO: Send alert/notification for failed events
    }
    
    /**
     * Schedules a retry for a failed event using exponential backoff.
     * Enhanced to respect Retry-After headers from rate limiting.
     */
    @Transactional
    private void scheduleRetry(Event event, WebhookDeliveryClient.DeliveryResult result) {
        int currentRetryCount = event.getRetryCount();
        int newRetryCount = currentRetryCount + 1;
        
        // Calculate delay before retry - respect Retry-After header if present
        long delayMs = retryPolicy.calculateDelay(currentRetryCount, result.getRetryAfterSeconds());
        
        logger.info("Scheduling retry for event: id={}, retryCount={}, delay={}ms, retryAfter={}", 
                event.getId(), newRetryCount, delayMs, result.getRetryAfterSeconds());
        
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

