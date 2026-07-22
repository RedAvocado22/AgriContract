# Contract Questions

No unresolved contract blocker remains after the frozen Phase 2 design review.

## Closed decisions

| Topic | Resolution | Authoritative source |
|---|---|---|
| Attribution | Keep `requestedBy`, `allegedBreachingRole`, and nullable `finalBreachingRole` separate; Rổ A creates a system decision directly and Rổ B resolves a `BreachCase`. | `milestone-escrow-phase2-design.md` sections 6.0-6.7 |
| Consequence trigger | `remedy.finalized` is the only money and negative-reputation trigger; `contract.terminated` is lifecycle/audit/analytics/notification only. | Milestone, bank, reputation, analytics and notification Phase 2 designs |
| Cancellation | `/cancel`, `cancel(initiatedBy)`, and `contract.cancelled` are retired. Explicit withdraw, mutual termination, breach review, replacement, termination and funding-retry paths are frozen in `golden-flow-api.yaml`. | `milestone-escrow-phase2-design.md` termination taxonomy |
| Legal profile | Use `governingLaw`, `contractType`, `maxContractualPenaltyRate`, and `damagesPolicy`; the cumulative boolean is retired. | `milestone-escrow-phase2-design.md` section 2.1b |
| Remedy semantics | Deposit forfeiture, contractual penalty and damages are separate legs; `remedyLegId` is the bank uniqueness unit. | Milestone and bank Phase 2 designs |
| Funding | Funding has pending/locked/failed states, retry/cure, partial-lock refund and an effective delivery deadline. | `milestone-escrow-phase2-design.md` sections 3.1 and 6b |
| Replacement | The old contract becomes `SUPERSEDED` only after the replacement is signed and old locks are refunded. | `milestone-escrow-phase2-design.md` section 6.6 |
| Reputation history | Persist immutable completion/dispute facts and derive score at query time; negative lock input is only `remedy.finalized`. | `reputation-service-phase2-design.md` |
| Audit replay | Deduplicate by source event and use durable anchor retry/outbox. | `hash-chain-phase2-design.md` |
| Analytics | Persist type/requester/final role/reason separately and overwrite every recomputed late-arrival month bucket. | `analytics-service-phase2-design.md` |
| Notification | Termination and breach content uses final attribution/remedy outcome and remains separate from domain events. | `notification-service-phase2-design.md` |
| Listing quality | New listings require a commodity-matching typed `declaredQualitySpec`; legacy null remains readable only until edit/republish. | `product-phase2-design.md` section 8b |
| Contract snapshot | Create/PATCH accepts `ContractTermsInput`; contract-service derives `goodsTerms` and nominal `totalContractValue`, and hashes the full `ContractTermsSnapshot`. | `milestone-escrow-phase2-design.md` section 2.1c and golden-flow OpenAPI |
| Rice identity | `varietyName` belongs to goods/committed identity only and never appears in actual metrics or deviation policy. | Product, milestone and inspection Phase 2 designs |
| Quality ownership | Inspection-service reports quantity/typed measurements; contract-service derives disposition, max-one-time discount and final entitlement. | `inspection-phase2-design.md` section 2.3/4; milestone section 3.3 |
| Quality resolution | Every quality dispute is Rổ B and has one money trigger through `remedy.finalized`; disposition maps explicitly to attribution/remedy, inspection cost and fund-source conservation. | `milestone-escrow-phase2-design.md` section 3.3/7.1 |
| Quality reason code | The only quality breach reason is `QUALITY_BELOW_COMMITTED`. | OpenAPI, domain events and notification schemas |
| Inspection cost | Signed policy is `LOSER_PAYS` only in Phase 2; `SPLIT` is a future additive extension. | `InspectionTerms` OpenAPI and milestone section 2.1c/3.3 |
| Delivery certificate | Optional per-batch certificate refs are evidence only and cannot populate actual metrics, `resultHash` or disposition. | Milestone section 3.3; inspection section 2.3 |
| Delivery terms | Signed terms allocate responsibility, risk, title and cost, while runtime address/carrier data remains outside the signed snapshot. | `milestone-escrow-phase2-design.md` section 2.1c |
| Price adjustment | JWT both-accept selects one price inside the signed band before funding; no OTP, terms mutation, hash mutation, funding delay or settlement true-up. Legal review remains required before productization. | `milestone-escrow-phase2-design.md` section 6c |
| Pricing dependency | pricing-service remains advisory-only and is never a funding/settlement dependency. | `pricing-service-phase2-design.md` section 4.1 |

Any future API/event/error addition requires a change to an authoritative Phase 2 design before this contract set can adopt it.
