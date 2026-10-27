# Issue #129: Level 1.5 report persistence foundation

This is a deliberately partial implementation for owner review. It does not expose a new API,
commission an inspector, sign or hash a report, or claim a successful signed submission.

## Scope and authorization

`Level15ReportPersistence` is an internal application capability. `allocateIdentity` reuses
`InspectionAuthorization` to resolve the authenticated eligible inspector's active assignment.
It returns an immutable server-generated UUID identity before hashing, so a future report hash
can bind that exact ID. Allocation alone does not persist a report or create a business state.

`persist` requires an existing caller transaction. It rechecks eligibility, locks the assignment
with a current database read, and validates active ownership, the contract/milestone/inspector
binding and the confirmation timestamp. A current locking read prevents two application calls
from inserting different reports for the same assignment. Duplicate insertion of a report ID
is also rejected by the primary key; this is conflict handling, not a public Idempotency-Key
replay protocol. An identity object is not an authorization token.

There is no durable pre-persistence idempotency allocation: allocating again creates a new UUID.
There is no database UNIQUE(l15_commission_id). The frozen design specifies the assignment
binding and UNIQUE(report_id) for signatures, but does not explicitly require a database-level
one-report-per-assignment constraint. Keep the current application locking/check behavior;
direct inserts with different report IDs are not covered by that application guarantee.

The repository has insert/read operations only, with no save/merge/update/delete path. Identity
fields are private and final, report snapshots are immutable records, and retrying an INSERT
cannot replace existing identity, ownership or evidence. This is application/persistence-boundary
immutability, not protection against privileged direct SQL UPDATE/DELETE. Database triggers
were not retained: MySQL with binary logging requires elevated privileges to create them under
the current database setup. No global MySQL security setting was weakened.

`load` requires current inspector eligibility and ownership of the persisted report, including
after its assignment ends. Missing and other-owner reports use the same denial exception.

## Pending work

`pending()` derives the actor from the trusted security context, rechecks eligibility, and
queries only that inspector's assignments with `ended_at IS NULL` and no associated CONFIRMED
report. The anti-join avoids duplicate rows from assignment history or reports. Closed historical
assignments and other inspectors' assignments are excluded. Results reflect the transaction's
database snapshot, not a reservation of permission: writes always recheck under a row lock.

There is no public pending route or invented response DTO. The frozen supplementary API defines
only the signed Level 1.5 submission POST; the public query contract remains an owner review item.
No Level 1.5 PENDING_REVIEW state is introduced. That state belongs to Level 2 automatic intake.

## Database

Additive `V4__create_level15_report_persistence.sql`:

- Adds `inspection_report` for the design's Level 1.5 CONFIRMED snapshot only.
- Server-generated report UUID is the primary key; identity/binding fields are NOT NULL.
- A composite same-database foreign key binds `l15_commission_id`, contract, milestone and
  inspector to the same assignment row, backed by an additive unique binding index.
- CHECKs restrict tier/status to LEVEL_1_5/CONFIRMED, enforce complete lowercase SHA-256-shaped
  commitments, and require confirmed_at <= created_at. Hash shape is not hash verification.
- JSON columns store content/normalized result; timestamps use TIMESTAMP(6) and the existing
  UTC connection convention. Semantic result validation is the future trusted caller's duty.
- Adds inspector/pending and commission/status indexes. No cross-service FK or data backfill.

Version choice was checked against actual trees: origin/main has V1; #160's branch has V2/V3;
V4 was unused. No existing migration was changed or copied. A fresh V1+V4 database is tested.
**Deployment ordering is unresolved:** do not deploy V4 ahead of #160's V2/V3 and then expect
default Flyway to apply those lower versions automatically. Merge/deploy V1,V2,V3,V4 in order,
or agree an unapplied-migration version plan before deployment. No out-of-order override is added.
Level 2 support would require its own additive schema changes; it is not implemented here.

## Future confirmation transaction and dependencies

The persistence adapter accepts already verified material from trusted in-process code; it is
not a validator for user-supplied hashes or normalized results. There is no production submission
caller in this change. Tests use explicitly synthetic storage fixtures, not real signatures.

Issue #143 owns the complete confirmation caller and publication. That future caller must:

1. Obtain authoritative file evidence and validate typed COFFEE results, hashes and trusted
   session freshness. No client-supplied authentication timestamp is trusted here.
2. Acquire #160's milestone lifecycle lock BEFORE this adapter's assignment row lock, to retain
   consistent lock ordering with timeout/reassignment.
3. Persist this report, the inspector signature, #160's assignment closure (including its
   deadline checks) and the confirmed-event outbox entry in one caller transaction, using
   shared OutboxWriter rather than a private outbox implementation.
4. Return the frozen signed-submission success only after all requirements are satisfied.

Dependency status refreshed on 2026-10-27 against main
`2a1febcf9c32b67149dae5e23e2d7a6bcebc15b4`:

- #107 is closed and its internal user endpoint exists on main. Live provider compatibility
  is not established by this branch's mocked eligibility tests. The frozen serviceAuth header
  versus runtime X-Internal-Secret discrepancy remains an owner integration decision.
