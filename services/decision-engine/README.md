# HookHub Decision Engine Service

A Python FastAPI microservice that provides learning-based decision intelligence for webhook error classification.

## Overview

This service analyzes historical error outcomes from the HookHub webhook delivery platform to make intelligent decisions about how to handle errors. It uses structured similarity matching (not ML) to group similar errors and compute success rates for different decision types.

## Features

- **Learning-based decisions**: Learns from historical outcomes
- **Structured similarity matching**: Groups similar errors deterministically
- **Success rate computation**: Calculates success rates per decision type
- **Confidence-based decisions**: Only uses learned decisions when confidence is high
- **Safe fallback**: Uses conservative fallback when data is insufficient
- **Explainable**: Provides human-readable explanations and evidence
- **Deterministic**: Same input always produces same output

## Architecture

```
┌─────────────────┐
│   FastAPI API   │
│  POST /classify │
└────────┬────────┘
         │
         ▼
┌─────────────────┐
│ Decision Engine │
│  - Load history │
│  - Match errors │
│  - Compute rates│
│  - Choose best  │
└────────┬────────┘
         │
         ▼
┌─────────────────┐
│ Similarity      │
│  - HTTP status  │
│  - Error type   │
│  - Message      │
└────────┬────────┘
         │
         ▼
┌─────────────────┐
│   MySQL (RDS)   │
│  - Classifications│
│  - Events       │
│  - Webhooks     │
└─────────────────┘
```

## Installation

### Prerequisites

- Python 3.11+
- MySQL database (AWS RDS)
- Access to the same database as the Java Spring Boot service

### Setup

1. **Clone and navigate to the service:**
   ```bash
   cd services/decision-engine
   ```

2. **Create virtual environment:**
   ```bash
   python3 -m venv venv
   source venv/bin/activate  # On Windows: venv\Scripts\activate
   ```

3. **Install dependencies:**
   ```bash
   pip install -r requirements.txt
   ```

4. **Configure environment:**
   ```bash
   cp .env.example .env
   # Edit .env with your database credentials
   ```

5. **Run the service:**
   ```bash
   uvicorn app.main:app --reload --host 0.0.0.0 --port 8001
   ```

## API Documentation

Once running, visit:
- **Swagger UI**: http://localhost:8001/docs
- **ReDoc**: http://localhost:8001/redoc

## Endpoints

### POST /api/v1/classify/error

Classify an error and get a recommended decision.

**Request:**
```json
{
  "error_signature": {
    "http_status_code": 429,
    "error_type": "RATE_LIMIT",
    "error_message_pattern": "rate limit"
  },
  "retry_count": 2,
  "recent_failure_rate": 0.3,
  "webhook_health": {
    "webhook_id": 1,
    "total_failures": 10,
    "total_successes": 90,
    "consecutive_failures": 3,
    "circuit_breaker_state": "CLOSED"
  }
}
```

**Response:**
```json
{
  "decision": "RETRY",
  "confidence_score": 0.85,
  "explanation": "Based on 25 similar historical errors (25 matched by similarity), RETRY has a 85% success rate. Similarity score: 92%. Error: HTTP 429, RATE_LIMIT. Confidence: 85%.",
  "evidence": {
    "sample_size": 25,
    "success_rate": 0.85,
    "decision_type": "RETRY",
    "similarity_score": 0.92,
    "confidence_score": 0.85
  },
  "fallback_used": false
}
```

## Decision Types

- **RETRY**: Retry the delivery with exponential backoff
- **FAIL_PERMANENT**: Mark as permanently failed, no more retries
- **PAUSE_WEBHOOK**: Temporarily pause all deliveries for this webhook
- **ESCALATE**: Requires manual intervention

## How It Works

### 1. Load Historical Data

The engine queries the `error_classifications` and `events` tables to find:
- Past errors with similar signatures
- Decisions that were made
- Whether those decisions led to success or failure

### 2. Similarity Matching

Errors are matched using structured similarity:
- **HTTP status code**: Exact match = 1.0, same category (4xx/5xx) = 0.5
- **Error type**: Exact match = 1.0
- **Error message**: Jaccard similarity of keywords

### 3. Success Rate Computation

For each decision type (RETRY, FAIL_PERMANENT, etc.):
- Count successes vs failures
- Calculate success rate
- Track sample size

### 4. Decision Selection

- Choose decision with highest `success_rate × confidence_score`
- Confidence based on:
  - Sample size (more samples = higher confidence)
  - Success rate clarity (further from 0.5 = higher confidence)
- Only use learned decision if confidence >= threshold (default: 0.6)

### 5. Fallback Logic

If insufficient data:
- Use conservative fallback based on error signature
- Rate limiting (429) → RETRY
- Server errors (5xx) → RETRY
- Client errors (4xx) → FAIL_PERMANENT
- Network errors → RETRY
- Default → RETRY (conservative)

## Configuration

### Environment Variables

```bash
# Database
DB_HOST=your-rds-endpoint.region.rds.amazonaws.com
DB_PORT=3306
DB_NAME=webhookdb
DB_USER=admin
DB_PASSWORD=your-password

# Decision Engine
MIN_SAMPLE_SIZE=5              # Minimum samples for confident decision
CONFIDENCE_THRESHOLD=0.6       # Minimum confidence to use learned decision
SIMILARITY_THRESHOLD=0.8       # Minimum similarity for error matching
MAX_HISTORICAL_DAYS=90         # Look back 90 days for historical data
```

## Database Schema

The service reads from the same MySQL database as the Java service:

- **error_classifications**: Historical error classifications
- **events**: Event outcomes (SUCCESS, FAILURE, etc.)
- **webhooks**: Webhook health metrics

## Development

### Project Structure

```
services/decision-engine/
├── app/
│   ├── __init__.py
│   ├── main.py                 # FastAPI app
│   ├── core/
│   │   ├── config.py           # Configuration
│   │   └── database.py         # Database connection
│   ├── models/
│   │   └── database.py         # SQLAlchemy models
│   ├── schemas/
│   │   ├── request.py          # Request schemas
│   │   └── response.py       # Response schemas
│   ├── services/
│   │   ├── similarity.py       # Error similarity matching
│   │   └── decision_engine.py  # Decision logic
│   └── api/
│       └── v1/
│           └── endpoints/
│               └── classify.py # API endpoint
├── requirements.txt
├── .env.example
└── README.md
```

### Running Tests

```bash
# Install test dependencies
pip install pytest pytest-asyncio httpx

# Run tests
pytest
```

## Integration with Java Service

The Java Spring Boot service can call this service:

```java
// Example integration (pseudo-code)
RestTemplate restTemplate = new RestTemplate();
ClassifyErrorRequest request = new ClassifyErrorRequest(...);
ClassifyErrorResponse response = restTemplate.postForObject(
    "http://decision-engine:8001/api/v1/classify/error",
    request,
    ClassifyErrorResponse.class
);
```

## Future Enhancements

- **Machine Learning**: Replace structured similarity with ML models
- **Per-webhook learning**: Learn patterns specific to each webhook
- **Time-based patterns**: Consider time-of-day, day-of-week patterns
- **A/B testing**: Test different decision strategies
- **Real-time learning**: Update models as new outcomes arrive

## License

Part of the HookHub platform.

