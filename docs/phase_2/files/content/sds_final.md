---
title: "AGRICONTRACT"
subtitle: "Software
author: "Tài liệu thiết kế chi tiết hợp nhất"
date: "Phiên bản final · Tháng 7/2026"
toc-title: "Mục lục"
---

# **0. Phạm vi, trạng thái và quy tắc diễn giải**

SDS này là đặc tả thiết kế mục tiêu Phase 2 ở trạng thái **implementation-ready**, không phải tuyên bố rằng mọi integration đã chạy production.

Quy tắc diễn giải: service owner là nơi phát quyết định; consumer không tự suy diễn nghĩa mới; `requestedBy` không đồng nghĩa `finalBreachingRole`; `breach.reported` chỉ là allegation; `remedy.finalized` là nguồn canonical duy nhất cho money/reputation consequences; lifecycle event chỉ được phát sau terminal reconciliation.

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
| escrow-service | Projection escrow và chuyển remedy legs thành bank commands | contract.signed, remedy.finalized, milestone outcomes, bank results | Không sở hữu sổ tiền authoritative | Owner-only mutation |
| bank-service | Append-only monetary ledger, lock/release/refund/seize, security lock | bank.*_requested; structuring signal; external verifier | Sở hữu sổ tiền authoritative | Money authoritative only |
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
| subject | Nullable/tuỳ trường hợp | subject | Optional audit/business subject. Include only when authoritative|
| payload | Bắt buộc | object |  |

Quy tắc: producer/eventType phải khớp schema; `causationId` trỏ request/event trực tiếp; `correlationId` giữ xuyên saga; result event có eventId mới; bank request `payload.sourceEventId` bằng request envelope eventId; replay DLQ giữ original eventId.

## **3.3 Enum ownership và wire vocabulary**

Enum được sở hữu bởi service phát quyết định; consumer chỉ validate giá trị đã công bố và không tự thêm nghĩa. Contract, milestone, funding, termination, remedy, money-leg và notification enum phải dùng đúng tập giá trị hiện hành trong các bảng contract của tài liệu này. Mọi trạng thái terminal, money leg và consequence đều phải đi qua boundary quyết định tương ứng; không dùng một enum quan sát để suy ra side effect mới.

## **3.4 Common data/security constraints**

UUID là identity xuyên service; tiền dùng decimal dương, không float. Timestamp UTC ISO-8601. PII không đi vào public DTO/event ngoài nhu cầu. Secret/key không nằm trong image hoặc mã triển khai. Database grants enforce append-only cho ledger/audit/lock facts.

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

# **6. bank-service — authoritative monetary ledger**

## **6.1 Ledger model**

Entry types: `LOCK_BUYER_DEPOSIT`, `LOCK_SELLER_DEPOSIT`, `LOCK_MILESTONE`, `RELEASE_TO_SELLER`, `REFUND_TO_BUYER`, `DEPOSIT_FORFEITURE`, `CONTRACTUAL_PENALTY`, `DAMAGES_COMPENSATION`. Fund types: `BUYER_DEPOSIT`, `SELLER_DEPOSIT`, `MILESTONE_PAYMENT`.

Mỗi entry lưu `sourceEventId`, `contractId`, optional `milestoneId`, `userId`, `entryType`, `fundType`, nullable `remedyDecisionId`, nullable `remedyLegId`, positive amount, timestamp. Tầng lưu trữ và DB user chỉ INSERT/SELECT; không UPDATE/DELETE.

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

`PriceQuote` theo commodity/region/source/time. VNSAT scrape COFFEE/RICE; RUBBER/CASHEW admin/manual theo source

# **9. User, notification và analytics**

## **9.1 user-service**

Roles BUYER/SELLER/ADMIN/INSPECTOR/OPERATOR; KYC PENDING/VERIFIED/REJECTED. Public DTO loại PII; internal API cho eligibility. Sign/listing/offer fail-closed. Reputation lock projection dùng persisted monotonic `last_lock_revision`; overlapping locks tính max effective lock và không unlock nhầm.

## **9.2 notification-service**

RabbitMQ command là ingress mặc định; synchronous `/internal/v1/notifications/otp-email` chỉ OTP. `NotificationLog PENDING/SENT/FAILED`, EMAIL channel, attachment evidence qua file-service READY. Dedup `(event_id, recipient_email, notification_type)`. Business service quyết recipient/purpose; notification không quyết định nghiệp vụ.

## **9.3 analytics-service**

CQRS read models ingest idempotent. `fact_contract_termination` tách `terminationType`, `requestedBy`, `finalBreachingRole`, `breachReasonCode`; mutual/no-fault không vào default breach rate. Monthly job recompute/overwrite mọi touched bucket. Analytics outage không ảnh hưởng settle; catch-up queue backlog, không claim cold rebuild. `AmlPatternScanJob` phát `analytics.structuring_pattern_detected`; reputation risk projection không hold tiền.

# **10. Frozen API, schemas và event contracts**

