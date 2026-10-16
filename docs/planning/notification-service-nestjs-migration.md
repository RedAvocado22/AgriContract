---
name: notification-service-nestjs-migration
description: "Kế hoạch chuyển notification-service từ Spring Boot (Java 21) sang NestJS (TypeScript) — parity Phase 1 trước, Phase 2 viết thẳng trên Nest."
status: PLANNED — team đã chốt chuyển sang NestJS và chốt các câu hỏi mở §10 (16/10/2026).
metadata:
  type: plan
  owner: "P3 (Node) — xem team-assignment-plan.md"
  related: "phase_2/design/notification-service-phase2-design.md; planning/implementation-plan.md T2/T10; backend/shared/node-eventing/README.md"
---

# Notification-service: Spring Boot → NestJS

## 1. Mục tiêu & phạm vi

**Mục tiêu:** thay `backend/notification-service` (Spring Boot) bằng một service NestJS **hành vi tương đương 100%** với Phase 1. Sau đó toàn bộ việc Phase 2 (T2 OTP, T10 notification commands) được code thẳng trên Nest, không đụng Java nữa.

| Trong phạm vi | Ngoài phạm vi (làm sau, trên Nest — §9) |
|---|---|
| Port 9 listener Phase 1, retry/DLQ, dedup, email, log | `notification.*_requested` commands, template versioning |
| Giữ nguyên DB schema `notification_logs` (V1) | Migration V2 (unique key mới, cột mới — design §6) |
| Giữ nguyên tên queue/exchange/DLX, env vars, port 8085 | Endpoint OTP `POST /internal/v1/notifications/otp-email` |
| Docker, docker-compose, CI, xoá module Java | Attachment `.ots` / PDF |

**Nguyên tắc:** migration và thay đổi nghiệp vụ không đi chung một release. Parity xong, cutover xong, rồi mới thêm Phase 2. Nhờ vậy rollback về Java vẫn an toàn vì schema chưa đổi.

**Freeze Java:** từ khi bắt đầu M1, không merge thay đổi tính năng vào notification-service Java. Chỉ cho phép hotfix, và hotfix phải port sang Nest cùng PR.

## 2. Hiện trạng (đã kiểm tra trong code)

- Spring Boot 3, Java 21, là module trong `backend/pom.xml`. Port `8085`. Không có REST controller, chỉ có RabbitMQ consumer và actuator `health, info`.
- **9 queue** (`RabbitMQConfig.java`):

  | Exchange (topic) | Routing key → queue `notification-svc.<key>` | DLX (direct) |
  |---|---|---|
  | `agricontract.contracts` | `contract.signed`, `contract.delivered`, `contract.cancelled`, `contract.disputed` | `notification-svc.contracts.dlx` |
  | `agricontract.escrow` | `escrow.locked`, `escrow.released`, `escrow.penalized` | `notification-svc.escrow.dlx` |
  | `agricontract.events` | `category.approved`, `category.rejected` | `notification-svc.events.dlx` |

  Mỗi queue là durable, chỉ có argument `x-dead-letter-exchange`. DLQ là `<queue>.dlq`, bind vào DLX với cùng routing key.
- **Retry:** tối đa 3 lần, backoff 1s → 2s (max 4s), sau đó reject không requeue (vào DLQ). `InvalidEventPayloadException` vào DLQ ngay.
- **Payload:** các producer (`OutboxPoller` của contract, escrow và product) publish **JSON phẳng**: `eventId`, `eventType`, `occurredAt` cùng các field nghiệp vụ ở top-level, **không có envelope** `eventVersion/producer/aggregateId/payload`. Bruno dùng `eventId` dạng `evt-signed-<timestamp>`, **không phải UUID**.
- **Dedup:** bảng `notification_logs`, unique `(event_id, user_id)`. Thực tế cột `user_id` đang chứa **email người nhận** (`findByEventIdAndUserId(eventId, recipientEmail)`).
- **Email:** text thuần, from `noreply@agricontract.com`, SMTP không auth (MailHog `1025` ở local).
- **DLQ monitor:** cứ 5 phút log warn nếu DLQ nào có message.
- **Migration:** Flyway, `V1__create_notification_logs.sql`. DB của các môi trường đang chạy đã có `flyway_schema_history`.
- **Test:** JUnit có 6 case (use case và consumer). Bruno có 20 request trong `backend/bruno/notification-service/` (publish qua RabbitMQ management API, verify qua MailHog API). Bruno không phụ thuộc ngôn ngữ, nên dùng lại nguyên bộ làm **parity test**.

