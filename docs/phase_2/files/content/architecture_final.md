---
title: "AGRICONTRACT"
subtitle: "Kiến trúc kỹ thuật của đồ án"
author: "Tài liệu đồ án tốt nghiệp"
date: "Phiên bản final · Tháng 7/2026"
toc-title: "Mục lục"
---

# **Tóm tắt điều hành**

Kiến trúc mục tiêu gồm **12 business services** và `api-gateway` ở lớp biên. Signature/OTP thuộc `contract-service`; hash chain/anchor thuộc `audit-service`; gateway không được tính như business service. Hệ thống dùng database-per-service, transactional outbox và at-least-once messaging, nhưng đặt guard mạnh hơn trên đường tiền: bank ledger là sổ tiền authoritative, mỗi money leg có idempotency key riêng, và lifecycle terminal chỉ được commit sau ledger reconciliation + zero remaining lock.

Phạm vi thực thi của đồ án chỉ là hợp đồng cà phê B2B nội địa Việt Nam. API write, product/listing, quality schema, pricing, template và document profile đều khóa `COFFEE`; dữ liệu commodity cũ chỉ được đọc/vận hành theo compatibility policy.

Ranh giới quan trọng nhất là **decision trước consequence**. `requestedBy`, allegation và final attribution được tách. `remedy.finalized` authoritative cho termination/remedy/quality-resolution money, contract-level deposit return và reputation lock; clean-path per-milestone money chạy qua `milestone.settled`; hai event không cùng phát cho một resolution. `contract.settled`/`contract.terminated` chỉ phản ánh lifecycle/audit/analytics; bank và reputation không được consume chúng để release/seize/lock.

Kiến trúc áp dụng các control zero-trust-oriented tại edge, internal route và External Verifier, nhưng không claim mô hình Zero Trust toàn diện. Bank thật, CA signature, external inspection production và một số DR/data integration vẫn ngoài phạm vi triển khai của đồ án.

# **1. Context, phạm vi và nguyên tắc**

## **1.1 Context hệ thống**

External actors gồm Buyer, Seller, Inspector, Admin, Operator, External Verifier, tổ chức giám định Level 2, nhà cung cấp email/OTP, nguồn giá và custodian/ngân hàng. Tất cả request người dùng đi qua Nginx/API Gateway; `/internal/**` chỉ cho service-to-service. External Verifier là ngoại lệ có endpoint ký ES256 trực tiếp tới bank-service qua route chuyên biệt, không JWT và không retry.

## **1.2 Nguyên tắc kiến trúc**

- Business capability ownership rõ và database-per-service; không cross-service DB access.
- Rich aggregate/state transition; không để controller hoặc tầng truy cập dữ liệu tự sửa state.
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
| contract-service | Contract, milestone, consent/signature/OTP, template/document, attribution, remedy, termination | contract_db | REST commands; domain events; internal user/OTP/file | Owner duy nhất của business decision, lifecycle và immutable contract representation metadata |
| escrow-service | Projection escrow và chuyển remedy legs thành bank commands | escrow_db | contract.signed, remedy.finalized, milestone outcomes, bank results | Không sở hữu sổ tiền authoritative |
| bank-service | Append-only monetary ledger, lock/release/refund/seize, security lock | bank_db | bank.*_requested; structuring signal; external verifier | Sở hữu sổ tiền authoritative |
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

Consent/signature/OTP challenge là domain con của contract-service vì phải binding với exact action, statement, terms hash và state. `ContractTemplateProfile` và `ContractDocument` metadata cũng do contract-service sở hữu; file-service chỉ giữ blob đã scan/hashed. audit-service sở hữu audit record/hash/anchor; inspection-service sở hữu result/report commitment. `signedContentHash`/`sourceContentHash`, `documentFileHash`, report/result hash và audit record hash có mục tiêu khác nhau, không được trộn thành một “hash chung”.

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

- `remedy.finalized`: producer `contract-service`; consumers `escrow-service`, `reputation-service`, `audit-service`; authoritative cho termination/remedy/quality-resolution money, contract-level deposit return và reputation lock; clean-path per-milestone money chạy qua `milestone.settled`; hai event không cùng phát cho một resolution.
- `contract.terminated`: consumers `audit-service`, `analytics-service`; lifecycle facts only. Notification đi qua `notification.contract_terminated_requested`.
- `contract.settled`: positive completion/audit/analytics/reputation facts; bank không release deposit từ event này.
- `milestone.cancelled_with_penalty`: observational outcome cho analytics/audit; money đã được biểu diễn bằng remedy legs.

