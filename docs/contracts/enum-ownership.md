# Enum Ownership

Only the owning service defines and persists a business enum. Other services consume the serialized value from the event contract and must not create a competing state machine.

| Enum / value family | Owner | Canonical source |
|---|---|---|
| `BUYER`, `SELLER`, `ADMIN`, `INSPECTOR`, `OPERATOR` | user-service / Keycloak role authority | `user-service-phase2-design.md` section 2.1; governance section 5 |
| KYC `PENDING`, `VERIFIED`, `REJECTED` | user-service | `user-service-phase2-design.md` section 2 |
| Contract `OFFERED`, `NEGOTIATING`, `SIGNED`, `ACTIVE`, `SETTLED`, cancellation/activation-failure states | contract-service | `milestone-escrow-phase2-design.md` section 3.1 |
| Signature `BUYER`, `SELLER` | contract-service | `signature-phase2-design.md` section 3 |
| Milestone state and decision values (`CREATED`, `IN_PROGRESS`, `SELLER_WEIGHED`, `BUYER_RECEIVED`, `SETTLED`, `FLAG_ISSUE`, `CONTESTED`, force-majeure and Level 2 branches) | contract-service | `milestone-escrow-phase2-design.md` section 3.2 |
| Escrow milestone projection (`LOCKED`, `PROVISIONALLY_RELEASED`, `RELEASED`, `PENALIZED`, `REFUNDED`) | escrow-service projection | `milestone-escrow-phase2-design.md` sections 2.3, 3.2 and 7.1 |
| Activation recovery (`ACTIVATION_REFUND_PENDING`, `ACTIVATION_FAILED`) | contract-service | `milestone-escrow-phase2-design.md` section 3.1 |
| Deposit states `DEPOSIT_LOCKED`, `DEPOSIT_RELEASED`, `DEPOSIT_SEIZED` | escrow-service projection | `milestone-escrow-phase2-design.md` sections 2.3 and 7.2 |
| Ledger entry types `LOCK_BUYER_DEPOSIT`, `LOCK_SELLER_DEPOSIT`, `LOCK_MILESTONE`, `RELEASE_TO_SELLER`, `SEIZE_PENALTY`, `REFUND_TO_BUYER` | bank-service | `bank-service-phase2-design.md` section 2 |
| File `DIRECT_UPLOAD`, `EMAIL_INTAKE`, `SYSTEM_GENERATED`; `PROCESSING`, `READY`, `FAILED` | file-service | `file-service-phase2-design.md` sections 2 and 2.2 |
| Notification `PENDING`, `SENT`, `FAILED`; channel `EMAIL` | notification-service | `notification-service-phase2-design.md` section 3 |
| Inspection tiers, `InspectionSettlementResultV1.qualityDisposition`, and report confirmation values | inspection-service | `inspection-phase2-design.md` sections 2-4 |
| Reputation lock ledger values and `lockRevision` | reputation-service | `reputation-service-phase2-design.md` sections 2 and 7 |
| Audit `source_type` values including `MILESTONE_EVENT`, `CONTRACT_SIGNED`, `INSPECTION_REPORT`, `EXTERNAL_INSPECTION_REPORT`, `LEVEL2_INSPECTION_COMMISSIONED`, `EXTERNAL_VERIFIER_KEY_REGISTERED`, `SECURITY_LOCK_TRIGGERED`, `SECURITY_UNLOCK_TRIGGERED`, `STRUCTURING_REPORT`, `AML_RISK_CLEARED` | audit-service | `hash-chain-phase2-design.md` section 2.4 |
| Commodity `COFFEE`, `RICE`, `RUBBER`, `CASHEW` | product-service category owner; copied in `contract.signed` | `product-phase2-design.md` section 9; `milestone-escrow-phase2-design.md` section 7.2 |

## Prohibited normalization

- Do not rename catalog event names to fit a different naming convention.
- Do not add `contract.offered`, `contract.negotiating`, or `milestone.lock_requested` without an authoritative catalog entry.
- Do not use Phase 1 `EscrowStatus` or `TransactionType` as Phase 2 source-of-truth enums until migration confirms they match the design.

## Resolution notes

- The first executable slice uses only escrow states explicitly named by the design; no additional enum values are introduced.
- `REFUNDED_PARTIAL` is not canonicalized from SDS-only material.
- OTP challenge lifecycle is derived from persisted fields; no status enum is owned or persisted.
- Error-code ownership is defined in `error-catalog.md`; business services own domain reason details while the shared transport vocabulary remains stable.
