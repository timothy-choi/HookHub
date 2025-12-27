package com.hookhub.api.circuitbreaker;

/**
 * Represents the state of a circuit breaker for a webhook.
 * 
 * Circuit breaker pattern prevents cascading failures by:
 * - CLOSED: Normal operation, allowing requests through
 * - OPEN: Failing fast, blocking requests after threshold failures
 * - HALF_OPEN: Testing if service recovered, allowing limited requests
 */
public enum CircuitBreakerState {
    
    /**
     * Circuit is closed - normal operation.
     * Requests are allowed through and failures are tracked.
     * If failure threshold is reached, transitions to OPEN.
     */
    CLOSED,
    
    /**
     * Circuit is open - failing fast.
     * Requests are immediately rejected without attempting delivery.
     * After cooldown period, transitions to HALF_OPEN for testing.
     */
    OPEN,
    
    /**
     * Circuit is half-open - testing recovery.
     * Limited requests are allowed to test if the service has recovered.
     * On success, transitions to CLOSED. On failure, transitions back to OPEN.
     */
    HALF_OPEN
}

