# HookHub

A cloud-native webhook delivery platform that provides reliable, scalable event delivery with intelligent error handling and observability.

## Project Structure

```
HookHub/
├── services/                 # Microservices
│   ├── api/                  # API/Gateway Service
│   │   ├── handlers/         # HTTP request handlers
│   │   ├── routes/           # API route definitions
│   │   ├── middleware/       # Request middleware (auth, logging, etc.)
│   │   └── validation/       # Input validation logic
│   │
│   ├── queue/                # Event Queue Service
│   │   ├── producers/        # Event enqueue logic
│   │   ├── consumers/        # Event dequeue logic
│   │   └── managers/         # Queue management utilities
│   │
│   ├── worker/               # Delivery Worker Service
│   │   ├── delivery/         # Core delivery logic
│   │   ├── http-client/      # HTTP client for webhook delivery
│   │   └── circuit-breaker/  # Circuit breaker pattern implementation
│   │
│   ├── error-classifier/     # Error Classification + Retry Service
│   │   ├── classifiers/      # Error classification logic
│   │   └── retry-logic/      # Exponential backoff retry implementation
│   │
│   ├── database/             # State/Database Service
│   │   ├── repositories/     # Data access layer
│   │   ├── migrations/       # Database schema migrations
│   │   └── schemas/          # Database schema definitions
│   │
│   ├── observability/        # Observability/Dashboard Service
│   │   ├── metrics/          # Metrics collection
│   │   ├── logs/             # Log aggregation
│   │   ├── alerts/           # Alerting logic
│   │   └── dashboard/        # Dashboard UI components
│   │
│   └── ai/                   # AI Integration Service (Optional)
│       ├── validators/       # Webhook validation using AI
│       ├── fix-suggestions/  # Payload fix suggestions
│       ├── payload-generators/ # Sample payload generation
│       └── health-summaries/ # Event health summarization
│
├── shared/                   # Shared code across services
│   ├── models/               # Common data models/entities
│   ├── utils/                # Shared utilities
│   └── config/               # Shared configuration
│
├── deployments/              # Deployment configurations
│   ├── docker/               # Docker configurations
│   └── kubernetes/           # Kubernetes manifests
│
├── docs/                     # Documentation
│   ├── architecture/         # Architecture documentation
│   ├── api/                  # API documentation
│   └── deployment/           # Deployment guides
│
├── tests/                    # Test suites
│   ├── unit/                 # Unit tests
│   ├── integration/          # Integration tests
│   └── e2e/                  # End-to-end tests
│
├── scripts/                  # Utility scripts
└── config/                   # Configuration files
```

## Architecture Overview

### Services

1. **API/Gateway Service**: REST API for webhook registration, listing, event triggering, and resuming paused events
2. **Event Queue Service**: Handles event enqueueing and dequeueing (initially in-memory, later cloud queue)
3. **Delivery Worker Service**: Pulls events from queue and delivers to webhook endpoints
4. **Error Classification + Retry Service**: Classifies errors (RETRYABLE, NON-RETRYABLE, UNKNOWN) and handles retries with exponential backoff
5. **State/Database Service**: Persists webhook metadata, event states, retry counts, and delivery history
6. **Observability/Dashboard Service**: Collects metrics, displays dashboards, and manages alerts
7. **AI Integration Service** (Optional): Validates webhooks, suggests payload fixes, generates sample payloads, and summarizes event health

## Features

- Webhook registration with URL and optional metadata
- Reliable event queueing before delivery
- HTTP error handling with intelligent retry logic
- Error classification (RETRYABLE, NON-RETRYABLE, UNKNOWN)
- Exponential backoff retry strategy
- Persistent event states and delivery history
- Comprehensive observability with metrics, dashboards, and alerts
- AI-assisted webhook validation and payload suggestions

## Getting Started

*Details to be added as the project develops.*

