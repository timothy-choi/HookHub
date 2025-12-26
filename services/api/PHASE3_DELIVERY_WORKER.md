# Phase 3: Delivery Worker Service Implementation

This document describes the Delivery Worker Service implementation for the HookHub platform.

## Overview

The Delivery Worker Service is responsible for actually delivering webhook events to target URLs. It continuously dequeues events from the queue, fetches webhook details, sends HTTP POST requests, and handles retries with exponential backoff.

## Architecture

```
EventQueue → DeliveryWorker → WebhookDeliveryClient → HTTP POST
                ↓                      ↓
         EventRepository      RetryPolicy (exponential backoff)
                ↓
         Status Updates (SUCCESS, RETRY_PENDING, FAILURE)
```

## Components

### 1. DeliveryWorker (`com.hookhub.api.worker.DeliveryWorker`)

Multi-threaded background worker that:
- Continuously polls `EventQueue` for events
- Uses `ExecutorService` with 5 worker threads for concurrent processing
- Fetches webhook details from `WebhookRepository`
- Calls `WebhookDeliveryClient` to deliver payloads
- Updates event status in database via `EventRepository`
- Implements retry logic using `RetryPolicy`

**Lifecycle:**
- Automatically starts on application startup (`@PostConstruct`)
- Gracefully shuts down on application shutdown (`@PreDestroy`)
- Uses daemon thread to allow JVM shutdown

### 2. WebhookDeliveryClient (`com.hookhub.api.worker.WebhookDeliveryClient`)

HTTP client for delivering webhook payloads:
- Uses Spring `RestTemplate` for HTTP requests
- Configurable timeouts (5s connect, 10s read)
- Handles different error types:
  - **5xx errors**: Retryable (server errors)
  - **4xx errors**: Non-retryable (client errors)
  - **Network/timeout errors**: Retryable
- Returns `DeliveryResult` with success status and error details

### 3. RetryPolicy (`com.hookhub.api.worker.RetryPolicy`)

Implements exponential backoff with jitter:
- **Base delay**: 1 second
- **Max delay**: 60 seconds
- **Max retries**: 5
- **Jitter**: Random variation to prevent thundering herd

**Retry delay calculation:**
- Retry 1: ~1-2 seconds
- Retry 2: ~2-4 seconds
- Retry 3: ~4-8 seconds
- Retry 4: ~8-16 seconds
- Retry 5: ~16-32 seconds
- Retry 6+: Capped at 60 seconds

### 4. Event Status Updates

Events transition through these statuses:
- `PENDING`: Initial state when enqueued
- `PROCESSING`: Currently being delivered
- `SUCCESS`: Successfully delivered
- `RETRY_PENDING`: Failed but will be retried
- `FAILURE`: Failed after max retries or non-retryable error
- `PAUSED`: Manually paused (can be resumed)

## Delivery Flow

### Successful Delivery

```
Event (PENDING) → Dequeue → Fetch Webhook → HTTP POST → 200 OK
    ↓
Update Status: SUCCESS
```

### Retryable Failure

```
Event (PENDING) → Dequeue → Fetch Webhook → HTTP POST → 500 Error
    ↓
Update Status: RETRY_PENDING
Update retryCount: +1
Calculate delay (exponential backoff)
    ↓
Re-enqueue after delay
    ↓
Retry delivery...
```

### Non-Retryable Failure

```
Event (PENDING) → Dequeue → Fetch Webhook → HTTP POST → 400 Error
    ↓
Update Status: FAILURE
Log error
(TODO: Send alert)
```

### Max Retries Reached

```
Event (RETRY_PENDING) → Retry 5 times → Still failing
    ↓
Update Status: FAILURE
Log error
(TODO: Send alert)
```

## Configuration

### HTTP Timeouts

Configured in `AppConfig.java`:
```java
@Bean
public RestTemplate restTemplate(RestTemplateBuilder builder) {
    // Connect timeout: 5 seconds
    // Read timeout: 10 seconds
}
```

### Retry Policy

Configured in `AppConfig.java`:
```java
@Bean
public RetryPolicy retryPolicy() {
    return new RetryPolicy(1000, 60000, 5);
    // baseDelay: 1000ms (1 second)
    // maxDelay: 60000ms (60 seconds)
    // maxRetries: 5
}
```

### Worker Threads

Configured in `DeliveryWorker.java`:
```java
private static final int WORKER_THREADS = 5;
```

## Usage

### Starting the Application

```bash
cd services/api
mvn spring-boot:run
```

The `DeliveryWorker` will automatically start and begin processing events.

### Creating Events

