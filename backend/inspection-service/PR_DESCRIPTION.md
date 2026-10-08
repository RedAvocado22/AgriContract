# [P1][inspection] Scaffold inspection service and eligibility gate

Closes #112

## Outcome

Add the inspection-service foundation under the existing application/common/domain/
infrastructure structure. An authenticated inspector must pass authoritative role,
KYC and license checks and own the active Level 1.5 assignment. Inspector A cannot
access Inspector B's commission; missing and ended assignments also deny access.

This implements the explicitly approved scope supplement: assignment persistence
and authorization without assignment selection, creation transport or a complete
inspection workflow. No business controller or event surface is introduced.

## Changes

- [x] Migration
- [x] Domain/application authorization
- [x] Adapter: persistence, gateway identity and internal-user client
- [x] Unit tests
- [x] Integration/contract boundary tests (local MySQL and HTTP stub; not cross-service E2E)

- Register the Spring Boot / Java 21 module in the backend reactor.
- Add an additive Flyway migration in inspection_db for
  level1_5_inspection_assignment, provenance, historical rows and a generated-column
  unique index allowing at most one active assignment per contract/milestone.
- Add immutable JPA mapping and read-only repository ports/adapters. No production
  seed, save/update/delete entrypoint or cross-service database access.
- Validate forwarded identity using the frozen gateway-secret trust boundary;
  never override an established principal or accept client ownership fields.
- Query the frozen internal user profile, fail closed on malformed/unavailable
  information, and exclude buyer/seller reputation locks from inspector eligibility.
- Add application-level commission and contract/milestone ownership checks.
- Document deployment configuration, schema approval, error semantics and deferred work.

## Verification

Executed from backend:

```text
mvn -pl inspection-service -am verify "-Dapi.version=1.44"
```

Passed on Java 21, Spring Boot 3.3.5, Docker Engine 29.6.1 and MySQL 8.0.42:

| Suite | Tests | Result |
|---|---:|---|
| InspectorEligibilityGateTest | 21 | Passed |
| InspectionAuthorizationTest | 8 | Passed |
| GatewayAuthenticationFilterTest | 8 | Passed |
| InternalUserEligibilityClientTest | 23 | Passed |
| ScaffoldBoundaryTest | 16 | Passed |
| Total | 76 | 0 failures, 0 errors, 0 skipped |

The full backend reactor also passed `mvn verify "-Dapi.version=1.44" --no-transfer-progress`; all eight reactor projects succeeded. Local logs: `target/review-verify.log` and `target/review-reactor-verify.log` (ignored build artifacts, not GitHub CI evidence).

The API-version flag supports the parent-managed docker-java client on Docker 29.
Flyway migration and Hibernate schema validation run against disposable MySQL, not
H2. Integration coverage includes actual CHECK/PK/unique constraints, two concurrent
assignment inserts, historical ownership, gateway-to-application-to-repository
authorization, and the closed HTTP surface. User-service HTTP behavior is stubbed;
this is not a claim of cross-service end-to-end readiness. Packaging also passed.

## Contract and scope boundaries

- No frozen API/event YAML, Level 2 table or inspection_report.commission_id change.
- AccessDeniedException represents the approved 403 authorization outcome.
- Error catalog specifies 503 for eligibility dependency failure, while the frozen
  inspection operation lists 201/403/409 only. The application retains a distinct
  dependency exception; no new response mapper/code or YAML change is introduced.
  Reconcile endpoint documentation when implementing report submission.
- Selection authority, vetted inspector sourcing and assignment creation transport
  remain undecided. No role or service is granted selection authority here.
- End/revoke/reassign workflows remain deferred; ended_at is storage support only.
- Unfinished integration dependency: user-service must implement its frozen
  `GET /internal/v1/users/{userId}` eligibility/profile API. This change verifies
  the inspection client against an HTTP stub only; cross-service integration
  remains pending. No second service is implemented in this change.
- Report submission, step-up freshness transport, signatures, result/report hashes,
  idempotent writes, Level 2, money, dispute resolution and reputation are deferred.

## References

