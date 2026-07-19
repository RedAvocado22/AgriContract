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

Any future API/event/error addition requires a change to an authoritative Phase 2 design before this contract set can adopt it.
