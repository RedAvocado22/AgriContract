---
name: hash-chain-phase2-design
description: "Audit Trail Hash Chain — chi tiết hoá services.md mục 5, 7, 8. Nguồn: design session 02/07/2026."
status: DESIGNED — chưa code. Cần Cường (Lead) review trước khi đưa vào Architecture/SDS/TechnicalSpec chính thức.
metadata:
  type: design
  phase: 2
  extends: "services.md § Security gaps (mục 4-9)"
  related: "milestone-escrow-phase2-design.md § 7 (Event Catalog nguồn dữ liệu)"
---

## 1. Bối cảnh & Scope

`services.md` mục 5 chốt nguyên tắc chung (append-only, `previousHash`/`recordHash`), mục 7-8 chốt 2 lớp bảo vệ bổ sung (multi-location, email anchor) nhưng ở mức khái niệm, chưa đủ chi tiết để code. Doc này chốt phần **thiết kế cụ thể tầng `audit-service`**: event nào vào chain, cấu trúc chain thật sự, verify chạy khi nào, và alert routing khi phát hiện tampering.

**Không detail lại** mục 4 (`signedContentHash` ở `contract-service`) và mục 6 (`reportHash` ở `inspection-service`) — 2 cơ chế đó đã tồn tại như nguồn phát sinh hash, doc này chỉ định nghĩa cách `audit-service` **nhận và nối** các hash đó vào chain, dùng chung 1 schema `AuditRecord` cho mọi nguồn.

---

## 2. Event Catalog vào Hash Chain

**Chốt (02/07/2026):** không phải mọi event đều vào chain. Tiêu chí: event phải mang theo **số liệu/quyết định có thể bị tranh chấp làm bằng chứng sau này**, hoặc là input để tính ra 1 con số sẽ bị tranh chấp — không chỉ riêng event dịch chuyển tiền.

### 2.1 Từ Milestone Escrow (`milestone-escrow-phase2-design.md` §7) — 6/8

| Event | Vào chain? | Lý do |
|---|---|---|
| `milestone.seller_weighed` | ✅ | Mang `sellerDeclaredWeight` — input gốc để tính Delta 1/Delta 2, cần chứng minh không bị sửa sau khi cân |
| `milestone.buyer_confirmed` | ✅ | Mang `buyerReceivedWeight` — input gốc tính Delta 2, cùng lý do trên |
| `milestone.flagged` | ❌ | Chỉ là tín hiệu "buyer bấm nút", không mang số liệu riêng — số liệu thật đã nằm ở `buyer_confirmed` |
| `milestone.force_majeure_claimed` | ✅ | Bằng chứng bất khả kháng seller nộp — cơ sở miễn/không miễn penalty, giá trị pháp lý cao (Điều 156/351 BLDS 2015) |
| `milestone.force_majeure_resolved` | ✅ | Quyết định APPROVE/REJECT của Admin/Level 1.5 — kết quả tranh chấp, bắt buộc immutable |
| `milestone.settled` | ✅ | Kết quả cuối, số tiền release — bắt buộc |
| `milestone.cancelled_with_penalty` | ✅ | Căn cứ tính `lockDurationDays`, penalty debt — có giá trị làm bằng chứng theo Luật TM 2005 Điều 302 |
| `milestone.settled.local-check` (Local Outbox) | ❌ | Không phải domain event — chỉ là cơ chế sync nội bộ `Milestone`→`Contract`, không có ý nghĩa pháp lý |

### 2.2 Nguồn khác (đã có sẵn ở mục 4, 6 — chỉ liệt kê, không thiết kế lại)

| Nguồn | Trigger | Publisher |
|---|---|---|
| `Contract.signedContentHash` | `sign()` được gọi | `contract-service` (mục 4) |
| `reportHash` | INSPECTOR submit report | `inspection-service` (mục 6) |

Cả 2 dùng chung schema `AuditRecord` ở §3 — không cần thiết kế pipeline riêng.

