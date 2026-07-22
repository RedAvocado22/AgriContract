# Enum Ownership

Only the owning service defines and persists a business enum. Consumers use the serialized contract value and must not create a competing state machine or infer values from another field.

| Enum / value family | Owner | Canonical values / source |
|---|---|---|
| Application roles | user-service / Keycloak | `BUYER`, `SELLER`, `ADMIN`, `INSPECTOR`, `OPERATOR`; user design §2, governance §5 |
| KYC state | user-service | `PENDING`, `VERIFIED`, `REJECTED`; user design §2 |
| Contract state | contract-service | `OFFERED`, `NEGOTIATING`, `SIGNED`, `ACTIVE`, `REPLACEMENT_PENDING`, `SUPERSEDE_REFUND_PENDING`, `ACTIVATION_REFUND_PENDING`, `SETTLED`, `TERMINATED`, `WITHDRAWN`, `SUPERSEDED`, `ACTIVATION_FAILED`; milestone design §3.1/§6.6 |
| Termination taxonomy | contract-service | `WITHDRAW_OFFER`, `MUTUAL_TERMINATION`, `MUTUAL_REPLACEMENT`, `TERMINATION_FOR_BREACH`, `TERMINATION_FOR_FORCE_MAJEURE`, `ACTIVATION_FAILURE`; milestone design §6.1 |
| Attribution roles | contract-service | `requestedBy` = `BUYER|SELLER|SYSTEM`; `allegedBreachingRole` and `finalBreachingRole` = `BUYER|SELLER|null`; milestone design §6.0/§6.4 |
| `BreachCase` state/severity | contract-service | `REPORTED`, `UNDER_REVIEW`, `RESOLVED`; `MINOR`, `MATERIAL`; milestone design §6.4 |
| Attribution decision source | contract-service | `SYSTEM`, `ADMIN`, `INSPECTOR_L1_5`, `INSPECTOR_L2`, `MUTUAL`; milestone design §6.4b |
| Breach reason | contract-service | Seller, buyer and no-fault codes from milestone design §6.4.1, including the sole quality code `QUALITY_BELOW_COMMITTED`; consumers do not derive eligibility from them |
| Legal profile | contract-service | `VN_COMMERCIAL_LAW`, `VN_CIVIL_LAW`; damages policies `COMMERCIAL_CUMULATIVE_IF_PROVEN`, `CIVIL_PENALTY_ONLY_UNLESS_EXPRESSLY_CUMULATIVE`, `EXPRESS_PENALTY_ONLY`; milestone design §2.1b |
| Commodity quality spec discriminator | product-service declaration; contract-service committed snapshot | `COFFEE`, `RICE`, `RUBBER`, `CASHEW`; declared/committed schemas must match `Category.commodity`; product §8b and milestone §2.1c |
| Coffee type | product/contract declaration, inspection measurement | `ARABICA`, `ROBUSTA`; exact mismatch may reject |
| Rubber grade | product/contract declaration, inspection measurement | `SVR_3L`, `SVR_5`, `SVR_10`, `SVR_20`; exact mismatch may reject |
| Cashew grade | product/contract declaration, inspection measurement | `W180`, `W210`, `W240`, `W320`, `W450`; exact mismatch may reject |
| Delivery allocation | contract-service | Responsibility `SELLER_DELIVERS|BUYER_COLLECTS`; risk/title transfer and cost-bearer values from `DeliveryTerms` OpenAPI |
| Inspection requirement/cost policy | contract-service | `NONE_UNLESS_DISPUTED`, `MANDATORY_BEFORE_SETTLEMENT`; required tier `LEVEL_1_5|LEVEL_2`; cost policy is only `LOSER_PAYS` in Phase 2 |
| Price adjustment workflow | contract-service | Acceptance `BOTH_ACCEPT`; cutoff `BEFORE_FUNDING_REQUEST`; proposal `PENDING`, accepted record immutable |
| Signature role | contract-service | `BUYER`, `SELLER`; signature design §3 |
| Milestone delivery state | contract-service | `CREATED`, `IN_PROGRESS`, `SELLER_WEIGHED`, `BUYER_RECEIVED`, `AWAITING_SELLER_RESPONSE`, `CONTESTED`, `FORCE_MAJEURE_PENDING_REVIEW`, `SETTLED`; milestone design §3.2 |
| Milestone funding state | escrow-service projection | `FUNDING_PENDING`, `LOCKED`, `FUNDING_FAILED`; milestone design §6b |
| Deposit state | escrow-service projection | `DEPOSIT_LOCKED`, `DEPOSIT_RELEASED`, `DEPOSIT_SEIZED`; milestone design §2.3 |
| Escrow milestone state | escrow-service projection | `LOCKED`, `PROVISIONALLY_RELEASED`, `RELEASED`, `REFUNDED`, `PENALIZED`; milestone design §2.3/§3.2 |
| Ledger entry type | bank-service | `LOCK_BUYER_DEPOSIT`, `LOCK_SELLER_DEPOSIT`, `LOCK_MILESTONE`, `RELEASE_TO_SELLER`, `REFUND_TO_BUYER`, `DEPOSIT_FORFEITURE`, `CONTRACTUAL_PENALTY`, `DAMAGES_COMPENSATION`; bank design §2 |
| Ledger fund type | bank-service | `BUYER_DEPOSIT`, `SELLER_DEPOSIT`, `MILESTONE_PAYMENT`; bank design §2 |
| Remedy type | contract-service decision; serialized to escrow/bank | `NONE`, `PAYMENT_SETTLEMENT`, `PAYMENT_REFUND`, `DEPOSIT_FORFEITURE`, `CONTRACTUAL_PENALTY`, `DAMAGES_COMPENSATION`; milestone §6.7 and bank §2 |
| File channel/state | file-service | `DIRECT_UPLOAD`, `EMAIL_INTAKE`, `SYSTEM_GENERATED`; `PROCESSING`, `READY`, `FAILED`; file design §2 |
| Notification state/channel | notification-service | `PENDING`, `SENT`, `FAILED`; `EMAIL`; notification design §3 |
| Inspection tier/measurement status | inspection-service | `LEVEL_1_5`, `LEVEL_2`; `COMPLETE`, `INCONCLUSIVE`; inspection §2.3 |
| Quality disposition | contract-service | `CONFORMING`, `PARTIALLY_CONFORMING`, `NON_CONFORMING`, `INCONCLUSIVE`; derived from committed spec/deviation policy plus inspection measurement, never supplied by inspection-service |
| Audit source type | audit-service | Full catalog including `BREACH_REPORTED`, `REMEDY_FINALIZED`, `CONTRACT_TERMINATED`; hash-chain §2.4 |
| Commodity | product-service category owner | `COFFEE`, `RICE`, `RUBBER`, `CASHEW`; product §9 |

## Superseded values

- `CANCELLED`, `contract.cancelled`, `cancel(initiatedBy)` and `initiatedBy`-as-fault are not canonical Phase 2 contract flow.
- `SEIZE_PENALTY` is retired; deposit forfeiture, contractual penalty and damages compensation are distinct.
- Boolean `penaltyAndDamagesCumulative` is retired; use `damagesPolicy`.
- `DELIVERED`, `confirmDelivery`, `ContractDeliveredEvent` and `contract.delivered` are Phase 1 dead paths.
- `REFUNDED_PARTIAL` and a persisted OTP status enum remain non-canonical.
- Inspector-owned `qualityDisposition` is retired. `InspectionSettlementResultV1` carries `measurementStatus` and typed actual metrics instead.

## Consumer rule

Eligibility is explicit: escrow executes `remedyLegs`; reputation reads `reputationEligible`; analytics and notification read `qualityDisposition` plus final attribution. No consumer derives fault, penalty, cost responsibility or lock eligibility from `requestedBy`, `flaggedBy`, `allegedBreachingRole` or a reason-code shortcut.
