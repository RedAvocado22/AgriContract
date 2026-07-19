---
title: "AGRICONTRACT"
subtitle: "Kiến trúc kỹ thuật hệ thống Phase 2"
author: "Tài liệu đồ án tốt nghiệp"
date: "Phiên bản final · Tháng 7/2026"
toc-title: "Mục lục"
---

# **Tóm tắt điều hành**

Kiến trúc mục tiêu gồm **12 business services** và `api-gateway` ở lớp biên. Signature/OTP thuộc `contract-service`; hash chain/anchor thuộc `audit-service`; gateway không được tính như business service. Hệ thống dùng database-per-service, transactional outbox và at-least-once messaging, nhưng đặt guard mạnh hơn trên đường tiền: bank ledger là nguồn sự thật duy nhất, mỗi money leg có idempotency key riêng, và lifecycle terminal chỉ được commit sau ledger reconciliation + zero remaining lock.

Ranh giới quan trọng nhất là **decision trước consequence**. `requestedBy`, allegation và final attribution được tách. `remedy.finalized` là event canonical duy nhất kích hoạt money consequences và negative reputation. `contract.settled`/`contract.terminated` chỉ phản ánh lifecycle/audit/analytics; bank và reputation không được consume chúng để release/seize/lock.

Kiến trúc áp dụng các control zero-trust-oriented tại edge, internal route và External Verifier, nhưng không claim full Zero Trust. Bank thật, CA signature, external inspection production và một số DR/data integration vẫn ngoài phạm vi Phase 2 implementation.

# **1. Context, phạm vi và nguyên tắc**

## **1.1 Context hệ thống**

External actors gồm Buyer, Seller, Inspector, Admin, Operator, External Verifier, tổ chức giám định Level 2, nhà cung cấp email/OTP, nguồn giá và custodian/ngân hàng. Tất cả request người dùng đi qua Nginx/API Gateway; `/internal/**` chỉ cho service-to-service. External Verifier là ngoại lệ có endpoint ký ES256 trực tiếp tới bank-service qua route chuyên biệt, không JWT và không retry.

## **1.2 Nguyên tắc kiến trúc**

- Business capability ownership rõ và database-per-service; không cross-service DB access.
- Rich aggregate/state transition; không để controller/repository tự sửa state.
- Outbox cùng local transaction; consumer idempotent; DLQ replay giữ event ID gốc.
- Số tiền chỉ authoritative ở bank ledger. escrow/contract giữ projection, expected legs và state, không tạo balance thứ hai.
- Decision side effect có canonical boundary; lifecycle/notification/analytics không được trở thành đường phạt song song.
- Immutability áp cho ledger/audit/lock facts; quyền xoá áp qua retention/legal hold/tombstone ở dữ liệu phù hợp.
- Fail-closed cho identity/eligibility/signing/listing/offer; analytics/notification không được chặn đường giao dịch cốt lõi trừ OTP synchronous.

# **2. Phân rã lớp và trust boundary**

| **Lớp** | **Thành phần** | **Ranh giới** |
|---|---|---|
| Edge | Nginx, api-gateway | TLS termination, size/rate/CORS, JWT, strip/overwrite identity header, coarse role; không fine-grained ownership |
| Business core | contract, escrow, bank, inspection, reputation | State/decision/money/trust facts; transaction và invariant quan trọng |
| Data/support | user, product, file, pricing, analytics, notification, audit | Eligibility, evidence, reference data, projection, delivery, verification |
| Infrastructure | RabbitMQ, MySQL schemas, MinIO, email/OTP, scheduler | At-least-once, persistence, blob lifecycle, external adapters |

Gateway không route `/internal/**`; downstream không tin `X-User-*` do client cung cấp. Owner service luôn kiểm tra participant/ownership. External Verifier request bị cap 64 KB, canonicalized, verify key/timestamp/nonce/action và không có Admin bypass.

# **3. Service ownership**