## **10.1 OpenAPI path catalog (41 paths)**

| Method | Path | Owner | operationId | Request, response and errors | Auth/role |
|---|---|---|---|---|---|
| POST | /api/v1/contracts | contract-service | createContract | Request `CreateContractRequest`; success/error: 201:ContractResponse, 400:—, 403:—, 503:— | BUYER |
| GET | /api/v1/contracts | contract-service | listContracts | Request `—`; success/error: 200:ContractPage | JWT |
| GET | /api/v1/contracts/{contractId} | contract-service | getContract | Request `—`; success/error: 200:ContractResponse, 403:—, 404:— | JWT |
| PATCH | /api/v1/contracts/{contractId}/terms | contract-service | updateContractTerms | Request `ContractTermsRequest`; success/error: 200:ContractResponse, 409:— | BUYER, SELLER |
| POST | /api/v1/contracts/{contractId}/sign/initiate | contract-service | initiateSign | Request `InitiateSignRequest`; success/error: 200:InitiateSignResponse, 401:—, 403:—, 409:—, 5XX:— | BUYER, SELLER |
| POST | /api/v1/contracts/{contractId}/sign/verify | contract-service | verifyOtpAndSign | Request `VerifyOtpAndSignRequest`; success/error: 204:—, 401:—, 403:—, 409:— | BUYER, SELLER |
| POST | /api/v1/contracts/{contractId}/withdraw | contract-service | withdrawOffer | Request `—`; success/error: 204:—, 400:—, 401:—, 403:—, 404:—, 409:— | BUYER, SELLER |
| POST | /api/v1/contracts/{contractId}/termination/mutual/propose | contract-service | proposeMutualTermination | Request `—`; success/error: 204:—, 400:—, 401:—, 403:—, 404:—, 409:— | BUYER, SELLER |
| POST | /api/v1/contracts/{contractId}/termination/mutual/confirm | contract-service | confirmMutualTermination | Request `—`; success/error: 204:—, 400:—, 401:—, 403:—, 404:—, 409:— | BUYER, SELLER |
| POST | /api/v1/contracts/{contractId}/replacement/propose | contract-service | proposeMutualReplacement | Request `ReplacementProposalRequest`; success/error: 201:ContractResponse, 400:—, 401:—, 403:—, 404:—, 409:— | BUYER, SELLER |
| POST | /api/v1/contracts/{contractId}/replacement/confirm | contract-service | confirmMutualReplacement | Request `—`; success/error: 204:—, 400:—, 401:—, 403:—, 404:—, 409:— | BUYER, SELLER |
| POST | /api/v1/contracts/{contractId}/breach-cases | contract-service | reportBreachCase | Request `BreachCaseReportRequest`; success/error: 201:BreachCase, 400:—, 401:—, 403:—, 404:—, 409:— | BUYER, SELLER, SYSTEM |
| POST | /api/v1/contracts/{contractId}/breach-cases/{breachCaseId}/review | contract-service | reviewBreachCase | Request `—`; success/error: 204:—, 400:—, 401:—, 403:—, 404:—, 409:— | ADMIN, INSPECTOR |
| POST | /api/v1/contracts/{contractId}/breach-cases/{breachCaseId}/resolve | contract-service | resolveBreachCase | Request `BreachCaseResolutionRequest`; success/error: 204:—, 400:—, 401:—, 403:—, 404:—, 409:— | ADMIN, INSPECTOR |
| POST | /api/v1/contracts/{contractId}/termination/execute | contract-service | executeTermination | Request `TerminationExecuteRequest`; success/error: 204:—, 400:—, 401:—, 403:—, 404:—, 409:— | BUYER, SELLER, ADMIN |
| POST | /api/v1/contracts/{contractId}/activation/retry-deposit-lock | contract-service | retryDepositLock | Request `—`; success/error: 204:—, 403:—, 409:— | ADMIN |
| POST | /api/v1/contracts/{contractId}/activation/mark-failed | contract-service | markActivationFailed | Request `—`; success/error: 204:—, 403:—, 409:— | ADMIN |
| POST | /api/v1/contracts/{contractId}/milestones/{milestoneId}/weigh | contract-service | recordSellerWeighed | Request `SellerWeighedRequest`; success/error: 204:—, 409:— | SELLER |
| POST | /api/v1/contracts/{contractId}/milestones/{milestoneId}/confirm | contract-service | confirmMilestoneClean | Request `BuyerReceivedRequest`; success/error: 204:—, 409:— | BUYER |
| POST | /api/v1/contracts/{contractId}/milestones/{milestoneId}/flag | contract-service | flagMilestoneIssue | Request `BuyerReceivedRequest`; success/error: 204:—, 409:— | BUYER |
| POST | /api/v1/contracts/{contractId}/milestones/{milestoneId}/respond | contract-service | respondToMilestoneFlag | Request `—`; success/error: 204:—, 409:— | SELLER |
| POST | /api/v1/contracts/{contractId}/milestones/{milestoneId}/force-majeure | contract-service | claimForceMajeure | Request `ForceMajeureClaimRequest`; success/error: 204:—, 409:— | SELLER |
| POST | /api/v1/contracts/{contractId}/milestones/{milestoneId}/force-majeure/resolve | contract-service | resolveForceMajeure | Request `ForceMajeureResolveRequest`; success/error: 204:—, 403:—, 409:— | ADMIN |
| POST | /api/v1/contracts/{contractId}/milestones/{milestoneId}/funding/retry | escrow-service | retryMilestoneFunding | Request `—`; success/error: 204:—, 403:—, 409:— | ADMIN |
| POST | /api/v1/users/register | user-service | registerUser | Request `—`; success/error: 2XX:—, 400:— | JWT |
| GET | /api/v1/users/me | user-service | getMyProfile | Request `—`; success/error: 2XX:—, 401:— | JWT |
| GET | /api/v1/users/{userId} | user-service | getPublicUser | Request `—`; success/error: 2XX:PublicUserResponse | JWT |
| GET | /api/v1/reputation/{userId}/public-summary | reputation-service | getPublicReputationSummary | Request `—`; success/error: 200:ReputationPublicSummary, 404:— | JWT |
| POST | /api/v1/admin/reputation/actions/propose | reputation-service | proposeReputationAction | Request `ReputationActionProposalRequest`; success/error: 201:ReputationAction, 403:—, 409:— | ADMIN, OPERATOR |
| POST | /api/v1/admin/reputation/actions/{actionId}/approve | reputation-service | approveReputationAction | Request `—`; success/error: 204:—, 403:—, 409:— | ADMIN |
| POST | /api/v1/admin/reputation/actions/{actionId}/reject | reputation-service | rejectReputationAction | Request `—`; success/error: 204:—, 403:—, 409:— | ADMIN |
| GET | /api/v1/admin/users | user-service | listPendingUsers | Request `—`; success/error: 2XX:—, 403:— | JWT |
| POST | /api/v1/admin/users/{userId}/verify | user-service | verifyUser | Request `VerifyUserRequest`; success/error: 2XX:—, 403:— | JWT |
| POST | /api/v1/admin/users/{userId}/reject | user-service | rejectUser | Request `RejectUserRequest`; success/error: 2XX:—, 403:— | JWT |
| GET | /internal/v1/users/{userId} | user-service | getInternalUserInfo | Request `—`; success/error: 2XX:—, 401:— | serviceAuth |
| POST | /internal/v1/notifications/otp-email | notification-service | sendOtpEmail | Request `OtpEmailRequest`; success/error: 200:—, 5XX:— | serviceAuth |
| GET | /internal/v1/audit/records | audit-service | getAuditRecordsForReconciliation | Request `—`; success/error: 200:AuditRecordList, 401:— | serviceAuth |
| GET | /api/v1/security/audit-hash | audit-service | getAuditHash | Request `—`; success/error: 200:AuditHashResponse, 401:— | X-Api-Key |
| POST | /api/v1/security/emergency-lock | bank-service | emergencyLock | Request `ExternalVerifierRequest`; success/error: 204:—, 401:—, 409:— | External Verifier (signed request) |
| POST | /api/v1/security/emergency-unlock | bank-service | emergencyUnlock | Request `ExternalVerifierRequest`; success/error: 204:—, 401:—, 409:— | External Verifier (signed request) |
| GET | /api/v1/escrows/statements | escrow-service | exportEscrowStatement | Request `—`; success/error: 200:—, 403:— | JWT |
| GET | /internal/v1/bank/ledger | bank-service | getInternalLedgerEntries | Request `—`; success/error: 200:LedgerEntryList, 401:— | serviceAuth |

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
| contract.terminated | contract-service | audit-service, analytics-service | `contractId`: uuid<br>`terminationType`: `WITHDRAW_OFFER`, `MUTUAL_TERMINATION`, `MUTUAL_REPLACEMENT`, `TERMINATION_FOR_BREACH`, `TERMINATION_FOR_FORCE_MAJEURE`, `ACTIVATION_FAILURE`<br>`requestedBy`: `BUYER`, `SELLER`, `SYSTEM`<br>`finalBreachingRole`: breachingRole<br>`breachReasonCode`: nullableBreachReasonCode<br>`remedyDecisionId`: nullableUuid<br>`breachCaseId`: nullableUuid<br>`affectedMilestoneIds`: array<uuid><br>`supersededByContractId`: nullableUuid<br>`replacesContractId`: nullableUuid | `finalBreachingRole` và `breachReasonCode` là required-but-nullable cho mutual, force-majeure và technical no-fault; replacement pointers chỉ non-null với `MUTUAL_REPLACEMENT`. Trừ pre-sign `WITHDRAW_OFFER`, chỉ publish sau khi đủ expected `remedyDecisionId/remedyLegId` và remaining contract lock bằng 0. |
| breach.reported | contract-service | audit-service | `breachCaseId`: uuid<br>`contractId`: uuid<br>`milestoneId`: nullableUuid<br>`requestedBy`: `BUYER`, `SELLER`, `SYSTEM`<br>`allegedBreachingRole`: `BUYER`, `SELLER` / null<br>`breachReasonCode`: breachReasonCode<br>`severity`: `MINOR`, `MATERIAL` |  |
| remedy.finalized | contract-service | escrow-service, reputation-service, audit-service | `remedyDecisionId`: uuid<br>`attributionDecisionId`: uuid<br>`breachCaseId`: nullableUuid<br>`contractId`: uuid<br>`buyerId`: uuid<br>`sellerId`: uuid<br>`affectedMilestoneIds`: array<uuid><br>`finalBreachingRole`: breachingRole<br>`breachReasonCode`: nullableBreachReasonCode<br>`decisionSource`: `SYSTEM`, `ADMIN`, `INSPECTOR_L1_5`, `INSPECTOR_L2`, `MUTUAL`<br>`penaltyEligible`: boolean<br>`reputationEligible`: boolean<br>`remedyLegs`: array<object> | Khi `finalBreachingRole = null`, cả hai eligibility flag phải false và legs chỉ có `NONE`, `PAYMENT_SETTLEMENT`, `PAYMENT_REFUND`; khi `penaltyEligible = false`, không có `CONTRACTUAL_PENALTY`. `remedyLegId` unique cho từng bank leg; `remedyDecisionId` group toàn bộ legs và tạo tối đa một reputation lock. |
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

