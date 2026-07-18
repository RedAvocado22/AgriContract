# AgriContract State Machines

Sources: `milestone-escrow-phase2-design.md` sections 3-6, `signature-phase2-design.md` sections 3-7, `file-service-phase2-design.md` section 2.2, `notification-service-phase2-design.md` section 3.1, `reputation-service-phase2-design.md` section 2.1, and Verification Matrix.

## Contract

```text
OFFERED -> NEGOTIATING -> SIGNED -> ACTIVE -> SETTLED
                         \-> ACTIVATION_REFUND_PENDING -> ACTIVATION_FAILED
```

- `SIGNED` means both buyer and seller have valid signatures over the same terms hash.
- Terms are immutable once the first signature exists; the second signature must match the first hash.
- `ACTIVE` is reached only after `escrow.deposit_locked` confirms every required deposit leg.
- `ACTIVE` remains active while milestones run; there is no Phase 2 `DELIVERED` transition.
- `SETTLED` is reached only after every milestone is `SETTLED`.
- `ACTIVATION_REFUND_PENDING` and `ACTIVATION_FAILED` are required for partial-lock compensation.
- `confirmDelivery`, `ContractDeliveredEvent`, and `contract.delivered` are superseded Phase 1 paths.

## Signature and OTP

```text
OTP challenge: CREATED -> VERIFIED
                         \-> EXPIRED / LOCKED / INVALIDATED
Signature: no row -> one BUYER or SELLER row -> both rows -> contract SIGNED
```

Authoritative behavior:

- OTP hash, attempt count, expiry, resend and challenge binding belong to `contract-service`.
- A terms hash change invalidates the challenge and requires a new initiation.
- The challenge is bound to `otpId`, `contractId`, signer identity and signer role.
- OTP email delivery is synchronous through `/internal/v1/notifications/otp-email`.
- No OTP challenge lifecycle is a RabbitMQ domain event.

The external HTTP paths for `InitiateSign` and `VerifyOtpAndSign` are frozen in `golden-flow-api.yaml`; the use-case semantics remain those of `signature-phase2-design.md` section 6.

## Milestone

```text
CREATED -> IN_PROGRESS -> SELLER_WEIGHED -> BUYER_RECEIVED
                                             |-> SETTLED
                                             |-> AWAITING_SELLER_RESPONSE -> CONTESTED -> SETTLED
IN_PROGRESS or SELLER_WEIGHED -> FORCE_MAJEURE_PENDING_REVIEW
FORCE_MAJEURE_PENDING_REVIEW -> BUYER_RECEIVED or SETTLED
```

Additional design-defined branches:

- `IN_PROGRESS` may enter seller-overdue handling after `expectedDeliveryDate + graceDays`.
- `SELLER_WEIGHED` has a buyer receive timeout; it must not auto-settle from seller-only evidence.
- `BUYER_RECEIVED` supports `CONFIRM_CLEAN`, buyer timeout treated as clean, or `FLAG_ISSUE`.
- `CONTESTED` is routed by the three-tier dispute service.
- `inspection.report_confirmed` may advance only the `CONTESTED` milestone whose `contractId` and `milestoneId` match the immutable `InspectionSettlementResultV1`; contract-service verifies `resultHash` and `reportHash`, then computes `min(batchAmount, acceptedQuantityKg * agreedPrice)` without applying tolerance twice. Duplicate `eventId`/`reportId` deliveries are no-ops.
- Level 2 provisional flow uses the exact event/state mechanics defined by the milestone design: provisional settlement, buffer reconciliation, then terminal settlement.
- Force-majeure evidence is processed through file-service and reviewed before resolution.

## Escrow and deposit

```text
Deposit leg: REQUIRED -> LOCK_REQUESTED -> LOCKED
                         \-> FAILED -> RETRYING -> LOCK_REQUESTED
Escrow milestone: LOCKED -> PROVISIONALLY_RELEASED -> RELEASED
                              |                    -> REFUNDED
                              |                    -> PENALIZED
```

`PROVISIONALLY_RELEASED` is the Level 2 intermediate projection. The three Level 2 events carry explicit seller-release and buyer-refund legs; no `REFUNDED_PARTIAL` value is canonicalized from SDS-only material.

Activation recovery:

```text
SIGNED -> ACTIVATION_REFUND_PENDING -> ACTIVATION_FAILED
SIGNED -> ACTIVE
```

`ACTIVATION_REFUND_PENDING` is entered only when `MarkActivationFailed` must compensate a previously locked leg. The terminal transition requires every positive refund command to receive confirmation.

- Buyer and seller deposit legs are independent.
- Retry only the failed leg.
- After retry exhaustion, `escrow.deposit_lock_failed` leaves the contract `SIGNED`.
- Admin `MarkActivationFailed` compensates every locked leg through refund confirmation before `ACTIVATION_FAILED`.
- Bank ledger is the money source of truth; escrow stores state/projection only.

The exact escrow enum values beyond the documented deposit states are owned by escrow-service and must be copied from its design migration without normalization.

## File

```text
PROCESSING -> READY
           \-> FAILED
```

The same technical state machine applies to `DIRECT_UPLOAD`, `EMAIL_INTAKE`, and `SYSTEM_GENERATED`. Ingest channel is selected by entrypoint, not caller input. `legalHold` blocks deletion even after retention expiry; deletion is a two-step tombstone/blob workflow.

## Notification

```text
PENDING -> SENT | FAILED
```

Async notifications retry according to notification-service rules and deduplicate on `(event_id, recipient_email, notification_type)`. OTP synchronous delivery returns success only after the provider accepts the request.

## Reputation lock projection

The source lock ledger is insert-only. User-service maintains a projection with monotonic `lockRevision`; stale revisions must not overwrite newer decisions. The exact lock-entry derived status is not updated in place.

## Contract resolution notes

- `OC-07` is resolved with the authoritative projection values `LOCKED`, `PROVISIONALLY_RELEASED`, `RELEASED`, `REFUNDED`, and `PENALIZED`; `REFUNDED_PARTIAL` remains non-canonical.
- `OC-08` is resolved by not introducing a persisted OTP status enum. The design defines `verified_at`, `expires_at`, and `attempt_count`; lifecycle is derived from those fields.
- Any future projection value requires an authoritative design update.
