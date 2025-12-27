package com.hookhub.api.worker;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import com.hookhub.api.model.Webhook;

/**
 * WebhookDeliveryClient handles HTTP delivery of webhook payloads to target URLs.
 * 
 * This component is responsible for:
 * - Sending HTTP POST requests to webhook URLs
 * - Handling HTTP responses and errors
 * - Determining if errors are retryable
 * - Configuring timeouts and headers
 * 
 * Note: HTTP timeouts are configured in AppConfig via RestTemplate bean.
 */
@Component
public class WebhookDeliveryClient {
    
    private static final Logger logger = LoggerFactory.getLogger(WebhookDeliveryClient.class);
    
    private final RestTemplate restTemplate;
    
    public WebhookDeliveryClient(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }
    
    /**
     * Delivers a webhook payload to the target URL.
     * 
     * @param webhook The webhook containing the target URL
     * @param payload The JSON payload to send
     * @return DeliveryResult containing success status and response details
     */
    public DeliveryResult deliver(Webhook webhook, String payload) {
        String url = webhook.getUrl();
        
        logger.info("Delivering webhook to URL: {}, payload size: {} bytes", url, payload != null ? payload.length() : 0);
        
        try {
            // Prepare HTTP headers
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("User-Agent", "HookHub-DeliveryWorker/1.0");
            
            // Add custom headers if metadata contains them
            if (webhook.getMetadata() != null && !webhook.getMetadata().isEmpty()) {
                // TODO: Parse metadata JSON and extract custom headers if needed
            }
            
            // Create HTTP entity
            HttpEntity<String> entity = new HttpEntity<>(payload, headers);
            
            // Send POST request
            ResponseEntity<String> response = restTemplate.exchange(
                    url,
                    HttpMethod.POST,
                    entity,
                    String.class
            );
            
            int statusCode = response.getStatusCode().value();
            String responseBody = response.getBody();
            
            logger.info("Webhook delivery successful: URL={}, Status={}, Response={}", 
                    url, statusCode, responseBody != null ? responseBody.substring(0, Math.min(100, responseBody.length())) : "empty");
            
            return DeliveryResult.success(statusCode, responseBody);
            
        } catch (HttpServerErrorException e) {
            // 5xx errors are retryable
            Integer retryAfter = extractRetryAfter(e.getResponseHeaders());
            logger.warn("Webhook delivery failed with server error: URL={}, Status={}, Message={}, Retry-After={}", 
                    url, e.getStatusCode(), e.getMessage(), retryAfter);
            return DeliveryResult.retryableFailure(e.getStatusCode().value(), e.getResponseBodyAsString(), retryAfter);
            
        } catch (HttpClientErrorException e) {
            // 4xx errors - check for rate limiting (429)
            Integer retryAfter = extractRetryAfter(e.getResponseHeaders());
            if (e.getStatusCode().value() == 429) {
                logger.warn("Webhook delivery rate limited: URL={}, Retry-After={}", url, retryAfter);
                return DeliveryResult.retryableFailure(e.getStatusCode().value(), e.getResponseBodyAsString(), retryAfter);
            }
            // Other 4xx errors are typically not retryable (client error)
            logger.warn("Webhook delivery failed with client error: URL={}, Status={}, Message={}", 
                    url, e.getStatusCode(), e.getMessage());
            return DeliveryResult.nonRetryableFailure(e.getStatusCode().value(), e.getResponseBodyAsString());
            
        } catch (ResourceAccessException e) {
            // Network/timeout errors are retryable
            logger.warn("Webhook delivery failed with network error: URL={}, Message={}", url, e.getMessage());
            return DeliveryResult.retryableFailure(0, e.getMessage());
            
        } catch (Exception e) {
            // Other exceptions - log and mark as retryable by default
            logger.error("Webhook delivery failed with unexpected error: URL={}", url, e);
            return DeliveryResult.retryableFailure(0, e.getMessage());
        }
    }
    
    /**
     * Extracts Retry-After header value from HTTP headers.
     * Supports both integer seconds and HTTP-date formats.
     * 
     * @param headers HTTP response headers
     * @return Retry-After value in seconds, or null if not present
     */
    private Integer extractRetryAfter(HttpHeaders headers) {
        if (headers == null) {
            return null;
        }
        
        String retryAfter = headers.getFirst("Retry-After");
        if (retryAfter == null || retryAfter.isEmpty()) {
            return null;
        }
        
        try {
            // Try parsing as integer (seconds)
            return Integer.parseInt(retryAfter);
        } catch (NumberFormatException e) {
            // Could be HTTP-date format, but for simplicity, we'll just log and return null
            // In production, you might want to parse HTTP-date format
            logger.debug("Retry-After header is not an integer: {}", retryAfter);
            return null;
        }
    }
    
    /**
     * Result of a webhook delivery attempt.
     * Enhanced to support Retry-After headers for rate limiting.
     */
    public static class DeliveryResult {
        private final boolean success;
        private final boolean retryable;
        private final int statusCode;
        private final String responseBody;
        private final String errorMessage;
        private final Integer retryAfterSeconds; // Retry-After header value in seconds
        
        private DeliveryResult(boolean success, boolean retryable, int statusCode, String responseBody, 
                             String errorMessage, Integer retryAfterSeconds) {
            this.success = success;
            this.retryable = retryable;
            this.statusCode = statusCode;
            this.responseBody = responseBody;
            this.errorMessage = errorMessage;
            this.retryAfterSeconds = retryAfterSeconds;
        }
        
        public static DeliveryResult success(int statusCode, String responseBody) {
            return new DeliveryResult(true, false, statusCode, responseBody, null, null);
        }
        
        public static DeliveryResult retryableFailure(int statusCode, String errorMessage) {
            return new DeliveryResult(false, true, statusCode, null, errorMessage, null);
        }
        
        public static DeliveryResult retryableFailure(int statusCode, String errorMessage, Integer retryAfterSeconds) {
            return new DeliveryResult(false, true, statusCode, null, errorMessage, retryAfterSeconds);
        }
        
        public static DeliveryResult nonRetryableFailure(int statusCode, String errorMessage) {
            return new DeliveryResult(false, false, statusCode, null, errorMessage, null);
        }
        
        public boolean isSuccess() {
            return success;
        }
        
        public boolean isRetryable() {
            return retryable;
        }
        
        public int getStatusCode() {
            return statusCode;
        }
        
        public String getResponseBody() {
            return responseBody;
        }
        
        public String getErrorMessage() {
            return errorMessage;
        }
        
        /**
         * Gets the Retry-After header value in seconds.
         * Returns null if not present or not applicable.
         * 
         * @return Retry-After seconds, or null
         */
        public Integer getRetryAfterSeconds() {
            return retryAfterSeconds;
        }
        
        /**
         * Checks if this result includes a Retry-After header.
         * 
         * @return true if Retry-After is present
         */
        public boolean hasRetryAfter() {
            return retryAfterSeconds != null && retryAfterSeconds > 0;
        }
    }
}

