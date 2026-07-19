---
title: "AGRICONTRACT"
subtitle: "Software Design Specification — Phase 2"
author: "Tài liệu thiết kế chi tiết hợp nhất"
date: "Phiên bản final · Tháng 7/2026"
toc-title: "Mục lục"
---

# **0. Trạng thái và quy tắc nguồn sự thật**

Tài liệu mô tả **target design Phase 2 ở trạng thái DESIGNED — chưa code đầy đủ/production**. Thứ tự ưu tiên: design `.md` mới nhất → Verification Matrix → contract/API/event/schema → DOCX. Code hiện tại chỉ là implementation baseline. Mọi mock, Future Work và limitation được ghi rõ, không được diễn giải là capability đã hoàn thành.

| Source | Vị trí phản ánh | Số section/loại | Coverage |
|---|---|---|---|
| docs/phase_2/design/analytics-service-phase2-design.md | Architecture §3/§7, SDS §9/Phụ lục | 18 | covered |
| docs/phase_2/design/api-gateway-phase2-design.md | Architecture §2/§8, SDS §3/§10 | 18 | covered |
| docs/phase_2/design/bank-service-phase2-design.md | Solution §3/§7, Architecture §3/§5/§6, SDS §6 | 24 | covered |
| docs/phase_2/design/data-governance-phase2-design.md | Market §8, Solution §6/§7/§12, Architecture §8/§9/§11, SDS §3/§10/§12 | 20 | covered |
| docs/phase_2/design/file-service-phase2-design.md | Solution §6/§12, Architecture §3/§8, SDS §8 | 14 | covered |
| docs/phase_2/design/hash-chain-phase2-design.md | Solution §6, Architecture §3/§8, SDS §7 | 18 | covered |
| docs/phase_2/design/inspection-phase2-design.md | Solution §4, Architecture §3/§7, SDS §7 | 17 | covered |
| docs/phase_2/design/milestone-escrow-phase2-design.md | Market §5/§7, Solution §3/§7, Architecture §3/§7, SDS §4–§6 | 34 | covered |
| docs/phase_2/design/notification-service-phase2-design.md | Architecture §3/§4, SDS §9 | 15 | covered |
| docs/phase_2/design/pricing-service-phase2-design.md | Market §4, Solution §2/§8, Architecture §3, SDS §8 | 9 | covered |
| docs/phase_2/design/product-phase2-design.md | Market §4, Solution §2/§12, Architecture §3, SDS §8 | 20 | covered |
| docs/phase_2/design/reputation-service-phase2-design.md | Solution §5, Architecture §3/§7, SDS §7 | 22 | covered |
| docs/phase_2/design/signature-phase2-design.md | Solution §3/§7, Architecture §3/§8, SDS §4 | 11 | covered |
| docs/phase_2/design/team-assignment-plan.md | SDS §12 (workstreams only; names/skill labels excluded) | 10 | covered |
| docs/phase_2/design/user-service-phase2-design.md | Solution §2/§5, Architecture §3/§8, SDS §9 | 14 | covered |
| docs/phase_2/design/verification-matrix-phase2.md | SDS §11 + release gate | 6 | covered |
| docs/contracts/enum-ownership.md | Architecture §5, SDS §3/§4/Phụ lục | 3 | covered |
| docs/contracts/error-catalog.md | SDS §3/Phụ lục | 5 | covered |
| docs/contracts/golden-flow.md | Solution §3, Architecture §7, SDS §4/§11 | 5 | covered |
| docs/contracts/integration-map.md | Architecture toàn bộ, SDS §4–§12 | 13 | covered |
| docs/contracts/open-questions.md | SDS §12 (closed decisions; no open blocker) | 2 | covered |
| docs/contracts/state-machines.md | Architecture §7, SDS §4–§9 | 10 | covered |
| docs/contracts/verification-traceability.md | SDS §11 | 2 | covered |
| docs/planning/implementation-plan.md | SDS §12 | 17 | covered |
| contracts/events/event-envelope.yaml | Architecture §4/§5, SDS §3/Phụ lục | schema | covered |
| contracts/events/golden-flow-events.yaml | Architecture §5, SDS Phụ lục A | schema | covered |
| contracts/events/notification-commands.yaml | Architecture §5, SDS §9/Phụ lục A | schema | covered |
| contracts/openapi/golden-flow-api.yaml | Architecture §4, SDS §10/Phụ lục B | schema | covered |

# **1. System scope và actor model**

## **1.1 Phạm vi**

AgriContract số hoá contract layer nông sản B2B: user eligibility/KYC, plot/listing, hợp đồng/terms/signature, deposit + milestone funding, delivery/inspection, attribution/remedy, ledger, reputation, audit/evidence, file, pricing, analytics và notification. Không bao gồm logistics, kế toán, phán quyết tài phán, dịch vụ thanh toán được cấp phép, due-diligence statement EUDR hoặc tín dụng tự động.

## **1.2 Actors và role**

| **Actor/role** | **Năng lực** | **Cấm/giới hạn** |
|---|---|---|
| BUYER | tạo offer, ký, funding, xác nhận/flag, đề xuất termination/replacement | Chỉ contract mình tham gia; không tự chọn final breach |
| SELLER | listing, ký, weigh/delivery, response/claim FM | Phải eligible/unlocked; không tự settle trên seller-only evidence |
| INSPECTOR | Level 1.5 case/result theo assignment | Không có quyền ADMIN/OPERATOR |
| OPERATOR | KYC operation, plot-risk review và nghiệp vụ đảo ngược thấp | Không approve maker-checker/đường money-security ADMIN |
| ADMIN | review breach/FM, retry/mark activation, high-risk governance | Không tự approve đề xuất của mình |
| External Verifier | audit hash query; ES256 emergency lock/unlock | Không JWT/API key/Admin bypass; request canonical/nonce/timestamp |
| Level 2 organization | nhận commission và gửi report ngoài hệ thống | Không giả định account/RBAC/signature nội bộ |

# **2. Service/component specification**

| Service | Owner data/domain | Inbound | Outbound/side effect | Critical invariant |
|---|---|---|---|---|
| user-service | Identity, KYC, role, eligibility và lock projection | user KYC/lock commands; internal eligibility API | Không sở hữu reputation ledger | Owner-only mutation |
| product-service | Plot registry, product/listing, commodity gate, geo/yield risk | user eligibility; file.ready | Plot là seller-owned; overlap là signal | Owner-only mutation |
| contract-service | Contract, milestone, signature/OTP, attribution, remedy, termination | REST commands; domain events; internal user/OTP | Owner duy nhất của business decision và lifecycle | Owner-only mutation |
| escrow-service | Projection escrow và chuyển remedy legs thành bank commands | contract.signed, remedy.finalized, milestone outcomes, bank results | Không là source of truth số tiền | Owner-only mutation |
| bank-service | Append-only monetary ledger, lock/release/refund/seize, security lock | bank.*_requested; structuring signal; external verifier | Single source of truth cho tiền | Money authoritative only |
| inspection-service | Level 1.5/2 commission, result/report hash, confirmation | contract/milestone commands, file.ready | Không ra phán quyết pháp lý | Owner-only mutation |
| reputation-service | Immutable completion/dispute/lock facts và derived score | remedy.finalized, settled facts, risk signals | Negative consequence chỉ từ remedy.finalized | Owner-only mutation |
| audit-service | Append-only dual hash chain, anchor, verify, evidence query | evidence/security/domain events | Không sửa source facts; dedup source_event_id | Owner-only mutation |
| file-service | Three trust-boundary ingests, malware scan, retention/legal hold | upload/email/system-generated; file events | READY gate trước khi thành evidence | Owner-only mutation |
| pricing-service | PriceQuote, VNSAT/manual ingestion và read API | scheduled/admin ingestion | Không là settlement engine | Owner-only mutation |
| analytics-service | CQRS read models, monthly recompute, AML structuring signals | domain/ledger facts | Non-critical; catch-up queue backlog only | Owner-only mutation |
| notification-service | Template delivery, evidence attachment, OTP sync | notification commands; internal OTP API | Không consume business event để tạo sanction | Owner-only mutation |

`api-gateway` là edge component, không tính trong 12 business services. Contract signature/OTP thuộc contract-service; hash-chain/anchor thuộc audit-service.

# **3. Common contracts**

## **3.1 REST convention**

- Base path `/api/v1`; internal `/internal/v1` không external route.
- Write yêu cầu `Idempotency-Key`; JWT/service auth là identity source.
- Error envelope: `timestamp`, `status`, `error`, `code`, `message`, `path`, `traceId`.
- Owner service làm fine-grained authorization và state guard; gateway chỉ coarse route/role.
- External verifier exact errors: `EXTERNAL_SIGNATURE_INVALID`/`EXTERNAL_KEY_UNKNOWN`/`SIGNATURE_TIMESTAMP_OUT_OF_WINDOW` (401), `NONCE_REPLAYED`/`SYSTEM_ALREADY_LOCKED`/`SYSTEM_NOT_LOCKED` (409).

## **3.2 Event envelope**

| Field | Yêu cầu | Schema | Mô tả |
|---|---|---|---|
| eventId | Bắt buộc | string |  |
| eventType | Bắt buộc | string |  |
| eventVersion | Bắt buộc | integer |  |
| occurredAt | Bắt buộc | string |  |
| producer | Bắt buộc | string |  |
| aggregateId | Bắt buộc | string | Canonical identifier of the source aggregate that produced the event. |
| correlationId | Nullable/tuỳ trường hợp | ['string', 'null'] | Required by contract when the event belongs to a request, saga, or scheduled workflow. The producer must generate it when possible. A standalone event that does not belong to such a workflow may omit it. |
| causationId | Nullable/tuỳ trường hợp | ['string', 'null'] | Absent or null for a root event. For a derived event, the eventId or commandId that directly caused it. Producers must not fabricate a value for validation. |
| subject | Nullable/tuỳ trường hợp | subject | Optional audit/business subject. Include only when authoritative design defines a subject such as CONTRACT, USER_PAIR, or SYSTEM. It is semantically independent from aggregateId. |
| payload | Bắt buộc | object |  |

