package com.hookhub.api.model;

import com.hookhub.api.error.ErrorDecision;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;

/**
 * ErrorClassification entity stores error classifications for audit and diagnostics.
 * 
 * This provides a historical record of:
 * - What errors occurred
 * - How they were classified
 * - What decision was made
 * - Human-readable explanations
 * 
 * This enables:
 * - Debugging delivery issues
 * - Analyzing error patterns
 * - Generating user-facing diagnostics
 * - Auditing retry decisions
 */
@Entity
@Table(name = "error_classifications")
public class ErrorClassification {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @NotNull
    @Column(nullable = false)
    private Long eventId;
    
    @NotNull
    @Column(nullable = false)
    private Long webhookId;
    
    @Column(nullable = false)
    private Integer httpStatusCode;
    
    @Column(columnDefinition = "TEXT")
    private String errorMessage;
    
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ErrorDecision decision;
    
    @Column(columnDefinition = "TEXT")
    private String explanation; // Human-readable explanation
    
    @Column(name = "error_type")
    private String errorType; // e.g., "RATE_LIMIT", "SERVER_ERROR", "AUTH_ERROR"
    
    @Column(name = "retry_after_seconds")
    private Integer retryAfterSeconds;
    
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;
    
    public ErrorClassification() {
    }
    
    public ErrorClassification(Long eventId, Long webhookId, Integer httpStatusCode, 
                              String errorMessage, ErrorDecision decision, String explanation,
                              String errorType, Integer retryAfterSeconds) {
        this.eventId = eventId;
        this.webhookId = webhookId;
        this.httpStatusCode = httpStatusCode;
        this.errorMessage = errorMessage;
        this.decision = decision;
        this.explanation = explanation;
        this.errorType = errorType;
        this.retryAfterSeconds = retryAfterSeconds;
    }
    
    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
    
    // Getters and Setters
    public Long getId() {
        return id;
    }
    
    public void setId(Long id) {
        this.id = id;
    }
    
    public Long getEventId() {
        return eventId;
    }
    
    public void setEventId(Long eventId) {
        this.eventId = eventId;
    }
    
    public Long getWebhookId() {
        return webhookId;
    }
    
    public void setWebhookId(Long webhookId) {
        this.webhookId = webhookId;
    }
    
    public Integer getHttpStatusCode() {
        return httpStatusCode;
    }
    
    public void setHttpStatusCode(Integer httpStatusCode) {
        this.httpStatusCode = httpStatusCode;
    }
    
    public String getErrorMessage() {
        return errorMessage;
    }
    
    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }
    
    public ErrorDecision getDecision() {
        return decision;
    }
    
    public void setDecision(ErrorDecision decision) {
        this.decision = decision;
    }
    
    public String getExplanation() {
        return explanation;
    }
    
    public void setExplanation(String explanation) {
        this.explanation = explanation;
    }
    
    public String getErrorType() {
        return errorType;
    }
    
    public void setErrorType(String errorType) {
        this.errorType = errorType;
    }
    
    public Integer getRetryAfterSeconds() {
        return retryAfterSeconds;
    }
    
    public void setRetryAfterSeconds(Integer retryAfterSeconds) {
        this.retryAfterSeconds = retryAfterSeconds;
    }
    
    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
    
    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}

