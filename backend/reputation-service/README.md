# Reputation service — Issue #113

Java 21 / Spring Boot 3.3.5 service, port 8088. This foundation persists two immutable facts and exposes the authenticated public reputation summary. Source of truth: `docs/phase_2/design/reputation-service-phase2-design.md` §§2, 3, 6.1b, data governance §8b, and the frozen OpenAPI/event contracts. The HALF_UP integer precision convention was explicitly approved for this implementation.

## Scope and architecture

- `domain/model`: immutable fact records and a strict public-summary property allowlist; UUID identities, UTC timestamps normalized to database microseconds.
- `domain/repository`: append-only fact interface and read-only summary interface. There are no update/delete operations or public fact-write endpoints.
- `application/usecase`: append facts and compute the public summary in a repeatable-read transaction. No user-service existence lookup, cross-service foreign keys or cross-service database access.
- `infrastructure/persistence`: JDBC inserts and reads. No JPA dirty checking, `REPLACE`, upsert, or broad `INSERT IGNORE`.
- `infrastructure/messaging`: two service-owned adapters for canonical v1 `contract.settled` and `milestone.dispute_resolved`; no new shared event framework (#89).
- `infrastructure/security` / `web`: gateway identity validation and PII-free GET projection. There is no need for a speculative client or external application port.

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
2. Run the two versioned scripts with Flyway using that account (locations: this service's `src/main/resources/db/migration`). This bootstrap step precedes runtime grants because MySQL table grants require the tables to exist.
3. Create a **fresh** `reputation_runtime` account with no global/schema privileges or inherited roles. Apply [ops/grants.sql](ops/grants.sql) as the provisioning administrator. It grants only SELECT/INSERT on facts and SELECT on lock/override storage. Adapt the account host to deployment policy; the checked-in script uses `%` and requires network isolation.
4. Verify `SHOW GRANTS FOR 'reputation_runtime'@'%'`; do not reuse a broadly privileged existing account. Start the application with separate runtime and migration credentials. Spring Flyway validates/migrates with its own account; JDBC uses the runtime account. Do not expose the database directly to clients.

No real credentials are included. The integration suite provisions these accounts against disposable MySQL, executes the actual grants script, checks MySQL permission-denied error codes for UPDATE/DELETE/TRUNCATE/ALTER/DROP and lock INSERT, and verifies the runtime account cannot read Flyway history. A privileged administrator can still alter data; that is outside the runtime-account protection boundary.

### Replay behavior

Each append first attempts a plain INSERT in its own transaction. A primary-key or event-key collision is compared in a **fresh transaction** after rollback, so a concurrent winner is visible under MySQL repeatable read. Identical immutable content returns DUPLICATE, including an identical business fact with a different event ID. Conflicting fields, an event ID reused for a different business fact, or an event/business key pointing at two rows are rejected without mutation. The original event ID stays stored; ignored alternate IDs are not persisted as an additional alias ledger. MySQL constraints are the concurrency authority.

Consumers map IDs directly from the frozen payload and fact time from envelope `occurredAt`. They reject unsupported versions, malformed/ambiguous JSON, unexpected fields, wrong producer/type and conflicting facts. The listener commits before returning for acknowledgment. Transient failures receive three bounded attempts; permanent failures and exhausted retries reject to the service's restricted DLQ. Replay must preserve the original event ID, timestamp and payload. Service-specific dead-letter routing avoids copying another consumer's failed delivery into these queues. No raw payload is included by the service's rejection handler.

Queues: `reputation-svc.contract.settled` and `reputation-svc.milestone.dispute_resolved`, with matching `.dlq` queues; topic exchange `agricontract.contracts`, direct DLX `agricontract.contracts.dlx`. The consumer receives raw messages and validates the canonical envelope; it does not accept the legacy flat producer payload as equivalent. Broker credentials and permissions must restrict publishing to trusted producers.

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
- Gateway routing/JWT propagation and canonical contract-service event production require their owners' integration. No unmerged implementation is imported or modified.
- #144 owns negative-consequence ingestion and production lock creation. This service does not bind `remedy.finalized`, `breach.reported` or `contract.terminated`. Unresolved allegations have no reputation consequences here.
- #145 owns dispute-derived scoring. Dispute facts are stored only.
- No inspection-service (#112), maker-checker, early-unlock write API, event publication, AML, credit export or two-business-day contest timer is implemented. The contest policy belongs upstream to attribution finalization.
- The out-of-scope `reputation.unlocked` event schema still needs owner alignment with the updated staff-role policy before implementing that workflow. No frozen contracts are changed by #113.
