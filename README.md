# FinCore Banking Platform

A production-grade multi-microservice banking platform built to demonstrate **senior/staff-level engineering practices** across the full stack of a modern financial system.

The platform processes payments end-to-end - from JWT-authenticated REST initiation through fraud detection, FX conversion, account ledger updates, and multi-channel notifications - with every design decision grounded in real banking requirements: at-least-once delivery, idempotency, distributed tracing, and compliance-aware audit logging.

---

## Architecture Overview

```
┌─────────────────────────────────────────────────────────────────────┐
│                        Client / External                            │
└───────────────────────────────┬─────────────────────────────────────┘
                                │ HTTPS
                                ▼
┌─────────────────────────────────────────────────────────────────────┐
│  API Gateway  :8080                                                 │
│  Spring Cloud Gateway · JWT validation · Redis rate limiting        │
│  Request logging · distributed trace injection                      │
└──────┬──────────────────────────────────────┬────────────────────────┘
       │ REST                                 │ REST
       ▼                                      ▼
┌──────────────────┐                ┌──────────────────────┐
│  Payment Service │                │  Account Service     │
│  :8082           │                │  :8081               │
│  Orchestrates    │◄───────────────│  Ledger · audit log  │
│  payment flow    │  WebClient     │  Balance management  │
│  Outbox pattern  │                │  Optimistic locking  │
└────────┬─────────┘                └──────────────────────┘
         │                                      ▲
         │ Kafka (Avro)                         │ Kafka (Avro)
         ▼                                      │
┌──────────────────┐                ┌──────────────────────┐
│  Fraud Detection │                │  FX Service  :8084   │
│  Service  :8083  │                │  ExchangeRate agg    │
│  Strategy Pattern│                │  Provider fallback   │
│  rule engine     │                │  chain + CB          │
│  FraudCase agg   │                │  ECB XML fallback    │
└────────┬─────────┘                └──────────────────────┘
         │                                      │
         │ Kafka (Avro)                         │ Kafka (Avro)
         ▼                                      ▼
┌─────────────────────────────────────────────────────────────────────┐
│  Notification Service  :8086                                        │
│  Strategy Pattern channel routing · Email · Push (FCM) · SMS        │
│  Thymeleaf templates · Outbox persistence · Per-channel retry       │
└─────────────────────────────────────────────────────────────────────┘
```

All inter-service Kafka messages are serialized with **Apache Avro** and validated against **Confluent Schema Registry** at both producer and consumer sides.

---

## Services

### API Gateway `:8080`
Entry point for all client traffic. Built on Spring Cloud Gateway (reactive, non-blocking).

- JWT validation via Keycloak (OAuth2 Resource Server) - tokens verified at the gateway, services trust the forwarded identity
- Redis-backed token bucket rate limiting per client IP and per JWT subject
- Distributed trace context injected into every downstream request (`X-B3-TraceId`, `traceparent`)
- Request/response logging filter with configurable body capture (off by default in prod)

### Account Service `:8081`
Owns the account aggregate and the financial ledger. No other service writes account balances directly.

- `Account` aggregate with optimistic locking (`@Version`) - concurrent debit/credit conflicts detected at DB level
- `AuditLog` entity captures every balance mutation with `changedBy`, `previousBalance`, `newBalance`, `reason`
- Flyway-managed schema with explicit index strategy (composite index on `owner_id + status` for account lookup)
- Avro events published: `AccountCreated`, `AccountDebited`, `AccountCredited`, `AccountFrozen`

### Payment Service `:8082`
Orchestrates the full payment lifecycle. The most complex service - coordinates Account Service, Fraud Detection, and FX Service.

- Hexagonal architecture with explicit ports and adapters - domain layer has zero infrastructure imports
- `Payment` aggregate tracks state transitions: `PENDING -> PROCESSING -> COMPLETED | FAILED | REJECTED_FRAUD`
- **Transactional Outbox Pattern** - domain events written to `outbox_messages` in the same DB transaction as the payment; `OutboxPoller` publishes to Kafka asynchronously
- **Idempotency** via `idempotency_key` (SHA-256 of client-provided key) - duplicate requests return the original response without re-processing
- **Distributed locking** via Redisson - prevents concurrent processing of the same payment across pod replicas
- FX conversion result (`convertedAmount`, `fxConversionId`) stored on the aggregate - ensures the correct converted amount is credited to the target account and that the applied rate is durably recorded per payment
- Resilience4j circuit breakers on Account Service and FX Service calls with independent retry policies
- Avro events published: `PaymentInitiated`, `PaymentCompleted`, `PaymentFailed`, `PaymentFraudRejected`, `PaymentCancelled`