## **5.2 Domain/bank event catalog**

| Event | Producer | Consumers | Side-effect boundary |
|---|---|---|---|
| contract.created | contract-service | product-service | Canonical event; consumers apply the frozen side-effect boundary and durable idempotency rules |
| contract.withdrawn | contract-service | product-service | Canonical event; consumers apply the frozen side-effect boundary and durable idempotency rules |
| contract.signed | contract-service | product-service, escrow-service, analytics-service, audit-service | Canonical event; consumers apply the frozen side-effect boundary and durable idempotency rules |
| contract.activation_failed | contract-service | product-service | Canonical event; consumers apply the frozen side-effect boundary and durable idempotency rules |
| contract.activated | contract-service | product-service | Canonical event; consumers apply the frozen side-effect boundary and durable idempotency rules |
| escrow.deposit_locked | escrow-service | contract-service, escrow-service | Canonical event; consumers apply the frozen side-effect boundary and durable idempotency rules |
| escrow.deposit_lock_failed | escrow-service | contract-service | Canonical event; consumers apply the frozen side-effect boundary and durable idempotency rules |
| bank.lock_requested | escrow-service | bank-service | Canonical event; consumers apply the frozen side-effect boundary and durable idempotency rules |
| bank.lock_completed | bank-service | escrow-service | Canonical event; consumers apply the frozen side-effect boundary and durable idempotency rules |
| bank.lock_failed | bank-service | escrow-service | Canonical event; consumers apply the frozen side-effect boundary and durable idempotency rules |
| bank.release_requested | escrow-service | bank-service | Canonical event; consumers apply the frozen side-effect boundary and durable idempotency rules |
| bank.release_completed | bank-service | escrow-service | Canonical event; consumers apply the frozen side-effect boundary and durable idempotency rules |
| bank.release_failed | bank-service | escrow-service | Canonical event; consumers apply the frozen side-effect boundary and durable idempotency rules |
| bank.seize_requested | escrow-service | bank-service | Canonical event; consumers apply the frozen side-effect boundary and durable idempotency rules |
| bank.seize_completed | bank-service | escrow-service | Canonical event; consumers apply the frozen side-effect boundary and durable idempotency rules |
| bank.seize_failed | bank-service | escrow-service | Canonical event; consumers apply the frozen side-effect boundary and durable idempotency rules |
| bank.refund_requested | escrow-service | bank-service | Canonical event; consumers apply the frozen side-effect boundary and durable idempotency rules |
| bank.refund_completed | bank-service | escrow-service | Canonical event; consumers apply the frozen side-effect boundary and durable idempotency rules |
| bank.refund_failed | bank-service | escrow-service | Canonical event; consumers apply the frozen side-effect boundary and durable idempotency rules |
| contract.settled | contract-service | reputation-service, analytics-service | Canonical event; consumers apply the frozen side-effect boundary and durable idempotency rules |
| contract.terminated | contract-service | audit-service, analytics-service | Canonical event; consumers apply the frozen side-effect boundary and durable idempotency rules |
| breach.reported | contract-service | audit-service | Canonical event; consumers apply the frozen side-effect boundary and durable idempotency rules |
| remedy.finalized | contract-service | escrow-service, reputation-service, audit-service | Canonical event; consumers apply the frozen side-effect boundary and durable idempotency rules |
| milestone.settled | contract-service | escrow-service, analytics-service, audit-service | Canonical event; consumers apply the frozen side-effect boundary and durable idempotency rules |
| milestone.dispute_resolved | contract-service | reputation-service | Canonical event; consumers apply the frozen side-effect boundary and durable idempotency rules |
| milestone.level2_provisional_settled | contract-service | escrow-service | Canonical event; consumers apply the frozen side-effect boundary and durable idempotency rules |
| milestone.level2_buffer_reconciled | contract-service | escrow-service | Canonical event; consumers apply the frozen side-effect boundary and durable idempotency rules |
| milestone.level2_terminal_settled | contract-service | escrow-service | Canonical event; consumers apply the frozen side-effect boundary and durable idempotency rules |
| milestone.price_adjusted | contract-service | audit-service, analytics-service | Canonical event; consumers apply the frozen side-effect boundary and durable idempotency rules |
| inspection.report_confirmed | inspection-service | audit-service, contract-service | Canonical event; consumers apply the frozen side-effect boundary and durable idempotency rules |
| milestone.seller_weighed | contract-service | file-service, audit-service | Canonical event; consumers apply the frozen side-effect boundary and durable idempotency rules |
| milestone.buyer_confirmed | contract-service | audit-service | Canonical event; consumers apply the frozen side-effect boundary and durable idempotency rules |
| milestone.flagged | contract-service |  | Canonical event; consumers apply the frozen side-effect boundary and durable idempotency rules |
| milestone.force_majeure_claimed | contract-service | audit-service | Canonical event; consumers apply the frozen side-effect boundary and durable idempotency rules |
| milestone.force_majeure_resolved | contract-service | escrow-service, audit-service | Canonical event; consumers apply the frozen side-effect boundary and durable idempotency rules |
| milestone.cancelled_with_penalty | contract-service | analytics-service, audit-service | Canonical event; consumers apply the frozen side-effect boundary and durable idempotency rules |
| escrow.milestone_funding_failed | escrow-service | contract-service | Canonical event; consumers apply the frozen side-effect boundary and durable idempotency rules |
| reputation.locked | reputation-service | user-service | Canonical event; consumers apply the frozen side-effect boundary and durable idempotency rules |
| reputation.unlocked | reputation-service | user-service | Canonical event; consumers apply the frozen side-effect boundary and durable idempotency rules |
| file.ready | file-service | contract-service, inspection-service | Canonical event; consumers apply the frozen side-effect boundary and durable idempotency rules |
| file.failed | file-service | contract-service, inspection-service | Canonical event; consumers apply the frozen side-effect boundary and durable idempotency rules |
| contract.document_generated | contract-service | audit-service | Immutable representation is READY once; audit records both source-content and PDF-file hashes |
| bank.security_lock_changed | bank-service | audit-service | Canonical event; consumers apply the frozen side-effect boundary and durable idempotency rules |
| bank.verifier_key_registered | bank-service | audit-service | Canonical event; consumers apply the frozen side-effect boundary and durable idempotency rules |
| bank.large_transaction_flagged | bank-service | reputation-service, audit-service | Canonical event; consumers apply the frozen side-effect boundary and durable idempotency rules |
| bank.suspicious_report_created | bank-service | audit-service | Canonical event; consumers apply the frozen side-effect boundary and durable idempotency rules |
| analytics.structuring_pattern_detected | analytics-service | reputation-service, bank-service | Canonical event; consumers apply the frozen side-effect boundary and durable idempotency rules |
| reputation.elevated_risk_cleared | reputation-service | audit-service | Canonical event; consumers apply the frozen side-effect boundary and durable idempotency rules |
| inspection.level2_commissioned | inspection-service | audit-service | Canonical event; consumers apply the frozen side-effect boundary and durable idempotency rules |
| file.email_notice | file-service | inspection-service | Canonical event; consumers apply the frozen side-effect boundary and durable idempotency rules |

