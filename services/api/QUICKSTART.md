# Quick Start Guide

## Prerequisites
- Java 17+
- Maven 3.6+

## Running the Application

1. Navigate to the API service directory:
   ```bash
   cd services/api
   ```

2. Build the project:
   ```bash
   mvn clean install
   ```

3. Run the application:
   ```bash
   mvn spring-boot:run
   ```

4. The API will be available at `http://localhost:8080`

## Testing the API

### Register a Webhook
```bash
curl -X POST http://localhost:8080/webhooks \
  -H "Content-Type: application/json" \
  -d '{
    "url": "https://example.com/webhook",
    "metadata": {
      "description": "Test webhook"
    }
  }'
```

### List Webhooks
```bash
curl http://localhost:8080/webhooks
```

### Create an Event
```bash
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

### Resume an Event
```bash
curl -X POST http://localhost:8080/events/1/resume
```

## H2 Database Console

Access the in-memory database console at:
- URL: http://localhost:8080/h2-console
- JDBC URL: `jdbc:h2:mem:hookhubdb`
- Username: `sa`
- Password: (leave empty)

