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
await channel.prefetch(10); // required, see "Queue setup" below
await consumeEvents({ channel, queue: 'analytics-svc.contract.signed', consumer });
```

### Queue setup (required for every consumer)

`consumeEvents` does not declare queues or set `prefetch`; the service does it once at startup, the same way the
Java `RabbitMQConfig` does:

- **`prefetch`**: always call `channel.prefetch(n)` before `consumeEvents`. Without it RabbitMQ pushes every
  ready message at once, and each one may sit in the retry backoff, so the number of messages in flight is unbounded.
  Pick a small number (10–20); it bounds memory and DB load while a backlog drains.
- **Dead-lettering**: declare each queue with `x-dead-letter-exchange`, plus a durable DLQ bound to that exchange
  with the same routing key. Use the Java names: queue `<service>-svc.<routingKey>`, DLQ `<queue>.dlq`.

```ts
const dlx = 'agricontract.analytics.dlx';
await channel.assertExchange('agricontract.contracts', 'topic', { durable: true, autoDelete: false });
await channel.assertExchange(dlx, 'direct', { durable: true, autoDelete: false });
await channel.assertQueue('analytics-svc.contract.signed', { durable: true, arguments: { 'x-dead-letter-exchange': dlx } });
await channel.assertQueue('analytics-svc.contract.signed.dlq', { durable: true });
await channel.bindQueue('analytics-svc.contract.signed', 'agricontract.contracts', 'contract.signed');
await channel.bindQueue('analytics-svc.contract.signed.dlq', dlx, 'contract.signed'); // DLX keeps the routing key
```

Do not add `x-max-length`, `x-overflow` or `x-message-ttl` to a consumer queue: they would drop events or push
back on producers. A non-empty DLQ needs a person (check the DLQ depth in the RabbitMQ UI or with
`rabbitmqctl list_queues name messages`); replay the **original** message so its `eventId` is kept.

### Delivery handling

| Situation | Result |
|---|---|
| Processed, or already processed (`DUPLICATE`) | `ack` |
| Invalid JSON / envelope off-contract (`InvalidEnvelopeError`) or handler throws `NonRetryableError` | `nack(requeue=false)` → DLQ immediately |
| Transient error | retried 3 times with backoff 1s, 2s (max 4s), then DLQ |
| Channel closed before the `ack`/`nack` | logged as an error, never thrown (no unhandled rejection); the message stays unacked, the broker redelivers it on a new channel and dedupe drops the repeat |

The marker and the handler's DB writes share a transaction, so a failed attempt leaves no marker, and a later DLQ
replay of the **original** `eventId` is processed once. Side effects outside the DB (emails, HTTP calls) must also
use their own semantic key, e.g. notification `(event_id, recipient_email, notification_type)`.

## Delivery semantics

At-least-once. If the service crashes after the broker confirmed but before the row is marked `PUBLISHED`,
the event is published again with the **same** `eventId`, and consumers drop it through dedupe.
The poller locks rows with `FOR UPDATE SKIP LOCKED`, so several instances can run without double-publishing.
It stops a batch at the first broker failure to keep per-service order. Rows stuck `PENDING` for more than
5 minutes are logged by the built-in monitor, like the Java `OutboxMonitor`.

**A row that can never be published blocks the rows after it.** This is deliberate: almost every publish failure
is a broker outage, and the attempt counter cannot tell "RabbitMQ is down for 10 minutes" from "this message is
broken". Giving up after N attempts would silently drop real events during an outage, and skipping a row would break
per-service order. So the row stays `PENDING`, its `attempts` and `last_error` grow on every poll, and the monitor
warns. If `last_error` shows a permanent cause (for example a message larger than the broker accepts), fix the cause
and let the poller retry, or, as a deliberate manual decision, park that one row so the queue moves on:

```sql
UPDATE event_outbox SET status = 'FAILED' WHERE id = ? AND status = 'PENDING';  -- logged, never automatic
```

## Event ids

`eventId` must be a plain RFC 4122 UUID (36 characters). A `urn:uuid:` prefix is rejected even though the
contract's `format: uuid` (as implemented by ajv-formats) accepts it: `event_id` columns are `CHAR(36)` and the Java
producers never send the prefix. `createEnvelope` always generates a plain UUID.

## Develop

```bash
npm install
npm run typecheck
npm test          # unit tests + contract test against contracts/events/event-envelope.yaml
npm run build
```
