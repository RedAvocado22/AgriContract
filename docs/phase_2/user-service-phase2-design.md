---
name: user-service-phase2-design
description: "User-service Phase 2 — profile tổ chức, KYC/thẩm quyền đại diện, cache trạng thái khoá và tách public/internal DTO."
status: DESIGNED — chưa code.
metadata:
  type: design
  phase: 2
  extends: "user-service Phase 1; AgriContract Architecture v2 §3.1; SDS Phần 5 §2"
  related: "signature-phase2-design.md §5; reputation-service-phase2-design.md §6; file-service-phase2-design.md §6"
---

## 1. Bối cảnh & Scope

Phase 1 có `UserProfile` tối giản (`organizationName`, `role`, contact info, `verificationStatus`) và ba API đăng ký/đọc profile. Phase 2 giữ `user-service` là cầu nối profile với Keycloak, đồng thời bổ sung hai trách nhiệm đã được các design khác yêu cầu nhưng chưa có owner document:

1. Xác minh pháp nhân và thẩm quyền đại diện của Buyer/Seller trước khi họ tham gia giao dịch có ràng buộc.
2. Enforce trạng thái khoá do `reputation-service` quyết định, không tự tính điểm hay thời hạn khoá.

`user-service` **không** lưu password/token, không ký hợp đồng và không sở hữu `Signature`. Keycloak sở hữu credential; `contract-service` sở hữu `Signature`, `signature_otp`, `signedContentHash` và transition `SIGNED`; `reputation-service` sở hữu lock ledger và công thức `lockDurationDays`.

## 2. Domain Model — `UserProfile`

| Field | Loại | Ghi chú |
|---|---|---|
| `userId` | String/UUID | Keycloak `sub`, identity ổn định |
| `organizationName` | VARCHAR | Tên pháp nhân/HTX |
| `role` | `BUYER \| SELLER \| ADMIN \| INSPECTOR` | Role nguồn từ Keycloak; Phase 1 cần mở enum cho INSPECTOR |
| `contactInfo` | VO | Email, phone, address; dữ liệu riêng tư |
| `verificationStatus` | `PENDING \| VERIFIED \| REJECTED` | State KYC hiện tại |
| `authorizationExpiresAt` | TIMESTAMP nullable | Ngày hết hạn trên giấy uỷ quyền; `NULL` chỉ khi giấy ghi vô thời hạn |
| `lockedUntil` | TIMESTAMP nullable | Projection/cache từ reputation events; không phải source of truth |
| `verifiedByAdminId` | UUID nullable | Admin thực hiện quyết định gần nhất |
| `verifiedAt` | TIMESTAMP nullable | Thời điểm verify |
| `rejectionReason` | TEXT nullable | Bắt buộc khi reject, xoá khi hồ sơ được nộp lại |

### 2.1 State machine KYC

```text
REGISTER → PENDING ──verify──▶ VERIFIED
                 └─reject───▶ REJECTED ──resubmit──▶ PENDING
```

- `verify()` chỉ chạy từ `PENDING`; ghi Admin, thời điểm và `authorizationExpiresAt` đúng theo giấy tờ thật.
- `reject(reason)` chỉ chạy từ `PENDING`; `reason` không được rỗng.
- `resubmit()` chạy từ `REJECTED`, đưa về `PENDING`; file hồ sơ nằm ở file-service, user-service chỉ giữ reference/metadata cần thiết khi implementation chốt form.
- `authorizationExpiresAt <= now()` làm thẩm quyền **không còn hợp lệ** nhưng không tự đổi `verificationStatus`; giữ lịch sử quyết định KYC, còn gate runtime kiểm cả hai điều kiện.

### 2.2 Lock projection

`lockedUntil` là bản sao trạng thái phục vụ enforce nhanh. `reputation-service` vẫn là nguồn quyết định duy nhất:

- `reputation.locked {eventId, userId, lockedUntil}` → set `lockedUntil = max(current, incoming)`.
- `reputation.unlocked {eventId, userId, unlockedAt}` → clear nếu event mới hơn quyết định lock đang áp dụng.
- Consumer lưu `eventId` vào idempotency log; RabbitMQ redelivery không được apply hai lần.
- Sau khi apply thành công, user-service publish notification command đã enrich contact info (`notification.user_lock_changed_requested`); reputation-service không cần và không được gọi ngược để lấy email.
- Reconciliation endpoint nội bộ của reputation-service là fallback vận hành, không được gọi trên mọi request nghiệp vụ.

## 3. Enforcement Matrix

| Hành động | KYC/thẩm quyền | Reputation lock | Failure policy |
|---|---|---|---|
| Xem public listing/price/reputation | Không gate | Không gate | N/A |
| Tạo listing | `VERIFIED` và authorization còn hạn | Không cho nếu đang khoá | Fail-open nếu check đồng bộ user-service tạm lỗi ở caller, nhưng caller phải log/metric |
| Tạo offer | `VERIFIED` và authorization còn hạn | Không cho nếu đang khoá | Cùng chính sách create listing |
| Ký hợp đồng | `VERIFIED` và authorization còn hạn | Không cho nếu đang khoá | **Fail-closed**; circuit breaker fallback phải reject |
| Admin/Inspector workflow | Role + ownership riêng | Không dùng seller/buyer lock | Enforce ở service sở hữu use case |

Gate nằm tại use-case của `product-service`/`contract-service`, không nằm ở Gateway. Gateway chỉ xác thực JWT và truyền identity.

## 4. API & DTO Boundary

