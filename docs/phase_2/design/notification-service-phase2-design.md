---
name: notification-service-phase2-design
description: "Notification-service Phase 2 — event-to-template routing, OTP delivery, email anchor, retry/DLQ và idempotency theo recipient."
status: DESIGNED — chưa code.
metadata:
  type: design
  phase: 2
  extends: "notification-service Phase 1; AgriContract Architecture v2 §3.12; SDS Phần 5 §3"
  related: "signature-phase2-design.md §4.1/§6; hash-chain-phase2-design.md §4-5; milestone-escrow-phase2-design.md §7"
---

## 1. Bối cảnh & Scope

Phase 1 đã có email adapter, `NotificationLog`, RabbitMQ listeners, retry/DLQ và dedup `(eventId, userId)`. Phase 2 tăng vai trò từ email giao dịch đơn giản thành ba nhóm có sức nặng khác nhau:

1. **Transactional:** KYC, contract, milestone, escrow/dispute.
2. **Security:** OTP ký, emergency lock/unlock, key fingerprint.
3. **Evidence:** snapshot + hash, OTS proof, weekly digest và integrity-failure alert.

Notification-service không quyết định nghiệp vụ, không tính hash, không xác minh OTP và không phán quyết ai là recipient. Publisher gửi payload đã tính xong cùng email người nhận; notification chỉ validate, render, gửi và ghi log.

## 2. Ingress & Trust Boundary

### 2.1 RabbitMQ notification commands — đường mặc định

Mọi notification không cần caller biết kết quả gửi ngay lập tức đi qua routing key `notification.*_requested`. Owner service tạo command đã có recipient/template data; không ép các domain event vốn phục vụ tiền/analytics phải phình payload vì email. Envelope canonical là schema executable `contracts/events/notification-commands.yaml`; recipients nằm **trong command payload**, không nằm top-level:

```json
{
  "eventId": "uuid",
  "eventType": "notification.milestone_anchor_requested",
  "eventVersion": 1,
  "occurredAt": "2026-07-16T10:00:00Z",
  "producer": "audit-service",
  "aggregateId": "contract-or-milestone-id",
  "correlationId": "uuid",
  "causationId": null,
  "payload": {
    "contractId": "uuid",
    "milestoneId": "uuid",
    "recipients": [{"userId": "uuid", "email": "seller@example.test", "role": "SELLER"}],
    "recordHash": "64-char-sha256-hex",
    "otsProof": "base64",
    "settlementSummary": {}
  }
}
```

- `eventVersion`, `producer`, `aggregateId`, `correlationId` là required; `causationId` là nullable nhưng nếu gửi thì phải đúng schema.
- Email nằm trong `payload.recipients` như routing data nội bộ, theo mô hình Phase 1 đã chốt.
- Owner service lấy email từ user-service hoặc local profile trước khi publish; notification-service không Feign ngược.
- Một event có thể có nhiều recipients; mỗi recipient được dedup/gửi độc lập.

### 2.2 Internal synchronous API — chỉ cho OTP

`POST /internal/v1/notifications/otp-email` chỉ nhận service-to-service call từ contract-service:

```json
{
  "requestId": "otpId",
  "recipientUserId": "uuid",
  "recipientEmail": "...",
  "contractId": "uuid",
  "otpCode": "123456",
  "signedContentHash": "sha256-hex",
  "expiresAt": "..."
}
```

- API trả `200 OK` chỉ khi mail provider đã nhận request gửi; lỗi provider trả 5xx để contract-service không nói sai “OTP đã gửi”.
- Không route endpoint này qua API Gateway.
- Notification không lưu/ghi log plaintext OTP; body được render trong memory. Log chỉ giữ `requestId`, template, recipient, status và hash/reference an toàn.
- Contract-service vẫn sở hữu OTP hash, attempt count, expiry và verify logic.

## 3. Domain Model

### 3.1 `NotificationLog`

| Field | Loại | Ghi chú |
|---|---|---|
| `notificationId` | UUID | Identity nội bộ |
| `eventId` | VARCHAR | Rabbit eventId hoặc OTP requestId |
| `eventType` | VARCHAR | Routing/audit |
| `notificationType` | VARCHAR | Template semantic, không đồng nhất bắt buộc với eventType |
| `recipientUserId` | VARCHAR nullable | Có thể null cho Software Buyer distribution list |
| `recipientEmail` | VARCHAR | Destination |
| `channel` | `EMAIL` | Phase 2 golden flow chỉ bắt buộc email |
| `templateVersion` | VARCHAR | Bằng chứng email nào đã được render |
| `status` | `PENDING \| SENT \| FAILED` | State machine |
| `retryCount` | INT | Tối đa 3 cho async flow |
| `providerMessageId` | VARCHAR nullable | Đối soát provider |
| `failureReason` | TEXT nullable | Không chứa secret/OTP |
| `sentAt` | TIMESTAMP nullable | Provider accepted time |

