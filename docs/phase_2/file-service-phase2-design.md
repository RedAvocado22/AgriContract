---
name: file-service-phase2-design
description: "File-service — lưu trữ file tập trung cho toàn hệ thống, async processing (virus-scan/email-parse), EUDR-compliant retention. Nguồn: design session 05/07/2026."
status: DESIGNED — chưa code.
metadata:
    type: design
    phase: 2
    extends: "services.md § file-service (gap #6 — pain point CHÍNH: async processing)"
    related: "product-phase2-design.md §2.1/§4 (originalKmlFileId, cadastralExtractFileId); milestone-escrow-phase2-design.md §2.2/§3.2 (sellerEvidenceFileId/buyerEvidenceFileId, bằng chứng bất khả kháng); inspection-phase2-design.md §3.6 (PDF report SGS/Bureau Veritas qua intake@...); hash-chain-phase2-design.md §2.3 (GenerateEUDRReport, Feign không read model riêng)"
---

## 1. Bối cảnh & Scope

**6 dependency đã cam kết từ service khác (không phải "nice to have" — các service đó đã "đóng session" dựa trên giả định file-service tồn tại và hoạt động đúng như mô tả):**

1. `sellerEvidenceFileId`/`buyerEvidenceFileId` — ảnh cân hàng lúc milestone.
2. Bằng chứng bất khả kháng — Admin xét qua file-service.
3. `cadastralExtractFileId` — scan/PDF trích lục địa chính.
4. File KML gốc lúc `ImportPlotsFromKML` — **quyết định (05/07/2026, xem §7): giữ lại**, qua `originalKmlFileId`.
5. PDF report SGS/Bureau Veritas ingest qua `intake@...` — tự động, không qua tay Admin lúc nhận.
6. Output PDF/CSV báo cáo EUDR (`GenerateEUDRReport`) — chiều ngược lại, file-service **nhận** file do service khác sinh ra, không phải nơi tự build nội dung report.

**3 quyết định kiến trúc đã chốt sẵn ở service khác, file-service phải tương thích:**

- `fileId` mọi nơi tham chiếu là **plain UUID**, không `REFERENCES` cross-service — đúng pattern đã áp cho `report_id`/`sellerId`/`signerUserId` ở các service khác.
- File-service tự giữ **hash riêng** để detect tampering ở tầng MinIO storage — tách biệt hoàn toàn với `signedContentHash`/`reportHash` đã có ở service khác (2 lớp hash khác mục đích).
- Pain point đặt tên sẵn: **async processing** — ít nhất 1 luồng không chạy đồng bộ trong request (virus-scan hoặc parse email SGS), cần RabbitMQ.

**Thứ tự ưu tiên đã chốt:** file-service → pricing-service → analytics-service.

**Đã loại hẳn:** `search-service` — không tồn tại như 1 service riêng, thay bằng 2 param filter trong `product-service`.

---

## 2. Domain Model — `File`

Aggregate root duy nhất, đơn giản, **agnostic với nghiệp vụ** (chốt 05/07/2026 — xem §2.1) — file-service không biết và không cần biết 1 file dùng để làm gì (evidence, cadastral, report...), chỉ biết nó là 1 blob + metadata kỹ thuật. Ý nghĩa nghiệp vụ nằm ở phía service đang giữ `fileId` đó (tên field tự nói lên ý nghĩa, VD `cadastralExtractFileId`).