| Service | Năng lực sở hữu | Storage | Tương tác chính | Boundary quan trọng |
|---|---|---|---|---|
| user-service | Identity, KYC, role, eligibility và lock projection | user_db | user KYC/lock commands; internal eligibility API | Không sở hữu reputation ledger |
| product-service | Plot registry, product/listing, commodity gate, geo/yield risk | product_db | user eligibility; file.ready | Plot là seller-owned; overlap là signal |
| contract-service | Contract, milestone, signature/OTP, attribution, remedy, termination | contract_db | REST commands; domain events; internal user/OTP | Owner duy nhất của business decision và lifecycle |
| escrow-service | Projection escrow và chuyển remedy legs thành bank commands | escrow_db | contract.signed, remedy.finalized, milestone outcomes, bank results | Không là source of truth số tiền |
| bank-service | Append-only monetary ledger, lock/release/refund/seize, security lock | bank_db | bank.*_requested; structuring signal; external verifier | Single source of truth cho tiền |
| inspection-service | Level 1.5/2 commission, result/report hash, confirmation | inspection_db | contract/milestone commands, file.ready | Không ra phán quyết pháp lý |
| reputation-service | Immutable completion/dispute/lock facts và derived score | reputation_db | remedy.finalized, settled facts, risk signals | Negative consequence chỉ từ remedy.finalized |
| audit-service | Append-only dual hash chain, anchor, verify, evidence query | audit_db | evidence/security/domain events | Không sửa source facts; dedup source_event_id |
| file-service | Three trust-boundary ingests, malware scan, retention/legal hold | file_db + MinIO | upload/email/system-generated; file events | READY gate trước khi thành evidence |
| pricing-service | PriceQuote, VNSAT/manual ingestion và read API | pricing_db | scheduled/admin ingestion | Không là settlement engine |
| analytics-service | CQRS read models, monthly recompute, AML structuring signals | analytics_db | domain/ledger facts | Non-critical; catch-up queue backlog only |
| notification-service | Template delivery, evidence attachment, OTP sync | notification_db | notification commands; internal OTP API | Không consume business event để tạo sanction |

## **3.1 Ownership của decision và lifecycle**

`contract-service` sở hữu `BreachCase`, `AttributionDecision`, `RemedyDecision`, contract/milestone state và phát `remedy.finalized`. escrow-service chỉ dịch từng remedy leg thành bank command; bank-service chỉ thực hiện ledger effect. reputation-service chỉ tạo lock khi payload final cho phép. Cách phân rã này ngăn việc allegation, request initiator hoặc lifecycle event bị biến thành money/reputation consequence.

## **3.2 Ownership của signature và evidence**

Signature/OTP challenge là domain con của contract-service vì phải binding với exact terms hash và state. audit-service sở hữu audit record/hash/anchor; file-service sở hữu blob lifecycle; inspection-service sở hữu result/report commitment. Các hash khác nhau có mục tiêu khác nhau và không được trộn thành một “hash chung”.

# **4. Integration model và consistency**

## **4.1 Đồng bộ**

REST chỉ dùng khi caller cần kết quả ngay: command từ UI, internal eligibility, OTP delivery, audit/ledger query phục vụ reconciliation. Mọi write endpoint yêu cầu `Idempotency-Key` theo OpenAPI; identity từ JWT/service auth, không từ body/header client.

## **4.2 Bất đồng bộ**

RabbitMQ mang domain event, bank command/result và notification command. Event envelope chuẩn có event identity, version, producer, aggregate, timestamp, correlation/causation và payload. Publisher ghi business state + outbox cùng transaction. Consumer lưu processed event/source event trước hoặc cùng side effect.

| Envelope field | Yêu cầu | Kiểu | Ý nghĩa |
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

## **4.3 Idempotency theo boundary**

| **Boundary** | **Dedup key/constraint** | **Lý do** |
|---|---|---|
| Bank request | `payload.sourceEventId == envelope.eventId`; `UNIQUE(source_event_id)` | Duplicate request không tạo bộ ledger thứ hai |
| Remedy leg | `UNIQUE(remedy_leg_id)` khi có | Một decision có nhiều leg; không unique `remedyDecisionId` ở ledger |
| Reputation lock | `UNIQUE(remedy_decision_id)` | Một final decision tạo tối đa một negative lock |
| Audit | `UNIQUE(audit_record.source_event_id)` | Replay không tạo record/anchor/email thứ hai |
| Notification | `(event_id, recipient_email, notification_type)` | Một command có thể có nhiều recipient/type |
| API command | `Idempotency-Key` + actor/resource/operation scope | Retry HTTP không lặp business transition |

## **4.4 Completion reconciler**

Contract-service không consume bank result trực tiếp để quyết định terminal. Reconciler đọc internal bank ledger theo `contractId`, so expected `remedyDecisionId/remedyLegId`, trạng thái leg và remaining lock. Chỉ khi bộ leg đầy đủ và lock bằng 0 mới commit `SETTLED`, `TERMINATED`, `SUPERSEDED` hoặc `ACTIVATION_FAILED` và publish lifecycle event.

# **5. Event và command architecture**

## **5.1 Canonical consequence boundary**

