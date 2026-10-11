# @agricontract/eventing

Shared Node.js eventing foundation for the Node services (`analytics`, `pricing`, `notification`, `file`).
It is the Node equivalent of what the Java services do with `DomainEvent` + `OutboxPoller` + consumer idempotency.

| Piece | What it guarantees | Source |
|---|---|---|
| **Event envelope** | Same fields, required set, nullability and `additionalProperties: false` as the frozen contract `contracts/events/event-envelope.yaml` | event-envelope.yaml |
| **Transactional outbox** | Event is persisted in the same DB transaction as the business change; the poller publishes only committed rows and marks them `PUBLISHED` only after the broker confirms | Java `OutboxPoller` / `OutboxMonitor` |
| **Consumer dedupe** | Each consumer processes an `eventId` at most once; redelivery and DLQ replay of the original `eventId` are no-ops | Verification Matrix #7, data-governance §8b |

Stack: MySQL 8 (`mysql2`) and RabbitMQ (`amqplib`), the same infra as the Java services. Both are optional
peer dependencies: the service creates the pool/channel and passes it in.

## Install the tables (additive migration)

Copy `migrations/V1__create_eventing_tables.sql` into the service's own migration folder and renumber it.
It only creates `event_outbox` and `processed_event` (`CREATE TABLE IF NOT EXISTS`) and changes nothing that already exists.

## Produce an event

```ts
import mysql from 'mysql2/promise';
import amqp from 'amqplib';
import {
  createEnvelope, Outbox, OutboxPoller,
  MysqlOutboxStore, MysqlTransactionRunner, AmqpConfirmPublisher,
} from '@agricontract/eventing';

const pool = mysql.createPool({ uri: process.env.DATABASE_URL });
const tx = new MysqlTransactionRunner(pool);
const outboxStore = new MysqlOutboxStore();
const outbox = new Outbox(outboxStore);

// 1) business change + event in ONE transaction (never publish directly)
await tx.inTransaction(async (conn) => {
  await conn.query('INSERT INTO price_history (...) VALUES (...)', [/* ... */]);
  await outbox.enqueue(conn, createEnvelope({
    eventType: 'pricing.reference_price_ingested',
    producer: 'pricing-service',
    aggregateId: 'COFFEE:DAK_LAK',
    correlationId: jobRunId,          // required for request/saga/scheduled-workflow events
    payload: { commodity: 'COFFEE', province: 'DAK_LAK', price: '120000.00' },
  }));
});

// 2) once at startup: the poller publishes committed rows (routing key = eventType)
const connection = await amqp.connect(process.env.AMQP_URL!);
const poller = new OutboxPoller({
  store: outboxStore,
  transactions: tx,
  publisher: new AmqpConfirmPublisher(await connection.createConfirmChannel()),
  exchange: 'agricontract.pricing',
});
poller.start();          // await poller.stop() on shutdown
```

For an event caused by another one, use `deriveEnvelope(cause, {...})`: it sets `causationId = cause.eventId`
and carries the `correlationId`. Never invent a `causationId` for a root event.

## Consume an event exactly once

```ts
import { IdempotentConsumer, MysqlProcessedEventStore, consumeEvents } from '@agricontract/eventing';

const consumer = new IdempotentConsumer({
  consumerName: 'analytics.dim-contract',   // stable name, part of the dedupe key
  transactions: tx,
  processedEvents: new MysqlProcessedEventStore(),
  handler: async (envelope, conn) => {
    // use `conn`: these writes commit atomically with the dedupe marker
    await conn.query('INSERT INTO dim_contract (...) VALUES (...) ON DUPLICATE KEY UPDATE ...', [/* ... */]);
  },
});

const channel = await connection.createChannel();
await channel.prefetch(10);
await consumeEvents({ channel, queue: 'analytics-svc.contract.signed', consumer });
```

Declare the queue with `x-dead-letter-exchange` (same pattern as the Java `RabbitMQConfig`). Delivery handling:

| Situation | Result |
|---|---|
| Processed, or already processed (`DUPLICATE`) | `ack` |
| Invalid JSON / envelope off-contract (`InvalidEnvelopeError`) or handler throws `NonRetryableError` | `nack(requeue=false)` → DLQ immediately |
| Transient error | retried 3 times with backoff 1s, 2s (max 4s), then DLQ |

The marker and the handler's DB writes share a transaction, so a failed attempt leaves no marker, and a later DLQ
replay of the **original** `eventId` is processed once. Side effects outside the DB (emails, HTTP calls) must also
use their own semantic key, e.g. notification `(event_id, recipient_email, notification_type)`.

## Delivery semantics

At-least-once. If the service crashes after the broker confirmed but before the row is marked `PUBLISHED`,
the event is published again with the **same** `eventId`, and consumers drop it through dedupe.
The poller locks rows with `FOR UPDATE SKIP LOCKED`, so several instances can run without double-publishing.
It stops a batch at the first broker failure to keep per-service order. Rows stuck `PENDING` for more than
5 minutes are logged by the built-in monitor, like the Java `OutboxMonitor`.

## Develop

```bash
npm install
npm run typecheck
npm test          # unit tests + contract test against contracts/events/event-envelope.yaml
npm run build
```