### 2.3 EUDR Export Flow (bổ sung 03/07/2026)

`product-phase2-design.md` §6 để ngỏ: audit-service đọc `ProductPlot` qua "Feign hoặc read model" lúc export báo cáo EUDR. Chốt: **Feign, không phải read model riêng.**

Lý do: read model (kiểu search-service — CQRS, consume event, lưu bản sao denormalized) đúng khi cần đọc liên tục, tần suất cao, latency thấp. Export EUDR ngược lại hoàn toàn — on-demand, hiếm (theo chính docx thị trường/giải pháp), không cần latency thấp. Build thêm 1 read model (event consumer + storage riêng + xử lý eventual consistency) cho 1 tính năng chạy vài lần/tháng là tốn công đúng chỗ không cần.

```
GenerateEUDRReport(contractId):
  1. Query audit_record WHERE contract_id = X, ORDER BY created_at
     → toàn bộ chain của hợp đồng này, đã verify integrity qua record_hash (§3)
  2. Lấy productId — cần xác nhận lúc code: content JSONB của event CONTRACT_SIGNED
     đã chứa productId sẵn chưa. Nếu chưa, thêm cột product_id vào audit_record
     (chỉ set cho source_type = CONTRACT_SIGNED), tránh phải Feign 2 lần (1 lấy
     contract, 1 lấy product) chỉ để tìm productId
  3. productId → Feign call sang product-service (client mới trong audit-service,
     cùng pattern ProductServiceClient contract-service đang dùng) → lấy ProductPlot list
  4. Assemble: GeoJSON plots (từ ProductPlot) + audit trail excerpt (verify status,
     record_hash liên quan) + reportHash nếu hợp đồng có inspection report
  5. Output PDF/CSV qua file-service
```

Open item nhỏ, không block kiến trúc: xác nhận `content` JSONB của `CONTRACT_SIGNED` có sẵn `productId` hay cần thêm cột — quyết định lúc code, không ảnh hưởng thiết kế tổng thể.

---

## 3. Cấu trúc Chain — Dual Chain trên cùng 1 bảng

**Chốt (02/07/2026):** **không** tách 2 bảng riêng cho global chain và per-contract chain — tốn kém, trùng lặp, đi ngược lập luận "blockchain không thực tế" đã chốt cuối `services.md`. Dùng **1 bảng duy nhất**, 2 cột `previousHash` khác mục đích:

```sql
CREATE TABLE audit_record (
    record_id           UUID PRIMARY KEY,
    contract_id         UUID NOT NULL,          -- luôn có, kể cả event không thuộc Contract trực tiếp (Milestone có contractId)
    source_type         VARCHAR(50) NOT NULL,   -- 'MILESTONE_EVENT' | 'CONTRACT_SIGNED' | 'INSPECTION_REPORT'
    source_event_type   VARCHAR(100) NOT NULL,  -- vd: 'milestone.settled', 'milestone.cancelled_with_penalty'
    content              JSONB NOT NULL,         -- payload gốc của event (số liệu, quyết định...)
    record_hash          VARCHAR(64) NOT NULL,   -- SHA-256(content + prev_hash_global)
    prev_hash_global      VARCHAR(64),            -- NULL nếu là record đầu tiên toàn hệ thống
    prev_hash_contract    VARCHAR(64),            -- NULL nếu là record đầu tiên của contract_id này
    ots_proof             TEXT,                   -- NULL trừ record thuộc event ✅ Event Catalog — xem §4.3
    created_at           TIMESTAMP NOT NULL DEFAULT now()
);
CREATE INDEX idx_audit_record_contract_id ON audit_record(contract_id, created_at);
```

**DB permission (mục 5 gốc):** DB user của `audit-service` chỉ có `INSERT` + `SELECT`, không có `UPDATE`/`DELETE` — giữ nguyên, không đổi.

**Cách tính lúc insert 1 record mới:**

