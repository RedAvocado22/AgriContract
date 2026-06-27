# 📋 Phân công công việc — Nguyễn Hải Long (NHL)

> **Vai trò:** BE Support — phụ trách `user-service` · `product-service` · `notification-service` · Email templates · Flyway + Postman verify
> **Deadline toàn dự án:** 24/07/2026 (~6.5 tuần, 3 sprints)
> **Nguồn:** `notes/plan-dev-a.html` + đối chiếu trạng thái codebase ngày 12/06/2026

---

## 🗺️ Timeline

| Sprint | Thời gian | Mục tiêu |
|---|---|---|
| Sprint 1 | 09/06 – 22/06 | user-service domain + persistence + API. product-service domain + persistence. |
| Sprint 2 | 23/06 – 06/07 | product-service API đủ cho NMC test. notification-service consumer gửi email được. |
| Sprint 3 | 07/07 – 20/07 | Assist integration test + buffer + bug fix |
| Buffer | 21/07 – 24/07 | Bug fix + demo prep |

## ⚠️ Dependencies quan trọng

1. **⏳ Chờ NMC — Keycloak:** cần realm `agricontract` + test users trước khi test user-service qua JWT (claim `sub`). NMC cam kết xong cuối tuần 1. Trong lúc chờ → cứ làm domain model + persistence (unit test không cần Keycloak).
2. **⚡ NHL unblocks NMC — critical path tuần 3:** `GET /api/v1/listings/{listingId}` **phải xong trước 23/06** vì NMC bị block `CreateContractUseCase`. Đây là ưu tiên số 1 của product-service.
3. **📧 notification-service cần user-service xong trước:** Feign gọi `GET /api/v1/users/{userId}` để lấy email → xong user-service API tuần 2 rồi mới bắt đầu notification-service.

---

## Sprint 1 (09/06 – 22/06) — user-service + product-service domain

### 👤 1. UserProfile Aggregate
File: `user-service/src/main/java/com/agricontract/user/domain/model/UserProfile.java`
*(hiện tại: skeleton, toàn bộ method đang `throw UnsupportedOperationException` / TODO)*

- [ ] Implement `create(...)` — khởi tạo với `verificationStatus = PENDING`, set `createdAt/updatedAt`
  - ⚠️ `userId` = Keycloak `sub` (lấy từ JWT) — **KHÔNG tự sinh UUID**, không nhận từ request body
- [ ] `verify()` — chỉ cho phép từ PENDING → VERIFIED, sai trạng thái thì `IllegalStateException`
- [ ] `reject()` — chỉ PENDING → REJECTED
- [ ] `updateContactInfo(...)` — update phone/address + `updatedAt`
- [ ] Fix `UserRegisteredEvent.of(...)` — `eventId` và `occurredAt` đang null:
  - `e.eventId = UUID.randomUUID().toString();`
  - `e.occurredAt = Instant.now();`
- [ ] **Unit tests:**
  - `create()` → status = PENDING
  - `verify()` khi PENDING → VERIFIED ✓
  - `verify()` khi VERIFIED → IllegalStateException ✓
  - `reject()` khi PENDING → REJECTED ✓
  - `updateContactInfo()` → phone/address được update

### 💾 2. User Persistence Layer
*(hiện tại: `UserProfileRepositoryImpl` còn 6 TODO)*

- [ ] `UserProfileJpaRepository extends JpaRepository<UserProfileJpaEntity, Long>`:
  - `Optional<UserProfileJpaEntity> findByUserId(String userId)`
  - `boolean existsByUserId(String userId)`
- [ ] Implement `UserProfileRepositoryImpl implements UserProfileRepository` (save / findById / existsById qua mapper)
- [ ] Hoàn thiện `UserProfileMapper` (MapStruct) — toDomain / toEntity
- [ ] Flyway migration cho `user_db` (theo `docs/diagrams/db/user-db.puml`)

### 🔌 3. User API + Use Cases
*(hiện tại: `UserController` 3 endpoint đều rỗng)*