## **5.3 Notification command catalog**

| Command | Producer | Consumer | Payload required |
|---|---|---|---|
| notification.contract_anchor_requested | contract-service | notification-service | `contractId`, `recipients`, `contractTermsSnapshot`, `signedContentHash`, `signedAt` |
| notification.contract_document_ready_requested | contract-service | notification-service | `documentId`, `contractId`, `recipients`, `fileId`, `sourceContentHash`, `documentFileHash`, `templateCode`, `templateVersion`, `generatedAt` |
| notification.contract_activation_failed_requested | contract-service | notification-service | `contractId`, `recipients`, `failedLeg`, `buyerDepositState`, `sellerDepositState`, `nextActions` |
| notification.contract_terminated_requested | contract-service | notification-service | `contractId`, `recipients`, `terminationType`, `requestedBy`, `finalBreachingRole`, `breachReasonCode`, `remedyDecisionId`, `breachCaseId`, `affectedMilestoneIds`, `supersededByContractId`, `replacesContractId`, `remedyLegs` |
| notification.breach_notice_requested | contract-service | notification-service | `breachCaseId`, `contractId`, `recipients`, `breachReasonCode`, `severity`, `evidenceRefs` |
| notification.remedy_finalized_requested | contract-service | notification-service | `remedyDecisionId`, `attributionDecisionId`, `breachCaseId`, `contractId`, `buyerId`, `sellerId`, `affectedMilestoneIds`, `recipients`, `finalBreachingRole`, `breachReasonCode`, `decisionSource`, `penaltyEligible`, `reputationEligible`, `remedyLegs` |
| notification.milestone_funding_status_requested | contract-service | notification-service | `contractId`, `milestoneId`, `recipients`, `statusType` |
| notification.milestone_status_requested | contract-service | notification-service | `contractId`, `milestoneId`, `recipients`, `statusType` |
| notification.milestone_anchor_requested | audit-service | notification-service | `contractId`, `milestoneId`, `recipients`, `recordHash`, `otsProof`, `settlementSummary` |
| notification.level2_commission_requested | inspection-service | notification-service | `commissionId`, `contractId`, `recipients`, `org`, `intakeAddress` |
| notification.audit_digest_requested | audit-service | notification-service | `recipients`, `recordHash` |
| notification.audit_failure_requested | audit-service | notification-service | `recipients`, `failureSummary` |
| notification.security_lock_changed_requested | bank-service | notification-service | `recipients`, `action`, `reason`, `timestamp` |
| notification.reconciliation_mismatch_requested | bank-service | notification-service | `contractId`, `recipients`, `mismatchSummary` |
| notification.user_kyc_result_requested | user-service | notification-service | `userId`, `recipientEmail`, `organizationName`, `result`, `decidedAt` |
| notification.user_lock_changed_requested | user-service | notification-service | `userId`, `recipientEmail`, `action`, `reasonCode` |
| notification.verifier_key_anchor_requested | bank-service | notification-service | `recipientEmail`, `publicKeyFingerprint` |

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

