package com.hookhub.api.circuitbreaker;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * CircuitBreaker implements the circuit breaker pattern per webhook.
 * 
 * This prevents retry storms and hammering broken endpoints by:
 * - Tracking consecutive failures
 * - Opening circuit after threshold failures
 * - Blocking requests when circuit is open
 * - Auto-recovering after cooldown period
 * 
 * States:
 * - CLOSED: Normal operation, tracking failures
 * - OPEN: Circuit open, blocking requests
 * - HALF_OPEN: Testing recovery, allowing limited requests
 * 
 * Configuration:
 * - Failure threshold: Number of consecutive failures before opening
 * - Cooldown period: Time to wait before attempting recovery
 * - Half-open test limit: Number of requests to allow in HALF_OPEN state
 */
@Component
public class CircuitBreaker {
    
    private static final Logger logger = LoggerFactory.getLogger(CircuitBreaker.class);
    
    /**
     * Default configuration values
     */
    private static final int DEFAULT_FAILURE_THRESHOLD = 5;      // Open after 5 failures
    private static final int DEFAULT_COOLDOWN_SECONDS = 60;      // Wait 60 seconds before recovery
    private static final int DEFAULT_HALF_OPEN_TEST_LIMIT = 3;    // Allow 3 test requests
    
    private final int failureThreshold;
    private final int cooldownSeconds;
    private final int halfOpenTestLimit;
    
    /**
     * Creates a circuit breaker with default settings.
     */
    public CircuitBreaker() {
        this(DEFAULT_FAILURE_THRESHOLD, DEFAULT_COOLDOWN_SECONDS, DEFAULT_HALF_OPEN_TEST_LIMIT);
    }
    
    /**
     * Creates a circuit breaker with custom settings.
     * 
     * @param failureThreshold Number of consecutive failures before opening circuit
     * @param cooldownSeconds Seconds to wait before attempting recovery
     * @param halfOpenTestLimit Number of requests to allow in HALF_OPEN state
     */
    public CircuitBreaker(int failureThreshold, int cooldownSeconds, int halfOpenTestLimit) {
        this.failureThreshold = failureThreshold;
        this.cooldownSeconds = cooldownSeconds;
        this.halfOpenTestLimit = halfOpenTestLimit;
    }
    
    /**
     * Represents the state of a circuit breaker for a specific webhook.
     */
    public static class WebhookCircuitState {
        private CircuitBreakerState state;
        private int consecutiveFailures;
        private LocalDateTime lastFailureTime;
        private LocalDateTime circuitOpenedAt;
        private int halfOpenTestCount;
        
        public WebhookCircuitState() {
            this.state = CircuitBreakerState.CLOSED;
            this.consecutiveFailures = 0;
            this.halfOpenTestCount = 0;
        }
        
        // Getters and Setters
        public CircuitBreakerState getState() {
            return state;
        }
        
        public void setState(CircuitBreakerState state) {
            this.state = state;
        }
        
        public int getConsecutiveFailures() {
            return consecutiveFailures;
        }
        
        public void setConsecutiveFailures(int consecutiveFailures) {
            this.consecutiveFailures = consecutiveFailures;
        }
        
        public LocalDateTime getLastFailureTime() {
            return lastFailureTime;
        }
        
        public void setLastFailureTime(LocalDateTime lastFailureTime) {
            this.lastFailureTime = lastFailureTime;
        }
        
        public LocalDateTime getCircuitOpenedAt() {
            return circuitOpenedAt;
        }
        
        public void setCircuitOpenedAt(LocalDateTime circuitOpenedAt) {
            this.circuitOpenedAt = circuitOpenedAt;
        }
        
        public int getHalfOpenTestCount() {
            return halfOpenTestCount;
        }
        
        public void setHalfOpenTestCount(int halfOpenTestCount) {
            this.halfOpenTestCount = halfOpenTestCount;
        }
    }
    