### Fraud Detection Service `:8083`
Consumes `PaymentInitiated` events and applies a rule engine to decide approve / block / escalate.

- **Strategy Pattern** rule engine - 7 fraud rules as independent `FraudRule` implementations, each producing a `RuleResult` with a weighted score
- Short-circuit logic: rules flagged as `CRITICAL` exit evaluation immediately on trigger (e.g. sanctions list match)
- `FraudCase` aggregate lifecycle: `UNDER_REVIEW -> APPROVED | BLOCKED | ESCALATED -> CONFIRMED`
- Redis-backed fraud context enrichment (velocity checks, pattern cache) - cache-aside with TTL per risk category
- Avro events published: `FraudCaseApproved`, `FraudCaseBlocked`, `FraudCaseEscalated`, `FraudConfirmed`

### FX Service `:8084`
Manages exchange rates and executes currency conversions.

- `ExchangeRate` and `FxConversion` aggregates - `BigDecimal` with explicit scale (`scale=8` for rates, `scale=4` for amounts)
- **Provider fallback chain**: primary REST provider (with circuit breaker) -> ECB XML feed fallback - if both fail, the conversion fails fast rather than using a stale rate
- `currencyPair` stored as `"EUR/PLN"` string rather than enum - forward-compatible schema evolution
- Avro events published: `ExchangeRatePublished`, `FxConversionExecuted`, `FxConversionFailed` (with `FxFailureCategory` enum: `RATE_UNAVAILABLE`, `CIRCUIT_BREAKER_OPEN`, `PROVIDER_ERROR`, `AMOUNT_TOO_LARGE`)

### Notification Service `:8086`
Delivers multi-channel notifications triggered by payment and account events.

- Consumes: `PaymentCompleted`, `PaymentFailed`, `PaymentFraudRejected`, `AccountDebited`, `AccountCredited`, `AccountFrozen`
- **Strategy Pattern** channel routing - `NotificationChannelRouter` maps each event type to a set of channels; adding a new channel requires only a new `ChannelSender @Component`
- Channel routing policy is a **domain rule**, not infrastructure config: fraud alerts go Email + Push + SMS; account debits go Push only (high frequency, email would be noise)
- One `Notification` aggregate per channel per event - independent retry, independent delivery status
- Contact details (email, phone, FCM token) snapshotted at notification creation time - GDPR compliance: the contact used is the one in effect at notification time
- Outbox pattern for delivery: `NotificationOutboxPoller` dispatches PENDING/FAILED notifications, retries up to 5 times before DEAD_LETTER
- Thymeleaf HTML email templates per notification type; plain text body for SMS (auto-truncated to 155 chars for single GSM-7 segment)
- Channels: **Email** via JavaMailSender (SMTP/SendGrid), **Push** via Firebase Cloud Messaging, **SMS** via Twilio

---

## Technical Stack

| Layer | Technology                                                    |
|---|---------------------------------------------------------------|
| Language | Java 25                                                       |
| Framework | Spring Boot 4.0.3 · Spring Framework 7                        |
| API Gateway | Spring Cloud Gateway 2025.0.0                                 |
| Messaging | Apache Kafka · Avro 1.12.0 · Confluent Schema Registry        |
| Persistence | PostgreSQL 16 · Spring Data JPA · Flyway                      |
| Caching / Locking | Redis 7 · Redisson 3.43.0                                     |
| Security | Keycloak 24 · OAuth2 · JWT                                    |
| Resilience | Resilience4j 2.3.0 (circuit breaker, retry, time limiter)     |
| Observability | Micrometer · Prometheus · Grafana · Grafana Tempo (OTLP)      |
| Email | Spring Mail · Thymeleaf templates                             |
| Push | Firebase Admin SDK 9.4.2 (FCM)                                |
| SMS | Twilio 10.6.4                                                 |
| Testing | JUnit 5 · Mockito · Testcontainers 2.0 · WireMock 3.13.0      |
| Build | Maven 3.9+ · multi-module with parent BOM                     |
| Container | Docker · Docker Compose · Kubernetes (K8s manifests included) |