| Command | Producer | Consumer | Required payload; optional payload |
|---|---|---|---|
| notification.contract_anchor_requested | contract-service | notification-service | `contractId`, `recipients`, `contractTermsSnapshot`, `signedContentHash`, `signedAt` |
| notification.contract_activation_failed_requested | contract-service | notification-service | `contractId`, `recipients`, `failedLeg`, `buyerDepositState`, `sellerDepositState`, `nextActions` |
| notification.contract_terminated_requested | contract-service | notification-service | `contractId`, `recipients`, `terminationType`, `requestedBy`, `finalBreachingRole`, `breachReasonCode`, `remedyDecisionId`, `breachCaseId`, `affectedMilestoneIds`, `supersededByContractId`, `replacesContractId`, `remedyLegs` |
| notification.breach_notice_requested | contract-service | notification-service | `breachCaseId`, `contractId`, `recipients`, `breachReasonCode`, `severity`, `evidenceRefs` |
| notification.remedy_finalized_requested | contract-service | notification-service | `remedyDecisionId`, `attributionDecisionId`, `breachCaseId`, `contractId`, `buyerId`, `sellerId`, `affectedMilestoneIds`, `recipients`, `finalBreachingRole`, `breachReasonCode`, `decisionSource`, `penaltyEligible`, `reputationEligible`, `remedyLegs` |
| notification.milestone_funding_status_requested | contract-service | notification-service | Required: `contractId`, `milestoneId`, `recipients`, `statusType`; optional nullable: `deadline`, `reason` |
| notification.milestone_status_requested | contract-service | notification-service | Required: `contractId`, `milestoneId`, `recipients`, `statusType`; optional: `deadline` (nullable), `statusData` |
| notification.milestone_anchor_requested | audit-service | notification-service | `contractId`, `milestoneId`, `recipients`, `recordHash`, `otsProof`, `settlementSummary` |
| notification.level2_commission_requested | inspection-service | notification-service | Required: `commissionId`, `contractId`, `recipients`, `org`, `intakeAddress`; optional: `contractContext` |
| notification.audit_digest_requested | audit-service | notification-service | `recipients`, `recordHash` |
| notification.audit_failure_requested | audit-service | notification-service | `recipients`, `failureSummary` |
| notification.security_lock_changed_requested | bank-service | notification-service | `recipients`, `action`, `reason`, `timestamp` |
| notification.reconciliation_mismatch_requested | bank-service | notification-service | `contractId`, `recipients`, `mismatchSummary` |
| notification.user_kyc_result_requested | user-service | notification-service | Required: `userId`, `recipientEmail`, `organizationName`, `result`, `decidedAt`; optional: `reason` |
| notification.user_lock_changed_requested | user-service | notification-service | Required: `userId`, `recipientEmail`, `action`, `reasonCode`; optional nullable: `effectiveUntil` |
| notification.verifier_key_anchor_requested | bank-service | notification-service | `recipientEmail`, `publicKeyFingerprint` |