Quy tắc: producer/eventType phải khớp schema; `causationId` trỏ request/event trực tiếp; `correlationId` giữ xuyên saga; result event có eventId mới; bank request `payload.sourceEventId` bằng request envelope eventId; replay DLQ giữ original eventId.

## **3.3 Enum ownership và superseded values**

Enum được sở hữu bởi service phát quyết định; consumer validate nhưng không tự thêm nghĩa. Contract state current không có `CANCELLED/DELIVERED`; escrow milestone không có `REFUNDED_PARTIAL`; bank không có `SEIZE_PENALTY`; boolean `penaltyAndDamagesCumulative` được thay bằng `damagesPolicy`; cancel endpoint/event cũ bị retire.

## **3.4 Common data/security constraints**

UUID là identity xuyên service; tiền dùng decimal dương, không float. Timestamp UTC ISO-8601. PII không đi vào public DTO/event ngoài nhu cầu. Secret/key không nằm trong image/repository. Database grants enforce append-only cho ledger/audit/lock facts.

# **4. contract-service — aggregate, signature, attribution và lifecycle**

## **4.1 Aggregate/value objects**

| **Model** | **Fields/quan hệ trọng yếu** | **Invariant** |
|---|---|---|
| Contract | buyerId, sellerId, state, termsVersion, signedContentHash, replacement pointers | Participant-only; signed terms immutable; terminal zero-lock |
| ContractTerms | commodity, total, milestones, deposits, windows, inspector selection, legalProfile | Chỉ sửa pre-second-signature; snapshot/frozen |
| LegalProfile | governingLaw, contractType, maxContractualPenaltyRate, damagesPolicy | Penalty cap/policy quyết định remedy; không boolean cumulative cũ |
| Signature/OTP challenge | userId, contractId, termsHash, otpId, expiry, attempts | Same terms hash; actor binding; không “OTP mới nhất” cross-user |
| Milestone | funding + delivery state, quantities, deadlines, inspection/flag refs | Aggregate riêng trong contract-service; settled immutable |
| BreachCase | alleged role/reason/evidence, REPORTED/UNDER_REVIEW/RESOLVED | Allegation không tạo consequence |
| AttributionDecision | breachCaseId nullable, finalBreachingRole nullable, reason, source | Điểm hội tụ Rổ A/Rổ B |
| RemedyDecision | legal profile snapshot, eligibility flags, affected milestones, legs | Một decision nhiều unique legs; no-fault guard |

## **4.2 Contract state machine**

| **State** | **Đường vào** | **Đường ra/guard** |
|---|---|---|
| OFFERED/NEGOTIATING | create/update terms | sign; withdraw pre-second-signature |
| SIGNED | two signatures same hash | deposit lock → ACTIVE; exhausted failure → ACTIVATION_REFUND_PENDING |
| ACTIVE | both deposits locked | settle; breach/FM/mutual termination; replacement pending |
| REPLACEMENT_PENDING | valid mutual replacement proposal | replacement signed → refund old; abort before effect if not signed |
| SUPERSEDE_REFUND_PENDING | replacement signed, refund old initiated | SUPERSEDED only expected legs complete + zero lock |
| ACTIVATION_REFUND_PENDING | partial deposit lock/failure | ACTIVATION_FAILED only refund complete + zero lock |
| SETTLED/TERMINATED/SUPERSEDED/ACTIVATION_FAILED | reconciler commit | terminal immutable |
| WITHDRAWN | pre-sign withdrawal | terminal, zero-lock by construction |

## **4.3 Authoritative actions**

- Create/update terms; initiate/verify sign; withdraw.
- Propose/confirm mutual termination or replacement.
- Report/review/resolve breach; execute termination.
- Retry deposit lock/mark activation failed.
- Weigh, confirm, flag, respond, claim/resolve FM, retry funding.

Không có generic `/cancel`. Command transport và exact paths nằm trong §10.

## **4.4 Signature flow**

1. Validate user eligibility/unlocked via internal user-service, fail-closed.
2. Canonicalize complete terms + legal profile; calculate SHA-256.
3. Create OTP challenge bound to user/contract/terms hash; synchronous notification call.
4. Verify actor, otpId, contract state, expiry/attempt and current hash.
5. Persist signature; second signature must match first hash.
6. Commit `SIGNED` + outbox `contract.signed` atomically; reject all later term changes.

## **4.5 Attribution and remedy rules**

`requestedBy` chỉ audit intent. Rổ A áp cho objective failure đã đủ evidence; Rổ B mở BreachCase. `finalBreachingRole = null` kéo theo `penaltyEligible=false`, `reputationEligible=false`, không leg `DEPOSIT_FORFEITURE/CONTRACTUAL_PENALTY/DAMAGES_COMPENSATION`.

Penalty rate không vượt `LegalProfile.maxContractualPenaltyRate` (8% với VN commercial profile) và tính trên breached obligation/milestone scope. `DAMAGES_COMPENSATION` chỉ khi policy cho và có evidence; forfeiture/penalty/damages phải tránh double recovery.

## **4.6 RemedyDecision wire shape**

| **Field** | **Nullable** | **Ý nghĩa** |
|---|---|---|
| remedyDecisionId | no | Grouping ID, không phải bank leg dedup key |
| attributionDecisionId | no | Final attribution source |
| breachCaseId | yes | Null cho Rổ A/no-case |
| contractId, buyerId, sellerId | no | Consumer không cross-service lookup |
| affectedMilestoneIds | no | Scope chưa settled/được xử lý |
| finalBreachingRole | yes | BUYER/SELLER/null |
| breachReasonCode | yes | Null no-fault |
| decisionSource | no | SYSTEM/ADMIN/INSPECTOR_L1_5/INSPECTOR_L2/MUTUAL |
| penaltyEligible, reputationEligible | no | Explicit guards |
| remedyLegs | no | `{remedyLegId, remedyType, fundType, role, amount}` |

## **4.7 Completion rules**

Normal completion tạo no-fault remedy decision để settlement milestone còn lại và return contract deposits. Termination/replacement/activation failure cũng tạo expected leg set. Reconciler query bank ledger, không dựa vào event ordering đơn lẻ. Lifecycle event chỉ phát sau state terminal commit.

# **5. Milestone and escrow orchestration**

## **5.1 Funding state**

`FUNDING_PENDING → LOCKED | FUNDING_FAILED`. Retry có giới hạn; partial lock phải refund. `fundingDelayBusinessDays` cộng vào `effectiveDeliveryDeadline`; seller clock chỉ chạy sau LOCKED. Confirmed bank/system outage pause cure; sau cure window, buyer funding failure có thể là objective attribution, không phạt seller.

## **5.2 Delivery state/actions**

`CREATED → IN_PROGRESS → SELLER_WEIGHED → BUYER_RECEIVED`; sau đó clean confirmation hoặc `AWAITING_SELLER_RESPONSE/CONTESTED/FORCE_MAJEURE_PENDING_REVIEW`; cuối cùng `SETTLED`. Seller weighed timeout không auto-release. Auto-confirm chỉ khi state vẫn BUYER_RECEIVED và không inspection pending.

## **5.3 Delta and provisional settlement**

Delta 1 xử lý quantity; vượt threshold mở case, không auto-breach. Phần giao thật có thể pro-rata release. Delta 2 chất lượng đi qua flag/response/inspection. Level 2 provisional dùng explicit release/refund/remaining lock và release floor; phải bảo toàn batch amount.

## **5.4 escrow-service projections**

Deposit: `DEPOSIT_LOCKED/DEPOSIT_RELEASED/DEPOSIT_SEIZED`. Funding: `FUNDING_PENDING/LOCKED/FUNDING_FAILED`. Escrow milestone: `LOCKED/PROVISIONALLY_RELEASED/RELEASED/REFUNDED/PENALIZED`.

escrow-service consume `remedy.finalized` một lần, tạo đúng bank command cho từng leg, persist source event/leg mapping và update projection từ bank result. Không consume `contract.terminated`/`contract.settled` cho tiền.

# **6. bank-service — monetary source of truth**

## **6.1 Ledger model**

Entry types: `LOCK_BUYER_DEPOSIT`, `LOCK_SELLER_DEPOSIT`, `LOCK_MILESTONE`, `RELEASE_TO_SELLER`, `REFUND_TO_BUYER`, `DEPOSIT_FORFEITURE`, `CONTRACTUAL_PENALTY`, `DAMAGES_COMPENSATION`. Fund types: `BUYER_DEPOSIT`, `SELLER_DEPOSIT`, `MILESTONE_PAYMENT`.

Mỗi entry lưu `sourceEventId`, `contractId`, optional `milestoneId`, `userId`, `entryType`, `fundType`, nullable `remedyDecisionId`, nullable `remedyLegId`, positive amount, timestamp. Repository và DB user chỉ INSERT/SELECT; không UPDATE/DELETE.

## **6.2 Command/result**

escrow-service là actor duy nhất cho `bank.lock/release/refund/seize_requested`. bank-service phát completed/failed với eventId mới, causationId=request eventId. Duplicate callback/request no-op theo source ID. Large-transaction signal phát khi có transfer thực, không cho từng projection entry.

## **6.3 Security lock**

External Verifier signed LOCK/UNLOCK thay `system_lock`; khi ACTIVE, mọi money request fail. Key unknown/signature/timestamp/nonce/state trả exact code. Admin không bypass. Security event vào audit/notification qua canonical paths.

## **6.4 Reconciliation/statement**

Internal ledger query filter contract/remedy/leg phục vụ contract reconciler và audit reconciliation. Buyer/Seller statement được expose qua escrow endpoint sau ownership check; bank internal API không public. `LedgerAuditReconciliationJob` so typed audit projection với ledger amount/legs và phát mismatch alert.

