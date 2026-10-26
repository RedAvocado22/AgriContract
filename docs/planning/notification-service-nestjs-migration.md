---
name: notification-service-nestjs-migration
description: "Kế hoạch chuyển notification-service từ Spring Boot (Java 21) sang NestJS (TypeScript) — parity Phase 1 trước, Phase 2 viết thẳng trên Nest."
status: "DONE: M0–M5 xong (M4: PR #206, checklist parity đã ký; M5: issue #230, rollback tag notification-java-final)"
metadata:
  type: plan
  owner: "P3 (Node) — xem team-assignment-plan.md"
  related: "phase_2/design/notification-service-phase2-design.md; backend/shared/node-eventing/README.md"
---

# Notification-service: Spring Boot → NestJS

## 1. Mục tiêu & phạm vi

**Mục tiêu:** thay `backend/notification-service` (Spring Boot) bằng một service NestJS **hành vi tương đương 100%** với Phase 1. Sau đó toàn bộ việc Phase 2 (T2 OTP, T10 notification commands) được code thẳng trên Nest, không đụng Java nữa.

| Trong phạm vi                                          | Ngoài phạm vi (làm sau, trên Nest — §9)                  |
| ------------------------------------------------------ | -------------------------------------------------------- |
| Port 9 listener Phase 1, retry/DLQ, dedup, email, log  | `notification.*_requested` commands, template versioning |
| Giữ nguyên DB schema `notification_logs` (V1)          | Migration V2 (unique key mới, cột mới — design §6)       |
| Giữ nguyên tên queue/exchange/DLX, env vars, port 8085 | Endpoint OTP `POST /internal/v1/notifications/otp-email` |
| Docker, docker-compose, CI, xoá module Java            | Attachment `.ots` / PDF                                  |

**Nguyên tắc:** migration và thay đổi nghiệp vụ không đi chung một release. Parity xong, cutover xong, rồi mới thêm Phase 2. Nhờ vậy rollback về Java vẫn an toàn vì schema chưa đổi.

**Freeze Java:** từ khi bắt đầu M1, không merge thay đổi tính năng vào notification-service Java. Chỉ cho phép hotfix, và hotfix phải port sang Nest cùng PR.

## 2. Hiện trạng (đã kiểm tra trong code)

- Spring Boot 3, Java 21, là module trong `backend/pom.xml`. Port `8085`. Không có REST controller, chỉ có RabbitMQ consumer và actuator `health, info`.
- **9 queue** (`RabbitMQConfig.java`):

  | Exchange (topic)         | Routing key → queue `notification-svc.<key>`                                       | DLX (direct)                     |
  | ------------------------ | ---------------------------------------------------------------------------------- | -------------------------------- |
  | `agricontract.contracts` | `contract.signed`, `contract.delivered`, `contract.cancelled`, `contract.disputed` | `notification-svc.contracts.dlx` |
  | `agricontract.escrow`    | `escrow.locked`, `escrow.released`, `escrow.penalized`                             | `notification-svc.escrow.dlx`    |
  | `agricontract.events`    | `category.approved`, `category.rejected`                                           | `notification-svc.events.dlx`    |

  Mỗi queue là durable, chỉ có argument `x-dead-letter-exchange`. DLQ là `<queue>.dlq`, bind vào DLX với cùng routing key.

- **Retry:** tối đa 3 lần, backoff 1s → 2s (max 4s), sau đó reject không requeue (vào DLQ). `InvalidEventPayloadException` vào DLQ ngay.
- **Payload:** các producer (`OutboxPoller` của contract, escrow và product) publish **JSON phẳng**: `eventId`, `eventType`, `occurredAt` cùng các field nghiệp vụ ở top-level, **không có envelope** `eventVersion/producer/aggregateId/payload`. Bruno dùng `eventId` dạng `evt-signed-<timestamp>`, **không phải UUID**.
- **Dedup:** bảng `notification_logs`, unique `(event_id, user_id)`. Thực tế cột `user_id` đang chứa **email người nhận** (`findByEventIdAndUserId(eventId, recipientEmail)`).
- **Email:** text thuần, from `noreply@agricontract.com`, SMTP không auth (MailHog `1025` ở local).
- **DLQ monitor:** cứ 5 phút log warn nếu DLQ nào có message.
- **Migration:** Flyway, `V1__create_notification_logs.sql`. DB của các môi trường đang chạy đã có `flyway_schema_history`.
- **Test:** JUnit có 6 case (use case và consumer). Bruno có 20 request trong `backend/bruno/notification-service/` (publish qua RabbitMQ management API, verify qua MailHog API). Bruno không phụ thuộc ngôn ngữ, nên dùng lại nguyên bộ làm **parity test**.

## 3. Quyết định kỹ thuật

