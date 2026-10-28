# Inspection service

Inspection-service owns Level 1.5 inspector assignment and report data in
`inspection_db`. An eligible authenticated INSPECTOR must own the active
assignment for a contract and milestone before report material is persisted.
Missing, ended, and other-owner assignments are denied without distinguishing
them. An assignment is not a reservation of authorization for a later write;
the persistence boundary rechecks ownership under a database lock.

The service provides internal report identity allocation, insert/read
persistence, and an actor-scoped query for active assignments awaiting a
confirmed report. These are application capabilities, not a public pending
query or a complete signed-submission workflow. Report identity and evidence
are immutable through the application and insert/read repository; this is not
protection against privileged direct SQL changes. Level 1.5 does not use the
Level 2 `PENDING_REVIEW` report state.

## Integration and schema

The internal user-service profile is checked for INSPECTOR role, verified KYC,
and a valid or explicitly unlimited authorization expiry. The client fails
closed on dependency errors. It uses `X-Internal-Secret`, matching the frozen
`serviceAuth` header.

The assignment and report tables belong to inspection-service. Report binding
uses a same-database foreign key to the assignment; cross-service identifiers
are not database foreign keys. The application stores Level 1.5 report material
but does not itself verify file bytes or hashes, create signatures, close the
assignment, or publish the confirmed event. These operations require a trusted
confirmation workflow and coordinated transaction.

Merged PR #209 supplies the assignment lifecycle and V2/V3 migrations; merged
PR #227 supplies deterministic hash calculation and verification. The persistence
boundary does not yet orchestrate these capabilities; confirmation/publication
remains owned by #143.

Flyway migrations must be applied in V1/V2/V3/V4 order. In particular, deploying
V4 before V2 and V3 does not cause default Flyway to backfill the earlier
versions automatically. MySQL 8.0.16 or later is required for enforced CHECK
constraints.

## Configuration and validation

Provision an `inspection_db` database and set `DB_USERNAME`, `DB_PASSWORD`,
`GATEWAY_INTERNAL_SECRET`, and `SERVICE_INTERNAL_SECRET`. Optional settings
include `DB_HOST` (default `localhost`), `DB_PORT` (3312), `SERVER_PORT`
(8087), and `USER_SERVICE_URL` (`http://localhost:8081`).

From `backend`, with Docker available, run:

```text
mvn -B -pl inspection-service -am test --no-transfer-progress -Dapi.version=1.44
```

Only `/actuator/health` is publicly accessible. See
`docs/design/inspection-phase2-design.md` for the authoritative design;
the current report migration does not yet implement its full two-tier schema.

## Level 1.5 assignment lifecycle

Consume `inspection.level1_5_requested` with shared `EventEnvelopeCodec` and
`ProcessedEventStore`. The processed-event claim and assignment changes share a
transaction. An existing active assignment is a no-op; database milestone locks
and the active-slot unique key protect concurrent requests.

The client reads the authoritative pool from user-service's private
`GET /internal/v1/users/inspectors/eligible` using `X-Internal-Secret`. The endpoint
and its security implementation are owned by merged PR #307, not this service.
The frozen serviceAuth header now agrees with the runtime header. Candidates are
INSPECTOR + VERIFIED with authorization expiry null or strictly after the UTC clock;
Buyer/Seller reputation locks do not apply. Selection is uniformly random with no
ADMIN/OPERATOR/ARBITRATOR override. Empty pools create no assignment and emit a
structured OPERATOR operational alert; human-facing delivery remains a gap.

Timeout ends the previous assignment and inserts a new row, excluding every
inspector previously assigned to the milestone. Empty replacement pools leave the
old assignment ended. `ended_at` moves only from NULL to a value. `closeConfirmed()`
requires the caller's transaction; the downstream report workflow owns the actual
call and atomic report confirmation. The persistence boundary above does not yet invoke it.

V2 requires `report_due_business_days INT NOT NULL` and `report_due_at TIMESTAMP(6)
NOT NULL`, with duration 1..30 and deadline at or after assignment time enforced
by `chk_l15_deadline`. There is no nullable legacy-deadline path. Inspection has
no production legacy rows requiring guessed deadlines; development databases
using the old V2 must be reset explicitly before verification. V1/V3 are unchanged.
Timeout occurs at the deadline; confirmation must be strictly before it.

Set `VIETNAM_HOLIDAYS` to a nonempty comma-separated list of ISO holiday dates
(`YYYY-MM-DD`) for the operating years. Operations must maintain this list annually;
no holiday dates are hardcoded. Missing, blank, comma-only, malformed or impossible
dates fail startup. Tokens are trimmed, duplicates collapse, and empty comma tokens
are ignored only when at least one valid date remains.

The pure-Java shared `com.agricontract:java-business-calendar` implementation uses
Asia/Ho_Chi_Minh Monday-Friday dates excluding configured holidays. Assignment date
is day zero; the deadline is 23:59:59.999 ICT on the Nth subsequent business day,
returned and persisted as a UTC Instant. The scan interval defaults to 60000 ms
(`INSPECTION_ASSIGNMENT_SCAN_INTERVAL_MS`). The service port remains 8087.

The Rabbit consumer validates the frozen envelope/payload and maps it to
`Level15AssignmentRequestedCommand`. It owns the transaction joining the existing
processed-event claim and assignment mutation. Invalid payloads raise
`InvalidEventPayloadException` and are not retried; transient failures retain retries.

The passive DLQ monitor checks `inspection-svc.inspection.level1_5_requested.dlq`
without consuming messages, using an isolated connection with bounded timeouts.
It warns on nonempty queues or probe failures without logging payloads or broker details.
Configure `inspection.dlq-monitor.enabled` (default true), `.interval-ms` (300000),
and `.timeout-ms` (3000) through Spring configuration.