---

## Project Structure

```
fincore/
├── pom.xml                          # Parent BOM - all dependency versions, shared plugin config
│
├── api-gateway/                     # :8080 - routing, JWT, rate limiting
├── account-service/                 # :8081 - account aggregate, ledger, audit
├── payment-service/                 # :8082 - payment orchestration, outbox, idempotency
├── fraud-detection-service/         # :8083 - rule engine, FraudCase aggregate
├── fx-service/                      # :8084 - exchange rates, conversion, provider fallback
├── notification-service/            # :8086 - Email/Push/SMS, outbox, channel strategy
│
├── infrastructure/
│   ├── docker-compose.yml           # Full local stack
│   ├── keycloak/
│   │   └── realm-export.json        # Pre-configured fincore realm, clients, roles
│   └── observability/
│       ├── prometheus.yml
│       └── tempo.yaml
│      
│
└── k8s/
    ├── base/                        # Namespace, NetworkPolicy
    ├── kafka/                       # Kafka + Zookeeper StatefulSets
    ├── schema-registry/             # Schema Registry Deployment + ConfigMaps
    └── services/                    # Per-service Deployments, Services, ConfigMaps
```

Each microservice follows the same internal layout:

```
<service>/
├── src/main/avro/                   # Avro schemas (.avsc) for events published by this service
└── src/main/java/com/fincore/<service>/
    ├── domain/
    │   ├── model/                   # Aggregates, value objects, domain events
    │   ├── port/
    │   │   ├── in/                  # Use case interfaces (primary ports)
    │   │   └── out/                 # Repository, client interfaces (secondary ports)
    │   └── service/                 # Pure domain services (no Spring annotations)
    ├── application/                 # Use case implementations (@Service, @Transactional)
    ├── adapter/
    │   ├── in/
    │   │   ├── web/                 # REST controllers, DTOs, GlobalExceptionHandler
    │   │   └── messaging/           # Kafka @KafkaListener consumers
    │   └── out/
    │       ├── persistence/         # JPA adapters, mappers, Spring Data repositories
    │       ├── client/              # WebClient adapters for downstream services
    │       └── messaging/           # Kafka producers, Avro mappers
    └── infrastructure/
        ├── config/                  # Spring @Configuration classes
        ├── messaging/               # OutboxPoller, serializer config
        └── persistence/
            ├── entity/              # @Entity classes
            └── repository/          # Spring Data JPA interfaces
```

---

## Kafka Topics

| Topic | Publisher | Consumer(s) | Description |
|---|---|---|---|
| `fincore.payments.payment-initiated` | payment-service | fraud-detection-service | Triggers fraud evaluation |
| `fincore.payments.payment-completed` | payment-service | notification-service | Payment success notification |
| `fincore.payments.payment-failed` | payment-service | notification-service | Payment failure notification |
| `fincore.payments.payment-fraud-rejected` | payment-service | notification-service | Security alert notification |
| `fincore.fraud.case-approved` | fraud-detection-service | payment-service | Resume payment processing |
| `fincore.fraud.case-blocked` | fraud-detection-service | payment-service | Reject and refund payment |
| `fincore.fraud.case-escalated` | fraud-detection-service | payment-service | Hold for manual review |
| `fincore.fraud.confirmed` | fraud-detection-service | payment-service | Manual review outcome |
| `fincore.fx.rate-published` | fx-service | *(analytics)* | Rate snapshot for downstream |
| `fincore.fx.conversion-executed` | fx-service | payment-service | Conversion result applied to payment |
| `fincore.fx.conversion-failed` | fx-service | payment-service | Triggers payment failure |
| `fincore.accounts.account-debited` | account-service | notification-service | Push notification trigger |
| `fincore.accounts.account-credited` | account-service | notification-service | Push notification trigger |
| `fincore.accounts.account-frozen` | account-service | notification-service | All-channel security alert |

All topics use **Avro serialization** with schema validation enforced by Confluent Schema Registry. Schema evolution is controlled with `auto.register.schemas=false` in production - schemas must be pre-registered and must be **BACKWARD** compatible.