| #   | Vấn đề                                           | Chọn                                                                                                                                                                                                                                | Lý do                                                                                                                                                                                                                                                                                                          |
| --- | ------------------------------------------------ | ----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | -------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| D1  | Runtime                                          | Node 22 LTS, NestJS 11, TypeScript strict                                                                                                                                                                                           | node-eventing yêu cầu Node ≥ 20. Node 22 có `require(esm)` (xem D3)                                                                                                                                                                                                                                            |
| D2  | RabbitMQ                                         | `amqplib` + `consumeEvents`/`handleDelivery` của `@agricontract/eventing`, đặt trong một Nest provider (`onModuleInit`/`onModuleDestroy`)                                                                                           | Retry 3 lần, backoff 1s/2s/4s, DLQ và xử lý channel đóng đã được viết và test, đúng semantics Java. **Không** dùng `@nestjs/microservices` RMQ transport: transport này đòi format `{pattern, data}` và không hợp với DLX/queue khai báo sẵn                                                                   |
| D3  | node-eventing là ESM-only, Nest CLI mặc định CJS | CJS project + Node ≥ 22.12 (`require(esm)`) cho runtime. Jest: `moduleNameMapper` trỏ package về **source TS** của node-eventing + `tsconfig.spec.json` (`module: commonjs`). **Không cần dual build** (spike M0, 16/10/2026)       | Runtime chạy được ngay. Jest không dùng `require(esm)` của Node và coi mọi `.js` trong package `type: module` là ESM trước khi transform, nên không thể dùng `dist` trong test                                                                                                                                 |
| D4  | Dedup                                            | **Không** dùng `IdempotentConsumer`. Dedup bằng `notification_logs` giống Java                                                                                                                                                      | `IdempotentConsumer` gói marker và handler trong một transaction. Email là side effect ngoài DB: nếu message gửi 2 mail, mail thứ 2 lỗi, transaction rollback làm mất luôn log `SENT` của mail đầu, và lần retry sẽ gửi trùng. README node-eventing cũng nói side effect ngoài DB phải dùng semantic key riêng |
| D5  | Parse payload Phase 1                            | Parser riêng cho payload phẳng, **không** dùng `parseEnvelope`                                                                                                                                                                      | Producer chưa gửi envelope. `parseEnvelope` sẽ đẩy mọi message vào DLQ. Validate strict theo envelope chỉ áp dụng cho Phase 2 commands                                                                                                                                                                         |
| D6  | Kiểu trả về `MessageConsumer`                    | PR nhỏ vào node-eventing: thêm type `DeliveryOutcome` (envelope tuỳ chọn, chỉ `eventId?`/`eventType?` để log) làm kiểu trả về của `MessageConsumer`. **Giữ nguyên `HandleOutcome`** nên `IdempotentConsumer` và caller cũ không đổi | Hiện `HandleOutcome` bắt buộc có `EventEnvelope`. **Đã đồng ý** (16/10/2026), diff soạn ở M0                                                                                                                                                                                                                   |
| D7  | DB access                                        | `mysql2/promise` + repository viết SQL tay, không ORM                                                                                                                                                                               | Bảng chỉ có 1. node-eventing cũng dùng `mysql2`. Tránh thêm TypeORM/Prisma chỉ cho 3 câu SQL                                                                                                                                                                                                                   |
| D8  | Migration                                        | Giữ Flyway: chạy one-shot container `flyway/flyway` trong compose, đọc `db/migration` của service                                                                                                                                   | DB đang chạy đã có `flyway_schema_history`. Đổi tool thì phải baseline lại. Flyway không phụ thuộc ngôn ngữ app                                                                                                                                                                                                |
| D9  | Email                                            | `nodemailer` (SMTP), adapter sau port `EmailPort`                                                                                                                                                                                   | Tương đương `JavaMailSender`                                                                                                                                                                                                                                                                                   |
| D10 | Config                                           | `@nestjs/config` + validate env lúc khởi động (fail fast)                                                                                                                                                                           | **Giữ nguyên tên env** để compose chỉ đổi phần build                                                                                                                                                                                                                                                           |
| D11 | Health                                           | `@nestjs/terminus`, route `GET /actuator/health` (kiểm tra DB + AMQP)                                                                                                                                                               | Giữ path cũ, không phải sửa chỗ nào đang gọi                                                                                                                                                                                                                                                                   |
| D12 | Log                                              | Nest `Logger` mặc định, bọc thành adapter truyền vào `logger` của node-eventing                                                                                                                                                     | Đủ cho Phase 1. `Logger` interface của node-eventing có `info/warn/error` (adapter map `info` → `log`)                                                                                                                                                                                                         |
| D13 | Test                                             | Jest (mặc định của Nest) cho unit test. Bruno cho parity/e2e                                                                                                                                                                        | —                                                                                                                                                                                                                                                                                                              |
| D14 | Kiến trúc                                        | Giữ layout hexagonal như Java: `domain` / `application` / `infrastructure`                                                                                                                                                          | Người review đọc song song 2 bản dễ hơn. Phase 2 cũng thêm vào đúng chỗ                                                                                                                                                                                                                                        |

## 4. Hành vi phải giữ nguyên (parity checklist)

Mỗi dòng phải có test (unit hoặc Bruno) trước khi cutover.

