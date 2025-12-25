package com.hookhub.api.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.hookhub.api.dto.EventRequest;
import com.hookhub.api.dto.EventResponse;
import com.hookhub.api.dto.WebhookRegistrationRequest;
import com.hookhub.api.dto.WebhookRegistrationResponse;
import com.hookhub.api.dto.WebhookResponse;
import com.hookhub.api.service.WebhookService;

import jakarta.validation.Valid;

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

    @GetMapping("/events")
    public ResponseEntity<List<EventResponse>> listEvents(
            @RequestParam(required = false) Long webhookId) {
        List<EventResponse> events;
        if (webhookId != null) {
            events = webhookService.listEventsByWebhookId(webhookId);
        } else {
            events = webhookService.listEvents();
        }
        return ResponseEntity.ok(events);
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

