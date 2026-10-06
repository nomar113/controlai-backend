# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

Controlaí is a payment notification and purchase invoice management backend built with **Kotlin 1.9.25**, **Spring Boot 3.5.7**, and **Gradle**. It uses MySQL 8.0, AWS SQS (via LocalStack for local dev), and Flyway for migrations. Java 21 is required.

## Common Commands

```bash
# Build
./gradlew build

# Run tests (only needs Docker running; no docker-compose services required)
./gradlew test

# Run tests with the class order reversed (detects order-dependent tests)
./gradlew test -PtestClassOrder=br.com.nomar.controlai.config.ReverseClassNameOrderer

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
- **`application/`** — Adapters: REST controllers (`entrypoint/rest/`), SQS queue listener for payment notifications (`payments_notification/entrypoint/queue/`), JPA models and repositories (`entrypoint/database/`), Spring Data projections (`entrypoint/database/model/`), gateway implementations (`application/`, named `*Provider`), Response DTOs (`entrypoint/rest/response/`), parsers, converters.
- **`config/`** — Spring configuration beans (SQS client, CORS).

### Key Patterns

- **Gateway/Provider pattern**: `fun interface` defined in `domain/gateway/`, implementations in `application/application/` named as `*Provider`. Controllers never access repositories directly — always go through use cases and gateways.
- **Response DTO with factory method**: Response classes in `entrypoint/rest/response/` use a `companion object` with `from(entity)` to encapsulate domain → response mapping. Controllers use method references: `.map(SomeResponse::from)`.
- **Converter pattern**: `*Converter` classes in `application/converter/` handle bidirectional mapping between domain entities and JPA models (`toModel()`, `toEntity()`).
- **Projection pattern**: Interface projections (e.g., `PurchaseProjection`) in `entrypoint/database/model/` for native SQL queries that combine data from multiple tables. Used with dedicated repositories (e.g., `PurchaseRepository`) and mapped to domain read models (e.g., `Purchase`).
- **Async queue processing**: only payment notifications go through SQS (`payments-notifications`): `POST /payments/notification` enqueues the raw text and `PaymentNotificationQueueListener` polls the queue every 5 seconds, parses, and persists. Purchase invoices are written synchronously (`POST /purchases/invoices/pending`); the old `purchases-invoices` queue and `POST /purchases/invoice` were removed.
- **`Result<T>` monad**: Use cases and gateways return `Result` for error propagation instead of throwing exceptions.
- **Value Objects**: Domain values like `AccessKey` and `InvoiceUrl` enforce validation at construction via private constructor + `of()` factory method.

### API Endpoints

- `GET /health` — Health check
- `POST /payments/notification` — Enqueue payment notification text for parsing
- `GET /purchases` — List all purchases (UNION of `payment_notifications` and `purchase_invoices`)
- `POST /purchases/invoices/pending` — Register a scanned NFC-e QR Code as a `PENDING` invoice (sync; `201`, `400` invalid QR, `409` duplicate in the group)

### Data Flows

Write (async, payment notifications only): `REST Controller → Use Case → SQS Queue → Scheduled Listener → Parser → Use Case → Provider → JPA Repository → MySQL`

Write (sync): `REST Controller → Use Case → Provider → JPA Repository → MySQL`

Read (sync): `REST Controller → Use Case → Provider → JPA Repository → Domain Entity → Response DTO`

Read with projection (sync): `REST Controller → Use Case → Provider → JPA Repository (native UNION query) → Projection interface → Domain Read Model → Response DTO`

## Local Development

Docker Compose provides MySQL 8.0 (port 3306) and LocalStack 3.8.1 (port 4566, SQS emulation). The SQS queue `payments-notifications` is auto-created by `docker/localstack-init/01-create-sqs.sh`.

Environment variables have sensible defaults for local development in `application.yml`. Key ones: `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`, `AWS_SQS_ENDPOINT`, `AWS_ACCOUNT_ID`.

## Database

Flyway migrations live in `src/main/resources/db/migration/`. Tables: `payment_notifications`, `purchase_invoices`, `purchase_payments`, `purchase_items`.

## Testing

Tests use **JUnit 5** + **Kotlin Test** + **Mockito**. For unit tests, gateways are mocked via lambda `fun interface` syntax (`ListPurchasesGateway { Result.success(data) }`). Mockito is used only for infrastructure components (e.g., `SqsClient`).

Integration tests run against a disposable MySQL 8.0 started by **Testcontainers** (JDBC URL `jdbc:tc:mysql:8.0:///controlai`), created on each `./gradlew test` run with only the Flyway migrations applied. They only need Docker running: they do **not** use the docker-compose MySQL, and never touch the local `controlai` database. The datasource is set by `TestcontainersDatasourcePostProcessor` (test-only `EnvironmentPostProcessor`, registered in `src/test/resources/META-INF/spring.factories`) as the highest-precedence property source, because `./application.yml` and a `DB_URL` env var outrank `src/test/resources/application.properties`. It applies to any runner (Gradle or IDE).

- **Cleanup:** wipe business data with `TestDatabaseCleaner.deleteFinancialData()` (deletes in FK order). Never write an unscoped `DELETE FROM <table>` in a test class; a test may only delete its own rows directly (filtered by `group_id`, e-mail, id...).
- **No ambient data:** create the rows a test needs in its own setup; don't rely on rows left by migrations or by other test classes (the suite must pass in any class order).
- **Migration tests:** filter `information_schema` queries by `table_schema = DATABASE()`.

## Language

The codebase and git history use **Brazilian Portuguese** for domain terms, commit messages, and variable names related to business concepts. Follow this convention.