- `remedy.finalized`: producer `contract-service`; consumers `escrow-service`, `reputation-service`, `audit-service`; sole trigger cho money/reputation consequences.
- `contract.terminated`: consumers `audit-service`, `analytics-service`; lifecycle facts only. Notification đi qua `notification.contract_terminated_requested`.
- `contract.settled`: positive completion/audit/analytics/reputation facts; bank không release deposit từ event này.
- `milestone.cancelled_with_penalty`: observational outcome cho analytics/audit; money đã được biểu diễn bằng remedy legs.

## **5.2 Domain/bank event catalog**

| Event | Producer | Consumers | Boundary/nguồn |
|---|---|---|---|
| contract.signed | contract-service | escrow-service, analytics-service, audit-service | milestone-escrow-phase2-design.md section 7.2 |
| escrow.deposit_locked | escrow-service | contract-service, escrow-service | milestone-escrow-phase2-design.md section 7.2 |
| escrow.deposit_lock_failed | escrow-service | contract-service | milestone-escrow-phase2-design.md sections 3.1 and 7.2 |
| bank.lock_requested | escrow-service | bank-service | bank-service-phase2-design.md section 3 |
| bank.lock_completed | bank-service | escrow-service | bank-service-phase2-design.md section 3 |
| bank.lock_failed | bank-service | escrow-service | bank-service-phase2-design.md section 3 |
| bank.release_requested | escrow-service | bank-service | bank-service-phase2-design.md sections 3.1 and 3.3; SDS Event Catalog 4.2 |
| bank.release_completed | bank-service | escrow-service | bank-service-phase2-design.md section 3; SDS Event Catalog 4.2 |
| bank.release_failed | bank-service | escrow-service | bank-service-phase2-design.md section 3; SDS Event Catalog 4.2 |
| bank.seize_requested | escrow-service | bank-service | bank-service-phase2-design.md sections 3 and 3.2; SDS Event Catalog 4.2 |
| bank.seize_completed | bank-service | escrow-service | bank-service-phase2-design.md section 3; SDS Event Catalog 4.2 |
| bank.seize_failed | bank-service | escrow-service | bank-service-phase2-design.md section 3; SDS Event Catalog 4.2 |
| bank.refund_requested | escrow-service | bank-service | bank-service-phase2-design.md sections 3.1-3.3; SDS Event Catalog 4.2 |
| bank.refund_completed | bank-service | escrow-service | bank-service-phase2-design.md section 3; SDS Event Catalog 4.2 |
| bank.refund_failed | bank-service | escrow-service | bank-service-phase2-design.md section 3; SDS Event Catalog 4.2 |
| contract.settled | contract-service | reputation-service, analytics-service | Lifecycle/positive-history event only. Normal completion first emits the existing SYSTEM/no-fault remedy.finalized owner-return legs; this event is published only after all expected legs reconcile and remaining lock is zero, and must not be consumed by escrow-service for money. |
| contract.terminated | contract-service | audit-service, analytics-service | Lifecycle/audit/analytics/notification only. Escrow and reputation MUST NOT consume this event for consequences. |
| breach.reported | contract-service | audit-service | Allegation only; no bank or reputation consumer is permitted. |
| remedy.finalized | contract-service | escrow-service, reputation-service, audit-service | Sole canonical trigger for termination/remedy money legs and reputation lock, including normal-completion return of contract-level deposits. |
| milestone.settled | contract-service | escrow-service, analytics-service, audit-service | milestone-escrow-phase2-design.md section 7.1 |
| milestone.dispute_resolved | contract-service | reputation-service | milestone-escrow-phase2-design.md section 7.1 |
| milestone.level2_provisional_settled | contract-service | escrow-service | milestone-escrow-phase2-design.md section 7.1 |
| milestone.level2_buffer_reconciled | contract-service | escrow-service | milestone-escrow-phase2-design.md section 7.1 |
| milestone.level2_terminal_settled | contract-service | escrow-service | milestone-escrow-phase2-design.md section 7.1 |
| inspection.report_confirmed | inspection-service | audit-service, contract-service | inspection-phase2-design.md section 4; milestone-escrow-phase2-design.md section 3.2; approved OQ-02 |
| milestone.seller_weighed | contract-service | file-service, audit-service | milestone-escrow-phase2-design.md sections 2.2, 3.2 and 7.1 |
| milestone.buyer_confirmed | contract-service | audit-service | milestone-escrow-phase2-design.md sections 2.2, 3.2 and 7.1 |
| milestone.flagged | contract-service |  | milestone-escrow-phase2-design.md sections 3.2 and 7.1 |
| milestone.force_majeure_claimed | contract-service | audit-service | milestone-escrow-phase2-design.md sections 2.2, 3.2 and 7.1 |
| milestone.force_majeure_resolved | contract-service | escrow-service, audit-service | milestone-escrow-phase2-design.md sections 3.2 and 7.1 |
| milestone.cancelled_with_penalty | contract-service | analytics-service, audit-service | Observational milestone outcome only. Money and reputation are already triggered by remedy.finalized and must not be consumed here. |
| escrow.milestone_funding_failed | escrow-service | contract-service | milestone-escrow-phase2-design.md section 6b and 7.1 |
| reputation.locked | reputation-service | user-service | user-service-phase2-design.md sections 2.2 and 7 |
| reputation.unlocked | reputation-service | user-service | user-service-phase2-design.md sections 2.2 and 7 |
| file.ready | file-service | contract-service, inspection-service | file-service-phase2-design.md section 5 |
| file.failed | file-service | contract-service, inspection-service | file-service-phase2-design.md section 5 |
| bank.security_lock_changed | bank-service | audit-service | bank-service-phase2-design.md sections 3.5.4 and 3.5.7 |
| bank.verifier_key_registered | bank-service | audit-service | bank-service-phase2-design.md section 3.5.6 |
| bank.large_transaction_flagged | bank-service | reputation-service, audit-service | bank-service-phase2-design.md section 3.4 |
| bank.suspicious_report_created | bank-service | audit-service | bank-service-phase2-design.md section 3.4b |
| analytics.structuring_pattern_detected | analytics-service | reputation-service, bank-service | analytics-service-phase2-design.md section 3.5 |
| reputation.elevated_risk_cleared | reputation-service | audit-service | reputation-service-phase2-design.md section 8 |
| inspection.level2_commissioned | inspection-service | audit-service | inspection-phase2-design.md sections 3.4 and 4 |
| file.email_notice | file-service | inspection-service | file-service-phase2-design.md sections 4.1 and 5 |

