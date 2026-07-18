# Golden Flow Contract

## Scenario

Listing → contract terms → two signatures with OTP → deposit locking → milestone 1 delivery and settlement → milestone 2 dispute → Level 1.5 inspection decision → settlement → contract completion → reputation update → bank/audit reconciliation.

## Frozen sequence

1. `product-service` exposes an approved listing. Product eligibility must fail closed if user-service is unavailable or the user is locked.
2. `contract-service` owns contract terms and participant ownership. Terms remain mutable only before the first signature.
3. `contract-service` runs `InitiateSign` and stores an OTP challenge. It calls synchronous `POST /internal/v1/notifications/otp-email`; notification-service owns delivery only.
4. `VerifyOtpAndSign` validates the exact `otpId`, caller, role, expiry, attempts, authorization expiry and current terms hash. Two signatures must share `signedContentHash`.
5. `contract-service` transitions to `SIGNED` and publishes `contract.signed` exactly once with the catalog payload.
6. `escrow-service` consumes `contract.signed` and publishes canonical `bank.lock_requested` independently for each required deposit leg.
7. `bank-service` appends ledger entries and emits `bank.lock_completed` or `bank.lock_failed` for deposit locking. Escrow changes state only after confirmation.
8. `escrow-service` publishes `escrow.deposit_locked` when all required deposit legs are confirmed. `contract-service` transitions `SIGNED -> ACTIVE`.
9. The first milestone enters `IN_PROGRESS`; seller weighing and buyer confirmation use the exact `milestone.seller_weighed` and `milestone.buyer_confirmed` events.
10. `milestone.settled` carries the designed `lockedAmount`, `actualAmount`, and recipient routing data. Escrow derives release/refund commands; bank appends ledger entries idempotently.
11. A buyer issue uses the exact `milestone.flagged`, seller response/contest states, and `milestone.dispute_resolved` event. No contract-level `dispute()` is used.
12. Level 1.5/Level 2 uses the exact `milestone.level2_provisional_settled`, `milestone.level2_buffer_reconciled`, and `milestone.level2_terminal_settled` events with explicit seller-release/buyer-refund legs. Contract-service calculates legs; escrow validates conservation and emits only strictly-positive bank commands; bank never recalculates X15/X2/rates.
13. `inspection-service` emits `inspection.report_confirmed` with `InspectionSettlementResultV1`, `resultHash`, `reportFileHash`, revised `reportHash`, correlation fields and actor attribution. Contract-service verifies both hashes and applies `min(batchAmount, acceptedQuantityKg * agreedPrice)` only to the matching `CONTESTED` milestone, without applying tolerance twice.
14. When every milestone is `SETTLED`, `contract-service` publishes `contract.settled`; a party-triggered cancellation publishes `contract.cancelled`. Escrow releases/refunds/seizes contract-level deposits according to the design.
15. Reputation consumes the exact settlement, penalty, dispute and AML signals. User-service applies lock projections monotonically by `lockRevision`.
16. `LedgerAuditReconciliationJob` verifies ledger amounts against the audit chain before the flow is accepted as complete.

## Frozen REST surface for the first executable slice

Contract:

- `POST /api/v1/contracts`
- `GET /api/v1/contracts` and `GET /api/v1/contracts/{id}`
- `PATCH /api/v1/contracts/{contractId}/terms`
- `POST /api/v1/contracts/{contractId}/sign/initiate`
- `POST /api/v1/contracts/{contractId}/sign/verify`
- `POST /api/v1/contracts/{contractId}/cancel`
- `POST /api/v1/contracts/{contractId}/activation/retry-deposit-lock`
- `POST /api/v1/contracts/{contractId}/activation/mark-failed`

Milestone:

- `POST /api/v1/contracts/{contractId}/milestones/{milestoneId}/weigh`
- `POST /api/v1/contracts/{contractId}/milestones/{milestoneId}/confirm`
- `POST /api/v1/contracts/{contractId}/milestones/{milestoneId}/flag`
- `POST /api/v1/contracts/{contractId}/milestones/{milestoneId}/respond`
- `POST /api/v1/contracts/{contractId}/milestones/{milestoneId}/force-majeure`
- `POST /api/v1/contracts/{contractId}/milestones/{milestoneId}/force-majeure/resolve`

The action names and paths are now authoritative Markdown. Admin activation recovery is part of the executable safety slice; Level 2 review and audit evidence export remain outside the first public REST slice.

## Hard invariants

- Duplicate `eventId` never creates a second money side effect.
- Signed terms and signatures are immutable; both signatures share one terms hash.
- A terminal contract has no outstanding lock.
- Audit records and anchors are append-only and tamper-evident.
- Audit-service is the only audit-record writer and consumes canonical events directly.
- Analytics and notification failures do not change money state.
- OTP failure cannot produce a delayed send for the old request.

## Closed flow contracts

- OQ-02/OQ-02b is closed by `InspectionSettlementResultV1` and its hash/money rules.
- OQ-03 is closed by the ES256/RFC8785 External Verifier request contract and exact errors.
