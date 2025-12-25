package com.hookhub.api.dto;

import jakarta.validation.constraints.NotBlank;

import java.util.Map;

public class WebhookRegistrationRequest {

    @NotBlank(message = "URL is required")
    private String url;

    private Map<String, Object> metadata;

    public WebhookRegistrationRequest() {
    }

    public WebhookRegistrationRequest(String url, Map<String, Object> metadata) {
        this.url = url;
        this.metadata = metadata;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public Map<String, Object> getMetadata() {
        return metadata;
    }

    public void setMetadata(Map<String, Object> metadata) {
        this.metadata = metadata;
    }
}