- Design: [Level 1.5 identity/KYC](https://github.com/RedAvocado22/AgriContract/blob/a545d9d05221c3bffb2a5f8104cd99dbafd7d9cc/docs/phase_2/design/inspection-phase2-design.md#L30), [inspection database ownership](https://github.com/RedAvocado22/AgriContract/blob/a545d9d05221c3bffb2a5f8104cd99dbafd7d9cc/docs/phase_2/design/inspection-phase2-design.md#L314), plus the user-approved assignment supplement recorded in [README on the proposed PR branch](https://github.com/RedAvocado22/AgriContract/blob/feat/112-inspection-foundation/backend/inspection-service/README.md#approved-schema-supplement).
- API contract: [submitInspectionReport and its authorization response](https://github.com/RedAvocado22/AgriContract/blob/a545d9d05221c3bffb2a5f8104cd99dbafd7d9cc/contracts/events/supplementary-services-api.yaml#L271). This issue does not implement the report endpoint.
- Event contracts: [inspectionReportConfirmed](https://github.com/RedAvocado22/AgriContract/blob/a545d9d05221c3bffb2a5f8104cd99dbafd7d9cc/contracts/events/golden-flow-events.yaml#L865), [inspectionLevel2Commissioned](https://github.com/RedAvocado22/AgriContract/blob/a545d9d05221c3bffb2a5f8104cd99dbafd7d9cc/contracts/events/golden-flow-events.yaml#L1268). Neither is changed or published by this scaffold.
- Verification rows: [12 — ownership](https://github.com/RedAvocado22/AgriContract/blob/a545d9d05221c3bffb2a5f8104cd99dbafd7d9cc/docs/phase_2/design/verification-matrix-phase2.md#L63), [13 — injected identity](https://github.com/RedAvocado22/AgriContract/blob/a545d9d05221c3bffb2a5f8104cd99dbafd7d9cc/docs/phase_2/design/verification-matrix-phase2.md#L64), [19 — fail-closed eligibility](https://github.com/RedAvocado22/AgriContract/blob/a545d9d05221c3bffb2a5f8104cd99dbafd7d9cc/docs/phase_2/design/verification-matrix-phase2.md#L70), [21f — dependency failure](https://github.com/RedAvocado22/AgriContract/blob/a545d9d05221c3bffb2a5f8104cd99dbafd7d9cc/docs/phase_2/design/verification-matrix-phase2.md#L77).
- Integration dependencies: [user-service internal eligibility API](https://github.com/RedAvocado22/AgriContract/blob/a545d9d05221c3bffb2a5f8104cd99dbafd7d9cc/docs/phase_2/design/user-service-phase2-design.md#L92), [inspector enforcement rules](https://github.com/RedAvocado22/AgriContract/blob/a545d9d05221c3bffb2a5f8104cd99dbafd7d9cc/docs/phase_2/design/user-service-phase2-design.md#L58), [gateway trust boundary](https://github.com/RedAvocado22/AgriContract/blob/a545d9d05221c3bffb2a5f8104cd99dbafd7d9cc/docs/phase_2/design/api-gateway-phase2-design.md#L25).
- Error semantics: [role/ownership 403](https://github.com/RedAvocado22/AgriContract/blob/a545d9d05221c3bffb2a5f8104cd99dbafd7d9cc/docs/contracts/error-catalog.md#L68), [eligibility dependency 503](https://github.com/RedAvocado22/AgriContract/blob/a545d9d05221c3bffb2a5f8104cd99dbafd7d9cc/docs/contracts/error-catalog.md#L81).

Matrix coverage is deliberately partial: row 12 originally names contract/escrow;
this issue verifies the analogous inspection ownership invariant. Row 13 is tested
at the downstream trust boundary, not the actual gateway. Rows 19/21f originally
refer to signing/listing/offer; only the inspection eligibility equivalent is
covered here. These tests do not close those system-wide verification rows.
User-service implementation and assignment transport dependency issue IDs still
need to be linked by the owners; no dependency number is fabricated.

## Boundaries

- [x] One business service; backend/pom.xml changes only register this module.
- [x] No unrelated refactor; unrelated workspace ZIP deletion and workflow document are excluded from the proposed commit.
- [x] No frozen API/event changes; assignment schema follows the explicit approved supplement.
- [x] Additive migration; no cross-service database reads/writes.
- [x] Duplicate/concurrent persistence reviewed: PK and active-assignment unique constraint tested. No production write/message handler exists, so transport replay is deferred.
- [x] Size review completed and accepted by the issue requester: 1,034 nonblank, non-comment-only source/build/test lines (453 production/build, 581 tests), excluding Markdown. All belong to the approved #112 scaffold, persistence, eligibility and ownership scope. The 76 tests cover distinct identity, dependency, authorization and real-MySQL constraint/concurrency boundaries. Keeping them with the implementation preserves evidence for the complete foundation; splitting solely to reduce test lines would separate that evidence from the behavior it verifies. No issue split or coverage reduction is required.
- [ ] Repository workflow prerequisite: `.github/PULL_REQUEST_TEMPLATE.md`, `.github/CODEOWNERS`, `.github/ISSUE_TEMPLATE/feature.yml` and `.github/ISSUE_TEMPLATE/bug.yml` are absent from the current base. Handle via the separate repository-enforcement issue required by workflow section 13; do not implement them in #112. Remote branch-protection settings have not been verified.
- [ ] Dependency readiness and issue owner/milestone/service label must be confirmed before Ready for Review/merge.
- [ ] Security review requires inspection owner plus the user/gateway boundary reviewer; remote CI/approvals are not yet available.

Suggested commit: `feat(inspection): scaffold inspection service and eligibility gate`.
Branch: `feat/112-inspection-foundation`. PR base: `main`.
Staging is authorized for review. Commit, push, PR creation and merge still await explicit confirmation.

## File inventory

Modified: backend/pom.xml.

Added beneath backend/inspection-service:

```text
pom.xml
README.md
PR_DESCRIPTION.md
src/main/resources/application.yml
src/main/resources/db/migration/V1__create_level1_5_inspection_assignment.sql
src/main/java/com/agricontract/inspection/
  InspectionServiceApplication.java
  application/port/AuthenticatedInspectorPort.java
  application/port/InspectionAssignmentRepository.java
  application/port/InspectorEligibilityPort.java
  application/service/InspectionAuthorization.java
  application/service/InspectorEligibilityGate.java
  common/exception/EligibilityUnavailableException.java
  domain/model/InspectionAssignment.java
  domain/model/InspectorEligibility.java
  infrastructure/config/InspectionConfiguration.java
  infrastructure/persistence/AssignmentJpaRepository.java
  infrastructure/persistence/InspectionAssignmentEntity.java
  infrastructure/persistence/JpaInspectionAssignmentRepository.java
  infrastructure/security/GatewayAuthenticationFilter.java
  infrastructure/security/SecurityContextInspector.java
  infrastructure/user/InternalUserEligibilityClient.java
src/test/java/com/agricontract/inspection/
  ScaffoldBoundaryTest.java
  application/service/InspectionAuthorizationTest.java
  application/service/InspectorEligibilityGateTest.java
  infrastructure/security/GatewayAuthenticationFilterTest.java
  infrastructure/user/InternalUserEligibilityClientTest.java
```
