# Phase 2 Golden Flow Contract

## Scenario

Typed listing declaration -> server-derived goods snapshot + immutable negotiated terms -> two OTP-bound signatures -> bounded milestone price selection -> deposit lock/milestone funding -> delivery/inspection measurement -> contract-owned quality assessment -> attribution -> `remedy.finalized` -> bank/reputation consequences -> lifecycle termination or settlement -> audit/analytics/notification.

All quantities/weights are canonical kilograms (`kg`). Timestamps are stored/serialized in UTC; business deadlines are calculated in `Asia/Ho_Chi_Minh`, date-only deadlines close at `23:59:59.999 ICT`, and business days use Monday-Friday minus the annually maintained Vietnam national-holiday list.

## Frozen sequence

1. `product-service` exposes an approved listing whose required `declaredQualitySpec` is discriminated by `Category.commodity` and whose `ListingResponse` includes opaque `listingVersionToken` backed by canonical UTC `updatedAt`. New listings cannot omit or mismatch the spec. Legacy listings may read with a null declaration, but their next edit/republish must supply it. Listing/offer eligibility still fails closed on unavailable/ineligible/locked users.
2. Create/PATCH accepts `ContractTermsInput`, never client-supplied `goodsTerms` or `totalContractValue`. A new client echoes `listingVersionToken`; contract-service compares it with the current listing before snapshot and returns `409 LISTING_VERSION_MISMATCH` without creating a contract when stale. Missing/null token is legacy-only. After the guard passes, contract-service snapshots Listing/Product/Category/Plot into `GoodsTermsSnapshot`, normalizes origin as `{regionText, plotRefs[]}`, and derives nominal `totalContractValue = agreedPrice × total committedQuantity`. Source edits/deletion after contract creation cannot change the snapshot.
3. `ContractTermsSnapshot` includes goods, committed quality/deviation, delivery risk/title/cost allocation, inspection requirement with `inspectionCostResponsibility = LOSER_PAYS`, `LegalProfile`, currency, signed `priceBand` and `priceAdjustmentRule`. Every nested signed field is included in canonical bytes for `signedContentHash`. Terms become immutable after the first signature; legacy signed contracts remain read-only/operable, while legacy drafts cannot sign until all required terms exist.
3a. `InitiateSign` persists the exact OTP challenge and synchronously calls `POST /internal/v1/notifications/otp-email`. `VerifyOtpAndSign` binds `otpId`, caller, contract, role and the same terms hash. Two signatures must share one `signedContentHash`.
4. `contract-service` publishes `contract.signed` once. `escrow-service` issues one `bank.lock_requested` per required deposit leg and changes projection state only after bank confirmation.
5. `escrow.deposit_locked` is the only activation signal. The contract moves `SIGNED -> ACTIVE`, then milestone 1 enters funding/delivery flow.
6. Before funding request, Buyer or Seller may propose one price inside the signed band and only the counterparty may accept via JWT + `Idempotency-Key`. No OTP is used for this bounded runtime selection. One pending proposal may be overwritten; only the accepted record is immutable/auditable and publishes `milestone.price_adjusted`. Funding never waits for a proposal: it uses accepted `effectiveUnitPrice`, otherwise `agreedPrice`.
6a. Each milestone funding projection moves `FUNDING_PENDING -> LOCKED` or `FUNDING_FAILED`. `batchAmount = committedQuantity × effectiveUnitPrice` is fixed when funding is requested and is never true-upped at settlement. Seller delivery obligations do not begin before `LOCKED`. After lock, seller deadlines use `effectiveDeliveryDeadline = expectedDeliveryDate + fundingDelayBusinessDays + graceDays`.
7. Funding failure retries the failed leg. After `fundingCureWindowDays`, buyer non-funding is Rổ A: a persisted `AttributionDecision` has `breachCaseId = null`, `decisionSource = SYSTEM`, `finalBreachingRole = BUYER`.
8. Seller weighing, buyer receipt, clean confirmation, flagging, force majeure and inspection follow the canonical milestone state machine. Optional batch certificates may be attached at seller/buyer weigh as delivery/dispute evidence only; they are never parsed as actual quality, included in `resultHash`, or treated as a confirmed report. `NONE_UNLESS_DISPUTED` has no inspection/actual metrics on the clean path. `MANDATORY_BEFORE_SETTLEMENT` uses an auxiliary guard without adding a milestone state and accepts reports in `BUYER_RECEIVED` or `CONTESTED`.
8a. Inspection-service owns measured/accepted quantity, `measurementStatus`, and commodity-discriminated actual metrics inside `resultHash`; it does not own disposition or money. Rice actual metrics never contain `varietyName`. Contract-service deterministically compares actuals with the committed spec/deviation policy; a reviewer verifies those signed/hashed inputs and does not make a subjective quality ruling.
8b. Quality disputes are always Rổ B and converge on `AttributionDecision -> RemedyDecision -> remedy.finalized`. `CONFORMING` and `PARTIALLY_CONFORMING` have null final breach role and no penalty/reputation; partial applies the maximum discount once. `NON_CONFORMING` attributes Seller with `QUALITY_BELOW_COMMITTED`, refunds the rejected milestone and applies existing remedy policy. `INCONCLUSIVE` releases no money and continues reinspection/Level 2.
8c. Clean quantity settlement keeps existing Delta 1/Delta 2 rules. Inspected/contested calculation uses `min(lockedAmount, acceptedQuantityKg × effectiveUnitPrice)` without applying Delta 2 again. `milestone.buyer_confirmed` never triggers money; `milestone.settled` is clean-path settlement only and cannot coexist with `remedy.finalized` for the same quality resolution.
9. Buyer non-receipt is Rổ A only after notice/window expiry and objective delivery evidence. Without objective evidence, contract-service opens a Rổ B `BreachCase` and moves `REPORTED -> UNDER_REVIEW -> RESOLVED`.
10. Rổ B outcomes create `AttributionDecision` only after review. `requestedBy`, `allegedBreachingRole` and `finalBreachingRole` remain separate; allegation alone cannot seize funds or lock reputation.
11. Every final attribution/no-fault decision feeds the remedy calculator. Normal completion creates the existing SYSTEM/no-fault attribution plus a `RemedyDecision` whose owner-return legs release the buyer deposit and optional seller deposit. `remedy.finalized` is the sole canonical consequence event for escrow/bank money legs and reputation lock. Each leg has a unique `remedyLegId`; `remedyDecisionId` groups the set and is not unique per ledger row.
11a. `remedy.finalized` carries `attributionDecisionId`, `buyerId`, `sellerId`, nullable `qualityDisposition` and explicit `affectedMilestoneIds[]`; nullable breach fields are present on the wire. Reputation maps the final role to the participant ID from this event without a cross-service lookup. Notification renders `qualityDisposition` with final attribution and never infers blame from `requestedBy`.
12. `contract.terminated` carries lifecycle facts only: `terminationType`, `requestedBy`, `finalBreachingRole`, `breachReasonCode`, affected milestones and replacement pointers. Escrow and reputation must not consume it for consequences. Contract-service publishes it only after the existing internal bank-ledger read confirms every expected remedy leg and zero remaining lock.
13. `finalBreachingRole = null` produces only owner-return settlement/refund legs and never contractual penalty, deposit forfeiture or reputation lock.
14. Mutual replacement commits only after the new contract is `SIGNED`: old contract `ACTIVE -> REPLACEMENT_PENDING -> SUPERSEDE_REFUND_PENDING -> SUPERSEDED`. Old locks must be fully refunded before `SUPERSEDED`; later activation failure of the new contract never rolls old history back.
15. Bank persists `sourceEventId`, `fundType`, `remedyDecisionId`, `remedyLegId`, `entryType` and `amount`. Bank never calculates attribution/remedy and never accepts zero/negative commands.
16. Reputation persists immutable completion/dispute facts and derives score at query time. Its only negative input is `remedy.finalized`, gated by `reputationEligible = true`, idempotent by `remedyDecisionId`.
17. Audit-service deduplicates `audit_record` by `sourceEventId`, preserves causation/correlation, and inserts an OTS anchor request into a durable outbox in the same transaction. Replay cannot create another record, anchor or evidence email.
18. Analytics records `terminationType`, `requestedBy`, `finalBreachingRole` and `breachReasonCode`; monthly jobs recompute and overwrite each touched month bucket. Notification templates use termination type, final attribution and remedy outcome, never `requestedBy` as blame.
19. Completion requires bank/audit reconciliation and the invariant that every terminal contract has zero remaining lock. Normal settlement stays `ACTIVE`, and generic termination stays in its current non-terminal state, until that guard succeeds; a failed/missing bank leg cannot produce `contract.settled` or `contract.terminated`.