ES256 public key baked/deploy-time, RFC 8785 canonicalization, timestamp ±300s, nonce persist, action binding. Lock active làm mọi `bank.*_requested` fail. Unlock mirror lock; không Admin bypass. Đây là một control khẩn cấp hẹp, không phải mô hình Zero Trust toàn diện.

## **8.3 Evidence scope**

Audit hash hỗ trợ kiểm tra record có bị sửa sau commit hay không; content/report hash hỗ trợ kiểm tra artefact bytes/normalized result; OTP ghi nhận challenge holder đã hoàn tất thao tác. Không lớp nào tự chứng minh dữ liệu đầu vào đúng, người thao tác có thẩm quyền pháp lý hoặc điều khoản hợp pháp; giá trị chứng cứ phụ thuộc độ tin cậy, tính toàn vẹn và khả năng xác định chủ thể trong bối cảnh giao dịch. [27]

Mỗi hành vi cần xác nhận tạo `ConsentEvidence` append-only, gắn actor/role, action, subject, statement code-version-hash, `presentedContentHash`, thời điểm và correlation ID. Ký hợp đồng yêu cầu checkbox + session step-up + OTP; hành vi vận hành như cân giao, nhận hàng, flag hoặc accept giá yêu cầu checkbox + authenticated ownership check. Checkbox không phải điểm chỉ/vân tay sinh trắc học và không nâng tier chữ ký thành chữ ký số CA.

Template hợp đồng có profile `{VN_COFFEE_B2B, contractTemplateVersion, contractTemplateHash, coffeeQualityProfileVersion}` nằm trong signed snapshot. Preview server-side luôn có watermark `CHƯA CÓ HIỆU LỰC`. Chữ ký thứ hai commit `SIGNED` và `ContractDocument(PENDING)`; worker render/lưu PDF ngoài transaction, rồi chuyển `READY`, phát `contract.document_generated` và notification final. `sourceContentHash` commit nội dung pháp lý, `documentFileHash` commit bytes PDF; document READY và blob final không được overwrite.

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

OpenAPI frozen có **46 paths / 47 operations / 62 schemas**. Supplementary API có **16 paths / 18 operations / 15 schemas**. Bảng dưới trình bày ownership-level catalog, request schema và auth/role; JSON error dùng `ErrorEnvelope` gồm `timestamp`, `status`, `error`, `code`, `message`, `path`, `traceId`. Endpoint không khai báo security riêng kế thừa bearer JWT; `/internal/**` dùng `serviceAuth`, audit hash dùng `X-Api-Key`, emergency lock/unlock chỉ nhận signed request của External Verifier.