1. **Topology:** đúng tên exchange, queue, DLQ, DLX và kiểu exchange như bảng §2. `assertQueue` phải truyền **đúng** `arguments: { 'x-dead-letter-exchange': ... }`, không thêm argument nào khác. Nếu khác, RabbitMQ trả `PRECONDITION_FAILED` và đóng channel, vì queue đã tồn tại.
2. `channel.prefetch(10)` trước khi consume (README node-eventing bắt buộc).
3. **Thuật toán gửi** (`NotificationSender.send`), mỗi người nhận xử lý độc lập. Đây là bản chặt hơn Java, sửa theo review #172:
   1. **Claim nguyên tử** theo `(event_id, user_id = email)`:
      - Lần đầu: `INSERT` trạng thái `PENDING`.
      - Nếu dòng đã tồn tại: chạy một câu `UPDATE` có điều kiện, chỉ lấy lại dòng `FAILED` hoặc dòng `PENDING` có `updated_at < UTC_TIMESTAMP(6) - 45 giây` (lease, giờ DB), rồi kiểm `affectedRows`. Lease 45 giây lớn hơn SMTP socket timeout 30 giây.
      - Nhiều delivery chạy đồng thời thì tối đa một bên claim được.
      - Hằng số `CLAIM_LEASE_MS`, `CLAIM_HEARTBEAT_MS`, `CLAIM_WAIT_MS`, `CLAIM_POLL_MS` nằm ở một chỗ: `application/notification-sender.ts`.
   2. Dòng `SENT` thì bỏ qua. Nếu một delivery khác đang giữ claim còn hạn: **chờ claim**, xem lại dòng mỗi 1 giây, tối đa 50 giây (message vẫn unacked trong lúc chờ):
      - Dòng thành `SENT`: bỏ qua người nhận này, không gửi.
      - Dòng thành `FAILED`, hoặc claim hết lease (bên giữ đã chết): lấy claim và gửi.
      - Hết 50 giây mà claim vẫn sống: **throw lỗi retryable**, không ack. Nếu ack thì mail sẽ mất khi bên đang giữ claim bị lỗi.
      - Shutdown bắt đầu trong lúc chờ: dừng chờ ngay, xem lại dòng một lần (thấy `SENT` thì bỏ qua). Claim vẫn sống thì throw `ClaimWaitAbortedError` và consumer **park** message như một lần retry (mục 8.3). Nhờ vậy lần chờ không kéo dài quá drain 40 giây.

      Vì chờ 50 giây lớn hơn lease 45 giây, claim của một delivery đã chết luôn được lấy lại trong thời gian chờ (sửa D-1 trong báo cáo parity M4). Không giảm `CLAIM_WAIT_MS` xuống dưới lease được.

      Cái giá: trong lúc chờ, message chiếm một slot prefetch (10) của consumer queue đó, tối đa 50 giây. Chỉ delivery trùng mới phải chờ (broker redeliver trong khi delivery đầu của cùng event vẫn đang gửi), nên hiếm. Kết quả cuối vẫn đúng (review PR #206).
   3. Gửi mail ngoài transaction. Trong lúc gửi, **heartbeat** mỗi 15 giây cập nhật `updated_at = UTC_TIMESTAMP(6)` của dòng (chỉ khi còn `PENDING`), nên claim của lần gửi đang sống không bao giờ quá lease. Heartbeat dừng khi gửi xong, dù thành công hay lỗi. Heartbeat lỗi thì log error (chỉ `eventId` và mã lỗi) và việc gửi vẫn tiếp tục. Thành công thì `SENT`. Lỗi thì `FAILED`, `retry_count + 1` (tính trong SQL), rồi throw để cả message được retry. Người nhận đã `SENT` sẽ bị bỏ qua ở lần retry.
   4. Log chỉ chứa `eventId` và mã lỗi, không chứa email hay nội dung payload.

   Java khác ở hai điểm: không claim dòng `PENDING`/`FAILED` có sẵn, và khi `INSERT` trùng khoá thì ack luôn.
4. **Người nhận và nội dung:** copy nguyên văn subject/body từ `ProcessNotificationUseCaseImpl.java` (9 handler, 14 lần gửi):
   - `contract.disputed` gửi theo thứ tự admin (`ADMIN_EMAIL`), seller, buyer.
   - `escrow.penalized` lấy `buyerEmail` nếu `penalizedParty === 'BUYER'`, ngược lại lấy `sellerEmail`.
   - `escrow.*` dùng `escrowId`. `category.*` dùng `name`, `proposedByEmail`, `rejectionReason`.
5. **Retry:** 3 lần, 1s/2s, sau đó DLQ. Payload sai thì throw `NonRetryableError` và vào DLQ ngay.
6. **DLQ monitor:** mỗi 5 phút gọi `checkQueue` trên 9 DLQ, log warn khi `messageCount > 0` (`@nestjs/schedule`).
7. **Ghi `created_at`/`updated_at`** kiểu `DATETIME(6)` UTC như Java (JPA auditing).
8. Ack/nack thủ công (`noAck: false`). Graceful shutdown, theo thứ tự:
   1. `enableShutdownHooks([], { useProcessExit: true })`: process thoát ngay sau các hook. Node là PID 1 trong container nên bỏ qua signal mà Nest gửi lại cho chính nó; thiếu tuỳ chọn này thì process chạy tiếp sau khi pool đã đóng.
   2. `onModuleDestroy`: ngừng reconnect RabbitMQ.
   3. `beforeApplicationShutdown`: từ lúc này **không bắt đầu lần thử mới**. Lần retry tới hạn sau backoff (hoặc delivery đến ngay trước khi cancel) bị **park**: không chạy handler, không claim DB, không ack, không nack. Message giữ unacked và được broker redeliver khi channel đóng, giống Java. Message đang **chờ claim** (mục 3.2) cũng bị park ngay: consumer abort `SHUTDOWN_CONTROLLER`, signal của nó cắt lần chờ trong `NotificationSender`. Sau đó cancel consumer và đợi các lần thử đang chạy xong, **tối đa 40 giây** (lớn hơn SMTP timeout 30 giây). Drain không chờ message đã park.
   4. Hết 40 giây mà vẫn còn lần gửi đang chạy: không ack, đóng channel, broker redeliver.
   5. `onApplicationShutdown`: đóng channel, connection, rồi pool (sau khi drain xong hoặc hết hạn).

   Compose cho `notification-service` `stop_grace_period: 60s`, lớn hơn drain 40 giây, nên process thoát trước SIGKILL.

**Thay đổi hành vi có chủ đích:**

- (Đã đồng ý 16/10/2026.) Java chỉ ném `InvalidEventPayloadException` khi field **sai kiểu**. Field **thiếu** (null): Java bỏ qua người nhận thiếu email, gửi cho những người còn lại, ack. Nest vào DLQ ngay (validate các field bắt buộc là string không rỗng).
- (D-3 parity M4, 25/10/2026.) **JSON lỗi vào DLQ ngay, không retry.** Java đưa `MessageConversionException` qua retry interceptor (chỉ `InvalidEventPayloadException` là non-retryable), nên thử 3 lần trong khoảng 3 giây rồi mới vào DLQ. Nest vào DLQ ngay. Kết quả cuối giống nhau: 1 message trong DLQ, không mail, không log.
- (Sửa D-1, 25/10/2026.) **Có thể gửi trùng khi crash** đúng lúc giữa lúc SMTP đã chấp nhận mail và lúc ghi `SENT`: dòng vẫn `PENDING`, message được redeliver, claim bị lấy lại sau lease và mail được gửi lần nữa. Java cũng có rủi ro này. Không loại bỏ hoàn toàn được nếu không có outbox hoặc idempotency phía nhà cung cấp mail.
- (Sửa D-1, 25/10/2026.) **Nest không gửi trùng do tranh chấp claim**: khi hai delivery cùng `(event_id, email)` chạy đồng thời, đúng một bên gửi, bên kia chờ claim rồi thấy `SENT`. Java chỉ chặn được lần `INSERT` đầu tiên (trùng khoá thì bỏ qua). Khi dòng đã có sẵn ở `PENDING` (một delivery đang gửi) hoặc `FAILED`, `ProcessNotificationUseCaseImpl.sendNotification` tìm thấy dòng, `save` cập nhật nó mà không lỗi rồi gửi. Vì vậy hai delivery đồng thời có thể cùng gửi. Đây là khác biệt có lợi.

## 5. Cấu trúc thư mục

Code trong `backend/notification-service-nest/` trong lúc phát triển. Lúc cutover (M5), xoá Java và đổi tên thành `backend/notification-service/` để giữ nguyên đường dẫn trong compose, Bruno và docs.

```text
backend/notification-service-nest/
├── package.json            # "@agricontract/eventing": "file:../shared/node-eventing"
├── tsconfig.json / nest-cli.json / jest.config.ts
├── Dockerfile
├── db/migration/V1__create_notification_logs.sql      # chuyển từ src/main/resources
├── src/
│   ├── main.ts                                         # enableShutdownHooks, listen SERVER_PORT
│   ├── app.module.ts
│   ├── config/env.validation.ts                        # schema env, fail fast
│   ├── domain/
│   │   └── notification-log.ts                         # create/markSent/markFailed + status/channel enum
│   ├── application/                                    # TypeScript thuần, không import Nest (review #172)
│   │   ├── commands/*.ts                               # 9 command type (thay record Java)
│   │   ├── ports/email.port.ts                         # EmailPort + EmailDeliveryError (chỉ mã lỗi)
│   │   ├── ports/notification-log.repository.ts        # claim / markSent / markFailed
│   │   ├── notification-sender.ts                      # thuật toán gửi §4.3
│   │   ├── notification-errors.ts                      # lỗi có thông báo an toàn để log
│   │   └── use-cases/notify-*.use-case.ts              # 9 Notify<Event>UseCase, execute(command)
│   └── infrastructure/
│       ├── messaging/topology.ts                       # khai báo exchange/queue/DLQ (bảng §2 làm data)
│       ├── messaging/legacy-payload.parsers.ts         # map JSON phẳng → command, NonRetryableError khi sai
│       ├── messaging/notification-event.consumer.ts    # MessageConsumer cho từng queue → consumeEvents
│       ├── messaging/amqp.provider.ts                  # connection/channel lifecycle, prefetch
│       ├── messaging/dlq-monitor.ts
│       ├── persistence/mysql.provider.ts               # mysql2 pool
│       ├── persistence/notification-log.mysql-repository.ts
│       ├── email/nodemailer-email.adapter.ts
│       └── health/health.controller.ts                 # GET /actuator/health
└── test/                                               # Jest, port 6 case JUnit + case mới
```

**Env** (giữ tên cũ, thêm 2 biến tuỳ chọn):

| Env                                                         | Default                                  | Ghi chú                        |
| ----------------------------------------------------------- | ---------------------------------------- | ------------------------------ |
| `DB_HOST` / `DB_PORT` / `DB_NAME`                           | `localhost` / `3311` / `notification_db` |                                |
| `DB_USERNAME` / `DB_PASSWORD`                               | `root` / `password`                      |                                |
| `RABBITMQ_HOST` / `RABBITMQ_USERNAME` / `RABBITMQ_PASSWORD` | `localhost` / `guest` / `guest`          |                                |
| `RABBITMQ_PORT`                                             | `5672`                                   | mới (Java hardcode)            |
| `MAIL_HOST` / `MAIL_PORT`                                   | `localhost` / `1025`                     |                                |
| `MAIL_FROM`                                                 | `noreply@agricontract.com`               | mới (Java: `spring.mail.from`) |
| `ADMIN_EMAIL`                                               | `admin@agricontract.com`                 |                                |
| `SERVER_PORT`                                               | `8085`                                   |                                |

## 6. Các mốc & task

Ước lượng cho 1 người (P3). Tổng khoảng **8–11 ngày làm việc**.

### M0 — Spike & chốt nền tảng (1 ngày)

- [x] Tạo Nest project, thêm `@agricontract/eventing` qua `file:`, build node-eventing, rồi chứng minh `import { consumeEvents } from '@agricontract/eventing'` chạy được cả trong `nest start` **và** Jest (D3). Nếu Jest không chạy được thì làm phương án dự phòng (dual build cho node-eventing).
- [x] Mở PR vào node-eventing cho D6 (`DeliveryOutcome`), có review của người giữ package: #169.
- [x] Team chốt các câu hỏi mở ở §10 (16/10/2026).

**Xong khi:** một consumer demo nhận được 1 message từ queue tạm, ack, và test Jest import được package.

**Kết quả spike (16/10/2026):**

| Kiểm tra                                                                           | Node 22.23               | Node 24.14        |
| ---------------------------------------------------------------------------------- | ------------------------ | ----------------- |
| `tsc` / `nest build` (strict, `module: nodenext`, output CJS)                      | —                        | OK                |
| App chạy, import package qua `require(esm)`                                        | OK (`node dist/main.js`) | OK (`nest start`) |
| Consumer `notification-svc.m0-spike` nhận message phẳng, ack (0 ready / 0 unacked) | OK                       | OK                |
| Jest 2/2 (load package thật + `handleDelivery` ack message không envelope)         | OK                       | OK                |
| ESLint                                                                             | —                        | sạch              |

- Jest với cấu hình Nest mặc định **fail** (`Must use import to load ES Module`). Transform `dist` cũng không cứu được, vì Jest phân loại ESM theo `"type": "module"` trước khi transform. Cách đã chạy: map package sang `src/index.ts` của node-eventing + `tsconfig.spec.json` compile CJS. Vì vậy **không cần dual build**. Đổi lại, test chạy trên source TS của node-eventing, còn app chạy trên `dist` của nó.
- Trên Windows, dừng tiến trình chạy qua `npx` có thể để lại tiến trình `node` con (consumer mồ côi vẫn giữ queue). Khi test tay M4 phải kiểm tra số consumer trên queue.

### M1 — Scaffold & hạ tầng trong app (1–2 ngày)

- [x] Config module + validate env (D10).
- [x] MySQL pool provider, AMQP provider (connection, channel, prefetch, shutdown).
- [x] `topology.ts`: khai báo đủ 9 queue, 9 DLQ, 3 exchange, 3 DLX, viết dưới dạng **bảng data** để Phase 2 chỉ cần thêm dòng.
- [x] Health endpoint `/actuator/health`.
- [x] ESLint + Prettier theo mặc định Nest.

**Xong khi:** `docker compose up` bản Nest khởi động được trên RabbitMQ **đang có sẵn queue do Java tạo** mà không bị `PRECONDITION_FAILED`.

**Kết quả M1 (16/10/2026):** Nest chạy bằng `npm run start`, chưa qua compose vì Dockerfile thuộc M3. Hạ tầng lấy từ compose.

- Trên broker đã có queue do Java tạo: không có `PRECONDITION_FAILED`, health trả 200. Output `rabbitmqctl list_queues` và `list_exchanges` giống hệt trước khi chạy Nest.
- Chiều ngược lại: trên broker trống, Nest tạo topology trùng khớp snapshot của Java. Sau đó Java khởi động không lỗi và gắn đủ 9 consumer.
- Broker tắt: app vẫn lên và retry với backoff 1s → 30s, health trả 503. Bật broker lại thì app tự kết nối, health về 200. DB không kết nối được, hoặc env sai, thì app thoát với exit code 1.
- Image Java trên `main` không build được: `backend/pom.xml` có module `inspection-service`, nhưng Dockerfile không `COPY inspection-service/pom.xml`. Lúc kiểm chứng phải dùng `docker-compose.override.yml` local. Cần sửa riêng, ngoài M1.

### M2 — Port nghiệp vụ (3–4 ngày)

- [x] `NotificationLog` domain, repository MySQL (`claim` nguyên tử, `markSent`, `markFailed`).
- [x] 9 `Notify<Event>UseCase` (TypeScript thuần) và `NotificationSender`: thuật toán §4.3, subject/body copy nguyên văn.
- [x] `legacy-payload.parsers.ts`: 9 parser, kể cả logic `penalizedParty`.
- [x] `NotificationEventConsumer`: mỗi queue nối parser → use case qua `legacyRoutes`, đăng ký bằng `consumeEvents`.
- [x] Nodemailer adapter.
- [x] DLQ monitor.
- [x] Unit test (Jest):
  - Port 6 case JUnit hiện có.
  - Thêm: đủ 9 handler (đúng người nhận, subject, body), `SENT` thì skip, `ER_DUP_ENTRY` thì không gửi, mail lỗi thì `FAILED` + throw, recipient thứ 2 lỗi rồi retry thì không gửi lại recipient 1, payload thiếu hoặc sai kiểu thì `NonRetryableError`, `penalizedParty` BUYER/SELLER.
  - Test `handleDelivery` với channel giả: retry 3 lần rồi nack(requeue=false).

**Xong khi:** mọi dòng §4 có test; coverage (lines) của `application/` ≥ 95% và của `infrastructure/messaging/` ≥ 90%.

Tiêu chí cũ "tương đương hoặc hơn Java" không kiểm được, vì Java không có baseline JaCoCo. Đã đổi theo review #172.

**Kết quả M2 (17/10/2026, cập nhật 19/10/2026 sau review #172):**

- **Test:** 366 unit test. Thêm `npm run test:int`: 5 test trên MySQL thật, chạy riêng vì cần DB.
- **Coverage (lines):** `application/` 100%, `infrastructure/messaging/` 97.8%.
- **Sửa theo review #172:**
  - Claim nguyên tử cho từng email (§4.3).
  - Không log email hay nội dung payload (có test bắt log đầu-cuối).
  - Tầng `application/` là TypeScript thuần: một `Notify<Event>UseCase` cho mỗi hành động.
- **Golden fixtures:** `test/fixtures/java-templates.json` được trích xuất từ `ProcessNotificationUseCaseImpl.java`.
- **Smoke test** (hạ tầng local, Java không chạy):
  - 3 event (contract, escrow, category) cho đúng 4 mail và 4 dòng `SENT`, với `user_id` là email. Publish lại cùng `eventId` không gửi thêm mail nào.
  - JSON hỏng vào DLQ sau 1 lần thử.
  - Khi MailHog tắt: 3 lần thử, rồi vào DLQ, log `FAILED` với `retry_count = 3`. DLQ monitor cảnh báo đúng 5 phút sau khi khởi động.
  - SIGINT lúc đang retry: cancel 9 consumer, đợi message xong, rồi đóng kết nối sạch.
- **Quyết định trong M2:**
  - `reason` của `contract.cancelled` / `contract.disputed` là **tuỳ chọn**, vì contract-service cho cancel/dispute không kèm lý do. Body khi đó là `Reason: null`, giống Java.
  - `categoryId` là tuỳ chọn, vì không template nào dùng.
  - Thứ tự tắt: `onModuleDestroy` ngừng reconnect, `beforeApplicationShutdown` drain consumer, `onApplicationShutdown` đóng channel và pool.

### M3 — Đóng gói, compose, CI (1–2 ngày)

- [x] `Dockerfile` multi-stage `node:22-alpine`, build context `backend/`: build `shared/node-eventing` trước, rồi `npm ci && npm run build` của service, image runtime chỉ có `dist` và `node_modules` production, chạy user `node`.
- [x] `docker-compose.yml`:
  - Thêm service `notification-migrate` (`flyway/flyway`, mount `notification-service-nest/db/migration` cho tới M5, `migrate`).
  - `notification-service` thêm `depends_on: notification-migrate: condition: service_completed_successfully` và trỏ build sang Dockerfile mới.
- [x] CI (`.github/workflows/ci.yml`): thêm job `notification-service-node` gồm setup-node 22 (cache npm), build và test `backend/shared/node-eventing`, rồi `npm ci`, `lint`, `test`, `build` của service. **Chưa** bỏ Java ở bước này.

**Xong khi:** CI xanh cả Maven lẫn Node. `docker compose up` từ volume trống chạy Flyway V1 rồi app lên healthy.

**Kết quả M3 (21/10/2026):**

- **Image:** 287 MB, Node 22, chạy user `node`. Layout giữ như stage build (`/app/notification-service-nest` cạnh `/app/shared/node-eventing`) để symlink `file:` vẫn đúng. `require('@agricontract/eventing')` chạy được. Không có `src/`, `test/` hay devDependencies; file `.ts` duy nhất là `.d.ts` và source kèm theo của `rxjs` trong `node_modules`. `HEALTHCHECK` gọi `/actuator/health` bằng `node -e`.
- **Migration:** `db/migration/V1__create_notification_logs.sql` copy nguyên byte từ Java (cùng blob `e62cf79`). `notification-migrate` dùng `flyway/flyway:10.10.0`, đúng phiên bản Flyway của Java.
- **Compose từ volume trống:** Flyway áp dụng V1 (`flyway_schema_history`: version 1, checksum `-1380183233`, success 1). `notification-service` lên `healthy`, health trả `{"status":"UP","components":{"db":{"status":"UP"},"rabbit":{"status":"UP"}}}`, đủ 9 queue + 9 DLQ, mỗi queue chính 1 consumer. Chạy migrate lần hai: "No migration necessary". Restart service: lại `healthy`, không thêm migration.
- **Tương thích với Java:** dừng Nest, chạy image Java trên DB do Flyway CLI tạo: Java validate V1 thành công, "Schema is up to date". Hai bên dùng chung `flyway_schema_history` được.
- **Build và CI:** `docker compose build` toàn bộ thành công (5 service Java + Nest). CI xanh cả job Maven và `notification-service-node` (lint, 366 test, build).
- **Quyết định trong M3:**
  - JDBC URL của Flyway thêm `allowPublicKeyRetrieval=true`, giống URL của Java. Driver MariaDB trong image Flyway không dùng SSL, nên thiếu tham số này thì không xác thực được với MySQL 8.
  - Volume `notification_db_data` có bảng `notification_logs` mà không có `flyway_schema_history` thì Flyway dừng với lỗi `non-empty schema`. Volume cần trống, hoặc là volume do Java tạo.

### M4 — Parity test (1–2 ngày)

- [x] Chạy toàn bộ `backend/bruno/notification-service/` (00–19) với **Java**, ghi lại kết quả. Dừng Java, chạy lại với **Nest** trên cùng broker và DB. Kết quả phải giống nhau, kể cả case duplicate/idempotency (02–03).
- [x] Test tay các tình huống Bruno chưa phủ:
  - Tắt MailHog: message retry 3 lần rồi vào DLQ, log `FAILED`, DLQ monitor cảnh báo.
  - Publish JSON lỗi: vào DLQ ngay.
  - Restart RabbitMQ trong lúc chạy: service không crash, consume lại.
  - `docker stop` giữa chừng: message chưa ack được redeliver; không gửi trùng ngoài cửa sổ đã ghi ở §4.
- [x] Chạy luồng contract/escrow end-to-end (`backend/bruno/contract`, `escrow`) và kiểm tra MailHog nhận đủ mail.

**Xong khi:** có checklist parity ký xác nhận (đính trong PR).

### M5 — Cutover & dọn Java (1 ngày, 1 PR riêng)

- [x] Xoá `backend/notification-service/` (Java), đổi tên `notification-service-nest` thành `notification-service`.
- [x] Bỏ `<module>notification-service</module>` khỏi `backend/pom.xml`.
- [x] **Xoá dòng `COPY notification-service/pom.xml ...`** trong Dockerfile của `api-gateway`, `user-service`, `product-service`, `contract-service`, `escrow-service`. Nếu sót, các service này build fail.
- [x] Sửa đường dẫn trong compose và CI theo tên mới. Chạy lại `mvn clean verify` cho phần Java còn lại.
- [x] Cập nhật `README.md` (bảng service: stack NestJS), `notification-service-phase2-design.md` (metadata `extends`) và `implementation-plan.md` T2/T10 (module/file path mới). `implementation-plan.md` đã bị xoá ở `342dc5f` (19/10/2026), không còn gì để cập nhật.
- [x] Cắt tag `notification-java-final` trên commit trước PR này để rollback.

**Kết quả M5 (26/10/2026, issue #230):**

- **Tag rollback:** `notification-java-final` (annotated, object `6450060`) trỏ vào `main` `b2376914adb8f50e5ef3e3d0b00252f85b9f6143`, commit cuối còn Java.
- **Đường dẫn:** compose (build `notification-service/Dockerfile`, mount `notification-service/db/migration`), Dockerfile của service (`WORKDIR /app/notification-service`), CI, CODEOWNERS, README của service và của analytics-service. CI: bỏ `notification-service` khỏi filter và matrix Maven. Job Node đổi tên `notification-service-node` → `notification-service`, output `node` → `notification`. `ci-gate` vẫn là check bắt buộc duy nhất.
- **`mvn clean verify`** (8 module còn lại, không còn notification; chạy trong `maven:3.9-eclipse-temurin-21` vì máy không có Maven): BUILD SUCCESS, 848 test, 0 fail. Hai lần chạy trước fail ở `reputation-service`, đều do môi trường chạy, không do code: lần 1 thiếu thư mục `contracts/` ở gốc repo (test đọc `../../contracts`), lần 2 có 1 test `ReputationMySqlTest` timeout khi kết nối MySQL Testcontainers (`Connect timed out`, 27 test còn lại cùng class đạt). Lần 3 không đổi gì, đạt toàn bộ.
- **Node:** `npm ci`, `lint:check`, `test` (392 pass, 1 skip), `build` đạt. Test bị skip là "golden fixture freshness", tự skip khi không còn file Java (đúng như thiết kế ở M2).
- **`docker compose build`:** đạt cả 6 image (5 service Java + `notification-service`).
- **Compose từ volume trống:** chạy trên stack riêng (compose project `agri-m5`, override tạm không commit, `container_name`/port khác, volume mới; stack và volume dùng chung không bị đụng). Flyway áp dụng V1 (checksum `-1380183233`, giống M3), service `healthy`, health `db`/`rabbit` `UP`, 9 queue + 9 DLQ, mỗi queue chính 1 consumer. Đã `down -v` sau khi chạy.
- **Bruno `notification-service` 00–19 trên stack đó:** giống lượt Nest ở M4. 00–03 pass (01: 3/3, 2 mail; 03: vẫn 2 mail). Tổng 18/20 request, 32/39 test, chỉ 17 và 19 fail (lỗi collection, báo cáo M4 §5.1). 14 dòng `SENT` (`retry_count` max 0), 14 mail, DLQ 0.
- **Grep:** không còn `notification-service-nest` hay module Java của notification ngoài tài liệu lịch sử (§5, M3, báo cáo M4) và tên file `notification-service-nestjs-migration.md`. Còn lại: `test/support/java-templates.ts` trỏ tới file Java đã xoá (test dùng nó tự skip) và script `npm run fixtures:java` không chạy được nữa. Để lại vì M5 không đổi code Nest; dọn ở PR sau.

## 7. Cutover & rollback

- **Không chạy Java và Nest cùng lúc trên cùng queue.** Hai consumer sẽ chia message. Unique key ngăn phần lớn mail trùng nhưng log và DLQ khó đối soát. Quy trình: dừng Java, deploy Nest, verify bằng Bruno 00–03.
- Message tồn trong queue lúc chuyển sẽ được Nest xử lý. Dedup theo `(event_id, user_id)` dùng chung bảng nên không gửi lại mail Java đã `SENT`.
- **Rollback:** deploy lại image hoặc tag `notification-java-final`. An toàn vì schema vẫn là V1 và topology không đổi. Sau khi migration V2 Phase 2 chạy thì rollback về Java **không còn an toàn**. Vì vậy V2 chỉ được merge sau khi Nest đã chạy ổn định ít nhất 1 vòng demo.

## 8. Rủi ro

| Rủi ro                                                           | Ảnh hưởng                                  | Giảm thiểu                                                 |
| ---------------------------------------------------------------- | ------------------------------------------ | ---------------------------------------------------------- |
| ESM/CJS giữa node-eventing và Nest/Jest                          | Chặn từ ngày đầu                           | Spike M0, có sẵn phương án dual build                      |
| Khai báo queue lệch argument so với Java                         | Channel bị đóng, không consume được        | Topology copy từ bảng §2. Test M1 trên broker có sẵn queue |
| Lỡ dùng `parseEnvelope`/`IdempotentConsumer` cho payload Phase 1 | Toàn bộ mail vào DLQ / gửi trùng khi retry | D4, D5. Bruno M4 bắt được ngay                             |
| Sót Dockerfile của service khác khi xoá module                   | Build compose fail                         | Checklist M5, CI build image                               |
| Subject/body lệch chữ                                            | Bruno verify (so subject) fail             | Copy nguyên văn. Snapshot test cho 9 handler               |
| Phase 2 bị trễ vì migration                                      | T2/T10 trễ                                 | Migration nhỏ (~2 tuần). Freeze Java để không làm 2 lần    |

## 9. Sau cutover: Phase 2 trên Nest

Làm theo `phase_2/design/notification-service-phase2-design.md`. Thứ tự đề xuất:

1. **Migration V2** (design §6), thêm vào `db/migration/V2__...sql`, Flyway chạy như cũ.
   Ghi chú: các row Phase 1 vốn lưu email trong `user_id`, nên `legacy:{user_id}` vẫn giữ được email gốc để đối soát.
2. **Command consumer** `notification.*_requested`: dùng `parseEnvelope` và validate payload theo `contracts/events/notification-commands.yaml` (ajv). Sai schema thì vào DLQ ngay. Dedup `(event_id, recipient_email, notification_type)`.
3. **Template có version** trong source (`templates/<notificationType>/<version>`), lưu `template_version` vào log.
4. **OTP endpoint** `POST /internal/v1/notifications/otp-email`: service-to-service auth, không retry nền, không log OTP (design §2.2, §8).
5. **Attachment** `.ots` và PDF final (design §3.3).
6. **Gỡ listener Phase 1** khi producer đã chuyển sang command. Riêng `contract.delivered` bị bỏ hẳn (design §4).

## 10. Quyết định đã chốt (16/10/2026)

| #   | Câu hỏi                      | Chốt                                                                                   |
| --- | ---------------------------- | -------------------------------------------------------------------------------------- |
| 1   | Runtime cho Docker/CI        | **Node 22 LTS** (máy dev Node 24 vẫn chạy được)                                        |
| 2   | Payload thiếu field bắt buộc | **Vào DLQ ngay**. Java bỏ qua người nhận thiếu email, gửi cho những người còn lại, ack. Nest vào DLQ ngay |
| 3   | node-eventing                | **Làm PR nới `HandleOutcome`** (D6). Dual build CJS **chỉ khi** spike M0 thất bại (D3) |
| 4   | Logger                       | **Nest `Logger` mặc định**, đủ cho Phase 1 (D12)                                       |
| 5   | Health path                  | **Giữ `/actuator/health`** (D11)                                                       |
| 6   | Framework                    | **NestJS**. Fastify (#168) bị bỏ (16/10/2026)                                          |