# **7. Inspection, reputation và audit**

## **7.1 inspection-service**

Level 1.5 dùng user identity/KYC/session nhưng có result signature/hash riêng. Normalized settlement result theo RFC 8785; report hash commit report file identity, result, time và actor. Level 2 organization được negotiate/frozen trong ContractTerms, commission có caseId, email intake server-derived, malware scan, human-confirm và frozen hash trước review. Không auto join report chỉ từ subject/email mơ hồ.

## **7.2 reputation-service**

Negative input duy nhất: `remedy.finalized` + `reputationEligible=true` + final role. Strategic reason mới lock; `PRODUCTION_SHOCK_NON_FM` không lock. `zeroProgressMultiplier` chỉ strategic + zero settled. Immutable completion/dispute/lock facts; score derived at read. Early unlock dùng append-only override + maker-checker.

## **7.3 audit-service**

Audit catalog ingest domain/security/evidence events. Canonical record hash commit source_type, subject_id, source event/correlation/causation, prev global/subject hash và content. `audit_record`/`audit_anchor` append-only. `audit_record + anchor_request_outbox` atomic. Verify global và per-subject chains; anchor/digest chỉ chạy sau verify; failure alert độc lập.

# **8. Product, file và pricing**

## **8.1 product-service**

Commodity enum `COFFEE/RICE/RUBBER/CASHEW`; category hai cấp + `varietyName`. Plot seller-owned, snapshot vào listing/contract. Coffee/rubber bắt buộc plot; rice/cashew không. KML primary, pin fallback; GEOMETRY SRID 4326 + JTS validation. Province cross-check giảm lỗi. ForTy/Natural Forest baseline là risk support. Cross-seller overlap HIGH → OPERATOR review, không auto reject. Yield anomaly là signal, không gate.

## **8.2 file-service**

Entrypoints: DIRECT_UPLOAD, EMAIL_INTAKE, SYSTEM_GENERATED; channel do server quyết định. State PROCESSING/READY/FAILED. Malware scan/metadata/hash trước READY; infected file không gắn evidence. Access theo owner/resource/purpose; read audit. Legal hold chặn xoá; deletion tombstone + blob delete + retry tự lành.

## **8.3 pricing-service**

`PriceQuote` theo commodity/region/source/time. VNSAT scrape COFFEE/RICE; RUBBER/CASHEW admin/manual theo source design; scheduled ingestion và read API/cache. Giá chỉ tham khảo/điều khoản nếu contract đã freeze; Phase 2 không có full indexed settlement.

# **9. User, notification và analytics**

## **9.1 user-service**

Roles BUYER/SELLER/ADMIN/INSPECTOR/OPERATOR; KYC PENDING/VERIFIED/REJECTED. Public DTO loại PII; internal API cho eligibility. Sign/listing/offer fail-closed. Reputation lock projection dùng persisted monotonic `last_lock_revision`; overlapping locks tính max effective lock và không unlock nhầm.

## **9.2 notification-service**

RabbitMQ command là ingress mặc định; synchronous `/internal/v1/notifications/otp-email` chỉ OTP. `NotificationLog PENDING/SENT/FAILED`, EMAIL channel, attachment evidence qua file-service READY. Dedup `(event_id, recipient_email, notification_type)`. Business service quyết recipient/purpose; notification không quyết định nghiệp vụ.

## **9.3 analytics-service**

CQRS read models ingest idempotent. `fact_contract_termination` tách `terminationType`, `requestedBy`, `finalBreachingRole`, `breachReasonCode`; mutual/no-fault không vào default breach rate. Monthly job recompute/overwrite mọi touched bucket. Analytics outage không ảnh hưởng settle; catch-up queue backlog, không claim cold rebuild. `AmlPatternScanJob` phát `analytics.structuring_pattern_detected`; reputation risk projection không hold tiền.

# **10. Frozen API, schemas và event contracts**

## **10.1 OpenAPI path catalog (41 paths)**

| Method | Path | Owner | operationId | Request schema | Auth/role |
|---|---|---|---|---|---|
| POST | /api/v1/contracts | contract-service | createContract | CreateContractRequest | BUYER |
| GET | /api/v1/contracts | contract-service | listContracts | — | JWT |
| GET | /api/v1/contracts/{contractId} | contract-service | getContract | — | JWT |
| PATCH | /api/v1/contracts/{contractId}/terms | contract-service | updateContractTerms | ContractTermsRequest | BUYER, SELLER |
| POST | /api/v1/contracts/{contractId}/sign/initiate | contract-service | initiateSign | InitiateSignRequest | BUYER, SELLER |
| POST | /api/v1/contracts/{contractId}/sign/verify | contract-service | verifyOtpAndSign | VerifyOtpAndSignRequest | BUYER, SELLER |
| POST | /api/v1/contracts/{contractId}/withdraw | contract-service | withdrawOffer | — | BUYER, SELLER |
| POST | /api/v1/contracts/{contractId}/termination/mutual/propose | contract-service | proposeMutualTermination | — | BUYER, SELLER |
| POST | /api/v1/contracts/{contractId}/termination/mutual/confirm | contract-service | confirmMutualTermination | — | BUYER, SELLER |
| POST | /api/v1/contracts/{contractId}/replacement/propose | contract-service | proposeMutualReplacement | ReplacementProposalRequest | BUYER, SELLER |
| POST | /api/v1/contracts/{contractId}/replacement/confirm | contract-service | confirmMutualReplacement | — | BUYER, SELLER |
| POST | /api/v1/contracts/{contractId}/breach-cases | contract-service | reportBreachCase | BreachCaseReportRequest | BUYER, SELLER, SYSTEM |
| POST | /api/v1/contracts/{contractId}/breach-cases/{breachCaseId}/review | contract-service | reviewBreachCase | — | ADMIN, INSPECTOR |
| POST | /api/v1/contracts/{contractId}/breach-cases/{breachCaseId}/resolve | contract-service | resolveBreachCase | BreachCaseResolutionRequest | ADMIN, INSPECTOR |
| POST | /api/v1/contracts/{contractId}/termination/execute | contract-service | executeTermination | TerminationExecuteRequest | BUYER, SELLER, ADMIN |
| POST | /api/v1/contracts/{contractId}/activation/retry-deposit-lock | contract-service | retryDepositLock | — | ADMIN |
| POST | /api/v1/contracts/{contractId}/activation/mark-failed | contract-service | markActivationFailed | — | ADMIN |
| POST | /api/v1/contracts/{contractId}/milestones/{milestoneId}/weigh | contract-service | recordSellerWeighed | SellerWeighedRequest | SELLER |
| POST | /api/v1/contracts/{contractId}/milestones/{milestoneId}/confirm | contract-service | confirmMilestoneClean | BuyerReceivedRequest | BUYER |
| POST | /api/v1/contracts/{contractId}/milestones/{milestoneId}/flag | contract-service | flagMilestoneIssue | BuyerReceivedRequest | BUYER |
| POST | /api/v1/contracts/{contractId}/milestones/{milestoneId}/respond | contract-service | respondToMilestoneFlag | — | SELLER |
| POST | /api/v1/contracts/{contractId}/milestones/{milestoneId}/force-majeure | contract-service | claimForceMajeure | ForceMajeureClaimRequest | SELLER |
| POST | /api/v1/contracts/{contractId}/milestones/{milestoneId}/force-majeure/resolve | contract-service | resolveForceMajeure | ForceMajeureResolveRequest | ADMIN |
| POST | /api/v1/contracts/{contractId}/milestones/{milestoneId}/funding/retry | escrow-service | retryMilestoneFunding | — | ADMIN |
| POST | /api/v1/users/register | user-service | registerUser | — | JWT |
| GET | /api/v1/users/me | user-service | getMyProfile | — | JWT |
| GET | /api/v1/users/{userId} | user-service | getPublicUser | — | JWT |
| GET | /api/v1/reputation/{userId}/public-summary | reputation-service | getPublicReputationSummary | — | JWT |
| POST | /api/v1/admin/reputation/actions/propose | reputation-service | proposeReputationAction | ReputationActionProposalRequest | ADMIN, OPERATOR |
| POST | /api/v1/admin/reputation/actions/{actionId}/approve | reputation-service | approveReputationAction | — | ADMIN |
| POST | /api/v1/admin/reputation/actions/{actionId}/reject | reputation-service | rejectReputationAction | — | ADMIN |
| GET | /api/v1/admin/users | user-service | listPendingUsers | — | JWT |
| POST | /api/v1/admin/users/{userId}/verify | user-service | verifyUser | VerifyUserRequest | JWT |
| POST | /api/v1/admin/users/{userId}/reject | user-service | rejectUser | RejectUserRequest | JWT |
| GET | /internal/v1/users/{userId} | user-service | getInternalUserInfo | — | serviceAuth |
| POST | /internal/v1/notifications/otp-email | notification-service | sendOtpEmail | OtpEmailRequest | serviceAuth |
| GET | /internal/v1/audit/records | audit-service | getAuditRecordsForReconciliation | — | serviceAuth |
| GET | /api/v1/security/audit-hash | audit-service | getAuditHash | — | JWT |
| POST | /api/v1/security/emergency-lock | bank-service | emergencyLock | ExternalVerifierRequest | External Verifier |
| POST | /api/v1/security/emergency-unlock | bank-service | emergencyUnlock | ExternalVerifierRequest | External Verifier |
| GET | /api/v1/escrows/statements | escrow-service | exportEscrowStatement | — | JWT |
| GET | /internal/v1/bank/ledger | bank-service | getInternalLedgerEntries | — | serviceAuth |

## **10.2 OpenAPI component schemas (38)**

Ký hiệu `?` là optional; field required nhưng nullable vẫn phải xuất hiện và nhận `null` theo schema.

