# analytics-service (Node + NestJS)

Non-critical CQRS projection for AgriContract Phase 2 (`docs/phase_2/design/analytics-service-phase2-design.md`).
It only listens to canonical RabbitMQ events and never calls core services. This increment (issue #118)
delivers **persistence and a working, idempotent ingestion path**; projection handlers (fact and dimension
tables) come with #133 and #148.

The service runs on NestJS (HTTP, modules, lifecycle hooks). Domain and application code stay framework-free;
Nest is used only in `infrastructure/` and `main.ts`. `/health` listens first and answers 503 until MySQL and
RabbitMQ are up (`AnalyticsLifecycle` connects in the background). SIGTERM/SIGINT run the graceful shutdown.

Built on the shared `@agricontract/eventing` library (`backend/shared/node-eventing`, issue #90).

## Layout

```
src/
  domain/                       ingested event types
  application/
    port.ts                     IngestEventCommand, IngestedEventStore, ProjectionHandler
    usecase/ingestEvent.ts      persist the event, then run the projections registered for its type
  infrastructure/
    messaging/                  topology, validation (eventContracts), mapping to the command, consumers, DLQ monitor
    persistence/                MySQL: landing table store, migrations, projection registry (empty for now)
    http/health.controller.ts   GET /health (Nest controller)
    nest/                       Nest modules and AnalyticsLifecycle (owns MySQL, RabbitMQ, shutdown)
    runtime.ts                  production wiring of the messaging side (also used by the tests)
    lifecycle.ts                graceful shutdown
    config.ts
  main.ts                       Nest bootstrap: creates the app, enables shutdown hooks, listens
```

## How it works

```
contract-service --publish--> exchange agricontract.contracts
                                   |  (binding = eventType)
                                   v
                    durable queue analytics-svc.<eventType>  --nack--> agricontract.analytics.dlx --> analytics-svc.<eventType>.dlq
                                   |
                                   v  prefetch 20, manual ack, one consumer per subscribed event type
     1. parse envelope        invalid  -> DLQ, no retry
     2. validate producer, eventVersion, payload against the frozen contract   invalid -> DLQ, no retry, nothing written
     3. ONE DB transaction:   analytics_idempotency_log row (dedupe by eventId)
                              + analytics_ingested_event row (the validated event)
                              + projection handlers for that type (none yet)
     4. ack                   (a transient failure rolls everything back and is retried 3 times: 1s, 2s)
```

A consumer exists for **every** subscribed event type from the first deploy, so a restart always drains the
backlog: events are validated, stored and acked even before their projection exists. The stored events are the
input for the #133/#148 projections. Recipient e-mail addresses (`milestone.settled`) are removed before
anything is written; analytics never stores contact data.

| Acceptance criterion | How it is met | Test |
|---|---|---|
| Consumer dedupes events | `IdempotentConsumer` marks the `eventId` in `analytics_idempotency_log` in the same transaction as the write; a redelivery or DLQ replay of the same `eventId` is skipped. | `ingestion.test.ts` "consumer dedupes events"; `mysql.test.ts` (real MySQL) |
| Outage does not block producers | Producers publish to their own exchange and never wait for analytics. Queues are durable with no length limit / overflow / TTL. contract-service and escrow-service have no client or URL for analytics. | "outage does not block producers", "queues cannot push back", "not on the transaction critical path" |
| Queue backlog can catch up | Production wiring test: stop analytics, publish, restart, everything is stored exactly once (duplicates and DLQ replays are no-ops). | `runtime.test.ts`, `ingestion.test.ts` "queue backlog can catch up" |

Subscribed events: `contract.signed`, `milestone.settled`, `milestone.cancelled_with_penalty`,
`milestone.force_majeure_resolved`, `milestone.price_adjusted`, `contract.settled`, `contract.terminated`.
To add a projection, implement `ProjectionHandler` and add it to `PROJECTIONS` in
`infrastructure/persistence/projections.ts`.

## Validation

`infrastructure/messaging/eventContracts.ts` mirrors `contracts/events/golden-flow-events.yaml`: producer,
supported `eventVersion`, every payload field (required, format, enum) and no unknown fields. Error messages name
the field and the rule, never the value. Date-times are checked field by field (a date such as
2026-02-30 is rejected; `Date.parse` is not used) and unknown fields are detected with an own-property check.
`test/event-contracts.test.ts` compares the rules with the YAML and runs the same valid and mutated payloads
(impossible dates, clock limits, extra fields such as `constructor`) through AJV (2020-12 + ajv-formats) compiled
from the frozen YAML and through the service; they must agree. `test/invalid-events.test.ts` checks that an
invalid event goes to the DLQ without a landing row and without a dedup marker.

## Dead-letter queues

Each queue has a DLQ. A monitor checks the depth of every DLQ every `DLQ_MONITOR_INTERVAL_MS` (30 s) on its own
channel and logs a warning with the queue name and depth while it is not empty (no message content).

## Shutdown

On SIGTERM/SIGINT: readiness goes false, the DLQ monitor and consumers stop, the HTTP server closes, in-flight
deliveries (handler, retries and ack) get up to `SHUTDOWN_TIMEOUT_MS` (25 s) to finish, then the MySQL pool and
the RabbitMQ channels and connection are closed. Messages that did not finish stay unacked, are redelivered and
are deduplicated by `eventId`. Exit code 0 when everything drained, 1 after a timeout.

## Run

Requires MySQL 8 and RabbitMQ (same as `backend/docker-compose.yml`).

```bash
npm install          # also installs and builds ../shared/node-eventing
npm run build
npm run migrate      # creates analytics_idempotency_log and analytics_ingested_event (additive)
npm start            # health: GET http://localhost:8093/health
```

Environment: `SERVER_PORT` (8093), `DB_HOST`, `DB_PORT`, `DB_NAME` (`analytics_db`), `DB_USERNAME`, `DB_PASSWORD`,
`RABBITMQ_HOST`, `RABBITMQ_PORT`, `RABBITMQ_USERNAME`, `RABBITMQ_PASSWORD` (or `AMQP_URL`), `ANALYTICS_PREFETCH` (20),
`DLQ_MONITOR_INTERVAL_MS` (30000), `SHUTDOWN_TIMEOUT_MS` (25000).
If MySQL or RabbitMQ is down at startup the service keeps retrying; if the broker connection or the consume channel
drops it exits so the orchestrator restarts it (unacked messages are redelivered and deduped).

## Test

```bash
npm run typecheck
npm test
```

In-memory broker and database cover the wiring, validation, DLQ monitor and shutdown (including a handler that
never finishes). `test/mysql.test.ts` runs the real transaction, dedupe and landing table against MySQL when
`ANALYTICS_TEST_MYSQL_URL` is set, e.g. `mysql://root:root@localhost:3306/analytics_test`.
Not covered by an automated test: a live RabbitMQ broker (the in-memory broker follows its ack/nack/DLX behavior).
CI does not run the Node tests yet; that is tracked in #87.
