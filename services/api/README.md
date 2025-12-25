# HookHub API Gateway Service

Spring Boot REST API service for webhook registration, event management, and webhook delivery orchestration.

## Features

- **Webhook Registration**: Register webhooks with URL validation and metadata
- **Webhook Listing**: List all registered webhooks
- **Event Creation**: Create and trigger events for webhooks
- **Event Resume**: Resume paused events
- **URL Validation**: Automatic validation of webhook URLs with suggestions
- **In-Memory Storage**: H2 in-memory database for Phase 1

## API Endpoints

### Register Webhook
```http
POST /webhooks
Content-Type: application/json

{
  "url": "https://example.com/webhook",
  "metadata": {
    "description": "Payment webhook",
    "secret": "optional-secret"
  }
}
```

**Response:**
```json
{
  "webhook": {
    "id": 1,
    "url": "https://example.com/webhook",
    "metadata": "{\"description\":\"Payment webhook\",\"secret\":\"optional-secret\"}",
    "createdAt": "2024-01-01T10:00:00",
    "updatedAt": "2024-01-01T10:00:00"
  },
  "isValid": true,
  "suggestions": []
}
```

### List Webhooks
```http
GET /webhooks
```

**Response:**
```json
[
  {
    "id": 1,
    "url": "https://example.com/webhook",
    "metadata": "{\"description\":\"Payment webhook\"}",
    "createdAt": "2024-01-01T10:00:00",
    "updatedAt": "2024-01-01T10:00:00"
  }
]
```

### Create Event
```http
POST /events
Content-Type: application/json

{
  "webhookId": 1,
  "payload": {
    "event": "payment.completed",
    "data": {
      "amount": 100.00,
      "currency": "USD"
    }
  }
}
```

**Response:**
```json
{
  "id": 1,
  "webhookId": 1,
  "payload": "{\"event\":\"payment.completed\",\"data\":{\"amount\":100.00,\"currency\":\"USD\"}}",
  "status": "PENDING",
  "createdAt": "2024-01-01T10:00:00",
  "updatedAt": "2024-01-01T10:00:00"
}
```

### Resume Event
```http
POST /events/{id}/resume
```

**Response:**
```json
{
  "id": 1,
  "webhookId": 1,
  "payload": "{\"event\":\"payment.completed\"}",
  "status": "PENDING",
  "createdAt": "2024-01-01T10:00:00",
  "updatedAt": "2024-01-01T10:00:00"
}
```

## Event Statuses

- `PENDING`: Event is queued and waiting to be processed
- `PROCESSING`: Event is currently being delivered
- `COMPLETED`: Event was successfully delivered
- `FAILED`: Event delivery failed
- `PAUSED`: Event is paused and can be resumed

## Running the Application

### Prerequisites
- Java 17 or higher
- Maven 3.6+

### Build and Run

```bash
# Navigate to the API service directory
cd services/api

# Build the project
mvn clean install

# Run the application
mvn spring-boot:run
```

The API will be available at `http://localhost:8080`

### H2 Console

For development, you can access the H2 database console at:
- URL: `http://localhost:8080/h2-console`
- JDBC URL: `jdbc:h2:mem:hookhubdb`
- Username: `sa`
- Password: (leave empty)

## Project Structure

```
services/api/
├── src/
│   ├── main/
│   │   ├── java/com/hookhub/api/
│   │   │   ├── HookHubApiApplication.java    # Main application class
│   │   │   ├── controller/
│   │   │   │   └── WebhookController.java    # REST endpoints
│   │   │   ├── service/
│   │   │   │   └── WebhookService.java       # Business logic
│   │   │   ├── repository/
│   │   │   │   ├── WebhookRepository.java    # Webhook data access
│   │   │   │   └── EventRepository.java      # Event data access
│   │   │   ├── model/
│   │   │   │   ├── Webhook.java              # Webhook entity
│   │   │   │   └── Event.java                # Event entity
│   │   │   ├── dto/
│   │   │   │   ├── WebhookRegistrationRequest.java
│   │   │   │   ├── WebhookRegistrationResponse.java
│   │   │   │   ├── WebhookResponse.java
│   │   │   │   ├── EventRequest.java
│   │   │   │   ├── EventResponse.java
│   │   │   │   └── ValidationSuggestion.java
│   │   │   ├── exception/
│   │   │   │   └── GlobalExceptionHandler.java  # Exception handling
│   │   │   └── config/
│   │   │       └── AppConfig.java            # Configuration beans
│   │   └── resources/
│   │       └── application.properties        # Application configuration
│   └── test/                                 # Test files (to be added)
└── pom.xml                                   # Maven dependencies
```

## Configuration

Edit `src/main/resources/application.properties` to configure:
- Server port (default: 8080)
- Database settings
- Logging levels
- JPA/Hibernate settings

## Next Steps

- Add integration with queue service for event processing
- Implement authentication and authorization
- Add comprehensive test coverage
- Add API documentation with Swagger/OpenAPI
- Replace H2 with production database (PostgreSQL, MySQL, etc.)