| Field           | Loại                                                       | Ghi chú                                                                                                                                                                                          |
| --------------- | ---------------------------------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------ |
| `fileId`        | UUID                                                       | Plain UUID, đúng pattern đã chốt toàn hệ thống                                                                                                                                                   |
| `storageHash`   | VARCHAR(64)                                                | Hash riêng của file-service, detect tampering ở MinIO — tách biệt `signedContentHash`/`reportHash`                                                                                               |
| `uploadedBy`    | VARCHAR(255)                                               | `userId` (upload trực tiếp) hoặc `serviceId` (VD `"audit-service"`, cho `SYSTEM_GENERATED`). Plain, không FK cross-service                                                                       |
| `ingestChannel` | Enum (`DIRECT_UPLOAD`, `EMAIL_INTAKE`, `SYSTEM_GENERATED`) | Quyết định bởi **entrypoint nào được gọi** (§3), không phải giá trị caller tự khai — xem trade-off §3                                                                                            |
| `contentType`   | VARCHAR(100)                                               | `image/jpeg`, `image/png`, `application/pdf`, `text/csv`...                                                                                                                                      |
| `fileSize`      | BIGINT                                                     | Bytes                                                                                                                                                                                            |
| `status`        | Enum (`PROCESSING`, `READY`, `FAILED`)                     | Trạng thái **kỹ thuật** (file toàn vẹn chưa) — không liên quan gì tới việc nội dung có được Admin duyệt hay không (đó là state machine riêng, thuộc service khác)                                |
| `failureReason` | TEXT, nullable                                             | Chỉ có giá trị khi `status = FAILED` — VD `"virus detected"`, `"email parse error: missing attachment"`. Giữ `status` gọn (3 giá trị), chi tiết debug nằm ở field phụ, không phình state machine |
| `attached`      | Boolean, default `FALSE`                                   | Cờ xác nhận đã có service khác lưu `fileId` này vào entity của nó — dùng cho orphan-cleanup (§7)                                                                                                 |
| `createdAt`     | Timestamp                                                  |                                                                                                                                                                                                  |

### 2.1 `ingestChannel` — quyết định bởi entrypoint, không phải param caller tự khai

**Trade-off đã cân nhắc (05/07/2026):**

- **Hướng A — 1 API generic** (`store(bytes, ingestChannel, uploadedBy)`, mọi nguồn dùng chung): ít code, dễ mở rộng — nhưng `ingestChannel` trở thành param caller tự khai, ai gọi cũng có thể tự xưng `SYSTEM_GENERATED` để né virus-scan. Lỗ hổng bảo mật thật, không phải lý thuyết.
- **Hướng B — 3 entrypoint mỏng, dùng chung 1 core logic bên trong** (đã chốt, xem §3): ranh giới bảo mật theo đúng trust boundary thật (public vs internal network) — `ingestChannel` do chính entrypoint quyết định, không thể giả mạo vì con đường gọi vào đã tự quyết định channel.

**Chốt: Hướng B.**

### 2.2 State machine

```
PROCESSING → READY
           ↘ FAILED
```

Dùng chung 1 enum cho cả 3 channel (không tách nhánh riêng theo channel) — `SYSTEM_GENERATED` nhảy thẳng `PROCESSING → READY` gần như tức thì (không qua scan/parse), vẫn đi qua đúng state đó để nhất quán. Lý do fail khác nhau tuỳ channel (virus vs email-parse-error) nằm ở `failureReason`, không cần thêm state.

### 2.3 Storage key trong MinIO

**Chốt (review pass 2, 05/07/2026):** object key = chính `fileId` (UUID), 1 bucket phẳng — không phân cấp theo `contentType`/`ingestChannel` (VD không tạo `images/`, `documents/`). Không ai truy cập MinIO trực tiếp để "duyệt tìm" file — mọi truy xuất đi qua `GetFile(fileId)`, luôn có `fileId` trong tay trước khi cần đọc. Phân loại ảnh/tài liệu đã có sẵn ở `contentType` trong bảng `file` (DB là nguồn sự thật cho metadata, storage key chỉ là địa chỉ vật lý). Thêm hierarchy chỉ có giá trị nếu cần lifecycle policy theo ngày/tháng hoặc partition ở scale rất lớn — ngoài scope đồ án, không thêm structure không cần dùng.

---

## 3. Entrypoints — 3 cổng vào tách theo trust boundary (Hướng B)