## **5.3 Notification command catalog**

| Command | Producer | Consumer | Payload required |
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

# **6. Data architecture và invariant storage**

## **6.1 Database-per-service**

Mỗi service có schema và DB user riêng. FK chỉ tồn tại trong schema owner; cross-service ID là UUID snapshot/reference. Migration deployment theo producer/consumer compatibility và outbox; không dùng distributed transaction.

## **6.2 Core persistence constraints**

| **Schema/table** | **Ràng buộc chính** | **Invariant** |
|---|---|---|
| contract/remedy | immutable signed terms hash; unique decision IDs; state/version | Hai chữ ký cùng terms; allegation không sửa decision đã final |
| bank.ledger_entry | append-only; source_event_id unique; remedy_leg_id partial unique; amount positive | No double money; one decision may have many legs |
| reputation.lock_entry | insert-only; remedy_decision_id unique; source_event_id unique | ≤1 lock/decision; history không UPDATE |
| audit.audit_record | source_event_id unique; chain sequence/hash unique; append-only grants | Replay/tamper detected |
| file | ingest channel server-derived; state; legal_hold; tombstone/version | File infected không READY; hold chặn xoá |
| user projection | last_lock_revision persisted | Old event không ghi đè sau restart |
| analytics | processed_event + month bucket key | At-least-once safe; recompute touched months |
| notification_log | event/recipient/type unique | Retry không gửi trùng |

# **7. State machines và choreography**

## **7.1 Contract lifecycle**

`OFFERED → NEGOTIATING → SIGNED → ACTIVE`. Từ pre-sign có thể `WITHDRAWN`; activation failure đi `ACTIVATION_REFUND_PENDING → ACTIVATION_FAILED`; replacement đi `REPLACEMENT_PENDING → SUPERSEDE_REFUND_PENDING → SUPERSEDED`; completion đi `SETTLED`; các nhánh chấm dứt khác đi `TERMINATED` sau reconciliation.

Không có current state `CANCELLED` hoặc `DELIVERED`. Terminal state luôn zero-lock; `WITHDRAWN` là ngoại lệ đơn giản vì chưa phát sinh lock.

## **7.2 Attribution/remedy**

Rổ A: objective guard → `AttributionDecision(SYSTEM)` → `RemedyDecision`. Rổ B: `BreachCase REPORTED → UNDER_REVIEW → RESOLVED` → decision. `finalBreachingRole = null` bắt buộc penalty/reputation false và chỉ có settlement/refund legs.

## **7.3 Milestone/funding**

