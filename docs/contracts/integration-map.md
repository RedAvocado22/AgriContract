# AgriContract Phase 2 Integration Map

Status: contract freeze draft generated from the authoritative Phase 2 design Markdown on 2026-07-18.

## Source precedence and counting

1. `docs/phase_2/design/*.md` owns business rules, state, APIs, events, invariants, security, and service ownership.
2. `docs/phase_2/design/verification-matrix-phase2.md` owns mandatory invariant tests.
3. DOCX files are reconciliation material only.
4. Current source code is the migration baseline and does not override Phase 2 design.

The architecture contains 12 business services. `api-gateway` is an edge component and is not counted as a thirteenth business service. Signature is a `contract-service` feature. Hash-chain is an `audit-service` capability.

## Integration map

| Service | System of record / responsibility | Synchronous dependencies | Publishes | Consumes | Phase 1 disposition | Owner |
|---|---|---|---|---|---|---|
| `user-service` | Profile, KYC, authorization expiry, lock projection | Keycloak; internal user lookup callers | `notification.user_kyc_result_requested`, `notification.user_lock_changed_requested` | `reputation.locked`, `reputation.unlocked` | Reuse profile foundation; refactor public/internal DTOs, roles and lock revision | Long (P2) |
| `product-service` | Product, category, listing, plot/geolocation | Internal eligibility lookup to user-service | Existing category events; no new golden-flow event is frozen here | Reputation lock/eligibility projection only where explicitly implemented | Reuse CRUD; refactor eligibility to fail closed and add geolocation | Long (P2) |
| `contract-service` | ContractTerms snapshot, Contract, Milestone, buyer/seller Signature, OTP challenge; inspection settlement money calculation | Internal user lookup; synchronous OTP email API; file proxy | Canonical contract/milestone events, `notification.contract_anchor_requested`, activation notifications | `escrow.deposit_locked`, `escrow.deposit_lock_failed`, `inspection.report_confirmed` | Major refactor from single-delivery Phase 1 | Cuong (P1) |
| `escrow-service` | Escrow state projections and orchestration; never the money source | None on the critical event path | `bank.*_requested`, `escrow.deposit_locked`, `escrow.deposit_lock_failed` | `contract.signed`, `contract.settled`, `contract.cancelled`, canonical `milestone.*`, `bank.*_completed`, `bank.*_failed` | Refactor two-phase escrow; legacy amount records are reference-only | Cuong (P1) |
| `bank-service` | Append-only ledger, system lock, verifier nonce/key, AML reports | Internal audit read for reconciliation only | `bank.*_completed`, `bank.*_failed`, `bank.large_transaction_flagged`, `bank.suspicious_report_created`, security audit events | Canonical `bank.*_requested`, `analytics.structuring_pattern_detected` | Build new | Cuong (P1) |
| `inspection-service` | Level 1.5 reports/signatures; Level 2 commission and human confirmation | File-service through events/proxy; user eligibility for Level 1.5 | `inspection.level2_commissioned`, `inspection.report_confirmed` | `file.ready`, `file.failed`, `file.email_notice`; dispute commissioning trigger | Build new | P4 |
| `reputation-service` | Insert-only lock ledger, live score, elevated-risk decisions | None required on transaction critical path | `reputation.locked`, `reputation.unlocked`, `reputation.elevated_risk_cleared` | `contract.settled`, `milestone.settled`, `milestone.cancelled_with_penalty`, `milestone.dispute_resolved`, `bank.large_transaction_flagged`, `analytics.structuring_pattern_detected` | Build new | P4 |
| `audit-service` | Sole writer of append-only `audit_record` and `audit_anchor`; dual hash chain, typed money projection and OTS proof | No write API; internal read API for bank reconciliation | `notification.milestone_anchor_requested`, audit digest/failure commands defined by notification design | Canonical source domain events in hash-chain design section 2.4 | Build new; contract/test preparation delegated to P4 with P1 approval; no generic audit-ingest command | Cuong (P1) |
| `file-service` | Blob and technical metadata, virus scanning, intake and retention mechanics | MinIO, ClamAV, IMAP | `file.ready`, `file.failed`, `file.email_notice` | Internal storage commands and `file.email.received` pipeline messages | Build new | P5 |
| `pricing-service` | Reference price quote and manual price attribution | External scrape sources where available | No golden-flow event is frozen | None on golden-flow critical path | Build new; T3/manual-entry cut allowed | Node (P3) |
| `analytics-service` | CQRS read models and AML pattern scan | None on transaction critical path | `analytics.structuring_pattern_detected` | `contract.signed`, settlement/cancellation and milestone events from its design ingest catalog | Build new; short-outage catch-up only | Node (P3) |
| `notification-service` | Notification delivery log and templates | Email provider | No business decision events; processes `notification.*_requested` | Canonical notification commands; synchronous OTP endpoint | Reuse delivery/log foundation; refactor ingress and dedup | Node (P3) |

