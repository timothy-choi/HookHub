package com.hookhub.api.error;

/**
 * Represents a rule for error classification.
 * Rules define how to classify errors based on various criteria.
 */
public class ErrorClassificationRule {
    
    private String name;
    private Integer statusCodeMin;
    private Integer statusCodeMax;
    private Integer exactStatusCode;
    private String errorTypePattern; // e.g., "RATE_LIMIT", "AUTH_ERROR"
    private String errorMessagePattern; // Regex pattern for error message matching
    private ErrorDecision decision;
    private String explanationTemplate; // Template for explanation (e.g., "Your endpoint returned {statusCode}")
    private Integer priority; // Higher priority rules are evaluated first
    private Boolean enabled;
    
    public ErrorClassificationRule() {
        this.enabled = true;
        this.priority = 0;
    }
    
    public ErrorClassificationRule(String name, ErrorDecision decision, String explanationTemplate) {
        this.name = name;
        this.decision = decision;
        this.explanationTemplate = explanationTemplate;
        this.enabled = true;
        this.priority = 0;
    }
    
    /**
     * Checks if this rule matches the given error criteria.
     * 
     * @param statusCode HTTP status code
     * @param errorMessage Error message
     * @param errorType Error type
     * @return true if this rule matches
     */
    public boolean matches(int statusCode, String errorMessage, String errorType) {
        if (!enabled) {
            return false;
        }
        
        // Check exact status code match
        if (exactStatusCode != null && statusCode != exactStatusCode) {
            return false;
        }
        
        // Check status code range
        if (statusCodeMin != null && statusCode < statusCodeMin) {
            return false;
        }
        if (statusCodeMax != null && statusCode > statusCodeMax) {
            return false;
        }
        
        // Check error type pattern
        if (errorTypePattern != null && errorType != null) {
            if (!errorType.equalsIgnoreCase(errorTypePattern)) {
                return false;
            }
        }
        
        // Check error message pattern (regex)
        if (errorMessagePattern != null && errorMessage != null) {
            if (!errorMessage.matches(errorMessagePattern)) {
                return false;
            }
        }
        
        return true;
    }
    
    /**
     * Generates explanation from template.
     * 
     * @param statusCode HTTP status code
     * @param errorMessage Error message
     * @return Formatted explanation
     */
    public String generateExplanation(int statusCode, String errorMessage) {
        if (explanationTemplate == null) {
            return String.format("Delivery failed with status %d", statusCode);
        }
        
        return explanationTemplate
                .replace("{statusCode}", String.valueOf(statusCode))
                .replace("{errorMessage}", errorMessage != null ? errorMessage : "")
                .replace("{errorType}", errorTypePattern != null ? errorTypePattern : "");
    }
    
    // Getters and Setters
    public String getName() {
        return name;
    }
    
    public void setName(String name) {
        this.name = name;
    }
    
    public Integer getStatusCodeMin() {
        return statusCodeMin;
    }
    
    public void setStatusCodeMin(Integer statusCodeMin) {
        this.statusCodeMin = statusCodeMin;
    }
    
    public Integer getStatusCodeMax() {
        return statusCodeMax;
    }
    
    public void setStatusCodeMax(Integer statusCodeMax) {
        this.statusCodeMax = statusCodeMax;
    }
    
    public Integer getExactStatusCode() {
        return exactStatusCode;
    }
    
    public void setExactStatusCode(Integer exactStatusCode) {
        this.exactStatusCode = exactStatusCode;
    }
    
    public String getErrorTypePattern() {
        return errorTypePattern;
    }
    
    public void setErrorTypePattern(String errorTypePattern) {
        this.errorTypePattern = errorTypePattern;
    }
    
    public String getErrorMessagePattern() {
        return errorMessagePattern;
    }
    
    public void setErrorMessagePattern(String errorMessagePattern) {
        this.errorMessagePattern = errorMessagePattern;
    }
    
    public ErrorDecision getDecision() {
        return decision;
    }
    
    public void setDecision(ErrorDecision decision) {
        this.decision = decision;
    }
    
    public String getExplanationTemplate() {
        return explanationTemplate;
    }
    
    public void setExplanationTemplate(String explanationTemplate) {
        this.explanationTemplate = explanationTemplate;
    }
    
    public Integer getPriority() {
        return priority;
    }
    
    public void setPriority(Integer priority) {
        this.priority = priority;
    }
    
    public Boolean getEnabled() {
        return enabled;
    }
    
    public void setEnabled(Boolean enabled) {
        this.enabled = enabled;
    }
}