| Entrypoint          | Ai gọi                                                                                                              | `ingestChannel`    | Validate trước khi nhận                                                                        |
| ------------------- | ------------------------------------------------------------------------------------------------------------------- | ------------------ | ---------------------------------------------------------------------------------------------- |
| `UploadFile(file)`  | Public API, JWT seller/admin                                                                                        | `DIRECT_UPLOAD`    | `contentType` ∈ {JPG, PNG, PDF}, `fileSize` ≤ **10MB** (chốt 05/07/2026)                       |
| Email intake bridge | Scheduled job nội bộ (§4.1), không expose API                                                                       | `EMAIL_INTAKE`     | `fileSize` ≤ **20MB** (chốt review pass 2, 05/07/2026 — cao hơn `DIRECT_UPLOAD` vì PDF report SGS/BV thường nhiều trang/nhúng ảnh chứng chỉ), check **streaming** trong lúc `EmailParseConsumer` đang đọc MIME part, không load hết vào memory rồi mới check — tránh OOM nếu mailbox bị compromise hoặc bên gửi nhầm file khủng. Vượt cap → lỗi nghiệp vụ, route `email_intake_failure` (không phải lỗi dev) |
| `store()` nội bộ    | Service-to-service (mTLS/service token, không phải JWT user) — VD `audit-service` gọi khi `GenerateEUDRReport` xong | `SYSTEM_GENERATED` | Không cần virus-scan (nội dung do chính hệ thống tạo, tin được)                                |

Core logic (tính `storageHash`, lưu MinIO, insert `File` metadata) dùng chung 1 hàm nội bộ cho cả 3 entrypoint — không trùng lặp code, chỉ khác lớp validate/auth phía trước.

---

## 4. Async Processing Pipeline

**Chốt (05/07/2026):** tách riêng 2 consumer theo **loại công việc** (parsing concern vs security concern), không gộp branching vào 1 consumer duy nhất.

**Trade-off:** gộp 1 consumer rẽ nhánh if/else theo `ingestChannel` thì ít code hơn ban đầu, nhưng 2 việc (virus-scan vs email-parse) khác nhau về bản chất domain, khác tải (CPU-bound nhanh vs I/O-bound nặng), và nếu gộp chung thread pool thì 1 pipeline bị treo (VD parser loop vô hạn với file SGS định dạng lạ) sẽ kéo cả pipeline kia chết theo — 2 việc không liên quan lại phụ thuộc lẫn nhau. Tách riêng cho phép scale và fail-isolate độc lập, đúng tinh thần đã áp cho `EscrowEventConsumer` ở Phase 1.

**Bug phát hiện ở review pass 2 (05/07/2026) — đã fix:** thiết kế gốc coi 2 consumer là 2 pipeline **song song, độc lập, mỗi file chỉ đi qua đúng 1 trong 2** — hệ quả là file tới từ `EMAIL_INTAKE` (nguồn ngoài internet, unauthenticated sender, đúng vector tấn công phổ biến nhất) không bao giờ được virus-scan, trong khi `DIRECT_UPLOAD` (nguồn tin cậy hơn, đã qua JWT auth) lại là kênh duy nhất bắt buộc scan. Root cause: tách consumer theo **nguồn gốc file** (`ingestChannel`) thay vì theo **loại công việc cần làm** — dẫn tới giả định sai "1 file 1 pipeline" thay vì đúng bản chất "1 file có thể cần đi qua nhiều bước xử lý nối tiếp nhau".

**Fix:** nối chuỗi thay vì tách song song — `EmailParseConsumer` chỉ làm đúng việc của nó (extract MIME attachment, validate format/size §3), parse xong thì **publish tiếp** vào cùng queue `file-service.virus-scan` mà `DIRECT_UPLOAD` đang dùng, để `VirusScanConsumer` trở thành **điểm chốt duy nhất** quyết định `markReady()` — bất kể file tới từ đâu. Không tạo pipeline scan riêng cho email để tránh trùng logic virus-scan ở 2 chỗ.

```
file.uploaded.direct  → queue: file-service.virus-scan   → VirusScanConsumer  → markReady()/markFailed()
file.email.received   → queue: file-service.email-parse  → EmailParseConsumer
                                                              ├─ parse OK   → publish vào file-service.virus-scan (dùng lại VirusScanConsumer) → markReady()/markFailed()
                                                              └─ parse FAIL → markFailed() + insert email_intake_failure
```

