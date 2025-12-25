package com.hookhub.api.service;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hookhub.api.dto.EventRequest;
import com.hookhub.api.dto.EventResponse;
import com.hookhub.api.dto.ValidationSuggestion;
import com.hookhub.api.dto.WebhookRegistrationRequest;
import com.hookhub.api.dto.WebhookRegistrationResponse;
import com.hookhub.api.dto.WebhookResponse;
import com.hookhub.api.model.Event;
import com.hookhub.api.model.Webhook;
import com.hookhub.api.repository.EventRepository;
import com.hookhub.api.repository.WebhookRepository;

@Service
@Transactional
public class WebhookService {

    private final WebhookRepository webhookRepository;
    private final EventRepository eventRepository;
    private final ObjectMapper objectMapper;

    public WebhookService(WebhookRepository webhookRepository, 
                         EventRepository eventRepository,
                         ObjectMapper objectMapper) {
        this.webhookRepository = webhookRepository;
        this.eventRepository = eventRepository;
        this.objectMapper = objectMapper;
    }

    public WebhookRegistrationResponse registerWebhook(WebhookRegistrationRequest request) {
        // Validate URL
        List<ValidationSuggestion> suggestions = validateWebhookUrl(request.getUrl());
        boolean isValid = suggestions.isEmpty();

        // Convert metadata to JSON string
        String metadataJson = null;
        if (request.getMetadata() != null) {
            try {
                metadataJson = objectMapper.writeValueAsString(request.getMetadata());
            } catch (JsonProcessingException e) {
                suggestions.add(new ValidationSuggestion("metadata", 
                    "Invalid metadata format", 
                    "Metadata should be a valid JSON object"));
            }
        }

        // Create and save webhook
        Webhook webhook = new Webhook();
        webhook.setUrl(request.getUrl());
        webhook.setMetadata(metadataJson);
        webhook = webhookRepository.save(webhook);

        // Convert to response DTO
        WebhookResponse webhookResponse = convertToWebhookResponse(webhook);

        return new WebhookRegistrationResponse(webhookResponse, isValid, suggestions);
    }

    public List<WebhookResponse> listWebhooks() {
        return webhookRepository.findAll().stream()
                .map(this::convertToWebhookResponse)
                .collect(Collectors.toList());
    }

    public List<EventResponse> listEvents() {
        return eventRepository.findAll().stream()
                .map(this::convertToEventResponse)
                .collect(Collectors.toList());
    }

    public List<EventResponse> listEventsByWebhookId(Long webhookId) {
        return eventRepository.findByWebhookId(webhookId).stream()
                .map(this::convertToEventResponse)
                .collect(Collectors.toList());
    }

    public EventResponse createEvent(EventRequest request) {
        // Verify webhook exists
        webhookRepository.findById(request.getWebhookId())
                .orElseThrow(() -> new ResourceNotFoundException("Webhook not found with id: " + request.getWebhookId()));

        // Convert payload to JSON string
        String payloadJson = null;
        if (request.getPayload() != null) {
            try {
                payloadJson = objectMapper.writeValueAsString(request.getPayload());
            } catch (JsonProcessingException e) {
                throw new IllegalArgumentException("Invalid payload format: " + e.getMessage());
            }
        }

        // Create and save event
        Event event = new Event();
        event.setWebhookId(request.getWebhookId());
        event.setPayload(payloadJson);
        event.setStatus(Event.EventStatus.PENDING);
        event = eventRepository.save(event);

        return convertToEventResponse(event);
    }

    public EventResponse resumeEvent(Long eventId) {
        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new ResourceNotFoundException("Event not found with id: " + eventId));

        if (event.getStatus() != Event.EventStatus.PAUSED) {
            throw new IllegalStateException("Event is not paused. Current status: " + event.getStatus());
        }

        event.setStatus(Event.EventStatus.PENDING);
        event = eventRepository.save(event);

        return convertToEventResponse(event);
    }

    private List<ValidationSuggestion> validateWebhookUrl(String url) {
        List<ValidationSuggestion> suggestions = new ArrayList<>();

        if (url == null || url.trim().isEmpty()) {
            suggestions.add(new ValidationSuggestion("url", 
                "URL is required", 
                "Please provide a valid webhook URL"));
            return suggestions;
        }

        try {
            URL urlObj = new URL(url);
            String protocol = urlObj.getProtocol();
            
            if (!protocol.equals("http") && !protocol.equals("https")) {
                suggestions.add(new ValidationSuggestion("url", 
                    "Invalid protocol", 
                    "URL must use HTTP or HTTPS protocol"));
            }

            if (urlObj.getHost() == null || urlObj.getHost().isEmpty()) {
                suggestions.add(new ValidationSuggestion("url", 
                    "Invalid host", 
                    "URL must contain a valid hostname"));
            }
        } catch (MalformedURLException e) {
            suggestions.add(new ValidationSuggestion("url", 
                "Invalid URL format", 
                "Please provide a valid URL (e.g., https://example.com/webhook)"));
        }

        return suggestions;
    }

    private WebhookResponse convertToWebhookResponse(Webhook webhook) {
        return new WebhookResponse(
                webhook.getId(),
                webhook.getUrl(),
                webhook.getMetadata(),
                webhook.getCreatedAt(),
                webhook.getUpdatedAt()
        );
    }

    private EventResponse convertToEventResponse(Event event) {
        return new EventResponse(
                event.getId(),
                event.getWebhookId(),
                event.getPayload(),
                event.getStatus(),
                event.getCreatedAt(),
                event.getUpdatedAt()
        );
    }

    // Custom exception for resource not found
    public static class ResourceNotFoundException extends RuntimeException {
        public ResourceNotFoundException(String message) {
            super(message);
        }
    }
}

