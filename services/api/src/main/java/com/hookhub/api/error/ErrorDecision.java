package com.hookhub.api.error;

/**
 * Represents the decision made by ErrorClassifier for how to handle a delivery failure.
 * 
 * The decision determines the action to take when a webhook delivery fails:
 * - RETRY: Retry the delivery with exponential backoff
 * - FAIL_PERMANENT: Mark as permanently failed, no more retries
 * - PAUSE_WEBHOOK: Temporarily pause all deliveries for this webhook
 * - ESCALATE: Requires manual intervention or alerting
 */
public enum ErrorDecision {
    
    /**
     * Retry the delivery. The retry strategy will determine when and how.
     * Used for transient errors like network timeouts, 5xx server errors,
     * or rate limiting (429) where retrying later makes sense.
     */
    RETRY,
    
    /**
     * Permanently fail the event. No more retries will be attempted.
     * Used for client errors (4xx) that won't be fixed by retrying,
     * such as 400 (Bad Request), 401 (Unauthorized), 404 (Not Found).
     */
    FAIL_PERMANENT,
    
    /**
     * Pause the webhook temporarily. All events for this webhook will be
     * paused until manually resumed or auto-recovery occurs.
     * Used when a webhook is consistently failing and needs investigation,
     * or when rate limiting indicates the endpoint is overwhelmed.
     */
    PAUSE_WEBHOOK,
    
    /**
     * Escalate for manual intervention. This might trigger alerts,
     * notifications, or require admin review.
     * Used for unexpected errors or patterns that need human attention.
     */
    ESCALATE
}

