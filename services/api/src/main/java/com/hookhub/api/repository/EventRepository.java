package com.hookhub.api.repository;

import com.hookhub.api.model.Event;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface EventRepository extends JpaRepository<Event, Long> {
    List<Event> findByWebhookId(Long webhookId);
    Optional<Event> findById(Long id);
    List<Event> findByStatus(Event.EventStatus status);
}