Funding: `FUNDING_PENDING → LOCKED | FUNDING_FAILED`; retry/cure có timer riêng. Delivery: `CREATED → IN_PROGRESS → SELLER_WEIGHED → BUYER_RECEIVED → AWAITING_SELLER_RESPONSE/CONTESTED/FORCE_MAJEURE_PENDING_REVIEW → SETTLED`. Inspection pending chặn auto-confirm.

## **7.4 Mutual replacement saga**

Draft/replacement phải đủ hai chữ ký trước khi đụng contract cũ. Khi replacement signed, cũ vào pending, refund toàn bộ lock còn lại, rồi mới `SUPERSEDED`; contract mới tự activation từ zero. Crash ở bất kỳ cửa sổ nào phải để state retryable và không rollback settled history.

# **8. Security, governance và evidence**

## **8.1 Authentication/authorization**

JWT cho user; service auth cho internal. Gateway coarse role; service owner fine-grained. Public user DTO không lộ PII; public reputation summary vẫn cần JWT. OPERATOR không truy cập đường ADMIN high-risk; maker-checker chặn self-approval.

## **8.2 External Verifier emergency control**

ES256 public key baked/deploy-time, RFC 8785 canonicalization, timestamp ±300s, nonce persist, action binding. Lock active làm mọi `bank.*_requested` fail. Unlock mirror lock; không Admin bypass. Đây là một control khẩn cấp hẹp, không phải full Zero Trust.

## **8.3 Evidence scope**

Audit hash chứng minh record không bị sửa sau commit; content/report hash chứng minh artefact bytes/normalized result; OTP chứng minh challenge holder hoàn tất thao tác; không lớp nào tự chứng minh thẩm quyền pháp lý, sự thật ngoài đời hoặc tính hợp pháp của điều khoản.

## **8.4 Retention/DR**

PII/evidence/ledger/audit có retention khác nhau; legal hold override deletion. File xoá hai bước tự lành. DLQ replay giữ original event ID. Restore bank/audit phải qua hai verification pass trước mở money traffic. Cross-border/ND13 là deployment constraint chưa được “giải quyết tự động”.

# **9. Deployment và quality attributes**

Môi trường mục tiêu dùng container, MySQL per service/schema, RabbitMQ, MinIO và reverse proxy. CI pin image digest, không nhúng secret, protected branch/approval. Observability dùng correlation/causation/trace ID; alert riêng cho money leg failed, reconciliation mismatch, audit verify fail, security lock và DLQ.

| **Quality attribute** | **Cơ chế** | **Giới hạn** |
|---|---|---|
| Consistency | local transaction + outbox + idempotency + reconciliation | Eventual consistency ngoài terminal money guard |
| Integrity | append-only ledger/audit, hash chain, source IDs | Trusted operator/collusion risk vẫn tồn tại |
| Availability | retry/DLQ/circuit breaker; analytics noncritical | OTP and eligibility intentionally fail-closed |
| Security | JWT/service auth, edge policies, maker-checker, verifier signature | Không full mTLS/Zero Trust production |
| Recoverability | backups, replay, two-pass verify | RTO/RPO production phải benchmark |
| Auditability | immutable facts, evidence export, read audit | Chất lượng source evidence vẫn cần con người |

# **10. API surface overview**

OpenAPI frozen có 41 paths. Bảng dưới là ownership-level catalog; request/response schema chi tiết nằm trong SDS.

| Method | Path | Owner | Operation | Request | Auth/role |
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

# **11. Known limitations và Future Work**

- Bank/custodian, external inspection, CA signature, email/OAuth/source pricing là adapter/mocks cần production integration.
- Full mTLS/service mesh, multi-region HA, HSM/KMS, external immutable storage policy và automated cold rebuild không phải Phase 2 completed capability.
- Full contract amendment, partial mutual termination, quality-indexed settlement và automated legal rule engine nằm ngoài scope; replacement là cơ chế hiện tại.
- Geo/yield/AML signals không auto convict/hold; external legal/EUDR validation vẫn cần.
- Analytics catch-up được bảo đảm từ queue backlog cho outage ngắn, không claim rebuild mọi history từ empty DB.

# **12. Source-of-truth index**

Thiết kế chi tiết được đồng bộ từ toàn bộ design service, Verification Matrix, state machine/integration/enum/error/golden-flow contract, OpenAPI, event/notification schemas và implementation plan ngày 20/07/2026. Khi có mâu thuẫn, design mới nhất và Verification Matrix được ưu tiên; source code chỉ là baseline triển khai.