```
prev_hash_global   = record_hash của row có created_at lớn nhất TOÀN BẢNG (bất kể contract_id)
prev_hash_contract = record_hash của row có created_at lớn nhất CÙNG contract_id này
record_hash        = SHA256(content + prev_hash_global)
```

**Lưu ý concurrency:** tính `prev_hash_global` cần đọc record cuối cùng toàn bảng — với nhiều contract insert đồng thời, cần `SELECT ... FOR UPDATE` trên row cuối hoặc 1 sequence riêng để tránh 2 insert cùng lúc tính ra cùng 1 `prev_hash_global` (race condition làm gãy chain ngay từ đầu). Với scale B2B forward contract thật (không phải giao dịch tần suất cao), serialize ở mức này không phải bottleneck.

**Ví dụ minh hoạ (đã thống nhất trong session):**

| Seq | contractId | Event | recordHash | prevHashGlobal | prevHashContract |
|---|---|---|---|---|---|
| 1 | A | `milestone.settled` | h1 | null | null |
| 2 | B | `milestone.settled` | h2 | h1 | null |
| 3 | A | `milestone.cancelled_with_penalty` | h3 | h2 | h1 |
| 4 | B | `milestone.settled` | h4 | h3 | h2 |

- **Verify per-contract** (`WHERE contract_id = A`, theo `prevHashContract`): dùng để export bằng chứng gọn cho riêng 1 vụ (VIAC/toà), tự-đủ-bằng-chứng, không cần giải thích gì về contract khác.
- **Verify global** (theo `prevHashGlobal`, đọc toàn bảng theo `created_at`): dùng để phát hiện **xoá nguyên cụm record của 1 contract** — per-contract chain đứng một mình không phát hiện được kiểu tấn công này, vì chain đó tự nó biến mất sạch không để lại dấu vết.

---

## 4. Multi-location Hash Storage (chi tiết mục 7 + 8)

**Chốt (02/07/2026):** mục 7 và mục 8 gốc mô tả **cùng 1 hành động** — 1 email gửi lúc `sign()`/report `SUBMITTED` — nhấn mạnh 2 giá trị khác nhau của nó, không phải 2 việc riêng.

### 4.1 Ba nơi lưu, phải khớp nhau

| # | Nơi lưu | Ai kiểm soát | Giá trị bảo vệ |
|---|---|---|---|
| 1 | `contract-service` DB (`Contract.signedContentHash`) | Platform | Nguồn gốc — nơi hash được tính ra đầu tiên |
| 2 | `audit-service` DB (`AuditRecord.record_hash`, `source_type = 'CONTRACT_SIGNED'`) | Platform | Nối vào chain, chống sửa-sau bằng toán học (§3) |
| 3 | Email gửi buyer + seller lúc `sign()` | **Ngoài platform** — hộp thư cá nhân buyer/seller | Ngoài tầm với của bất kỳ ai trong platform, kể cả Admin có full quyền root |

**Nội dung email (mục 7 — "có bản sao ở ngoài"):** snapshot đầy đủ `ContractTerms` (PDF/JSON) + dòng `signedContentHash`, gửi qua `notification-service` ngay khi `sign()` thành công.

**Giá trị timestamp (mục 8 — "có mốc thời gian độc lập"):** thời điểm gửi do hệ thống email bên thứ 3 (Gmail/Outlook) ghi lại, không phải đồng hồ do platform kiểm soát — platform không tự "lùi ngày" được mốc này sau khi email đã gửi.

### 4.2 Verify logic

3 giá trị hash (contract-service DB, audit-service `AuditRecord`, hash trong email đã gửi) phải **khớp tuyệt đối** vì cùng tính từ 1 `ContractTerms` tại đúng 1 thời điểm `sign()`. Attacker muốn qua mặt phải compromise cả 3 cùng lúc — 2 DB nội bộ (khả thi nếu có quyền cao) + email đã nằm ngoài platform (bất khả thi trừ khi hack thêm tài khoản email cá nhân của buyer/seller).