```bash
# Register a webhook
curl -X POST http://localhost:8080/webhooks \
  -H "Content-Type: application/json" \
  -d '{
    "url": "https://httpbin.org/post",
    "metadata": {"description": "Test webhook"}
  }'

# Create an event (will be automatically delivered)
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

### Monitoring Delivery

Check application logs for delivery status:
```
INFO  - Processing event: id=1, webhookId=1, retryCount=0
INFO  - Delivering webhook to URL: https://httpbin.org/post, payload size: 45 bytes
INFO  - Webhook delivery successful: URL=https://httpbin.org/post, Status=200
INFO  - Event marked as SUCCESS: id=1
```

### Retry Behavior

For retryable failures (5xx, network errors):
```
WARN  - Webhook delivery failed (retryable): id=1, retryCount=0, will retry
INFO  - Scheduling retry for event: id=1, retryCount=1, delay=1234ms
INFO  - Event re-enqueued for retry: id=1, retryCount=1
```

## Database Schema Updates

The `events` table tracks:
- `status`: Event status (PENDING, PROCESSING, SUCCESS, RETRY_PENDING, FAILURE, PAUSED)
- `retry_count`: Number of retry attempts (default: 0)

## Thread Safety

- **DeliveryWorker**: Uses `ExecutorService` with fixed thread pool
- **EventQueue**: Thread-safe `ConcurrentLinkedQueue`
- **Database Updates**: Handled by Spring `@Transactional` in repositories
- **Retry Scheduling**: Thread-safe re-enqueue operations

## Error Handling

### Retryable Errors
- HTTP 5xx (server errors)
- Network timeouts
- Connection failures

### Non-Retryable Errors
- HTTP 4xx (client errors - bad request, unauthorized, etc.)
- Webhook not found in database

### Max Retries
- After 5 retry attempts, event is marked as `FAILURE`
- TODO: Send alert/notification for failed events

## Logging

The delivery worker logs:
- Event processing start
- Webhook delivery attempts
- Success/failure status
- Retry scheduling
- Error details

Log levels:
- `INFO`: Normal operations, successful deliveries
- `WARN`: Retryable failures, retry scheduling
- `ERROR`: Permanent failures, unexpected errors

## Testing

### Manual Testing

1. **Test Successful Delivery:**
   ```bash
   # Use httpbin.org for testing
   curl -X POST http://localhost:8080/webhooks \
     -d '{"url": "https://httpbin.org/post"}'
   
   curl -X POST http://localhost:8080/events \
     -d '{"webhookId": 1, "payload": {"test": "data"}}'
   ```

2. **Test Retry Logic:**
   ```bash
   # Use a URL that returns 500 errors
   curl -X POST http://localhost:8080/webhooks \
     -d '{"url": "https://httpstat.us/500"}'
   ```

3. **Test Non-Retryable Errors:**
   ```bash
   # Use a URL that returns 400 errors
   curl -X POST http://localhost:8080/webhooks \
     -d '{"url": "https://httpstat.us/400"}'
   ```

### Unit Testing (Future)

- Test `WebhookDeliveryClient` with mock RestTemplate
- Test `RetryPolicy` delay calculations
- Test `DeliveryWorker` with mock dependencies
- Test retry scheduling logic

## Performance Considerations

### Concurrent Processing
- 5 worker threads process events concurrently
- Adjust `WORKER_THREADS` based on load

### Queue Polling
- Polls every 100ms when queue is empty
- Adjust `POLL_INTERVAL_MS` for different latency requirements

### HTTP Timeouts
- Connect timeout: 5 seconds
- Read timeout: 10 seconds
- Adjust based on target webhook response times

## Future Enhancements

### Phase 4: Error Classification
- Classify errors more granularly
- Different retry policies for different error types
- Circuit breaker pattern for failing webhooks

### Phase 5: Observability
- Metrics for delivery success rate
- Metrics for retry counts
- Dashboard for monitoring delivery status

### Phase 6: Alerting
- Send alerts for failed events
- Send alerts for high retry rates
- Integration with notification services

## Troubleshooting

### Events Not Being Delivered

1. Check if `DeliveryWorker` started: Look for "DeliveryWorker started" in logs
2. Verify queue is not empty: Check application logs
3. Check for exceptions in worker threads
4. Verify webhook URLs are accessible

### High Retry Rates

1. Check target webhook server health
2. Review retry policy settings
3. Check network connectivity
4. Review HTTP timeout settings

### Memory Issues

1. Monitor queue size
2. Check for events stuck in RETRY_PENDING
3. Review worker thread count
4. Consider implementing queue size limits

## Code Structure

```
services/api/src/main/java/com/hookhub/api/
├── worker/
│   ├── DeliveryWorker.java          # Main worker component
│   ├── WebhookDeliveryClient.java  # HTTP delivery client
│   └── RetryPolicy.java            # Retry logic with backoff
├── config/
│   └── AppConfig.java              # RestTemplate & RetryPolicy beans
└── model/
    └── Event.java                  # Updated with new statuses
```

## Summary

Phase 3 successfully implements:
✅ Multi-threaded delivery worker
✅ HTTP delivery with RestTemplate
✅ Retry logic with exponential backoff and jitter
✅ Status tracking (SUCCESS, RETRY_PENDING, FAILURE)
✅ Database persistence of status updates
✅ Comprehensive logging
✅ Graceful shutdown
✅ Spring Boot best practices

The system is now fully functional for delivering webhooks with automatic retries!

