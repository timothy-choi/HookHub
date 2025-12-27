package com.hookhub.api.error;

import com.hookhub.api.worker.WebhookDeliveryClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * ErrorClassifier analyzes delivery failures and determines the appropriate action.
 * 
 * This component implements intelligent error classification based on:
 * - HTTP status codes (4xx client errors, 5xx server errors)
 * - Network failures and timeouts
 * - DNS and connection issues
 * - Rate limiting (429) with Retry-After headers
 * 
 * The classifier makes decisions to prevent retry storms, avoid hammering
 * broken endpoints, and provide actionable diagnostics.
 */
@Component
public class ErrorClassifier {
    
    private static final Logger logger = LoggerFactory.getLogger(ErrorClassifier.class);
    
    /**
     * Classifies a delivery result and returns the appropriate decision.
     * 
     * Classification logic:
     * - Success (2xx): Not applicable (should not call this method)
     * - 429 (Rate Limited): RETRY (respect Retry-After header)
     * - 5xx (Server Errors): RETRY (transient server issues)
     * - 401/403 (Auth Errors): FAIL_PERMANENT (credentials issue)
     * - 400/404 (Client Errors): FAIL_PERMANENT (bad request/endpoint)
     * - Network/Timeout: RETRY (transient network issues)
     * - DNS/Connection: RETRY (may be temporary)
     * 
     * @param result The delivery result to classify
     * @return ErrorDecision indicating what action to take
     */
    public ErrorDecision classify(WebhookDeliveryClient.DeliveryResult result) {
        if (result.isSuccess()) {
            logger.warn("ErrorClassifier called with successful result - this should not happen");
            return ErrorDecision.RETRY; // Fallback, shouldn't occur
        }
        
        int statusCode = result.getStatusCode();
        String errorMessage = result.getErrorMessage();
        
        // Rate limiting (429) - retry but respect Retry-After header
        if (statusCode == 429) {
            logger.info("Rate limiting detected (429) - will retry with respect to Retry-After header");
            return ErrorDecision.RETRY;
        }
        
        // 5xx Server Errors - retry (transient server issues)
        if (statusCode >= 500 && statusCode < 600) {
            logger.info("Server error (5xx) detected - will retry: statusCode={}", statusCode);
            return ErrorDecision.RETRY;
        }
        
        // 4xx Client Errors - classify based on specific status
        if (statusCode >= 400 && statusCode < 500) {
            return classifyClientError(statusCode);
        }
        
        // Network/Connection errors (statusCode 0 or negative indicates network issue)
        if (statusCode == 0 || statusCode < 0) {
            logger.info("Network/connection error detected - will retry: errorMessage={}", errorMessage);
            return ErrorDecision.RETRY;
        }
        
        // Unknown status codes - retry by default (conservative approach)
        logger.warn("Unknown status code: {} - defaulting to RETRY", statusCode);
        return ErrorDecision.RETRY;
    }
    
    /**
     * Classifies 4xx client errors into specific decisions.
     * 
     * @param statusCode HTTP status code (400-499)
     * @return ErrorDecision for the client error
     */
    private ErrorDecision classifyClientError(int statusCode) {
        switch (statusCode) {
            case 400: // Bad Request
                logger.info("Bad Request (400) - request format is invalid, failing permanently");
                return ErrorDecision.FAIL_PERMANENT;
                
            case 401: // Unauthorized
                logger.info("Unauthorized (401) - authentication credentials invalid, failing permanently");
                return ErrorDecision.FAIL_PERMANENT;
                
            case 403: // Forbidden
                logger.info("Forbidden (403) - access denied, failing permanently");
                return ErrorDecision.FAIL_PERMANENT;
                
            case 404: // Not Found
                logger.info("Not Found (404) - endpoint does not exist, failing permanently");
                return ErrorDecision.FAIL_PERMANENT;
                
            case 408: // Request Timeout
                logger.info("Request Timeout (408) - server timeout, will retry");
                return ErrorDecision.RETRY;
                
            case 409: // Conflict
                logger.info("Conflict (409) - resource conflict, failing permanently");
                return ErrorDecision.FAIL_PERMANENT;
                
            case 410: // Gone
                logger.info("Gone (410) - endpoint permanently removed, failing permanently");
                return ErrorDecision.FAIL_PERMANENT;
                
            case 413: // Payload Too Large
                logger.info("Payload Too Large (413) - request too large, failing permanently");
                return ErrorDecision.FAIL_PERMANENT;
                
            case 414: // URI Too Long
                logger.info("URI Too Long (414) - URL too long, failing permanently");
                return ErrorDecision.FAIL_PERMANENT;
                
            case 422: // Unprocessable Entity
                logger.info("Unprocessable Entity (422) - request format invalid, failing permanently");
                return ErrorDecision.FAIL_PERMANENT;
                
            case 451: // Unavailable For Legal Reasons
                logger.info("Unavailable For Legal Reasons (451) - legal restriction, pausing webhook");
                return ErrorDecision.PAUSE_WEBHOOK;
                
            default:
                // Other 4xx errors - fail permanently (conservative approach)
                logger.info("Other client error (4xx): {} - failing permanently", statusCode);
                return ErrorDecision.FAIL_PERMANENT;
        }
    }
    
    /**
     * Gets a human-readable explanation for an error decision.
     * 
     * @param result The delivery result
     * @param decision The error decision
     * @return Human-readable explanation
     */
    public String getExplanation(WebhookDeliveryClient.DeliveryResult result, ErrorDecision decision) {
        int statusCode = result.getStatusCode();
        
        if (statusCode == 429) {
            return "Your endpoint is rate-limiting requests. Retrying later with respect to Retry-After header.";
        }
        
        if (statusCode >= 500) {
            return String.format("Your endpoint returned %d – server error. This is likely temporary, retrying.", statusCode);
        }
        
        if (statusCode == 401) {
            return "Your endpoint returned 401 – authentication credentials may be invalid.";
        }
        
        if (statusCode == 403) {
            return "Your endpoint returned 403 – access denied. Check permissions.";
        }
        
        if (statusCode == 404) {
            return "Your endpoint returned 404 – endpoint not found. Verify the webhook URL.";
        }
        
        if (statusCode == 400) {
            return "Your endpoint returned 400 – bad request. Check payload format.";
        }
        
        if (statusCode == 0 || statusCode < 0) {
            return "Network error – connection failed. Retrying.";
        }
        
        return String.format("Delivery failed with status %d. Decision: %s", statusCode, decision);
    }
}