| Method | Path | Owner | Operation | Request | Auth/role |
|---|---|---|---|---|---|
| POST | /api/v1/contracts | contract-service | createContract | CreateContractRequest | BUYER |
| GET | /api/v1/contracts | contract-service | listContracts | — | JWT |
| GET | /api/v1/contracts/{contractId} | contract-service | getContract | — | JWT |
| GET | /api/v1/contracts/{contractId}/document-preview | contract-service | previewContractDocument | — | BUYER, SELLER |
| GET | /api/v1/contracts/{contractId}/documents | contract-service | listContractDocuments | — | BUYER, SELLER |
| GET | /api/v1/contracts/{contractId}/documents/{documentId} | contract-service | getContractDocument | — | BUYER, SELLER |
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
| POST | /api/v1/contracts/{contractId}/milestones/{milestoneId}/respond | contract-service | respondToMilestoneFlag | ConsentInput | SELLER |
| POST | /api/v1/contracts/{contractId}/milestones/{milestoneId}/force-majeure | contract-service | claimForceMajeure | ForceMajeureClaimRequest | SELLER |
| POST | /api/v1/contracts/{contractId}/milestones/{milestoneId}/force-majeure/resolve | contract-service | resolveForceMajeure | ForceMajeureResolveRequest | ADMIN |
| POST | /api/v1/contracts/{contractId}/milestones/{milestoneId}/funding/retry | escrow-service | retryMilestoneFunding | — | ADMIN |
| POST | /api/v1/contracts/{contractId}/milestones/{milestoneId}/price-adjustments/propose | contract-service | proposeMilestonePriceAdjustment | PriceAdjustmentProposalRequest | BUYER, SELLER |
| POST | /api/v1/contracts/{contractId}/milestones/{milestoneId}/price-adjustments/{proposalId}/accept | contract-service | acceptMilestonePriceAdjustment | ConsentInput | BUYER, SELLER |
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
| GET | /api/v1/security/audit-hash | audit-service | getAuditHash | — | X-Api-Key |
| POST | /api/v1/security/emergency-lock | bank-service | emergencyLock | ExternalVerifierRequest | External Verifier |
| POST | /api/v1/security/emergency-unlock | bank-service | emergencyUnlock | ExternalVerifierRequest | External Verifier |
| GET | /api/v1/escrows/statements | escrow-service | exportEscrowStatement | — | JWT |
| GET | /internal/v1/bank/ledger | bank-service | getInternalLedgerEntries | — | serviceAuth |

# **10.1 Frozen design synchronization addendum**

## **Signature, lifecycle và OTP**

Terms chỉ hợp lệ để sửa trước bất kỳ chữ ký nào. Chữ ký đầu tiên làm đóng băng toàn bộ terms và `signedContentHash`; chữ ký thứ hai bắt buộc dùng đúng hash đó. Không có `PATCH /terms` sau chữ ký đầu tiên. Normal completion phát `milestone.settled` cho milestone cuối và dùng `remedy.finalized` chỉ để trả cọc cấp contract; hai event không cùng làm money consequence cho một quality resolution.

OTP được binding theo tuple `(signerUserId, contractId, signedContentHash)`. Không có khái niệm “latest OTP” xuyên user hoặc xuyên contract; trong cùng tuple chỉ challenge mới nhất còn hạn mới verify, còn challenge cũ bị vô hiệu khi resend. Matrix item tương ứng là `21g`.

Trước khi tạo challenge, server kiểm tra checkbox statement đang active, session step-up và `presentedContentHash`, rồi ghi `ConsentEvidence` append-only. Template profile `VN_COFFEE_B2B` và coffee quality profile version nằm trong `ContractTermsSnapshot`; đổi template/profile làm đổi `signedContentHash`. Sau chữ ký thứ hai, final PDF đi qua `PENDING -> READY | FAILED` bất đồng bộ; lỗi render không rollback chữ ký. Preview luôn mang watermark `CHƯA CÓ HIỆU LỰC`; final READY được kiểm chứng bằng cả `sourceContentHash` và `documentFileHash`.

## **Inventory, reservation và concurrency**

