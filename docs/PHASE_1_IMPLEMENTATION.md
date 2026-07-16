# Phase 1 — Tài liệu triển khai hiện tại

> Baseline: code trên `main`, cập nhật ngày 17/07/2026. Tài liệu này mô tả hành vi đang có trong code. Khi tài liệu thiết kế Phase 1 cũ mâu thuẫn với file này, file này là nguồn tham chiếu ưu tiên.

## 1. Phạm vi đã triển khai

Phase 1 gồm một React SPA, sáu Java service, năm MySQL database, Keycloak, RabbitMQ và MailHog. Luồng thực tế đã bao phủ từ marketplace đến tất toán hoặc phân xử tranh chấp:

1. Seller tạo product và listing.
2. Buyer tạo offer từ listing đang `ACTIVE`.
3. Hai bên counter-offer; mỗi bản điều khoản được lưu theo revision.
4. Hai bên ký cùng revision; listing đóng và buyer payment được lock tự động.
5. Seller xác nhận cọc; escrow `FULLY_LOCKED`, contract `ACTIVE`.
6. Contract có thể bị hủy và áp penalty khi đang `ACTIVE`.
7. Buyer xác nhận nhận hàng; contract `DELIVERED`, escrow vào cửa sổ chờ tranh chấp.
8. Hết cửa sổ mà không có dispute: escrow release, contract `SETTLED`.
9. Có dispute: escrow giữ tiền, Admin phân chia, contract `SETTLED`, hai bên nhận email kết quả.

Phase 1 dùng số dư giả lập trong database, không kết nối ngân hàng và không chuyển tiền thật.

## 2. Thành phần và ownership

| Thành phần | Port trong container | Ownership |
|---|---:|---|
| frontend | 5173 | UI, Keycloak browser login, role-aware routing, API feedback |
| api-gateway | 8888 | Route `/api/v1/**`, xác thực JWT khi route yêu cầu, inject trusted headers |
| user-service | 8081 | `UserProfile`, thông tin tổ chức và verification status |
| product-service | 8082 | `Category`, `Product`, `Listing`, product images, product outbox |
| contract-service | 8083 | `Contract`, `ContractTerms`, negotiation revisions, contract outbox |
| escrow-service | 8084 | `EscrowAccount`, append-only escrow transactions, escrow outbox, delayed release |
| notification-service | 8085 | Notification log và email MailHog |

Database được tách theo service: `user_db`, `product_db`, `contract_db`, `escrow_db`, `notification_db`. Không có cross-database join.

## 3. Security và request trust

### 3.1 Public routes

Gateway và product-service cho phép không cần token đối với:

- `GET /api/v1/products`
- `GET /api/v1/listings`
- `GET /api/v1/listings/{listingId}`

`GET /api/v1/listings/seller` không phải public route. Security policy có matcher cho product item, nhưng `ProductController` hiện không implement `GET /products/{productId}`, vì vậy đây không phải endpoint công khai sử dụng được.

### 3.2 Authenticated routes

Các route còn lại cần Keycloak bearer token. Gateway validate RS256 JWT, lấy `sub`, `email`, `realm_access.roles`, bỏ header `Authorization` trước khi forward và inject:

- `X-User-Id`
- `X-User-Email`
- `X-User-Role`
- `X-Gateway-Secret`

Request trực tiếp vào downstream service không có gateway secret bị chặn, trừ public product/listing reads và actuator health/info. Feign giữa service dùng `X-Internal-Secret` riêng.

### 3.3 Frontend auth

Frontend dùng Keycloak Authorization Code + PKCE S256. Token nằm trong memory, được refresh qua `keycloak-js` và gắn vào request bằng Axios interceptor. User đã login nhưng chưa có profile sẽ được chuyển sang `/register-profile`.

## 4. Domain model và state machine

### 4.1 Product và listing

`Category` đi theo state machine:

```text
PENDING -> APPROVED
        -> REJECTED
```

- Seller hoặc Buyer có thể propose category.
- Admin approve/reject category; approve/reject phát event qua product outbox.
- Product chỉ được tạo với category `APPROVED`.
- Product cần từ 1 đến 5 image URL; có endpoint cập nhật images.
- Listing snapshot `productName` và `coverImageUrl` tại lúc tạo.
- Public listing API chỉ trả listing `ACTIVE`; seller có route riêng để xem listing của mình.

Docker/Gateway mặc định hiện route `/api/v1/products/**` và `/api/v1/listings/**`. Category administration được gọi trực tiếp vào product-service trong bộ Bruno, vì `/api/v1/categories/**` chưa được expose bởi gateway và service port chỉ được expose khi dùng `backend/docker-compose.override.yml`.

### 4.2 Contract

```text
OFFERED -> NEGOTIATING -> SIGNED -> ACTIVE -> DELIVERED -> SETTLED
    |          |                     |            |
    +----------+                     +-> CANCELLED +-> DISPUTED -> SETTLED
```

Quy tắc chính:

