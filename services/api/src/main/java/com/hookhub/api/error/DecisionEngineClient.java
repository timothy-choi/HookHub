package com.hookhub.api.error;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hookhub.api.worker.WebhookDeliveryClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.Map;

/**
 * Client for calling the Python Decision Engine service.
 * 
 * This component makes HTTP requests to the learning-based decision engine
 * to get intelligent error classification decisions.
 */
@Component
public class DecisionEngineClient {
    
    private static final Logger logger = LoggerFactory.getLogger(DecisionEngineClient.class);
    
    private final RestTemplate restTemplate;
    
    @Value("${decision.engine.url:http://localhost:8001}")
    private String decisionEngineUrl;
    
    @Value("${decision.engine.enabled:true}")
    private boolean enabled;
    
    @Value("${decision.engine.timeout:5000}")
    private int timeoutMs;
    
    public DecisionEngineClient(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }
    
    /**
     * Classifies an error using the Python decision engine.
     * 
     * @param result Delivery result from webhook attempt
     * @param retryCount Current retry count
     * @param recentFailureRate Recent failure rate (0.0-1.0)
     * @param webhookId Webhook ID
     * @param totalFailures Total failures for webhook
     * @param totalSuccesses Total successes for webhook
     * @param consecutiveFailures Consecutive failures
     * @param circuitBreakerState Circuit breaker state
     * @return DecisionEngineResponse with decision and confidence, or null if service unavailable
     */
    public DecisionEngineResponse classifyError(
            WebhookDeliveryClient.DeliveryResult result,
            int retryCount,
            double recentFailureRate,
            Long webhookId,
            Long totalFailures,
            Long totalSuccesses,
            Integer consecutiveFailures,
            String circuitBreakerState) {
        
        if (!enabled) {
            logger.debug("Decision engine is disabled, skipping call");
            return null;
        }
        
        try {
            // Build request payload
            Map<String, Object> request = buildRequest(
                    result, retryCount, recentFailureRate, webhookId,
                    totalFailures, totalSuccesses, consecutiveFailures, circuitBreakerState
            );
            
            // Prepare HTTP headers
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(request, headers);
            
            // Call Python decision engine
            String url = decisionEngineUrl + "/api/v1/classify/error";
            logger.debug("Calling decision engine: {}", url);
            
            ResponseEntity<DecisionEngineResponse> response = restTemplate.postForEntity(
                    url,
                    entity,
                    DecisionEngineResponse.class
            );
            
            DecisionEngineResponse decisionResponse = response.getBody();
            
            if (decisionResponse != null) {
                logger.info("Decision engine response: decision={}, confidence={}, fallback={}",
                        decisionResponse.getDecision(),
                        decisionResponse.getConfidenceScore(),
                        decisionResponse.isFallbackUsed());
            }
            
            return decisionResponse;
            
        } catch (ResourceAccessException e) {
            logger.warn("Decision engine unavailable (timeout/connection): {}", e.getMessage());
            return null;
        } catch (RestClientException e) {
            logger.warn("Decision engine request failed: {}", e.getMessage());
            return null;
        } catch (Exception e) {
            logger.error("Unexpected error calling decision engine", e);
            return null;
        }
    }
    
    /**
     * Builds the request payload for the decision engine.
     */
    private Map<String, Object> buildRequest(
            WebhookDeliveryClient.DeliveryResult result,
            int retryCount,
            double recentFailureRate,
            Long webhookId,
            Long totalFailures,
            Long totalSuccesses,
            Integer consecutiveFailures,
            String circuitBreakerState) {
        
        Map<String, Object> request = new HashMap<>();
        
        // Error signature
        Map<String, Object> errorSignature = new HashMap<>();
        errorSignature.put("http_status_code", result.getStatusCode());
        
        // Determine error type
        String errorType = determineErrorType(result.getStatusCode(), result.getErrorMessage());
        errorSignature.put("error_type", errorType);
        errorSignature.put("error_message_pattern", result.getErrorMessage());
        request.put("error_signature", errorSignature);
        
        // Retry count and failure rate
        request.put("retry_count", retryCount);
        request.put("recent_failure_rate", recentFailureRate);
        
        // Webhook health
        Map<String, Object> webhookHealth = new HashMap<>();
        webhookHealth.put("webhook_id", webhookId);
        webhookHealth.put("total_failures", totalFailures != null ? totalFailures : 0);
        webhookHealth.put("total_successes", totalSuccesses != null ? totalSuccesses : 0);
        webhookHealth.put("consecutive_failures", consecutiveFailures != null ? consecutiveFailures : 0);
        webhookHealth.put("circuit_breaker_state", circuitBreakerState);
        request.put("webhook_health", webhookHealth);
        
        return request;
    }
    
    /**
     * Determines error type from status code and error message.
     */
    private String determineErrorType(int statusCode, String errorMessage) {
        if (statusCode == 429) {
            return "RATE_LIMIT";
        }
        if (statusCode >= 500) {
            return "SERVER_ERROR";
        }
        if (statusCode == 401 || statusCode == 403) {
            return "AUTH_ERROR";
        }
        if (statusCode >= 400 && statusCode < 500) {
            return "CLIENT_ERROR";
        }
        if (statusCode == 0 || statusCode < 0) {
            if (errorMessage != null && errorMessage.toLowerCase().contains("timeout")) {
                return "TIMEOUT_ERROR";
            }
            if (errorMessage != null && errorMessage.toLowerCase().contains("dns")) {
                return "DNS_ERROR";
            }
            return "NETWORK_ERROR";
        }
        return "UNKNOWN_ERROR";
    }
    
    /**
     * Response from the decision engine.
     */
    public static class DecisionEngineResponse {
        private String decision;
        private double confidenceScore;
        private String explanation;
        private DecisionEvidence evidence;
        private boolean fallbackUsed;
        
        public String getDecision() {
            return decision;
        }
        
        public void setDecision(String decision) {
            this.decision = decision;
        }
        
        public double getConfidenceScore() {
            return confidenceScore;
        }
        
        public void setConfidenceScore(double confidenceScore) {
            this.confidenceScore = confidenceScore;
        }
        
        public String getExplanation() {
            return explanation;
        }
        
        public void setExplanation(String explanation) {
            this.explanation = explanation;
        }
        
        public DecisionEvidence getEvidence() {
            return evidence;
        }
        
        public void setEvidence(DecisionEvidence evidence) {
            this.evidence = evidence;
        }
        
        public boolean isFallbackUsed() {
            return fallbackUsed;
        }
        
        public void setFallbackUsed(boolean fallbackUsed) {
            this.fallbackUsed = fallbackUsed;
        }
        
        public static class DecisionEvidence {
            private int sampleSize;
            private double successRate;
            private String decisionType;
            private Double similarityScore;
            private double confidenceScore;
            
            public int getSampleSize() {
                return sampleSize;
            }
            
            public void setSampleSize(int sampleSize) {
                this.sampleSize = sampleSize;
            }
            
            public double getSuccessRate() {
                return successRate;
            }
            
            public void setSuccessRate(double successRate) {
                this.successRate = successRate;
            }
            
            public String getDecisionType() {
                return decisionType;
            }
            
            public void setDecisionType(String decisionType) {
                this.decisionType = decisionType;
            }
            
            public Double getSimilarityScore() {
                return similarityScore;
            }
            
            public void setSimilarityScore(Double similarityScore) {
                this.similarityScore = similarityScore;
            }
            
            public double getConfidenceScore() {
                return confidenceScore;
            }
            
            public void setConfidenceScore(double confidenceScore) {
                this.confidenceScore = confidenceScore;
            }
        }
    }
}