- #208's eligible-inspector contract has merged and is no longer a blocker.
- #160 assignment lifecycle/closeConfirmed (PR #209) and #130 deterministic commitments
  (PR #227) remain separate, unmerged integration dependencies.
- #143 owns confirmation, signature/session-freshness orchestration and publishing
  inspection.report_confirmed; #146 owns trusted READY/owned file evidence.
- Trusted gateway auth_time/session freshness and approved file/state integration remain
  prerequisites for a real signed submission, not capabilities implemented by #129.

No code from these issues is copied, no second service is changed, and no fake hash/signature/
timestamp is generated. This subset does not validate current remote milestone state or publish
events. It duplicates no shared eventing implementation and has no MVC adapter requiring
java-web-error; domain/application exceptions remain HTTP-agnostic.

The frozen inspection.report_confirmed payload requires reportId, contractId, milestoneId,
tier, normalizedResult, resultHash, reportFileHash, reportHash and confirmedAt. Optional
attribution fields are inspectorId and confirmedByOperatorId.
Optional UUID fields may be omitted but cannot be explicit null; additional properties are
forbidden. Level 1.5 attribution must carry the inspector identity per the design. Publication
belongs to #143. This clarification changes no contract and adds no publisher.

## Acceptance criteria

| Criterion | Status | Evidence and remaining gap |
|---|---|---|
| Only authorized actor commissions | PARTIAL | #129 creates no assignments or human override. Existing authorization is reused for identity allocation, persistence and reads; role/owner/eligibility tests pass. Runtime commissioning remains #160-owned. |
| Report identity is immutable | PASS for this persistence boundary | Server allocation, final identity, insert-only repository, primary key and composite binding FK; identity replacement, duplicate and binding tests. Privileged direct SQL immutability and full signed submission are not claimed. |
| Pending state is queryable | PARTIAL | Internal actor-scoped query is implemented/tested with closed/history/confirmed exclusions. No approved public query API exists. |

## Verification

Reverified on 2026-10-27 after rebase onto main at `2a1febcf9c32b67149dae5e23e2d7a6bcebc15b4`:
focused #129 tests passed **20 cases**; full Maven verification passed **111 tests,
0 failures, 0 errors, 0 skipped** (91 existing cases and 20 #129 cases). Stale build
outputs were cleaned before testing. The first focused attempt could not start MySQL because
Docker was stopped; after starting Docker, the focused and full suites both passed without
skipping tests. Contract validation passed all 5 files;
all 11 validator tests passed. `git diff --check` passed. Commands:

```text
mvn -B -pl inspection-service -am clean test -Dtest=Level15ReportPersistenceTest,ReportIdentityTest -Dsurefire.failIfNoSpecifiedTests=false --no-transfer-progress -Dapi.version=1.44
mvn -B -pl inspection-service -am test -Dtest=Level15ReportPersistenceTest,ReportIdentityTest -Dsurefire.failIfNoSpecifiedTests=false --no-transfer-progress -Dapi.version=1.44
mvn -B -pl inspection-service -am verify --no-transfer-progress -Dapi.version=1.44
python scripts/validate-contracts.py
python -m pytest scripts/test_validate_contracts.py -q
git diff --check
```

`Level15ReportPersistenceTest` exercises real MySQL 8.0.42 migration, storage/retrieval with a
recreated repository, role/ownership denial, current assignment recheck, pending queries,
reassignment history, duplicate IDs, concurrent report attempts, caller rollback and SQL
constraint enforcement. `ReportIdentityTest` covers server allocation/immutability and required
commitments. Existing scaffold tests retain the no-cross-service-FK invariant while allowing
the new same-database report-to-assignment FK. Live user/file/hash/signature integrations and an
actual process restart were not exercised; durable storage is verified via database reload.

This branch is rebased directly onto main and contains no #160/#130 implementation commits;
the already-merged #208 contract is inherited from main, not duplicated in the PR diff.
The Draft PR is a reviewable persistence subset, not a claim that all #129 criteria are complete.

## Changed files (all in inspection-service)

- `application/usecase/Level15ReportPersistence.java`: authorized allocation, persistence and reads.
- `domain/model/ReportIdentity.java`, `ReportEvidence.java`, `Level15Report.java`: immutable identity/material/snapshot.
- `domain/repository/Level15ReportRepository.java`: insert/read-only port.
- `infrastructure/persistence/JdbcLevel15ReportRepository.java`: JDBC persistence and pending query.
- `src/main/resources/db/migration/V4__create_level15_report_persistence.sql`: additive storage and constraints.
- `Level15ReportPersistenceTest.java`, `domain/model/ReportIdentityTest.java`: new integration/unit tests.
- `ScaffoldBoundaryTest.java`: allow same-database FK, still reject cross-service FK.
- `README.md`, `ISSUE_129.md`: scope, verification and review limitations.

Java production paths above are relative to `src/main/java/com/agricontract/inspection`;
test paths are relative to `src/test/java/com/agricontract/inspection`.
