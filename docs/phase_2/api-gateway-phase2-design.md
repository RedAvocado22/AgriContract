---
name: api-gateway-phase2-design
description: "API Gateway Phase 2 — route/access matrix cho 12 services, JWT/header trust boundary, internal endpoint isolation và edge failure policy."
status: DESIGNED — chưa code.
metadata:
  type: design
  phase: 2
  extends: "api-gateway Phase 1; AgriContract Architecture v2 §1/§8/§9"
  related: "user-service-phase2-design.md §4; file-service-phase2-design.md §6; bank-service-phase2-design.md §3.5"
---

## 1. Bối cảnh & Scope

Phase 1 Gateway route 5 service và `SecurityConfig` đang `permitAll`; filter tự quyết định hai public prefix rồi inject identity headers. Phase 2 có 12 business services, thêm public reference APIs, Admin/Inspector workflows, internal-only APIs và External Verifier security endpoints. Nếu chỉ thêm route mà không chốt access matrix, endpoint nội bộ hoặc dữ liệu PII rất dễ bị expose.

Gateway chịu trách nhiệm:

- Validate JWT và phân loại route coarse-grained.
- Strip mọi identity header từ client rồi inject identity đã xác thực.
- Route tới đúng service, áp timeout/request-size/rate policy ở edge.
- Chặn `/internal/**` và service ports khỏi external traffic.

Gateway **không** chịu trách nhiệm ownership, KYC, reputation lock, contract state, file business access hoặc quyết định tiền.

## 2. Authentication & Header Trust Boundary

```text
Client → Nginx → API Gateway (verify JWT/JWKS) → downstream service
```

Với authenticated request, Gateway xoá các header client có thể giả mạo rồi inject lại:

| Header | Nguồn |
|---|---|
| `X-User-Id` | JWT `sub` |
| `X-User-Email` | JWT `email` |
| `X-User-Role` | Keycloak `realm_access.roles`, chỉ giữ role ứng dụng |
| `X-Correlation-Id` | Giữ UUID hợp lệ từ client hoặc sinh mới |
| `X-Gateway-Secret` | Secret deployment, không nhận giá trị client |

Gateway xoá `Authorization` trước khi chuyển xuống service theo pattern Phase 1; downstream tin headers chỉ khi gateway secret hợp lệ. Service-to-service dùng network nội bộ + `SERVICE_INTERNAL_SECRET`, tiến hoá mTLS.

Public request không được inject identity giả. Nếu JWT optional nhưng hợp lệ, Gateway có thể inject context; JWT sai/expired luôn trả `401`, không hạ xuống anonymous.

## 3. Route & Access Matrix

### 3.1 Public — không cần JWT

| Path | Destination | Policy |
|---|---|---|
| `GET /api/v1/listings/**` | product-service | Public read only |
| `GET /api/v1/products/**` | product-service | Chỉ endpoint catalog/listing-public được permit; `/products/mine` không match rule public cụ thể |
| `GET /api/v1/prices/**` | pricing-service | Reference data |
| `GET /api/v1/reputation/{userId}/public-summary` | reputation-service | Public partner summary |
| `GET /api/v1/security/audit-hash` | bank-service | API key + rate limit; hash read-only, theo bank-service §3.5.1 |
| `/actuator/health`, `/actuator/info` | Gateway | Public operational probe; không expose env/config |

Public policy match theo **method + exact path pattern**, không dùng prefix rộng như Phase 1 (`startsWith("/products")`).

### 3.2 Authenticated Buyer/Seller/common

| Prefix | Destination | Ghi chú |
|---|---|---|
| `/api/v1/users/**` | user-service | `/me`, register, safe public user DTO |
| `/api/v1/products/**`, `/api/v1/listings/**`, `/api/v1/plots/**` | product-service | Write/owned reads authenticated |
| `/api/v1/contracts/**`, `/api/v1/milestones/**` | contract-service | Ownership/state ở contract-service |
| `/api/v1/escrows/**` | escrow-service | Chỉ participant-owned read/commands đã expose |
| `/api/v1/inspections/**` | inspection-service | Role/assignment ở service |
| `/api/v1/reputation/**` | reputation-service | Credit export cần consent/authorization ở service |
| `/api/v1/notifications/**` | notification-service | Chỉ persisted log của chính user nếu Phase 2 expose; không có send-generic API |

### 3.3 Role-gated

| Path | Role tại Gateway | Destination |
|---|---|---|
| `/api/v1/admin/users/**` | ADMIN | user-service |
| `/api/v1/admin/categories/**`, `/api/v1/admin/products/**` | ADMIN | product-service |
| `/api/v1/admin/inspections/**` | ADMIN | inspection-service |
| `/api/v1/admin/reputation/**` | ADMIN | reputation-service |
| `/api/v1/admin/prices/**` hoặc `POST /api/v1/prices/manual` | ADMIN | pricing-service |
| `/api/v1/admin/analytics/**`, `/api/v1/analytics/**` | ADMIN | analytics-service |
| `/api/v1/admin/security/**` | ADMIN | audit/bank read/admin workflow; **không** được emergency unlock |
| `/api/v1/inspector/**` | INSPECTOR | inspection-service |

Gateway role gate là lớp coarse-grained; service vẫn kiểm tra role + resource assignment.

### 3.4 External Verifier

