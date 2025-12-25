package com.hookhub.api.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.hookhub.api.model.Event;

@Repository
public interface EventRepository extends JpaRepository<Event, Long> {
    
    /**
     * Find events by webhook ID
     * @param webhookId Webhook ID
     * @return List of events for the webhook
     */
    List<Event> findByWebhookId(Long webhookId);
    
    /**
     * Find event by ID
     * @param id Event ID
     * @return Optional event
     */
    Optional<Event> findById(Long id);
    
    /**
     * Find events by status
     * @param status Event status
     * @return List of events with the specified status
     */
    List<Event> findByStatus(Event.EventStatus status);
    
    /**
     * Find events by webhook ID and status
     * @param webhookId Webhook ID
     * @param status Event status
     * @return List of events matching both criteria
     */
    List<Event> findByWebhookIdAndStatus(Long webhookId, Event.EventStatus status);
    
    /**
     * Count events by status
     * @param status Event status
     * @return Count of events with the specified status
     */
    long countByStatus(Event.EventStatus status);
    
    /**
     * Find events created after a specific date
     * @param dateTime Date and time
     * @return List of events created after the specified date
     */
    List<Event> findByCreatedAtAfter(LocalDateTime dateTime);
    
    /**
     * Find events by webhook ID ordered by creation date descending
     * @param webhookId Webhook ID
     * @return List of events ordered by creation date (newest first)
     */
    List<Event> findByWebhookIdOrderByCreatedAtDesc(Long webhookId);
}

