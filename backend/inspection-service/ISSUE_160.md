# Issue #160 — automatic Level 1.5 assignment lifecycle

## Rebase verification — 2026-10-27

Rebased implementation onto main `2a1febcf9c32b67149dae5e23e2d7a6bcebc15b4`,
excluding the old prerequisite commit `8bbc81e`. Main now contains the owner-delivered
pool contract (`9e1e2a3`); there is no contracts/** diff in this PR.
The only textual conflict was EventEnvelopeCodec: main's nullable map-value handling
was retained and the redundant branch mixin removed. Main's internal API authentication
returns 401 and recognizes context-relative paths; the pool tests now follow that behavior,
while retaining blank/repeated service credential rejection. No assignment behavior changed.

After cleaning affected build outputs, this exact command passed from backend/:

```text
mvn -B -pl inspection-service,user-service -am verify --no-transfer-progress -Dapi.version=1.44
```

User-service: 124 tests; inspection-service: 126; shared java-eventing: 119.
Total: **369 passed, 0 failures, 0 errors, 0 skipped**. MySQL/RabbitMQ integrations ran.
The earlier rebase onto 1ce745a passed 377 tests. Main then moved eight error-handler tests
out of java-eventing into java-web-error, outside this affected reactor. The second rebase
had no conflicts and reran the full verification; no tests were removed or skipped by #160.
PR #280 is confirmed merged at 9e1e2a3; it supplies the prerequisite contract and design §4.3.
`python scripts/validate-contracts.py` validated all 5 files;
`python -m pytest scripts/test_validate_contracts.py -q` passed all 11 tests.
`git diff --check` passed. Earlier verification below is retained as historical evidence.
The contract/runtime header discrepancy below and existing notification/report-confirmation
gaps remain explicit; this rebase does not claim to complete those integrations.

## A. Architecture and user-service dependency

User-service now exposes private `GET /internal/v1/users/inspectors/eligible`.
The response is a complete array of `{ "userId": "<uuid>" }`; it contains no contact or KYC detail.
The database query selects only `role = INSPECTOR AND verificationStatus = VERIFIED
AND (authorizationExpiresAt IS NULL OR authorizationExpiresAt > now)`.
The application supplies `now` using its existing UTC Clock. No buyer/seller reputation checks apply.

The existing `X-Internal-Secret` service credential is required. Gateway credentials,
end-user identities/JWTs, missing/wrong/repeated service headers cannot authorize this route.
API Gateway already rejects `/internal/**`; no gateway route was added.
The prerequisite OpenAPI path is now merged into main (9e1e2a3), outside this PR.
The merged contract references `serviceAuth` (currently documented as X-Gateway-Secret),
while main's executable internal-user security and this client require X-Internal-Secret.
This existing contract/runtime header discrepancy requires contract-owner review; this rebase
does not change frozen contracts or weaken service-only authentication. Invalid internal
credentials return 401 using main's current implementation and the endpoint's frozen response.

`InspectorEligibilityPort.load(userId)` is preserved. Its new pool method calls only the
private pool route and rejects malformed/duplicate identities or dependency failures rather
than treating them as an empty pool. Eligibility is a snapshot at the authoritative query time;
there is no distributed lock on subsequent user-profile changes. Existing submit eligibility
checks still apply in the future report workflow.

## Consumer and persistence

`Level15RequestedConsumer` uses shared `EventEnvelopeCodec`, validates the frozen payload
(including required nullable initiating actor and requestedAt), producer and supported version,
and rejects unknown fields such as inspectorId. No API accepts an inspector selection.

Durable queue: `inspection-svc.inspection.level1_5_requested`.
Exchange: `agricontract.contracts`; routing key: `inspection.level1_5_requested`.
DLX: `agricontract.inspection.dlx`; DLQ: queue name plus `.dlq`.
The neighboring Java consumer convention is reused: invalid messages are rejected without
requeue, transient failures get bounded retry, then dead-letter without logging payloads.

Shared `ProcessedEventStore` claims eventId in the same transaction as lifecycle work.
A failed transaction rolls back both; a redelivery after commit is a no-op.
A distinct event for an already-active contract+milestone is also a no-op before fetching
the pool or drawing randomness.

The consumer fixtures use the real shared codec. The RabbitMQ integration test reads the
serialized envelope written by shared OutboxWriter before delivering it to the broker.
The nullable-payload correction is now supplied by main; the #160 regression test remains.

A stable local lock row serializes request, timeout and confirmation mutations for each
contract+milestone, including the initially absent-assignment case. Current locking reads
avoid stale MySQL REPEATABLE READ snapshots. The existing V1 generated active_slot unique
index remains the database backstop. Selection uses SecureRandom.nextInt(pool.size()).
Only a new row is inserted; the only update to an assignment is conditional NULL-to-value ended_at.

Migrations (V1 is unchanged):

- V2 adds report_due_business_days/report_due_at, checks, timeout/history indexes and local lock rows.
- V3 copies the shared eventing schema, including processed_event, using its documented migration pattern.
- Existing V1 rows retain NULL deadlines; they are not assigned fabricated deadlines or silently timed out.
  Any preexisting production active rows require an explicit data-reconciliation decision.

## Deadline and timeout semantics

Set `INSPECTION_VIETNAM_HOLIDAYS` to comma-separated ISO dates for the operating years;
configuration must be present and contain valid dates; blank, malformed and delimiter-only
values fail startup. Operations must maintain the national holiday
list annually, including Tet. This issue supplies no authoritative holiday dataset.

Assignment date is day zero. The deadline is 23:59:59.999 Asia/Ho_Chi_Minh on the Nth
subsequent Monday-Friday date excluding configured holidays, converted to UTC for persistence.
The request's reportDueBusinessDays (1..30) is retained for each replacement.
The clock is injected and timestamp writes use microsecond precision.

The scheduled job scans at most 100 overdue rows every
`INSPECTION_ASSIGNMENT_SCAN_INTERVAL_MS` (default 60000).
At or after deadline, it locks and rechecks the active assignment, ends it at report_due_at,
fetches the current authoritative pool, removes every historically assigned inspector,
and inserts a new identity if any candidate remains. A stale or concurrent scan is a no-op.
If the replacement pool is empty, the old row remains ended and no replacement is created.
A dependency failure rolls back the attempt and leaves it retryable by the next scan;
it is never interpreted as an empty eligible pool.

Closing the old assignment and inserting its replacement are one transaction. A failure
after closure rolls back the closure too; the next scan retries it. A committed empty-pool
attempt leaves it ended. Redelivery of the original event cannot restart that cycle.
A distinct canonical request after the milestone has no active assignment follows the initial
request rule and uses the current full pool; historical exclusion applies to timeout
reassignment. The payload has no separate inspection-cycle identity. This review does not
invent a new cross-command exclusion rule.

## B. Invariants

1. One active assignment: V1 generates active_slot = 1 for ended_at IS NULL and NULL otherwise.
   MySQL's unique (contract_id, milestone_id, active_slot) rejects a second active row but permits
   multiple historical NULL slots. The local database lock serializes the whole lifecycle;
   ScaffoldBoundaryTest.concurrentAssignmentInsertsCannotBothWin tests the DB backstop directly.
2. Duplicate event has no duplicate effect: the MANDATORY ProcessedEventStore joins the
   requested() transaction. Assignment and claim commit or roll back together. Concurrent
   duplicates and failure after insert are tested against MySQL.
3. Reassignment cannot reuse an inspector: the current pool is filtered by every historical
   inspector for that contract+milestone under the same lock, before the bounded random draw.
4. History is preserved: replacements use new commission IDs and INSERTs. The lifecycle
   repository updates only ended_at with an IS NULL guard, never historical identity/deadlines.
5. No human chooses the inspector: strict canonical payload validation rejects inspectorId;
   no assignment mutation API or ADMIN/OPERATOR/ARBITRATOR override is introduced.

## C. Frozen contract verification and demonstrated fixes

contracts/events/golden-flow-events.yaml lists initiatedByActorId in required and defines it
as nullableUuid. It is **required but nullable**: explicit null is valid; omission is invalid.
The schema is unchanged. A regression test reproduced shared EventEnvelopeCodec omitting this
null map entry. Main now preserves payload null entries through defaultPropertyInclusion;
optional envelope metadata remains omitted. No application-wide Jackson setting was changed.
EventEnvelopeCodecTest.level15RequestPreservesRequiredNullableActorWithoutInventingMissingFields
proves explicit null survives serialization and omission stays distinguishable. The real
outbox-to-RabbitMQ integration test exercises those bytes through the consumer and persistence.
No production publisher of this event exists in contract-service in this checkout; these tests
do not claim a deployed upstream trigger or end-to-end producer integration.

Two other demonstrated defects were corrected without redesign:

- HeaderAuthenticationFilter used the full URI to recognize internal routes. With a servlet
  context path, gateway identity could bypass the service-only check (regression returned 200
  instead of denial). Main now matches the application-relative path and returns 401;
  valid service access still works. The rebase retains #160's repeated/blank credential guards.
- AssignmentConfiguration accepted comma-only holiday input as an empty calendar. It now rejects
  an empty parsed set, with tests for missing, blank, invalid and delimiter-only configuration.

Review baseline was pre-#160 commit 50c24f6, including all untracked implementation files.
V1 remains byte-for-byte unchanged; V2/V3 are additive. MySQL EXPLAIN integration assertions
check use of idx_l15_timeout and indexed ref access for history. The history query can use
idx_l15_previous_inspectors, but MySQL selected the existing uk_l15_active_milestone prefix
for this fixture. The test accepts either matching index and rejects a table scan; it does
not force the optimizer to choose the newly added index or claim that index is indispensable.

## D. Acceptance matrix

| Requirement | Status | Implementation and evidence |
|---|---|---|
| Consume canonical request | PASS | Level15RequestedConsumer + shared codec; Level15RequestedConsumerTest and brokerDeliveryDedupAndMalformedEventDeadLetter |
| Idempotent delivery and atomic effects | PASS | ProcessedEventStore in requested() transaction; concurrentSameEventIsDeduplicated and failureAfterInsertRollsBackAssignmentAndDedupTogether |
| Active assignment is a no-op | PASS | canonicalDeliveryDedupAndActiveMilestoneNoOp verifies no second pool lookup/draw |
| Uniform automatic selection | PASS | SecureRandom.nextInt(N), injected deterministic generator verifies exact pool bound/member in lifecycle tests; no weighting |
| Authoritative eligible pool | PASS | EligibleInspectorQueryTest on MySQL and InternalUserEligibilityClientTest; single-actor load preserved |
| Internal-only minimal response | PASS | InternalInspectorControllerTest checks service secret, rejected user/gateway callers, context path and only userId; gateway denies internal routes |
| No human override | PASS | Level15RequestedConsumerTest rejects inspectorId/foreign producers; no mutation HTTP endpoint introduced |
| At most one active assignment | PASS | V1 DB unique index; concurrentAssignmentInsertsCannotBothWin, concurrentRequestsForSameMilestoneProduceOneActiveAssignment and concurrentTimeoutsCreateOnlyOneReplacement |
| Preserve history and exclude every previous inspector on timeout | PASS | multipleTimeoutsAppendHistoryAndNeverReusePreviousInspectors; new INSERT and full historical exclusion |
| ended_at only NULL to value | PASS | Conditional repository UPDATE; confirmationBoundaryRequiresCallerTransactionAndRollsBackTogether and repeated timeout checks |
| Empty initial pool creates no assignment | PASS | emptyPoolCreatesNoAssignmentAndOnlyOneAlertOnRedelivery |
| Empty replacement pool ends old assignment without reuse | PASS | multipleTimeoutsAppendHistoryAndNeverReusePreviousInspectors; redeliveryAfterExhaustionIsNoOpWhileNewCommandUsesCurrentPool |
| Atomic timeout and retry | PASS | timeoutFailureRollsBackClosureAndCanBeRetried, scheduledScanFindsOverdueRowsAndCreatesReplacement |
| Business-day deadline | PASS | VietnamBusinessDaysTest fixed ICT/weekend/holiday/year boundaries; AssignmentConfigurationTest rejects absent/invalid configuration |
| Additive migrations and safe legacy handling | PASS | V1 unchanged; legacyRowsAreIgnoredAndQueriesUseSupportingIndexes verifies deadline-NULL rows ignored and EXPLAIN index selection on MySQL |
| Report CONFIRMED closes assignment | DEFERRED TO #129 | closeConfirmed MANDATORY boundary tested with caller rollback; no real report confirmation caller yet |
| OPERATOR attention | PARTIAL | structuredOperationalAlertIsEmittedOnlyAfterCommit; no suitable existing human-facing notification contract, so log routing/delivery remains unavailable |
| Narrow scope | PASS | Assignment lifecycle, minimum pool implementation and shared-codec regression test; contract and codec production fix already in main; no #129/#130/Level 2 implementation |

## E. Remaining gaps: operational alert and report boundary

Empty initial/replacement pools produce structured WARN
`inspection_assignment_empty_pool audience=OPERATOR` after transaction commit.
It includes contract, milestone and source event/commission identifiers, without contact PII.
The stable inspection_assignment_empty_pool token identifies the reason/code, replacement
distinguishes the path, and the existing logging framework supplies the timestamp.
Redelivery does not duplicate the alert; rollback emits none. This follows existing logging
monitors, not a new cross-service protocol.

There is no executable human-facing notification contract for this alert in the checkout.
Log routing to an OPERATOR is an operational integration dependency. A process crash between
database commit and the log callback can lose the log; this is not a durable notification outbox.
Therefore end-to-end OPERATOR notification is **not complete**.

`ManageLevel15Assignments.closeConfirmed(...)` requires an existing transaction (MANDATORY),
locks/rechecks active ownership, and writes ended_at = confirmedAt once. It rejects an expired
confirmation timestamp, a foreign inspector/commission and an already-ended row.
Tests prove rollback with the caller transaction. #129 must call it from the real authorized
report confirmation transaction; no report table, report signing, resultHash (#130), or Level 2
workflow is implemented here. Actual report CONFIRMED integration is **not complete**.

## F. Local verification (2026-10-25)

From backend:

```text
mvn -B -pl inspection-service,user-service -am verify --no-transfer-progress -Dapi.version=1.44
```

BUILD SUCCESS (2026-10-25, 20:19:57 +07:00): user-service 70 tests,
inspection-service 126 tests, java-eventing 118 tests. All 314 tests passed with zero failures,
errors or skips; counts were checked against Surefire XML. MySQL 8.0.42 and RabbitMQ 3.13
containers exercise the new lifecycle, including concurrent requests/timeouts and real DLQ delivery.
The user-service filtering query runs against MySQL; the pool HTTP client has focused stub tests.
This is not a claim of a deployed user-to-inspection integration or GitHub CI.

From repository root:

```text
python scripts/validate-contracts.py
python -m pytest scripts/test_validate_contracts.py -q
git diff --check
```

Contract validation passed for all five files; all 11 validator tests passed; diff whitespace check passed.
The Maven executable and Python dependencies were resolved from installed local runtimes because
Maven/Python were not on PATH. No CI configuration was changed. The original shared production
codec fix has since been superseded by main during rebase; only its regression test remains. The red regression runs first
demonstrated all three defects; final verification uses the corrected code.
The first full review reactor also exposed an overly specific new EXPLAIN assertion: MySQL
selected the existing matching unique index for history instead of the new history index.
The revised assertion requires indexed ref access using either applicable prefix and verifies
the new index is a candidate. No failing test was disabled, and no production query was forced
to use a particular index just to satisfy the test.

Merge-readiness: the reviewed assignment foundation passes local verification after the three
demonstrated fixes. This is not approval to close all of #160: OPERATOR notification remains
PARTIAL and the actual report-confirmation integration remains DEFERRED TO #129. Deployment
also requires holiday configuration and an explicit decision for any legacy active rows.

## Changed-file inventory

### User-service dependency

- `backend/user-service/src/main/java/com/agricontract/user/application/dto/EligibleInspectorResponse.java`
- `backend/user-service/src/main/java/com/agricontract/user/application/usecase/ListEligibleInspectors.java`
- `backend/user-service/src/main/java/com/agricontract/user/domain/repository/UserProfileRepository.java`
- `backend/user-service/src/main/java/com/agricontract/user/infrastructure/config/HeaderAuthenticationFilter.java`
- `backend/user-service/src/main/java/com/agricontract/user/infrastructure/persistence/repository/UserProfileJpaRepository.java`
- `backend/user-service/src/main/java/com/agricontract/user/infrastructure/persistence/repository/UserProfileRepositoryImpl.java`
- `backend/user-service/src/main/java/com/agricontract/user/infrastructure/web/InternalInspectorController.java`
- `backend/user-service/src/test/java/com/agricontract/user/infrastructure/persistence/repository/EligibleInspectorQueryTest.java`
- `backend/user-service/src/test/java/com/agricontract/user/infrastructure/web/InternalInspectorControllerTest.java`

### Inspection lifecycle

- `backend/inspection-service/ISSUE_160.md`
- `backend/inspection-service/README.md`
- `backend/inspection-service/pom.xml`
- `backend/inspection-service/src/main/java/com/agricontract/inspection/application/port/AssignmentAlerts.java`
- `backend/inspection-service/src/main/java/com/agricontract/inspection/application/port/InspectorEligibilityPort.java`
- `backend/inspection-service/src/main/java/com/agricontract/inspection/application/service/VietnamBusinessDays.java`
- `backend/inspection-service/src/main/java/com/agricontract/inspection/application/usecase/ManageLevel15Assignments.java`
- `backend/inspection-service/src/main/java/com/agricontract/inspection/domain/model/AssignmentRequest.java`
- `backend/inspection-service/src/main/java/com/agricontract/inspection/domain/model/TimedAssignment.java`
- `backend/inspection-service/src/main/java/com/agricontract/inspection/domain/repository/AssignmentLifecycleRepository.java`
- `backend/inspection-service/src/main/java/com/agricontract/inspection/infrastructure/client/InternalUserEligibilityClient.java`
- `backend/inspection-service/src/main/java/com/agricontract/inspection/infrastructure/config/AssignmentConfiguration.java`
- `backend/inspection-service/src/main/java/com/agricontract/inspection/infrastructure/config/AssignmentRabbitConfiguration.java`
- `backend/inspection-service/src/main/java/com/agricontract/inspection/infrastructure/messaging/Level15RequestedConsumer.java`
- `backend/inspection-service/src/main/java/com/agricontract/inspection/infrastructure/messaging/OperationalAssignmentAlerts.java`
- `backend/inspection-service/src/main/java/com/agricontract/inspection/infrastructure/persistence/JdbcAssignmentLifecycleRepository.java`
- `backend/inspection-service/src/main/java/com/agricontract/inspection/infrastructure/scheduling/AssignmentTimeoutJob.java`
- `backend/inspection-service/src/main/resources/application.yml`
- `backend/inspection-service/src/main/resources/db/migration/V2__assignment_lifecycle.sql`
- `backend/inspection-service/src/main/resources/db/migration/V3__create_eventing_tables.sql`
- `backend/inspection-service/src/test/java/com/agricontract/inspection/AssignmentLifecycleIntegrationTest.java`
- `backend/inspection-service/src/test/java/com/agricontract/inspection/ScaffoldBoundaryTest.java`
- `backend/inspection-service/src/test/java/com/agricontract/inspection/application/service/VietnamBusinessDaysTest.java`
- `backend/inspection-service/src/test/java/com/agricontract/inspection/infrastructure/client/InternalUserEligibilityClientTest.java`
- `backend/inspection-service/src/test/java/com/agricontract/inspection/infrastructure/config/AssignmentConfigurationTest.java`
- `backend/inspection-service/src/test/java/com/agricontract/inspection/infrastructure/messaging/Level15RequestedConsumerTest.java`

### Executable contract (already in main; not in this PR diff)

The eligible pool endpoint is present in `contracts/openapi/golden-flow-api.yaml` on main.

### Shared serialization hardening

- `backend/shared/java-eventing/src/test/java/com/agricontract/eventing/EventEnvelopeCodecTest.java`