## 3. Quyết định kỹ thuật

| # | Vấn đề | Chọn | Lý do |
|---|---|---|---|
| D1 | Runtime | Node 22 LTS, NestJS 11, TypeScript strict | node-eventing yêu cầu Node ≥ 20. Node 22 có `require(esm)` (xem D3) |
| D2 | RabbitMQ | `amqplib` + `consumeEvents`/`handleDelivery` của `@agricontract/eventing`, đặt trong một Nest provider (`onModuleInit`/`onModuleDestroy`) | Retry 3 lần, backoff 1s/2s/4s, DLQ và xử lý channel đóng đã được viết và test, đúng semantics Java. **Không** dùng `@nestjs/microservices` RMQ transport: transport này đòi format `{pattern, data}` và không hợp với DLX/queue khai báo sẵn |
| D3 | node-eventing là ESM-only, Nest CLI mặc định CJS | CJS project + Node ≥ 22.12 (`require(esm)`) cho runtime. Jest: `moduleNameMapper` trỏ package về **source TS** của node-eventing + `tsconfig.spec.json` (`module: commonjs`). **Không cần dual build** (spike M0, 16/10/2026) | Runtime chạy được ngay. Jest không dùng `require(esm)` của Node và coi mọi `.js` trong package `type: module` là ESM trước khi transform, nên không thể dùng `dist` trong test |
| D4 | Dedup | **Không** dùng `IdempotentConsumer`. Dedup bằng `notification_logs` giống Java | `IdempotentConsumer` gói marker và handler trong một transaction. Email là side effect ngoài DB: nếu message gửi 2 mail, mail thứ 2 lỗi, transaction rollback làm mất luôn log `SENT` của mail đầu, và lần retry sẽ gửi trùng. README node-eventing cũng nói side effect ngoài DB phải dùng semantic key riêng |
| D5 | Parse payload Phase 1 | Parser riêng cho payload phẳng, **không** dùng `parseEnvelope` | Producer chưa gửi envelope. `parseEnvelope` sẽ đẩy mọi message vào DLQ. Validate strict theo envelope chỉ áp dụng cho Phase 2 commands |
| D6 | Kiểu trả về `MessageConsumer` | PR nhỏ vào node-eventing: thêm type `DeliveryOutcome` (envelope tuỳ chọn, chỉ `eventId?`/`eventType?` để log) làm kiểu trả về của `MessageConsumer`. **Giữ nguyên `HandleOutcome`** nên `IdempotentConsumer` và caller cũ không đổi | Hiện `HandleOutcome` bắt buộc có `EventEnvelope`. **Đã đồng ý** (16/10/2026), diff soạn ở M0 |
| D7 | DB access | `mysql2/promise` + repository viết SQL tay, không ORM | Bảng chỉ có 1. node-eventing cũng dùng `mysql2`. Tránh thêm TypeORM/Prisma chỉ cho 3 câu SQL |
| D8 | Migration | Giữ Flyway: chạy one-shot container `flyway/flyway` trong compose, đọc `db/migration` của service | DB đang chạy đã có `flyway_schema_history`. Đổi tool thì phải baseline lại. Flyway không phụ thuộc ngôn ngữ app |
| D9 | Email | `nodemailer` (SMTP), adapter sau port `EmailPort` | Tương đương `JavaMailSender` |
| D10 | Config | `@nestjs/config` + validate env lúc khởi động (fail fast) | **Giữ nguyên tên env** để compose chỉ đổi phần build |
| D11 | Health | `@nestjs/terminus`, route `GET /actuator/health` (kiểm tra DB + AMQP) | Giữ path cũ, không phải sửa chỗ nào đang gọi |
| D12 | Log | Nest `Logger` mặc định, bọc thành adapter truyền vào `logger` của node-eventing | Đủ cho Phase 1. `Logger` interface của node-eventing có `info/warn/error` (adapter map `info` → `log`) |
| D13 | Test | Jest (mặc định của Nest) cho unit test. Bruno cho parity/e2e | — |
| D14 | Kiến trúc | Giữ layout hexagonal như Java: `domain` / `application` / `infrastructure` | Người review đọc song song 2 bản dễ hơn. Phase 2 cũng thêm vào đúng chỗ |

## 4. Hành vi phải giữ nguyên (parity checklist)

Mỗi dòng phải có test (unit hoặc Bruno) trước khi cutover.