### 4.3 OpenTimestamps Anchor — bổ sung (03/07/2026)

**Vấn đề mục 4.2 chưa cover:** verify logic ở trên chứng minh 3 nơi lưu khớp nhau tại đúng 1 thời điểm `sign()` — nhưng không chứng minh được **global chain hiện tại** (toàn bộ `audit_record`, không riêng 1 hợp đồng) chưa từng bị **cascade tampering**: Admin có quyền root DB xoá 1 record ở giữa/cuối chain, rồi sửa lại `prev_hash_global`/`record_hash` của toàn bộ record phía sau để chain vẫn tự-nhất-quán. `VerifyChainJob` (§5) chỉ đọc và so snapshot **hiện tại** với chính nó — một chain đã bị cascade sạch sẽ pass verify y hệt chain chưa từng bị đụng, vì không có gì để so sánh với quá khứ. Xoá ở cuối chain (record mới nhất) rẻ nhất cho attacker — không ai đang trỏ `prev_hash_global` về nó, nên không cần cascade gì cả.

**Giải pháp:** neo `record_hash` của các event quan trọng lên Bitcoin qua **OpenTimestamps (OTS)** — dịch vụ mã nguồn mở, dùng calendar server công khai miễn phí gom hash của nhiều người dùng thành 1 giao dịch Bitcoin duy nhất, chia phí ra gần như 0 đồng mỗi lần dùng. Không cần ví crypto, không cần thẻ thanh toán — chỉ gọi REST API bình thường. Verify sau này (`ots verify`, chạy offline) không phụ thuộc platform còn sống hay hợp tác hay không — đúng tinh thần multi-location đã chốt ở §4.1, thêm 1 nơi lưu nằm hẳn ngoài mọi hệ thống do platform kiểm soát.

**Trigger — 2 tầng, không chỉ theo sự kiện (sửa lại 03/07/2026, phát hiện gap so với bản đầu):** event-triggered (mỗi event ✅ trong Event Catalog) không đủ tự nó — quy mô B2B forward contract tần suất thấp (§3), hoàn toàn có thể có 1 tuần không phát sinh event ✅ nào cả, lúc đó không có OTS nào được tạo suốt tuần, cửa sổ tấn công mở toang dù cơ chế event-triggered vẫn tồn tại trên giấy. Nên `VerifyChainJob` (§5.1) **luôn** tự tạo thêm 1 OTS độc lập cho head hiện tại mỗi Chủ Nhật, không phụ thuộc tuần đó có event ✅ hay không — đảm bảo trần cứng: cửa sổ tấn công không bao giờ vượt quá 7 ngày, bất kể platform có giao dịch hay ế.

*Tầng 1 (event-triggered):* mỗi khi 1 event thuộc danh sách ✅ ở Event Catalog (§2.1) được ghi vào `audit_record`, gọi OTS API ngay lập tức lấy `.ots` cho `record_hash` vừa tạo — vì `record_hash` đã cuốn theo toàn bộ lịch sử qua `prev_hash_global`, nó tự nhiên là commitment cho **toàn bộ chain**, không chỉ riêng record đó. Lưu `.ots` vào cột `ots_proof` (schema §3) của đúng record vừa ghi.

*Tầng 2 (weekly, luôn chạy):* `VerifyChainJob` mỗi Chủ Nhật tự tạo 1 OTS mới cho `record_hash` của head hiện tại, kể cả khi tuần đó không có event ✅ nào — dùng làm điểm neo cho lần verify tuần sau (§5.1).

**Gửi cho buyer/seller — chỉ tại `milestone.settled`:** đính kèm `.ots` vào đúng email đã có sẵn ở §4.1 khi milestone đạt trạng thái cuối, không phải mọi event ✅. Buyer/seller có động lực giữ lại email tại mốc này (gắn với kết quả tiền bạc của họ) — gửi ở mọi bước trung gian (`seller_weighed`, `buyer_confirmed`) chỉ tạo noise, dễ bị bỏ qua hoặc đánh dấu spam, ảnh hưởng ngược lại tới email anchor quan trọng hơn ở §4.1.

