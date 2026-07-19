# AgriContract Phase 2 Integration Map

Status: synchronized with the frozen Phase 2 design set and Verification Matrix on 2026-07-20.

## Source precedence

1. Latest Phase 2 design Markdown.
2. `verification-matrix-phase2.md`.
3. Contract/API/event documents.
4. Planning documents.
5. Phase 1 code is an implementation baseline only.

The architecture contains 12 business services. `api-gateway` is an edge component, signature is owned by `contract-service`, and hash-chain is owned by `audit-service`.

## Service ownership

| Service | System of record / responsibility | Publishes | Consumes | Phase 1 disposition |
|---|---|---|---|---|
| `user-service` | Profile, KYC, role/eligibility and persisted lock projection | User notification commands | `reputation.locked`, `reputation.unlocked` | Refactor DTO split, roles, fail-closed lookup and monotonic lock revision |
| `product-service` | Product, category, listing, plot/geolocation and risk signals | Existing product/category events | User eligibility/lock projection where designed | Reuse CRUD; add plot and yield signals without turning them into automatic rejection |
| `contract-service` | ContractTerms/LegalProfile, Contract, Milestone, signatures, `BreachCase`, `AttributionDecision`, remedy calculation and termination lifecycle | `contract.signed`, milestone facts, `breach.reported`, `remedy.finalized`, `contract.terminated`, notification commands | Escrow funding results and inspection decisions | Replace the Phase 1 cancel/single-delivery model |
| `escrow-service` | Deposit and milestone funding orchestration; projection only, never remedy calculator or money source | `bank.*_requested`, escrow funding result events | `contract.signed`, milestone settlement instructions, `remedy.finalized`, `bank.*_completed`, `bank.*_failed` | Add funding retry/cure, partial-lock refund and remedy-leg orchestration |
| `bank-service` | Append-only ledger, system lock, verifier nonce/key and reconciliation | `bank.*_completed`, `bank.*_failed`, AML/security facts | Canonical `bank.*_requested`, analytics structuring signal | New service; persist event, fund and remedy identities |
| `inspection-service` | Level 1.5 reports/signatures and Level 2 commission/confirmation | Inspection decisions and notification commands | File readiness and frozen commissioning inputs | New service |
| `reputation-service` | Immutable completion/dispute facts, insert-only lock ledger, query-time score and pair-risk projection | `reputation.locked`, `reputation.unlocked`, risk-clear events | Positive facts; negative consequences only from `remedy.finalized`; `analytics.structuring_pattern_detected` updates `pair_risk_state` projection | New service; RabbitMQ is not the historical store; Phase 2 does not enforce pair-risk settlement blocking |
| `audit-service` | Sole writer of append-only `audit_record`/`audit_anchor`, dual hash chain and OTS anchoring | Audit notification commands | Canonical source events directly | Add `sourceEventId` dedup and durable anchor outbox/retry |
| `file-service` | Blob, technical metadata, virus scan, intake, retention and legal hold | `file.ready`, `file.failed`, `file.email_notice` | Storage/intake commands | New service |
| `pricing-service` | Reference price quote and manual price attribution | No frozen golden-flow event | None on the critical path | New service |
| `analytics-service` | CQRS facts, monthly aggregates and AML scan | `analytics.structuring_pattern_detected` | Contract/milestone/remedy lifecycle facts including `contract.terminated` | Recompute and overwrite every touched month bucket |
| `notification-service` | Notification delivery log and templates; synchronous OTP delivery | No business decision event | `notification.*_requested` commands only | Refactor ingress, templates and recipient/type dedup |

## Canonical interaction map

| Flow | Producer | Contract | Consumer | Required invariant |
|---|---|---|---|---|
| Eligibility | contract/product | `GET /internal/v1/users/{userId}` | user | Fail closed on unavailable/locked user |
| OTP | contract | `POST /internal/v1/notifications/otp-email` | notification | No delayed send after failed synchronous request |
| Fully signed | contract | `contract.signed` | escrow, analytics, audit | Both signatures bind the same immutable terms hash |
| Initial deposit | escrow | `bank.lock_requested` | bank | One source event per command and one unique remedy/funding leg |
| Funding result | bank | `bank.lock_completed` / `bank.lock_failed` | escrow | Delivery obligation starts only after `LOCKED` |
| Funding failure | escrow | `escrow.milestone_funding_failed` and notification command | contract, notification | Retry/cure; refund any partial lock; use `effectiveDeliveryDeadline` |
| Evidence | contract/inspection/file | canonical milestone, inspection and file events | audit and owning consumers | Evidence replay is a no-op by `sourceEventId` |
| Attribution A | contract | direct `AttributionDecision` (`breachCaseId = null`, `decisionSource = SYSTEM`) | remedy calculator | Only after objective rule is satisfied |
| Attribution B | contract | `breach.reported`; `REPORTED -> UNDER_REVIEW -> RESOLVED` | contract workflow | Allegation has no money/reputation consequence |
| Consequence | contract | `remedy.finalized` | escrow, reputation, audit | Sole trigger for money legs and negative reputation; analytics and notification consume lifecycle/command projections |
| Money command | escrow | canonical `bank.*_requested` with `sourceEventId`, `fundType`, `remedyDecisionId`, `remedyLegId` | bank | `sourceEventId` blocks command replay; `remedyLegId` blocks duplicate leg |
| Termination | contract | `contract.terminated` | audit, analytics | Lifecycle only; notification receives `notification.contract_terminated_requested`, not the domain event |
| Completion | contract | `contract.settled` | reputation, analytics | Positive history/lifecycle only; no money trigger |
| Reconciliation | bank / contract | typed internal audit read plus existing `/internal/v1/bank/ledger` filtered by `contractId` | audit / bank | Bank detects ledger/audit mismatch; contract verifies every expected remedy leg and zero remaining lock before terminal transition |