`status` vẫn ở `PROCESSING` xuyên suốt cả 2 bước (parse rồi scan) — consumer downstream (contract-service/inspection-service) không thấy khác biệt gì, vẫn chỉ chờ đúng 1 event `file.ready`/`file.failed` như §5 đã định nghĩa. Event contract §5 không đổi.

Retry: **3 lần**, backoff cố định qua RabbitMQ dead-letter-exchange + TTL (không cần exponential backoff tự code cho scope này).

**DLQ — 2 hàng đợi riêng, ý nghĩa khác nhau:**

- `file-service.virus-scan.dlq` — lỗi hệ thống tạm thời (MinIO down...), alert kỹ thuật (dev/log), không phải nghiệp vụ. Nhận cả file từ `DIRECT_UPLOAD` lẫn file đã parse xong từ `EMAIL_INTAKE`.
- `file-service.email-parse.dlq` — nghiệp vụ thật (SGS/Bureau Veritas gửi định dạng lạ hoặc vượt cap 20MB, parser không đọc được) — cần luồng báo **Admin** (VD insert `email_intake_failure`, notify), để Admin chủ động liên hệ bên gửi lại đúng định dạng. Không phải bug cần dev sửa.

### 4.1 Email intake bridge

**Chốt (05/07/2026):** dùng **IMAP polling định kỳ** (Spring `@Scheduled`, mỗi 5 phút, thư viện Jakarta Mail), không dùng webhook (AWS SES/Mailgun inbound parse) — webhook cần domain thật + verify DNS + public HTTPS endpoint, quá nặng cho scope đồ án. Job poll mailbox `intake@...`, mỗi mail mới → extract MIME attachment → lưu file thô (`ingestChannel = EMAIL_INTAKE`, `status = PROCESSING`) → publish `file.email.received`.

---

## 5. Event Contract

```
file.ready   (fileId, uploadedBy, ingestChannel, contentType, fileSize)
file.failed  (fileId, uploadedBy, ingestChannel, failureReason)
```

Không đưa `storageHash` vào payload — service khác không cần biết hash, chỉ cần biết fileId dùng được hay không.

**Idempotency:** key = `fileId` (unique tuyệt đối theo bản chất business — khác bank-service phải dùng `sourceEventId` vì 1 `contractId` có nhiều entry; ở đây 1 `fileId` chỉ ra đúng 1 `file.ready`/`file.failed`). Consumer bên nhận (VD milestone-service) tự chịu trách nhiệm idempotent-check (kiểm tra state hiện tại trước khi xử lý lại) — không phải việc file-service phải làm thêm.

**Lý do bắt buộc dùng event, không dùng polling:** nếu contract-service/milestone-service coi `fileId` nhận được lúc `UploadFile` trả về là "đã có evidence hợp lệ" ngay lập tức — trong khi file vẫn `PROCESSING` — và vài giây sau file bị `FAILED` (virus/sai định dạng), thì 2 service lệch state, không ai biết cho tới khi có người mở file thủ công. Đúng dạng dual-write đã học ở bank-service, chỉ khác hướng (fire-and-forget không đợi confirm). Event bắt buộc consumer phải nhận `file.ready` mới đổi state tiếp — không có đường tắt để lỡ quên như polling (optional, dễ bị bỏ qua).

---

## 6. Access Control (Download)

**Chốt (05/07/2026):** file-service **không tự kiểm tra quyền nghiệp vụ** (VD "user X có phải 1 bên của contract Y chứa evidence này không") — đúng nguyên tắc agnostic đã áp xuyên suốt, vì đó là kiến thức nghiệp vụ chỉ service sở hữu `fileId` mới có.

**Pattern: proxy qua service sở hữu, không expose file-service trực tiếp ra ngoài cho end-user.** Service đang giữ `fileId` (VD contract-service) tự expose endpoint riêng của nó (`GET /contracts/{id}/milestones/{id}/evidence`), tự validate quyền theo đúng business logic của nó, sau đó **mới** gọi `file-service.GetFile(fileId)` nội bộ (service-to-service, trusted) để lấy bytes và proxy ngược lại cho end-user. File-service chỉ enforce 1 lớp bảo vệ tối thiểu, chung cho mọi trường hợp: endpoint `GetFile` không public cho end-user gọi thẳng, chỉ nhận service-to-service call (mTLS/service token) hoặc chính `uploadedBy`/role Admin (cho case Admin cần xem trực tiếp qua UI quản trị).

