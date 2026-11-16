# Reputation service

Java 21 / Spring Boot 3.3.5 service, port 8088. This foundation persists two immutable facts and exposes the authenticated public reputation summary. Source of truth: `docs/design/reputation-service-phase2-design.md` §§2, 3, 6.1b, data governance §8b, and the frozen OpenAPI/event contracts. The HALF_UP integer precision convention was explicitly approved for this implementation.

## Scope and architecture

- `domain/model`: immutable fact records and a strict public-summary property allowlist; UUID identities, UTC timestamps normalized to database microseconds.
- `domain/repository`: append-only fact interface and read-only summary interface. There are no update/delete operations or public fact-write endpoints.
- `application/dto` and `application/usecase`: immutable append Commands, summary Query and Results isolate use-case inputs/outputs from domain records. Use cases construct domain facts and compute the public summary in a repeatable-read transaction. No user-service existence lookup, cross-service foreign keys or cross-service database access.
- `infrastructure/persistence`: JDBC inserts and reads. No JPA dirty checking, `REPLACE`, upsert, or broad `INSERT IGNORE`.
- `infrastructure/messaging`: two service-owned adapters for canonical v1 `contract.settled` and `milestone.dispute_resolved`; no new shared event framework (#89).
- `infrastructure/security` / `web`: gateway identity validation and explicit application-Result-to-HTTP-response mapping. The frozen PII-free response allowlist is independent of domain records. There is no need for a speculative client or external application port.

## Database and append-only enforcement

`V1__create_reputation_facts.sql` creates:

| Table | Business primary key | Event constraint | Query indexes |
| --- | --- | --- | --- |
| `completed_contract_fact` | `contract_id` | unique, non-null `source_event_id` | buyer and seller separately |
| `dispute_outcome_fact` | `milestone_id` | unique, non-null `source_event_id` | flagger and resolution |

The fields match the frozen schemas; `settled_at` / `resolved_at` are `DATETIME(6)`, and dispute resolution is constrained to BUYER/SELLER.

`V2__create_reputation_lock_read_storage.sql` creates the frozen `lock_entry` and `lock_override_event` fields. It retains unique event and remedy-decision identities and all four multiplier inputs. The only foreign key is local: override to lock entry. Checks enforce lock role, positive duration, the `UNLOCK_EARLY` type and distinct proposer/approver identities. Indexes support user history and override lookup. Lock timestamps retain the frozen `TIMESTAMP` precision. No status column is mutated.

The database boundary is enforced by privileges, not an ORM annotation. Provision in this order:

1. As a deployment DBA, create `reputation_db` and a **dedicated migration account** scoped to this database. Supply passwords through secret tooling. The migration account needs schema DDL and schema-history DML; never grant it access to another service database.
2. Run the versioned scripts with Flyway using that account (locations: this service's `src/main/resources/db/migration`). This bootstrap step precedes runtime grants because MySQL table grants require the tables to exist.
3. Create a **fresh** `reputation_runtime` account with no global/schema privileges or inherited roles. Apply [ops/grants.sql](ops/grants.sql) as the provisioning administrator. It grants only SELECT/INSERT on facts and SELECT on lock/override storage. Adapt the account host to deployment policy; the checked-in script uses `%` and requires network isolation.
4. Verify `SHOW GRANTS FOR 'reputation_runtime'@'%'`; do not reuse a broadly privileged existing account. Start the application with separate runtime and migration credentials. Spring Flyway validates/migrates with its own account; JDBC uses the runtime account. Do not expose the database directly to clients.

No real credentials are included. The integration suite provisions these accounts against disposable MySQL, executes the actual grants script, checks MySQL permission-denied error codes for UPDATE/DELETE/TRUNCATE/ALTER/DROP and lock INSERT, and verifies the runtime account cannot read Flyway history. A privileged administrator can still alter data; that is outside the runtime-account protection boundary.

### Replay behavior

Each append first attempts a plain INSERT in its own transaction. A primary-key or event-key collision is compared in a **fresh transaction** after rollback, so a concurrent winner is visible under MySQL repeatable read. Identical immutable content returns DUPLICATE, including an identical business fact with a different event ID. Conflicting fields, an event ID reused for a different business fact, or an event/business key pointing at two rows are rejected without mutation. The original event ID stays stored; ignored alternate IDs are not persisted as an additional alias ledger. MySQL constraints are the concurrency authority.

Consumers map IDs directly from the frozen payload and envelope `occurredAt` into application Commands; domain construction retains microsecond normalization. They reject unsupported versions, malformed/ambiguous JSON, unexpected fields, wrong producer/type and conflicting facts. The listener commits before returning for acknowledgment. Transient failures receive three bounded attempts; permanent failures and exhausted retries reject to the service's restricted DLQ. Replay must preserve the original event ID, timestamp and payload. A service-owned DLX isolates failed deliveries from other consumers while preserving canonical routing keys. No raw payload is included by the service's rejection handler.

Queues: `reputation-svc.contract.settled` and `reputation-svc.milestone.dispute_resolved`, with matching `.dlq` queues; topic exchange `agricontract.contracts`, service-owned direct DLX `agricontract.reputation.dlx`. Each DLQ binds its original event key (`contract.settled` / `milestone.dispute_resolved`); there is no `x-dead-letter-routing-key` override. This follows the [final 14/10/2026 reviewer convention](https://github.com/RedAvocado22/AgriContract/pull/166#issuecomment-6165707457). The reviewer confirmed these queues have not been deployed, so no deployed-queue migration is required. The consumer validates the canonical envelope and does not accept legacy flat producer payloads. Broker permissions must restrict publishing to trusted producers.

The DLQ monitor passively checks only those two owned DLQs every five minutes, starting after the first interval. It never reads message bodies or acknowledges/replays messages. Nonempty queues emit `event=reputation_dlq_nonempty queue=<owned-name> messageCount=<count>`; probe failures emit only `event=reputation_dlq_probe_failed queue=<owned-name>`. Empty queues produce no warning. Each probe uses a separate short-lived connection cloned from the configured broker/TLS settings, with bounded connect, handshake, RPC and shutdown timeouts, and no automatic recovery. Failures are contained per queue; consumer connections are never closed or reconfigured. Raw driver exception logging is suppressed only on these monitor connections.

Optional settings: `REPUTATION_DLQ_MONITOR_ENABLED` (default true), `REPUTATION_DLQ_MONITOR_INTERVAL_MS` (default 300000; accepted 30000–3600000), `REPUTATION_DLQ_MONITOR_TIMEOUT_MS` (default 3000; accepted 1000–10000 per network phase). Invalid timing values fail startup rather than enabling unbounded timeouts or tight polling. The bounded two-queue pass runs with fixed delay, so slow probes cannot pile up overlapping scheduled checks.

## Public summary and security

`GET /api/v1/reputation/{userId}/public-summary` returns exactly:

```json
{"userId":"e66a2861-599d-4000-96c0-783b5d128fb4","score":null,"locked":false,"lockHistory":[]}
```

Completed contracts count once when the user is buyer OR seller. Every historical lock counts, including expired and early-unlocked entries. Score = `100 * completed / (completed + locks)`, `BigDecimal`, scale 0, HALF_UP; no history gives null. Dispute facts do not affect this baseline score. Examples: (5,0) → 100; (4,1) → 80; (1,7) → 13.

History is newest first (entry ID breaks timestamp ties) and exposes only `lockedAt`, `lockedUntil`, `lockDurationDays`, `penalizedRole`, `status`. A valid override yields UNLOCKED_EARLY; otherwise `locked_until > now` is ACTIVE and all other entries are EXPIRED, including the exact expiry instant. Any remaining ACTIVE entry keeps `locked=true`.

The gateway must strip client-supplied identity headers, validate JWTs, and forward one each of `X-Gateway-Secret`, `X-User-Id`, `X-User-Role`. This service verifies the non-empty shared secret before trusting a strict UUID and recognized role list. Missing, duplicate, malformed or forged identity returns 401. Authenticated reads may target another user's UUID. There is no ADMIN fallback for staff operations; no staff operation is implemented here. Keep the service on the internal network; the external JWT boundary remains the gateway's responsibility.

A valid UUID without completion/lock facts returns 200 with the empty summary, even if unknown to user-service. Invalid UUIDs return 400. Error responses use the frozen ErrorEnvelope shape. Public output excludes contact PII, participant/event/decision IDs, override reasons, staff identities, AML and risk state.

Unexpected application/database failures return a generic HTTP 500 ErrorEnvelope from controller-scoped infrastructure advice. The exception and its cause are never serialized. A separate infrastructure renderer handles container error dispatches with the same envelope; only `DispatcherType.ERROR` to `/error` is exempt from re-authentication. Direct requests and forwards to `/error` remain denied, as do unauthenticated business requests. The local UUID validation handler retains HTTP 400.

## Internal batch public summary

`POST /internal/v1/reputation/public-summaries` accepts `{"userIds":["<uuid>"]}`
with 1–100 distinct UUIDs and no other properties. It returns `{"items":[{"userId":"<uuid>","score":null,"locked":false}]}`,
one item per requested UUID. Invalid requests return 400 before reading facts. Unknown UUIDs return 200
with `score: null` and `locked: false`; the batch never includes lock history.

Set `SERVICE_INTERNAL_SECRET` (`service.internal-secret`) and send exactly one `X-Internal-Secret` header.
Missing, wrong, or repeated headers return 401. A missing/blank configured secret disables internal access.
The internal security chain does not trust gateway secrets or user identity headers. This route must remain
on the internal service network and is never routed through API Gateway.

Both summary endpoints use `ReputationReadRepository.publicSummaries`: one parameter-bound aggregate SQL
statement for 1 or 100 UUIDs. The public endpoint additionally reads its existing lock history. Score rounding
is shared in `ReputationReadProjection`; aggregate and history reads share one effective-lock expression.
Each use case captures the clock once inside a read-only repeatable-read transaction. No migration is required.

## Configuration and verification

Required environment values: `REPUTATION_DB_USERNAME`, `REPUTATION_DB_PASSWORD`, `REPUTATION_MIGRATION_USERNAME`, `REPUTATION_MIGRATION_PASSWORD`, `GATEWAY_INTERNAL_SECRET`, `RABBITMQ_USERNAME`, `RABBITMQ_PASSWORD`. Optional: `REPUTATION_DB_URL`, `RABBITMQ_HOST`, `RABBITMQ_PORT`, `SERVER_PORT`. Hikari sets every runtime session to UTC, including when the database URL is overridden. Use MySQL 8.0.16+ for enforced CHECK constraints; tests use 8.0.42.

From the repository root, with Java 21, Maven and a running Linux Docker engine:

```sh
mvn -f backend/pom.xml -pl reputation-service -am verify --no-transfer-progress
mvn -f backend/pom.xml clean verify --no-transfer-progress
```

On Docker 29 with the repository's managed older docker-java client, add `-Dapi.version=1.44` to the Maven command. This is a test-host compatibility setting, not a service requirement. Tests intentionally fail if Docker is unavailable; MySQL tests are not silently skipped and run under Surefire even when `-DskipITs` is present. Frozen schemas are loaded directly from `contracts/`, with UUID/date-time format assertions enabled; no copied contract fixtures need maintaining.

Coverage includes immutable construction, replay and content conflicts, concurrent collisions, migrations and constraints, real permission denials, UTC precision, score rounding, overlapping locks, override/expiry precedence, DTO allowlists, cross-user authentication, malformed UUIDs, empty summaries, and canonical event mappings. Focused MockMvc tests inject database/application failures and exercise internal ERROR dispatches without relaxing ordinary request authentication.

`ReputationRabbitMySqlTest` runs RabbitMQ 3.13.7 and MySQL 8.0.42 in Testcontainers with the production listener factory, bindings, retry policy and runtime grants. It checks canonical deliveries, duplicate replay, commit-before-acknowledgment, malformed/conflicting rejection, transient recovery, exhausted retries, DLQ headers and replay after recovery. Retry failures are injected at the JDBC repository boundary; successful inserts use real MySQL transactions. Latches and broker management counters observe acknowledgment before and after persistence. These tests establish the service's broker boundary, not production gateway or upstream producer compliance. The database fixture is shared by the two integration suites; no shared production event framework is introduced.

## Dependencies and exclusions

- #87 remains a separate repository workflow gate; implementation approval does not waive PR publication/review/merge gates.
- #89 owns the eventual shared event infrastructure; these two local adapters can adopt it when merged.
- #105 gateway routing/JWT propagation and canonical contract-service event production require their owners' integration. No unmerged implementation is imported or modified.
- #144 owns negative-consequence ingestion and production lock creation. This service does not bind `remedy.finalized`, `breach.reported` or `contract.terminated`. Unresolved allegations have no reputation consequences here.
- #145 owns dispute-derived scoring. Dispute facts are stored only.
- No inspection-service (#112), maker-checker, early-unlock write API, AML, credit export or two-business-day contest timer is implemented. The contest policy belongs upstream to attribution finalization.

## Lock events — Issue #268 (D53)

`V3__create_lock_event_lifecycle.sql` adds `user_lock_state` (mutable per-user revision/effective expiry),
`lock_event_receipt` (append-only business transition identities), and the shared `event_outbox` schema.
V3 backfills `user_lock_state` from active, non-overridden ledger entries, taking the maximum expiry per
user and setting revision to 0. An empty ledger creates no state rows. It does not alter ledger rows or emit
historical lock/unlock events. No revision reconciliation is needed: the leader confirmed no such events
were previously published. The first subsequent lock event uses revision 1. Apply updated `ops/grants.sql` after migration:
runtime can mutate the projection/outbox and insert receipts; lock/override storage remains read-only until
the owning #144/#269 writers and their grants are delivered. Run migrations only against disposable test
databases during development; deployment requires the normal database-owner rollout.

`LockEventLifecycle` is the integration boundary for future #144/#269 use cases, not an HTTP API or a remedy
consumer. Its callback contains the owning use case's ledger INSERT, after eligibility/approval has been
established. The callback must use the same transactional datasource, must run only inside this boundary,
and must not perform external effects. Never insert a lock first and then call this boundary in a separate
transaction: that would bypass the per-user mutex and permit a race with expiry.

- `lockCreated(userId, remedyDecisionId, breachReasonCode, correlationId, appendLock)`: locks the projection
  row before invoking the writer, verifies the resulting persisted decision belongs to the user, recomputes
  the MAX over unexpired/non-overridden entries, advances revision and appends `reputation.locked`. The
  reason is supplied from the source remedy, unchanged. This boundary validates wire vocabulary, not
  eligibility or multiplier policy. The persisted source event ID becomes envelope `causationId`.
- `unlockedEarly(userId, overrideId, correlationId, appendApprovedOverride)`: same mutex and transaction,
  verifies a matching persisted override, always emits `UNLOCKED_EARLY` with the remaining effective expiry
  (including unchanged expiry or null). #269 must establish actual approval and actor authorization; the
  boundary does not create approvals. The override event ID is the direct cause.
- Receipts use remedy decision ID / override ID, not incoming delivery ID. Replays skip the callback and
  consume neither another revision nor another outbox row, even after outbox retention. A conflicting
  user/reason replay fails. Receipts must not be purged independently of the immutable ledger.
- `ExpireLocksJob` scans bounded keyset pages using a fixed DB-clock cutoff and `(effective_locked_until, user_id)`.
  It advances past failures, which remain eligible for the next scan. Concurrent changes can move a row behind
  the cursor; a later scan catches it. Each user gets a separate transaction,
  locks/rechecks its state, reads the DB UTC clock after acquiring the mutex and recomputes effective lock.
  Only a still-due state with no remaining active entry emits `EXPIRED` with explicit null expiry. Clearing
  the projection durably prevents duplicate timeout events across retries, restarts and job instances.
  A non-MAX expiry emits nothing. Failed transactions remain due and are logged for retry on the next pass.

The plain `TransactionRunner` port keeps new application code independent of Spring transaction APIs (D44).
`SpringTransactionRunner` uses READ_COMMITTED so ledger recomputation sees the preceding mutex holder's
commit. Compatible outer transactions are joined; read-only or non-READ_COMMITTED ambient transactions
are rejected before business work. Existing public-summary REPEATABLE_READ annotations belong to #420
and are unchanged by #268.

Projection revision, receipt, ledger callback and outbox INSERT commit or roll back together. Revision 1
is the first committed event; users have independent sequences; overflow fails rather than wrapping.
`OutboxLockEventPublisher` reuses `EventEnvelopeCodec`/`OutboxWriter`; the existing shared poller sends to
`agricontract.reputation` with confirms/returns and retains failed sends for retry. Transport is at-least-once:
a crash after broker acknowledgment may resend the same stored event ID. This is not transport exactly-once.

Configuration: `REPUTATION_LOCK_EXPIRY_ENABLED` (true), `REPUTATION_LOCK_EXPIRY_INTERVAL` (PT1M, ISO-8601 or
milliseconds), `REPUTATION_LOCK_EXPIRY_BATCH_SIZE` (100, range 1..1000), `REPUTATION_OUTBOX_ENABLED` (true).
No user-service queues are declared here; consumer bindings remain owned by their consumer.

Integration limitations: #144 must call this boundary for production lock creation and supply the remedy
reason; #269 must call it only after a valid approval. There are no fabricated production callers. V3 does not
invent historical creation reasons/events. A delayed creation with no effective active lock is rejected transactionally because
the canonical locked event requires a non-null effective expiry. The owning #144 workflow must resolve such
a case before integration. No public-summary scoring/response change is made.

Tests: `LockEventLifecycleTest` covers application ordering/guards without Spring; `ExpireLocksJobTest`
covers per-user failure isolation; `LockEventLifecycleMySqlTest` uses MySQL 8.0.42 with production migrations
for revision races, durable replay, rollback, both creation/timeout lock orders, overlapping/early-unlock
semantics, actual runtime grants and DB UTC time. A real RabbitMQ 3.13.7 test exercises unroutable retry and
resend identity. Controlled privileged fixture writers stand in for #144/#269 and share the same transaction;
they are test-only and do not relax the production ledger grants.