| Schema | Fields/type | Constraint |
|---|---|---|
| CreateContractRequest | `listingId`: string<br>`terms`: ContractTermsRequest | closed object |
| ContractTermsRequest | `agreedPrice`: string<br>`milestoneSchedule`: array<MilestoneTermRequest><br>`toleranceRate`: number<br>`shortfallPenaltyThreshold`: number<br>`buyerPenaltyRate`: number<br>`sellerPenaltyRate`: number<br>`forceMajeureReportWindowDays`: integer<br>`buyerConfirmWindowDays`: integer<br>`buyerDepositRate`: number<br>`disputeFloorReleaseRate`: number<br>`sellerDepositRate`: number<br>`level2InspectorOrg?`: ['string', 'null']<br>`legalProfile`: LegalProfile | closed object |
| LegalProfile | `governingLaw`: `VN_COMMERCIAL_LAW`, `VN_CIVIL_LAW`<br>`contractType`: string<br>`maxContractualPenaltyRate`: number<br>`damagesPolicy`: `COMMERCIAL_CUMULATIVE_IF_PROVEN`, `CIVIL_PENALTY_ONLY_UNLESS_EXPRESSLY_CUMULATIVE`, `EXPRESS_PENALTY_ONLY` | closed object |
| MilestoneTermRequest | `milestoneIndex`: integer<br>`committedQuantity`: number<br>`expectedDeliveryDate`: string<br>`graceDays`: integer | closed object |
| ContractResponse | `contractId`: string<br>`listingId`: string<br>`buyerId`: string<br>`sellerId`: string<br>`status`: `OFFERED`, `NEGOTIATING`, `SIGNED`, `ACTIVE`, `REPLACEMENT_PENDING`, `SUPERSEDE_REFUND_PENDING`, `ACTIVATION_REFUND_PENDING`, `SETTLED`, `TERMINATED`, `WITHDRAWN`, `SUPERSEDED`, `ACTIVATION_FAILED`<br>`terms`: ContractTermsRequest<br>`milestones`: array<MilestoneResponse> | closed object |
| MilestoneResponse | `milestoneId`: string<br>`milestoneIndex`: integer<br>`status`: `CREATED`, `IN_PROGRESS`, `SELLER_WEIGHED`, `BUYER_RECEIVED`, `AWAITING_SELLER_RESPONSE`, `CONTESTED`, `FORCE_MAJEURE_PENDING_REVIEW`, `SETTLED`<br>`fundingStatus`: `FUNDING_PENDING`, `LOCKED`, `FUNDING_FAILED`<br>`committedQuantity`: number<br>`batchAmount`: string<br>`expectedDeliveryDate`: string<br>`graceDays`: integer<br>`fundingDelayBusinessDays`: integer<br>`effectiveDeliveryDeadline`: string | closed object |
| BreachCase | `breachCaseId`: string<br>`contractId`: string<br>`milestoneId`: ['string', 'null']<br>`requestedBy`: `BUYER`, `SELLER`, `SYSTEM`<br>`allegedBreachingRole`: `BUYER`, `SELLER` / null<br>`breachReasonCode`: BreachReasonCode<br>`severity`: `MINOR`, `MATERIAL`<br>`evidenceFileIds`: array<string><br>`finalBreachingRole`: `BUYER`, `SELLER` / null<br>`decisionSource`: `ADMIN`, `INSPECTOR_L1_5`, `INSPECTOR_L2`, `MUTUAL` / null<br>`status`: `REPORTED`, `UNDER_REVIEW`, `RESOLVED` | closed object |
| ReplacementProposalRequest | `listingId`: string<br>`terms`: ContractTermsRequest | closed object |
| BreachCaseReportRequest | `milestoneId`: ['string', 'null']<br>`allegedBreachingRole`: `BUYER`, `SELLER`<br>`breachReasonCode`: BreachReasonCode<br>`severity`: `MINOR`, `MATERIAL`<br>`evidenceFileIds`: array<string> | closed object |
| BreachCaseResolutionRequest | `finalBreachingRole`: `BUYER`, `SELLER` / null<br>`decisionSource`: `ADMIN`, `INSPECTOR_L1_5`, `INSPECTOR_L2`, `MUTUAL`<br>`evidenceRefs`: array<string> | closed object |
| TerminationExecuteRequest | `terminationType`: `MUTUAL_TERMINATION`, `MUTUAL_REPLACEMENT`, `TERMINATION_FOR_BREACH`, `TERMINATION_FOR_FORCE_MAJEURE`, `ACTIVATION_FAILURE`<br>`attributionDecisionId`: string<br>`breachCaseId?`: ['string', 'null'] | closed object |
| ReputationPublicSummary | `userId`: string<br>`score`: number<br>`locked`: boolean<br>`lockHistory`: array<object> | closed object |
| ReputationActionProposalRequest | `actionType`: `UNLOCK_EARLY`, `CLEAR_ELEVATED_RISK`<br>`targetUserId`: string<br>`reason`: string | closed object |
| ReputationAction | `actionId`: string<br>`actionType`: `UNLOCK_EARLY`, `CLEAR_ELEVATED_RISK`<br>`targetUserId`: string<br>`proposedBy`: string<br>`status`: `PROPOSED`, `APPROVED`, `REJECTED` | closed object |
| AttributionDecision | `attributionDecisionId`: string<br>`breachCaseId`: ['string', 'null']<br>`decisionSource`: `SYSTEM`, `ADMIN`, `INSPECTOR_L1_5`, `INSPECTOR_L2`, `MUTUAL`<br>`finalBreachingRole`: `BUYER`, `SELLER` / null<br>`breachReasonCode`: NullableBreachReasonCode<br>`evidenceRefs`: array<string> | closed object |
| RemedyDecision | `remedyDecisionId`: string<br>`attributionDecisionId`: string<br>`breachCaseId`: ['string', 'null']<br>`contractId`: string<br>`buyerId`: string<br>`sellerId`: string<br>`affectedMilestoneIds`: array<string><br>`finalBreachingRole`: `BUYER`, `SELLER` / null<br>`breachReasonCode`: NullableBreachReasonCode<br>`decisionSource`: `SYSTEM`, `ADMIN`, `INSPECTOR_L1_5`, `INSPECTOR_L2`, `MUTUAL`<br>`penaltyEligible`: boolean<br>`reputationEligible`: boolean<br>`remedyLegs`: array<RemedyLeg> | closed object |
| RemedyLeg | `remedyLegId`: string<br>`remedyType`: `NONE`, `PAYMENT_SETTLEMENT`, `PAYMENT_REFUND`, `DEPOSIT_FORFEITURE`, `CONTRACTUAL_PENALTY`, `DAMAGES_COMPENSATION`<br>`fundType`: `BUYER_DEPOSIT`, `SELLER_DEPOSIT`, `MILESTONE_PAYMENT`<br>`role`: `BUYER`, `SELLER`<br>`amount`: string | closed object |
| BreachReasonCode | `NON_DELIVERY`, `SHORTFALL`, `LATE_DELIVERY`, `NON_CONFORMING_QUALITY`, `SIDE_SELLING`, `PRODUCTION_SHOCK_NON_FM`, `FUNDING_FAILURE`, `LATE_PAYMENT`, `FAILURE_TO_RECEIVE`, `WRONGFUL_REJECTION`, `FORCE_MAJEURE`, `MUTUAL_EXIT`, `ACTIVATION_FAILURE` | enum: NON_DELIVERY, SHORTFALL, LATE_DELIVERY, NON_CONFORMING_QUALITY, SIDE_SELLING, PRODUCTION_SHOCK_NON_FM, FUNDING_FAILURE, LATE_PAYMENT, FAILURE_TO_RECEIVE, WRONGFUL_REJECTION, FORCE_MAJEURE, MUTUAL_EXIT, ACTIVATION_FAILURE |
| NullableBreachReasonCode | BreachReasonCode / null | oneOf |
| ContractPage | `content`: array<ContractResponse><br>`page`: integer<br>`size`: integer<br>`totalElements`: integer<br>`totalPages`: integer | closed object |
| PublicUserResponse | `userId`: string<br>`organizationName`: string<br>`role`: `BUYER`, `SELLER`, `ADMIN`, `INSPECTOR`, `OPERATOR`<br>`verificationStatus`: `PENDING`, `VERIFIED`, `REJECTED` | closed object |
| VerifyUserRequest | `authorizationExpiresAt?`: ['string', 'null'] | closed object |
| RejectUserRequest | `reason`: string | closed object |
| OtpEmailRequest | `requestId`: string<br>`recipientUserId`: string<br>`recipientEmail`: string<br>`contractId`: string<br>`otpCode`: string<br>`signedContentHash`: string<br>`expiresAt`: string | closed object |
| InitiateSignRequest | `signerRole`: `BUYER`, `SELLER` | closed object |
| InitiateSignResponse | `otpId`: string<br>`expiresAt`: string | closed object |
| VerifyOtpAndSignRequest | `otpId`: string<br>`otpCode`: string | closed object |
| SellerWeighedRequest | `sellerDeclaredWeight`: number<br>`sellerEvidenceFileId`: string | closed object |
| BuyerReceivedRequest | `buyerReceivedWeight`: number<br>`buyerEvidenceFileId`: string | closed object |
| ForceMajeureClaimRequest | `evidenceFileId`: string | closed object |
| ForceMajeureResolveRequest | `decision`: `APPROVED`, `REJECTED` | closed object |
| AuditRecordList | array<object> | — |
| AuditReconciliationContent | `contractId`: string<br>`milestoneId`: ['string', 'null']<br>`settledAmount`: string<br>`seizedAmount`: string<br>`sourceEventId?`: ['string', 'null']<br>`releaseLegs`: array<MoneyLeg><br>`refundLegs`: array<MoneyLeg> | closed object |
| AuditHashResponse | `contractId`: string<br>`recordHash`: string | closed object |
| MoneyLeg | `entryType`: `RELEASE_TO_SELLER`, `REFUND_TO_BUYER`, `DEPOSIT_FORFEITURE`, `CONTRACTUAL_PENALTY`, `DAMAGES_COMPENSATION`<br>`fundType`: `BUYER_DEPOSIT`, `SELLER_DEPOSIT`, `MILESTONE_PAYMENT`<br>`remedyDecisionId`: ['string', 'null']<br>`remedyLegId`: ['string', 'null']<br>`amount`: string<br>`sourceEventId`: string | closed object |
| LedgerEntryList | array<object> | — |
| ExternalVerifierRequest | `keyId`: string<br>`signedPayload`: object<br>`signature`: string | closed object |
| ErrorEnvelope | `timestamp`: string<br>`status`: integer<br>`error`: string<br>`code`: string<br>`message`: string<br>`path`: string<br>`traceId`: string | closed object |

