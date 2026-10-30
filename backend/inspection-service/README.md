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
closed on dependency errors. It uses `X-Internal-Secret`; the frozen API's
`serviceAuth` header differs and requires owner reconciliation.

The assignment and report tables belong to inspection-service. Report binding
uses a same-database foreign key to the assignment; cross-service identifiers
are not database foreign keys. The application stores Level 1.5 report material
but does not itself verify file bytes or hashes, create signatures, close the
assignment, or publish the confirmed event. These operations require a trusted
confirmation workflow and coordinated transaction.

Flyway migrations must be applied in version order. In particular, deploying
V4 before V2 and V3 does not cause default Flyway to backfill the earlier
versions automatically. MySQL 8.0.16 or later is required for enforced CHECK
constraints.

## Configuration and validation

Provision an `inspection_db` database and set `DB_USERNAME`, `DB_PASSWORD`,
`GATEWAY_INTERNAL_SECRET`, and `SERVICE_INTERNAL_SECRET`. Optional settings
include `DB_HOST` (default `localhost`), `DB_PORT` (3312), `SERVER_PORT`
(8086), and `USER_SERVICE_URL` (`http://localhost:8081`).

From `backend`, with Docker available, run:

```text
mvn -pl inspection-service -am verify
```

Only `/actuator/health` is publicly accessible. See
`docs/phase_2/design/inspection-phase2-design.md` for the authoritative design;
the current report migration does not yet implement its full two-tier schema.