## Gateway trust boundary

- Strip client-supplied `X-User-Id`, `X-User-Role`, and `X-Gateway-Secret`; inject identity derived from JWT.
- Do not route `/internal/**` externally.
- `GET /api/v1/security/audit-hash` routes to audit-service and uses `X-Api-Key` plus rate limiting, not JWT.
- Emergency lock/unlock route to bank-service with asymmetric signature validation and no automatic retry.
- Business ownership and state authorization remain in the owning service.

Sources: `api-gateway-phase2-design.md` sections 2-5; Verification Matrix 12-14.

## Canonical golden-flow interactions

| Step | Producer | Contract | Consumer | Evidence / invariant |
|---|---|---|---|---|
| Eligibility | contract-service | `GET /internal/v1/users/{userId}` | user-service | Fail closed; Verification Matrix 19 and 21b |
| OTP delivery | contract-service | `POST /internal/v1/notifications/otp-email` | notification-service | No delayed background retry; Matrix 20 |
| Fully signed | contract-service | `contract.signed` | escrow, analytics, audit | Same terms hash; Matrix 3, 11c, 11f |
| Deposit request | escrow-service | `bank.lock_requested` | bank-service | One request per leg; Matrix 1, 6, 11, 11b |
| Deposit confirmation | bank-service | `bank.lock_completed` or `bank.lock_failed` | escrow-service | Escrow does not set state before confirmation |
| Activation | escrow-service | `escrow.deposit_locked` | contract-service | `SIGNED -> ACTIVE` only after required legs lock |
| Delivery evidence | contract-service | `milestone.seller_weighed`, `milestone.buyer_confirmed` | audit-service | Source measurements are immutable |
| Settlement | contract-service | `milestone.settled` | escrow, reputation, analytics, audit | `lockedAmount` and `actualAmount`; no double release |
| Bank settlement | escrow-service | canonical `bank.release_requested` / `bank.refund_requested` | bank-service | Append-only ledger and idempotency |
| Dispute decision | contract-service | `milestone.dispute_resolved` | reputation-service | Abuse signal only; payload fixed by catalog |
| Inspection evidence/decision | inspection-service | `inspection.report_confirmed` | audit-service and contract-service | `InspectionSettlementResultV1`, `resultHash`, revised `reportHash`; contract-service verifies hashes and applies entitlement only to matching `CONTESTED` milestone |
| Contract completion | contract-service | `contract.settled` | escrow, reputation, analytics | Terminal lock total must be zero |
| Reconciliation | bank-service | internal audit read API | audit-service | Matrix 5, 8 and 25 |

## Audit ingestion ownership

Audit-service consumes canonical domain events directly and is the only writer of `audit_record`. There is no `audit.record_ingest_requested`, generic audit write event, or audit write API. The canonical source-event mapping is owned by `hash-chain-phase2-design.md` section 2.4.

Canonical domain schemas are in `contracts/events/golden-flow-events.yaml`; internal delivery commands with recipient routing data are separated into `contracts/events/notification-commands.yaml`.

## Foundation audit

### Reuse

- Maven multi-module structure, Spring layering, repository abstractions, basic controller/security scaffolding.
- Existing contract/escrow transactional outbox and poller concepts, subject to envelope and idempotency refactor.
- User profile registration, product/category/listing baseline, notification log and consumer test structure.

### Refactor

- Contract state machine and immutable terms/signature storage.
- Event envelope and PII minimization.
- User roles and internal/public DTO split.
- Gateway routes and identity-header boundary.
- Escrow into milestone/deposit state orchestration with bank confirmations.

### Reference-only

