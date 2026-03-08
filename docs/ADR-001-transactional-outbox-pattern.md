# ADR-001: Transactional Outbox Pattern for Reliable Event Publishing

## Context

payment-service and notification-service must publish domain events to Kafka after mutating their aggregates. The naive
approach - saving to DB, then publishing to Kafka in sequence - is broken by design:

```
// BROKEN - do not do this
paymentRepository.save(payment);          // (1) DB write succeeds
kafkaTemplate.send("payment-initiated");  // (2) Kafka publish FAILS
// Result: payment saved in DB, no Kafka event - downstream services never notified
```

The reverse ordering has the same problem: Kafka publish succeeds, DB write fails - event published for a payment that
doesn't exist.

The only way to make both atomic is a **distributed transaction** (XA / 2PC across JDBC and Kafka). XA transactions are
supported by neither Confluent Kafka nor most cloud-managed brokers. They are also operationally complex, slow, and
contraindicated for microservices.

---

## Decision

Implement the **Transactional Outbox Pattern** in payment-service and notification-service.

**Write side:** domain events are written to an `outbox_messages` table in the same local JDBC transaction as the
aggregate mutation. Both succeed or both roll back - atomicity guaranteed by the DB.

```sql
BEGIN;
UPDATE payments
SET status = 'COMPLETED'
WHERE id = :id; -- aggregate mutation
INSERT INTO outbox_messages (topic, payload, status) -- event record
VALUES ('fincore.payments.payment-completed', :payload, 'PENDING');
COMMIT;
-- Both rows are now durable in PostgreSQL.
-- Kafka is not involved in this transaction.
```

**Read side (`OutboxPoller`):** a `@Scheduled` component polls `outbox_messages WHERE status = 'PENDING'`, publishes
each message to Kafka, and marks it `SENT` - in a `REQUIRES_NEW` transaction per message.

```
OutboxPoller (every 500ms)
  -> SELECT ... WHERE status = 'PENDING' LIMIT 50
  -> for each row:
      KafkaTemplate.send(topic, payload).get()  // synchronous - wait for broker ACK
      UPDATE outbox_messages SET status = 'SENT' WHERE id = :id
```

**Transaction design - why `REQUIRES_NEW` per message, not one batch transaction:**  
A single Kafka failure must not roll back the entire batch of DB updates. Each message gets its own DB transaction -
failure on message N does not affect messages 1..N-1.

---

## Alternatives Considered

### Option A: Kafka Transactions (exactly-once)

Kafka's transactional producer (`transactional.id`, `isolation.level=read_committed`) provides exactly-once semantics
within Kafka. However, it does not span JDBC - it cannot atomically commit a DB write and a Kafka publish.

**Rejected:** Does not solve the problem. Still requires XA to coordinate with JDBC.

### Option B: Change Data Capture (Debezium)

Debezium monitors the PostgreSQL WAL and publishes every INSERT/UPDATE to Kafka. The outbox table becomes the source of
truth; Debezium reads it and forwards events without any application-level poller.

**Advantages:** No polling overhead. Truly event-driven at the infrastructure level. CDC is the canonical outbox
implementation in large-scale systems.

**Rejected for this project:** Requires running Debezium as a separate Kafka Connect deployment - adds operational
complexity disproportionate to a portfolio project. The application-level poller achieves the same correctness guarantee
and is significantly easier to reason about, test, and operate. In production at scale, CDC would be the right call.

### Option C: Dual Write with Compensation

Write to DB, attempt Kafka publish, if Kafka fails write a "publish pending" flag and retry via a background job.

**Rejected:** This is the outbox pattern with worse ergonomics and no transactional guarantee. Same latency, more code,
harder to reason about.

---

## Consequences

**Positive:**

- Atomic consistency between DB and Kafka with no distributed transaction
- At-least-once delivery guaranteed - poller retries until broker ACK received
- Failure modes are simple: broker down -> outbox accumulates -> drains when broker recovers
- Testable in isolation - `OutboxPollerTest` uses real Postgres, no Kafka needed to verify DB writes

**Negative:**

- At-least-once, not exactly-once - consumers must be idempotent (they are: `IdempotencyGuard` in payment-service,
  `UNIQUE (correlation_event_id, channel)` in notification-service)
- Small latency overhead: poller fires every 500ms -> events published up to 500ms after commit (acceptable for this
  domain)
- Outbox table accumulates rows - must be cleaned up. A `@Scheduled` cleanup job deletes `SENT` rows older than 7 days (
  retention policy matches Kafka topic retention)
- Poller is a single-threaded polling loop - not a bottleneck at current scale, but would need partitioned polling (e.g.
  by aggregate type) for very high throughput

---

## Implementation Notes

**Partial index for poller performance:**

```sql
CREATE INDEX idx_outbox_pending ON outbox_messages (created_at ASC)
    WHERE status = 'PENDING';
```

This index contains only PENDING rows - as SENT rows accumulate, the index stays small. The poller query
`WHERE status = 'PENDING' ORDER BY created_at ASC LIMIT 50` is O(pending count), not O(total outbox size).

**`SELECT FOR UPDATE SKIP LOCKED`:**
Each `REQUIRES_NEW` transaction uses `SKIP LOCKED` to prevent multiple poller instances (if scaled horizontally) from
processing the same row:

```sql
SELECT *
FROM outbox_messages
WHERE status = 'PENDING'
ORDER BY created_at ASC
LIMIT 50 FOR UPDATE SKIP LOCKED;
```

**Dead letter handling:**
After `max_retry_count` (configurable, default 5) failed publish attempts, the row is marked `DEAD_LETTER`. A Prometheus
alert fires. Ops team manually inspects and replays via the `/actuator/outbox/replay` endpoint.