Mọi notification command dùng envelope required `eventId`, `eventType`, `eventVersion`, `occurredAt`, `producer`, `aggregateId`, `correlationId`, `payload`; `causationId` optional nullable. Trong payload, các field required-but-nullable vẫn phải xuất hiện: termination command giữ nullable `finalBreachingRole`, `breachReasonCode`, `remedyDecisionId`, `breachCaseId`, `supersededByContractId`, `replacesContractId`; remedy command giữ nullable `breachCaseId`, `finalBreachingRole`, `breachReasonCode`. `additionalProperties = false` áp cho envelope và payload.

# **11. Verification Matrix và release gate**

Mỗi invariant P0/P1/P2 phải có test theo đúng boundary. P0 money/immutability/evidence là gate trước golden-flow merge; P1 gate trước external exposure; P2 gate trước pilot claim. Matrix hiện có 55 rows.

| # | Priority | Invariant | Acceptance test |
|---|---|---|---|
| 1 | P0 | Event lặp không release/lock tiền hai lần | Publish cùng bank request nhiều lần với `payload.sourceEventId == envelope.eventId` → đúng 1 bộ ledger entry; result dùng eventId mới và causationId=request eventId |
| 2 | P0 | Ledger append-only — không UPDATE/DELETE | Tầng lưu trữ chỉ có insert/select; test lớp lưu trữ + quyền DB user |
| 3 | P0 | Hợp đồng đã `SIGNED` không nhận update terms | Gọi update terms sau ký → `409`; hash không đổi |
| 4 | P0 | `audit_record` bị sửa phải bị phát hiện | Tamper trực tiếp DB (đổi amount/hash) → `VerifyChainJob` fail + đúng vị trí |
| 5 | P0 | Ledger lệch với record đã anchor phải bị phát hiện | Sửa 1 ledger leg lệch typed audit projection (`contractId`, `milestoneId`, `settledAmount`, `seizedAmount`, release/refund legs) → `LedgerAuditReconciliationJob` bắn `reconciliation_mismatch` |
| 6 | P0 | Bank completion callback lặp không nhân đôi | Gửi duplicate callback → 1 bộ entry, trạng thái không đổi lần 2 |
| 7 | P0 | DLQ replay không side effect lặp | Replay `eventId` gốc sau khi đã xử lý → consumer skip, không entry mới |
| 8 | P0 | Contract terminal không còn tiền lock treo | Normal completion/termination phát `remedy.finalized`, mock một bank leg còn missing/failed → contract vẫn non-terminal và chưa publish lifecycle event; khi đủ expected `remedyLegId` + remaining lock = 0 mới cho `contract.settled`/`contract.terminated`/`SUPERSEDED`/`ACTIVATION_FAILED` (pre-sign `WITHDRAWN` zero by construction) |
| 9 | P0 | Kill switch chặn toàn bộ dòng tiền | ES256/RFC8785 request: wrong key/signature/timestamp/action/replayed nonce bị exact 401/409 code; `system_lock ACTIVE` làm mọi `bank.*_requested` fail; Gateway không retry và cap 64 KB |
| 10 | P0 | Provisional Level 2 không release quá mức sàn và bảo toàn tiền | Buyer im lặng → `sellerReleaseAmount` dùng release floor, không buyer refund; mọi reconcile/terminal leg explicit, tổng release+refund+remaining lock bảo toàn batchAmount, zero command bị omit |
| 11 | P0 | Khoá cọc fail không kẹt im lặng | Bank fail liên tục → hết 3 retry → `escrow.deposit_lock_failed` + contract giữ `SIGNED` + notification 3 bên; `RetryDepositLock` sau khi hết lỗi → `ACTIVE` |
| 11b | P0 | Partial lock không nuốt tiền: buyer LOCKED + seller fail → terminal phải refund | Mock seller-leg fail vĩnh viễn → `MarkActivationFailed` → `bank.refund_requested` cho leg buyer, qua `ACTIVATION_REFUND_PENDING`, chỉ `ACTIVATION_FAILED` khi refund confirmed; refund fail → đứng lại + alert |
| 11c | P0 | 2 chữ ký phải cùng bản terms | Buyer ký, đổi terms (phải bị 409); ép seller ký hash khác → REJECT, không SIGNED |
| 11d | P0 | Mọi field record đều được hash commit | Tamper riêng `source_type`/`subject_id`/`prev_hash_subject` (giữ nguyên content) → `VerifyChainJob` recompute fail |
| 11e | P0 | `audit_anchor` cũng bất biến | Tầng lưu trữ/DB user chỉ INSERT+SELECT trên audit_anchor; UPDATE/DELETE → denied |
| 11f | P0 | Source/result hash commitment verify riêng với record hash | `Contract.signedContentHash` 3-way khớp; inspection recompute `resultHash` từ RFC8785 normalized result và `reportHash` từ report identity/file/result/timestamp/actor; tamper bất kỳ field nào bị reject; `record_hash` vẫn verify riêng |
| 11g | P0 | Allegation chưa final không đụng tiền/reputation | Mở `BreachCase` (`breach.reported`) rồi thử phát seize/forfeiture/penalty + kiểm tra reputation consumer → mọi lệnh tiền bị chặn khi `status != RESOLVED`; reputation không có `lock_entry` mới |
| 11h | P0 | Người request termination không tự động bị coi là vi phạm | Seller yêu cầu terminate vì buyer `FUNDING_FAILURE` → `finalBreachingRole = BUYER`, seller không bị seize/lock; mirror chiều ngược |
| 11i | P0 | `finalBreachingRole = NULL` → không ai bị phạt | `MUTUAL_TERMINATION`/`MUTUAL_REPLACEMENT`/FM/`ACTIVATION_FAILURE` → cọc về đúng chủ (`REFUND_TO_BUYER`/`RELEASE_TO_SELLER`), zero `DEPOSIT_FORFEITURE`/`CONTRACTUAL_PENALTY`, zero `lock_entry` |
| 11j | P0 | Contractual penalty không vượt cap LegalProfile | `sign()` với `buyerPenaltyRate`/`sellerPenaltyRate` > `maxContractualPenaltyRate` (8% VN_COMMERCIAL_LAW) → reject 400; penalty tính trên batchAmount phần bị vi phạm, không trên totalAmount |
| 11k | P0 | Không double recovery | Remedy calculator: với `damagesPolicy = CIVIL_PENALTY_ONLY_UNLESS_EXPRESSLY_CUMULATIVE`/`EXPRESS_PENALTY_ONLY` → không có `DAMAGES_COMPENSATION` leg; với `COMMERCIAL_CUMULATIVE_IF_PROVEN` → DAMAGES chỉ khi có bằng chứng, và `DEPOSIT_FORFEITURE` offset vào penalty; ledger không có 2 khoản seize bù cùng 1 tổn thất |
| 11l | P0 | Mutual replacement nối đúng 2 contract, không double-count | Supersede → contract cũ `SUPERSEDED` + `supersededByContractId`; contract mới `replacesContractId`; milestone `SETTLED` cũ giữ nguyên; tiền lock cũ refund hết (tổng lock contract cũ = 0), contract mới lock từ 0 |
| 11m | P0 | Milestone funding failure không kẹt tiền + không phạt oan seller | Mock bank fail lock batch milestone 2 → hết retry → `FUNDING_FAILED`, đồng hồ giao hàng seller tạm dừng (không trigger seller-quá-hạn), leg đã lock refund; hết `fundingCureWindowDays` → buyer breach Rổ A, seller không bị seize |
| 11n | P0 | Termination pro-rata không đụng milestone đã `SETTLED` | `TERMINATION_FOR_BREACH` giữa hợp đồng (milestone 1-2 `SETTLED`, 3-5 chưa) → penalty/forfeiture chỉ tính trên batch 3-5; ledger milestone 1-2 không có entry mới, tiền đã release không bị truy thu |
| 11o | P0 | Một `remedyDecisionId` → đúng 1 bộ bank legs, ≤ 1 reputation lock (chặn double-consume) | Replay `remedy.finalized` / gửi leg trùng → `ledger_entry.remedy_leg_id` UNIQUE chặn từng leg (đơn vị dedup đúng — 1 decision nhiều legs), reconcile `SUM` group theo `remedy_decision_id` khớp đúng 1 bộ; reputation `lock_entry.remedy_decision_id` UNIQUE chặn lock thứ 2 kể cả từ event khác |
| 11p | P0 | Supersede crash windows không nuốt tiền/không kẹt hợp đồng | (a) draft replacement không đủ 2 chữ ký → cũ vẫn `ACTIVE`, zero sự kiện tiền; (b) mới `SIGNED`, refund cũ fail giữa chừng → cũ đứng `SUPERSEDE_REFUND_PENDING` + alert, không nhảy `SUPERSEDED`; (c) cũ `SUPERSEDED` xong, activation mới fail → mới đi đường `ACTIVATION_FAILED` chuẩn, cũ không rollback |
| 11q | P0 | Replay evidence event không nhân đôi audit record/anchor | Redeliver cùng `remedy.finalized`/`contract.terminated` (eventId cũ) → `audit_record.source_event_id` UNIQUE reject/no-op, không record mới, không anchor mới, không email evidence thứ 2 |
| 12 | P1 | User khác không thao tác contract người khác | Ownership check → `403` (contract, escrow, statement) |
| 13 | P1 | Client không tự tiêm identity | Gửi kèm `X-User-Id`/`X-User-Role`/`X-Gateway-Secret` giả → bị strip/overwrite, downstream nhận identity từ JWT |
| 14 | P1 | `/internal/**` không ra ngoài | Gọi từ external → `404/403` tại Gateway, không forward |
| 15 | P1 | File nhiễm virus không thành evidence | Upload EICAR qua `StoreOnBehalfOf` và qua email intake → `FAILED`, không có `file.ready`, không gắn được vào milestone/report |
| 16 | P1 | OPERATOR không chạm được đường ADMIN | OPERATOR gọi approve maker-checker / `RetryDepositLock` / `/admin/audit` → `403` |
| 17 | P1 | ADMIN không tự approve đề xuất của chính mình | Cùng 1 account propose rồi approve → reject |
| 18 | P1 | Unlock sớm không mở khoá nhầm khi nhiều lock chồng | 2 lock (T+60, T+30), unlock sớm lock 1 → `lockedUntil` user-service = T+30, `sign()` vẫn bị chặn |
| 19 | P1 | `sign()` fail-closed khi user-service chết | Stop user-service → sign bị reject, không fallback anonymous |
| 20 | P1 | OTP fail không gửi muộn | Provider trả lỗi → contract-service nhận 5xx; xác nhận không có mail nào được gửi sau đó với `requestId` cũ trừ khi caller tự retry |
| 21 | P1 | Public DTO không lộ PII | `GET /users/{id}` → không email/phone/address; public-summary yêu cầu JWT |
| 21b | P1 | OTP challenge binding | Verify với JWT user khác / otpId khác contract / terms đã đổi sau phát OTP → 403/409/invalidate; không lấy được "OTP mới nhất" của người khác |
| 21c | P1 | Lock ordering sống qua restart | Apply unlock revision N, restart service, deliver locked revision N-1 (eventId mới) → bị bỏ qua nhờ `last_lock_revision` persist |
| 21d | P1 | `legalHold` chặn xoá tuyệt đối | Set legalHold + retention đã hết → lifecycle job gọi `DeleteFile` → REJECT, file còn nguyên |
| 21e | P1 | Xoá file 2 bước tự lành | Mock MinIO delete fail sau tombstone → job re-run → cả DB lẫn blob về trạng thái nhất quán, không rác |
| 21f | P1 | Listing/offer fail-closed khi user-service chết | Stop user-service → CreateListing/CreateOffer trả 503, user đang khoá không lách được |
| 22 | P2 | Cà phê/cao su bắt buộc plot; gạo/điều không | Commodity-gate test 2 chiều: tạo listing thiếu plot → fail/pass đúng theo commodity |
| 23 | P2 | Analytics chết không ảnh hưởng giao dịch | Stop analytics-service → settle milestone vẫn hoàn tất; analytics **catch-up từ queue backlog** khi sống lại (outage ngắn — KHÔNG phải cold rebuild, sửa 18/07/2026) |
| 24 | P2 | Notification dedup theo recipient + type | 1 event 2 recipients + retry → mỗi (recipient, type) đúng 1 mail `SENT` |
| 25 | P2 | Restore đụng tiền phải qua verify mới mở | Restore `bank_db` test env → 2 job verify chạy pass trước khi mở traffic (demo DR) |
| 26 | P2 | Event cũ không ghi đè quyết định mới | Deliver `reputation.locked/unlocked` sai thứ tự (`lockRevision` nhỏ hơn — sửa 18/07) → bị bỏ qua |
| 26b | P2 | Plot overlap chéo seller bị bắt | Seller B đăng ký polygon trùng/lọt trong polygon Seller A → `plotReuseRisk = HIGH` + OPERATOR review, không auto-reject |
| 26c | P2 | Yield anomaly là signal, không phải gate | `declaredQuantity` vượt Σ(plotArea×maxYield) → `yieldRisk` + yêu cầu bổ sung/review; listing KHÔNG bị auto-reject |
| 26d | P2 | Deployment policy enforce được | CI check: image pin theo digest, secret không nằm trong image, protected branch bắt buộc approval — fail build nếu vi phạm (policy test, không phải runtime test) |
| 26e | P2 | Delta 1 vượt threshold không auto-gán bẻ kèo | Shortfall > threshold, không FM claim → milestone mở `BreachCase` (không bắn `milestone.cancelled_with_penalty` tự động); pro-rata phần đã giao vẫn release |
| 26f | P2 | Reputation chỉ phạt strategic breach | `remedy.finalized` với `breachReasonCode = PRODUCTION_SHOCK_NON_FM` → không `lock_entry`; với `SIDE_SELLING` → có; `zeroProgressMultiplier` 1.5x chỉ khi strategic + 0 settled |
| 26g | P2 | Analytics đo `finalBreachingRole`, không đo người bấm nút | `contract.terminated` các nhánh → `fact_contract_termination` tách `terminationType`/`requestedBy`/`finalBreachingRole`; mutual không đếm vào default rate |
| 26h | P2 | Allegation bị bác/được miễn trách không phát hậu quả tiêu cực (đổi tên 19/07 lần 2 — Phase 2 cố tình chưa có cure state, không gọi "cure thành công") | `BreachCase` RESOLVED với `finalBreachingRole = NULL` → không termination, không lock_entry cho bên bị cáo buộc; `remedy.finalized` nếu phát chỉ chứa refund/release legs |
| 26i | P2 | Inspection đang pending chặn auto-confirm | Milestone `CONTESTED`/có commission Level 1.5-2 đang chạy → hết `buyerConfirmWindowDays` KHÔNG auto `CONFIRM_CLEAN`, không release; timer chỉ áp khi milestone còn đúng state `BUYER_RECEIVED` |

