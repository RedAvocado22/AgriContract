# Phase 2 Golden Flow Contract

## Scenario

Listing -> immutable legal terms -> two OTP-bound signatures -> deposit lock -> milestone funding -> delivery/inspection -> attribution -> `remedy.finalized` -> bank/reputation consequences -> lifecycle termination or settlement -> audit/analytics/notification.

## Frozen sequence

1. `product-service` exposes an approved listing. Listing/offer eligibility fails closed when user-service is unavailable or the actor is not KYC-valid, lacks current authorization, or is reputation-locked.
2. `contract-service` owns `ContractTerms`, including `LegalProfile {governingLaw, contractType, maxContractualPenaltyRate, damagesPolicy}`. Terms become immutable after the first signature; Commercial Law penalty rates cannot exceed 8% of the breached obligation value by default.
3. `InitiateSign` persists the exact OTP challenge and synchronously calls `POST /internal/v1/notifications/otp-email`. `VerifyOtpAndSign` binds `otpId`, caller, contract, role and the same terms hash. Two signatures must share one `signedContentHash`.
4. `contract-service` publishes `contract.signed` once. `escrow-service` issues one `bank.lock_requested` per required deposit leg and changes projection state only after bank confirmation.
5. `escrow.deposit_locked` is the only activation signal. The contract moves `SIGNED -> ACTIVE`, then milestone 1 enters funding/delivery flow.
6. Each milestone funding projection moves `FUNDING_PENDING -> LOCKED` or `FUNDING_FAILED`. Seller delivery obligations do not begin before `LOCKED`. After lock, seller deadlines use `effectiveDeliveryDeadline = expectedDeliveryDate + fundingDelayBusinessDays + graceDays`.
7. Funding failure retries the failed leg. After `fundingCureWindowDays`, buyer non-funding is Rổ A: a persisted `AttributionDecision` has `breachCaseId = null`, `decisionSource = SYSTEM`, `finalBreachingRole = BUYER`.
8. Seller weighing, buyer receipt, clean confirmation, flagging, force majeure and inspection follow the canonical milestone state machine. `milestone.buyer_confirmed` never triggers money; `milestone.settled` is the normal per-milestone settlement input.
9. Buyer non-receipt is Rổ A only after notice/window expiry and objective delivery evidence. Without objective evidence, contract-service opens a Rổ B `BreachCase` and moves `REPORTED -> UNDER_REVIEW -> RESOLVED`.
10. Rổ B outcomes create `AttributionDecision` only after review. `requestedBy`, `allegedBreachingRole` and `finalBreachingRole` remain separate; allegation alone cannot seize funds or lock reputation.
11. Every final attribution/no-fault decision feeds the remedy calculator. Normal completion creates the existing SYSTEM/no-fault attribution plus a `RemedyDecision` whose owner-return legs release the buyer deposit and optional seller deposit. `remedy.finalized` is the sole canonical consequence event for escrow/bank money legs and reputation lock. Each leg has a unique `remedyLegId`; `remedyDecisionId` groups the set and is not unique per ledger row.
11a. `remedy.finalized` carries `attributionDecisionId`, `buyerId`, `sellerId` and explicit `affectedMilestoneIds[]`; nullable breach fields are present on the wire. Reputation maps the final role to the participant ID from this event without a cross-service lookup.
12. `contract.terminated` carries lifecycle facts only: `terminationType`, `requestedBy`, `finalBreachingRole`, `breachReasonCode`, affected milestones and replacement pointers. Escrow and reputation must not consume it for consequences. Contract-service publishes it only after the existing internal bank-ledger read confirms every expected remedy leg and zero remaining lock.
13. `finalBreachingRole = null` produces only owner-return settlement/refund legs and never contractual penalty, deposit forfeiture or reputation lock.
14. Mutual replacement commits only after the new contract is `SIGNED`: old contract `ACTIVE -> REPLACEMENT_PENDING -> SUPERSEDE_REFUND_PENDING -> SUPERSEDED`. Old locks must be fully refunded before `SUPERSEDED`; later activation failure of the new contract never rolls old history back.
15. Bank persists `sourceEventId`, `fundType`, `remedyDecisionId`, `remedyLegId`, `entryType` and `amount`. Bank never calculates attribution/remedy and never accepts zero/negative commands.
16. Reputation persists immutable completion/dispute facts and derives score at query time. Its only negative input is `remedy.finalized`, gated by `reputationEligible = true`, idempotent by `remedyDecisionId`.
17. Audit-service deduplicates `audit_record` by `sourceEventId`, preserves causation/correlation, and inserts an OTS anchor request into a durable outbox in the same transaction. Replay cannot create another record, anchor or evidence email.
18. Analytics records `terminationType`, `requestedBy`, `finalBreachingRole` and `breachReasonCode`; monthly jobs recompute and overwrite each touched month bucket. Notification templates use termination type, final attribution and remedy outcome, never `requestedBy` as blame.
19. Completion requires bank/audit reconciliation and the invariant that every terminal contract has zero remaining lock. Normal settlement stays `ACTIVE`, and generic termination stays in its current non-terminal state, until that guard succeeds; a failed/missing bank leg cannot produce `contract.settled` or `contract.terminated`.

## Frozen REST surface

The OpenAPI file freezes the owner-design command paths:

- Contract/signature: create, list/detail, pre-sign terms update, sign initiate/verify, withdraw offer, mutual termination/replacement, breach report/review/resolve, termination execute.
- Activation recovery: retry deposit lock and mark activation failed.
- Milestone actions: weigh, confirm, flag, respond, force-majeure claim/resolve, milestone funding retry.
- Reputation: authenticated public summary and maker-checker propose/approve/reject.
- User/internal user, OTP delivery, audit reconciliation/hash, bank ledger read, escrow statement, and External Verifier lock/unlock.

The Phase 1 endpoint `POST /api/v1/contracts/{contractId}/cancel` is retired. The explicit Phase 2 commands above are the canonical transport surface.

## Hard invariants

- Every event has one publisher; notification commands are not domain events.
- `remedy.finalized` is the only trigger for termination/remedy money and reputation consequences.
- No consumer infers fault from `requestedBy` or allegation fields.
- `finalBreachingRole = null` cannot create penalty/forfeiture/reputation effects.
- Every remedy leg is idempotent by `remedyLegId`; every bank request is replay-safe by `sourceEventId`.
- Signed terms, signatures, ledger records, lock facts, audit records and anchors are append-only/immutable as designed.
- Contract terminal states (`SETTLED`, `TERMINATED`, `WITHDRAWN`, `SUPERSEDED`, `ACTIVATION_FAILED`) require zero locked funds; terminal lifecycle events are published only after the guard succeeds.
- Both Rổ A and Rổ B converge on `AttributionDecision -> RemedyDecision -> remedy.finalized`.
- Normal completion uses the same decision/event path with nullable breach fields and owner-return legs; `contract.settled` never runs money.