- Phase 1 escrow transaction amounts as a money source.
- Legacy event payloads containing buyer/seller email.
- `confirm-deposit`, arbitration and single-delivery endpoints where they conflict with milestone escrow.

### Remove

- `confirmDelivery`, `ContractDeliveredEvent`, `contract.delivered`, and consumers tied only to single-delivery Phase 1.
- Dual-write balance/amount fields that compete with the bank ledger.
- Cross-service database writes or foreign keys.

### Build new

- Bank ledger/security/reconciliation, audit/hash-chain, inspection, reputation, file, pricing and analytics services.

## OC resolution status

| OC | Classification | Resolution | Source |
|---|---|---|---|
| OC-01 | Design defines use cases; approved decision promotes exact paths | Resolved with `POST /api/v1/contracts/{contractId}/sign/initiate` and `POST /api/v1/contracts/{contractId}/sign/verify`; paths are now authoritative Markdown. | `signature-phase2-design.md` section 6.0 |
| OC-02 | Design defines request/confirmation pattern; consolidated catalog supplies all names | Resolved with `bank.lock_completed`/`_failed`, `bank.release_completed`/`_failed`, `bank.seize_completed`/`_failed`, and `bank.refund_completed`/`_failed`; shared result payload is mirrored across operations. | `bank-service-phase2-design.md` section 3; SDS Event Catalog `docs/phase_2/files/build05.js` section 4.2 |
| OC-03 | Design exists; complete payload missing | Resolved: `contract.settled` carries existing buyer/seller IDs; `contract.cancelled` carries `{contractId, initiatedBy}`. | `milestone-escrow-phase2-design.md` sections 6.3 and 7.2; existing `ContractSettledEvent` |
| OC-04 | Design exists; contract was overlooked | Resolved with `GET /internal/v1/audit/records?contractId&sourceType&from&to` and typed reconciliation content `{contractId,milestoneId,settledAmount,seizedAmount,releaseLegs,refundLegs}`. | `hash-chain-phase2-design.md` sections 3 and 4.5; `bank-service-phase2-design.md` section 5b.1 |
| OC-05 | Design names behavior; approved decision promotes exact REST paths | Resolved with the milestone action catalog and activation recovery paths in authoritative Markdown. | `milestone-escrow-phase2-design.md` section 3.2.0 |
| OC-06 | Design explicitly defines a routing-data exception | Resolved without moving notification routing into the domain event: `milestone.settled` carries `recipients` because audit-service cannot Feign user-service; audit-service then emits `notification.milestone_anchor_requested`, which is the actual notification command. | `milestone-escrow-phase2-design.md` §7.1; `hash-chain-phase2-design.md` §4.3; `notification-service-phase2-design.md` §2.1, §4 |
| OC-07 | State prose exists; complete enum table missing | Resolved with `PROVISIONALLY_RELEASED`, `RELEASED`, `PENALIZED`, `REFUNDED` and activation recovery states; `REFUNDED_PARTIAL` is not canonicalized from SDS-only material. | `milestone-escrow-phase2-design.md` sections 2.3, 3.1 and 3.2 |
| OC-08 | No enum is required by design | Resolved as a derived OTP lifecycle from persisted timestamps/counters; no status enum is added. | `signature-phase2-design.md` sections 4.1, 6 and 7 |
| OC-09 | Design behavior exists; shared wire format is in SDS, not Markdown | Resolved by retaining the SDS error envelope and freezing the six approved External Verifier codes/statuses. | `error-catalog.md`; `bank-service-phase2-design.md` section 3.5.2 |
| OC-10 | Genuine cross-service contract gap | Resolved with `InspectionSettlementResultV1`, `milestoneId`, `resultHash`, revised `reportHash`, entitlement formula and idempotent matching `CONTESTED` transition. | `inspection-phase2-design.md` §§2.3, 4-5; `milestone-escrow-phase2-design.md` §3.2 |
| OC-11 | Design behavior exists; approved decision freezes wire | Resolved with ES256 body, RFC 8785 signing, exact action paths, replay window, nonce and canonical error codes. | `bank-service-phase2-design.md` sections 3.5.1-3.5.2; `api-gateway-phase2-design.md` section 3.4 |

Deferred implementation-time decisions are recorded in `docs/contracts/open-questions.md`.