- [ ] DTOs: `RegisterUserRequest`, `UserProfileResponse` (record)
- [ ] `RegisterUserUseCaseImpl` — **idempotent**: nếu user đã tồn tại → trả profile hiện có (Controller trả `200 OK` thay vì `201`)
- [ ] `GetUserProfileUseCase` implement
- [ ] Endpoints:

  | Method | Path | Actor | Ghi chú |
  |---|---|---|---|
  | POST | `/api/v1/users/register` | Bất kỳ | userId + email lấy từ JWT (`jwt.getSubject()`, claim `email`), không từ body |
  | GET | `/api/v1/users/me` | Đang login | userId từ JWT `sub` |
  | GET | `/api/v1/users/{userId}` | Internal | notification-service Feign gọi để lấy email |

### 📦 4. Product + Listing Aggregate
File: `product-service/.../domain/model/Listing.java` *(hiện tại: 4 TODO)*

- [ ] `create(...)` — status khởi tạo ACTIVE; `productName` là **SNAPSHOT** (copy string từ Product, không giữ reference)
- [ ] `close()` — chỉ ACTIVE → CLOSED (NMC Feign gọi khi contract SIGNED), sai thì `IllegalStateException`
- [ ] `expire()` — chỉ ACTIVE → EXPIRED (deliveryDeadline đã qua)
- [ ] Implement `Product` aggregate tương tự
- [ ] **Unit tests:**
  - `create()` → ACTIVE
  - `close()` khi ACTIVE → CLOSED ✓ / khi CLOSED → exception ✓ / khi EXPIRED → exception ✓
  - `expire()` khi ACTIVE → EXPIRED ✓

### 💾 5. Product Persistence Layer
*(hiện tại: **chưa có** JpaRepository / RepositoryImpl / Mapper — chỉ có 2 entity)*

- [ ] `ListingJpaRepository`: `findByListingId`, `findBySellerId`, `findByStatus`
- [ ] `ProductJpaRepository`
- [ ] `ListingRepositoryImpl`, `ProductRepositoryImpl` + `ListingMapper`, `ProductMapper` (MapStruct)
- [ ] Flyway migration cho `product_db` (theo `docs/diagrams/db/product-db.puml`)

---

## Sprint 2 (23/06 – 06/07) — product API + notification-service

### 🔌 6. Product API + Internal /close ⚡ ƯU TIÊN SỐ 1
*(hiện tại: `ListingController` có sẵn skeleton 4 endpoint, đều rỗng)*

- [ ] `CreateListingUseCaseImpl` — load Product → snapshot `product.getName()` vào Listing
- [ ] Endpoints theo độ ưu tiên:

  | Method | Path | Role | Priority |
  |---|---|---|---|
  | GET | `/api/v1/listings/{listingId}` | Public | 🔥 **NMC blocked nếu chưa có — xong trước 23/06** |
  | PUT | `/api/v1/listings/{listingId}/close` | Internal | 🔥 NMC blocked nếu chưa có |
  | GET | `/api/v1/listings` | Public | Normal |
  | POST | `/api/v1/listings` | SELLER | Normal |
  | POST | `/api/v1/products` | SELLER | Normal |
  | GET | `/api/v1/listings/seller` | SELLER | Normal (cần thêm endpoint — chưa có trong controller) |

- [ ] `/close` bảo vệ bằng header `X-Internal-Call: true` — thiếu header → `403`
- [ ] ✅ **DoD:** GET /listings/{id} trả đúng JSON đủ fields · PUT /close có header → CLOSED · không header → 403 · unit tests pass

### 💾 7. Notification Persistence + RabbitMQ Config
*(hiện tại: **chưa có** JpaRepository / RepositoryImpl / RabbitMQConfig)*

- [ ] `NotificationLogJpaRepository`: `existsByEventId` (idempotency guard), `findByEventId`, `findByStatus`
- [ ] `NotificationLogRepositoryImpl` + `NotificationLogMapper`
- [ ] `RabbitMQConfig`: 2 TopicExchange (`agricontract.contracts`, `agricontract.escrow`), 2 durable queue (`notif-svc.contracts`, `notif-svc.escrow`), binding wildcard `#`, `Jackson2JsonMessageConverter`
  - ⚠️ Tên queue phải khớp SHARED_CONTRACTS của NMC — check lại plan NMC trước khi code
