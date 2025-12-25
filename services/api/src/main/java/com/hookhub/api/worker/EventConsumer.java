package com.hookhub.api.worker;

import com.hookhub.api.model.Event;
import com.hookhub.api.queue.EventQueue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;

/**
 * EventConsumer continuously dequeues events from the queue and processes them.
 * 
 * This is a background worker that runs in a separate thread, polling the queue
 * for new events. Currently, it prints events to the console. In future phases,
 * this will be integrated with the delivery worker to actually send webhooks.
 * 
 * The consumer runs continuously until the application shuts down.
 */
@Component
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
     * Currently prints the event details. In future phases, this will
     * integrate with the delivery worker to send webhooks.
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
        
        // TODO: In Phase 3, integrate with delivery worker to actually send webhooks
        // For now, we just log the event
        System.out.println("========================================");
        System.out.println("Event Dequeued and Processed:");
        System.out.println("  ID: " + event.getId());
        System.out.println("  Webhook ID: " + event.getWebhookId());
        System.out.println("  Status: " + event.getStatus());
        System.out.println("  Retry Count: " + event.getRetryCount());
        System.out.println("  Payload: " + event.getPayload());
        System.out.println("  Created At: " + event.getCreatedAt());
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