---

## Design Patterns in Use

**Hexagonal Architecture (Ports & Adapters)** - the domain layer in every service has zero infrastructure imports. Business logic is testable without a Spring context, without a database, without Kafka.

**Transactional Outbox Pattern** (payment-service, notification-service) - domain events are written to an `outbox_messages` table in the same JDBC transaction as the aggregate mutation. A background `OutboxPoller` publishes them to Kafka. Guarantees at-least-once delivery without distributed transactions (no 2PC).

**Strategy Pattern** (fraud-detection-service, notification-service) - fraud rules and channel senders are pluggable `@Component` implementations of a shared interface. Adding a new fraud rule or a new notification channel requires zero changes to the orchestrating service.

**Aggregate Pattern** - `Payment`, `FraudCase`, `ExchangeRate`, `FxConversion`, `Account`, and `Notification` are proper DDD aggregates: they encapsulate invariant enforcement, record domain events internally, and expose state only through methods, not public setters.

---

## Local Development

### Prerequisites

- Docker Desktop (or Docker Engine + Compose plugin)
- Java 25 JDK (e.g. via [SDKMAN](https://sdkman.io): `sdk install java 25-open`)
- Maven 3.9+

### 1. Start the infrastructure stack

```bash
cd infrastructure
docker compose up -d
```

This starts: PostgreSQL (×5, one per service), Redis, Kafka, Zookeeper, Schema Registry, Keycloak, Prometheus, Grafana, Tempo, Mailpit.

Wait for all services to be healthy:

```bash
docker compose ps
```

### 2. Verify infrastructure

| Service | URL | Credentials       |
|---|---|-------------------|
| Kafka UI | http://localhost:8090 | -                 |
| Schema Registry | http://localhost:8081 | -                 |
| Keycloak Admin | http://localhost:8180 | `admin` / `admin` |
| Prometheus | http://localhost:9090 | -                 |
| Grafana | http://localhost:3000 | `admin` / `admin` |
| Mailpit (email preview) | http://localhost:8025 | -                 |

### 3. Build all services

```bash
mvn clean install -DskipTests
```

### 4. Run a service locally

```bash
cd payment-service
mvn spring-boot:run
```

Each service connects to its own PostgreSQL instance. Flyway migrations run automatically on startup.

### 5. Run tests

```bash
# Unit tests only (fast, no Docker required)
mvn test

# Unit + integration tests (Testcontainers spins up real Postgres and Kafka)
mvn verify
```

---

## Ports Reference

| Service | HTTP | Database |
|---|---|----------|
| API Gateway | 8080 | -        |
| Account Service | 8081 | 5432     |
| Payment Service | 8082 | 5433     |
| Fraud Detection Service | 8083 | 5434     |
| FX Service | 8084 | 5435     |
| Notification Service | 8086 | 5436     |
| Kafka | 9092 (external) / 29092 (internal) | -        |
| Schema Registry | 8081 | -        |
| Keycloak | 8180 | -        |
| Redis | 6379 | -        |

---

## Observability

All services export metrics, logs, and traces in a unified format.

**Metrics** - Micrometer -> Prometheus scrape -> Grafana dashboards. Each service exposes `/actuator/prometheus`. Custom counters on all business-critical paths: payments initiated/completed/failed, fraud rule evaluations, FX provider fallback activations, notification delivery per channel.

**Distributed Tracing** - Micrometer Tracing with OpenTelemetry bridge -> Grafana Tempo. Every Kafka message carries a `traceparent` header. Trace IDs appear in all log lines via MDC: `[traceId/spanId]`. Tempo is pre-wired in Grafana - click a trace ID anywhere to jump to the full distributed trace.

**Structured Logging** - all services use a consistent log pattern:
```
2025-03-08 14:22:01.543 [http-nio-8082-exec-3] [abc123/def456] INFO  PaymentController - Payment initiated: paymentId=pay-789
```

**Health checks** - `/actuator/health/liveness` and `/actuator/health/readiness` enabled on all services, wired to Kubernetes probes.

**Circuit breaker state** - `/actuator/circuitbreakers` exposed and scraped by Prometheus. Grafana shows open/half-open/closed state per instance over time.

---

## Security Model

Authentication is centralized at the **API Gateway** - downstream services trust the gateway and validate JWT signatures independently using the Keycloak JWKS endpoint.

- All public endpoints are blocked at the gateway by default. Only whitelisted paths are forwarded.
- JWT claims carry `sub` (userId), `roles`, and `account_ids` (accounts the user owns). Services extract these from the security context.
- Keycloak realm (`fincore`) is pre-configured and imported automatically on first startup via `realm-export.json`. It includes the `payment-client` confidential client, `USER` and `ADMIN` roles, and a test user.
- Services run with minimal Keycloak permissions. Only the gateway holds a client secret - downstream services use public key verification only.

---

## Database Strategy

Each service owns its data exclusively - no shared tables, no cross-service joins. This is enforced by separate PostgreSQL instances, not just by convention.

**Schema management** - Flyway with versioned migrations (`V1__`, `V2__`, ...). `ddl-auto: validate` in all services - Hibernate validates against the Flyway-managed schema on startup and fails fast on mismatch.

**Optimistic locking** - `@Version` column on all aggregates subject to concurrent updates (`Payment`, `Account`, `Notification`). Concurrent modification throws `ObjectOptimisticLockingFailureException` at the JPA layer, caught and retried by the application.

**Outbox table** - `payment-service` and `notification-service` include an `outbox_messages` table co-located with the aggregate table. The poller selects `WHERE status = 'PENDING' ORDER BY created_at ASC LIMIT :batchSize` - a partial index on `status` ensures this query is O(pending count), not O(total messages).

---

## Kubernetes

K8s manifests under `k8s/` deploy the full platform to any Kubernetes cluster (tested on k3s and GKE).

```bash
kubectl apply -f k8s/base/
kubectl apply -f k8s/kafka/
kubectl apply -f k8s/schema-registry/
kubectl apply -f k8s/services/
```

Notable design decisions:

- **NetworkPolicy** - each service pod only allows inbound traffic from the API Gateway and from Kafka. Direct pod-to-pod communication between services is blocked at the network level.
- **Kafka topics** created by an init `Job` (`kafka-topics-init`) before any service starts - topics are not auto-created in production; partition count and replication factor are explicit.
- **Schema Registry** schemas pre-registered via a ConfigMap-backed init container - `auto.register.schemas=false` in all service configs.
- **Secrets** for DB credentials, Keycloak client secret, Twilio auth token, and Firebase credentials are expected as Kubernetes Secrets (not committed to the repository).
- **Graceful shutdown** - all services use `server.shutdown: graceful` with a 30s termination grace period. In-flight requests complete before the pod terminates.

---

## Key Engineering Decisions

**Why one Notification per channel, not one aggregate with a list of channels?**
Each channel has independent retry semantics - if Email delivery fails, SMS should not be retried alongside it. Separate aggregates give independent `PENDING -> SENT | FAILED -> DEAD_LETTER` lifecycle per channel, which is correct for audit and for operations alerting.

**Why Outbox Pattern instead of Kafka transactions (exactly-once)?**
Kafka transactions require the Kafka producer and the DB write to participate in the same transaction - impossible across JDBC and Kafka without XA. The outbox pattern achieves the same guarantee using only a local JDBC transaction, with Kafka as a dumb transport.

**Why store FX conversion result on the Payment aggregate?**
`ProcessPaymentService` calls FX Service, receives `convertedAmount`, and must use it to credit the target account. Without storing it on the aggregate, the system has no durable record of what rate was applied to a specific payment - unacceptable in a regulated environment. `payment.lockFxConversion(convertedAmount, conversionId)` enforces this invariant at the domain level.

**Why Redis for fraud context enrichment rather than a second DB query?**
Velocity checks (e.g. "how many payments from this account in the last 5 minutes") require aggregated data across recent events, not a point-in-time row lookup. Redis sorted sets and counters with TTL are the natural fit. The fraud DB holds the durable `FraudCase` record; Redis holds the ephemeral scoring context.

**Why Avro over JSON for Kafka?**
Schema Registry enforces compatibility contracts - a producer cannot publish a schema that would break existing consumers. This is a hard requirement in a multi-team banking environment. Avro's binary encoding also reduces message size by roughly 60% compared to JSON for typical payment events, which matters at volume.