---

## 7. Retention & Deletion

**Chốt (05/07/2026) — neo vào luật thật, không phải chỉ tối ưu chi phí:** EUDR (Regulation 2023/1115, Điều 9) bắt buộc operator giữ toàn bộ tài liệu due diligence (bao gồm evidence, geolocation) **tối thiểu 5 năm** kể từ ngày due diligence statement được nộp. Đây là lý do default phải là **giữ**, không phải xoá — sai lầm "xoá quá sớm 1 file cần cho audit EUDR" nghiêm trọng hơn nhiều so với sai lầm "tốn thêm vài GB storage".

**Nguyên tắc: file-service không tự quyết retention theo nghiệp vụ (đúng agnostic), chỉ cung cấp cơ chế, để service sở hữu tự quyết khi nào an toàn để xoá.**

- **Default: giữ vĩnh viễn.** Không có TTL/cronjob tự xoá file `READY` đã `attached = TRUE`.
- **`DeleteFile(fileId)`** — API tường minh, chỉ được gọi bởi chính service đang sở hữu `fileId` đó, khi service đó (biết rõ retention rule nghiệp vụ của riêng nó) xác định đã an toàn để xoá. File-service không bao giờ tự khởi xướng.
- **Orphan cleanup — cơ chế `ConfirmAttached(fileId)`, không dùng heuristic đoán:** service nhận `fileId` từ `store()`/`UploadFile` phải tự lưu `fileId` vào entity của mình **trước**, thành công thì mới gọi `ConfirmAttached(fileId)` set `attached = TRUE`. Nếu service kia crash giữa chừng (nhận `fileId` nhưng chưa kịp lưu) → không bao giờ gọi `ConfirmAttached` → file vẫn `attached = FALSE`. Cùng pattern "không set state trước khi có confirmation" đã dùng ở bank-service, áp ngược cho chính file-service. File `attached = FALSE` quá **1 tuần** → coi là mồ côi, tự động xoá — không có compliance nào áp lên file chưa từng có chủ.
- **File cũ bị ghi đè khi re-import/upload lại** (`cadastralExtractFileId` qua `UploadCadastralExtract`, `originalKmlFileId` qua re-import KML) — **không** rơi vào nhánh orphan, vì đã từng được `ConfirmAttached = TRUE`. Giữ nguyên theo policy "default giữ vĩnh viễn" — đây là **chủ đích**, không phải bug: giữ được lịch sử "trích lục/KML nào từng gắn với entry này trước khi bị thay", có giá trị audit trail.

---

## 8. Database Migration

```sql
CREATE TABLE file (
    file_id           UUID PRIMARY KEY,
    storage_hash      VARCHAR(64) NOT NULL,   -- MinIO tampering detection, tách biệt signedContentHash/reportHash
    uploaded_by       VARCHAR(255) NOT NULL,  -- userId hoặc serviceId, plain, không FK cross-service
    ingest_channel    VARCHAR(20) NOT NULL,   -- DIRECT_UPLOAD | EMAIL_INTAKE | SYSTEM_GENERATED — quyết định bởi entrypoint, không phải caller tự khai
    content_type      VARCHAR(100) NOT NULL,
    file_size         BIGINT NOT NULL,
    status            VARCHAR(20) NOT NULL DEFAULT 'PROCESSING',  -- PROCESSING | READY | FAILED
    failure_reason    TEXT NULL,              -- chỉ có giá trị khi status = FAILED
    attached          BOOLEAN NOT NULL DEFAULT FALSE,  -- xem §7, orphan cleanup
    created_at        TIMESTAMP NOT NULL DEFAULT now()
);
CREATE INDEX idx_file_status ON file(status);
CREATE INDEX idx_file_attached_created ON file(attached, created_at);  -- phục vụ query orphan cleanup

CREATE TABLE email_intake_failure (
    failure_id      UUID PRIMARY KEY,
    file_id         UUID NULL REFERENCES file(file_id),  -- NULL nếu fail trước khi tạo được file record (VD parse MIME lỗi hoàn toàn)
    source_mailbox  VARCHAR(255) NOT NULL,
    failure_reason  TEXT NOT NULL,
    detected_at     TIMESTAMP NOT NULL DEFAULT now(),
    reviewed_by_admin BOOLEAN NOT NULL DEFAULT FALSE
);
-- Bảng riêng cho DLQ email-parse (§4) — Admin cần biết để chủ động liên hệ SGS/Bureau Veritas gửi lại,
-- khác bản chất với lỗi kỹ thuật virus-scan (chỉ cần alert dev, không cần bảng riêng).
```

