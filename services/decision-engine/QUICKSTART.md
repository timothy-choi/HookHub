# Quick Start Guide

## Prerequisites

- Python 3.11+
- MySQL database (same as Java service)
- Database access credentials

## Setup (5 minutes)

1. **Navigate to service directory:**
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
   # Edit .env with your database credentials:
   # DB_HOST=your-rds-endpoint.region.rds.amazonaws.com
   # DB_USER=admin
   # DB_PASSWORD=your-password
   ```

5. **Run the service:**
   ```bash
   uvicorn app.main:app --reload --host 0.0.0.0 --port 8001
   ```

6. **Test the service:**
   ```bash
   # Health check
   curl http://localhost:8001/health
   
   # API docs
   open http://localhost:8001/docs
   ```

## Example Request

```bash
curl -X POST "http://localhost:8001/api/v1/classify/error" \
  -H "Content-Type: application/json" \
  -d '{
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
  }'
```

## Integration with Java Service

The Java service can call this service using RestTemplate:

```java
@Autowired
private RestTemplate restTemplate;

public ErrorDecision getDecisionFromEngine(ClassifyErrorRequest request) {
    String url = "http://decision-engine:8001/api/v1/classify/error";
    ClassifyErrorResponse response = restTemplate.postForObject(
        url, request, ClassifyErrorResponse.class
    );
    return ErrorDecision.valueOf(response.getDecision());
}
```

## Configuration

Key settings in `.env`:

- `MIN_SAMPLE_SIZE=5`: Minimum samples for confident decision
- `CONFIDENCE_THRESHOLD=0.6`: Minimum confidence to use learned decision
- `SIMILARITY_THRESHOLD=0.8`: Minimum similarity for error matching
- `MAX_HISTORICAL_DAYS=90`: Look back 90 days for historical data

## Troubleshooting

**Database connection error:**
- Verify database credentials in `.env`
- Check network connectivity to RDS
- Ensure database exists and tables are created

**No historical data:**
- Service will use fallback decisions
- Ensure Java service has been running and creating error classifications

**Import errors:**
- Ensure virtual environment is activated
- Run `pip install -r requirements.txt` again

