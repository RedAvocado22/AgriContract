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

V2 requires `report_due_business_days` and `report_due_at` to be NOT NULL.
`chk_l15_deadline` enforces a duration of 1–30 business days and a deadline at
or after `assigned_at`. The timeout and previous-inspector indexes remain intact.
There is no production legacy data to migrate. Development databases initialized
with the old V2 migration must be reset before verification; this change does not
reset databases automatically. Tests use fresh disposable MySQL containers.

## Configuration and validation

Provision an `inspection_db` database and set `DB_USERNAME`, `DB_PASSWORD`,
`GATEWAY_INTERNAL_SECRET`, `SERVICE_INTERNAL_SECRET`, and
`VIETNAM_HOLIDAYS`. This required startup value is a comma-separated list of
ISO dates (`YYYY-MM-DD`) interpreted as Vietnam local calendar dates. Operations
must update the national holiday list annually for the operating years; no
holiday table is built into the application. Missing, blank, comma-only,
malformed or impossible-date configuration fails startup. Surrounding token
whitespace is trimmed, duplicate dates collapse, and empty comma tokens are
ignored provided at least one valid date remains.

The shared `com.agricontract:java-business-calendar` module calculates business
days as Monday-Friday excluding configured holidays in `Asia/Ho_Chi_Minh`.
The assignment date is day zero; the deadline is 23:59:59.999 ICT on the Nth
subsequent business date, returned and persisted as a UTC `Instant`.

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
