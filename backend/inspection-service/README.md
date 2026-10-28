# Inspection service

Inspection-service owns Level 1.5 inspector assignments in `inspection_db`. It
consumes requests from contract-service, selects an eligible inspector uniformly
at random, and preserves assignment history. An active assignment is required
for the assigned inspector to submit a report. No role may select or override
the inspector.

On timeout, the service ends the assignment and selects from eligible inspectors
excluding those previously assigned to the milestone. An empty pool creates no
new assignment. The service logs a structured operational warning; delivery of
a durable, human-facing OPERATOR alert is not implemented. Report confirmation
must close the assignment in the same transaction as the report workflow; the
assignment-side close boundary requires a caller transaction.

## Authorization and integration

The authenticated actor must be an eligible INSPECTOR and own the active
assignment. The user-service internal API supplies the authoritative profile
and eligible pool. Requests use `X-Internal-Secret`; the frozen API's
`serviceAuth` header differs and requires owner reconciliation. User-service
failures fail closed. The assignment does not reserve authorization for a later
transaction: write workflows must recheck ownership and concurrency.

## Database

`level1_5_inspection_assignment` is owned by inspection-service. Its generated
active slot and unique index allow at most one active assignment per contract
and milestone while preserving history. Cross-service identifiers are not
database foreign keys. `ended_at` is set only when an assignment ends.
Assignment lifecycle and eventing migrations follow the initial assignment
schema. MySQL 8.0.16 or later is required for enforced CHECK constraints.

## Configuration and validation

Provision an `inspection_db` database and set `DB_USERNAME`, `DB_PASSWORD`,
`GATEWAY_INTERNAL_SECRET`, `SERVICE_INTERNAL_SECRET`, and
`INSPECTION_VIETNAM_HOLIDAYS`. The holiday value is a nonempty comma-separated
list of ISO dates and must be maintained for the operating years. Business
days use Vietnam weekdays and the configured holidays; timestamps are stored
in UTC. Missing or invalid holiday configuration fails startup.

Optional settings include `DB_HOST` (default `localhost`), `DB_PORT` (3312),
`SERVER_PORT` (8086), `USER_SERVICE_URL` (`http://localhost:8081`), and
`INSPECTION_ASSIGNMENT_SCAN_INTERVAL_MS` (60000).

From `backend`, with Docker available, run:

```text
mvn -pl inspection-service -am verify
```

Only `/actuator/health` is publicly accessible; no business HTTP endpoint is
introduced here. See `docs/phase_2/design/inspection-phase2-design.md` for the
authoritative service design.
