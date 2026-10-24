---
name: notification-service-parity-m4
description: "Báo cáo parity M4: notification-service Java (Spring Boot) và NestJS chạy trên cùng broker, DB và MailHog. Bruno 00–19 và 5 tình huống ngoài Bruno."
status: "CHƯA ĐẠT: có 1 lỗi của Nest (D-1) và 1 khác biệt ngoài §6 cần quyết định (D-2). M4 chưa tick."
metadata:
  type: report
  plan: "planning/notification-service-nestjs-migration.md §4, §6 M4, §7"
  run_date: "24/10/2026"
---

# Parity M4: notification-service Java ↔ NestJS

## 0. Kết luận

| Hạng mục                                                     | Kết quả                                                                                                                                                                            |
| ------------------------------------------------------------ | ---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| Bruno 00–19                                                  | **Giống nhau.** Cả hai 18/20 request pass, 32/39 test. 17 và 19 fail ở **cả hai** bên vì lỗi của chính collection (§5.1), không phải của service                                   |
| `notification_logs` sau Bruno                                | **Giống nhau:** 14 dòng, cùng `event_id`/`user_id`/`status`/`retry_count`/`channel`, cùng thứ tự, subject giống từng byte, `created_at`/`updated_at` đều là UTC                       |
| MailHog sau Bruno                                            | **Giống nhau** về số mail (14), người nhận, thứ tự, From, subject và body sau khi giải mã. **Khác** ở cách mã hoá MIME trên đường truyền (D-2)                                     |
| DLQ sau Bruno                                                | 0 ở cả hai bên                                                                                                                                                                     |
| Tình huống 1, 3, 5                                           | Đạt ở cả hai bên                                                                                                                                                                   |
| Tình huống 2 (JSON lỗi)                                      | Kết quả cuối giống nhau (vào DLQ, không mail, không log). **Java retry 3 lần** rồi mới vào DLQ, Nest vào DLQ ngay (D-3)                                                             |
| Tình huống 2 (thiếu field)                                   | Khác như đã chấp nhận trong §6, nhưng hành vi thật của Java **khác với mô tả trong plan** (D-4)                                                                                     |
| Tình huống 4 (`docker stop` giữa chừng)                      | Đạt theo cách chạy đã ghi. Biến thể 4b (dừng khi đang gửi, rồi khởi động lại trong vòng 120 giây): **Nest làm mất mail**, message vào DLQ, dòng log kẹt ở `PENDING`. **Lỗi D-1** |
| End-to-end `bruno/contract`, `bruno/escrow` (plan M4 bullet 3) | **Chưa chạy**, đang bị chặn (§5.2)                                                                                                                                                 |

Không sửa code Nest. Chưa tick M4 trong plan §6. Cần team quyết định D-1 và D-2 (§4) trước khi chạy lại.

## 1. Môi trường