    /**
     * Records a successful delivery and updates circuit breaker state.
     * 
     * @param circuitState The circuit state for the webhook
     */
    public void recordSuccess(WebhookCircuitState circuitState) {
        if (circuitState.getState() == CircuitBreakerState.HALF_OPEN) {
            // Success in HALF_OPEN means service recovered
            logger.info("Circuit breaker: Success in HALF_OPEN state - closing circuit");
            circuitState.setState(CircuitBreakerState.CLOSED);
            circuitState.setConsecutiveFailures(0);
            circuitState.setHalfOpenTestCount(0);
            circuitState.setCircuitOpenedAt(null);
        } else if (circuitState.getState() == CircuitBreakerState.CLOSED) {
            // Reset failure count on success
            circuitState.setConsecutiveFailures(0);
        }
        // OPEN state: success doesn't change state (need to transition to HALF_OPEN first)
    }
    
    /**
     * Records a failure and updates circuit breaker state.
     * 
     * @param circuitState The circuit state for the webhook
     */
    public void recordFailure(WebhookCircuitState circuitState) {
        LocalDateTime now = LocalDateTime.now();
        circuitState.setLastFailureTime(now);
        
        if (circuitState.getState() == CircuitBreakerState.CLOSED) {
            // Increment failure count
            int failures = circuitState.getConsecutiveFailures() + 1;
            circuitState.setConsecutiveFailures(failures);
            
            // Open circuit if threshold reached
            if (failures >= failureThreshold) {
                logger.warn("Circuit breaker: Opening circuit after {} consecutive failures", failures);
                circuitState.setState(CircuitBreakerState.OPEN);
                circuitState.setCircuitOpenedAt(now);
            }
        } else if (circuitState.getState() == CircuitBreakerState.HALF_OPEN) {
            // Failure in HALF_OPEN means service hasn't recovered
            logger.warn("Circuit breaker: Failure in HALF_OPEN state - reopening circuit");
            circuitState.setState(CircuitBreakerState.OPEN);
            circuitState.setCircuitOpenedAt(now);
            circuitState.setHalfOpenTestCount(0);
        }
        // OPEN state: failures don't change state (already open)
    }
    
    /**
     * Checks if a request should be allowed through the circuit breaker.
     * 
     * @param circuitState The circuit state for the webhook
     * @return true if request should be allowed, false if circuit is open
     */
    public boolean allowRequest(WebhookCircuitState circuitState) {
        if (circuitState.getState() == CircuitBreakerState.CLOSED) {
            return true; // Always allow in CLOSED state
        }
        
        if (circuitState.getState() == CircuitBreakerState.OPEN) {
            // Check if cooldown period has passed
            if (circuitState.getCircuitOpenedAt() != null) {
                LocalDateTime cooldownEnd = circuitState.getCircuitOpenedAt()
                        .plusSeconds(cooldownSeconds);
                
                if (LocalDateTime.now().isAfter(cooldownEnd)) {
                    // Cooldown passed, transition to HALF_OPEN
                    logger.info("Circuit breaker: Cooldown period passed - transitioning to HALF_OPEN");
                    circuitState.setState(CircuitBreakerState.HALF_OPEN);
                    circuitState.setHalfOpenTestCount(0);
                    return true; // Allow test request
                }
            }
            return false; // Circuit is open, block request
        }
        
        if (circuitState.getState() == CircuitBreakerState.HALF_OPEN) {
            // Allow limited test requests
            if (circuitState.getHalfOpenTestCount() < halfOpenTestLimit) {
                return true;
            }
            return false; // Test limit reached
        }
        
        return false; // Unknown state, block for safety
    }
    
    /**
     * Gets the current state of the circuit breaker.
     * 
     * @param circuitState The circuit state for the webhook
     * @return Current circuit breaker state
     */
    public CircuitBreakerState getState(WebhookCircuitState circuitState) {
        return circuitState.getState();
    }
    
    /**
     * Manually resets the circuit breaker to CLOSED state.
     * Useful for manual intervention or testing.
     * 
     * @param circuitState The circuit state for the webhook
     */
    public void reset(WebhookCircuitState circuitState) {
        logger.info("Circuit breaker: Manually resetting to CLOSED state");
        circuitState.setState(CircuitBreakerState.CLOSED);
        circuitState.setConsecutiveFailures(0);
        circuitState.setHalfOpenTestCount(0);
        circuitState.setCircuitOpenedAt(null);
    }
}