## **10.3 Domain/bank events (44)**

| Event | Producer | Consumers | Payload fields | Nullability/idempotency/guard |
|---|---|---|---|---|
| contract.signed | contract-service | escrow-service, analytics-service, audit-service | `contractId`: uuid<br>`commodity`: `COFFEE`, `RICE`, `RUBBER`, `CASHEW`<br>`buyerId`: uuid<br>`sellerId`: uuid<br>`totalAmount`: money<br>`buyerDepositAmount`: money<br>`sellerDepositAmount`: money<br>`signedContentHash`: string<br>`signedAt`: string |  |
| escrow.deposit_locked | escrow-service | contract-service, escrow-service | `contractId`: uuid<br>`buyerDepositState`: `DEPOSIT_LOCKED`, `DEPOSIT_RELEASED`, `DEPOSIT_SEIZED`<br>`sellerDepositState`: `DEPOSIT_LOCKED`, `DEPOSIT_RELEASED`, `DEPOSIT_SEIZED` |  |
| escrow.deposit_lock_failed | escrow-service | contract-service | `contractId`: uuid<br>`failedLeg`: `BUYER`, `SELLER`, `BOTH`<br>`buyerDepositState`: string<br>`sellerDepositState`: string<br>`reason`: string |  |
| bank.lock_requested | escrow-service | bank-service | `contractId`: uuid<br>`milestoneId?`: uuid / null<br>`userId`: uuid<br>`entryType`: `LOCK_BUYER_DEPOSIT`, `LOCK_SELLER_DEPOSIT`, `LOCK_MILESTONE`<br>`fundType`: fundType<br>`remedyDecisionId`: nullableUuid<br>`remedyLegId`: nullableUuid<br>`amount`: positiveMoney<br>`sourceEventId`: uuid |  |
| bank.lock_completed | bank-service | escrow-service | `contractId`: uuid<br>`milestoneId?`: uuid / null<br>`userId`: uuid<br>`entryType`: `LOCK_BUYER_DEPOSIT`, `LOCK_SELLER_DEPOSIT`, `LOCK_MILESTONE`<br>`fundType`: fundType<br>`remedyDecisionId`: nullableUuid<br>`remedyLegId`: nullableUuid<br>`amount`: positiveMoney<br>`sourceEventId`: uuid<br>`ledgerEntryId`: uuid |  |
| bank.lock_failed | bank-service | escrow-service | `contractId`: uuid<br>`milestoneId?`: uuid / null<br>`userId`: uuid<br>`entryType`: `LOCK_BUYER_DEPOSIT`, `LOCK_SELLER_DEPOSIT`, `LOCK_MILESTONE`<br>`fundType`: fundType<br>`remedyDecisionId`: nullableUuid<br>`remedyLegId`: nullableUuid<br>`amount`: positiveMoney<br>`sourceEventId`: uuid<br>`failureReason`: string |  |
| bank.release_requested | escrow-service | bank-service | `contractId`: uuid<br>`milestoneId?`: uuid / null<br>`userId`: uuid<br>`entryType`: const `RELEASE_TO_SELLER`<br>`fundType`: fundType<br>`remedyDecisionId`: nullableUuid<br>`remedyLegId`: nullableUuid<br>`amount`: positiveMoney<br>`sourceEventId`: uuid |  |
| bank.release_completed | bank-service | escrow-service | `contractId`: uuid<br>`milestoneId?`: uuid / null<br>`userId`: uuid<br>`entryType`: const `RELEASE_TO_SELLER`<br>`fundType`: fundType<br>`remedyDecisionId`: nullableUuid<br>`remedyLegId`: nullableUuid<br>`amount`: positiveMoney<br>`sourceEventId`: uuid<br>`ledgerEntryId`: uuid |  |
| bank.release_failed | bank-service | escrow-service | `contractId`: uuid<br>`milestoneId?`: uuid / null<br>`userId`: uuid<br>`entryType`: const `RELEASE_TO_SELLER`<br>`fundType`: fundType<br>`remedyDecisionId`: nullableUuid<br>`remedyLegId`: nullableUuid<br>`amount`: positiveMoney<br>`sourceEventId`: uuid<br>`failureReason`: string |  |
| bank.seize_requested | escrow-service | bank-service | `contractId`: uuid<br>`milestoneId?`: uuid / null<br>`userId`: uuid<br>`entryType`: `DEPOSIT_FORFEITURE`, `CONTRACTUAL_PENALTY`, `DAMAGES_COMPENSATION`<br>`fundType`: fundType<br>`remedyDecisionId`: uuid<br>`remedyLegId`: uuid<br>`amount`: positiveMoney<br>`sourceEventId`: uuid |  |
| bank.seize_completed | bank-service | escrow-service | `contractId`: uuid<br>`milestoneId?`: uuid / null<br>`userId`: uuid<br>`entryType`: `DEPOSIT_FORFEITURE`, `CONTRACTUAL_PENALTY`, `DAMAGES_COMPENSATION`<br>`fundType`: fundType<br>`remedyDecisionId`: uuid<br>`remedyLegId`: uuid<br>`amount`: positiveMoney<br>`sourceEventId`: uuid<br>`ledgerEntryId`: uuid |  |
| bank.seize_failed | bank-service | escrow-service | `contractId`: uuid<br>`milestoneId?`: uuid / null<br>`userId`: uuid<br>`entryType`: `DEPOSIT_FORFEITURE`, `CONTRACTUAL_PENALTY`, `DAMAGES_COMPENSATION`<br>`fundType`: fundType<br>`remedyDecisionId`: uuid<br>`remedyLegId`: uuid<br>`amount`: positiveMoney<br>`sourceEventId`: uuid<br>`failureReason`: string |  |
| bank.refund_requested | escrow-service | bank-service | `contractId`: uuid<br>`milestoneId?`: uuid / null<br>`userId`: uuid<br>`entryType`: const `REFUND_TO_BUYER`<br>`fundType`: fundType<br>`remedyDecisionId`: nullableUuid<br>`remedyLegId`: nullableUuid<br>`amount`: positiveMoney<br>`sourceEventId`: uuid |  |
| bank.refund_completed | bank-service | escrow-service | `contractId`: uuid<br>`milestoneId?`: uuid / null<br>`userId`: uuid<br>`entryType`: const `REFUND_TO_BUYER`<br>`fundType`: fundType<br>`remedyDecisionId`: nullableUuid<br>`remedyLegId`: nullableUuid<br>`amount`: positiveMoney<br>`sourceEventId`: uuid<br>`ledgerEntryId`: uuid |  |
| bank.refund_failed | bank-service | escrow-service | `contractId`: uuid<br>`milestoneId?`: uuid / null<br>`userId`: uuid<br>`entryType`: const `REFUND_TO_BUYER`<br>`fundType`: fundType<br>`remedyDecisionId`: nullableUuid<br>`remedyLegId`: nullableUuid<br>`amount`: positiveMoney<br>`sourceEventId`: uuid<br>`failureReason`: string |  |
| contract.settled | contract-service | reputation-service, analytics-service | `contractId`: uuid<br>`buyerId`: uuid<br>`sellerId`: uuid |  |
| contract.terminated | contract-service | audit-service, analytics-service | `contractId`: uuid<br>`terminationType`: `WITHDRAW_OFFER`, `MUTUAL_TERMINATION`, `MUTUAL_REPLACEMENT`, `TERMINATION_FOR_BREACH`, `TERMINATION_FOR_FORCE_MAJEURE`, `ACTIVATION_FAILURE`<br>`requestedBy`: `BUYER`, `SELLER`, `SYSTEM`<br>`finalBreachingRole`: breachingRole<br>`breachReasonCode`: nullableBreachReasonCode<br>`remedyDecisionId`: nullableUuid<br>`breachCaseId`: nullableUuid<br>`affectedMilestoneIds`: array<uuid><br>`supersededByContractId`: nullableUuid<br>`replacesContractId`: nullableUuid | Except pre-sign WITHDRAW_OFFER, publish only after the expected remedyDecisionId/remedyLegId set is complete in the bank ledger and remaining contract lock is zero. |
| breach.reported | contract-service | audit-service | `breachCaseId`: uuid<br>`contractId`: uuid<br>`milestoneId`: nullableUuid<br>`requestedBy`: `BUYER`, `SELLER`, `SYSTEM`<br>`allegedBreachingRole`: `BUYER`, `SELLER` / null<br>`breachReasonCode`: breachReasonCode<br>`severity`: `MINOR`, `MATERIAL` |  |
| remedy.finalized | contract-service | escrow-service, reputation-service, audit-service | `remedyDecisionId`: uuid<br>`attributionDecisionId`: uuid<br>`breachCaseId`: nullableUuid<br>`contractId`: uuid<br>`buyerId`: uuid<br>`sellerId`: uuid<br>`affectedMilestoneIds`: array<uuid><br>`finalBreachingRole`: breachingRole<br>`breachReasonCode`: nullableBreachReasonCode<br>`decisionSource`: `SYSTEM`, `ADMIN`, `INSPECTOR_L1_5`, `INSPECTOR_L2`, `MUTUAL`<br>`penaltyEligible`: boolean<br>`reputationEligible`: boolean<br>`remedyLegs`: array<object> | remedyLegId is unique per bank leg; remedyDecisionId groups all legs and is unique for at most one reputation lock. |
| milestone.settled | contract-service | escrow-service, analytics-service, audit-service | `contractId`: uuid<br>`milestoneId`: uuid<br>`lockedAmount`: money<br>`actualAmount`: money<br>`recipients`: array<object> |  |
| milestone.dispute_resolved | contract-service | reputation-service | `contractId`: uuid<br>`milestoneId`: uuid<br>`flaggedBy`: const `BUYER`<br>`flaggedByUserId`: uuid<br>`resolutionFavors`: `BUYER`, `SELLER` |  |
| milestone.level2_provisional_settled | contract-service | escrow-service | `contractId`: uuid<br>`milestoneId`: uuid<br>`level1_5ReportId`: uuid<br>`level1_5EntitlementAmount`: money<br>`releaseRate`: string<br>`sellerReleaseAmount`: money<br>`remainingLockedAmount`: money |  |
| milestone.level2_buffer_reconciled | contract-service | escrow-service | `contractId`: uuid<br>`milestoneId`: uuid<br>`level1_5ReportId`: uuid<br>`level2ReportId`: uuid<br>`finalSellerEntitlementAmount`: money<br>`alreadyReleasedAmount`: money<br>`sellerAdditionalReleaseAmount`: money<br>`buyerRefundAmount`: money<br>`overReleaseAmount`: money |  |
| milestone.level2_terminal_settled | contract-service | escrow-service | `contractId`: uuid<br>`milestoneId`: uuid<br>`level1_5ReportId`: uuid<br>`finalSellerEntitlementAmount`: money<br>`alreadyReleasedAmount`: money<br>`sellerAdditionalReleaseAmount`: money<br>`buyerRefundAmount`: money |  |
| inspection.report_confirmed | inspection-service | audit-service, contract-service | `reportId`: uuid<br>`contractId`: uuid<br>`milestoneId`: uuid<br>`tier`: `LEVEL_1_5`, `LEVEL_2`<br>`normalizedResult`: inspectionSettlementResultV1<br>`resultHash`: string<br>`reportFileHash`: string<br>`reportHash`: string<br>`confirmedAt`: string<br>`inspectorId?`: uuid<br>`confirmedByAdminId?`: uuid |  |
| milestone.seller_weighed | contract-service | file-service, audit-service | `contractId`: uuid<br>`milestoneId`: uuid<br>`sellerDeclaredWeight`: number<br>`sellerEvidenceFileId`: uuid |  |
| milestone.buyer_confirmed | contract-service | audit-service | `contractId`: uuid<br>`milestoneId`: uuid<br>`buyerReceivedWeight`: number<br>`buyerEvidenceFileId`: uuid |  |
| milestone.flagged | contract-service |  | `contractId`: uuid<br>`milestoneId`: uuid<br>`flaggedBy`: const `BUYER` |  |
| milestone.force_majeure_claimed | contract-service | audit-service | `contractId`: uuid<br>`milestoneId`: uuid<br>`forceMajeureClaimId`: uuid |  |
| milestone.force_majeure_resolved | contract-service | escrow-service, audit-service | `contractId`: uuid<br>`milestoneId`: uuid<br>`forceMajeureClaimId`: uuid<br>`decision`: `APPROVED`, `REJECTED` |  |
| milestone.cancelled_with_penalty | contract-service | analytics-service, audit-service | `contractId`: uuid<br>`milestoneId`: uuid<br>`remedyDecisionId`: uuid<br>`finalBreachingRole`: `BUYER`, `SELLER`<br>`breachReasonCode`: breachReasonCode<br>`penaltyAmount`: money |  |
| escrow.milestone_funding_failed | escrow-service | contract-service | `contractId`: uuid<br>`milestoneId`: uuid<br>`reason`: string |  |
| reputation.locked | reputation-service | user-service | `userId`: uuid<br>`lockedUntil`: string<br>`reasonCode`: string<br>`lockRevision`: integer<br>`occurredAt`: string |  |
| reputation.unlocked | reputation-service | user-service | `userId`: uuid<br>`effectiveLockedUntil`: string / null<br>`reasonCode`: string<br>`lockRevision`: integer<br>`occurredAt`: string |  |
| file.ready | file-service | contract-service, inspection-service | `fileId`: uuid<br>`uploadedBy`: string<br>`ingestChannel`: `DIRECT_UPLOAD`, `EMAIL_INTAKE`, `SYSTEM_GENERATED`<br>`contentType`: string<br>`fileSize`: integer<br>`emailMeta?`: object |  |
| file.failed | file-service | contract-service, inspection-service | `fileId`: uuid<br>`uploadedBy`: string<br>`ingestChannel`: `DIRECT_UPLOAD`, `EMAIL_INTAKE`, `SYSTEM_GENERATED`<br>`failureReason`: string |  |
| bank.security_lock_changed | bank-service | audit-service | `action`: `LOCKED`, `UNLOCKED`<br>`verifierOrgId`: string<br>`reason`: string<br>`signedPayload`: object |  |
| bank.verifier_key_registered | bank-service | audit-service | `publicKeyFingerprint`: string<br>`rotationSignedByOldKey?`: ['boolean', 'null'] |  |
| bank.large_transaction_flagged | bank-service | reputation-service, audit-service | `transferId`: uuid<br>`relatedEntryId?`: nullableUuid<br>`contractId`: uuid<br>`userId`: uuid<br>`amount`: positiveMoney<br>`transferType`: string<br>`occurredAt`: string |  |
| bank.suspicious_report_created | bank-service | audit-service | `buyerId`: uuid<br>`sellerId`: uuid<br>`windowEnd`: string<br>`reportHash`: string |  |
| analytics.structuring_pattern_detected | analytics-service | reputation-service, bank-service | `buyerId`: uuid<br>`sellerId`: uuid<br>`contractIds`: array<uuid><br>`nearThresholdCount`: integer<br>`windowStart`: string<br>`windowEnd`: string<br>`detectedAt`: string |  |
| reputation.elevated_risk_cleared | reputation-service | audit-service | `buyerId`: uuid<br>`sellerId`: uuid<br>`clearedByAdminId`: uuid<br>`reason`: string<br>`proposedByOperatorId`: uuid<br>`approvedByAdminId`: uuid |  |
| inspection.level2_commissioned | inspection-service | audit-service | `contractId`: uuid<br>`commissionId`: uuid<br>`org`: string<br>`requestedAt`: string |  |
| file.email_notice | file-service | inspection-service | `senderDomain`: string<br>`spfDkimResult`: string<br>`subject`: string<br>`snippet`: string<br>`receivedAt`: string |  |

