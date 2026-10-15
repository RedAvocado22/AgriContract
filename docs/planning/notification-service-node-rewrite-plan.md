# Kế hoạch viết lại notification-service bằng Node.js

Trạng thái: đề xuất, chờ team duyệt · Ngày: 16/10/2026 · Liên quan: issue #114, `implementation-plan.md` T10, `notification-service-phase2-design.md`

## 1. Quyết định và phạm vi

Team quyết định thay `backend/notification-service` (Java 21 / Spring Boot 3.3) bằng một bản Node.js/TypeScript. Đây là **thay thế cùng một service**, không thêm service thứ 13: cùng tên, cùng port `8085`, cùng database `notification_db`, cùng exchange/queue RabbitMQ, cùng MailHog.

Kế hoạch này gồm:

- Dựng khung service Node.
- Làm endpoint OTP đồng bộ (#114) là tính năng đầu tiên.
- Port đầy đủ 9 consumer Phase 1 đang chạy, đạt parity với bộ Bruno hiện có.
- Cutover: tắt Java, bật Node, chạy migration trên DB thật.

Ngoài phạm vi (làm sau trên nền Node, theo T10 của `implementation-plan.md`):

- 17 notification command Phase 2 trong `contracts/events/notification-commands.yaml`.
- Xoá consumer `contract.cancelled` / `contract.delivered`. Producer vẫn đang publish hai event này, nên bản Node phải giữ chúng cho đến khi T10 gỡ.

## 2. Nguyên tắc

1. **Không đổi gì nhìn thấy từ bên ngoài trước cutover.** Tên exchange, queue, DLX/DLQ, argument của queue, subject email, port, biến môi trường đều giữ nguyên.
2. **Bộ Bruno `backend/bruno/notification-service/` (20 request) là tiêu chí parity.** Bộ test này độc lập ngôn ngữ, gọi RabbitMQ và đọc MailHog, nên phải pass nguyên trạng trên bản Node.
3. **Không chạy song song Java và Node trên cùng queue hoặc cùng DB.** Hai consumer trên một queue sẽ chia message cho nhau. Migration V3 (mục 6) cũng làm Java gửi thiếu mail. Trước cutover, Node chỉ chạy trong test và trong compose profile riêng.
4. **Cấu trúc thư mục theo hexagonal như các service Java** (`domain` / `application` / `infrastructure`), để team đọc code quen thuộc.

## 3. Stack đề xuất

| Thành phần | Lựa chọn | Lý do |
|---|---|---|
| Runtime | Node.js 24 LTS, TypeScript `strict` | LTS hiện hành; type giúp giữ đúng shape payload |
| HTTP | Fastify | Validate request bằng JSON Schema (Ajv) có sẵn, khớp với schema trong `golden-flow-api.yaml` |
| Validation | Ajv + `ajv-formats` | Cần format `email`, `date-time`, `uuid` của contract |
| RabbitMQ | `amqplib` + `amqp-connection-manager` | Tự reconnect và tự khai báo lại topology |
| MySQL | `mysql2/promise`, SQL tham số hoá trong repository | Chỉ có một bảng; không cần ORM |
| Migration | Flyway CLI (image `flyway/flyway`, pin tag cụ thể) chạy one-shot trước service | Giữ nguyên `flyway_schema_history` và V1; không phải đổi công cụ migration |
| Email | Nodemailer, SMTP transport | Theo quyết định của team; MailHog ở local |
| Log | pino, có `redact` | Bắt buộc không log OTP (design §8) |
| Test | Vitest + Testcontainers (MySQL, RabbitMQ, MailHog) | Chạy được trên runner Ubuntu của GitHub Actions |
| Lint/format | ESLint + Prettier | |

Thư mục khi phát triển: `backend/notification-service-node/`. Khi cutover thì xoá thư mục Java và đổi tên thành `backend/notification-service/` (mục 5, giai đoạn N6).

```text
backend/notification-service-node/
  package.json, tsconfig.json, Dockerfile
  db/migration/                 # V1 (copy y nguyên), V2, V3
  scripts/sync-contracts.ts     # copy schema từ /contracts vào src/contracts/generated
  src/
    main.ts                     # bootstrap, graceful shutdown
    config.ts                   # đọc env, fail fast nếu thiếu
    domain/                     # NotificationLog, status, notificationType
    application/                # use case: sendOtpEmail, processLegacyEvent
    infrastructure/
      http/                     # Fastify app, route OTP, auth X-Internal-Secret, /health
      messaging/                # topology, consumer, retry, DLQ monitor
      persistence/              # NotificationLogRepository (mysql2)
      email/                    # EmailPort + Nodemailer adapter, templates
    contracts/generated/        # schema đã sync, commit vào repo
  test/unit, test/integration
```

## 4. Các giai đoạn

Mỗi giai đoạn là một issue và một PR có đúng một `Closes #...`.

```text
N0 ──> N1 ──> N2 ──> N3 (#114)  ──┐
          └──> N4 ──> N5 ─────────┴──> N6 cutover
```

Đường găng là N1 → N2 → N3, vì #114 là P0 của milestone P1. N4 và N5 có thể do người khác làm song song sau khi N1 merge.

### N0 — Ghi nhận quyết định (cỡ S)

- Thêm ADR ngắn vào `docs/` ghi quyết định thay runtime, lý do và các ràng buộc ở mục 2.
- Sửa issue #114: bỏ dòng "No second service implementation", thay bằng "Bản Node thay thế Java tại cutover; không deploy song song trên production".
- Ghi chú vào T10 của `implementation-plan.md` rằng notification được làm trên bản Node.
- Ghi nhận trong `golden-flow-api.yaml` rằng `serviceAuth` = header `X-Internal-Secret` (mục 4/N3), và bổ sung response `400`/`401` cho endpoint OTP. Đây là thay đổi contract, cần chủ contract đồng ý.

### N1 — Khung service Node (cỡ M)

- Scaffold như mục 3, `config.ts` đọc đúng tên biến môi trường đang dùng: `DB_HOST`, `DB_PORT`, `DB_NAME`, `DB_USERNAME`, `DB_PASSWORD`, `RABBITMQ_HOST`, `RABBITMQ_USERNAME`, `RABBITMQ_PASSWORD`, `MAIL_HOST`, `MAIL_PORT`, `ADMIN_EMAIL`, `SERVER_PORT` (mặc định `8085`), thêm `SERVICE_INTERNAL_SECRET`.
- `GET /health` kiểm tra DB và kết nối AMQP.
- Graceful shutdown khi nhận `SIGTERM`: ngừng nhận message, chờ xử lý dở, đóng channel và pool.
- `Dockerfile` multi-stage (build TS → image runtime gọn, chạy user không phải root).
- Compose: thêm service `notification-service-node` dưới profile `notification-node` (không bật mặc định), dùng database riêng `notification_node_db` trên container `notification-db`.
- CI: thêm job Node trong `.github/workflows/ci.yml` chạy `npm ci`, lint, typecheck, unit test, build. Job Java giữ nguyên.

Xong khi: image build được, `/health` trả 200 trong compose profile, CI xanh.

### N2 — Persistence và migration (cỡ M)

- Copy `V1__create_notification_logs.sql` **y nguyên từng byte**. Flyway kiểm checksum, sửa một ký tự là DB thật sẽ từ chối migrate.
- `V2__notification_logs_phase2_columns.sql`: thêm cột và backfill theo design §6 (`event_type`, `notification_type`, `recipient_email`, `template_version`, `provider_message_id`, `failure_reason`, `sent_at`; backfill `recipient_email = CONCAT('legacy:', user_id)`; `user_id` dài hơn 248 ký tự thì không vừa VARCHAR(255) sau prefix, nên copy nguyên `user_id`). V2 chỉ thêm cột có default nên Java vẫn chạy bình thường sau V2.
- `V3__notification_logs_recipient_type_unique.sql`: chạy lại đúng lệnh backfill của V2 (cho các row Java ghi nếu đã rollback về Java giữa V2 và V3), rồi bỏ `uq_event_user`, tạo `uq_event_recipient_type (event_id, recipient_email, notification_type)`. **Sau V3 không được chạy lại Java**: Java ghi row mới với `recipient_email = ''` và `notification_type = 'LEGACY_P1'`, nên mail thứ hai cùng `eventId` (vd seller sau buyer) sẽ đụng unique key và bị bỏ qua im lặng.
- Repository: `reserve` (insert `PENDING`, coi lỗi duplicate là "đã có người xử lý"), `markSent`, `markFailed`, `findByKey`.
- Cột `user_id` vẫn `NOT NULL`. OTP ghi `recipientUserId`; consumer Phase 1 ghi email như Java hiện tại vì event Phase 1 không có userId.
- Cột `body` vẫn `NOT NULL`: mail OTP lưu chuỗi giữ chỗ (vd `[redacted:OTP_SIGN_V1]`), không bao giờ lưu nội dung có mã.

Xong khi: integration test với Testcontainers MySQL chạy V1→V3 trên DB có sẵn dữ liệu kiểu Phase 1 (nhiều row cùng `event_id`) mà không lỗi và không mất row.

### N3 — Endpoint OTP đồng bộ (#114, cỡ M)

`POST /internal/v1/notifications/otp-email`, theo `OtpEmailRequest` trong `golden-flow-api.yaml`.

Xử lý:

1. **Xác thực caller:** header `X-Internal-Secret` phải bằng `SERVICE_INTERNAL_SECRET`, so sánh constant-time (`crypto.timingSafeEqual`). Đây là cơ chế các service Java đang dùng (`FeignConfig` của contract-service, `HeaderAuthenticationFilter` của product/user-service). Sai hoặc thiếu → `401`.
2. **Validate payload** bằng schema sync từ contract: đủ 7 field, `otpCode` 6 chữ số, `signedContentHash` 64 hex, `recipientEmail` đúng format, từ chối field lạ (`additionalProperties: false`). Thêm kiểm tra `expiresAt` phải ở tương lai. Lỗi → `400`.
3. **Idempotency theo `requestId`** (khoá `(requestId, recipientEmail, OTP_SIGN)`):

   | Trạng thái row có sẵn | Phản hồi | Gửi mail? |
   |---|---|---|
   | Không có | Reserve `PENDING`, gửi ngay | Có |
   | `SENT` | `200` | Không |
   | `FAILED` | `502` | Không. Caller phải tạo OTP mới với `requestId` mới |
   | `PENDING` (lần trước đang chạy hoặc process chết giữa chừng) | `502` | Không |

   Không bao giờ gửi lại một `requestId` đã từng thất bại. Đây là cách hiểu chặt nhất của matrix dòng 20 ("không có mail nào được gửi sau đó với `requestId` cũ").
4. **Gửi đồng bộ:** gọi Nodemailer trong request, không queue, không retry nền. Đặt `connectionTimeout`, `greetingTimeout`, `socketTimeout`. Provider nhận → `markSent` (lưu `providerMessageId`, `sentAt`) → `200`. Provider lỗi hoặc hết giờ → `markFailed` (lý do không chứa OTP) → `502`.
5. **Ngân sách thời gian:** tổng timeout SMTP phải **nhỏ hơn** timeout của client trong contract-service. Nếu caller hết giờ trước, nó sẽ báo lỗi trong khi mail vẫn có thể đi ra, tức là gửi muộn đúng thứ matrix 20 cấm. Ghi con số này vào README của service và vào issue phía contract-service.
6. **Không log OTP:** tắt log body cho route này; pino `redact` cho `otpCode` ở mọi path. Template `OTP_SIGN_V1` render trong memory, có mã hợp đồng, hash rút gọn và thời điểm hết hạn (hiển thị giờ ICT).

Test:

- Unit: sai/thiếu secret, từng lỗi validate, field lạ, `expiresAt` quá khứ, bảng idempotency ở trên, provider lỗi trả `502` và không retry.
- Integration (Testcontainers): gửi thật qua MailHog rồi đọc `GET /api/v2/messages`; gọi lại cùng `requestId` → vẫn đúng một mail; SMTP trỏ vào port đóng → `502` và MailHog không nhận mail nào sau đó.
- Bruno: thêm `20-otp-send.bru`, `21-verify-otp-mailhog.bru`, `22-otp-invalid-payload.bru`, `23-otp-missing-secret.bru` chạy trên compose profile `notification-node`.
- Kiểm tra log trong integration test: không dòng log nào chứa chuỗi `otpCode` đã gửi.

Ngoài phạm vi #114: code gọi endpoint ở contract-service (thuộc T2) và verify OTP.

### N4 — Hạ tầng RabbitMQ (cỡ M)

- Khai báo topology **giống hệt** `RabbitMQConfig.java`. Queue đã tồn tại durable trên broker, nên khai báo lệch argument sẽ bị broker từ chối (`PRECONDITION_FAILED`):
  - Exchange topic durable: `agricontract.contracts`, `agricontract.escrow`, `agricontract.events`.
  - DLX direct durable: `notification-svc.contracts.dlx`, `notification-svc.escrow.dlx`, `notification-svc.events.dlx`.
  - Mỗi routing key: queue `notification-svc.<routingKey>` với `x-dead-letter-exchange` tương ứng, và `notification-svc.<routingKey>.dlq` bind vào DLX bằng cùng routing key.
- Đọc message: body là JSON do `Jackson2JsonMessageConverter` của producer Java tạo. Bỏ qua header `__TypeId__`. Số thập phân cần giữ dạng chuỗi hoặc decimal, không ép sang float.
- Retry giống Java: tối đa 3 lần trong process, backoff 1s → 2s → 4s; lỗi payload (tương đương `InvalidEventPayloadException`) không retry; hết lượt thì `nack(requeue=false)` để vào DLQ. Số lần và backoff đọc từ config (design §7 về sau dùng 3 lần, bắt đầu 5s).
- Prefetch có giới hạn (vd 10) để retry trong process không giữ quá nhiều message.
- DLQ monitor: mỗi 5 phút kiểm tra số message trong mọi `*.dlq`, log `warn` nếu khác 0 (như `DlqMonitor.java`).

Xong khi: integration test với RabbitMQ container khai báo topology lên broker đã có queue do Java tạo trước mà không lỗi; message lỗi payload vào DLQ ngay; lỗi SMTP tạm thời retry đủ 3 lần rồi vào DLQ.

### N5 — Parity 9 consumer Phase 1 (cỡ M)

Port `NotificationEventConsumer` và `ProcessNotificationUseCaseImpl`. Subject và body phải giống từng ký tự vì Bruno so sánh chính xác.

| Queue | Người nhận | Subject |
|---|---|---|
| `contract.signed` | buyer, seller | `[AgriContract] Contract has been signed` |
| `contract.cancelled` | buyer, seller | `[AgriContract] Contract has been cancelled` |
| `contract.delivered` | seller | `[AgriContract] Buyer confirmed receipt of goods` |
| `contract.disputed` | admin, seller, buyer | `[AgriContract] [ADMIN] New dispute requires resolution` |
| `escrow.locked` | seller | `[AgriContract] Escrow locked — proceed with delivery` |
| `escrow.released` | seller, buyer | `[AgriContract] Payment has been released` |
| `escrow.penalized` | bên bị phạt theo `penalizedParty` | `[AgriContract] Contract penalty notification` |
| `category.approved` | `proposedByEmail` | `[AgriContract] Category proposal approved` |
| `category.rejected` | `proposedByEmail` | `[AgriContract] Category proposal rejected` |

- Dedup theo khoá mới: `eventId` + email người nhận + `notificationType` (vd `P1_CONTRACT_SIGNED`).
- Sửa một lỗ hổng của bản Java thay vì chép lại: Java gửi lại khi gặp row `PENDING`, nên hai lần giao lại cùng lúc có thể gửi trùng. Bản Node chỉ gửi khi chính nó vừa reserve được row, hoặc khi row ở `FAILED` và còn lượt retry (design §5).
- `ADMIN_EMAIL` lấy từ env như hiện tại.

Xong khi: cả 20 request Bruno trong `backend/bruno/notification-service/` pass khi chạy với bản Node, không sửa file Bruno nào.

### N6 — Cutover (cỡ M)

Một PR duy nhất:

1. Xoá `backend/notification-service/` (Java), đổi tên `notification-service-node` thành `notification-service`.
2. Bỏ `<module>notification-service</module>` khỏi `backend/pom.xml`.
3. Bỏ dòng `COPY notification-service/pom.xml ...` trong Dockerfile của `api-gateway`, `user-service`, `product-service`, `contract-service`, `escrow-service`. Không bỏ thì build Docker của các service Java sẽ lỗi vì thiếu file.
4. `docker-compose.yml`: service `notification-service` build từ Dockerfile Node, thêm `SERVICE_INTERNAL_SECRET`, thêm service one-shot `notification-migrate` (Flyway) mà `notification-service` phụ thuộc với `condition: service_completed_successfully`. Bỏ profile `notification-node` và DB tạm.
5. CI: job Node chạy trên đường dẫn mới, job Java không còn module notification.
6. README: sửa mô tả stack (hiện ghi toàn bộ là Java 21 / Spring Boot 3.3).

Runbook khi triển khai lên môi trường có dữ liệu:

1. Dừng `notification-service` Java. Chờ các queue `notification-svc.*` ổn định; message đến trong lúc này nằm lại trong queue, không mất.
2. Backup `notification_db`.
3. Chạy Flyway V2, kiểm tra số row và backfill `legacy:` không còn `recipient_email = ''`.
4. Chạy Flyway V3.
5. Bật Node; nó xử lý message còn tồn trong queue.
6. Chạy toàn bộ Bruno `notification-service`, kiểm tra DLQ bằng 0.

Rollback:

- Trước V3: dừng Node, bật lại image Java cũ. V2 chỉ thêm cột nên Java vẫn chạy được.
- Sau V3: phải chạy tay `DROP INDEX uq_event_recipient_type; CREATE UNIQUE INDEX uq_event_user ON notification_logs(event_id, user_id);` trước khi bật Java. Script này nằm trong runbook, không nằm trong `db/migration`.

## 5. Rủi ro

| Rủi ro | Hậu quả | Cách giảm |
|---|---|---|
| Khai báo queue lệch argument | Node không khởi động được trên broker thật | Test N4 khai báo lên broker đã có queue do Java tạo |
| Sửa V1 | Flyway từ chối migrate DB thật | Copy byte-identical, CI so checksum với bản Java trước khi xoá |
| Chạy Java sau V3 | Mail thứ hai cùng `eventId` bị bỏ qua im lặng | Runbook rollback có bước khôi phục index |
| Timeout caller ngắn hơn timeout SMTP | OTP đến sau khi caller đã báo lỗi | Ghi ngân sách thời gian, test với SMTP chậm |
| Schema trong Node lệch contract | Validate sai | `scripts/sync-contracts.ts` + CI kiểm tra không có diff |
| Docker build context là `backend/`, còn `contracts/` ở gốc repo | Không đọc được schema lúc build image | Schema sync được commit trong `src/contracts/generated` |
| Team chưa có quy ước cho service Node | Code khó review, mỗi người một kiểu | N1 chốt lint, cấu trúc và mẫu test trước khi làm tính năng |
| Cutover dở dang | Mất notification Phase 1 | Message nằm trong queue durable; rollback trước V3 không cần thao tác DB |

## 6. Việc phát sinh ngoài service này

- Gateway vẫn còn route `/api/v1/notifications/**` trong `api-gateway/src/main/resources/application.yml`, trong khi design §9 nói đã bỏ. Notification không có endpoint public nào. Báo chủ gateway; không sửa trong các PR trên.
- Phía contract-service cần issue riêng để gọi endpoint OTP với `X-Internal-Secret` và timeout lớn hơn ngân sách SMTP.
- `docs/phase_2/design/notification-service-phase2-design.md` mô tả service là phần mở rộng của Phase 1 Java. Cập nhật metadata `extends` khi cutover xong.

## 7. Điểm cần team chốt

| Câu hỏi | Mặc định trong kế hoạch |
|---|---|
| Phiên bản Node | 24 LTS |
| Framework HTTP | Fastify |
| OTP `FAILED` gọi lại cùng `requestId` | Trả `502`, không gửi lại; caller tạo OTP mới |
| Thêm `400`/`401` và định nghĩa `serviceAuth` vào contract | Có, qua N0 |
| Ai làm N4/N5 song song với #114 | Chưa phân công |
| Khung giờ cutover | Chưa chốt |