1. **Topology:** đúng tên exchange, queue, DLQ, DLX và kiểu exchange như bảng §2. `assertQueue` phải truyền **đúng** `arguments: { 'x-dead-letter-exchange': ... }`, không thêm argument nào khác. Nếu khác, RabbitMQ trả `PRECONDITION_FAILED` và đóng channel, vì queue đã tồn tại.
2. `channel.prefetch(10)` trước khi consume (README node-eventing bắt buộc).
3. **Thuật toán gửi** (`sendNotification`), mỗi người nhận xử lý độc lập:
   1. Tìm log theo `(event_id, user_id = email)`. Nếu `SENT` thì bỏ qua.
   2. Nếu chưa có: `INSERT` trạng thái `PENDING`. Nếu lỗi `ER_DUP_ENTRY` thì return (consumer khác đã giành), không gửi.
   3. Gửi mail. Thành công thì `SENT`. Lỗi thì `FAILED`, `retry_count + 1`, rồi **throw** để cả message được retry. Người nhận đã `SENT` trước đó sẽ bị skip ở lần retry.
4. **Người nhận và nội dung:** copy nguyên văn subject/body từ `ProcessNotificationUseCaseImpl.java` (9 handler, 14 lần gửi):
   - `contract.disputed` gửi theo thứ tự admin (`ADMIN_EMAIL`), seller, buyer.
   - `escrow.penalized` lấy `buyerEmail` nếu `penalizedParty === 'BUYER'`, ngược lại lấy `sellerEmail`.
   - `escrow.*` dùng `escrowId`. `category.*` dùng `name`, `proposedByEmail`, `rejectionReason`.
5. **Retry:** 3 lần, 1s/2s, sau đó DLQ. Payload sai thì throw `NonRetryableError` và vào DLQ ngay.
6. **DLQ monitor:** mỗi 5 phút gọi `checkQueue` trên 9 DLQ, log warn khi `messageCount > 0` (`@nestjs/schedule`).
7. **Ghi `created_at`/`updated_at`** kiểu `DATETIME(6)` UTC như Java (JPA auditing).
8. Ack/nack thủ công (`noAck: false`). Graceful shutdown: `enableShutdownHooks()`, cancel consumer, đợi in-flight message xong, rồi đóng channel, connection và pool.

