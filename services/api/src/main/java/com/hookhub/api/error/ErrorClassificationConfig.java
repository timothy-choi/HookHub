package com.hookhub.api.error;

import java.util.ArrayList;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration properties for error classification rules.
 * Rules are loaded from application.properties or application.yml.
 * 
 * Example configuration:
 * error.classification.rules[0].name=rate-limit
 * error.classification.rules[0].exactStatusCode=429
 * error.classification.rules[0].decision=RETRY
 * error.classification.rules[0].explanationTemplate=Your endpoint is rate-limiting requests. We'll retry after the rate limit window expires.
 * error.classification.rules[0].priority=100
 */
@Configuration
@ConfigurationProperties(prefix = "error.classification")
public class ErrorClassificationConfig {
    
    private List<ErrorClassificationRule> rules = new ArrayList<>();
    
    /**
     * Default rules that are used if no configuration is provided.
     * These can be overridden by configuration.
     */
    public static List<ErrorClassificationRule> getDefaultRules() {
        List<ErrorClassificationRule> defaultRules = new ArrayList<>();
        
        // Rate limiting (429) - highest priority
        ErrorClassificationRule rateLimit = new ErrorClassificationRule();
        rateLimit.setName("rate-limit");
        rateLimit.setExactStatusCode(429);
        rateLimit.setDecision(ErrorDecision.RETRY);
        rateLimit.setExplanationTemplate("Your endpoint is rate-limiting requests. We'll retry after the rate limit window expires.");
        rateLimit.setPriority(100);
        defaultRules.add(rateLimit);
        
        // Server errors (5xx) - retry
        ErrorClassificationRule serverError = new ErrorClassificationRule();
        serverError.setName("server-error");
        serverError.setStatusCodeMin(500);
        serverError.setStatusCodeMax(599);
        serverError.setDecision(ErrorDecision.RETRY);
        serverError.setExplanationTemplate("Your endpoint returned {statusCode} – server error. This is likely temporary, and we'll retry automatically.");
        serverError.setPriority(50);
        defaultRules.add(serverError);
        
        // Authentication errors (401)
        ErrorClassificationRule unauthorized = new ErrorClassificationRule();
        unauthorized.setName("unauthorized");
        unauthorized.setExactStatusCode(401);
        unauthorized.setDecision(ErrorDecision.FAIL_PERMANENT);
        unauthorized.setExplanationTemplate("Your endpoint returned 401 – authentication credentials may be invalid. Please check your webhook authentication settings.");
        unauthorized.setPriority(90);
        defaultRules.add(unauthorized);
        
        // Forbidden (403)
        ErrorClassificationRule forbidden = new ErrorClassificationRule();
        forbidden.setName("forbidden");
        forbidden.setExactStatusCode(403);
        forbidden.setDecision(ErrorDecision.FAIL_PERMANENT);
        forbidden.setExplanationTemplate("Your endpoint returned 403 – access denied. Please verify that your webhook endpoint accepts requests from our service.");
        forbidden.setPriority(90);
        defaultRules.add(forbidden);
        
        // Not found (404)
        ErrorClassificationRule notFound = new ErrorClassificationRule();
        notFound.setName("not-found");
        notFound.setExactStatusCode(404);
        notFound.setDecision(ErrorDecision.FAIL_PERMANENT);
        notFound.setExplanationTemplate("Your endpoint returned 404 – endpoint not found. Please verify that the webhook URL is correct and the endpoint exists.");
        notFound.setPriority(90);
        defaultRules.add(notFound);
        
        // Bad request (400)
        ErrorClassificationRule badRequest = new ErrorClassificationRule();
        badRequest.setName("bad-request");
        badRequest.setExactStatusCode(400);
        badRequest.setDecision(ErrorDecision.FAIL_PERMANENT);
        badRequest.setExplanationTemplate("Your endpoint returned 400 – bad request. The request format may be incorrect. Please check your webhook endpoint's expected payload format.");
        badRequest.setPriority(90);
        defaultRules.add(badRequest);
        
        // Request timeout (408) - retry
        ErrorClassificationRule timeout = new ErrorClassificationRule();
        timeout.setName("request-timeout");
        timeout.setExactStatusCode(408);
        timeout.setDecision(ErrorDecision.RETRY);
        timeout.setExplanationTemplate("Request timeout – your endpoint did not respond in time. We'll retry automatically.");
        timeout.setPriority(80);
        defaultRules.add(timeout);
        
        // Network errors (statusCode 0 or negative)
        ErrorClassificationRule networkError = new ErrorClassificationRule();
        networkError.setName("network-error");
        networkError.setStatusCodeMax(0);
        networkError.setDecision(ErrorDecision.RETRY);
        networkError.setExplanationTemplate("Network error – connection failed. This may be temporary, and we'll retry automatically.");
        networkError.setPriority(70);
        defaultRules.add(networkError);
        
        // Other 4xx client errors - fail permanent
        ErrorClassificationRule clientError = new ErrorClassificationRule();
        clientError.setName("client-error");
        clientError.setStatusCodeMin(400);
        clientError.setStatusCodeMax(499);
        clientError.setDecision(ErrorDecision.FAIL_PERMANENT);
        clientError.setExplanationTemplate("Your endpoint returned {statusCode} – client error. This error is not retryable. Please check your webhook configuration.");
        clientError.setPriority(10);
        defaultRules.add(clientError);
        
        return defaultRules;
    }
    
    public List<ErrorClassificationRule> getRules() {
        if (rules == null || rules.isEmpty()) {
            return getDefaultRules();
        }
        return rules;
    }
    
    public void setRules(List<ErrorClassificationRule> rules) {
        this.rules = rules;
    }
}

