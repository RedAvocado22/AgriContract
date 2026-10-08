# Inspection service foundation — issue 112

Implements the user-approved scope: scaffold, local Level 1.5 assignment persistence,
inspector eligibility and owner-service authorization. No business API, assignment
creation transport, report submission workflow or event publisher is introduced.
Only `/actuator/health` is public; business requests remain closed.

## Ownership and eligibility

`InspectionAuthorization` is the application boundary for commission ID or the
frozen `(contractId, milestoneId)` report route. Both paths first require an
eligible authenticated INSPECTOR, then load a persisted assignment and require
matching inspector identity and `ended_at IS NULL`. Missing, ended and other-owner
assignments all raise `AccessDeniedException` (403 semantics).

`SecurityContextInspector` reads the established authentication context, never a
request DTO. Per frozen gateway design section 2, `GatewayAuthenticationFilter`
accepts forwarded identity only with the configured gateway secret; it rejects
ambiguous identity headers and cannot overwrite an existing authenticated
principal. Internal service credentials do not grant inspector identity.

The user adapter calls `GET /internal/v1/users/{userId}` with `X-Internal-Secret`.
The profile must match the authenticated actor and have `INSPECTOR`, `VERIFIED`,
and an unexpired license or explicit JSON null (unlimited license). Missing expiry
is not unlimited. Buyer/seller reputation locks are intentionally not applied.
HTTP errors, redirects, malformed/missing profiles and transport failures fail
closed. Connect/read timeouts are bounded; sensitive responses are not included
in exceptions.

Eligibility by itself is not commission authorization. Future write use cases
must call the combined authorization boundary and perform state/concurrency
checks within their mutation transaction; this read-only scaffold is not a
reservation of permission for a later transaction.

## Approved schema supplement

The frozen inspection design did not specify Level 1.5 assignment storage. The
user approved the following supplement for issue 112 on 2026-10-08:

- `level1_5_inspection_assignment` belongs exclusively to `inspection_db`.
- Primary key: `commission_id`; cross-service references: `contract_id`,
  `milestone_id`, `inspector_id`, nullable `initiated_by_actor_id` (all CHAR(36)).
- Provenance: `assigned_at`, nullable `ended_at`, `assigned_by_service`.
- Generated `active_slot` is 1 for an active assignment and NULL for history.
  Unique `(contract_id, milestone_id, active_slot)` permits at most one active
  assignment while retaining multiple historical rows. The index also supports
  lookup by contract/milestone; no speculative listing indexes are added.
- Check constraint: end time cannot precede assignment time.
- No cross-service FK and no Level 2 table/column changes. In particular,
  `inspection_report.commission_id` retains its frozen Level 2 meaning.
- Immutable JPA read mapping; repository exposes no save/update/delete operation.
  There is no production seed or assignment mutation use case. Tests insert
  fixtures into their own disposable database only.

Migration: `src/main/resources/db/migration/V1__create_level1_5_inspection_assignment.sql`.
It is additive and creates only the approved assignment table. MySQL >= 8.0.16
is required for enforced CHECK constraints; repository deployment uses MySQL 8.0.
Tests pin MySQL 8.0.42 and run the real Flyway migration plus Hibernate validation.
Database privileges/credentials must be scoped to `inspection_db` by deployment.

MySQL references: [generated columns](https://dev.mysql.com/doc/refman/8.0/en/create-table-generated-columns.html),
[unique indexes](https://dev.mysql.com/doc/refman/8.0/en/create-index.html),
[CHECK constraints](https://dev.mysql.com/doc/refman/8.0/en/create-table-check-constraints.html).

## Error contract verification

The frozen supplementary `submitInspectionReport` operation defines 403 for an
unauthorized inspector and lists 201/403/409 responses. The shared error catalog
separately specifies 503 for unavailable eligibility dependencies. Accordingly,
this scaffold keeps `EligibilityUnavailableException` distinct from access denial
and fails closed, but adds no business HTTP response mapper or YAML response.
A future report endpoint must reconcile its 503 documentation and existing error
envelope with the contract owner. No new error codes are invented here.

## Deferred decisions and integration dependencies

- Who selects an inspector, the vetted candidate source and selection policy.
  No selection authority is assigned to ADMIN, OPERATOR, INSPECTOR or either service.
- Assignment creation transport, idempotent creation behavior and authenticated
  provenance propagation; contract-service owns only the need-for-inspection decision.
- End/revoke/reassign workflows. `ended_at` supports persistence/history only.
- Report/file/signature/hash/idempotency/event workflows, Level 2, dispute routing,
  settlement, reputation and assignment-to-report round binding.
- The existing user-service does not yet expose the frozen Phase 2 internal user
  profile. This module targets that contract without modifying the other service;
  its HTTP adapter is tested against a stub. End-to-end operation awaits the owner.
- Step-up `authTime` transport and the 1800-second report freshness check belong
  to submission, not this scaffold. No client timestamp is trusted here.

## Run and validate

Runtime configuration: `DB_USERNAME`, `DB_PASSWORD`, `GATEWAY_INTERNAL_SECRET`,
`SERVICE_INTERNAL_SECRET`; optionally `DB_HOST` (localhost), `DB_PORT` (3312),
`SERVER_PORT` (8086), `USER_SERVICE_URL` (http://localhost:8081).
Provision a separate empty `inspection_db`; Flyway creates the approved table.
This change does not alter the shared docker-compose topology.

From `backend`, with Docker available:

    mvn -pl inspection-service -am verify

For Docker Engine 29 with the parent-managed Testcontainers/docker-java version,
add the quoted Maven option `'-Dapi.version=1.44'`. Tests do not silently skip when
Docker is unavailable. MySQL and Ryuk containers are disposable and cleaned by
Testcontainers. No existing application database is used.

Tests cover role/KYC/expiry, dependency errors, malformed profiles, gateway header
spoofing, ownership, missing/ended assignments, persistence/provenance round-trip,
primary key, temporal CHECK and concurrent active-assignment uniqueness. Servlet
filter-to-application-to-MySQL authorization is exercised without inventing a
controller endpoint. MockMvc verifies the actual health/closed HTTP surface.

## Sources

- `docs/phase_2/design/inspection-phase2-design.md` sections 2, 4, 5
- `docs/phase_2/design/user-service-phase2-design.md` sections 2–4
- `docs/phase_2/design/api-gateway-phase2-design.md` section 2
- `contracts/events/supplementary-services-api.yaml`: `submitInspectionReport`
- `contracts/events/golden-flow-events.yaml`: `inspectionReportConfirmed`
- `docs/contracts/error-catalog.md`
- User-approved supplement in this issue conversation; no frozen YAML is changed.
