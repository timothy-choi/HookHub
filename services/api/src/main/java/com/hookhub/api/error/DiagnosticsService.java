package com.hookhub.api.error;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import com.hookhub.api.model.ErrorClassification;
import com.hookhub.api.model.Webhook;
import com.hookhub.api.repository.ErrorClassificationRepository;

/**
 * DiagnosticsService provides human-readable explanations and diagnostics for webhook delivery issues.
 * 
 * This service:
 * - Generates user-friendly error messages
 * - Analyzes error patterns
 * - Provides actionable recommendations
 * - Tracks webhook health metrics
 */
@Service
public class DiagnosticsService {
    
    private final ErrorClassificationRepository errorClassificationRepository;
    
    public DiagnosticsService(ErrorClassificationRepository errorClassificationRepository) {
        this.errorClassificationRepository = errorClassificationRepository;
    }
    
    /**
     * Generates a human-readable explanation for a delivery failure.
     * 
     * @param statusCode HTTP status code
     * @param errorMessage Error message
     * @param decision Error decision
     * @return Human-readable explanation
     */
    public String generateExplanation(int statusCode, String errorMessage, ErrorDecision decision) {
        if (statusCode == 429) {
            return "Your endpoint is rate-limiting requests. We'll retry after the rate limit window expires.";
        }
        
        if (statusCode >= 500 && statusCode < 600) {
            return String.format("Your endpoint returned %d – server error. This is likely temporary, and we'll retry automatically.", statusCode);
        }
        
        if (statusCode == 401) {
            return "Your endpoint returned 401 – authentication credentials may be invalid. Please check your webhook authentication settings.";
        }
        
        if (statusCode == 403) {
            return "Your endpoint returned 403 – access denied. Please verify that your webhook endpoint accepts requests from our service.";
        }
        
        if (statusCode == 404) {
            return "Your endpoint returned 404 – endpoint not found. Please verify that the webhook URL is correct and the endpoint exists.";
        }
        
        if (statusCode == 400) {
            return "Your endpoint returned 400 – bad request. The request format may be incorrect. Please check your webhook endpoint's expected payload format.";
        }
        
        if (statusCode == 0 || statusCode < 0) {
            if (errorMessage != null && errorMessage.toLowerCase().contains("timeout")) {
                return "Connection timeout – your endpoint did not respond in time. We'll retry automatically.";
            }
            if (errorMessage != null && errorMessage.toLowerCase().contains("dns")) {
                return "DNS resolution failed – the webhook URL cannot be resolved. Please verify the URL is correct.";
            }
            return "Network error – connection failed. This may be temporary, and we'll retry automatically.";
        }
        
        return String.format("Delivery failed with status %d. %s", statusCode, getDecisionExplanation(decision));
    }
    
    /**
     * Gets explanation for an error decision.
     * 
     * @param decision Error decision
     * @return Explanation of what the decision means
     */
    private String getDecisionExplanation(ErrorDecision decision) {
        switch (decision) {
            case RETRY:
                return "We'll retry the delivery automatically.";
            case FAIL_PERMANENT:
                return "This error is not retryable. Please check your webhook configuration.";
            case PAUSE_WEBHOOK:
                return "Webhook has been temporarily paused due to repeated failures. Please review and resume when ready.";
            case ESCALATE:
                return "This issue requires attention. Our team has been notified.";
            default:
                return "Please review the error details.";
        }
    }
    
    /**
     * Gets webhook health summary with recent errors.
     * 
     * @param webhook The webhook to analyze
     * @param recentClassifications Recent error classifications (last N errors)
     * @return Human-readable health summary
     */
    public String getWebhookHealthSummary(Webhook webhook, List<ErrorClassification> recentClassifications) {
        StringBuilder summary = new StringBuilder();
        
        summary.append(String.format("Webhook Health Summary for %s:\n", webhook.getUrl()));
        summary.append(String.format("  Total Successes: %d\n", webhook.getTotalSuccesses()));
        summary.append(String.format("  Total Failures: %d\n", webhook.getTotalFailures()));
        
        if (webhook.getTotalSuccesses() + webhook.getTotalFailures() > 0) {
            double successRate = (double) webhook.getTotalSuccesses() / 
                                (webhook.getTotalSuccesses() + webhook.getTotalFailures()) * 100;
            summary.append(String.format("  Success Rate: %.1f%%\n", successRate));
        }
        
        summary.append(String.format("  Circuit Breaker State: %s\n", webhook.getCircuitBreakerState()));
        summary.append(String.format("  Consecutive Failures: %d\n", webhook.getConsecutiveFailures()));
        
        if (webhook.getPausedUntil() != null && webhook.getPausedUntil().isAfter(LocalDateTime.now())) {
            summary.append(String.format("  Paused Until: %s\n", webhook.getPausedUntil()));
        }
        
        if (!recentClassifications.isEmpty()) {
            summary.append("\nRecent Errors:\n");
            recentClassifications.stream()
                    .limit(5)
                    .forEach(classification -> {
                        summary.append(String.format("  - [%s] %s: %s\n",
                                classification.getCreatedAt(),
                                classification.getErrorType(),
                                classification.getExplanation()));
                    });
        }
        
        return summary.toString();
    }
    
    /**
     * Gets recent error patterns for a webhook.
     * 
     * @param webhookId Webhook ID
     * @param limit Number of recent errors to analyze
     * @return List of recent error classifications
     */
    public List<ErrorClassification> getRecentErrors(Long webhookId, int limit) {
        return errorClassificationRepository.findByWebhookIdOrderByCreatedAtDesc(webhookId)
                .stream()
                .limit(limit)
                .collect(Collectors.toList());
    }
    
    /**
     * Analyzes error patterns and provides recommendations.
     * 
     * @param webhook Webhook to analyze
     * @return Recommendations string
     */
    public String analyzeAndRecommend(Webhook webhook) {
        List<ErrorClassification> recentErrors = getRecentErrors(webhook.getId(), 10);
        
        if (recentErrors.isEmpty()) {
            return "No recent errors to analyze.";
        }
        
        // Count error types
        long authErrors = recentErrors.stream()
                .filter(e -> e.getErrorType() != null && e.getErrorType().contains("AUTH"))
                .count();
        long rateLimitErrors = recentErrors.stream()
                .filter(e -> e.getErrorType() != null && e.getErrorType().contains("RATE_LIMIT"))
                .count();
        long serverErrors = recentErrors.stream()
                .filter(e -> e.getHttpStatusCode() != null && e.getHttpStatusCode() >= 500)
                .count();
        
        StringBuilder recommendations = new StringBuilder("Recommendations:\n");
        
        if (authErrors > 3) {
            recommendations.append("  - Multiple authentication errors detected. Please verify your webhook credentials.\n");
        }
        
        if (rateLimitErrors > 2) {
            recommendations.append("  - Frequent rate limiting. Consider implementing exponential backoff on your endpoint.\n");
        }
        
        if (serverErrors > 5) {
            recommendations.append("  - High number of server errors. Your endpoint may be experiencing issues.\n");
        }
        
        if (webhook.getCircuitBreakerState() != null && 
            webhook.getCircuitBreakerState().name().equals("OPEN")) {
            recommendations.append("  - Circuit breaker is OPEN. Webhook is temporarily disabled due to repeated failures.\n");
        }
        
        if (recommendations.toString().equals("Recommendations:\n")) {
            recommendations.append("  - No specific recommendations at this time.\n");
        }
        
        return recommendations.toString();
    }
}