## **11.1 Cross-contract release checks**

- Producer, consumer, eventType, payload required/nullability và canonical side effect khớp YAML.
- State/action/API không dùng current value/endpoint bị retire.
- Money/reputation chỉ từ final decision; no double-consume/seize; cọc về đúng owner.
- Terminal zero-lock và expected legs complete.
- API write idempotency + DB unique/append-only constraint đủ chứng minh invariant.
-

# **12. Current Scope, migration và implementation plan**

Current Scope của SDS là đặc tả triển khai cho 12 business services và `api-gateway`: common envelope, ownership, state machine, API/schema, domain/bank event, notification command, persistence invariant, idempotency, security boundary, verification gate và workstream migration. Các bảng contract mô tả giá trị/field hiện hành; chúng không khẳng định mọi adapter bên ngoài đã chạy production.

## **12.1 Phase 1 retirement/migration**

Migration from an earlier implementation must proceed additively: create LegalProfile snapshots, split termination facts into requested intent and final attribution, add remedy/leg identifiers and uniqueness constraints, persist lock revisions, formalize file-ingest state, record audit source identifiers, and expose the analytics termination dimensions. Historical records that lack evidence remain legacy/unknown; migration must not infer a breaching party from the requester.

Compatibility triển khai theo thứ tự schema additive → producer dual/read compatibility nếu cần → consumer canonical → cutover endpoint/event → remove legacy. Không backfill synthetic attribution sai; record lịch sử không đủ evidence phải đánh dấu legacy/unknown, không suy người request là breacher.