Unique key: `(event_id, recipient_email, notification_type)`. Key cũ `(event_id, user_id)` không đủ vì một event có thể gửi nhiều loại mail cho cùng người hoặc nhiều người không có platform userId.

### 3.2 Template

Template được version trong code/resources để review cùng source; DB chỉ lưu `templateVersion` đã dùng. Không cho Admin sửa nội dung evidence/security template runtime vì sẽ làm mất khả năng tái hiện bằng chứng.

### 3.3 Attachment cho evidence mail (mới, 17/07/2026)

2 luồng evidence cần nhiều hơn text thuần, trước đây chưa chốt:

- `notification.milestone_anchor_requested` — email quyết toán **đính file `.ots`** làm attachment: proof là binary, publisher (audit-service) truyền base64 trong payload (`otsProof`), notification decode và attach, tên file `{contractId}-{milestoneId}.ots`.
- `notification.contract_anchor_requested` — snapshot `ContractTerms` render **thẳng trong body email** (HTML/JSON có cấu trúc) kèm dòng `signedContentHash`. **Không sinh PDF trong Phase 2** — email anchor cần nội dung + timestamp độc lập, không cần định dạng in ấn; PDF generation là enhancement.

`NotificationLog` không lưu attachment bytes — chỉ lưu size + SHA-256 của attachment trong metadata để đối soát; bytes tái hiện được từ nguồn (audit-service giữ proof trong `audit_anchor.proof` — hash-chain §3, sửa 18/07/2026).

## 4. Notification Command → Template → Recipient Matrix

| Routing key / publisher | Recipient | Notification/payload bắt buộc |
|---|---|---|
| `notification.user_kyc_result_requested` / user-service | User tương ứng | Kết quả KYC, reason nếu reject |
| `notification.user_lock_changed_requested` / user-service | User bị tác động | LOCKED/UNLOCKED, thời hạn/lý do |
| `notification.contract_anchor_requested` / contract-service | Buyer + Seller | Snapshot `ContractTerms` + `signedContentHash` + contractId |
| `notification.contract_terminated_requested` / contract-service (**SỬA 19/07/2026 — thay `contract_cancelled_requested`**) | Buyer + Seller | Canonical lifecycle fields (`contractId`, `terminationType`, `requestedBy`, nullable attribution/reason/decision/case fields, affected milestones and replacement pointers) plus `remedyLegs` and `payload.recipients`. Template PHẢI phân biệt: mutual/replacement/FM/technical (không ai vi phạm — giọng trung tính) vs breach (nêu bên vi phạm theo attribution). KHÔNG dùng chữ "huỷ do X" khi X chỉ là requestedBy |
| `notification.breach_notice_requested` / contract-service (**mới 19/07/2026**) | Bên bị cáo buộc + bên cáo buộc | `breachCaseId`, `breachReasonCode`, severity, evidence refs — thông báo `BreachCase` mở (milestone-escrow §6.4); là notice chính thức có timestamp, đầu chuỗi bằng chứng wrongful-termination |
| `notification.remedy_finalized_requested` / contract-service (**mới 19/07/2026; quality sync 23/07/2026**) | Buyer + Seller | Canonical remedy fields gồm `remedyDecisionId`, `attributionDecisionId`, nullable `breachCaseId`, `contractId`, `buyerId`, `sellerId`, `affectedMilestoneIds[]`, nullable final attribution/reason, nullable `qualityDisposition`, eligibility flags và exact `remedyLegs`; quality resolution bắt buộc disposition non-null và template hiển thị nó cùng final attribution, không suy blame từ `requestedBy`/`flaggedBy`; command chỉ bổ sung `payload.recipients` cho routing |
| `notification.milestone_funding_status_requested` / contract-service (**mới 19/07/2026**) | Buyer (nhắc nạp) / Seller (báo tạm dừng đồng hồ) / cả 2 + Admin khi FUNDING_FAILED | statusType: `FUNDING_REMINDER` \| `FUNDING_FAILED` \| `FUNDING_CURE_DEADLINE` (mail actionable có deadline `fundingCureWindowDays`) — milestone-escrow §6b |
| `notification.milestone_status_requested` / contract-service | Buyer/Seller/Admin theo status | weighed, confirmed, flagged, force-majeure, penalty hoặc provisional Level 2 |
| `notification.milestone_anchor_requested` / audit-service | Buyer + Seller | Quyết toán + `recordHash` + `.ots` proof; chỉ publish sau khi audit record/OTS sẵn sàng |
| `notification.level2_commission_requested` / inspection-service | Org + Buyer + Seller | commissionId, org, intake address, contract context |
| `notification.audit_digest_requested` / audit-service | Software Buyer contacts | Weekly digest, chỉ sau WEEKLY_VERIFY_OK |
| `notification.audit_failure_requested` / audit-service | Admin + **nhiều** Software Buyer contacts song song | Vị trí lỗi/anchored hash; không qua Admin approval |
| `notification.verifier_key_anchor_requested` / bank-service | External Verifier contacts | Public-key fingerprint |
| `notification.security_lock_changed_requested` / bank-service | Admin + External Verifier | LOCKED/UNLOCKED, reason/timestamp; không fan-out buyer/seller |
| `notification.contract_activation_failed_requested` / contract-service (**mới 17/07/2026**) | Buyer + Seller + Admin | Hợp đồng đã ký nhưng khoá cọc fail sau retry (milestone-escrow §3.1) — không penalty, nêu bước tiếp theo (Admin retry / xác nhận không tiếp tục) |
| `notification.reconciliation_mismatch_requested` / bank-service (**mới 18/07/2026**) | Admin only | Ledger lệch số với audit record đã anchor (bank §5b.1) — vị trí lệch (contractId/milestoneId, chênh lệch); không fan-out buyer/seller, không auto-fix |

