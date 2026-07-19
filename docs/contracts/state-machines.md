# AgriContract Phase 2 State Machines

Sources: milestone escrow §§3-7, signature §§3-7, bank §§2-4, reputation §§2-4, file §2, notification §3, and the 55-row Verification Matrix.

## Contract lifecycle

```text
OFFERED -> NEGOTIATING -> SIGNED -> ACTIVE -> SETTLED
    |          |
    +----------+-> WITHDRAWN
                         |           |\
                         |           | -> REPLACEMENT_PENDING -> SUPERSEDE_REFUND_PENDING -> SUPERSEDED
                         |           | -> TERMINATED
                         |-> ACTIVATION_REFUND_PENDING -> ACTIVATION_FAILED
```

- `SIGNED` means two signatures over the same immutable terms hash.
- `ACTIVE` requires `escrow.deposit_locked`; signing is not activation.
- `ACTIVE` persists while milestones execute; Phase 2 has no `DELIVERED` state.
- `TERMINATED` is classified by `terminationType`; it does not imply breach.
- `REPLACEMENT_PENDING` starts only after the replacement contract reaches `SIGNED`.
- `SUPERSEDE_REFUND_PENDING` cannot reach `SUPERSEDED` until every old-contract lock is confirmed returned.
- `ACTIVATION_REFUND_PENDING` cannot reach `ACTIVATION_FAILED` until every partially locked deposit is returned to its owner.
- Normal completion keeps the contract `ACTIVE` after the last milestone settles, emits the existing no-fault `remedy.finalized` owner-return legs, and reaches `SETTLED` only after ledger reconciliation proves every leg complete and remaining lock is zero.
- Generic termination likewise keeps the current state non-terminal after `remedy.finalized`; `TERMINATED` and `contract.terminated` are committed/published only after the same reconciliation guard succeeds.
- Every terminal state (`SETTLED`, `TERMINATED`, `WITHDRAWN`, `SUPERSEDED`, `ACTIVATION_FAILED`) requires total locked funds for the contract to equal zero.

`WITHDRAW_OFFER` is a terminal `WITHDRAWN` state transition from `OFFERED` or `NEGOTIATING` before the second signature. It publishes `contract.terminated` with `terminationType = WITHDRAW_OFFER`, null attribution fields and no remedy legs. The legacy `CANCELLED`/`contract.cancelled` flow is retired.

## Attribution and remedy

```text
Rổ A: objective timeout + expired notice/cure window
       -> AttributionDecision(breachCaseId = null, decisionSource = SYSTEM)

Rổ B: BreachCase REPORTED -> UNDER_REVIEW -> RESOLVED
       -> AttributionDecision(breachCaseId = case id)

Both -> RemedyDecision -> remedy.finalized
```

- `requestedBy` records who initiated; `allegedBreachingRole` records the allegation; `finalBreachingRole` records the final conclusion.
- Allegation cannot trigger seizure, contractual penalty, damages, deposit forfeiture or reputation lock.
- Buyer non-receipt is Rổ A only with objective delivery evidence after notice/window expiry; otherwise it is Rổ B.
- `finalBreachingRole = null` is no-fault and produces no negative consequence.
- `remedy.finalized` is the sole money/reputation consequence trigger. `contract.terminated` is lifecycle/audit/analytics/notification only.
- Completion is confirmed through the existing internal bank-ledger read filtered by `contractId`; contract-service verifies all expected `remedyLegId` values and computes zero remaining lock before publishing a terminal lifecycle event. Bank result events remain escrow-owned.

## Signature and OTP

```text
OTP challenge: created -> verified
               |-> expired / attempts exhausted / invalidated by terms change
Signature: none -> first BUYER|SELLER -> matching second signature -> SIGNED
```

OTP state is derived from persisted challenge fields; no OTP status enum is stored. Challenge binding includes `otpId`, user, contract, signer role and terms hash. OTP delivery is synchronous and cannot be retried later in the background after a caller-visible failure.

## Milestone delivery

```text
CREATED -> IN_PROGRESS -> SELLER_WEIGHED -> BUYER_RECEIVED -> SETTLED
                                      \-> AWAITING_SELLER_RESPONSE -> CONTESTED -> SETTLED
IN_PROGRESS|SELLER_WEIGHED -> FORCE_MAJEURE_PENDING_REVIEW -> normal/settled path
```

- Seller deadline checks use `effectiveDeliveryDeadline`, never raw `expectedDeliveryDate` after delayed funding.
- Buyer timeout at `BUYER_RECEIVED` may auto-confirm only while the milestone remains in that state and no inspection is pending.
- `SELLER_WEIGHED` timeout never auto-settles from seller-only evidence.
- Delta 1 above threshold opens Rổ B; pro-rata delivered value may settle, but penalty waits for attribution.
- Level 2 provisional/reconcile/terminal events carry explicit positive release/refund legs and preserve batch conservation.

## Milestone funding

```text
FUNDING_PENDING -> LOCKED
       \-> FUNDING_FAILED -> FUNDING_PENDING (retry)
                           \-> buyer Rổ A after cure window
```

- Seller preparation/delivery obligation begins only at `LOCKED`.
- `fundingDelayBusinessDays` is measured from request to successful lock and feeds `effectiveDeliveryDeadline`.
- Confirmed bank/system outage pauses cure attribution; technical failure is not buyer breach.

## Escrow and bank

```text
Deposit leg: required -> lock requested -> locked
                         \-> failed -> retry same leg
Escrow milestone: FUNDING_PENDING -> LOCKED -> PROVISIONALLY_RELEASED -> RELEASED|REFUNDED|PENALIZED
```

Escrow stores state only. Bank ledger is the money source of truth. Every bank request carries `sourceEventId`, `fundType`, nullable/non-null remedy identifiers as applicable, `entryType`, and strictly positive `amount`. `remedyLegId` is unique; `remedyDecisionId` groups multiple legs.

## Reputation

`lock_entry` is insert-only. Negative processing occurs only for `remedy.finalized` with `reputationEligible = true`, unique by `remedyDecisionId`. Completed-contract and dispute-outcome facts are immutable; score is derived at query time. User-service applies lock projection monotonically by persisted `lockRevision`.

## Audit anchor

```text
domain event -> audit_record + anchor_request_outbox (same transaction)
             -> OTS worker -> audit_anchor -> optional evidence notification
```

`audit_record.source_event_id` is unique. Replay returns/no-ops on the existing record and cannot create another outbox request, anchor or evidence email. `sourceEventId`, `causationId` and `correlationId` are preserved in the record hash commitment.

## File and notification

```text
File: PROCESSING -> READY | FAILED
Notification: PENDING -> SENT | FAILED
```

Evidence files become usable only after `file.ready`. Notification commands remain separate from domain events and deduplicate on `(event_id, recipient_email, notification_type)`.