## Attribution and termination boundary

- `requestedBy` is audit context only and never implies breach.
- `allegedBreachingRole` exists only before the decision is final.
- `finalBreachingRole` is nullable and is the only role usable for sanctions.
- `finalBreachingRole = null` forbids contractual-penalty legs and reputation lock creation.
- Canonical termination types are `WITHDRAW_OFFER`, `MUTUAL_TERMINATION`, `MUTUAL_REPLACEMENT`, `TERMINATION_FOR_BREACH`, `TERMINATION_FOR_FORCE_MAJEURE`, and `ACTIVATION_FAILURE`.
- Buyer non-receipt is direct system attribution only after the notice/window expires and objective delivery evidence exists; otherwise it enters `BreachCase` review.

## Mutual replacement sequence

1. Create and sign the replacement while the old contract remains non-terminal.
2. After the replacement is `SIGNED`, move the old contract to `REPLACEMENT_PENDING` and start refunding its remaining locks.
3. A refund interruption leaves the old contract in `SUPERSEDE_REFUND_PENDING`; it is not yet `SUPERSEDED`.
4. Mark the old contract `SUPERSEDED` only after all old locks are zero and link both contract IDs.
5. A later activation failure of the replacement follows its normal `ACTIVATION_FAILURE` path and never rolls back the old contract history.

## Settlement and termination completion

1. Last-milestone completion or final termination attribution persists the existing `AttributionDecision`/`RemedyDecision` and publishes `remedy.finalized`.
2. Escrow consumes bank results and retries failed/missing legs; contract-service does not consume those result events.
3. Contract-service's idempotent completion reconciler reads the existing internal bank ledger by `contractId`, checks the expected `remedyDecisionId`/`remedyLegId` set, and computes remaining lock from append-only entries.
4. Only a complete leg set plus remaining lock `0.00` permits `SETTLED`/`TERMINATED` and their lifecycle event. A bank failure leaves the contract non-terminal.

## Audit and notification boundary

- Audit consumes canonical domain events directly; there is no generic audit-write command or API.
- `audit_record.source_event_id` is unique. The record retains `sourceEventId`, `causationId`, and `correlationId`.
- OTS anchoring uses a durable outbox/retry mechanism. Replaying the same source event creates no second record, anchor or evidence email.
- Notification messages are commands, separate from domain events. Termination templates use `terminationType`, `finalBreachingRole`, and remedy outcome, never requester attribution.

## Phase 1 migration and compatibility

### Remove or retire

- Remove `CancelContractUseCase`, `cancel(initiatedBy)`, the `/cancel` route, `contract.cancelled`, and every old consumer/template tied to that event.
- Remove `confirmDelivery`, `ContractDeliveredEvent`, `contract.delivered`, and consumers tied only to the single-delivery flow.
- Retire `SEIZE_PENALTY` and any consumer that calculates remedy or infers fault locally.
- Stop escrow money consumers of `contract.settled`/`contract.terminated` and reputation consumers other than `remedy.finalized` for negative outcomes.
- Remove dual-write amount/balance fields that compete with the bank ledger.

### Database migration and backfill

- Contract DB: add LegalProfile, breach/attribution/remedy tables, remedy-leg identity, replacement links/states, milestone funding status/delay/effective deadline and outbox indexes.
- Bank DB: add `source_event_id`, `fund_type`, `remedy_decision_id`, `remedy_leg_id`, `entry_type`, `amount`; unique indexes on source command and remedy leg, not on remedy decision alone.
- Reputation DB: add immutable completion/dispute facts and unique negative lock identity by `remedy_decision_id`; do not persist a precomputed score.
- Audit DB: add unique `source_event_id`, causation/correlation columns and durable anchor-outbox state.
- Analytics DB: add termination facts for type/requester/final role/reason and replace increment-only month jobs with full touched-bucket overwrite.
- Notification DB: add event/type/recipient/template/provider/failure metadata with Phase 1-safe defaults; backfill `recipient_email = legacy:{user_id}` before replacing `uq_event_user` with unique `(event_id, recipient_email, notification_type)`.
- Phase 1 `CANCELLED` rows migrate to the read projection `TERMINATED` without publishing a new canonical event; `terminationType`/`finalBreachingRole` remain null when evidence is insufficient, while `cancelledBy` may be retained only as requester audit data. Do not replay automatic negative consequences.

### Deployment order

1. Deploy additive columns, enums, event-envelope support and tolerant consumers.
2. Deploy new `remedy.finalized` consumers and idempotency indexes before enabling its producer.
3. Cut producers to the canonical events, then stop legacy publishers and consumers.
4. Backfill immutable facts/identities and reconcile terminal locks.
5. Remove compatibility reads and legacy columns only after replay and matrix fixtures pass.

## Resolved contract notes

- Signature and milestone action paths already frozen by their owner designs remain authoritative.
- The legacy `/cancel` endpoint is retired. Explicit withdraw, mutual termination/replacement, breach review/resolve and termination execution paths are frozen in `contracts/openapi/golden-flow-api.yaml` and mirrored by the milestone owner design.
- Notification routing data remains in notification commands; approved recipient data on an evidence event is only the explicit hash-chain design exception.