Các domain event (`contract.signed`, `milestone.settled`...) vẫn giữ payload nghiệp vụ canonical ở owner design. Owner/audit consumer publish notification command tương ứng; notification-service không subscribe trực tiếp domain event nếu payload đó không đủ template/recipient. `contract.delivered` là dead event Phase 1 và phải xoá listener/template.

Buyer/seller chỉ được báo tampering sau khi Admin khoanh vùng đúng contract bị ảnh hưởng; flow này là notification command riêng có người xác nhận nội dung, không tự fan-out từ global verify failure.

**Làm rõ template `notification.milestone_status_requested` (17/07/2026):** payload có field `statusType`; mỗi `statusType` map 1 `notificationType`/template version riêng để dedup key hoạt động đúng. Ngoài các trạng thái đã liệt kê, 2 template bắt buộc dễ sót khi implement: `LEVEL2_OPTOUT_REQUESTED` — hỏi buyer chọn mức release provisional, **mail actionable có deadline `disputeOptOutWindowDays`** (milestone-escrow §3.2 Bước 0, buyer im lặng → default sàn); và `BUYER_RECEIVE_REMINDER` — nhắc buyer cân nhận hàng khi hết `buyerReceiveWindowDays` (milestone-escrow §3.2).

## 5. Processing, Retry & Idempotency

```text
receive → validate → reserve unique key/PENDING → render → provider send
                                                ├─ accepted → SENT
                                                └─ temporary failure → retry (3 lần) → DLQ/FAILED
```

- Insert/reserve unique key trước khi gửi để hai consumer không gửi song song cùng mail.
- Duplicate khi row `SENT` → ack/skip. Row `PENDING/FAILED` chỉ retry theo policy, không tạo row mới.
- Retry async tối đa 3 lần với exponential backoff; permanent validation error đi DLQ ngay.
- OTP synchronous không retry nền sau khi đã trả lỗi cho caller, tránh caller tạo OTP mới trong khi OTP cũ bất ngờ được gửi muộn. Caller có thể retry cùng `requestId`; idempotency trả lại kết quả đã biết.
- DLQ security/evidence tạo operational alert; không silently discard.

## 6. Database Migration

Migration Phase 2 thay unique key và bổ sung metadata:

```sql
ALTER TABLE notification_logs
  ADD COLUMN event_type VARCHAR(100) NOT NULL DEFAULT 'LEGACY_P1',
  ADD COLUMN notification_type VARCHAR(100) NOT NULL DEFAULT 'LEGACY_P1',
  ADD COLUMN recipient_email VARCHAR(255) NOT NULL DEFAULT '',
  ADD COLUMN template_version VARCHAR(50) NOT NULL DEFAULT 'p1-unknown',
  ADD COLUMN provider_message_id VARCHAR(255) NULL,
  ADD COLUMN failure_reason TEXT NULL,
  ADD COLUMN sent_at DATETIME(6) NULL;

-- Backfill TRƯỚC khi đổi unique key — bắt buộc, không phải tuỳ chọn: các row Phase 1
-- cùng event_id (key cũ phân biệt bằng user_id, key mới bằng recipient_email) sẽ va nhau
-- ngay lúc CREATE UNIQUE INDEX nếu recipient_email còn sentinel '' giống nhau:
UPDATE notification_logs SET recipient_email = CONCAT('legacy:', user_id)
  WHERE recipient_email = '';

ALTER TABLE notification_logs DROP INDEX uq_event_user;
CREATE UNIQUE INDEX uq_event_recipient_type
  ON notification_logs(event_id, recipient_email, notification_type);
```