### 4.1 End-user API — qua Gateway

| Method | Path | Quyền | Response/ý nghĩa |
|---|---|---|---|
| `POST` | `/api/v1/users/register` | Authenticated | Idempotent theo Keycloak `sub`; tạo `PENDING` nếu mới |
| `GET` | `/api/v1/users/me` | Chính chủ | `UserProfileResponse` đầy đủ contact/KYC/authorization/lock |
| `GET` | `/api/v1/users/{userId}` | Authenticated | `PublicUserResponse`: `userId`, `organizationName`, `role`, `verificationStatus`; **không email/phone/address** |

Reputation công khai không nhét vào DTO này; client gọi `GET /api/v1/reputation/{userId}/public-summary` ở service sở hữu.

### 4.2 Admin KYC API — qua Gateway, role ADMIN

| Method | Path | Ý nghĩa |
|---|---|---|
| `GET` | `/api/v1/admin/users?verificationStatus=PENDING` | Hàng đợi duyệt |
| `POST` | `/api/v1/admin/users/{userId}/verify` | Body chứa `authorizationExpiresAt` nullable theo giấy thật |
| `POST` | `/api/v1/admin/users/{userId}/reject` | Body chứa `reason` bắt buộc |

Mọi quyết định ghi audit metadata; việc đưa quyết định KYC vào hash chain là enhancement, không phải Phase 2 golden flow.

### 4.3 Internal API — tuyệt đối không route qua Gateway

`GET /internal/v1/users/{userId}` trả `InternalUserInfoResponse`:

```json
{
  "userId": "...",
  "organizationName": "...",
  "email": "...",
  "role": "BUYER",
  "verificationStatus": "VERIFIED",
  "authorizationExpiresAt": "2027-06-30T00:00:00Z",
  "lockedUntil": null
}
```

Chỉ service identity (mTLS/service token; Phase 2 có thể dùng `SERVICE_INTERNAL_SECRET`) được gọi. Không tái dùng `UserInfoResponse` public cho Feign vì đó là nguyên nhân KI-3.

## 5. Events

| Event | Publisher | Consumer | Payload tối thiểu |
|---|---|---|---|
| `notification.user_kyc_result_requested` | user-service | notification-service | `{eventId, userId, recipientEmail, organizationName, result: VERIFIED\|REJECTED, reason?, decidedAt}` |
| `reputation.locked` | reputation-service | user-service | `{eventId, userId, lockedUntil, reasonCode}`; không mang email |
| `reputation.unlocked` | reputation-service | user-service | `{eventId, userId, unlockedAt, reasonCode}`; không mang email |
| `notification.user_lock_changed_requested` | user-service | notification-service | `{eventId, sourceEventId, userId, recipientEmail, action: LOCKED\|UNLOCKED, effectiveUntil?, reasonCode}` |

Email trong event là routing data nội bộ RabbitMQ, không được expose qua API/log plaintext ngoài nhu cầu vận hành.

## 6. Database Migration

Migration additive trên `user_db`:

```sql
ALTER TABLE user_profiles
  MODIFY role ENUM('SELLER','BUYER','ADMIN','INSPECTOR') NOT NULL,
  ADD COLUMN authorization_expires_at DATETIME(6) NULL,
  ADD COLUMN locked_until DATETIME(6) NULL,
  ADD COLUMN verified_by_admin_id VARCHAR(255) NULL,
  ADD COLUMN verified_at DATETIME(6) NULL,
  ADD COLUMN rejection_reason TEXT NULL;

CREATE TABLE user_event_idempotency (
  event_id VARCHAR(255) PRIMARY KEY,
  processed_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
);
```

Không thêm credential, password hoặc Keycloak token vào `user_db`.

## 7. Security & Failure Modes

- Downstream chỉ tin identity headers khi có gateway/internal secret hợp lệ; client không được tự tiêm header.
- `/users/{userId}` không trả contact info. Chính chủ dùng `/me`; service dùng `/internal`.
- `sign()` fail-closed nếu user-service unavailable hoặc response không xác định được verification/lock state.
- Event lock/unlock xử lý idempotent và theo ordering metadata, tránh event cũ mở khoá quyết định mới.
- Admin không được tự đặt `authorizationExpiresAt` theo hằng số platform; phải đọc từ giấy thật, `NULL` chỉ cho vô thời hạn.

## 8. Known Limitations / Out of Scope

- Checklist giấy tờ chi tiết theo từng loại hình pháp nhân cần legal review khi làm form thật.
- Tự động đối soát giấy phép/đăng ký doanh nghiệp với cơ sở dữ liệu Nhà nước ngoài scope; Admin duyệt thủ công.
- `lockedUntil` là eventually consistent projection; cửa sổ trễ event nhỏ vẫn tồn tại. Hành động rủi ro cao `sign()` có sync check/fail-closed để thu hẹp.
- mTLS là hướng production; scope đồ án có thể dùng internal secret trong network cô lập.

## 9. Status — User-service Design

**Chốt:** user-service sở hữu profile/KYC/authorization và cache lock; Keycloak sở hữu credential; contract-service sở hữu Signature. Public/internal DTO tách hẳn để đóng KI-3. Enforcement ở use-case, không ở Gateway; `sign()` fail-closed. Events KYC và lock đủ dữ liệu cho notification nhưng không làm notification gọi sync ngược.

---

*Design bổ sung: 16/07/2026 · Chưa code · Sẵn sàng đồng bộ vào SDS/Architecture ở lần regenerate kế tiếp.*
