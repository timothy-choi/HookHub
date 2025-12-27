package com.hookhub.api.worker;

import java.util.Random;

/**
 * RetryPolicy implements exponential backoff with jitter for retrying failed webhook deliveries.
 * 
 * This class calculates the delay between retry attempts using:
 * - Exponential backoff: delay = baseDelay * (2 ^ retryCount)
 * - Jitter: random variation to prevent thundering herd problem
 * 
 * Example delays (with baseDelay=1000ms, maxDelay=60000ms):
 * - Retry 1: ~1000-2000ms
 * - Retry 2: ~2000-4000ms
 * - Retry 3: ~4000-8000ms
 * - Retry 4: ~8000-16000ms
 * - Retry 5: ~16000-32000ms
 * - Retry 6+: ~32000-60000ms (capped at maxDelay)
 */
public class RetryPolicy {
    
    private final long baseDelayMs;
    private final long maxDelayMs;
    private final int maxRetries;
    private final Random random;
    
    /**
     * Default retry policy configuration
     */
    private static final long DEFAULT_BASE_DELAY_MS = 1000;      // 1 second
    private static final long DEFAULT_MAX_DELAY_MS = 60000;       // 60 seconds
    private static final int DEFAULT_MAX_RETRIES = 5;
    
    /**
     * Creates a retry policy with default settings.
     */
    public RetryPolicy() {
        this(DEFAULT_BASE_DELAY_MS, DEFAULT_MAX_DELAY_MS, DEFAULT_MAX_RETRIES);
    }
    
    /**
     * Creates a retry policy with custom settings.
     * 
     * @param baseDelayMs Base delay in milliseconds (will be doubled for each retry)
     * @param maxDelayMs Maximum delay in milliseconds (caps the exponential growth)
     * @param maxRetries Maximum number of retry attempts
     */
    public RetryPolicy(long baseDelayMs, long maxDelayMs, int maxRetries) {
        this.baseDelayMs = baseDelayMs;
        this.maxDelayMs = maxDelayMs;
        this.maxRetries = maxRetries;
        this.random = new Random();
    }
    
    /**
     * Calculates the delay before the next retry attempt.
     * Uses exponential backoff with jitter.
     * 
     * @param retryCount Current retry attempt number (0-based)
     * @return Delay in milliseconds before next retry
     */
    public long calculateDelay(int retryCount) {
        // Exponential backoff: baseDelay * 2^retryCount
        long exponentialDelay = baseDelayMs * (1L << retryCount);
        
        // Cap at maxDelay
        long cappedDelay = Math.min(exponentialDelay, maxDelayMs);
        
        // Add jitter: random value between 0 and cappedDelay
        // This prevents all retries from happening at the same time
        long jitter = (long) (random.nextDouble() * cappedDelay);
        
        return cappedDelay + jitter;
    }
    
    /**
     * Calculates the delay before the next retry attempt, respecting Retry-After header.
     * If Retry-After is provided, uses that value (with a minimum of baseDelay).
     * Otherwise, uses exponential backoff with jitter.
     * 
     * @param retryCount Current retry attempt number (0-based)
     * @param retryAfterSeconds Retry-After header value in seconds (null if not present)
     * @return Delay in milliseconds before next retry
     */
    public long calculateDelay(int retryCount, Integer retryAfterSeconds) {
        // If Retry-After header is present, use it (but ensure minimum delay)
        if (retryAfterSeconds != null && retryAfterSeconds > 0) {
            long retryAfterMs = retryAfterSeconds * 1000L;
            // Use the larger of Retry-After or base delay (respect server but don't retry too fast)
            return Math.max(retryAfterMs, baseDelayMs);
        }
        
        // Otherwise, use standard exponential backoff
        return calculateDelay(retryCount);
    }
    
    /**
     * Checks if another retry should be attempted.
     * 
     * @param retryCount Current retry attempt number
     * @return true if retries should continue, false if max retries reached
     */
    public boolean shouldRetry(int retryCount) {
        return retryCount < maxRetries;
    }
    
    /**
     * Gets the maximum number of retries allowed.
     * 
     * @return Maximum retry count
     */
    public int getMaxRetries() {
        return maxRetries;
    }
    
    /**
     * Gets the base delay in milliseconds.
     * 
     * @return Base delay
     */
    public long getBaseDelayMs() {
        return baseDelayMs;
    }
    
    /**
     * Gets the maximum delay in milliseconds.
     * 
     * @return Maximum delay
     */
    public long getMaxDelayMs() {
        return maxDelayMs;
    }
}