- [ ] Flyway migration cho `notification_db`

### 📧 8. Event Consumer + Email Sending
*(hiện tại: `NotificationEventConsumer` còn 5 TODO)*

- [ ] Listener `notif-svc.contracts` + `notif-svc.escrow`, xử lý **đúng thứ tự bắt buộc**:
  1. Idempotency — `existsByEventId` → skip duplicate (RabbitMQ at-least-once)
  2. Persist log PENDING **trước khi** gửi
  3. Dispatch theo `eventType` (switch)
- [ ] Retry email 3 lần, backoff 1s/2s/4s; fail cả 3 → `log.status = FAILED`
- [ ] Email templates:

  | Event | Recipient | Subject |
  |---|---|---|
  | ContractSignedEvent | Seller + Buyer | [AgriContract] Hợp đồng đã được ký |
  | EscrowLockedEvent | Seller | [AgriContract] Escrow đã lock — tiến hành giao hàng |
  | GoodsDeliveredEvent | Seller | [AgriContract] Buyer xác nhận đã nhận hàng |
  | EscrowReleasedEvent | Seller + Buyer | [AgriContract] Thanh toán đã giải phóng |
  | ContractCancelledEvent | Seller + Buyer | [AgriContract] Hợp đồng đã bị hủy |
  | EscrowPenalizedEvent | Bên bị phạt | [AgriContract] Thông báo phạt hợp đồng |
  | ContractDisputedEvent | Admin + cả hai | [AgriContract][ADMIN] Tranh chấp mới cần xử lý |

- [ ] ✅ **DoD:** chạy happy path → MailHog (`localhost:8025`) có ≥ 3 emails · gửi lại event 2 lần → email chỉ xuất hiện 1 lần

### 🔗 9. UserService Feign Client
- [ ] `UserServiceClient` — `@FeignClient(name = "user-service", url = "${user-service.url:http://user-service:8081}")`, `GET /api/v1/users/{userId}`
- [ ] Record `UserInfo(userId, organizationName, email, role)` — khớp format `UserProfileResponse`
- [ ] `getEmail()` lỗi → log warn + return null, **không throw** (skip, tiếp tục xử lý)
- [ ] Thêm `@EnableFeignClients` vào `NotificationServiceApplication` + OpenFeign dependency vào `pom.xml`

---

## Tuần 4 (24/06 – 30/06) — 🤝 Assist NMC: Saga Wiring

- [ ] Verify RabbitMQ UI (`localhost:15672`) — exchanges, queues, bindings đúng
- [ ] Test full flow: create listing → create contract → sign ×2 → verify listing CLOSED, escrow LOCKED
- [ ] Debug Feign: listing không CLOSED → check logs contract-service, verify format `GET /listings/{id}`
- [ ] Kiểm tra idempotency: retry ContractSignedEvent → escrow không lock 2 lần
- [ ] Verify MailHog: sau mỗi step key phải có email tại `localhost:8025`

## Sprint 3 (07/07 – 20/07)
- [ ] Assist integration test toàn hệ thống + buffer + bug fix
- [ ] Postman/Bruno collection verify API (xem mẫu sẵn ở thư mục `bruno/`)

---

## 📌 Ghi chú trạng thái codebase (12/06/2026)

- Cả 3 service đã có **skeleton structure** (domain model, vo, repository interface, controller) nhưng **toàn bộ logic là TODO**.
- `user-service`: đã có đủ file kể cả `RepositoryImpl` + `Mapper` (rỗng) — chỉ cần fill logic.
- `product-service`: **thiếu** toàn bộ tầng JpaRepository/RepositoryImpl/Mapper + use cases — phải tạo mới.
- `notification-service`: **thiếu** JpaRepository/RepositoryImpl/Mapper, RabbitMQConfig, Feign client — phải tạo mới.
- Chưa service nào có Flyway migration / thư mục `db/migration`.
- Gateway + auth public-path đã được NMC làm xong (PR #1 đã merge) — public endpoints của product-service đã được permit.
