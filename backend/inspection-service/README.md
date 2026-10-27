# Inspection service foundation — issue 112

Issue #129 adds an internal report persistence and pending-work query foundation.
See [ISSUE_129.md](ISSUE_129.md) for its exact scope, migration ordering and remaining
signed-submission dependencies. #129's pending query is internal application capability for
active owned Level 1.5 assignments awaiting a report, not a PENDING_REVIEW report state or
an approved HTTP query. Confirmation/publication belongs to #143; trusted READY/owned file
evidence belongs to #146. Identity immutability is enforced at the application/persistence
boundary, not against privileged SQL. The sections below describe the original #112 baseline.

Implements the user-approved scope: scaffold, local Level 1.5 assignment persistence,
inspector eligibility and owner-service authorization. No business API, assignment
creation transport, report submission workflow or event publisher is introduced.
Only `/actuator/health` is public; business requests remain closed.

## Ownership and eligibility

`InspectionAuthorization` is the application boundary for commission ID or the
frozen `(contractId, milestoneId)` report route. Both paths first require an
eligible authenticated INSPECTOR, then load a persisted assignment and require
matching inspector identity and `ended_at IS NULL`. Missing, ended and other-owner
assignments all raise `InspectionAssignmentAccessDeniedException` (403 semantics).
Ineligible authoritative profiles raise `InspectorNotEligibleException`. Both are
application exceptions; infrastructure authentication retains Spring Security errors.

`SecurityContextInspector` reads the established authentication context, never a
request DTO. Per frozen gateway design section 2, `GatewayAuthenticationFilter`
accepts forwarded identity only with the configured gateway secret; it rejects
ambiguous identity headers and missing/blank roles, and cannot overwrite an existing
authenticated principal. ARBITRATOR is recognized as a platform role but does not
grant INSPECTOR authorization. Internal service credentials do not grant inspector identity.

The user adapter calls `GET /internal/v1/users/{userId}` with `X-Internal-Secret`.
The profile must match the authenticated actor and have `INSPECTOR`, `VERIFIED`,
and an unexpired license or explicit JSON null (unlimited license). Missing expiry
is not unlimited. Buyer/seller reputation locks are intentionally not applied.
HTTP errors, redirects, malformed/missing profiles and transport failures fail
closed. Connect and response-header waits are bounded by the configured JDK
transport timeouts. A stalled body after headers is not bounded by that transport
in the current Spring version; a complete response-body deadline remains a
follow-up hardening item. Sensitive responses are not included
in exceptions. Each failed lookup emits exactly one sanitized warning with a fixed
category: `transport`, `dependency_http`, `malformed_response`, or
`identity_mismatch` (`unexpected` for unclassified client failures). Successful
lookups emit none. Diagnostics carry no IDs, URLs, credentials, response bodies
or exception details.

Eligibility by itself is not commission authorization. Future write use cases
must call the combined authorization boundary and perform state/concurrency
checks within their mutation transaction; this read-only scaffold is not a
reservation of permission for a later transaction.

## Authoritative Level 1.5 schema

[Inspection design sections 2.5 and 5](https://github.com/RedAvocado22/AgriContract/blob/d6be0e00b56cf434d68670af045755569c9e2cc6/docs/phase_2/design/inspection-phase2-design.md),
merged in [PR #158](https://github.com/RedAvocado22/AgriContract/pull/158),
adopt the assignment schema in V1. The design is also present in the latest main onto
which #129 was rebased. The original read-only foundation contains:

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

The updated supplementary `submitInspectionReport` contract lists 201/400/403/409/503.
Eligibility and assignment denials retain 403 semantics; unavailable authoritative
information remains a separate `EligibilityUnavailableException` with the fixed
message "Authoritative inspector eligibility could not be validated". A future
report operation must map these application exceptions to its frozen responses
and error envelope. This scaffold introduces no business endpoint or HTTP mapper.

## Approved design and deferred implementation

The following are defined in the authoritative design, but are **not implemented
by #112**:

- Contract-service decides that Level 1.5 is needed and publishes
  `inspection.level1_5_requested` through its outbox. The command is now defined
  as `inspectionLevel15Requested` in the pinned event YAML (the design's note
  saying it awaits YAML synchronization is stale).
- Inspection-service selects uniformly at random from eligible INSPECTORs.
  No platform role selects an inspector manually. An empty candidate set alerts
  OPERATOR without a fallback assignment.
- Consumer deduplication uses `eventId`; an already-active milestone is a no-op.
  Assignment provenance records the originating service and initiating actor.
- Confirmation ends the assignment in the same transaction. An overdue assignment
  ends and triggers random reselection excluding previously assigned inspectors.
  History remains immutable except for setting `ended_at` once; a replacement
  uses a new row, never overwrites the previous inspector.
- Level 1.5 reports bind through `inspection_report.l15_commission_id`.
  `inspection_report.commission_id` remains exclusively Level 2.

Production creation/selection/termination/reassignment belongs to #160 and the owning
contract-service producer work. #129 adds the internal report persistence foundation;
#130 owns deterministic hashes, #143 owns confirmation/signing/event orchestration, and
#146 owns trusted file evidence. None of those integration workflows is supplied by the
original read-only assignment repository. Level 2 remains outside this branch.

V4 remains unique; main currently contains inspection V1 and #160 separately supplies
V2/V3. Do not deploy V4 first and expect default Flyway to backfill lower versions later.
Coordinate deployment in V1,V2,V3,V4 order; this branch does not enable out-of-order migrations.

## Remaining integration dependencies

- [User-service #107](https://github.com/RedAvocado22/AgriContract/issues/107)
  is closed and its internal profile endpoint exists on main. Real provider integration
  verification remains separate from this branch's tests.
  The current client tests use an HTTP stub; they do not establish live compatibility.
- The internal authentication discrepancy remains unresolved: the adapter sends
  `X-Internal-Secret` using `SERVICE_INTERNAL_SECRET`, while frozen OpenAPI
  `serviceAuth` names `X-Gateway-Secret`. The gateway design distinguishes
  gateway-forwarded identity (`GATEWAY_INTERNAL_SECRET`) from internal service
  credentials. User-service/gateway owners must settle the header and credential
  semantics and verify success, invalid credentials, unavailable/malformed profile
  and identity mismatch across services. This branch changes neither convention
  nor any shared contract and does not accept both headers as a workaround.
- #208's eligible-inspector contract has merged; it is no longer a blocker. PR #209
  (#160) and PR #227 (#130) remain unmerged integration dependencies as of 2026-10-27.
  Shared eventing/error infrastructure exists on main; #129 adds no unused dependency.

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
- Design and contract references above are read at `d6be0e00b56cf434d68670af045755569c9e2cc6`; no frozen YAML is changed.
- `docs/phase_2/design/data-governance-phase2-design.md` section 5 (SRS v1.6 roles)
- `docs/phase_2/design/verification-matrix-phase2.md` (inspection and eligibility checks)