- Chỉ buyer của một listing đang `ACTIVE` mới tạo offer; contract snapshot buyer/seller org và email.
- Caller có thể gửi `contractId`. Gọi lại cùng ID và cùng buyer/listing/terms trả bản ghi cũ; payload khác trả `409`.
- `counterOffer()` chỉ chạy từ `OFFERED`/`NEGOTIATING`, tăng `termsRevision`, lưu revision mới và xóa chữ ký của revision cũ.
- Contract chỉ `SIGNED` khi buyer và seller đều ký revision hiện tại.
- `activate()` chỉ đến từ event `escrow.locked`.
- Chỉ buyer được `confirm-delivery`, từ `ACTIVE` sang `DELIVERED`.
- Buyer hoặc seller được cancel chỉ khi `ACTIVE`.
- Chỉ buyer được dispute khi `DELIVERED`.
- `escrow.released` settle luồng bình thường; `escrow.arbitrated` settle luồng tranh chấp.
- Admin được đọc contract và negotiation history; user khác buyer/seller bị `403`.

Negotiation history nằm ở bảng `contract_negotiations`, unique theo `(contract_id, terms_revision)`, và được lấy qua endpoint riêng thay vì nhúng toàn bộ vào `ContractResponse`.

### 4.3 Escrow

```text
BUYER_LOCKED -> FULLY_LOCKED -> DELIVERY_PENDING -> RELEASED
                    |                  |
                    |                  +-> DISPUTED -> ARBITRATED
                    +-> PENALIZED_BUYER
                    +-> PENALIZED_SELLER
                    +-> DISPUTED -> ARBITRATED
```

- `contract.signed` tạo một escrow unique theo `contractId`, lock amount trong `terms.agreedPrice` và chuyển `BUYER_LOCKED`.
- Seller gọi `confirm-deposit`; cọc seller bằng `totalAmount × sellerDepositRate`; escrow `FULLY_LOCKED` và phát `escrow.locked`.
- `contract.delivered` không release ngay. Escrow chuyển `DELIVERY_PENDING` và đặt `releaseEligibleAt = now + disputeWindow`.
- Cấu hình local mặc định: `DELIVERY_DISPUTE_WINDOW=PT30S`; scheduler poll mỗi `1000 ms`.
- Nếu `contract.disputed` tới trước release, escrow chuyển `DISPUTED` và xóa `releaseEligibleAt`.
- Scheduler và dispute cùng dựa trên optimistic locking để tránh release và hold đồng thời.
- Admin chỉ arbitrate escrow `DISPUTED`. `buyerAmount + sellerAmount` phải đúng bằng `totalAmount + sellerDeposit`, `justification` không được rỗng.
- Arbitration tạo hai ledger rows (`ARBITRATION_BUYER`, `ARBITRATION_SELLER`) và phát `escrow.arbitrated`.

Cancellation:

| Người hủy | Escrow result | Ledger effect |
|---|---|---|
| Buyer | `PENALIZED_BUYER` | Penalty `totalAmount × buyerPenaltyRate` cho seller, phần còn lại hoàn buyer, cọc hoàn seller |
| Seller | `PENALIZED_SELLER` | Buyer nhận lại toàn bộ payment; seller deposit là penalty cho buyer |

## 5. API hiện có

Tất cả URL dưới đây dùng prefix `/api/v1`.

### 5.1 User API

| Method | Path | Mô tả |
|---|---|---|
| POST | `/users/register` | Tạo profile từ trusted JWT headers; `201` nếu mới, `200` nếu đã tồn tại |
| GET | `/users/me` | Profile của user hiện tại |
| GET | `/users/{userId}` | Organization/role/email dùng cho inter-service snapshot |

### 5.2 Product API

| Method | Path | Access | Mô tả |
|---|---|---|---|
| GET | `/products` | Public | Danh sách product phân trang |
| POST | `/products` | SELLER | Tạo product bằng approved category và image URLs |
| PUT | `/products/{productId}/images` | SELLER | Thay danh sách 1–5 image URLs |
| GET | `/listings` | Public | Listing `ACTIVE` phân trang |
| GET | `/listings/{listingId}` | Public | Chi tiết listing |
| GET | `/listings/seller` | SELLER | Listing của seller hiện tại |
| POST | `/listings` | SELLER | Tạo listing |
| PUT | `/listings/{listingId}/close` | Internal | Contract-service đóng listing sau khi ký |
| POST | `/categories` | SELLER/BUYER | Propose category (product-service direct) |
| PUT | `/categories/{categoryId}/approve` | ADMIN | Approve category (product-service direct) |
| PUT | `/categories/{categoryId}/reject` | ADMIN | Reject category (product-service direct) |

Product và listing query hiện hỗ trợ `page`, `size`, `sortBy`, `sortDirection`. Bộ lọc marketplace theo category, price, delivery window, search và sort được frontend áp dụng trên page listing đã tải.

### 5.3 Contract API

