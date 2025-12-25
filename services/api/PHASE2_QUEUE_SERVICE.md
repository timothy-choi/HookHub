# Phase 2: Event Queue Service Implementation

This document describes the Event Queue Service implementation for the HookHub platform.

## Overview

The Event Queue Service provides a thread-safe, in-memory queue for processing webhook events. Events are enqueued when created via the API and consumed by a background worker thread.

## Architecture

```
API Controller → WebhookService → EventRepository (Database)
                      ↓
                 EventQueue (InMemoryEventQueue)
                      ↓
              EventConsumer (Background Thread)
```

## Components

### 1. EventQueue Interface (`com.hookhub.api.queue.EventQueue`)

Defines the contract for event queue operations:

- `enqueue(Event event)`: Adds an event to the queue
- `dequeue()`: Removes and returns the next event
- `isEmpty()`: Checks if the queue is empty
- `size()`: Returns the current queue size

### 2. InMemoryEventQueue (`com.hookhub.api.queue.InMemoryEventQueue`)

Thread-safe in-memory implementation using `ConcurrentLinkedQueue`:

- **Thread-safe**: Uses lock-free algorithms for concurrent access
- **Non-blocking**: Fast enqueue/dequeue operations
- **Spring Component**: Automatically managed by Spring container
- **Suitable for**: Single-instance deployments

**Note**: For distributed systems, consider implementing a distributed queue (Redis, RabbitMQ, etc.) using the same `EventQueue` interface.

### 3. EventConsumer (`com.hookhub.api.worker.EventConsumer`)

Background worker that continuously polls the queue and processes events:

- **Lifecycle Management**: Automatically starts on application startup (`@PostConstruct`)
- **Graceful Shutdown**: Stops cleanly on application shutdown (`@PreDestroy`)
- **Polling**: Checks queue every 100ms for new events
- **Current Behavior**: Prints event details to console
- **Future Integration**: Will connect to delivery worker in Phase 3

### 4. Event Entity Updates

The `Event` entity has been updated to include:

- `retryCount` (Integer): Tracks the number of retry attempts (default: 0)
- Automatically initialized to 0 on creation

## Integration Flow

### Creating an Event

1. **API Request**: `POST /events` received by `WebhookController`
2. **Service Layer**: `WebhookService.createEvent()`:
   - Validates webhook exists
   - Converts payload to JSON
   - Creates Event entity with `retryCount = 0`
   - **Saves to database** via `EventRepository`
   - **Enqueues event** via `EventQueue`
3. **Queue**: Event added to `InMemoryEventQueue`
4. **Consumer**: `EventConsumer` picks up event and processes it

### Processing Flow

```
Event Created → Saved to DB → Enqueued → Consumer Dequeues → Processed
```

## Usage

### Starting the Application

```bash
cd services/api
mvn spring-boot:run
```

The `EventConsumer` will automatically start and begin processing events.

### Creating Events via API

```bash
# Create an event
curl -X POST http://localhost:8080/events \
  -H "Content-Type: application/json" \
  -d '{
    "webhookId": 1,
    "payload": {
      "event": "test.event",
      "data": {"key": "value"}
    }
  }'
```

### Observing Queue Processing

When an event is created and enqueued, you'll see output like:

```
========================================
Event Dequeued and Processed:
  ID: 1
  Webhook ID: 1
  Status: PENDING
  Retry Count: 0
  Payload: {"event":"test.event","data":{"key":"value"}}
  Created At: 2024-12-24T18:30:00
========================================
```

## Configuration

### Polling Interval

The consumer polls the queue every 100ms. To change this, modify `POLL_INTERVAL_MS` in `EventConsumer.java`:

```java
private static final long POLL_INTERVAL_MS = 100; // milliseconds
```

### Queue Size Monitoring

You can monitor the queue size by adding a monitoring endpoint or using Spring Actuator.

## Database Schema

The `events` table now includes:

```sql
CREATE TABLE events (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    webhook_id BIGINT NOT NULL,
    payload TEXT,
    status VARCHAR(50) NOT NULL,
    retry_count INT NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL
);
```

## Thread Safety

- **InMemoryEventQueue**: Thread-safe using `ConcurrentLinkedQueue`
- **EventConsumer**: Runs in a separate daemon thread
- **WebhookService**: Uses `@Transactional` for database operations

## Future Enhancements

### Phase 3: Delivery Worker Integration

The `EventConsumer` will be enhanced to:
- Call delivery worker service
- Update event status based on delivery results
- Handle retries with exponential backoff

### Distributed Queue

For multi-instance deployments, implement:
- Redis-based queue
- RabbitMQ integration
- AWS SQS integration

All using the same `EventQueue` interface for easy swapping.

## Testing

### Manual Testing

1. Start the application
2. Create a webhook: `POST /webhooks`
3. Create an event: `POST /events`
4. Observe console output showing event processing

### Unit Testing (Future)

- Test `InMemoryEventQueue` thread safety
- Test `EventConsumer` lifecycle
- Test queue integration in `WebhookService`

## Troubleshooting

### Events Not Being Processed

1. Check if `EventConsumer` started: Look for "EventConsumer started" in logs
2. Verify queue is not empty: Check application logs
3. Check for exceptions in consumer thread

### High Memory Usage

- Monitor queue size
- Consider implementing queue size limits
- For production, use distributed queue with persistence

## Code Structure

```
services/api/src/main/java/com/hookhub/api/
├── queue/
│   ├── EventQueue.java              # Interface
│   └── InMemoryEventQueue.java     # Implementation
├── worker/
│   └── EventConsumer.java          # Background consumer
├── model/
│   └── Event.java                   # Updated with retryCount
├── service/
│   └── WebhookService.java         # Integrated with queue
└── dto/
    └── EventResponse.java          # Updated with retryCount
```

## Summary

Phase 2 successfully implements:
✅ Thread-safe in-memory event queue
✅ Background consumer for processing events
✅ Integration with existing API service
✅ Database persistence before queueing
✅ Event entity with retryCount field
✅ Spring Boot best practices (dependency injection, lifecycle management)

The system is ready for Phase 3: Delivery Worker integration.