| Mục             | Giá trị                                                                                                                                                                                                      |
| --------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------ |
| Nhánh / commit  | `feat/notification-service-nest-m4` từ `main` `50c24f6` (đã có M3 #181). Code Nest và node-eventing không đổi kể từ M3                                                                                       |
| Java            | image `notification-service-java:m3-verify` (`1465e6c218aa`), chạy bằng `docker run` trên `backend_agricontract-net` với lệnh trong brief M4                                                                 |
| Nest            | `backend-notification-service:latest` build lại từ nhánh này (`b9ba188d60b2`), chạy bằng `docker compose up -d notification-migrate notification-service`                                                    |
| Hạ tầng dùng chung | `rabbitmq:3.13-management-alpine`, `mailhog/mailhog:latest`, `mysql:8.0` (`notification-db`, `@@system_time_zone = UTC`). Không có `docker-compose.override.yml`                                         |
| Bruno           | `@usebruno/cli@4.3.0`, `npx @usebruno/cli@4.3.0 run notification-service --env local` trong `backend/bruno/`                                                                                                  |
| `.env`          | tạo ở máy chạy test (gitignored, **không commit**) vì compose bắt buộc `GATEWAY_INTERNAL_SECRET`/`SERVICE_INTERNAL_SECRET` (`:?`) ngay cả khi chỉ chạy một service                                          |
| Dọn trạng thái  | `backend/bruno/notification-service/reset.sh` trước mỗi lượt (Linux, macOS, Git Bash trên Windows). Lượt B chạy reset sau lượt A, nên xoá cả dữ liệu lượt A                                                |

**So sánh env mặc định** (`application.yml` ↔ `env.validation.ts`):

| Env                                                         | Java                                 | Nest                                 | Ghi chú                                                                                                       |
| ----------------------------------------------------------- | ------------------------------------ | ------------------------------------ | ------------------------------------------------------------------------------------------------------------- |
| `DB_HOST` / `DB_PORT` / `DB_NAME`                           | `localhost` / `3311` / `notification_db` | giống                            |                                                                                                               |
| `DB_USERNAME` / `DB_PASSWORD`                               | `root` / `password`                  | giống                                |                                                                                                               |
| `RABBITMQ_HOST` / `RABBITMQ_USERNAME` / `RABBITMQ_PASSWORD` | `localhost` / `guest` / `guest`      | giống                                |                                                                                                               |
| `RABBITMQ_PORT`                                             | không có (cố định `5672`)            | `5672`                               | mới ở Nest, đúng plan §5                                                                                      |
| `MAIL_HOST` / `MAIL_PORT`                                   | `localhost` / `1025`                 | giống                                |                                                                                                               |
| From                                                        | `spring.mail.from`, mặc định `noreply@agricontract.com` (env tương ứng là `SPRING_MAIL_FROM`) | `MAIL_FROM`, mặc định giống | tên env khác; giá trị mặc định giống                                                                           |
| `ADMIN_EMAIL`                                               | `admin@agricontract.com`             | giống                                |                                                                                                               |
| `SERVER_PORT`                                               | `8085`                               | giống                                |                                                                                                               |
| Giá trị rỗng / port sai                                     | Spring nhận, lỗi lúc dùng            | thoát ngay lúc khởi động             | fail fast theo D10                                                                                            |
| Kết nối DB                                                  | `useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC` | `mysql2`, `timezone: 'Z'`  | cả hai ghi UTC (đã kiểm ở §2)                                                                                 |
| Actuator                                                    | `health, info`                       | chỉ `/actuator/health` (`/actuator/info` → 404) | D-6. Phía Java chỉ đọc từ config, chưa gọi thử khi chạy                                                       |

## 2. Bruno 00–19

Cột "Java" và "Nest" là kết quả Bruno CLI. Số mail/dòng lấy từ snapshot sau lượt chạy (§7).

| #   | Request                                    | Java                                   | Nest                                   | Khớp | Ghi chú                                                                                        |
| --- | ------------------------------------------ | -------------------------------------- | -------------------------------------- | ---- | ---------------------------------------------------------------------------------------------- |
| 00  | publish `contract.signed`                  | pass, 200, `routed: true`              | pass, 200, `routed: true`              | ✅   |                                                                                                |
| 01  | verify signed                              | pass, 3/3: 2 mail buyer + seller       | pass, 3/3                              | ✅   | 2 dòng `SENT`, buyer trước seller                                                              |
| 02  | publish lại cùng `eventId`                 | pass                                   | pass                                   | ✅   |                                                                                                |
| 03  | verify idempotency                         | pass, vẫn 2 mail                       | pass, vẫn 2 mail                       | ✅   | không thêm dòng log                                                                            |
| 04  | publish `contract.delivered`               | pass                                   | pass                                   | ✅   |                                                                                                |
| 05  | verify delivered                           | pass, 3/3: 1 mail seller               | pass, 3/3                              | ✅   |                                                                                                |
| 06  | publish `contract.cancelled`               | pass                                   | pass                                   | ✅   |                                                                                                |
| 07  | verify cancelled                           | pass, 3/3: 2 mail                      | pass, 3/3                              | ✅   | Nest gửi quoted-printable (D-2)                                                                |
| 08  | publish `contract.disputed`                | pass                                   | pass                                   | ✅   |                                                                                                |
| 09  | verify disputed                            | pass, 3/3: 3 mail                      | pass, 3/3                              | ✅   | thứ tự admin → seller → buyer ở cả hai. Nest gửi quoted-printable (D-2)                        |
| 10  | publish `escrow.locked`                    | pass                                   | pass                                   | ✅   |                                                                                                |
| 11  | verify locked                              | pass, 3/3: 1 mail seller               | pass, 3/3                              | ✅   | subject có em dash, mã hoá RFC 2047 khác nhau (D-2), giải mã ra giống nhau                     |
| 12  | publish `escrow.released`                  | pass                                   | pass                                   | ✅   |                                                                                                |
| 13  | verify released                            | pass, 3/3: 2 mail                      | pass, 3/3                              | ✅   | seller trước buyer ở cả hai                                                                    |
| 14  | publish `escrow.penalized`                 | pass                                   | pass                                   | ✅   |                                                                                                |
| 15  | verify penalized                           | pass, 3/3: 1 mail buyer                | pass, 3/3                              | ✅   | `penalizedParty = BUYER`                                                                       |
| 16  | publish `category.approved`                | pass                                   | pass                                   | ✅   |                                                                                                |
| 17  | verify approved                            | **fail**, 0/3 (`count` = 0)            | **fail**, 0/3 (`count` = 0)            | ✅   | mail đã gửi đúng ở cả hai (mail #13, dòng `SENT`). Lỗi collection §5.1                         |
| 18  | publish `category.rejected`                | pass                                   | pass                                   | ✅   |                                                                                                |
| 19  | verify rejected                            | **fail**, 0/4 (`count` = 0)            | **fail**, 0/4 (`count` = 0)            | ✅   | mail đã gửi đúng ở cả hai (mail #14, có lý do từ chối). Lỗi collection §5.1                    |

Sau Bruno: **14 mail, 14 dòng `SENT` (`retry_count = 0`, `channel = EMAIL`), 0 message trong 9 DLQ**, ở cả hai bên. Diff hai snapshot đã chuẩn hoá (thay timestamp và ID theo lượt) chỉ ra ba thứ: header MIME (D-2), cột `subject_hex` chỉ có ở snapshot lượt B (đã so tay: dòng escrow-locked ở cả hai là `…6C6F636B656420E28094207072…`), và số message trong queue `escrow-svc.*`/`contract-svc.*` (§5.2).

## 3. Năm tình huống ngoài Bruno

Mỗi tình huống dùng một `eventId` riêng (`evt-s<n>-java` / `evt-s<n>-nest`). DLQ là `notification-svc.contract.signed.dlq` và **cộng dồn** qua các tình huống, vì giữa các tình huống không reset.

| #   | Tình huống                                                                                   | Java                                                                                                                                                                                                                                                       | Nest                                                                                                                                                                                                                                                                                                                 | Đạt?                             |
| --- | -------------------------------------------------------------------------------------------- | ---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | -------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | -------------------------------- |
| 1   | MailHog tắt, publish `contract.signed`                                                       | 3 lần thử trong 8.1s (mỗi lần DNS `UnknownHostException` mất ~1.7s, cộng backoff 1s và 2s). Vào DLQ (1). buyer `FAILED`, `retry_count = 3`. seller không có dòng (handler dừng ở người nhận đầu). DLQ monitor cảnh báo lúc 13:29:00 (lần chạy định kỳ đầu tiên sau khi có message) | 3 lần thử trong 19.2s (lần 1 `ETIMEDOUT` sau 10s `connectionTimeout`, lần 2 và 3 `ESOCKET`). Vào DLQ (1). buyer `FAILED`, `retry_count = 3`. seller không có dòng. DLQ monitor cảnh báo lúc 13:37:53, cùng câu chữ | ✅ cả hai                        |
| 2a  | Body không phải JSON (`this is {not json`)                                                   | **Retry 3 lần** (`Retries exhausted` sau 3.15s, do `MessageConversionException` không nằm trong danh sách non-retryable), rồi vào DLQ (2). Không mail, không log                                                                                             | Vào DLQ sau **1** lần thử (`permanent: true`) (2). Không mail, không log                                                                                                                                                                                                                                            | Nest ✅, Java ❌ kỳ vọng (D-3)   |
| 2b  | JSON thiếu `buyerEmail`                                                                      | `INSERT` với `user_id = NULL` lỗi 1048, `DataIntegrityViolationException` bị nuốt → **bỏ qua buyer**, gửi mail cho seller (dòng `SENT`), ack. DLQ vẫn 2                                                                                                    | Vào DLQ ngay (`field "buyerEmail" must be a non-empty string`) (3). Không mail, không log                                                                                                                                                                                                                         | khác đã chấp nhận §6, xem D-4   |
| 3   | `docker restart rabbitmq` khi đang chạy                                                      | Không crash. 9 consumer về `1` sau ~12s. Message `contract.delivered` publish sau đó: 1 mail, 1 dòng `SENT`                                                                                                                                                 | Không crash (`RestartCount = 0`, vẫn `healthy`). Reconnect với backoff 1s → 2s → 4s → 8s, 9 consumer về `1` sau ~16s. Message publish sau đó: 1 mail, 1 dòng `SENT`                                                                                                                                                  | ✅ cả hai                        |
| 4   | MailHog tắt, publish, `docker stop` 2s sau, bật MailHog rồi start service                    | Dừng trong 5.8s, exit **143**. Spring đợi worker 5s rồi đóng channel: message về `ready`. buyer kẹt `PENDING`, `retry_count = 0`. Sau khi start: redeliver, buyer và seller mỗi người **1** mail, 2 dòng `SENT`                                            | Dừng trong 10.4s, exit **137** (bị SIGKILL). Drain 8s hết hạn, message về `ready`. buyer `FAILED`, `retry_count = 1` (lần thử 1 xong trước khi dừng). Sau khi start: redeliver, mỗi người **1** mail, 2 dòng `SENT` (`retry_count` buyer = 1)                                                                     | ✅ theo tiêu chí. Exit code: D-1 |
| 4b  | Như 4 nhưng `docker stop` 1s sau publish, start lại ngay (trong vòng lease 120s). Chỉ chạy với Nest | (đối chiếu: ở tình huống 4, Java cũng để buyer ở `PENDING` và được redeliver ~30s sau khi claim, và Java gửi lại thành công)                                                                                                                                          | Drain hết hạn lúc lần gửi còn treo. Pool MySQL đóng trong khi handler vẫn chạy (`notification log write failed`). buyer kẹt `PENDING`, exit 137. Sau khi start: 3 lần thử đều `another delivery is sending this email` → **vào DLQ (4), không mail nào được gửi**, dòng vẫn `PENDING`                                 | ❌ Nest: **lỗi D-1**             |
| 5   | Publish lại `eventId` của tình huống 4                                                       | Không mail mới, không dòng mới, `updated_at` không đổi                                                                                                                                                                                                     | Không mail mới, không dòng mới, `updated_at` không đổi                                                                                                                                                                                                                                                              | ✅ cả hai                        |

DLQ sau từng tình huống: Java 1 → 2 → 2 → 2 → 2 → 2. Nest 1 → 2 → **3** → 3 → 3 → 3 (→ 4 sau 4b). Chênh lệch từ 2b là khác biệt đã chấp nhận ở §6.

## 4. Khác biệt ngoài §6

### D-1. Nest làm mất mail khi bị dừng trong lúc đang gửi rồi khởi động lại trong vòng 120 giây (**lỗi, chặn M4**)

**Tái hiện (tình huống 4b):** MailHog tắt, publish `contract.signed`, sau 1s chạy `docker stop notification-service` (timeout mặc định 10s), rồi bật MailHog và start service ngay.

**Ghi nhận:**

```text
13:40:55 LOG  Cancelled 9 consumers; waiting for 1 in-flight message(s)
13:41:03 WARN 1 message(s) still in flight after 8000ms; they stay unacked and will be redelivered
13:41:04 WARN ... attempt 1 ... notification log write failed (Error)      <- pool đã đóng, handler vẫn chạy
13:41:05 WARN ... attempt 2 ... notification log write failed (Error)
         exit=137 (SIGKILL lúc 10.4s)
evt-s4b-nest  buyer1@test.com  PENDING  0  created 13:40:54.500304  updated 13:40:54.500304
--- start lại 13:41:09 ---
13:41:10 WARN ... attempt 1 ... another delivery is sending this email; retrying later
13:41:11 WARN ... attempt 2 ... another delivery is sending this email; retrying later
13:41:13 ERROR Event dead-lettered {"attempts":3,"permanent":false,...}
MailHog total=0, DLQ 3 → 4, dòng vẫn PENDING
```

**Nguyên nhân** (đọc code, chưa sửa):

1. `beforeApplicationShutdown` chỉ đợi tối đa `DRAIN_TIMEOUT_MS = 8_000` ([notification-event.consumer.ts:31](../../backend/notification-service-nest/src/infrastructure/messaging/notification-event.consumer.ts#L31), [:137](../../backend/notification-service-nest/src/infrastructure/messaging/notification-event.consumer.ts#L137)). Sau đó Nest đóng channel và pool, nhưng vòng retry của `handleDelivery` không bị dừng. Lần gửi đang treo (tối đa 10s connect, 10s greeting, 30s socket) kết thúc sau khi pool đã đóng, nên không ghi được `FAILED`, và dòng vẫn ở `PENDING` với `updated_at` là lúc claim. Timer retry còn sống nên process không tự thoát và bị SIGKILL (137). Java thoát sạch (143) trong 5.8s.
2. Lease của claim là `CLAIM_LEASE_MS = 120_000` ([notification-sender.ts:18](../../backend/notification-service-nest/src/application/notification-sender.ts#L18)), dài hơn nhiều so với cửa sổ retry (1s + 2s cộng thời gian gửi). Message được redeliver trong vòng 120s sẽ gặp `IN_PROGRESS` ở cả 3 lần thử rồi vào DLQ. Mail không bao giờ được gửi, và không còn ai lấy lại dòng `PENDING` đó.

Điểm 2 không chỉ xảy ra với `docker stop`. Mọi lần process chết trong lúc gửi (OOM, SIGKILL, mất điện) rồi lên lại trong vòng 2 phút đều cho cùng kết quả. Java ở cùng tình huống thì gửi lại (tình huống 4: buyer `PENDING`, redeliver ~30s sau khi claim, gửi thành công). Điều này trái với §6 ("Kết quả cuối cùng phải giống nhau").

**Hướng sửa có thể** (cần team chọn, chưa làm):
- Shutdown: khi drain hết hạn thì dừng hẳn vòng retry của message đang chạy (không thử tiếp, không ghi DB sau khi pool đóng), để process thoát trước SIGKILL. Hoặc tăng `stop_grace_period` trong compose và drain timeout cho đủ một lần gửi.
- Lease: cho cửa sổ retry dài hơn lease, hoặc rút lease cho khớp timeout SMTP, hoặc khi gặp `IN_PROGRESS` ở lần thử cuối thì nack có requeue thay vì dead-letter. Mỗi cách đổi thuật toán §4.3 nên cần review riêng.

### D-2. Mã hoá MIME của mail khác nhau (giải mã ra giống nhau; cần quyết định)

| Header                      | Java (JavaMail)                                                 | Nest (nodemailer)                                                                                                                                         |
| --------------------------- | --------------------------------------------------------------- | --------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `Content-Type`              | `text/plain; charset=UTF-8`                                     | `text/plain; charset=utf-8`                                                                                                                               |
| `Content-Transfer-Encoding` | luôn `7bit`                                                     | `7bit` khi mọi dòng ngắn; `quoted-printable` với soft line break (`=\r\n`) khi body dài hơn 76 ký tự: 6/14 mail (cancelled ×2, disputed ×3, category.rejected) |
| Subject có ký tự non-ASCII  | `=?UTF-8?Q?[AgriContract]_Escrow_locked_=E2=80=94_proceed_with_delivery?=` | `=?UTF-8?Q?=5BAgriContract=5D_Escrow_locked_?= =?UTF-8?Q?=E2=80=94_proceed_with_delivery?=`                                                               |

Client mail chuẩn MIME hiển thị giống nhau. Ảnh hưởng thực tế là ở chỗ đọc **nội dung thô**: search `containing` của MailHog và các assert trên `Content.Body` trong Bruno. Một cụm từ nằm vắt qua soft line break, hoặc chứa `=` hay ký tự non-ASCII, sẽ không còn tìm được. Bruno 19 vẫn pass (`Trung ten voi danh muc da co` nằm gọn sau chỗ ngắt), nhưng sẽ fail nếu chỗ ngắt rơi vào giữa cụm từ.

**Đề xuất:** chấp nhận và thêm vào danh sách §6 (nodemailer không có tuỳ chọn ép `7bit` cho body dài; JavaMail cũng tự chuyển sang quoted-printable khi body có ký tự non-ASCII). Nếu không chấp nhận thì cần sửa adapter email, nghĩa là sửa code Nest, cần đồng ý trước.

### D-3. Java retry JSON lỗi 3 lần (lệch so với kỳ vọng ở phía Java)

Kỳ vọng "vào DLQ ngay, không retry" chỉ đúng với Nest. Java đưa `MessageConversionException` qua retry interceptor (chỉ `InvalidEventPayloadException` là non-retryable), nên thử 3 lần trong ~3s rồi mới vào DLQ. Kết quả cuối giống nhau: 1 message trong DLQ, không mail, không log. Java đang freeze nên không sửa. **Đề xuất:** ghi nhận đây là cải thiện có chủ đích của Nest, thêm vào §6.

### D-4. Mô tả hành vi Java khi thiếu field trong plan chưa đúng

§4 và §10 mục 2 của plan ghi "Java gửi mail tới `null`". Thực tế: `INSERT` dòng log với `user_id = NULL` vi phạm `NOT NULL`, Java nuốt `DataIntegrityViolationException` và **bỏ qua** người nhận đó, vẫn gửi cho những người còn lại, rồi ack. Không có mail nào gửi tới `null`, và không có message nào vào DLQ. Nest vào DLQ ngay và không gửi cho ai. Khác biệt này vẫn thuộc nhóm đã chấp nhận, chỉ cần sửa câu chữ trong plan cho đúng.

### D-5. DLQ monitor: thời điểm chạy lần đầu

Java dùng `@Scheduled(fixedDelay = 300000)` không có `initialDelay`, nên chạy **ngay khi khởi động** rồi mới tới mỗi 5 phút (thấy lúc 13:31:26, khi Java khởi động lại ở tình huống 4). Nest dùng `@Interval(300_000)`, nên lần đầu là 5 phút sau khi khởi động. Chỉ ảnh hưởng log cảnh báo. Đề xuất chấp nhận.

### D-6. `/actuator/info`

Java mở `health, info` (`management.endpoints.web.exposure.include`). Nest chỉ có `/actuator/health`, còn `/actuator/info` trả 404. Plan D11 chỉ yêu cầu health. Phía Java mới kiểm qua config, chưa gọi thử. Đề xuất chấp nhận, trừ khi có chỗ nào đang gọi `/actuator/info`.

**Không tính là khác biệt:** thời gian giữa các lần retry phụ thuộc vào cách SMTP lỗi (DNS fail nhanh ở Java, connect timeout 10s ở Nest). Backoff 1s và 2s vẫn giống nhau. `retry_count` ở tình huống 4 lệch (0 và 1) là do tại thời điểm dừng một bên đã xong lần thử đầu, bên kia chưa.

## 5. Vấn đề của test, không phải của service

### 5.1. Bruno 17 và 19 luôn fail

17 và 19 search MailHog theo `notif_category_*_id` (`categoryId`). Template `category.approved`/`category.rejected` (Java và Nest giống nhau) **không** chứa `categoryId`, nên search luôn ra `count = 0`. Request 16 đã lưu `notif_category_approved_name` nhưng 17 không dùng. Đề xuất sửa collection, search theo tên danh mục, ở một PR riêng (ngoài phạm vi M4).

### 5.2. End-to-end chưa chạy: queue của service khác đang giữ event giả

Exchange `agricontract.contracts` và `agricontract.escrow` còn bind tới queue của escrow-service và contract-service. Mỗi lần Bruno hay tình huống publish thì các queue này cũng nhận một bản. Sau hai lượt, các queue này đang giữ:

```text
contract-svc.escrow.locked      2
contract-svc.escrow.released    2
escrow-svc.contract.cancelled   2
escrow-svc.contract.delivered   4
escrow-svc.contract.signed     15
escrow-svc.contract.signed.dlq  1   (có từ trước M4)
```

Brief M4 không cho đụng tới các queue này. Nếu khởi động escrow-service và contract-service để chạy `bruno/contract` và `bruno/escrow`, hai service sẽ xử lý các event giả (`contractId` như `notif-signed-…`, `s4-nest`) và ghi vào `escrow_db`/`contract_db`. Cần quyết định trước khi chạy e2e: purge các queue này, hoặc chấp nhận side effect.

### 5.3. MailHog lưu mail trong bộ nhớ

Image `mailhog/mailhog` mặc định không lưu mail ra đĩa (volume `mailhog_data` không được dùng), nên mỗi lần `docker stop`/`start mailhog` thì mail cũ mất. Số mail ở §3 được đếm ngay trong từng tình huống, trước khi MailHog khởi động lại.

## 6. Checklist parity (người review ký)

Đánh dấu khi đồng ý với kết quả và cách xử lý ở §4. Agent không ký.

- [ ] §4.1 Topology: cả hai khởi động trên broker có sẵn queue mà không gặp `PRECONDITION_FAILED`. 9 queue, 9 DLQ, mỗi queue chính đúng 1 consumer
- [ ] §4.2 `prefetch(10)`: theo unit test M2 (không đo được qua Bruno)
- [ ] §4.3 Thuật toán gửi: idempotency (Bruno 02–03, tình huống 5) giống nhau. **Không đạt ở 4b (D-1)**
- [ ] §4.4 Người nhận, nội dung, thứ tự: giống nhau ở 14/14 mail (decoded). Mã hoá MIME khác nhau (D-2), cần quyết định
- [ ] §4.5 Retry 3 lần (1s/2s), rồi DLQ: giống nhau ở tình huống 1. Payload sai vào DLQ ngay: Nest đạt, Java retry (D-3)
- [ ] §4.6 DLQ monitor 5 phút: cả hai cảnh báo cùng câu chữ. Thời điểm lần đầu khác (D-5)
- [ ] §4.7 `created_at`/`updated_at` UTC, `DATETIME(6)`: giống nhau
- [ ] §4.8 Ack thủ công và graceful shutdown: redeliver đạt. **Shutdown Nest bị SIGKILL (D-1)**
- [ ] Bruno 00–19 giống nhau (17 và 19 fail ở cả hai bên do collection, §5.1)
- [ ] 5 tình huống ngoài Bruno, cả hai bên (§3)
- [ ] End-to-end contract/escrow (§5.2): **chưa chạy**
- [ ] Các khác biệt D-2…D-6: chấp nhận / không chấp nhận (ghi rõ)

| Vai trò  | Tên | Ngày | Chữ ký / approve PR |
| -------- | --- | ---- | ------------------- |
| Reviewer |     |      |                     |
| Owner P3 |     |      |                     |

## 7. Output thô

### 7.1. Trước và sau mỗi lượt

| Thời điểm (UTC)                          | `notification_logs` | MailHog | 9 DLQ (tổng)                    | consumer `notification-svc.*` |
| ---------------------------------------- | ------------------- | ------- | ------------------------------- | ----------------------------- |
| A, trước (13:16:03, sau reset)           | 0                   | 0       | 0                               | 0                             |
| A, sau Bruno (13:23:21)                  | 14 (`SENT`)         | 14      | 0                               | 9 × 1                         |
| A, cuối (13:32:04, sau tình huống 5)     | 19                  | 2¹      | 2 (`contract.signed.dlq`)       | 9 × 1 → 0 sau khi dừng        |
| B, trước (13:32:47, sau reset)           | 0                   | 0       | 0                               | 0                             |
| B, sau Bruno (13:34:01)                  | 14 (`SENT`)         | 14      | 0                               | 9 × 1                         |
| B, cuối (13:41:48, sau 4b)               | 19                  | 0¹      | 4 (`contract.signed.dlq`)       | 9 × 1 → 0 sau `docker compose stop` |

¹ MailHog vừa khởi động lại (§5.3).

Trước khi chạy lượt A, reset đã purge 2 message còn sót trong `notification-svc.category.rejected.dlq` và `notification-svc.escrow.locked.dlq` (có từ trước M4).

### 7.2. `notification_logs` sau Bruno, lượt A (Java)

```text
event_id                           user_id                 status retry channel created_at                 updated_at
evt-signed-1792848133047           buyer1@test.com         SENT   0     EMAIL   2026-10-24 13:22:13.499245 2026-10-24 13:22:13.733842
evt-signed-1792848133047           seller1@test.com        SENT   0     EMAIL   2026-10-24 13:22:13.760556 2026-10-24 13:22:13.787268
evt-delivered-1792848143971        seller1@test.com        SENT   0     EMAIL   2026-10-24 13:22:24.084425 2026-10-24 13:22:24.113478
evt-cancelled-1792848149286        buyer1@test.com         SENT   0     EMAIL   2026-10-24 13:22:29.364477 2026-10-24 13:22:29.392534
evt-cancelled-1792848149286        seller1@test.com        SENT   0     EMAIL   2026-10-24 13:22:29.412758 2026-10-24 13:22:29.437979
evt-disputed-1792848154610         admin@agricontract.com  SENT   0     EMAIL   2026-10-24 13:22:34.682428 2026-10-24 13:22:34.711439
evt-disputed-1792848154610         seller1@test.com        SENT   0     EMAIL   2026-10-24 13:22:34.735299 2026-10-24 13:22:34.766988
evt-disputed-1792848154610         buyer1@test.com         SENT   0     EMAIL   2026-10-24 13:22:34.788702 2026-10-24 13:22:34.819442
evt-escrow-locked-1792848159890    seller1@test.com        SENT   0     EMAIL   2026-10-24 13:22:39.966107 2026-10-24 13:22:39.997738
evt-escrow-released-1792848165346  seller1@test.com        SENT   0     EMAIL   2026-10-24 13:22:45.408991 2026-10-24 13:22:45.432762
evt-escrow-released-1792848165346  buyer1@test.com         SENT   0     EMAIL   2026-10-24 13:22:45.453499 2026-10-24 13:22:45.479242
evt-escrow-penalized-1792848170597 buyer1@test.com         SENT   0     EMAIL   2026-10-24 13:22:50.655843 2026-10-24 13:22:50.678030
evt-cat-approved-1792848171458     seller1@test.com        SENT   0     EMAIL   2026-10-24 13:22:51.514492 2026-10-24 13:22:51.548857
evt-cat-rejected-1792848176752     buyer1@test.com         SENT   0     EMAIL   2026-10-24 13:22:56.832847 2026-10-24 13:22:56.890126
```

Lượt B (Nest) có cùng 14 dòng, cùng thứ tự, `status`/`retry_count`/`channel`/`user_id` giống hệt, timestamp từ 13:33:17 tới 13:33:57 UTC. Đồng hồ DB lúc chụp: `NOW(6) = UTC_TIMESTAMP(6)`, `@@system_time_zone = UTC`. `created_at` của từng dòng khớp với `Created` (UTC) của mail tương ứng trong MailHog, cùng giây, chênh vài trăm ms.

### 7.3. MailHog sau Bruno (theo thứ tự gửi, giống nhau ở cả hai lượt)

```text
#1  buyer1   [AgriContract] Contract has been signed
#2  seller1  [AgriContract] Contract has been signed
#3  seller1  [AgriContract] Buyer confirmed receipt of goods
#4  buyer1   [AgriContract] Contract has been cancelled              body "... Reason: Buyer withdrew due to price change"
#5  seller1  [AgriContract] Contract has been cancelled
#6  admin    [AgriContract] [ADMIN] New dispute requires resolution  body "... Reason: Goods quality does not match spec"
#7  seller1  [AgriContract] [ADMIN] New dispute requires resolution
#8  buyer1   [AgriContract] [ADMIN] New dispute requires resolution
#9  seller1  [AgriContract] Escrow locked — proceed with delivery
#10 seller1  [AgriContract] Payment has been released
#11 buyer1   [AgriContract] Payment has been released
#12 buyer1   [AgriContract] Contract penalty notification
#13 seller1  [AgriContract] Category proposal approved               body "Your proposed category \"Ca Phe <ts>\" has been approved."
#14 buyer1   [AgriContract] Category proposal rejected               body "... Reason: Trung ten voi danh muc da co"
From: noreply@agricontract.com (tất cả)
```

### 7.4. Dòng log của các tình huống

```text
-- Java (A, cuối)
evt-s1-java   buyer1@test.com   FAILED 3  2026-10-24 13:23:57.563096 2026-10-24 13:24:05.683423
evt-s2b-java  seller1@test.com  SENT   0  2026-10-24 13:29:54.448910 2026-10-24 13:29:54.470256
evt-s3-java   seller1@test.com  SENT   0  2026-10-24 13:30:33.281080 2026-10-24 13:30:33.303850
evt-s4-java   buyer1@test.com   SENT   0  2026-10-24 13:30:56.989525 2026-10-24 13:31:27.047615
evt-s4-java   seller1@test.com  SENT   0  2026-10-24 13:31:27.068035 2026-10-24 13:31:27.097437
-- Nest (B, cuối)
evt-s1-nest   buyer1@test.com   FAILED  3  2026-10-24 13:34:52.468732 2026-10-24 13:35:11.660926
evt-s3-nest   seller1@test.com  SENT    0  2026-10-24 13:39:00.167102 2026-10-24 13:39:00.182682
evt-s4-nest   buyer1@test.com   SENT    1  2026-10-24 13:39:19.237643 2026-10-24 13:40:06.578251
evt-s4-nest   seller1@test.com  SENT    0  2026-10-24 13:40:06.586149 2026-10-24 13:40:06.600096
evt-s4b-nest  buyer1@test.com   PENDING 0  2026-10-24 13:40:54.500304 2026-10-24 13:40:54.500304
```

## 8. Cách chạy lại

```bash
cd backend
docker compose up -d rabbitmq mailhog notification-db
bruno/notification-service/reset.sh                       # phải kết thúc bằng "OK: no consumer ..."

# Lượt A
docker run -d --name notification-service-java --network backend_agricontract-net -p 8085:8085 \
  -e DB_HOST=notification-db -e DB_PORT=3306 -e DB_NAME=notification_db \
  -e DB_USERNAME=root -e DB_PASSWORD=password \
  -e RABBITMQ_HOST=rabbitmq -e MAIL_HOST=mailhog -e MAIL_PORT=1025 \
  notification-service-java:m3-verify
(cd bruno && npx @usebruno/cli@4.3.0 run notification-service --env local)
bruno/notification-service/reset.sh --status
docker stop notification-service-java && docker rm notification-service-java

# Lượt B
bruno/notification-service/reset.sh
docker compose up -d notification-migrate notification-service
(cd bruno && npx @usebruno/cli@4.3.0 run notification-service --env local)
docker compose stop notification-service
```

Bruno CLI ghi các biến `bru.setEnvVar` ngược vào `bruno/environments/local.bru`. Chạy xong thì `git restore backend/bruno/environments/local.bru`.

Các tình huống ở §3 publish qua `POST /api/exchanges/%2f/agricontract.contracts/publish` (management API, `delivery_mode: 2`, `content_type: application/json`), giống cách Bruno publish.
