package com.hookhub.api.repository;

import com.hookhub.api.model.ErrorClassification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ErrorClassificationRepository extends JpaRepository<ErrorClassification, Long> {
    
    /**
     * Find all error classifications for a specific event.
     * 
     * @param eventId Event ID
     * @return List of error classifications for the event
     */
    List<ErrorClassification> findByEventId(Long eventId);
    
    /**
     * Find all error classifications for a specific webhook.
     * 
     * @param webhookId Webhook ID
     * @return List of error classifications for the webhook
     */
    List<ErrorClassification> findByWebhookId(Long webhookId);
    
    /**
     * Find recent error classifications for a webhook.
     * Useful for analyzing error patterns.
     * 
     * @param webhookId Webhook ID
     * @return List of recent error classifications
     */
    List<ErrorClassification> findByWebhookIdOrderByCreatedAtDesc(Long webhookId);
}

