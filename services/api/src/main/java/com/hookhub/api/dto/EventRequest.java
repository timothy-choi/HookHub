package com.hookhub.api.dto;

import jakarta.validation.constraints.NotNull;

import java.util.Map;

public class EventRequest {

    @NotNull(message = "Webhook ID is required")
    private Long webhookId;

    private Map<String, Object> payload;

    public EventRequest() {
    }

    public EventRequest(Long webhookId, Map<String, Object> payload) {
        this.webhookId = webhookId;
        this.payload = payload;
    }

    public Long getWebhookId() {
        return webhookId;
    }

    public void setWebhookId(Long webhookId) {
        this.webhookId = webhookId;
    }

    public Map<String, Object> getPayload() {
        return payload;
    }

    public void setPayload(Map<String, Object> payload) {
        this.payload = payload;
    }
}

