# Phase 2 Implementation Plan

This is the dependency-ordered implementation and migration plan for the frozen Phase 2 contracts. It does not add domain capability. Every task names an owner, expected modules, dependencies, acceptance criteria, Verification Matrix rows and Phase 1 migration work.

## Delivery order

### T0 — Shared contracts, envelope and compatibility

- **Owner service:** contract/platform architecture (all producers and consumers).
- **Files/modules:** `contracts/events/event-envelope.yaml`, `contracts/events/golden-flow-events.yaml`, `contracts/events/notification-commands.yaml`, `contracts/openapi/golden-flow-api.yaml`; shared DTO/envelope modules in each backend service.
- **Dependencies:** none.
- **Acceptance criteria:** one owner per event; `eventId`, `sourceEventId`, `causationId`, `correlationId` and occurred time are consistent; nullable fields are identical in producer/consumer schemas; bank requests carry fund/remedy identities; notification commands remain separate from domain events; tolerant readers support the additive version during rollout.
- **Verification Matrix:** 1, 6, 7, 11f.
- **Phase 1 migration/refactor:** additive schema/event version first; retire `contract.cancelled`, `SEIZE_PENALTY` and old envelope aliases only after consumers migrate.

### T1 — User, gateway and fail-closed foundation

- **Owner service:** `user-service` and `api-gateway`.
- **Files/modules:** user public/internal DTOs, lock projection/revision repository, gateway identity filters/routes, contract/product eligibility clients.
- **Dependencies:** T0.
- **Acceptance criteria:** client identity headers are stripped; `/internal/**` is private; JWT identity is authoritative; user outage/lock fails closed; ADMIN-only routes reject OPERATOR; emergency bank route enforces API-key/ES256 metadata and no retry.
- **Verification Matrix:** 9, 12, 13, 14, 16, 19, 21, 21f.
- **Phase 1 migration/refactor:** split public DTOs from contact-bearing internal DTOs, remove trusted `X-User-*` handling and add route compatibility only while old clients drain.

### T2 — Contract terms, LegalProfile, signatures and OTP

- **Owner service:** `contract-service` (signature feature) and `notification-service` for synchronous OTP.
- **Files/modules:** contract terms aggregate/DTO, LegalProfile validation, signature/OTP use cases/controllers, notification OTP client and schemas.
- **Dependencies:** T0, T1.
- **Acceptance criteria:** persist `governingLaw`, `contractType`, `maxContractualPenaltyRate`, `damagesPolicy`; default Commercial cap is 8% of the violated-obligation value; signed terms/hash are immutable; both signatures bind the same hash; OTP binds user/contract/terms and provider failure returns synchronously with no late send.
- **Verification Matrix:** 3, 11c, 11j, 19, 20, 21b.
- **Phase 1 migration/refactor:** replace the old cumulative boolean and 30% validation; migrate old terms to explicit policy values; retire single-delivery confirmation/signature shortcuts.

### T3 — BreachCase, AttributionDecision and termination taxonomy

- **Owner service:** `contract-service`.
- **Files/modules:** breach case aggregate/state machine, attribution decision repository, termination command handlers (`withdraw`, mutual termination, mutual replacement, breach report/review/resolve, execute termination), replacement pointers/states, `breach.reported` and `contract.terminated` publishers. HTTP paths are frozen in `contracts/openapi/golden-flow-api.yaml`.
- **Dependencies:** T1, T2.
- **Acceptance criteria:** keep `requestedBy`, `allegedBreachingRole`, `finalBreachingRole` separate; implement Rổ A direct system decision and Rổ B `REPORTED -> UNDER_REVIEW -> RESOLVED`; use only the six canonical termination types; objective evidence gates buyer non-receipt Rổ A; replacement remains pending until replacement is signed and refund succeeds; termination is not committed/published until the existing bank-ledger reconciliation read proves every remedy leg complete and remaining lock zero.
- **Verification Matrix:** 8, 11g, 11h, 11l, 11p, 12, 16, 26e, 26h.
- **Phase 1 migration/refactor:** remove `CancelContractUseCase`, `CancelContractCommand`, `CancelledBy`, `/cancel`, `contract.cancelled`, and any fault inference from `initiatedBy`/`cancelledBy`; migrate persisted `CANCELLED` rows to read-side `TERMINATED` without emitting events, keep unprovable termination type/final role null, and treat the old actor only as requester audit data.

### T4 — Remedy calculator and `remedy.finalized`

- **Owner service:** `contract-service`.
- **Files/modules:** remedy calculator, LegalProfile policy evaluator, `RemedyDecision`/`RemedyLeg` persistence and event publisher.
- **Dependencies:** T2, T3.
- **Acceptance criteria:** only final attribution can create sanctions; null final role creates no penalty/reputation leg; normal completion uses the existing SYSTEM/no-fault attribution and owner-return legs for contract-level deposits; deposit forfeiture, contractual penalty and damages remain separate; policy controls cumulative damages and offsets; affected milestones are pro-rata; payload/producer DTO/notification command copy the exact canonical field set and nullability (`attributionDecisionId`, nullable `breachCaseId`, `buyerId`, `sellerId`, `affectedMilestoneIds[]`, nullable final role/reason, eligibility flags and explicit legs); event is emitted once with stable identity.
- **Verification Matrix:** 10, 11g, 11h, 11i, 11j, 11k, 11n, 11o, 26e, 26f, 26h.
- **Phase 1 migration/refactor:** retire local escrow/reputation remedy calculations and `SEIZE_PENALTY`; do not replay negative consequences for ambiguous historical cancellations.