Product-service là owner duy nhất của `minOrderQuantity`, `quantityAvailable`, `reservedQuantity`, listing và reservation. Listing lifecycle là `AVAILABLE -> PARTIALLY_COMMITTED -> CLOSED` (vẫn giữ `PAUSED`); reservation lifecycle là `RESERVED -> SIGNED_RELEASABLE -> COMMITTED | RELEASED`. Reserve qua synchronous internal product-service API trước khi persist `OFFERED`; persistence failure phải compensation-release idempotent. `contract.activated` mới commit inventory; `requestedQuantity = SUM(committedQuantity)` và F18 fail closed nếu tổng không bằng `reservedQuantity`.

Listing response expose opaque `listingVersionToken`, tạo từ canonical UTC `Listing.updatedAt`. Stale token trả `LISTING_VERSION_MISMATCH` (`VERSION_MISMATCH`, reload/retry); thiếu tồn, dưới `minOrderQuantity`, legacy `quantityAvailable = null` hoặc state conflict trả `INSUFFICIENT_LISTING_QUANTITY` (`STATE_CONFLICT`, refresh, không blind retry). Reservation replay không decrement/restore lần hai.

`OrphanReservationReaperJob` chạy trong product-service, mặc định `orphanReservationGraceMinutes = 30`. Job chỉ release reservation `RESERVED` quá grace khi durable store chưa có `contract.created`; không gọi cross-service, không thêm endpoint, không đụng `SIGNED_RELEASABLE`/`COMMITTED`, không reopen `CLOSED`/`PAUSED`, và log + alert Admin mỗi release. Late `contract.created` sau `RELEASED` là inventory inconsistency: error log + Admin alert, không silent deduplication, reverse transition, re-decrement hoặc inventory path thứ hai.

## **Signed pricing, deposits và money authority**

`priceBand` và `priceAdjustmentRule` là signed terms và nằm trong `signedContentHash`. Propose/accept là hai JWT-authenticated, idempotent actions, không OTP, chỉ hợp lệ trước funding. Adjustment in-band chỉ thực thi rule đã ký, không amend terms/hash; phát `milestone.price_adjusted` audit/lifecycle-only. Funding dùng `effectiveUnitPrice` (accepted adjustment hoặc agreed fallback), không true-up settlement. Deposit tính một lần từ nominal `totalContractValue`; `pricing-service` chỉ advisory.

`buyerDepositRate` có band `[3%, 10%]`, default `5%`, lock tại `SIGNED` trên nominal value. Signed legacy rate ngoài band vẫn readable và operable, không migration normalize/backfill. `sellerDepositRate` optional giữ nguyên vocabulary hiện hành.

## **Snapshots, delivery, inspection và metadata**

Contract CREATE server-derives and snapshots `goodsTerms`, `deliveryTerms`, `inspectionTerms` from Listing/Product/Category/Plot. Later source edits/deletes do not alter snapshot/hash; changing goods requires replacement. Các thao tác ghi mới của đồ án require `commodity = COFFEE` and a non-empty `originSnapshot.plotRefs[]`; other commodity values are rejected. Only delivery allocation rules are signed; warehouse, carrier, address, tracking and concrete delivery instances remain runtime data. `cropYear` and `harvestSeason` are required-but-nullable server-derived signed snapshot fields; `certifications` remains seller-declared listing metadata, not verified compliance.

Inspection terms include level/cost and require `inspectionCostResponsibility = LOSER_PAYS`; `MANDATORY_BEFORE_SETTLEMENT` is a settlement guard, not a milestone state. Read-only nullable listing metadata are `cropYear`, `harvestSeason`, `certifications`, `packagingDeclaration`, `warehouseLocation`, and `estimatedAvailabilityDate`; certifications are seller-declared free text, and warehouse location is distinct from delivery point.

## **Quality and disposition**

Quality has declared, committed and actual layers. A clean confirmation does not measure quality unless a quality flag exists or inspection is mandatory. Each metric carries `deviationPolicy {direction, tolerance, penalty, reject, discountRate}` with disjoint tolerance/penalty/reject zones and inclusive boundaries on the stricter side. Inspection reports contain raw actual metrics and `resultHash`; contract-service, not inspector, computes disposition and money.