**Cửa sổ tấn công còn lại:** trần cứng 7 ngày nhờ tầng 2 (luôn chạy, không phụ thuộc hoạt động platform); tuần nào có event ✅ thì cửa sổ thực tế hẹp hơn nhiều nhờ tầng 1. Không triệt tiêu hoàn toàn — giới hạn cố hữu của bất kỳ mô hình chốt-theo-mốc nào (kể cả anchor liên tục theo mili-giây cũng còn khe hở, chỉ hẹp gần bằng 0), không phải lỗi thực thi.

**Giới hạn cần nói rõ khi trình bày:** OTS chỉ giải quyết vế "bằng chứng có tồn tại độc lập ngoài platform hay không" — **không** giải quyết vế "có ai chủ động đi đối chiếu hay không". Xem cập nhật ở §6.

---

## 5. Weekly Verify Job

**Chốt (02/07/2026):** không chỉ verify lúc export EUDR report (quá hiếm, phát hiện muộn) — thêm `@Scheduled` job chạy **định kỳ hàng tuần, 2-3h sáng Chủ Nhật** (giờ traffic thấp nhất, cùng convention với Outbox Poller chạy nền).

### 5.1 Thứ tự với Weekly Digest (mục 8)

Verify chạy **trước**, digest gửi **sau**, cùng 1 lần chạy tuần — không phải 2 job độc lập không biết tới nhau:

```
02:00 CN — VerifyChainJob chạy:
  1. Đọc toàn bộ audit_record theo created_at (global chain)
  2. Tính lại record_hash từng row theo công thức §3, so với record_hash đã lưu
  3. Lấy anchoredHash = record_hash gần nhất đã được OTS-anchor (event-triggered trong tuần, hoặc weekly anchor tuần trước nếu tuần này chưa có event ✅ nào) → query SELECT 1 FROM audit_record WHERE record_hash = anchoredHash. Vì record_hash phụ thuộc đệ quy vào toàn bộ lịch sử phía trước, cascade tampering ở bất kỳ điểm nào trước hoặc tại vị trí đó sẽ làm giá trị này biến mất khỏi bảng — không cần biết attacker sửa bao nhiêu dòng, chỉ 1 query là đủ
  4. Tạo OTS mới cho record_hash của head hiện tại (tầng 2, §4.3) — luôn chạy, không phụ thuộc tuần này có event ✅ hay không, dùng làm anchoredHash cho lần verify tuần sau
  5. Nếu khớp 100% ở bước 2 và anchoredHash vẫn tồn tại ở bước 3 → set flag WEEKLY_VERIFY_OK
  6. Nếu có row lệch (bước 2) hoặc anchoredHash không tìm thấy (bước 3) → set flag WEEKLY_VERIFY_FAILED, ghi lại vị trí lệch đầu tiên tìm được, hoặc note "anchored hash missing — cascade tampering" nếu bước 2 pass nhưng bước 3 fail

02:30 CN — DigestJob chạy (chỉ sau khi VerifyChainJob xong):
  - Nếu WEEKLY_VERIFY_OK → tính hash của record mới nhất (commitment — đại diện toàn chain, xem §3), gửi digest bình thường cho Software Buyer
  - Nếu WEEKLY_VERIFY_FAILED → KHÔNG gửi digest như thường lệ, chuyển thẳng sang flow alert §5.2
```

**Note quy mô (03/07/2026, ghi nhận có chủ đích, không thiết kế chi tiết — ngoài phạm vi capstone):** bước 1-2 quét toàn bảng mỗi lần chạy — chi phí O(n) theo số record trong bảng, không phải chi phí hash (SHA256 mỗi record luôn O(1), không đổi theo kích thước chain — xem cách tính ở §3). Ở quy mô vài nghìn tới vài chục nghìn record, không đáng lo. Nếu vận hành nhiều năm và bảng phình tới hàng triệu record, `VerifyChainJob` sẽ chậm dần tuyến tính theo thời gian — hướng fix (không detail ở đây) là incremental verify: lưu con trỏ `last_verified_record_id`, mỗi lần chỉ verify phần mới từ lần chạy trước thay vì quét lại từ đầu.

