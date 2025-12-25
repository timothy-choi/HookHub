package com.hookhub.api.dto;

import java.util.List;

public class WebhookRegistrationResponse {
    private WebhookResponse webhook;
    private boolean isValid;
    private List<ValidationSuggestion> suggestions;

    public WebhookRegistrationResponse() {
    }

    public WebhookRegistrationResponse(WebhookResponse webhook, boolean isValid, List<ValidationSuggestion> suggestions) {
        this.webhook = webhook;
        this.isValid = isValid;
        this.suggestions = suggestions;
    }

    public WebhookResponse getWebhook() {
        return webhook;
    }

    public void setWebhook(WebhookResponse webhook) {
        this.webhook = webhook;
    }

    public boolean isValid() {
        return isValid;
    }

    public void setValid(boolean valid) {
        isValid = valid;
    }

    public List<ValidationSuggestion> getSuggestions() {
        return suggestions;
    }

    public void setSuggestions(List<ValidationSuggestion> suggestions) {
        this.suggestions = suggestions;
    }
}

