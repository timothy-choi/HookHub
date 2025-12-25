package com.hookhub.api.queue;

import com.hookhub.api.model.Event;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Thread-safe in-memory implementation of EventQueue using ConcurrentLinkedQueue.
 * 
 * This implementation provides a fast, non-blocking queue suitable for
 * single-instance deployments. For distributed systems, consider using
 * a distributed queue implementation (Redis, RabbitMQ, etc.).
 * 
 * Thread-safety is guaranteed by ConcurrentLinkedQueue, which uses
 * lock-free algorithms for concurrent access.
 */
@Component
public class InMemoryEventQueue implements EventQueue {
    
    /**
     * Thread-safe queue implementation using ConcurrentLinkedQueue.
     * This provides lock-free, thread-safe operations for concurrent
     * enqueue and dequeue operations.
     */
    private final ConcurrentLinkedQueue<Event> queue;
    
    /**
     * Constructor initializes the concurrent queue.
     */
    public InMemoryEventQueue() {
        this.queue = new ConcurrentLinkedQueue<>();
    }
    
    /**
     * Enqueues an event to the queue.
     * Thread-safe operation that can be called concurrently from multiple threads.
     * 
     * @param event The event to be enqueued
     * @return true if the event was successfully added (always true for ConcurrentLinkedQueue)
     */
    @Override
    public boolean enqueue(Event event) {
        if (event == null) {
            throw new IllegalArgumentException("Event cannot be null");
        }
        return queue.offer(event);
    }
    
    /**
     * Dequeues the next event from the queue.
     * Returns null if the queue is empty. Thread-safe operation.
     * 
     * @return The next event in the queue, or null if the queue is empty
     */
    @Override
    public Event dequeue() {
        return queue.poll();
    }
    
    /**
     * Checks if the queue is empty.
     * Thread-safe operation.
     * 
     * @return true if the queue contains no events, false otherwise
     */
    @Override
    public boolean isEmpty() {
        return queue.isEmpty();
    }
    
    /**
     * Returns the current size of the queue.
     * Note: The size may change immediately after this call due to concurrent operations.
     * 
     * @return The number of events currently in the queue
     */
    @Override
    public int size() {
        return queue.size();
    }
}

