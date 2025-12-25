# HookHub Project Structure

This document provides a detailed overview of the HookHub project structure.

## Directory Purpose

### `/services/`
Contains all microservices that make up the HookHub platform.

#### `/services/api/`
API/Gateway Service - REST API endpoints for:
- Webhook registration and management
- Event triggering
- Resuming paused events
- Listing webhooks and events

#### `/services/queue/`
Event Queue Service - Manages event queueing:
- Producers: Enqueue events
- Consumers: Dequeue events
- Managers: Queue configuration and management

#### `/services/worker/`
Delivery Worker Service - Delivers events to webhook endpoints:
- Delivery: Core delivery orchestration
- HTTP Client: HTTP request handling
- Circuit Breaker: Resilience patterns

#### `/services/error-classifier/`
Error Classification + Retry Service:
- Classifiers: Determine if errors are RETRYABLE, NON-RETRYABLE, or UNKNOWN
- Retry Logic: Exponential backoff retry implementation

#### `/services/database/`
State/Database Service - Data persistence:
- Repositories: Data access layer
- Migrations: Database schema versioning
- Schemas: Database schema definitions

#### `/services/observability/`
Observability/Dashboard Service:
- Metrics: Collection and aggregation
- Logs: Log management and aggregation
- Alerts: Alerting rules and handlers
- Dashboard: UI for monitoring

#### `/services/ai/`
AI Integration Service (Optional):
- Validators: AI-powered webhook validation
- Fix Suggestions: AI suggestions for payload fixes
- Payload Generators: Generate sample payloads
- Health Summaries: Summarize event health

### `/shared/`
Shared code and utilities used across services:
- Models: Common data models and entities
- Utils: Shared utility functions
- Config: Shared configuration

### `/deployments/`
Deployment configurations:
- Docker: Dockerfiles and docker-compose files
- Kubernetes: K8s manifests and Helm charts

### `/docs/`
Documentation:
- Architecture: System design and architecture docs
- API: API documentation
- Deployment: Deployment guides

### `/tests/`
Test suites:
- Unit: Unit tests for individual components
- Integration: Integration tests for service interactions
- E2e: End-to-end tests

### `/scripts/`
Utility scripts for development, deployment, and maintenance

### `/config/`
Configuration files for different environments

