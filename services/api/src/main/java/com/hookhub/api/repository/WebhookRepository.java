package com.hookhub.api.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.hookhub.api.model.Webhook;

@Repository
public interface WebhookRepository extends JpaRepository<Webhook, Long> {
    
    /**
     * Find all webhooks
     * @return List of all webhooks
     */
    List<Webhook> findAll();
    
    /**
     * Find webhook by ID
     * @param id Webhook ID
     * @return Optional webhook
     */
    Optional<Webhook> findById(Long id);
    
    /**
     * Find webhooks by URL
     * @param url Webhook URL
     * @return List of webhooks with matching URL
     */
    List<Webhook> findByUrl(String url);
    
    /**
     * Check if webhook exists by URL
     * @param url Webhook URL
     * @return true if webhook exists
     */
    boolean existsByUrl(String url);
}

