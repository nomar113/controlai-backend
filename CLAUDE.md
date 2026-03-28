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

The project follows **Hexagonal Architecture (Ports & Adapters)** with two bounded contexts: `payments_notification` and `purchases_invoices`.

### Layer Structure

- **`domain/`** — Pure business logic: use cases, gateway interfaces (ports), entities, value objects. No Spring dependencies.
- **`application/`** — Adapters: REST controllers (`entrypoint/rest/`), SQS queue listeners (`entrypoint/queue/`), JPA models and repositories (`entrypoint/database/`), gateway implementations (Providers), parsers, converters.
- **`config/`** — Spring configuration beans (SQS client, CORS).

### Key Patterns

- **Gateway/Provider pattern**: Interfaces defined in `domain/gateway/`, implementations in `application/application/` named as `*Provider`.
- **Async queue processing**: REST endpoints enqueue messages to SQS; scheduled listeners poll queues every 5 seconds, parse, and persist.
- **`Result<T>` monad**: Use cases return `Result` for error propagation instead of throwing exceptions.
- **Value Objects**: Domain values like `Cnpj`, `AccessKey`, `InvoiceUrl` enforce validation at construction.

### API Endpoints

- `GET /health` — Health check
- `POST /payments/notification` — Enqueue payment notification text for parsing
- `GET /purchases` — List all purchase invoices
- `POST /purchases/invoice` — Enqueue purchase invoice

### Data Flow

`REST Controller → SQS Queue → Scheduled Listener → Parser/Converter → JPA Repository → MySQL`

## Local Development

Docker Compose provides MySQL 8.0 (port 3306) and LocalStack 3.8.1 (port 4566, SQS emulation). SQS queues (`payments-notifications`, `purchases-invoices`) are auto-created by `docker/localstack-init/01-create-sqs.sh`.

Environment variables have sensible defaults for local development in `application.yml`. Key ones: `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`, `AWS_SQS_ENDPOINT`, `AWS_ACCOUNT_ID`.

## Database

Flyway migrations live in `src/main/resources/db/migration/`. Tables: `payment_notifications`, `purchase_invoices`, `purchase_payments`, `purchase_items`. Tests use H2 in-memory database.

## Language

The codebase and git history use **Brazilian Portuguese** for domain terms, commit messages, and variable names related to business concepts. Follow this convention.