| Method | Path | Mô tả |
|---|---|---|
| POST | `/contracts` | Tạo offer |
| GET | `/contracts?role=BUYER|SELLER&status=&page=&size=` | Danh sách contract theo vai trò hiện tại |
| GET | `/contracts/{contractId}` | Contract detail cho participant/Admin |
| GET | `/contracts/{contractId}/negotiations` | Lịch sử terms revisions |
| PUT | `/contracts/{contractId}/negotiate` | Counter-offer |
| PUT | `/contracts/{contractId}/sign` | Ký revision hiện tại |
| PUT | `/contracts/{contractId}/cancel` | Hủy từ `ACTIVE` |
| PUT | `/contracts/{contractId}/confirm-delivery` | Buyer xác nhận nhận hàng |
| PUT | `/contracts/{contractId}/dispute` | Buyer mở tranh chấp từ `DELIVERED` |

`ContractResponse` hiện có `termsRevision` và `signatories`; negotiation history được trả bởi endpoint riêng.

### 5.4 Escrow API

| Method | Path | Access | Mô tả |
|---|---|---|---|
| GET | `/escrows/contract/{contractId}` | Participant/Admin | Escrow theo contract |
| GET | `/escrows/{escrowId}/transactions` | Participant/Admin | Append-only transaction history |
| PUT | `/escrows/{contractId}/confirm-deposit` | SELLER | Xác nhận cọc seller |
| PUT | `/escrows/{contractId}/arbitrate` | ADMIN | Phân chia toàn bộ held amount với justification |

## 6. Event catalog thực tế

| Exchange | Producer | Events được consumer dùng |
|---|---|---|
| `agricontract.events` | product-service | `category.approved`, `category.rejected` |
| `agricontract.contracts` | contract-service | `contract.signed`, `contract.delivered`, `contract.cancelled`, `contract.disputed` |
| `agricontract.escrow` | escrow-service | `escrow.locked`, `escrow.released`, `escrow.penalized`, `escrow.arbitrated` |

Contract-service còn phát event lifecycle cho audit/extension (`contract.offered`, `contract.negotiating`, `contract.partially_signed`, `contract.activated`, `contract.settled`). Escrow-service còn phát `escrow.buyer_locked` và `escrow.refunded`. Không phải mọi event đều có consumer Phase 1.

Consumer contract/escrow/notification retry tối đa 3 lần với backoff 1s, 2s, 4s; message hết retry đi DLQ tương ứng. Payload không hợp lệ không retry.

## 7. Consistency và idempotency

- Product, Contract và Escrow lưu aggregate cùng outgoing events trong một local transaction; outbox poll mỗi giây và giữ row `PENDING` nếu RabbitMQ unavailable.
- Outbox hiện có hai trạng thái `PENDING` và `PUBLISHED`; không có trạng thái `FAILED` trong schema.
- Contract/Escrow dùng JPA `@Version` để tránh lost update.
- Escrow lock duplicate được chặn bằng unique `contract_id`; các transition async chính kiểm tra status để bỏ qua duplicate hợp lệ.
- Notification unique theo `(event_id, user_id)`, vì cùng một event có thể gửi cho nhiều người nhưng không gửi hai lần cho cùng recipient.
- Không có bảng `processed_events` TTL 7 ngày trong implementation hiện tại.

## 8. Frontend đã triển khai

| Route | Quyền |
|---|---|
| `/login`, `/listings`, `/listings/:listingId` | Public |
| `/register-profile`, `/dashboard`, `/contracts`, `/contracts/:contractId`, `/escrow` | Authenticated |
| `/listings/create`, `/listings/mine` | SELLER |
| `/admin/arbitrate/:contractId` | ADMIN |

Frontend có real API adapters cho user/product/listing/contract/escrow, polling React Query cho state async, UI negotiation history, action matrix theo role/status và global handling cho 401/403/5xx.

## 9. Chạy và test

Hướng dẫn chạy local đầy đủ nằm trong [`README.md`](../README.md). Các lệnh kiểm tra chính:

```bash
mvn test

cd frontend
npm test
npm run lint
npm run build
npm run test:e2e
```

Bruno contract E2E:

```bash
docker compose -f docker-compose.yml -f backend/docker-compose.override.yml up --build -d
./scripts/run-e2e.sh
```

Playwright hiện cover login/public marketplace và các flow happy path, buyer cancel, dispute, admin arbitration. Bruno contract suite cover thêm authorization, invalid transitions, idempotency, buyer/seller list filters và penalty cho cả hai bên.

## 10. Giới hạn Phase 1

- Escrow là mock database ledger, không phải tài khoản custody thật.
- Frontend chạy bằng Vite riêng, chưa nằm trong `docker-compose.yml`.
- Category admin API chưa route qua gateway mặc định.
- Marketplace filters phần lớn chạy phía frontend, không phải server-side query filters.
- Outbox là scheduled polling, chưa dùng CDC.
- Notification là email qua MailHog; chưa có notification feed API.
- Swagger của downstream service không được expose qua gateway trong compose mặc định.
