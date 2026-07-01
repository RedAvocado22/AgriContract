# 📋 Phân công công việc — Nguyễn Hải Long (NHL)

> **Vai trò:** BE Support — phụ trách `user-service` · `product-service` · `notification-service` · Email templates · Flyway + Postman verify
> **Deadline toàn dự án:** 24/07/2026 (~6.5 tuần, 3 sprints)
> **Nguồn:** `notes/plan-dev-a.html` + đối chiếu trạng thái codebase ngày 12/06/2026

---

## 🗺️ Timeline

| Sprint | Thời gian | Mục tiêu |
|---|---|---|
| Sprint 1 | 09/06 – 22/06 | user-service domain + persistence + API. product-service domain + persistence. ✅ DONE |
| Sprint 2 | 23/06 – 06/07 | product-service API đủ cho NMC test. notification-service consumer gửi email được. ✅ DONE (01/07/2026) |
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

## Sprint 2 (23/06 – 06/07) — product API + notification-service ✅ DONE (01/07/2026)

### 🔌 6. Product API + Internal /close ⚡ ƯU TIÊN SỐ 1 — ✅ DONE
- [x] `CreateListingUseCaseImpl` — load Product → snapshot `product.getName()` vào Listing
- [x] Endpoints:

  | Method | Path | Role | Status |
  |---|---|---|---|
  | GET | `/api/v1/listings/{listingId}` | Public | ✅ |
  | PUT | `/api/v1/listings/{listingId}/close` | Internal | ✅ |
  | GET | `/api/v1/listings` | Public | ✅ |
  | POST | `/api/v1/listings` | SELLER | ✅ |
  | POST | `/api/v1/products` | SELLER | ✅ |
  | GET | `/api/v1/listings/seller` | SELLER | ✅ |

- [x] `/close` bảo vệ bằng header `X-Internal-Secret` verify với `SERVICE_INTERNAL_SECRET` (đổi từ plan gốc `X-Internal-Call: true` — literal đó forgeable, xem `decisions.md`) — thiếu/sai secret → `403`
- [x] **DoD:** GET /listings/{id} trả đúng JSON đủ fields · PUT /close có secret đúng → CLOSED · sai/thiếu → 403 · unit tests pass
- [x] Bruno collection `bruno/product-service/` (00–06) — 27/27 test pass (20/06/2026)

### 💾 7. Notification Persistence + RabbitMQ Config — ✅ DONE
- [x] `NotificationLogJpaRepository`: `existsByEventIdAndUserId`, `findByEventIdAndUserId`
- [x] `NotificationLogRepositoryImpl` + `NotificationLogMapper`
- [x] `RabbitMQConfig`: 2 TopicExchange (`agricontract.contracts`, `agricontract.escrow`), **7 queue** đặt tên `notification-svc.{routing-key}` (đổi từ plan gốc 2 queue gộp + binding `#` — verify trực tiếp code NMC 30/06/2026 ra spec khác ban đầu, xem `shared_contracts.md`), `Jackson2JsonMessageConverter`
- [x] Flyway migration `V1__create_notification_logs.sql`

### 📧 8. Event Consumer + Email Sending — ✅ DONE
- [x] 7 listener (`@RabbitListener` riêng từng queue), xử lý **đúng thứ tự bắt buộc**:
  1. Idempotency — `existsByEventIdAndUserId` (đổi từ `existsByEventId` — 1 event có thể sinh nhiều notification cho nhiều recipient, key phải gồm cả userId/email) → skip duplicate
  2. Persist log PENDING **trước khi** gửi
  3. Dispatch theo `eventType`
- [x] Retry 3 lần, backoff 1s/2s/4s (`RetryInterceptorBuilder` trên `SimpleRabbitListenerContainerFactory`)
- [x] Email templates (subject cuối cùng dùng tiếng Anh, không phải bản tiếng Việt trong plan gốc):

  | Event (routing key) | Recipient | Subject (đã implement) |
  |---|---|---|
  | `contract.signed` | Seller + Buyer | [AgriContract] Contract has been signed |
  | `escrow.locked` | Seller | [AgriContract] Escrow locked — proceed with delivery |
  | `contract.delivered` | Seller | [AgriContract] Buyer confirmed receipt of goods |
  | `escrow.released` | Seller + Buyer | [AgriContract] Payment has been released |
  | `contract.cancelled` | Seller + Buyer | [AgriContract] Contract has been cancelled |
  | `escrow.penalized` | Bên bị phạt (theo `penalizedParty`) | [AgriContract] Contract penalty notification |
  | `contract.disputed` | Admin + Seller + Buyer | [AgriContract] [ADMIN] New dispute requires resolution |

- [x] **DoD:** Bruno suite (`bruno/notification-service/`, 16 request / 30 test) verify MailHog nhận đúng email cho cả 7 event · gửi lại `eventId` trùng → không có email thứ 2 (idempotency test) · `notification_logs` chuyển đúng `SENT`

### 🔗 9. UserService Feign Client — ⚠️ built nhưng KHÔNG dùng
- [x] `UserServiceClient` + `UserPortAdapter` + `UserPort` + `UserInfo` đã code xong, `@EnableFeignClients` + OpenFeign dependency đã thêm
- ⚠️ **Phát hiện 01/07/2026: Feign client này không được gọi ở đâu cả** (`grep` 0 kết quả cho `UserPort` ngoài chính 2 file định nghĩa nó). Lý do: `buyerEmail`/`sellerEmail` đã được contract-service/escrow-service publish trực tiếp trong event payload, nên `sendNotification()` dùng thẳng field đó, không cần Feign resolve qua user-service nữa (xem `shared_contracts.md`). Giữ nguyên code (không phải dead code nguy hiểm, chỉ là chưa dùng) — cân nhắc xoá nếu chắc chắn không cần trong Sprint 3.

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

## 📌 Cập nhật trạng thái sau Sprint 2 (01/07/2026)

- **Cả 3 service (user-service, product-service, notification-service) đã DONE**, đúng scope Sprint 1 + 2.
- `user-service`: PR merged vào `main` (26/06/2026).
- `product-service`: PR merged, kèm fix `X-Internal-Secret` auth (26/06/2026). Bruno 27/27 pass.
- `notification-service`: PR #35 (`feature/notification-service` → `main`), gồm toàn bộ implementation + Bruno suite 30/30 pass + 5 bug fix phát hiện lúc viết test:
  1. `docker-compose.yml` — lỗi cú pháp port khiến container không build được
  2. `pom.xml` — dependency Maven thừa vào module `user-service` (0 chỗ dùng) khiến Docker build fail; thiếu `jackson-datatype-jsr310`
  3. `application.yml` — `admin-email` thụt lề sai khiến app crash lúc start; `DB_PORT` hardcode 3306 không khớp port host-expose (3311) gây lỗi khi chạy local/IDE
  4. `NotificationLogRepositoryImpl.save()` — luôn INSERT (mapper ignore `id` thật) → duplicate key ở lần save thứ 2 (PENDING → SENT). Đã fix bằng cách tìm entity cũ theo `(eventId, userId)` trước khi save.
- Data test dùng user thật từ Keycloak (`buyer1`/`seller1`, verify live qua token + `/userinfo`), không phải UUID giả.
- Việc chưa xong / để dành Sprint 3: email template đẹp hơn (hiện plain text đủ demo), cân nhắc xoá Feign `UserPort` không dùng (mục 9), assist NMC saga wiring + integration test.