### 5.2 Alert Routing khi Verify Fail

**Vấn đề đã nhận diện trong session:** nếu chỉ báo Admin rồi đợi Admin điều tra mới quyết định có báo tiếp không — trường hợp chính Admin là người sửa data, Admin chỉ cần không bao giờ "điều tra xong" là chặn được cảnh báo vĩnh viễn. Không được để 1 người duy nhất làm gatekeeper.

**Chốt cơ chế:** verify fail → hệ thống **tự động bắn song song, không qua bước duyệt của ai**:

1. **Admin** — để điều tra kỹ thuật, xác định chính xác record/contract nào bị ảnh hưởng.
2. **Nhiều địa chỉ liên hệ phía Software Buyer** (hiệp hội/DN deploy) — **không chỉ 1 người** (giảm rủi ro collusion của đúng 1 cá nhân phía nhận, dù không loại trừ hoàn toàn được nếu tất cả cùng thông đồng — xem §6).

Cả 2 hướng gửi **cùng lúc, tự động qua `notification-service`**, không có bước "Admin xác nhận trước rồi mới gửi tiếp".

**Về việc báo cho buyer/seller trong từng hợp đồng cụ thể:** không báo ngay lúc verify fail (chưa xác định được contract nào bị ảnh hưởng, báo nhầm gây hoang mang). Sau khi Admin điều tra xong, khoanh vùng đúng `contract_id` bị ảnh hưởng → mới thông báo cho đúng buyer/seller của contract đó qua kênh liên lạc thông thường (không phải tự động, cần người xác nhận nội dung trước khi gửi vì đây là thông tin nhạy cảm, cần chính xác).

---

## 6. Known Limitation (ghi nhận có chủ đích, không phải điểm mù)

Toàn bộ thiết kế 3 lớp (hash chain + multi-location + email anchor) đứng trên giả định **trusted operator** đã chốt từ đầu (`services.md`, dòng cuối): *"Platform có trusted operator — bài toán trustless consensus không tồn tại."*

**Kịch bản không giải quyết được trong scope này:** nếu Admin **và** toàn bộ (hoặc đa số) người nhận cảnh báo phía Software Buyer cùng thông đồng — không có cơ chế software nào trong kiến trúc hiện tại chặn được, vì đây chính xác là bài toán trustless consensus mà blockchain giải quyết bằng cách phân tán ra nhiều node độc lập không ai kiểm soát hết, và nhóm đã quyết định không theo hướng đó (5 tháng, 3 người, không thực tế).

**2 lớp vẫn đứng vững kể cả trong kịch bản xấu nhất này:**
- Email anchor lúc `sign()` gửi cho **buyer/seller thật trong từng hợp đồng cụ thể** — người này khác hoàn toàn, thời điểm khác hoàn toàn so với người nhận weekly digest. Không bị ảnh hưởng bởi collusion ở phía hiệp hội.
- Bằng chứng toán học (hash mismatch) tồn tại độc lập với việc có ai chủ động báo hay không — bất kỳ bên thứ 3 nào sau này (luật sư, chuyên gia toà chỉ định) tự chạy lại được đúng phép verify này trên dữ liệu thô, không cần platform "thông báo" mới phát hiện ra.
- OTS anchor (§4.3, bổ sung 03/07/2026) cho global commitment hash — verify được ngay cả khi platform sập hoàn toàn, không domain, không server, vì bằng chứng nằm trên Bitcoin, không nằm trong hệ thống của platform.