## **10.4 Notification commands (16)**

| Command | Producer | Consumer | Required payload |
|---|---|---|---|
| contractAnchorRequested | contract-service | notification-service |  |
| contractActivationFailedRequested | contract-service | notification-service |  |
| contractTerminatedRequested | contract-service | notification-service |  |
| breachNoticeRequested | contract-service | notification-service |  |
| remedyFinalizedRequested | contract-service | notification-service |  |
| milestoneFundingStatusRequested | contract-service | notification-service |  |
| milestoneStatusRequested | contract-service | notification-service |  |
| milestoneAnchorRequested | audit-service | notification-service |  |
| level2CommissionRequested | inspection-service | notification-service |  |
| auditDigestRequested | audit-service | notification-service |  |
| auditFailureRequested | audit-service | notification-service |  |
| securityLockChangedRequested | bank-service | notification-service |  |
| reconciliationMismatchRequested | bank-service | notification-service |  |
| userKycResultRequested | user-service | notification-service |  |
| userLockChangedRequested | user-service | notification-service |  |
| verifierKeyAnchorRequested | bank-service | notification-service |  |

# **11. Verification Matrix và release gate**

Mỗi invariant P0/P1/P2 phải có test theo đúng boundary. P0 money/immutability/evidence là gate trước golden-flow merge; P1 gate trước external exposure; P2 gate trước pilot claim. Matrix hiện có 55 rows.