The four dispositions are explicit: `CONFORMING` rejects an allegation after a flag, settles normally, and Buyer bears inspection cost under `LOSER_PAYS`; `PARTIALLY_CONFORMING` is payment adjustment only, not a breach, with no penalty, breach attribution or reputation effect, selects only the largest applicable `discountRate`, reduces `RELEASE_TO_SELLER`, and transfers the difference to Buyer; `NON_CONFORMING` is Seller breach with standard remedy, penalty base equal to affected milestone value, fails only that milestone, and does not auto-terminate the contract; `INCONCLUSIVE` routes to reinspection/Level 2 without inventing money or breach consequences. Các schema chất lượng của đồ án accept only coffee; exact-match rejection applies to coffee `type`. Complete quality resolution emits exactly one `remedy.finalized`.

## **Conventions and guardrails**

Canonical mass unit is `kg`; `unitOfMeasure` is constant `KG`. Store timestamps in UTC; display and calculate deadlines in `Asia/Ho_Chi_Minh`; date deadlines end at ICT day end; working days are Monday-Friday excluding Vietnamese holidays. Money uses `DECIMAL(18,2)` with `HALF_UP`; only contract-service rounds, and bank-service never re-multiplies or re-rounds. Money strings are decimal strings with exactly two fractional digits, schema `pattern` enforced, and field-specific `maxLength` `11` or `19` (not numeric `maximum`). Guardrails: max 100 milestones per contract, max 1,000,000 kg per milestone, delivered quantity `exclusiveMinimum: 0`, measured/accepted quantities `minimum: 0`, so `accepted = 0` is valid.

## **LegalProfile references**

`LegalProfile` aligns LTM Điều 301 with the 8% contractual-penalty cap, LTM Điều 266 with the 10x inspection-fee ceiling, LTM Điều 302/307 with damages and penalty interaction, and BLDS Điều 328/330 with deposit/security references. The existing Known Limitation remains: classification of the deposit under BLDS Điều 328 requires lawyer confirmation.

# **10.2 Supplementary API and event totals**

The authoritative supplementary API catalog is `16 paths / 18 operations / 15 schemas`. The authoritative domain-event catalog is **50 events**; lifecycle mappings are `contract.created` (contract-service -> product-service), `contract.activated` (contract-service -> product-service), `contract.withdrawn` (contract-service -> product-service), and `contract.activation_failed` (contract-service -> product-service). `contract.signed` includes `listingId` and `reservedQuantity` and is consumed by product-service, escrow-service, analytics-service and audit-service. `contract.document_generated` carries the immutable generated representation hashes to audit-service.
| Supplementary method | Path | Owner | operationId | Auth/role |
|---|---|---|---|---|
| POST | /api/v1/plots | product-service | registerPlot | SELLER |
| POST | /api/v1/listings | product-service | createListing | SELLER |
| GET | /api/v1/listings | product-service | searchListings | JWT |
| PUT | /api/v1/listings/{listingId} | product-service | updateListing | SELLER |
| POST | /internal/v1/listing-reservations | product-service | reserveListingQuantity | service-to-service |
| POST | /internal/v1/listing-reservations/{contractId}/release | product-service | compensateListingReservation | service-to-service |
| POST | /api/v1/listings/{listingId}/offers | product-service | createOffer | BUYER |
| GET | /api/v1/prices/{commodity}/{province} | pricing-service | getPriceQuote | public |
| GET | /api/v1/prices/{commodity}/{province}/items | pricing-service | listPriceItems | public |
| POST | /api/v1/prices/manual | pricing-service | recordManualPrice | ADMIN |
| GET | /api/v1/analytics/terminations | analytics-service | getTerminationAnalytics | ADMIN |
| GET | /api/v1/analytics/escrow-effectiveness | analytics-service | getEscrowEffectiveness | ADMIN |
| GET | /api/v1/analytics/force-majeure-trends | analytics-service | getForceMajeureTrends | ADMIN |
| POST | /api/v1/inspections/{contractId}/milestones/{milestoneId}/reports | inspection-service | submitInspectionReport | INSPECTOR |
| POST | /internal/v1/files | file-service | storeOnBehalfOf | service-to-service |
| GET | /internal/v1/files/{fileId} | file-service | getFile | service-to-service |
| DELETE | /internal/v1/files/{fileId} | file-service | deleteFile | service-to-service |
| POST | /internal/v1/files/{fileId}/confirm-attached | file-service | confirmAttached | service-to-service |


