package com.hookhub.api.model;

import java.time.LocalDateTime;

import com.hookhub.api.circuitbreaker.CircuitBreakerState;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

@Entity
@Table(name = "webhooks")
public class Webhook {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank(message = "URL is required")
    @Column(nullable = false, unique = false)
    private String url;

    @Column(columnDefinition = "TEXT")
    private String metadata; // JSON string for metadata

    // Circuit Breaker State
    @Enumerated(EnumType.STRING)
    @Column(name = "circuit_breaker_state")
    private CircuitBreakerState circuitBreakerState;

    @Column(name = "consecutive_failures")
    private Integer consecutiveFailures = 0;

    @Column(name = "circuit_opened_at")
    private LocalDateTime circuitOpenedAt;

    @Column(name = "last_failure_time")
    private LocalDateTime lastFailureTime;

    // Webhook Health Tracking
    @Column(name = "total_failures")
    private Long totalFailures = 0L;

    @Column(name = "total_successes")
    private Long totalSuccesses = 0L;

    @Column(name = "paused_until")
    private LocalDateTime pausedUntil; // Webhook paused until this timestamp

    @Column(name = "is_disabled")
    private Boolean isDisabled = false;

    @NotNull
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    public Webhook() {
    }

    public Webhook(Long id, String url, String metadata, LocalDateTime createdAt, LocalDateTime updatedAt) {
        this.id = id;
        this.url = url;
        this.metadata = metadata;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    @PrePersist
    protected void onCreate() {
        if (circuitBreakerState == null) {
            circuitBreakerState = CircuitBreakerState.CLOSED;
        }
        if (consecutiveFailures == null) {
            consecutiveFailures = 0;
        }
        if (totalFailures == null) {
            totalFailures = 0L;
        }
        if (totalSuccesses == null) {
            totalSuccesses = 0L;
        }
        if (isDisabled == null) {
            isDisabled = false;
        }
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    // Getters and Setters
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public String getMetadata() {
        return metadata;
    }

    public void setMetadata(String metadata) {
        this.metadata = metadata;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    // Circuit Breaker Getters and Setters
    public CircuitBreakerState getCircuitBreakerState() {
        return circuitBreakerState;
    }

    public void setCircuitBreakerState(CircuitBreakerState circuitBreakerState) {
        this.circuitBreakerState = circuitBreakerState;
    }

    public Integer getConsecutiveFailures() {
        return consecutiveFailures;
    }

    public void setConsecutiveFailures(Integer consecutiveFailures) {
        this.consecutiveFailures = consecutiveFailures;
    }

    public LocalDateTime getCircuitOpenedAt() {
        return circuitOpenedAt;
    }

    public void setCircuitOpenedAt(LocalDateTime circuitOpenedAt) {
        this.circuitOpenedAt = circuitOpenedAt;
    }

    public LocalDateTime getLastFailureTime() {
        return lastFailureTime;
    }

    public void setLastFailureTime(LocalDateTime lastFailureTime) {
        this.lastFailureTime = lastFailureTime;
    }

    // Health Tracking Getters and Setters
    public Long getTotalFailures() {
        return totalFailures;
    }

    public void setTotalFailures(Long totalFailures) {
        this.totalFailures = totalFailures;
    }

    public Long getTotalSuccesses() {
        return totalSuccesses;
    }

    public void setTotalSuccesses(Long totalSuccesses) {
        this.totalSuccesses = totalSuccesses;
    }

    public LocalDateTime getPausedUntil() {
        return pausedUntil;
    }

    public void setPausedUntil(LocalDateTime pausedUntil) {
        this.pausedUntil = pausedUntil;
    }

    public Boolean getIsDisabled() {
        return isDisabled;
    }

    public void setIsDisabled(Boolean isDisabled) {
        this.isDisabled = isDisabled;
    }
}