| # | Priority | Invariant | Acceptance test & source |
|---|---|---|---|
| 1 | P0 | Event lặp không release/lock tiền hai lần | Publish cùng bank request nhiều lần với `payload.sourceEventId == envelope.eventId` → đúng 1 bộ ledger entry; result dùng eventId mới và causationId=request eventId<br>_Nguồn: bank §4 idempotency_ |
| 2 | P0 | Ledger append-only — không UPDATE/DELETE | Repository chỉ có insert/select; test lớp repo + quyền DB user<br>_Nguồn: bank ledger, governance nguyên tắc 3_ |
| 3 | P0 | Hợp đồng đã `SIGNED` không sửa được terms | Gọi update terms sau ký → `409`; hash không đổi<br>_Nguồn: signature §3, milestone §3.1_ |
| 4 | P0 | `audit_record` bị sửa phải bị phát hiện | Tamper trực tiếp DB (đổi amount/hash) → `VerifyChainJob` fail + đúng vị trí<br>_Nguồn: hash-chain §4.2/§5_ |
| 5 | P0 | Ledger lệch với record đã anchor phải bị phát hiện | Sửa 1 ledger leg lệch typed audit projection (`contractId`, `milestoneId`, `settledAmount`, `seizedAmount`, release/refund legs) → `LedgerAuditReconciliationJob` bắn `reconciliation_mismatch`<br>_Nguồn: bank §5b.1; hash-chain §4.5_ |
| 6 | P0 | Bank completion callback lặp không nhân đôi | Gửi duplicate callback → 1 bộ entry, trạng thái không đổi lần 2<br>_Nguồn: bank §4_ |
| 7 | P0 | DLQ replay không side effect lặp | Replay `eventId` gốc sau khi đã xử lý → consumer skip, không entry mới<br>_Nguồn: governance §8b_ |
| 8 | P0 | Contract terminal không còn tiền lock treo | Normal completion/termination phát `remedy.finalized`, mock một bank leg còn missing/failed → contract vẫn non-terminal và chưa publish lifecycle event; khi đủ expected `remedyLegId` + remaining lock = 0 mới cho `contract.settled`/`contract.terminated`/`SUPERSEDED`/`ACTIVATION_FAILED` (pre-sign `WITHDRAWN` zero by construction)<br>_Nguồn: milestone-escrow §3.1/§6.7; bank §3.2; escrow_ |
| 9 | P0 | Kill switch chặn toàn bộ dòng tiền | ES256/RFC8785 request: wrong key/signature/timestamp/action/replayed nonce bị exact 401/409 code; `system_lock ACTIVE` làm mọi `bank.*_requested` fail; Gateway không retry và cap 64 KB<br>_Nguồn: bank §3.5.2-3.5.5; gateway §3.4_ |
| 10 | P0 | Provisional Level 2 không release quá mức sàn và bảo toàn tiền | Buyer im lặng → `sellerReleaseAmount` dùng release floor, không buyer refund; mọi reconcile/terminal leg explicit, tổng release+refund+remaining lock bảo toàn batchAmount, zero command bị omit<br>_Nguồn: milestone §3.2 Bước 0-3; bank §3.3_ |
| 11 | P0 | Khoá cọc fail không kẹt im lặng | Bank fail liên tục → hết 3 retry → `escrow.deposit_lock_failed` + contract giữ `SIGNED` + notification 3 bên; `RetryDepositLock` sau khi hết lỗi → `ACTIVE`<br>_Nguồn: milestone §3.1_ |
| 11b | P0 | Partial lock không nuốt tiền: buyer LOCKED + seller fail → terminal phải refund | Mock seller-leg fail vĩnh viễn → `MarkActivationFailed` → `bank.refund_requested` cho leg buyer, qua `ACTIVATION_REFUND_PENDING`, chỉ `ACTIVATION_FAILED` khi refund confirmed; refund fail → đứng lại + alert<br>_Nguồn: milestone §3.1 (18/07)_ |
| 11c | P0 | 2 chữ ký phải cùng bản terms | Buyer ký, đổi terms (phải bị 409); ép seller ký hash khác → REJECT, không SIGNED<br>_Nguồn: signature §6 bước 4 (18/07)_ |
| 11d | P0 | Mọi field record đều được hash commit | Tamper riêng `source_type`/`subject_id`/`prev_hash_subject` (giữ nguyên content) → `VerifyChainJob` recompute fail<br>_Nguồn: hash-chain §3 canonical (18/07 r2)_ |
| 11e | P0 | `audit_anchor` cũng bất biến | Repository/DB user chỉ INSERT+SELECT trên audit_anchor; UPDATE/DELETE → denied<br>_Nguồn: hash-chain §3 (18/07 r2)_ |
| 11f | P0 | Source/result hash commitment verify riêng với record hash | `Contract.signedContentHash` 3-way khớp; inspection recompute `resultHash` từ RFC8785 normalized result và `reportHash` từ report identity/file/result/timestamp/actor; tamper bất kỳ field nào bị reject; `record_hash` vẫn verify riêng<br>_Nguồn: hash-chain §4.1; inspection §2.3/§4_ |
| 11g | P0 | Allegation chưa final không đụng tiền/reputation | Mở `BreachCase` (`breach.reported`) rồi thử phát seize/forfeiture/penalty + kiểm tra reputation consumer → mọi lệnh tiền bị chặn khi `status != RESOLVED`; reputation không có `lock_entry` mới<br>_Nguồn: milestone-escrow §6.4 invariant (19/07)_ |
| 11h | P0 | Người request termination không tự động bị coi là vi phạm | Seller yêu cầu terminate vì buyer `FUNDING_FAILURE` → `finalBreachingRole = BUYER`, seller không bị seize/lock; mirror chiều ngược<br>_Nguồn: milestone-escrow §6.0/§6b (19/07)_ |
| 11i | P0 | `finalBreachingRole = NULL` → không ai bị phạt | `MUTUAL_TERMINATION`/`MUTUAL_REPLACEMENT`/FM/`ACTIVATION_FAILURE` → cọc về đúng chủ (`REFUND_TO_BUYER`/`RELEASE_TO_SELLER`), zero `DEPOSIT_FORFEITURE`/`CONTRACTUAL_PENALTY`, zero `lock_entry`<br>_Nguồn: milestone-escrow §6.5/§6.6/§6.7; bank §3.2; reputation §3 (19/07)_ |
| 11j | P0 | Contractual penalty không vượt cap LegalProfile | `sign()` với `buyerPenaltyRate`/`sellerPenaltyRate` > `maxContractualPenaltyRate` (8% VN_COMMERCIAL_LAW) → reject 400; penalty tính trên batchAmount phần bị vi phạm, không trên totalAmount<br>_Nguồn: milestone-escrow §2.1b (19/07)_ |
| 11k | P0 | Không double recovery | Remedy calculator: với `damagesPolicy = CIVIL_PENALTY_ONLY_UNLESS_EXPRESSLY_CUMULATIVE`/`EXPRESS_PENALTY_ONLY` → không có `DAMAGES_COMPENSATION` leg; với `COMMERCIAL_CUMULATIVE_IF_PROVEN` → DAMAGES chỉ khi có bằng chứng, và `DEPOSIT_FORFEITURE` offset vào penalty; ledger không có 2 khoản seize bù cùng 1 tổn thất<br>_Nguồn: milestone-escrow §2.1b/§6.7; bank §2 (19/07, sửa lần 2)_ |
| 11l | P0 | Mutual replacement nối đúng 2 contract, không double-count | Supersede → contract cũ `SUPERSEDED` + `supersededByContractId`; contract mới `replacesContractId`; milestone `SETTLED` cũ giữ nguyên; tiền lock cũ refund hết (tổng lock contract cũ = 0), contract mới lock từ 0<br>_Nguồn: milestone-escrow §6.6 (19/07)_ |
| 11m | P0 | Milestone funding failure không kẹt tiền + không phạt oan seller | Mock bank fail lock batch milestone 2 → hết retry → `FUNDING_FAILED`, đồng hồ giao hàng seller tạm dừng (không trigger seller-quá-hạn), leg đã lock refund; hết `fundingCureWindowDays` → buyer breach Rổ A, seller không bị seize<br>_Nguồn: milestone-escrow §6b (19/07)_ |
| 11n | P0 | Termination pro-rata không đụng milestone đã `SETTLED` | `TERMINATION_FOR_BREACH` giữa hợp đồng (milestone 1-2 `SETTLED`, 3-5 chưa) → penalty/forfeiture chỉ tính trên batch 3-5; ledger milestone 1-2 không có entry mới, tiền đã release không bị truy thu<br>_Nguồn: milestone-escrow §6.1 pro-rata (19/07)_ |
| 11o | P0 | Một `remedyDecisionId` → đúng 1 bộ bank legs, ≤ 1 reputation lock (chặn double-consume) | Replay `remedy.finalized` / gửi leg trùng → `ledger_entry.remedy_leg_id` UNIQUE chặn từng leg (đơn vị dedup đúng — 1 decision nhiều legs), reconcile `SUM` group theo `remedy_decision_id` khớp đúng 1 bộ; reputation `lock_entry.remedy_decision_id` UNIQUE chặn lock thứ 2 kể cả từ event khác<br>_Nguồn: milestone-escrow §7.2; reputation §2.1; bank §2 DDL (19/07 lần 2, schema-hoá lần 4)_ |
| 11p | P0 | Supersede crash windows không nuốt tiền/không kẹt hợp đồng | (a) draft replacement không đủ 2 chữ ký → cũ vẫn `ACTIVE`, zero sự kiện tiền; (b) mới `SIGNED`, refund cũ fail giữa chừng → cũ đứng `SUPERSEDE_REFUND_PENDING` + alert, không nhảy `SUPERSEDED`; (c) cũ `SUPERSEDED` xong, activation mới fail → mới đi đường `ACTIVATION_FAILED` chuẩn, cũ không rollback<br>_Nguồn: milestone-escrow §6.6 saga (19/07 lần 3)_ |
| 11q | P0 | Replay evidence event không nhân đôi audit record/anchor | Redeliver cùng `remedy.finalized`/`contract.terminated` (eventId cũ) → `audit_record.source_event_id` UNIQUE reject/no-op, không record mới, không anchor mới, không email evidence thứ 2<br>_Nguồn: hash-chain §3 `source_event_id` (19/07 lần 3)_ |
| 12 | P1 | User khác không thao tác contract người khác | Ownership check → `403` (contract, escrow, statement)<br>_Nguồn: contract/escrow, gateway §4_ |
| 13 | P1 | Client không tự tiêm identity | Gửi kèm `X-User-Id`/`X-User-Role`/`X-Gateway-Secret` giả → bị strip/overwrite, downstream nhận identity từ JWT<br>_Nguồn: gateway §2/§7_ |
| 14 | P1 | `/internal/**` không ra ngoài | Gọi từ external → `404/403` tại Gateway, không forward<br>_Nguồn: gateway §3.5_ |
| 15 | P1 | File nhiễm virus không thành evidence | Upload EICAR qua `StoreOnBehalfOf` và qua email intake → `FAILED`, không có `file.ready`, không gắn được vào milestone/report<br>_Nguồn: file-service §3-4_ |
| 16 | P1 | OPERATOR không chạm được đường ADMIN | OPERATOR gọi approve maker-checker / `RetryDepositLock` / `/admin/audit` → `403`<br>_Nguồn: governance §5.4_ |
| 17 | P1 | ADMIN không tự approve đề xuất của chính mình | Cùng 1 account propose rồi approve → reject<br>_Nguồn: governance §5.3_ |
| 18 | P1 | Unlock sớm không mở khoá nhầm khi nhiều lock chồng | 2 lock (T+60, T+30), unlock sớm lock 1 → `lockedUntil` user-service = T+30, `sign()` vẫn bị chặn<br>_Nguồn: reputation §7, user §2.2_ |
| 19 | P1 | `sign()` fail-closed khi user-service chết | Stop user-service → sign bị reject, không fallback anonymous<br>_Nguồn: user §3/§7_ |
| 20 | P1 | OTP fail không gửi muộn | Provider trả lỗi → contract-service nhận 5xx; xác nhận không có mail nào được gửi sau đó với `requestId` cũ trừ khi caller tự retry<br>_Nguồn: notification §2.2/§5_ |
| 21 | P1 | Public DTO không lộ PII | `GET /users/{id}` → không email/phone/address; public-summary yêu cầu JWT<br>_Nguồn: user §4.1, gateway §3.2_ |
| 21b | P1 | OTP challenge binding | Verify với JWT user khác / otpId khác contract / terms đã đổi sau phát OTP → 403/409/invalidate; không lấy được "OTP mới nhất" của người khác<br>_Nguồn: signature §6-§7 (18/07)_ |
| 21c | P1 | Lock ordering sống qua restart | Apply unlock revision N, restart service, deliver locked revision N-1 (eventId mới) → bị bỏ qua nhờ `last_lock_revision` persist<br>_Nguồn: user §2.2 (18/07)_ |
| 21d | P1 | `legalHold` chặn xoá tuyệt đối | Set legalHold + retention đã hết → lifecycle job gọi `DeleteFile` → REJECT, file còn nguyên<br>_Nguồn: file-service §7, governance §8 (18/07 r2)_ |
| 21e | P1 | Xoá file 2 bước tự lành | Mock MinIO delete fail sau tombstone → job re-run → cả DB lẫn blob về trạng thái nhất quán, không rác<br>_Nguồn: file-service §7 (18/07 r2)_ |
| 21f | P1 | Listing/offer fail-closed khi user-service chết | Stop user-service → CreateListing/CreateOffer trả 503, user đang khoá không lách được<br>_Nguồn: user §3 (18/07 r2)_ |
| 22 | P2 | Cà phê/cao su bắt buộc plot; gạo/điều không | Commodity-gate test 2 chiều: tạo listing thiếu plot → fail/pass đúng theo commodity<br>_Nguồn: product (EUDR gate)_ |
| 23 | P2 | Analytics chết không ảnh hưởng giao dịch | Stop analytics-service → settle milestone vẫn hoàn tất; analytics **catch-up từ queue backlog** khi sống lại (outage ngắn — KHÔNG phải cold rebuild, sửa 18/07/2026)<br>_Nguồn: governance §3 (read model)_ |
| 24 | P2 | Notification dedup theo recipient + type | 1 event 2 recipients + retry → mỗi (recipient, type) đúng 1 mail `SENT`<br>_Nguồn: notification §3.1/§5_ |
| 25 | P2 | Restore đụng tiền phải qua verify mới mở | Restore `bank_db` test env → 2 job verify chạy pass trước khi mở traffic (demo DR)<br>_Nguồn: governance §8c_ |
| 26 | P2 | Event cũ không ghi đè quyết định mới | Deliver `reputation.locked/unlocked` sai thứ tự (`lockRevision` nhỏ hơn — sửa 18/07) → bị bỏ qua<br>_Nguồn: user §2.2_ |
| 26b | P2 | Plot overlap chéo seller bị bắt | Seller B đăng ký polygon trùng/lọt trong polygon Seller A → `plotReuseRisk = HIGH` + OPERATOR review, không auto-reject<br>_Nguồn: product §2.3b (18/07)_ |
| 26c | P2 | Yield anomaly là signal, không phải gate | `declaredQuantity` vượt Σ(plotArea×maxYield) → `yieldRisk` + yêu cầu bổ sung/review; listing KHÔNG bị auto-reject<br>_Nguồn: product §2.3b (18/07 r2)_ |
| 26d | P2 | Deployment policy enforce được | CI check: image pin theo digest, secret không nằm trong image, protected branch bắt buộc approval — fail build nếu vi phạm (policy test, không phải runtime test)<br>_Nguồn: governance §2 threat model (18/07 r2)_ |
| 26e | P2 | Delta 1 vượt threshold không auto-gán bẻ kèo | Shortfall > threshold, không FM claim → milestone mở `BreachCase` (không bắn `milestone.cancelled_with_penalty` tự động); pro-rata phần đã giao vẫn release<br>_Nguồn: milestone-escrow §4 nhánh 2 (19/07)_ |
| 26f | P2 | Reputation chỉ phạt strategic breach | `remedy.finalized` với `breachReasonCode = PRODUCTION_SHOCK_NON_FM` → không `lock_entry`; với `SIDE_SELLING` → có; `zeroProgressMultiplier` 1.5x chỉ khi strategic + 0 settled<br>_Nguồn: reputation §3/§4.3 (19/07)_ |
| 26g | P2 | Analytics đo `finalBreachingRole`, không đo người bấm nút | `contract.terminated` các nhánh → `fact_contract_termination` tách `terminationType`/`requestedBy`/`finalBreachingRole`; mutual không đếm vào default rate<br>_Nguồn: analytics (19/07)_ |
| 26h | P2 | Allegation bị bác/được miễn trách không phát hậu quả tiêu cực (đổi tên 19/07 lần 2 — Phase 2 cố tình chưa có cure state, không gọi "cure thành công") | `BreachCase` RESOLVED với `finalBreachingRole = NULL` → không termination, không lock_entry cho bên bị cáo buộc; `remedy.finalized` nếu phát chỉ chứa refund/release legs<br>_Nguồn: milestone-escrow §6.4; reputation §4.3 (19/07)_ |
| 26i | P2 | Inspection đang pending chặn auto-confirm | Milestone `CONTESTED`/có commission Level 1.5-2 đang chạy → hết `buyerConfirmWindowDays` KHÔNG auto `CONFIRM_CLEAN`, không release; timer chỉ áp khi milestone còn đúng state `BUYER_RECEIVED`<br>_Nguồn: milestone-escrow §3.2 bổ sung (19/07)_ |