## Frozen goods and quality shapes

| Commodity | Committed identity/exact field | Measurable committed/actual metrics |
|---|---|---|
| `COFFEE` | `type = ARABICA|ROBUSTA` | `moisturePercent`, `foreignMatterPercent`, `blackBrokenBeansPercent` |
| `RICE` | `varietyName` (goods/committed only) | `brokenPercent`, `moisturePercent`, `chalkyKernelPercent`, `foreignMatterPercent`, `purityPercent` |
| `RUBBER` | `grade = SVR_3L|SVR_5|SVR_10|SVR_20` | `dirtPercent`, `ashPercent`, `volatileMatterPercent`, `nitrogenPercent`, `plasticityRetentionIndex` |
| `CASHEW` | `grade = W180|W210|W240|W320|W450` | `moisturePercent`, `defectiveKernelPercent`, `foreignMatterPercent`, `kernelOutturnLbsPer80Kg` |

Every numeric deviation entry is `{direction, tolerance, penalty, reject, discountRate}` with `tolerance <= penalty < reject`. Only coffee `type` and rubber/cashew `grade` use exact-match rejection. Rice deviation keys are numeric-only.

`originSnapshot` is exactly `{regionText, plotRefs[]}`. Coffee/rubber require non-empty plot refs and may carry nullable `traceabilitySnapshot`; rice/cashew require an empty plot list and cannot carry traceability evidence in the signed goods variant.