## **12.2 Workstreams/T0–T13**

Implementation plan chia theo golden-flow slices và service workstreams, không “một người một service” cố định. Luồng chính contract–escrow–bank–audit được tích hợp sớm; product/file/pricing, inspection/reputation và user/notification/analytics chạy song song với contract tests. Test/docs là trách nhiệm ngang đội và double-check ở boundary producer–consumer/money/security.

Các mốc T0–T13 phải bao phủ common contracts và event envelope; contract/signature/attribution; milestone/escrow/bank; inspection/evidence; reputation; product/file/pricing; user/governance; notification/analytics; API contract tests; security/DR; pilot validation và release gate. Mỗi mốc phải có producer–consumer contract test, idempotency test và acceptance evidence ở đúng boundary.

# **13. Known Limitations**

- Bank/custodian thật, CA signature, Level 2 production intake, provider pricing và OpenTimestamps production chưa hoàn tất; các adapter hiện tại không phải bằng chứng capability đã vận hành production.
- Phân loại deposit/penalty/damages, licensing boundary, mẫu contract và dispute clause cần legal validation trước pilot có tiền thật; tách custody khỏi platform không tự chứng minh compliance. [28], [29], [45], [46]
- Zero-trust-oriented controls không tạo thành mô hình Zero Trust toàn diện; collusion hoặc hạ tầng bị compromise đồng thời vẫn là giới hạn.
- Full amendment, partial mutual termination, quality-indexed settlement, full cure workflow, automated insurance/credit và logistics tracking nằm ngoài Phase 2.
- Geo/EUDR, AML, yield và overlap là evidence/risk signals; traceability còn phụ thuộc chất lượng dữ liệu, chi phí và quy trình due diligence, nên không tự chứng minh compliance hay factual truth. [21], [22], [30]
- Analytics/notification có eventual consistency; catch-up backlog không thay cho cold rebuild.