Khi migrate, cột Phase 1 `user_id` đổi semantics thành `recipient_user_id` hoặc được giữ tạm rồi backfill; migration implementation phải không làm mất log cũ. **Bổ sung (17/07/2026):** cột `NOT NULL` mới mang `DEFAULT` sentinel (`LEGACY_P1`/`''`/`p1-unknown`) để `ALTER` chạy được trên bảng có data mà không phụ thuộc SQL mode; `recipient_email` backfill `legacy:{user_id}` **trước** khi tạo unique index mới (tránh va chạm các row cùng `event_id`). Log cũ là read-only audit — `notification_type = 'LEGACY_P1'` không trùng với type mới nên không tham gia dedup của Phase 2.

## 7. Configuration

| Config | Default | Ghi chú |
|---|---:|---|
| `notification.retry.maxAttempts` | 3 | Async events |
| `notification.retry.initialDelaySeconds` | 5 | Exponential backoff |
| `notification.softwareBuyerContacts` | ≥2 địa chỉ | Integrity alerts |
| `notification.adminEmail` | deployment value | Không hardcode production |
| `notification.externalVerifierContacts` | deployment value | Key/lock confirmation |

SMTP/SendGrid credential chỉ từ secret/env. MailHog dùng local development.

## 8. Security & Evidence Rules

- Không log OTP, full email body chứa OTP, access token hoặc attachment nhạy cảm.
- Snapshot/hash/OTS nhận từ owner service; notification không tự tính lại rồi tạo nguồn sự thật thứ hai.
- Evidence template immutable theo version; log giữ provider ID và sent time.
- Identity/recipient email do publisher xác định; notification validate format nhưng không được thay recipient theo input Admin tùy ý cho evidence mail.
- Verify failure fan-out không có gatekeeper đơn lẻ.

## 9. Known Limitations / Out of Scope

- In-app notification/WebSocket là enhancement; Phase 2 bắt buộc email + persisted log. Không có end-user API đọc log trong Phase 2 — Gateway đã bỏ route `/api/v1/notifications/**` (17/07/2026); nếu sau này expose, thêm endpoint + route cùng lúc.
- Provider accepted không chứng minh người nhận đã đọc; email anchor chứng minh bản đã rời platform và có timestamp độc lập.
- Bounce/complaint reconciliation nâng cao ngoài golden flow; providerMessageId giữ đường mở rộng.
- Event payload mang email tạo PII trong broker; chấp nhận có chủ đích cho scope, hạn chế bằng private network, access control và retention broker.

## 10. Status — Notification-service Design

**Chốt:** event-driven cho notification nghiệp vụ/evidence; internal sync API chỉ cho OTP. Recipient email đi trong payload, không Feign ngược. Dedup theo event + recipient + notification type. Retry 3 lần + DLQ; OTP không gửi muộn sau khi caller đã nhận failure. `contract.delivered` bị loại bỏ.

**Cập nhật 19/07/2026 (quality sync 23/07/2026):** `contract_cancelled_requested` → `contract_terminated_requested` (template theo `terminationType`/`finalBreachingRole`, không theo initiatedBy); thêm 3 command mới: `breach_notice_requested`, `remedy_finalized_requested`, `milestone_funding_status_requested`. Quality resolution qua `remedy_finalized_requested` mang `qualityDisposition` cùng final attribution và không suy blame từ requester. Dedup key `(event_id, recipient_email, notification_type)` sẵn có cover được — mỗi statusType/terminationType map 1 notificationType riêng, cùng quy tắc đã chốt cho `milestone_status`.

**Cập nhật 17/07/2026:** thêm command `notification.contract_activation_failed_requested` (khoá cọc fail sau `SIGNED` — milestone-escrow §3.1); làm rõ 2 template dễ sót của `milestone_status` (`LEVEL2_OPTOUT_REQUESTED` có deadline, `BUYER_RECEIVE_REMINDER`); §3.3 chốt attachment `.ots` + snapshot render trong body, không PDF Phase 2; migration thêm DEFAULT sentinel + backfill `legacy:{user_id}` trước khi tạo unique index mới; xác nhận không có end-user API (Gateway bỏ route).

---

*Design bổ sung: 16/07/2026 · Rà soát cross-service + sửa: 17/07/2026 · Cập nhật 19/07/2026 (đợt 6 — terminated/breach/remedy/funding commands) · Chưa code · Sẵn sàng đồng bộ vào SDS/Architecture ở lần regenerate kế tiếp.*
