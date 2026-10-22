# analytics-service (Node + NestJS)

Non-critical CQRS projection for AgriContract Phase 2 (`docs/phase_2/design/analytics-service-phase2-design.md`).
It only listens to canonical RabbitMQ events and never calls core services. This increment (issue #118)
delivers **persistence and a working, idempotent ingestion path** (#118); #133 adds the first projections
(signed contracts and milestone outcomes); the contract-level facts come with #148.

The service runs on NestJS 11 (CommonJS, Node >= 22.12, Jest; same setup as `notification-service-nest`) for HTTP, modules and lifecycle hooks. Domain and application code stay framework-free;
Nest is used only in `infrastructure/` and `main.ts`. `/health` listens first and answers 503 until MySQL and
RabbitMQ are up (`AnalyticsLifecycle` connects in the background). SIGTERM/SIGINT run the graceful shutdown.

Built on the shared `@agricontract/eventing` library (`backend/shared/node-eventing`, issue #90).

## Layout

```
src/
  domain/                       ingested event types, money arithmetic, read-model rows
  application/
    port.ts                     IngestEventCommand, IngestedEventStore, ProjectionHandler, repositories
    usecase/ingestEvent.ts      persist the event, then run the projections registered for its type
    usecase/recordSignedContract.ts, recordMilestoneOutcome.ts, flagForceMajeure.ts   the #133 projections
  infrastructure/
    messaging/                  topology, validation (eventContracts), mapping to the command, consumers, DLQ monitor
    persistence/                MySQL: landing table store, migrations, projection repositories and registry
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
                              + projection handlers for that type (#133: dim_contract and milestone facts)
     4. ack                   (a transient failure rolls everything back and is retried 3 times: 1s, 2s)
```

A consumer exists for **every** subscribed event type from the first deploy, so a restart always drains the
backlog: events are validated, stored and acked even before their projection exists. The stored events are the
input for projections that are added later. Recipient e-mail addresses (`milestone.settled`) are removed before
anything is written; analytics never stores contact data.

| Acceptance criterion | How it is met | Test |
|---|---|---|
| Consumer dedupes events | `IdempotentConsumer` marks the `eventId` in `analytics_idempotency_log` in the same transaction as the write; a redelivery or DLQ replay of the same `eventId` is skipped. | `ingestion.spec.ts` "consumer dedupes events"; `mysql.spec.ts` (real MySQL) |
| Outage does not block producers | Producers publish to their own exchange and never wait for analytics. Queues are durable with no length limit / overflow / TTL. contract-service and escrow-service have no client or URL for analytics. | "outage does not block producers", "queues cannot push back", "not on the transaction critical path" |
| Queue backlog can catch up | Production wiring test: stop analytics, publish, restart, everything is stored exactly once (duplicates and DLQ replays are no-ops). | `runtime.spec.ts`, `ingestion.spec.ts` "queue backlog can catch up" |

Subscribed events: `contract.signed`, `milestone.settled`, `milestone.cancelled_with_penalty`,
`milestone.force_majeure_resolved`, `milestone.price_adjusted`, `contract.settled`, `contract.terminated`.
## Read models (issue #133)

| Event | Effect (same transaction as the dedupe row) |
|---|---|
| `contract.signed` | UPSERT `dim_contract` by `contract_id` |
| `milestone.settled` | INSERT `fact_milestone_performance` (`status` SETTLED, `delta_shortfall_amount` = locked - actual) |
| `milestone.cancelled_with_penalty` | INSERT `fact_milestone_performance` (`status` CANCELLED_WITH_PENALTY, amounts 0.00) |
| `milestone.force_majeure_resolved` | only `decision = APPROVED`: UPDATE `has_force_majeure`, or stage it in `pending_force_majeure_flag` when the fact row does not exist yet |

- A force-majeure approval arrives before the milestone ends, so the settled / cancelled handler first looks in
  `pending_force_majeure_flag`, sets `has_force_majeure` in the new row and deletes the staging row (design 2.4).
  The result is the same for any arrival order.
- The `fact_id` is the event id and `milestone_id` is UNIQUE, so a duplicate event (same id, or the same
  milestone under a new id) is never counted twice.
- A settlement and an approval of the same milestone can be consumed in parallel. Both handlers take a row lock
  on `milestone_projection_lock` (one row per milestone) as their first statement and read with `FOR UPDATE`, so
  they run one after the other and the flag is never lost, whichever commits first.
- Migration `V3__create_signed_and_milestone_facts.sql` is additive. Money columns are `DECIMAL(18,2)`;
  timestamps are `DATETIME(3)` in UTC.
- `milestone.cancelled_with_penalty` carries no `lockedAmount` (the contract only has `penaltyAmount`), so its
  fact stores 0.00 for locked, actual and shortfall instead of inventing money. Monthly aggregates must not
  read efficiency from those rows.

To add a projection, implement `ProjectionHandler`, build it in `buildProjections`
(`infrastructure/persistence/projections.ts`) and provide its repository in `infrastructure/nest/projections.module.ts`;
Nest injects the resulting list (`PROJECTIONS` token) into the ingestion lifecycle.

## Validation

`infrastructure/messaging/eventContracts.ts` mirrors `contracts/events/golden-flow-events.yaml`: producer,
supported `eventVersion`, every payload field (required, format, enum) and no unknown fields. Error messages name
the field and the rule, never the value. Date-times are checked field by field (a date such as
2026-02-30 is rejected; `Date.parse` is not used) and unknown fields are detected with an own-property check.
`test/event-contracts.spec.ts` compares the rules with the YAML and runs the same valid and mutated payloads
(impossible dates, clock limits, extra fields such as `constructor`) through AJV (2020-12 + ajv-formats) compiled
from the frozen YAML and through the service; they must agree. `test/invalid-events.spec.ts` checks that an
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
npm run build        # nest build -> dist/
npm run migrate      # creates analytics_idempotency_log and analytics_ingested_event (additive)
npm start            # nest start; health: GET http://localhost:8093/health
```

Environment: `SERVER_PORT` (8093), `DB_HOST`, `DB_PORT`, `DB_NAME` (`analytics_db`), `DB_USERNAME`, `DB_PASSWORD`,
`RABBITMQ_HOST`, `RABBITMQ_PORT`, `RABBITMQ_USERNAME`, `RABBITMQ_PASSWORD` (or `AMQP_URL`), `ANALYTICS_PREFETCH` (20),
`DLQ_MONITOR_INTERVAL_MS` (30000), `SHUTDOWN_TIMEOUT_MS` (25000).
If MySQL or RabbitMQ is down at startup the service keeps retrying; if the broker connection or the consume channel
drops it exits so the orchestrator restarts it (unacked messages are redelivered and deduped).

## Test

```bash
npm run typecheck
npm test             # Jest
```

`@agricontract/eventing` is ESM-only. At runtime the CommonJS app loads it through Node's `require(esm)` (Node >= 22.12). Jest
cannot do that, so `package.json` maps `@agricontract/eventing` to the library's TypeScript source and strips `.js` from its
relative imports, and `tsconfig.spec.json` compiles specs and that source to CommonJS (same setup as `notification-service-nest`).

In-memory broker and database cover the wiring, validation, DLQ monitor and shutdown (including a handler that
never finishes). `test/mysql.spec.ts` runs the real transaction, dedupe and landing table against MySQL when
`ANALYTICS_TEST_MYSQL_URL` is set, e.g. `mysql://root:root@localhost:3306/analytics_test`. Each MySQL suite creates
its own empty database from that URL (the user needs CREATE / DROP), applies every migration and drops it
afterwards, so the suites do not depend on each other or on existing tables. `mysql-projections.spec.ts` also
runs two overlapping transactions (settlement and approval of one milestone) in both orders.
Not covered by an automated test: a live RabbitMQ broker (the in-memory broker follows its ack/nack/DLX behavior).
CI does not run the Node tests yet; that is tracked in #87.
