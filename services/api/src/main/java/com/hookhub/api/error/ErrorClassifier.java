package com.hookhub.api.error;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.hookhub.api.worker.WebhookDeliveryClient;

/**
 * ErrorClassifier analyzes delivery failures and determines the appropriate action.
 * 
 * This component now delegates to the Python Decision Engine service for learning-based
 * decisions. Falls back to rule-based classification if the decision engine is unavailable.
 * 
 * The classifier makes decisions to prevent retry storms, avoid hammering
 * broken endpoints, and provide actionable diagnostics.
 */
@Component
public class ErrorClassifier {
    
    private static final Logger logger = LoggerFactory.getLogger(ErrorClassifier.class);
    
    private final ErrorClassificationConfig config;
    private final DecisionEngineClient decisionEngineClient;
    
    @Value("${decision.engine.fallback.enabled:true}")
    private boolean fallbackToRules;
    
    public ErrorClassifier(ErrorClassificationConfig config, DecisionEngineClient decisionEngineClient) {
        this.config = config;
        this.decisionEngineClient = decisionEngineClient;
    }
    
    /**
     * Classifies a delivery result and returns the appropriate decision.
     * 
     * First tries the Python Decision Engine for learning-based decisions.
     * Falls back to rule-based classification if decision engine is unavailable.
     * 
     * @param result The delivery result to classify
     * @param retryCount Current retry count
     * @param recentFailureRate Recent failure rate (0.0-1.0)
     * @param webhookId Webhook ID
     * @param totalFailures Total failures for webhook
     * @param totalSuccesses Total successes for webhook
     * @param consecutiveFailures Consecutive failures
     * @param circuitBreakerState Circuit breaker state
     * @return ErrorDecision indicating what action to take
     */
    public ErrorDecision classify(
            WebhookDeliveryClient.DeliveryResult result,
            int retryCount,
            double recentFailureRate,
            Long webhookId,
            Long totalFailures,
            Long totalSuccesses,
            Integer consecutiveFailures,
            String circuitBreakerState) {
        
        if (result.isSuccess()) {
            logger.warn("ErrorClassifier called with successful result - this should not happen");
            return ErrorDecision.RETRY; // Fallback, shouldn't occur
        }
        
        // Try Python decision engine first
        DecisionEngineClient.DecisionEngineResponse engineResponse = 
                decisionEngineClient.classifyError(
                        result, retryCount, recentFailureRate, webhookId,
                        totalFailures, totalSuccesses, consecutiveFailures, circuitBreakerState
                );
        
        if (engineResponse != null && engineResponse.getConfidenceScore() >= 0.6) {
            // Use decision from Python engine
            try {
                ErrorDecision decision = ErrorDecision.valueOf(engineResponse.getDecision());
                logger.info("Using decision from Python engine: {} (confidence: {})",
                        decision, engineResponse.getConfidenceScore());
                return decision;
            } catch (IllegalArgumentException e) {
                logger.warn("Invalid decision from Python engine: {}, falling back to rules",
                        engineResponse.getDecision());
            }
        }
        
        // Fallback to rule-based classification
        if (fallbackToRules) {
            logger.debug("Falling back to rule-based classification");
            return classifyWithRules(result);
        }
        
        // Last resort: default to RETRY
        logger.warn("No decision available, defaulting to RETRY");
        return ErrorDecision.RETRY;
    }
    
    /**
     * Rule-based classification (fallback implementation).
     * 
     * @param result The delivery result to classify
     * @return ErrorDecision indicating what action to take
     */
    private ErrorDecision classifyWithRules(WebhookDeliveryClient.DeliveryResult result) {
        int statusCode = result.getStatusCode();
        String errorMessage = result.getErrorMessage();
        String errorType = determineErrorType(statusCode, errorMessage);
        
        // Get rules sorted by priority (highest first)
        List<ErrorClassificationRule> rules = config.getRules().stream()
                .filter(rule -> rule.getEnabled() != null && rule.getEnabled())
                .sorted(Comparator.comparing(ErrorClassificationRule::getPriority).reversed())
                .collect(Collectors.toList());
        
        // Find first matching rule
        for (ErrorClassificationRule rule : rules) {
            if (rule.matches(statusCode, errorMessage, errorType)) {
                logger.info("Error classification matched rule: {} -> {}", rule.getName(), rule.getDecision());
                return rule.getDecision();
            }
        }
        
        // No rule matched - default to RETRY (conservative approach)
        logger.warn("No classification rule matched for statusCode={}, errorMessage={}, defaulting to RETRY", 
                statusCode, errorMessage);
        return ErrorDecision.RETRY;
    }
    
    /**
     * Gets a human-readable explanation for an error decision.
     * Uses the explanation template from the matching rule.
     * 
     * @param result The delivery result
     * @param decision The error decision
     * @return Human-readable explanation
     */
    public String getExplanation(WebhookDeliveryClient.DeliveryResult result, ErrorDecision decision) {
        int statusCode = result.getStatusCode();
        String errorMessage = result.getErrorMessage();
        String errorType = determineErrorType(statusCode, errorMessage);
        
        // Find matching rule to get explanation template
        List<ErrorClassificationRule> rules = config.getRules().stream()
                .filter(rule -> rule.getEnabled() != null && rule.getEnabled())
                .filter(rule -> rule.getDecision() == decision)
                .sorted(Comparator.comparing(ErrorClassificationRule::getPriority).reversed())
                .collect(Collectors.toList());
        
        for (ErrorClassificationRule rule : rules) {
            if (rule.matches(statusCode, errorMessage, errorType)) {
                return rule.generateExplanation(statusCode, errorMessage);
            }
        }
        
        // Fallback explanation if no rule matched
        return String.format("Delivery failed with status %d. Decision: %s", statusCode, decision);
    }
    
    /**
     * Determines error type from status code and error message.
     * 
     * @param statusCode HTTP status code
     * @param errorMessage Error message
     * @return Error type string
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
}

