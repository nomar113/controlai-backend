# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

Controlaí is a payment notification and purchase invoice management backend built with **Kotlin 1.9.25**, **Spring Boot 3.5.7**, and **Gradle**. It uses MySQL 8.0, AWS SQS (via LocalStack for local dev), and Flyway for migrations. Java 21 is required.

## Common Commands

```bash
# Build
./gradlew build

# Run tests
./gradlew test

# Run a single test class
./gradlew test --tests "br.com.nomar.controlai.SomeTestClass"

# Run application (start Docker services first)
docker-compose up -d
./gradlew bootRun
```

## Architecture

The project follows **Hexagonal Architecture (Ports & Adapters)** with two bounded contexts: `payments_notification` and `purchases_invoices`. There is also a cross-context read model (`Purchase`) that combines data from both contexts via a UNION query.

### Layer Structure

- **`domain/`** — Business logic: use cases (`@Component`), gateway interfaces (ports), entities, value objects. Use cases are the only domain classes with Spring annotations.
- **`application/`** — Adapters: REST controllers (`entrypoint/rest/`), SQS queue listeners (`entrypoint/queue/`), JPA models and repositories (`entrypoint/database/`), Spring Data projections (`entrypoint/database/model/`), gateway implementations (`application/`, named `*Provider`), Response DTOs (`entrypoint/rest/response/`), parsers, converters.
- **`config/`** — Spring configuration beans (SQS client, CORS).

### Key Patterns

- **Gateway/Provider pattern**: `fun interface` defined in `domain/gateway/`, implementations in `application/application/` named as `*Provider`. Controllers never access repositories directly — always go through use cases and gateways.
- **Response DTO with factory method**: Response classes in `entrypoint/rest/response/` use a `companion object` with `from(entity)` to encapsulate domain → response mapping. Controllers use method references: `.map(SomeResponse::from)`.
- **Converter pattern**: `*Converter` classes in `application/converter/` handle bidirectional mapping between domain entities and JPA models (`toModel()`, `toEntity()`).
- **Projection pattern**: Interface projections (e.g., `PurchaseProjection`) in `entrypoint/database/model/` for native SQL queries that combine data from multiple tables. Used with dedicated repositories (e.g., `PurchaseRepository`) and mapped to domain read models (e.g., `Purchase`).
- **Async queue processing**: REST endpoints enqueue messages to SQS; scheduled listeners poll queues every 5 seconds, parse, and persist.
- **`Result<T>` monad**: Use cases and gateways return `Result` for error propagation instead of throwing exceptions.
- **Value Objects**: Domain values like `Cnpj`, `AccessKey`, `InvoiceUrl`, `TotalItems` enforce validation at construction via private constructor + `of()` factory method.

### API Endpoints

- `GET /health` — Health check
- `POST /payments/notification` — Enqueue payment notification text for parsing
- `GET /purchases` — List all purchases (UNION of `payment_notifications` and `purchase_invoices`)
- `POST /purchases/invoice` — Enqueue purchase invoice

### Data Flows

Write (async): `REST Controller → Use Case → SQS Queue → Scheduled Listener → Parser/Converter → Use Case → Provider → JPA Repository → MySQL`

Read (sync): `REST Controller → Use Case → Provider → JPA Repository → Domain Entity → Response DTO`

Read with projection (sync): `REST Controller → Use Case → Provider → JPA Repository (native UNION query) → Projection interface → Domain Read Model → Response DTO`

## Local Development

Docker Compose provides MySQL 8.0 (port 3306) and LocalStack 3.8.1 (port 4566, SQS emulation). SQS queues (`payments-notifications`, `purchases-invoices`) are auto-created by `docker/localstack-init/01-create-sqs.sh`.

Environment variables have sensible defaults for local development in `application.yml`. Key ones: `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`, `AWS_SQS_ENDPOINT`, `AWS_ACCOUNT_ID`.

## Database

Flyway migrations live in `src/main/resources/db/migration/`. Tables: `payment_notifications`, `purchase_invoices`, `purchase_payments`, `purchase_items`. Tests use H2 in-memory database.

## Testing

Tests use **JUnit 5** + **Kotlin Test** + **Mockito**. H2 in-memory database for integration tests. For unit tests, gateways are mocked via lambda `fun interface` syntax (`ListPurchasesGateway { Result.success(data) }`). Mockito is used only for infrastructure components (e.g., `SqsClient`).

## Language

The codebase and git history use **Brazilian Portuguese** for domain terms, commit messages, and variable names related to business concepts. Follow this convention.