### T5 — Escrow/bank ledger and remedy-leg idempotency

- **Owner service:** `escrow-service` and `bank-service`.
- **Files/modules:** escrow command handlers/outbox, bank ledger aggregate/repository/callbacks, reconciliation projection and indexes.
- **Dependencies:** T0, T3, T4.
- **Acceptance criteria:** `remedy.finalized` is the sole money trigger; every request/result persists `sourceEventId`, `fundType`, `remedyDecisionId`, `remedyLegId`, `entryType`, `amount`; source event and remedy leg are unique separately; a decision may have many legs; bank never computes remedy/attribution; ledger is append-only and positive; contract-service uses the existing contract-filtered ledger read as the completion guard, so terminal lifecycle events are impossible while any remedy leg is failed/missing or remaining lock is non-zero.
- **Verification Matrix:** 1, 2, 5, 6, 8, 9, 10, 11, 11b, 11i, 11k, 11n, 11o, 25.
- **Phase 1 migration/refactor:** remove balance/amount dual writes, old seize consumer and money consumers of `contract.settled`/`contract.terminated`; add ledger columns and unique indexes; reconcile/backfill legacy lock records.

### T6 — Milestone funding failure and effective deadline

- **Owner service:** `escrow-service` with `contract-service` projection.
- **Files/modules:** milestone funding state/aggregate, retry/cure scheduler, partial-refund workflow, deadline calculator, funding events and notification commands. ADMIN retry path is `POST /api/v1/contracts/{contractId}/milestones/{milestoneId}/funding/retry`.
- **Dependencies:** T3, T5.
- **Acceptance criteria:** implement `FUNDING_PENDING`, `LOCKED`, `FUNDING_FAILED`, retry/cure and partial-lock refund; seller delivery obligation cannot begin before lock confirmation; compare deadlines using `effectiveDeliveryDeadline` including funding delay; cure expiry can open buyer breach without blaming seller.
- **Verification Matrix:** 8, 11, 11b, 11m, 11i.
- **Phase 1 migration/refactor:** add funding status/delay/deadline columns and outbox; stop using immutable `expectedDeliveryDate` as the late predicate; backfill existing locked milestones with zero delay only when evidenced.

### T7 — Milestone, inspection and provisional settlement

- **Owner service:** `contract-service` and `inspection-service`.
- **Files/modules:** milestone transition handlers, inspection result DTO/hash verifier, Level 2 commission, settlement result consumer and evidence links.
- **Dependencies:** T2, T5, T6, file contract.
- **Acceptance criteria:** provisional Level 2 legs conserve batch amount; pending inspection prevents auto-confirm; inspection result/report hashes are independently verified; only matching `CONTESTED` milestone transitions; settled milestones are excluded from later pro-rata remedy.
- **Verification Matrix:** 10, 11f, 11n, 11l, 15, 17, 21b, 26i.
- **Phase 1 migration/refactor:** remove `confirmDelivery`, `ContractDeliveredEvent`, `contract.delivered` and single-delivery consumers; map old delivery evidence to immutable milestone evidence only where source identity exists.

### T8 — Reputation facts and remedy lock consumer

- **Owner service:** `reputation-service` and `user-service` projection.
- **Files/modules:** immutable completed-contract/dispute fact tables, remedy lock consumer, unique lock identity, revisioned user projection and score query.
- **Dependencies:** T0, T4, T7.
- **Acceptance criteria:** negative input is only `remedy.finalized`; lock only when `reputationEligible = true`; idempotent by `remedyDecisionId`; strategic reason rules and null final role produce no lock; `buyerId`/`sellerId` in the event resolve the penalized user without lookup; `milestone.dispute_resolved` carries `contractId` and `flaggedByUserId`; score is derived at query time from immutable facts, not RabbitMQ or a stored snapshot.
- **Verification Matrix:** 11i, 11o, 18, 21c, 26, 26f, 26h.
- **Phase 1 migration/refactor:** add fact/lock tables and unique indexes; do not replay negative locks from legacy cancellation events; keep only validated positive history backfill.

### T9 — Audit ingestion, dedup and durable OTS anchor

- **Owner service:** `audit-service`.
- **Files/modules:** canonical-event consumers, `audit_record`/`audit_anchor` repositories, hash-chain verifier, reconciliation read client, OTS outbox/retry.
- **Dependencies:** T0, T5, T7.
- **Acceptance criteria:** audit directly consumes canonical events; `sourceEventId` is unique; records retain causation/correlation; every field is hash committed; anchors are append-only; OTS submission is durable and replay-safe; no generic audit write API/command exists.
- **Verification Matrix:** 4, 5, 7, 11d, 11e, 11f, 11q, 25.
- **Phase 1 migration/refactor:** add source/correlation/causation columns and anchor outbox; backfill only verifiable records; replay evidence events without creating second records/anchors/emails.