**Thay đổi hành vi có chủ đích (đã đồng ý 16/10/2026):** Java chỉ ném `InvalidEventPayloadException` khi field **sai kiểu**. Field **thiếu** (null) vẫn lọt qua và gửi mail tới `null`. Bản Nest sẽ validate các field bắt buộc là string không rỗng, thiếu thì vào DLQ ngay.

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
│   ├── application/
│   │   ├── commands/*.ts                               # 9 command type (thay record Java)
│   │   ├── ports/email.port.ts
│   │   ├── ports/notification-log.repository.ts
│   │   └── process-notification.service.ts            # 9 handler + sendNotification
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

| Env | Default | Ghi chú |
|---|---|---|
| `DB_HOST` / `DB_PORT` / `DB_NAME` | `localhost` / `3311` / `notification_db` | |
| `DB_USERNAME` / `DB_PASSWORD` | `root` / `password` | |
| `RABBITMQ_HOST` / `RABBITMQ_USERNAME` / `RABBITMQ_PASSWORD` | `localhost` / `guest` / `guest` | |
| `RABBITMQ_PORT` | `5672` | mới (Java hardcode) |
| `MAIL_HOST` / `MAIL_PORT` | `localhost` / `1025` | |
| `MAIL_FROM` | `noreply@agricontract.com` | mới (Java: `spring.mail.from`) |
| `ADMIN_EMAIL` | `admin@agricontract.com` | |
| `SERVER_PORT` | `8085` | |

## 6. Các mốc & task

Ước lượng cho 1 người (P3). Tổng khoảng **8–11 ngày làm việc**.

### M0 — Spike & chốt nền tảng (1 ngày)

- [x] Tạo Nest project, thêm `@agricontract/eventing` qua `file:`, build node-eventing, rồi chứng minh `import { consumeEvents } from '@agricontract/eventing'` chạy được cả trong `nest start` **và** Jest (D3). Nếu Jest không chạy được thì làm phương án dự phòng (dual build cho node-eventing).
- [ ] Mở PR vào node-eventing cho D6 (`DeliveryOutcome`), có review của người giữ package. *Diff đã soạn và test; chờ tạo PR.*
- [x] Team chốt các câu hỏi mở ở §10 (16/10/2026).

**Xong khi:** một consumer demo nhận được 1 message từ queue tạm, ack, và test Jest import được package.

**Kết quả spike (16/10/2026):**

| Kiểm tra | Node 22.23 | Node 24.14 |
|---|---|---|
| `tsc` / `nest build` (strict, `module: nodenext`, output CJS) | — | OK |
| App chạy, import package qua `require(esm)` | OK (`node dist/main.js`) | OK (`nest start`) |
| Consumer `notification-svc.m0-spike` nhận message phẳng, ack (0 ready / 0 unacked) | OK | OK |
| Jest 2/2 (load package thật + `handleDelivery` ack message không envelope) | OK | OK |
| ESLint | — | sạch |

- Jest với cấu hình Nest mặc định **fail** (`Must use import to load ES Module`). Transform `dist` cũng không cứu được, vì Jest phân loại ESM theo `"type": "module"` trước khi transform. Cách đã chạy: map package sang `src/index.ts` của node-eventing + `tsconfig.spec.json` compile CJS. Vì vậy **không cần dual build**. Đổi lại, test chạy trên source TS của node-eventing, còn app chạy trên `dist` của nó.
- Trên Windows, dừng tiến trình chạy qua `npx` có thể để lại tiến trình `node` con (consumer mồ côi vẫn giữ queue). Khi test tay M4 phải kiểm tra số consumer trên queue.

### M1 — Scaffold & hạ tầng trong app (1–2 ngày)

- [ ] Config module + validate env (D10).
- [ ] MySQL pool provider, AMQP provider (connection, channel, prefetch, shutdown).
- [ ] `topology.ts`: khai báo đủ 9 queue, 9 DLQ, 3 exchange, 3 DLX, viết dưới dạng **bảng data** để Phase 2 chỉ cần thêm dòng.
- [ ] Health endpoint `/actuator/health`.
- [ ] ESLint + Prettier theo mặc định Nest.

**Xong khi:** `docker compose up` bản Nest khởi động được trên RabbitMQ **đang có sẵn queue do Java tạo** mà không bị `PRECONDITION_FAILED`.

### M2 — Port nghiệp vụ (3–4 ngày)

- [ ] `NotificationLog` domain, repository MySQL (`findByEventIdAndRecipient`, `insert` bắt `ER_DUP_ENTRY`, `update`).
- [ ] `ProcessNotificationService`: 9 handler, thuật toán §4.3, subject/body copy nguyên văn.
- [ ] `legacy-payload.parsers.ts`: 9 parser, kể cả logic `penalizedParty`.
- [ ] `NotificationEventConsumer`: mỗi queue nối parser → service, đăng ký bằng `consumeEvents`.
- [ ] Nodemailer adapter.
- [ ] DLQ monitor.
- [ ] Unit test (Jest):
  - Port 6 case JUnit hiện có.
  - Thêm: đủ 9 handler (đúng người nhận, subject, body), `SENT` thì skip, `ER_DUP_ENTRY` thì không gửi, mail lỗi thì `FAILED` + throw, recipient thứ 2 lỗi rồi retry thì không gửi lại recipient 1, payload thiếu hoặc sai kiểu thì `NonRetryableError`, `penalizedParty` BUYER/SELLER.
  - Test `handleDelivery` với channel giả: retry 3 lần rồi nack(requeue=false).

**Xong khi:** coverage của `application/` và `messaging/` tương đương hoặc hơn Java, mọi dòng §4 có test.

### M3 — Đóng gói, compose, CI (1–2 ngày)

- [ ] `Dockerfile` multi-stage `node:22-alpine`, build context `backend/`: build `shared/node-eventing` trước, rồi `npm ci && npm run build` của service, image runtime chỉ có `dist` và `node_modules` production, chạy user `node`.
- [ ] `docker-compose.yml`:
  - Thêm service `notification-migrate` (`flyway/flyway`, mount `notification-service/db/migration`, `migrate`).
  - `notification-service` thêm `depends_on: notification-migrate: condition: service_completed_successfully` và trỏ build sang Dockerfile mới.
- [ ] CI (`.github/workflows/ci.yml`): thêm job `notification-service-node` gồm setup-node 22 (cache npm), build và test `backend/shared/node-eventing`, rồi `npm ci`, `lint`, `test`, `build` của service. **Chưa** bỏ Java ở bước này.

**Xong khi:** CI xanh cả Maven lẫn Node. `docker compose up` từ volume trống chạy Flyway V1 rồi app lên healthy.

### M4 — Parity test (1–2 ngày)

- [ ] Chạy toàn bộ `backend/bruno/notification-service/` (00–19) với **Java**, ghi lại kết quả. Dừng Java, chạy lại với **Nest** trên cùng broker và DB. Kết quả phải giống nhau, kể cả case duplicate/idempotency (02–03).
- [ ] Test tay các tình huống Bruno chưa phủ:
  - Tắt MailHog: message retry 3 lần rồi vào DLQ, log `FAILED`, DLQ monitor cảnh báo.
  - Publish JSON lỗi: vào DLQ ngay.
  - Restart RabbitMQ trong lúc chạy: service không crash, consume lại.
  - `docker stop` giữa chừng: message chưa ack được redeliver và không gửi trùng.
- [ ] Chạy luồng contract/escrow end-to-end (`backend/bruno/contract`, `escrow`) và kiểm tra MailHog nhận đủ mail.

**Xong khi:** có checklist parity ký xác nhận (đính trong PR).

### M5 — Cutover & dọn Java (1 ngày, 1 PR riêng)

- [ ] Xoá `backend/notification-service/` (Java), đổi tên `notification-service-nest` thành `notification-service`.
- [ ] Bỏ `<module>notification-service</module>` khỏi `backend/pom.xml`.
- [ ] **Xoá dòng `COPY notification-service/pom.xml ...`** trong Dockerfile của `api-gateway`, `user-service`, `product-service`, `contract-service`, `escrow-service`. Nếu sót, các service này build fail.
- [ ] Sửa đường dẫn trong compose và CI theo tên mới. Chạy lại `mvn clean verify` cho phần Java còn lại.
- [ ] Cập nhật `README.md` (bảng service: stack NestJS), `notification-service-phase2-design.md` (metadata `extends`) và `implementation-plan.md` T2/T10 (module/file path mới).
- [ ] Cắt tag `notification-java-final` trên commit trước PR này để rollback.

## 7. Cutover & rollback

- **Không chạy Java và Nest cùng lúc trên cùng queue.** Hai consumer sẽ chia message. Unique key ngăn phần lớn mail trùng nhưng log và DLQ khó đối soát. Quy trình: dừng Java, deploy Nest, verify bằng Bruno 00–03.
- Message tồn trong queue lúc chuyển sẽ được Nest xử lý. Dedup theo `(event_id, user_id)` dùng chung bảng nên không gửi lại mail Java đã `SENT`.
- **Rollback:** deploy lại image hoặc tag `notification-java-final`. An toàn vì schema vẫn là V1 và topology không đổi. Sau khi migration V2 Phase 2 chạy thì rollback về Java **không còn an toàn**. Vì vậy V2 chỉ được merge sau khi Nest đã chạy ổn định ít nhất 1 vòng demo.

## 8. Rủi ro

| Rủi ro | Ảnh hưởng | Giảm thiểu |
|---|---|---|
| ESM/CJS giữa node-eventing và Nest/Jest | Chặn từ ngày đầu | Spike M0, có sẵn phương án dual build |
| Khai báo queue lệch argument so với Java | Channel bị đóng, không consume được | Topology copy từ bảng §2. Test M1 trên broker có sẵn queue |
| Lỡ dùng `parseEnvelope`/`IdempotentConsumer` cho payload Phase 1 | Toàn bộ mail vào DLQ / gửi trùng khi retry | D4, D5. Bruno M4 bắt được ngay |
| Sót Dockerfile của service khác khi xoá module | Build compose fail | Checklist M5, CI build image |
| Subject/body lệch chữ | Bruno verify (so subject) fail | Copy nguyên văn. Snapshot test cho 9 handler |
| Phase 2 bị trễ vì migration | T2/T10 trễ | Migration nhỏ (~2 tuần). Freeze Java để không làm 2 lần |

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

| # | Câu hỏi | Chốt |
|---|---|---|
| 1 | Runtime cho Docker/CI | **Node 22 LTS** (máy dev Node 24 vẫn chạy được) |
| 2 | Payload thiếu field bắt buộc | **Vào DLQ ngay** — cải thiện so với Java (gửi mail tới `null`) |
| 3 | node-eventing | **Làm PR nới `HandleOutcome`** (D6). Dual build CJS **chỉ khi** spike M0 thất bại (D3) |
| 4 | Logger | **Nest `Logger` mặc định**, đủ cho Phase 1 (D12) |
| 5 | Health path | **Giữ `/actuator/health`** (D11) |