# **14. Future Work**

- Productionize custodian, CA/WebAuthn, inspection/accreditation, email/price adapters và OTS anchoring.
- Bổ sung ContractVersion amendment, notice–cure–remedy đầy đủ, quality settlement theo chuẩn hàng hoá và payment/security package mở rộng.
- Hoàn thiện mTLS/KMS/HSM, separation of duties, deployment attestation, DR/scale và cross-border/ND13 controls.
- Tự động hoá giá cao su quốc tế sau khi chốt đơn vị/tỷ giá; tích hợp logistics/3PL khi có đối tác thật.

# **15. Danh mục nguồn tham khảo**

[21] A. Kashyap et al., “Traceability Adoption Barriers in Digital Food Supply Chain to Achieve Food Security and Sustainability,” Bus. Strategy Environ., vol. 35, no. 1, 2025, doi: 10.1002/bse.70177.

[22] V. Guye, P. Meyfroidt, and E. z. Ermgassen, “The Need for Improved Public Transparency in the Era of Due Diligence Regulations,” Regulation & Governance, 2026, doi: 10.1111/rego.70142.

[28] Chính phủ Việt Nam, Nghị định 52/2024/NĐ-CP, 2024.

[29] Chính phủ Việt Nam, Nghị định 340/2025/NĐ-CP, 2025.

[30] European Parliament and Council, Regulation (EU) 2023/1115, consolidated Dec. 26, 2025.

[45] Quốc hội Việt Nam, Bộ luật Dân sự số 91/2015/QH13, 2015.

[46] Quốc hội Việt Nam, Luật Thương mại số 36/2005/QH11, 2005.