**Cập nhật (03/07/2026) — OTS thu hẹp limitation này, không đóng hoàn toàn, cần nói rõ ranh giới khi trình bày:** OTS chỉ giải quyết vế *"bằng chứng toán học có tồn tại độc lập ngoài platform hay không"* — nếu Admin và toàn bộ (hoặc đa số) người nhận digest cùng thông đồng **và không ai chủ động đối chiếu** OTS proof với chain hiện tại, thì có anchor hay không cũng không tạo ra khác biệt thực tế, vì `VerifyChainJob` (§5.1 bước 3) là nơi duy nhất trong kiến trúc chủ động chạy phép đối chiếu đó — nếu chính job này bị vô hiệu hoá hoặc kết quả bị bỏ qua, OTS proof vẫn tồn tại trên Bitcoin nhưng không ai tra cứu nó. OTS không tạo ra động lực phát hiện — nó chỉ đảm bảo rằng **khi** có người chịu nhìn (luật sư, chuyên gia toà chỉ định, kiểm toán độc lập), họ nhìn được sự thật mà không cần platform "thông báo" hay "cho phép". Vế "phải có người chủ động nhìn" vẫn là giới hạn cố hữu của mô hình trusted-operator, OTS không đổi được vế này.

**Khi hội đồng hỏi:** *"Đây là giới hạn cố hữu của mô hình trusted-operator mà nhóm chọn có chủ đích thay vì blockchain, phù hợp ràng buộc thời gian/nhân lực thật của dự án — không phải điểm mù bị bỏ sót."*

---

## 7. Status — Hash Chain Design

**Chốt (02/07/2026):** 6/8 Milestone event vào chain (loại `flagged`, `milestone.settled.local-check`). Schema `AuditRecord` dual-chain (`prevHashGlobal` + `prevHashContract`) trên cùng 1 bảng, không tách bảng riêng. Mục 7+8 = cùng 1 email, 2 giá trị (bản sao ngoài platform + timestamp độc lập). 3 nơi lưu hash (`contract-service` DB, `audit-service` DB, email) phải khớp tuyệt đối. Verify job chạy hàng tuần 2-3h sáng CN, trước digest. Verify fail → alert tự động song song Admin + nhiều contact Software Buyer, không qua gatekeeper 1 người. Buyer/seller từng hợp đồng chỉ được báo sau khi khoanh vùng xong, không tự động. Collusion toàn diện (Admin + hiệp hội) là known limitation có chủ đích, không phải thiếu sót.

**Chốt bổ sung (03/07/2026) — OpenTimestamps Anchor:** thêm cột `ots_proof` vào `audit_record` (§3). Anchor lên Bitcoin qua OpenTimestamps theo sự kiện (không theo lịch cố định) cho các event ✅ trong Event Catalog (§4.3) — miễn phí, không cần ví crypto/thẻ thanh toán, dùng calendar server công khai. Gửi `.ots` cho buyer/seller chỉ tại `milestone.settled`, tránh noise email ở các bước trung gian. `VerifyChainJob` (§5.1) thêm bước 3 đối chiếu OTS proof với head hiện tại của chain — bắt được **cascade tampering** (xoá + sửa lại toàn bộ prevHash phía sau để chain tự-nhất-quán), kiểu tấn công mà verify tự-so-với-chính-nó ở bản gốc 02/07 không phát hiện được, vì nó chỉ so sánh trạng thái hiện tại với chính nó, không có điểm neo quá khứ để đối chiếu. Known Limitation §6 được thu hẹp một phần (vế "bằng chứng độc lập ngoài platform"), **không** đóng hoàn toàn — vế "phải có người chủ động đối chiếu" vẫn còn nguyên, đây là giới hạn cố hữu, không phải thiếu sót của lần bổ sung này.

Hash Chain — **đóng session, sẵn sàng đưa vào Architecture/SDS/TechnicalSpec chính thức.**

---

*Design session: 02/07/2026 · Bổ sung OpenTimestamps Anchor: 03/07/2026 · Chưa code · Chưa đưa vào Architecture/SDS/TechnicalSpec chính thức.*
