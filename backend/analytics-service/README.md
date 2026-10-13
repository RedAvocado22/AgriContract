# analytics-service (Node)

Non-critical CQRS projection for AgriContract Phase 2 (`docs/phase_2/design/analytics-service-phase2-design.md`).
It only listens to canonical RabbitMQ events and never calls core services. This increment (issue #118)
scaffolds **persistence and idempotent ingestion**; projection handlers come with #133 and #148.

Built on the shared `@agricontract/eventing` library (`backend/shared/node-eventing`, issue #90).

## How it works

```
contract-service --publish--> exchange agricontract.contracts
                                   |  (binding = eventType)
                                   v
                    durable queue analytics-svc.<eventType>  --nack--> agricontract.analytics.dlx --> analytics-svc.<eventType>.dlq
                                   |
                                   v  prefetch 20, manual ack
     validate envelope -> dedupe by eventId in analytics_idempotency_log -> projection handler (same DB transaction)
```

| Acceptance criterion | How it is met | Test |
|---|---|---|
| Consumer dedupes events | Every event goes through `IdempotentConsumer`: the `analytics_idempotency_log` row and the projection write commit in one transaction; a redelivery or DLQ replay of the same `eventId` is skipped. | `test/ingestion.test.ts` "consumer dedupes events" |
| Outage does not block producers | Producers publish to their own exchange and never wait for analytics. Queues are durable with no length limit / overflow / TTL, so nothing pushes back on producers. contract-service and escrow-service have no client or URL for analytics. | "outage does not block producers", "queues cannot push back", "not on the transaction critical path" |
| Queue backlog can catch up | While analytics is down, events wait in the durable queues; on restart the backlog is consumed in order with bounded prefetch, each event applied exactly once (Verification Matrix #23). | "queue backlog can catch up" |

Subscribed events (queues are declared from the first deploy, even before their handler exists, so events
start buffering immediately instead of being lost): `contract.signed`, `milestone.settled`,
`milestone.cancelled_with_penalty`, `milestone.force_majeure_resolved`, `milestone.price_adjusted`,
`contract.settled`, `contract.terminated` (`src/domain/ingestedEvents.ts`). Adding a handler is one entry in
`src/infrastructure/persistence/projections.ts`.

Invalid envelopes, events routed to the wrong queue and handlers throwing `NonRetryableError` go straight to the
DLQ; other errors are retried 3 times (1s, 2s backoff) and then dead-lettered, like the Java services.

## Layout

Same canonical layout as the Java services (`domain` / `application` / `infrastructure`), checked by `test/architecture.test.ts`:

```
src/
  domain/ingestedEvents.ts          canonical event types analytics ingests
  application/
    port.ts                         IngestionHandler (projection handler port)
    usecase/ingestEvent.ts          idempotent ingest of one event (dedupe + projection in one transaction)
  infrastructure/
    config.ts
    messaging/topology.ts           exchanges, durable queues, DLX/DLQ
    messaging/consumers.ts          one RabbitMQ consumer per registered handler
    persistence/projections.ts      MySQL projection handlers (#133, #148)
    persistence/migrate.ts          migration runner
    http/health.ts                  GET /health
  main.ts                           composition root
```

Dependency rule: `domain` imports nothing else; `application` imports only `domain` and the framework-free
`@agricontract/eventing`; drivers (`amqplib`, `mysql2`, `node:http`) stay in `infrastructure` and `main.ts`.

## Run

Requires MySQL 8 and RabbitMQ (same as `backend/docker-compose.yml`).

```bash
npm install          # also installs and builds ../shared/node-eventing
npm run build
npm run migrate      # creates analytics_idempotency_log (additive)
npm start            # health: GET http://localhost:8093/health
```

Environment: `SERVER_PORT` (8093), `DB_HOST`, `DB_PORT`, `DB_NAME` (`analytics_db`), `DB_USERNAME`, `DB_PASSWORD`,
`RABBITMQ_HOST`, `RABBITMQ_PORT`, `RABBITMQ_USERNAME`, `RABBITMQ_PASSWORD` (or `AMQP_URL`), `ANALYTICS_PREFETCH` (20).
If MySQL or RabbitMQ is down at startup the service keeps retrying; if the broker connection drops it exits so the
orchestrator restarts it (unacked messages are redelivered and deduped).

## Test

```bash
npm run typecheck
npm test
```

Uses an in-memory broker and database to prove the three acceptance criteria, plus contract tests against
`contracts/events/golden-flow-events.yaml` and `docs/contracts/integration-map.md`. `test/mysql.test.ts`
runs the dedupe through a real MySQL (migration + shared MySQL adapters) when a database URL is given
(the database must exist; the test applies the migrations):

```bash
ANALYTICS_TEST_MYSQL_URL=mysql://root:root@localhost:3306/analytics_test npm test
```