| Path | Authentication | Destination |
|---|---|---|
| `POST /api/v1/security/emergency-lock` | Asymmetric signature, timestamp + nonce/replay guard | bank-service |
| `POST /api/v1/security/emergency-unlock` | Mirror asymmetric verification; không Admin bypass | bank-service |

Hai endpoint này không dùng JWT user flow và không bị filter ép `X-User-*`. Gateway preserve raw signed body/required signature headers, áp size cap nhỏ và không retry. Verification mật mã cuối cùng nằm ở bank-service.

### 3.5 Internal-only — không có external route

- `/internal/v1/users/**`
- `/internal/v1/notifications/**`
- File-service raw upload/download/attach APIs dành cho owning service.
- Bank ledger command APIs/events.
- Service reconciliation, outbox poller và webhook nội bộ không được liệt kê public.

Request external có path `/internal/**` trả `404` hoặc `403` ngay tại Gateway; không forward.

## 4. Authorization Ownership

| Concern | Owner |
|---|---|
| JWT validity, coarse role, public/private route | Gateway |
| User ownership/PII | user-service |
| KYC/authorization expiry | user-service + caller use-case |
| Listing/contract participant ownership | product/contract-service |
| Reputation lock | user projection + caller use-case; không Gateway |
| File access | Service sở hữu `fileId`, proxy bytes từ file-service |
| Emergency signature/replay | bank-service |

Không gọi user-service từ `UserContextInjectionFilter`; tránh biến mọi request thành dependency đồng bộ và tránh cascading failure.

## 5. Edge Policies

### 5.1 Timeout & retry

- GET idempotent: timeout rõ ràng; chỉ retry khi route cho phép và lỗi kết nối xảy ra trước response.
- POST/PUT/PATCH/DELETE: Gateway không tự retry nếu không có idempotency key được service định nghĩa.
- Upload/download dùng timeout riêng, không dùng timeout ngắn của JSON API.
- Circuit breaker ở Gateway chỉ bảo vệ tài nguyên/fast-fail; fallback không được giả response nghiệp vụ thành công.

### 5.2 Request size

- JSON API default cap nhỏ (đề xuất 1 MB).
- Direct upload cap 10 MB và email intake 20 MB được file-service enforce streaming theo owner design; Gateway chỉ cho upload qua business endpoint đã uỷ quyền, không expose raw file-service.
- Emergency lock/unlock cap nhỏ (đề xuất 64 KB) để giữ signature verification deterministic.

### 5.3 Rate limit

- Nginx giữ coarse per-IP rate limit.
- Gateway/Redis áp route bucket cho public pricing/reputation/audit-hash và auth endpoints.
- OTP resend/attempt business limits nằm ở contract-service theo `signature-phase2-design.md`; Gateway chỉ chống flood thô, không thay counter nghiệp vụ.

### 5.4 CORS & observability

- CORS allowlist từ config deployment, không wildcard kèm credential.
- Mọi response có correlation ID; log path template, status, latency và destination, không log Authorization/OTP/email body.
- Chỉ expose `health/info`; metrics cần network/admin protection.

## 6. Configuration Delta

`application.yml` thêm route URI/env cho:

```text
BANK_SERVICE_URL
INSPECTION_SERVICE_URL
REPUTATION_SERVICE_URL
FILE_SERVICE_URL (internal route only, normally no external predicate)
PRICING_SERVICE_URL
AUDIT_SERVICE_URL
ANALYTICS_SERVICE_URL
```

Route definitions phải có order rõ: exact public/role routes trước wildcard authenticated routes; `/internal/**` deny trước tất cả.

`SecurityConfig` chuyển từ `anyExchange().permitAll()` sang explicit public permit + authenticated default. Role-specific path dùng Keycloak application roles, bỏ qua các realm management roles không thuộc enum ứng dụng.

## 7. Failure & Security Cases

- JWT thiếu/sai/expired trên protected route → `401`.
- JWT hợp lệ nhưng thiếu role yêu cầu → `403`.
- Client tự gửi `X-User-Id`, `X-User-Role`, internal secret → bị strip/overwrite.
- Unknown/unrouted path → `404`, không fallback tới service gần nhất.
- Downstream timeout → `504/503` với correlation ID; không fabricate success.
- Gateway không có emergency unlock path cho Admin; chữ ký External Verifier là đường duy nhất.

## 8. Known Limitations / Out of Scope

- Internal secret trong shared network yếu hơn mTLS; mTLS là production evolution.
- Gateway không phải WAF; Nginx/Cloudflare là lớp DDoS/bot nâng cấp.
- Route matrix cần cập nhật khi endpoint implementation cuối cùng đổi tên; test route contract bắt buộc ngăn drift.
- Centralized fine-grained authorization bị loại có chủ đích; domain service giữ ownership/state logic.

## 9. Status — API Gateway Design

**Chốt:** Gateway là edge authentication/routing boundary, không phải business authorization service. Public match theo method + exact pattern; authenticated default; internal APIs không route. Identity headers luôn strip rồi inject. External Verifier lock/unlock giữ đường auth riêng, không Admin bypass và không automatic retry.

---

*Design bổ sung: 16/07/2026 · Chưa code · Sẵn sàng đồng bộ vào SDS/Architecture ở lần regenerate kế tiếp.*