---

## 9. Known Limitations / Out of Scope (có chủ đích)

- **Orphan-cleanup dựa vào `ConfirmAttached` do service khác tự gọi đúng lúc** — nếu service đó có bug quên gọi `ConfirmAttached` dù đã lưu `fileId` thành công, file hợp lệ có thể bị xoá nhầm sau 1 tuần. Rủi ro chấp nhận được cho scope đồ án, nhưng cần lưu ý khi code các service consumer.
- **Access control ở §6 dựa vào service sở hữu tự validate đúng** — file-service không double-check lại quyền nghiệp vụ, nên nếu service sở hữu có lỗ hổng auth thì file-service không phải lớp phòng thủ thứ 2.
- **Không đối chiếu nội dung file với dữ liệu đã khai** (đã nêu ở product-phase2-design §4 cho `cadastralExtractFileId`) — file-service chỉ lưu, không OCR/verify nội dung khớp với business data.
- **Retry/DLQ dùng cơ chế RabbitMQ built-in (dead-letter-exchange + TTL)**, không tự code retry logic — đủ cho scope 5 tháng, không cần framework retry phức tạp hơn (Resilience4j...).

---

## 10. Status — File-service Design

**Chốt (05/07/2026):** file-service là kho lưu trữ file **agnostic với nghiệp vụ** — chỉ biết blob + metadata kỹ thuật, không biết ý nghĩa nghiệp vụ của file. 3 entrypoint tách theo trust boundary (không phải 1 API generic) để `ingestChannel` không thể bị giả mạo. State machine kỹ thuật (`PROCESSING/READY/FAILED`) tách biệt hoàn toàn khỏi quyết định duyệt nghiệp vụ (thuộc service khác). 2 pipeline async — parse (email) và scan (security) — **nối chuỗi cho file `EMAIL_INTAKE`, không chạy song song độc lập** (xem §4, fix review pass 2). Event-driven (`file.ready`/`file.failed`), không polling — tránh dual-write giữa file-service và service tiêu thụ. Retention neo vào EUDR Điều 9 (tối thiểu 5 năm) — default giữ vĩnh viễn, xoá chỉ qua lệnh tường minh từ service sở hữu, trừ orphan cleanup (`ConfirmAttached` pattern, 1 tuần). Storage key trong MinIO = `fileId` phẳng, không phân cấp theo content-type (§2.3).

**Review pass 2 (05/07/2026) — bug đã fix:** kênh `EMAIL_INTAKE` (nguồn ngoài internet) từng bỏ qua virus-scan hoàn toàn do tách nhầm consumer theo nguồn gốc file thay vì theo loại công việc — xem §4. Đã fix bằng cách nối chuỗi `EmailParseConsumer` → `file-service.virus-scan` queue. Đồng thời chốt thêm size cap 20MB (streaming check) cho `EMAIL_INTAKE` — trước đó chưa có giới hạn nào (§3).

**Việc còn treo, không block thiết kế này:** chưa xin cấp phép/khảo sát thực tế khả năng dùng IMAP polling với mailbox thật của SGS/Bureau Veritas (giả định đơn giản hoá cho scope đồ án).

File-service — **đóng session, sẵn sàng đưa vào Architecture/SDS/TechnicalSpec chính thức.**

---

_Design session: 05/07/2026 · Review pass 2 (fix virus-scan gap + storage key + size cap): 05/07/2026 · Chưa code · Chưa đưa vào Architecture/SDS/TechnicalSpec chính thức._