### T10 — Notification contracts and templates

- **Owner service:** `notification-service`.
- **Files/modules:** `contracts/events/notification-commands.yaml`, `backend/notification-service` command DTO/consumer/template modules and `notification_logs` migration.
- **Dependencies:** T0, T3, T4, T6, T9.
- **Acceptance criteria:** contracts exist for breach notice, remedy finalized, milestone funding status and contract terminated; every publisher uses the executable envelope (`eventVersion`, `producer`, `aggregateId`, `correlationId`, `payload`) and puts recipients in `payload.recipients`; inspection publisher DTO is exactly `{commissionId, contractId, recipients, org, intakeAddress, contractContext}`; termination copy uses type/final role/remedy outcome; dedup is `(eventId, recipient, notificationType)`; domain events and commands are not mixed.
- **Verification Matrix:** 11m, 11q, 20, 24.
- **Phase 1 migration/refactor:** remove `ContractCancelledCommand`/`ContractDeliveredCommand` consumers and templates; add Phase 1-safe sentinel defaults, backfill `recipient_email = legacy:{user_id}`, then replace `uq_event_user` with unique `(event_id, recipient_email, notification_type)`; keep synchronous OTP path separate.

### T11 — Analytics termination facts and month recomputation

- **Owner service:** `analytics-service`.
- **Files/modules:** termination fact schema, event consumers, late-arrival month recompute job and AML projection.
- **Dependencies:** T3, T4, T5, T9.
- **Acceptance criteria:** measure termination type, requester, final breaching role and reason independently; never infer breach from requester; late events recompute and overwrite every touched month bucket idempotently; analytics outage does not block transaction flow and backlog catches up; `analytics.structuring_pattern_detected` feeds the reputation pair-risk projection, but no Phase 2 settlement auto-block or review workflow is claimed.
- **Verification Matrix:** 23, 26g.
- **Phase 1 migration/refactor:** replace cancellation-derived facts and increment-only aggregators; backfill requester only when recorded and leave final role null when not evidenced.

### T12 — Product, pricing, file and governance matrix scope

- **Owner service:** `product-service`, `pricing-service`, `file-service`, `governance`.
- **Files/modules:** plot overlap/yield validators; pricing `price_history`/`price_ingestion_failure` migrations, Admin manual entry and public reference read/cache-aside APIs; file scan/hold/tombstone jobs; maker-checker and deployment policy checks.
- **Dependencies:** T1, T7.
- **Acceptance criteria:** commodity plot gate is exact; overlap and yield are signals/review states; pricing has an owned migration, `POST /api/v1/prices/manual` for ADMIN/OPERATOR and the existing public quote/item reads with cache-aside behavior; manual entries are append-only and contract-test covered; infected files never become evidence; `retention_until`, `legal_hold`, `deleted_at` and `deletion_reason` persist the file governance invariants; legal hold blocks deletion; two-step deletion self-heals; maker-checker and deployment policy tests pass.
- **Verification Matrix:** 15, 17, 21d, 21e, 22, 26b, 26c, 26d; pricing contract/ migration checks are task acceptance gates because the current 55-row matrix has no pricing behavior row.
- **Phase 1 migration/refactor:** preserve existing file blobs while adding tombstone/hold metadata; do not turn risk signals into new automatic business outcomes.

### T13 — Migration, backfill, compatibility and release gate

- **Owner service:** platform release owner with every service owner signing its migration.
- **Files/modules:** database migrations/indexes, event-version adapters, backfill/reconciliation jobs, CI/deployment checks, `docs/contracts/verification-traceability.md`.
- **Dependencies:** T0-T12.
- **Acceptance criteria:** additive schema first; consumers before producer cutover; legacy publishers/consumers stopped; terminal locks reconcile to zero; ambiguous Phase 1 cancellations remain null/no negative replay; every Matrix row 1-26i has a passing fixture; two restore verification passes precede money traffic; no Phase 2 task depends on an unapproved capability.
- **Verification Matrix:** 8, 9, 11o, 11p, 11q, 25, 26d and all rows 1-26i.
- **Phase 1 migration/refactor:** execute the remove/retire/backfill list in `docs/contracts/integration-map.md`; remove compatibility readers only after replay and reconciliation evidence is retained.

## Cutover sequence

1. Apply additive DB/event-envelope changes (T0), then deploy tolerant readers (T1-T2).
2. Deploy attribution/remedy persistence and consumers (T3-T5), then enable `remedy.finalized` publishing.
3. Enable funding/replacement/inspection flows (T6-T7), then reputation/audit consumers (T8-T9).
4. Enable notification and analytics projections (T10-T11), followed by product/file/governance checks (T12).
5. Run migration/backfill, replay, reconciliation, restore, pricing contract checks and all 55 matrix fixtures (T13).

No implementation task in this plan introduces a new service, event, state, endpoint or workflow beyond the frozen Phase 2 designs.