## **11.1 Cross-contract release checks**

- Producer, consumer, eventType, payload required/nullability và canonical side effect khớp YAML.
- State/action/API không dùng current value/endpoint bị retire.
- Money/reputation chỉ từ final decision; no double-consume/seize; cọc về đúng owner.
- Terminal zero-lock và expected legs complete.
- API write idempotency + DB unique/append-only constraint đủ chứng minh invariant.
- Verification Matrix row có owner test và environment phù hợp; DB tamper test không mock.

# **12. Migration, implementation plan và known limitations**

## **12.1 Phase 1 retirement/migration**

Remove/retire generic cancel endpoint, `contract.cancelled`, `initiatedBy`-as-breacher logic, `SEIZE_PENALTY`, `REFUNDED_PARTIAL`, persisted reputation score, direct negative consumer từ lifecycle/allegation, dual money balance và audit dedup thiếu source event. Backfill/migration phải tạo LegalProfile, split termination facts, add remedy/leg IDs, unique constraints, lock revisions, file ingest channel/state, audit source IDs và analytics termination dimensions.

Compatibility triển khai theo thứ tự schema additive → producer dual/read compatibility nếu cần → consumer canonical → cutover endpoint/event → remove legacy. Không backfill synthetic attribution sai; record lịch sử không đủ evidence phải đánh dấu legacy/unknown, không suy người request là breacher.

## **12.2 Workstreams/T0–T13**

Implementation plan chia theo golden-flow slices và service workstreams, không “một người một service” cố định. Luồng chính contract–escrow–bank–audit được tích hợp sớm; product/file/pricing, inspection/reputation và user/notification/analytics chạy song song với contract tests. Test/docs là trách nhiệm ngang đội và double-check ở boundary producer–consumer/money/security.

Các mốc T0–T13 phải bao phủ: common contracts/migrations; identity/eligibility; listing/offer/sign; deposits/activation failure; milestone funding/delivery; inspection; attribution/remedy; termination/replacement; reputation; audit/reconciliation; notification/analytics; security/DR; end-to-end verification/cutover.

## **12.3 Known limitations/Future Work**

- Bank/custodian, CA signature, Level 2 institutions, source price/email and OpenTimestamps production integration chưa hoàn tất.
- Legal classification of deposits/penalty/damages, contract template và dispute clauses cần counsel validation.
- Full Zero Trust/mTLS mesh/HSM/multi-region HA/cold rebuild không được claim.
- Full amendment, partial mutual termination, quality-indexed settlement và automated insurance/credit nằm ngoài Phase 2.
- EUDR geo evidence không tự chứng minh due diligence; AML/yield/overlap/risk là signal.
- Analytics/notification projection có eventual consistency; analytics outage ngắn catch-up từ backlog, không rebuild từ zero.

# **13. Superseded terminology register**

| **Superseded** | **Current** | **Xử lý** |
|---|---|---|
| `/contracts/{id}/cancel`, `cancel()` | six explicit termination/replacement/withdraw actions | Removed from current API/design |
| `contract.cancelled` | `remedy.finalized` + terminal lifecycle event | Removed; no consumer compatibility in final docs |
| `initiatedBy` used as fault | `requestedBy` + `finalBreachingRole` | Split and never infer fault |
| `SEIZE_PENALTY` | `DEPOSIT_FORFEITURE`, `CONTRACTUAL_PENALTY`, `DAMAGES_COMPENSATION` | Split legal/economic meanings |
| `REFUNDED_PARTIAL` | explicit refund/release legs + projection states | Removed |
| `CANCELLED`, `DELIVERED` contract states | canonical state set | Removed |
| `penaltyAndDamagesCumulative` | `damagesPolicy` | Removed boolean ambiguity |
| Lifecycle event triggers money/reputation | `remedy.finalized` sole canonical trigger | Consumers corrected |
| “Zero-Trust system” | zero-trust-oriented controls at named boundaries | Claim narrowed |
| “Production integration completed” | designed adapter/mock/known limitation | Claim removed |
