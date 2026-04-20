# Core API & Guardrail Microservice

## Overview
This project is a Spring Boot microservice designed to act as an API gateway with built-in guardrails to prevent excessive bot interactions and AI compute runaway. It utilizes Redis for distributed state management and atomic concurrency control.

## Tech Stack
- Java 17
- Spring Boot 3.2
- PostgreSQL
- Redis
- Docker & Docker Compose

## Features
- **Atomic Concurrency**: Limits bot replies to 100 per post using Redis Lua scripts to prevent race conditions.
- **Thread Safety**: Enforces a maximum comment depth of 20.
- **Cooldown Periods**: Implements a 10-minute cooldown for bot-to-human interactions.
- **Smart Notifications**: Batches notifications every 15 minutes to prevent spam, with a background task to summarize missed interactions.
- **Real-time Scoring**: Calculates virality scores based on human likes and comments.

## Setup & Run

### Prerequisites
- Docker & Docker Desktop

### Steps
1. Clone the repository.
2. Run the following command to start the infrastructure and the application:
   ```bash
   docker-compose up --build -d
   ```
3. The API will be available at `http://localhost:8080`.

## API Documentation
The project includes self-documenting API endpoints. You can access the UI here:
- **Swagger UI**: `http://localhost:8080/swagger-ui/index.html`

## Testing the Spam Protection
To verify the atomic horizontal cap (100 bot replies), you can run the included concurrency test:
```bash
./gradlew test --tests com.coreapi.guardrails.service.ConcurrentBotCommentTest
```

## Implementation Notes
- **Thread Safety**: Guaranteed using Redis-based atomic operations and TTL-managed keys.
- **Statelessness**: The application is fully stateless; all transient state (counters, locks) is managed in Redis.
- **Data Integrity**: Database transactions are only committed after Redis guardrails are satisfied.
