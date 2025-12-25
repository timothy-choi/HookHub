package com.hookhub.api.controller;

import com.hookhub.api.dto.*;
import com.hookhub.api.service.WebhookService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
public class WebhookController {

    private final WebhookService webhookService;

    public WebhookController(WebhookService webhookService) {
        this.webhookService = webhookService;
    }

    @PostMapping("/webhooks")
    public ResponseEntity<WebhookRegistrationResponse> registerWebhook(
            @Valid @RequestBody WebhookRegistrationRequest request) {
        WebhookRegistrationResponse response = webhookService.registerWebhook(request);
        
        HttpStatus status = response.isValid() 
            ? HttpStatus.CREATED 
            : HttpStatus.CREATED; // Still create even with suggestions
        
        return ResponseEntity.status(status).body(response);
    }

    @GetMapping("/webhooks")
    public ResponseEntity<List<WebhookResponse>> listWebhooks() {
        List<WebhookResponse> webhooks = webhookService.listWebhooks();
        return ResponseEntity.ok(webhooks);
    }

    @PostMapping("/events")
    public ResponseEntity<EventResponse> createEvent(
            @Valid @RequestBody EventRequest request) {
        EventResponse event = webhookService.createEvent(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(event);
    }

    @PostMapping("/events/{id}/resume")
    public ResponseEntity<EventResponse> resumeEvent(@PathVariable Long id) {
        EventResponse event = webhookService.resumeEvent(id);
        return ResponseEntity.ok(event);
    }
}

