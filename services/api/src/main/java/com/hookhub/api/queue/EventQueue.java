package com.hookhub.api.queue;

import com.hookhub.api.model.Event;

/**
 * Interface for event queue operations.
 * Provides methods to enqueue events for processing and dequeue them for delivery.
 * 
 * This interface abstracts the queue implementation, allowing for different
 * queue backends (in-memory, Redis, RabbitMQ, etc.) to be used interchangeably.
 */
public interface EventQueue {
    
    /**
     * Adds an event to the queue for processing.
     * 
     * @param event The event to be enqueued
     * @return true if the event was successfully enqueued, false otherwise
     */
    boolean enqueue(Event event);
    
    /**
     * Removes and returns the next event from the queue.
     * Returns null if the queue is empty.
     * 
     * @return The next event in the queue, or null if the queue is empty
     */
    Event dequeue();
    
    /**
     * Checks if the queue is empty.
     * 
     * @return true if the queue contains no events, false otherwise
     */
    boolean isEmpty();
    
    /**
     * Returns the current size of the queue.
     * 
     * @return The number of events currently in the queue
     */
    int size();
}