# **11. Current Scope**

Đồ án hiện thực hoá boundary của 12 business services cho giao dịch cà phê B2B nội địa Việt Nam: API Gateway, database-per-service, event envelope, outbox/idempotency, consent/signature, template và immutable document lifecycle, contract–escrow–bank golden flow, inspection, reputation, audit/evidence, product geolocation, file intake, external reference pricing, user governance, notification và analytics projection. Các state, event, endpoint, schema và side-effect boundary trong tài liệu là contract thiết kế để triển khai; chúng không phải bằng chứng rằng toàn bộ capability đã chạy production.

# **12. Known Limitations**

- Bank/custodian thật, CA signature, tổ chức inspection production, provider pricing và OpenTimestamps production chưa được tích hợp trong đồ án; bank-service là ledger/adapter mô phỏng.
- Full mTLS/service mesh, HSM/KMS độc lập, multi-region HA, deployment attestation và cold rebuild analytics chưa thuộc phạm vi implementation.
- Geo/yield/AML/overlap chỉ là tín hiệu hỗ trợ review; traceability phụ thuộc chất lượng dữ liệu, chi phí và quy trình due diligence, nên không tự kết luận gian lận, sự thật địa chính, compliance EUDR hay trách nhiệm pháp lý. [21], [22], [30]
- Checkbox/step-up/OTP là cơ chế bằng chứng điện tử tầng cơ sở; không phải điểm chỉ/vân tay thật hoặc chữ ký số CA. Giá trị chứng cứ và thẩm quyền đại diện vẫn cần đánh giá theo giao dịch, định danh và tài liệu thực tế. [27]
- Phân loại tiền cọc, penalty, damages, licensing boundary và mẫu hợp đồng cần legal validation; việc tách custody khỏi platform không tự chứng minh compliance. [28], [29], [45], [46]
- Hệ thống không bảo vệ tuyệt đối trước collusion của các trust domain hoặc hạ tầng bị compromise đồng thời; zero-trust-oriented controls chỉ giảm implicit trust tại các boundary đã nêu.
- Analytics/notification có eventual consistency; queue backlog chỉ hỗ trợ catch-up outage ngắn, không phải cold rebuild toàn bộ lịch sử.

# **13. Future Work**

- Tích hợp custodian/ngân hàng thật, đối soát và báo cáo AML theo adapter đã đóng băng.
- Tích hợp CA/WebAuthn, production inspection/accreditation và adapter email/provider phù hợp deployment.
- Tách separation-of-duties hạ tầng, mTLS/KMS/HSM, deployment attestation và DR/scale production.
- Thiết kế riêng cho ContractVersion amendment sau ký, full notice–cure–remedy state machine, quality-indexed settlement, payment/security package đầy đủ và partial mutual termination.
- Nghiên cứu mở rộng commodity sau cà phê bằng template, quality profile, nguồn giá và verification matrix riêng; logistics/3PL tracking chỉ khi có đối tác thật.
- Hoàn thiện cross-border/ND13 assessment, DSAR và các chính sách vận hành ngoài prototype.

# **14. Danh mục nguồn tham khảo**

[21] A. Kashyap et al., “Traceability Adoption Barriers in Digital Food Supply Chain to Achieve Food Security and Sustainability,” Bus. Strategy Environ., vol. 35, no. 1, 2025, doi: 10.1002/bse.70177.

[22] V. Guye, P. Meyfroidt, and E. z. Ermgassen, “The Need for Improved Public Transparency in the Era of Due Diligence Regulations,” Regulation & Governance, 2026, doi: 10.1111/rego.70142.

[27] Quốc hội Việt Nam, Luật Giao dịch điện tử số 20/2023/QH15, 2023.

[28] Chính phủ Việt Nam, Nghị định 52/2024/NĐ-CP, 2024.

[29] Chính phủ Việt Nam, Nghị định 340/2025/NĐ-CP, 2025.

[30] European Parliament and Council, Regulation (EU) 2023/1115, consolidated Dec. 26, 2025.

[45] Quốc hội Việt Nam, Bộ luật Dân sự số 91/2015/QH13, 2015.

[46] Quốc hội Việt Nam, Luật Thương mại số 36/2005/QH11, 2005.
