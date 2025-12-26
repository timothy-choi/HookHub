package com.hookhub.api.worker;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.hookhub.api.model.Event;
import com.hookhub.api.queue.EventQueue;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;

/**
 * EventConsumer continuously dequeues events from the queue and processes them.
 * 
 * NOTE: This component is now deprecated in favor of DeliveryWorker (Phase 3).
 * DeliveryWorker handles actual webhook delivery with retry logic.
 * 
 * This consumer can be disabled by removing @Component annotation if DeliveryWorker
 * is being used exclusively.
 * 
 * The consumer runs continuously until the application shuts down.
 */
// @Component  // Disabled - DeliveryWorker handles event processing in Phase 3
public class EventConsumer {
    
    private static final Logger logger = LoggerFactory.getLogger(EventConsumer.class);
    
    private final EventQueue eventQueue;
    private volatile boolean running = false;
    private Thread consumerThread;
    
    /**
     * Polling interval in milliseconds.
     * The consumer checks the queue every 100ms for new events.
     */
    private static final long POLL_INTERVAL_MS = 100;
    
    public EventConsumer(EventQueue eventQueue) {
        this.eventQueue = eventQueue;
    }
    
    /**
     * Starts the consumer thread after Spring context initialization.
     * This method is called automatically by Spring after dependency injection.
     */
    @PostConstruct
    public void start() {
        if (running) {
            logger.warn("EventConsumer is already running");
            return;
        }
        
        running = true;
        consumerThread = new Thread(this::consumeEvents, "EventConsumer-Thread");
        consumerThread.setDaemon(true); // Allow JVM to exit even if thread is running
        consumerThread.start();
        logger.info("EventConsumer started and ready to process events");
    }
    
    /**
     * Stops the consumer thread gracefully before application shutdown.
     * This method is called automatically by Spring before context destruction.
     */
    @PreDestroy
    public void stop() {
        logger.info("Stopping EventConsumer...");
        running = false;
        
        if (consumerThread != null) {
            try {
                consumerThread.interrupt();
                consumerThread.join(5000); // Wait up to 5 seconds for thread to finish
                logger.info("EventConsumer stopped successfully");
            } catch (InterruptedException e) {
                logger.warn("Interrupted while waiting for EventConsumer to stop", e);
                Thread.currentThread().interrupt();
            }
        }
    }
    
    /**
     * Main consumer loop that continuously polls the queue for events.
     * This method runs in a separate thread and processes events until stopped.
     */
    private void consumeEvents() {
        logger.info("EventConsumer thread started, polling queue every {}ms", POLL_INTERVAL_MS);
        
        while (running) {
            try {
                if (!eventQueue.isEmpty()) {
                    Event event = eventQueue.dequeue();
                    if (event != null) {
                        processEvent(event);
                    }
                } else {
                    // Queue is empty, sleep to avoid busy-waiting
                    Thread.sleep(POLL_INTERVAL_MS);
                }
            } catch (InterruptedException e) {
                logger.info("EventConsumer thread interrupted, shutting down");
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                logger.error("Error processing event from queue", e);
                // Continue processing even if one event fails
            }
        }
        
        logger.info("EventConsumer thread stopped");
    }
    
    /**
     * Processes a dequeued event.
     * 
     * NOTE: This method is deprecated. EventConsumer is disabled in favor of DeliveryWorker
     * (Phase 3), which handles actual webhook delivery with retry logic, status tracking,
     * and database persistence.
     * 
     * This method is kept for reference but is no longer actively used since EventConsumer
     * has its @Component annotation disabled.
     * 
     * @param event The event to process
     */
    private void processEvent(Event event) {
        logger.info("Processing event: id={}, webhookId={}, status={}, retryCount={}, payload={}",
                event.getId(),
                event.getWebhookId(),
                event.getStatus(),
                event.getRetryCount(),
                event.getPayload());
        
        // NOTE: Actual webhook delivery is now handled by DeliveryWorker (Phase 3).
        // DeliveryWorker provides:
        // - HTTP delivery via RestTemplate
        // - Retry logic with exponential backoff
        // - Status tracking (SUCCESS, RETRY_PENDING, FAILURE)
        // - Database persistence of status updates
        // - Multi-threaded concurrent processing
        
        // This console output is kept for debugging purposes only
        System.out.println("========================================");
        System.out.println("Event Dequeued (EventConsumer - Deprecated):");
        System.out.println("  ID: " + event.getId());
        System.out.println("  Webhook ID: " + event.getWebhookId());
        System.out.println("  Status: " + event.getStatus());
        System.out.println("  Retry Count: " + event.getRetryCount());
        System.out.println("  Payload: " + event.getPayload());
        System.out.println("  Created At: " + event.getCreatedAt());
        System.out.println("  NOTE: Use DeliveryWorker for actual webhook delivery");
        System.out.println("========================================");
    }
    
    /**
     * Checks if the consumer is currently running.
     * 
     * @return true if the consumer is running, false otherwise
     */
    public boolean isRunning() {
        return running;
    }
}