## Frozen REST surface

The OpenAPI file freezes the owner-design command paths:

- Contract/signature: create, list/detail, pre-sign terms update, sign initiate/verify, withdraw offer, mutual termination/replacement, breach report/review/resolve, termination execute.
- Activation recovery: retry deposit lock and mark activation failed.
- Milestone actions: weigh, confirm, flag, respond, force-majeure claim/resolve, milestone funding retry, bounded price propose/accept.
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
- Input cannot supply server-owned `goodsTerms`/`totalContractValue`; source records cannot rehydrate or mutate an existing snapshot.
- New clients cannot create from a stale listing view; `listingVersionToken` is checked before snapshot and mismatch is a non-mutating `409`.
- Every contract has exactly one Buyer and one Seller; multi-party/consortium/broker structures are outside Phase 2.
- `agreedPrice` remains the signed base price and deposits always use nominal `totalContractValue`; milestone adjustments affect only pre-funding milestone amount.
- Rice `varietyName` is goods/committed identity only and cannot appear in actual metrics or deviation policy.
- Multiple penalty-zone quality metrics apply the greatest `discountRate` once; quality adjustment is not a contractual-penalty ledger leg.
- Quality `MILESTONE_PAYMENT` legs conserve exactly one `batchAmount`; penalty and deposit-forfeiture reconcile against separate fund sources. Reject penalty base is `effectiveUnitPrice × committedQuantity`, never `totalContractValue`.
- `NON_CONFORMING` fails only the affected milestone and never auto-terminates the contract; a different commercial bargain requires mutual supersede.
- Final quality resolution has exactly one money trigger: `remedy.finalized`, never a second `milestone.settled`.
- Contract terminal states (`SETTLED`, `TERMINATED`, `WITHDRAWN`, `SUPERSEDED`, `ACTIVATION_FAILED`) require zero locked funds; terminal lifecycle events are published only after the guard succeeds.
- Both Rổ A and Rổ B converge on `AttributionDecision -> RemedyDecision -> remedy.finalized`.
- Normal completion uses the same decision/event path with nullable breach fields and owner-return legs; `contract.settled` never runs money.
