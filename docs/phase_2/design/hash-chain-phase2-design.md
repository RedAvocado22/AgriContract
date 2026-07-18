---
name: hash-chain-phase2-design
description: "Audit Trail Hash Chain — chi tiết hoá services.md mục 5, 7, 8. Nguồn: design session 02/07/2026."
status: DESIGNED — chưa code.
metadata:
    type: design
    phase: 2
    extends: "services.md § Security gaps (mục 4-9)"
    related: "milestone-escrow-phase2-design.md §7; notification-service-phase2-design.md §4/§8; api-gateway-phase2-design.md §3.4"
---

## 1. Bối cảnh & Scope

`services.md` mục 5 chốt nguyên tắc chung (append-only, `previousHash`/`recordHash`), mục 7-8 chốt 2 lớp bảo vệ bổ sung (multi-location, email anchor) nhưng ở mức khái niệm, chưa đủ chi tiết để code. Doc này chốt phần **thiết kế cụ thể tầng `audit-service`**: event nào vào chain, cấu trúc chain thật sự, verify chạy khi nào, và alert routing khi phát hiện tampering.

**Không detail lại** mục 4 (`signedContentHash` ở `contract-service`) và mục 6 (`reportHash` ở `inspection-service`) — 2 cơ chế đó đã tồn tại như nguồn phát sinh hash, doc này chỉ định nghĩa cách `audit-service` **nhận và nối** các hash đó vào chain, dùng chung 1 schema `AuditRecord` cho mọi nguồn.

---

## 2. Event Catalog vào Hash Chain

**Chốt (02/07/2026):** không phải mọi event đều vào chain. Tiêu chí: event phải mang theo **số liệu/quyết định có thể bị tranh chấp làm bằng chứng sau này**, hoặc là input để tính ra 1 con số sẽ bị tranh chấp — không chỉ riêng event dịch chuyển tiền.

### 2.1 Từ Milestone Escrow (`milestone-escrow-phase2-design.md` §7) — 6/8

| Event                                          | Vào chain? | Lý do                                                                                                             |
| ---------------------------------------------- | ---------- | ----------------------------------------------------------------------------------------------------------------- |
| `milestone.seller_weighed`                     | ✅         | Mang `sellerDeclaredWeight` — input gốc để tính Delta 1/Delta 2, cần chứng minh không bị sửa sau khi cân          |
| `milestone.buyer_confirmed`                    | ✅         | Mang `buyerReceivedWeight` — input gốc tính Delta 2, cùng lý do trên                                              |
| `milestone.flagged`                            | ❌         | Chỉ là tín hiệu "buyer bấm nút", không mang số liệu riêng — số liệu thật đã nằm ở `buyer_confirmed`               |
| `milestone.force_majeure_claimed`              | ✅         | Bằng chứng bất khả kháng seller nộp — cơ sở miễn/không miễn penalty, giá trị pháp lý cao (Điều 156/351 BLDS 2015) |
| `milestone.force_majeure_resolved`             | ✅         | Quyết định APPROVE/REJECT của Admin/Level 1.5 — kết quả tranh chấp, bắt buộc immutable                            |
| `milestone.settled`                            | ✅         | Kết quả cuối, số tiền release — bắt buộc                                                                          |
| `milestone.cancelled_with_penalty`             | ✅         | Căn cứ tính `lockDurationDays`, penalty debt — có giá trị làm bằng chứng theo Luật TM 2005 Điều 302               |
| `milestone.settled.local-check` (Local Outbox) | ❌         | Không phải domain event — chỉ là cơ chế sync nội bộ `Milestone`→`Contract`, không có ý nghĩa pháp lý              |

### 2.2 Nguồn khác (đã có sẵn ở mục 4, 6 — chỉ liệt kê, không thiết kế lại)

| Nguồn                        | Trigger                 | Publisher                    |
| ---------------------------- | ----------------------- | ---------------------------- |
| `Contract.signedContentHash` | `sign()` được gọi       | `contract-service` (mục 4)   |
| `reportHash`                 | INSPECTOR submit report | `inspection-service` (mục 6) |

Cả 2 dùng chung schema `AuditRecord` ở §3 — không cần thiết kế pipeline riêng. **Transport (chốt 17/07/2026):** `Contract.signedContentHash` vào chain qua chính domain event `contract.signed` (payload mở rộng mang `signedContentHash`, audit-service là consumer — bỏ "đường push riêng" chưa đặt tên ở signature §6 bước 5 bản cũ); `reportHash` vào chain qua `inspection.report_confirmed` (inspection §4). Danh sách đầy đủ — §2.4.

### 2.3 Sự kiện bảo mật External Verifier (mới, 08/07/2026 — đi kèm bank-service §3.5)

| Event / `source_type`               | Trigger                                             | Vì sao vào chain                                                                                              |
| ----------------------------------- | --------------------------------------------------- | ------------------------------------------------------------------------------------------------------------- |
| `EXTERNAL_VERIFIER_KEY_REGISTERED`  | Đăng ký/đổi public key của External Verifier        | Root-of-trust của kill switch — phải tamper-evident để Admin không lén swap key (bank-service §3.5.6 lớp 2)     |
| `SECURITY_LOCK_TRIGGERED`           | External Verifier ký lệnh emergency-lock            | Quyết định đóng băng toàn hệ thống — bằng chứng ai ra lệnh, lúc nào, lý do gì (bank-service §3.5.7)             |
| `SECURITY_UNLOCK_TRIGGERED`         | External Verifier ký lệnh emergency-unlock          | Quyết định mở băng — cùng lý do, kill switch tự nó phải tamper-evident                                         |

Cả 3 dùng chung schema `AuditRecord` §3 — `content` mang payload đã ký (không mang private key). Chúng đi vào **cả** dual-chain (global + per-subject: `subject_type = SYSTEM` cho key-registration/emergency lock — **sửa 18/07/2026**, bỏ sentinel, xem §3) **và** được OTS-anchor như event ✅ (§4.3), vì đây là loại quyết định giá trị pháp lý cao nhất — không được để rơi ngoài anchor.

---

### 2.4 Audit Ingestion Catalog — cơ chế thống nhất + danh sách đầy đủ `source_type` (mới, 17/07/2026)

**Vấn đề:** các doc khác lần lượt thêm `source_type` mới ("ghi hash vào audit-service") nhưng không doc nào chốt *đi bằng đường nào*, và catalog ở §2/§3 bị lệch so với tổng các doc (thiếu `STRUCTURING_REPORT` của bank §3.4b, thiếu source_type cho quyết định gỡ `ELEVATED_RISK` của reputation §8). Chốt cơ chế thống nhất: **mọi nguồn vào chain là 1 domain event RabbitMQ, audit-service là consumer và là writer DUY NHẤT của `audit_record`** — không service nào INSERT thẳng, không có internal API ghi hộ. Danh sách đầy đủ:

| `source_type` | Event nguồn | Publisher |
|---|---|---|
| `MILESTONE_EVENT` | các `milestone.*` ✅ theo §2.1 | contract-service |
| `CONTRACT_SIGNED` | `contract.signed` (payload mang `signedContentHash` — 17/07/2026) | contract-service |
| `INSPECTION_REPORT` | `inspection.report_confirmed` (tier = LEVEL_1_5) | inspection-service |
| `EXTERNAL_INSPECTION_REPORT` | `inspection.report_confirmed` (tier = LEVEL_2) | inspection-service |
| `LEVEL2_INSPECTION_COMMISSIONED` | `inspection.level2_commissioned` | inspection-service |
| `EXTERNAL_VERIFIER_KEY_REGISTERED` | `bank.verifier_key_registered` | bank-service |
| `SECURITY_LOCK_TRIGGERED` / `SECURITY_UNLOCK_TRIGGERED` | `bank.security_lock_changed {action}` | bank-service |
| `STRUCTURING_REPORT` | `bank.suspicious_report_created` | bank-service (bank §3.4b) |
| `AML_RISK_CLEARED` | `reputation.elevated_risk_cleared` | reputation-service (reputation §8 mục 6 — quyết định gỡ `ELEVATED_RISK` phải defensible) |

`STRUCTURING_REPORT` và `AML_RISK_CLEARED` là 2 giá trị trước đây chỉ được nhắc ở bank/reputation mà chưa vào catalog này — đã gom về. Record cấp hệ thống/cấp cặp (key registration, security lock, AML) dùng `subject_type = SYSTEM | USER_PAIR` (**sửa 18/07/2026** — bỏ hẳn contractId sentinel; sentinel làm per-contract chain lẫn semantic, giờ mỗi subject có chain riêng sạch sẽ qua `prev_hash_subject`). OTS policy: 2 nhóm security (`EXTERNAL_VERIFIER_*`, `SECURITY_*`) anchor như event ✅ (§2.3); `STRUCTURING_REPORT`/`AML_RISK_CLEARED` cũng anchor — cùng tiêu chí "quyết định có thể bị tranh chấp".

---


## 3. Cấu trúc Chain — Dual Chain trên cùng 1 bảng

**Chốt (02/07/2026):** **không** tách 2 bảng riêng cho global chain và per-contract chain — tốn kém, trùng lặp, đi ngược lập luận "blockchain không thực tế" đã chốt cuối `services.md`. Dùng **1 bảng duy nhất**, 2 cột `previousHash` khác mục đích:

```sql
CREATE TABLE audit_record (
    record_id           CHAR(36) PRIMARY KEY,
    subject_type        VARCHAR(20) NOT NULL,   -- CONTRACT | USER_PAIR | SYSTEM (sửa 18/07/2026 —
                                                -- thay contract_id NOT NULL + sentinel: key registration,
                                                -- emergency lock, AML cấp cặp không phải contract;
                                                -- sentinel làm per-contract chain lẫn semantic)
    subject_id          VARCHAR(100) NOT NULL,  -- contractId | "buyerId:sellerId" | "SYSTEM"
    source_type         VARCHAR(50) NOT NULL,   -- danh sách đầy đủ ở §2.4 (17/07/2026): MILESTONE_EVENT | CONTRACT_SIGNED | INSPECTION_REPORT | EXTERNAL_INSPECTION_REPORT | LEVEL2_INSPECTION_COMMISSIONED | EXTERNAL_VERIFIER_KEY_REGISTERED | SECURITY_LOCK_TRIGGERED | SECURITY_UNLOCK_TRIGGERED | STRUCTURING_REPORT | AML_RISK_CLEARED
    source_event_type   VARCHAR(100) NOT NULL,  -- vd: 'milestone.settled', 'milestone.cancelled_with_penalty'
    source_hash         VARCHAR(64) NULL,       -- SỬA 18/07/2026 (round 2): hash của BẢN THỂ NGUỒN —
                                                -- signedContentHash (CONTRACT_SIGNED), reportHash
                                                -- (INSPECTION_*)... Đây mới là giá trị 3-way match với
                                                -- contract-service DB + email (§4.1); record_hash là
                                                -- chain-integrity hash, verify RIÊNG. NULL cho event
                                                -- không có source hash. Cột riêng để query/reconcile,
                                                -- không moi từ content JSON
    content              JSON NOT NULL,          -- MySQL 8 JSON (sửa dialect 18/07/2026 — JSONB là Postgres). MINIMAL HOÁ (governance §6): chỉ ID (UUID
                                             -- pseudonymous), số liệu cần verify và metadata sự kiện;
                                             -- KHÔNG contact info/tên tổ chức/địa chỉ — xoá/ẩn danh bản
                                             -- gốc ở service DB không đứt chain (quyền xoá vs immutability)
    record_hash          VARCHAR(64) NOT NULL,   -- công thức canonical — xem ngay dưới (sửa 18/07/2026)
    prev_hash_global      VARCHAR(64),            -- NULL nếu là record đầu tiên toàn hệ thống
    prev_hash_subject     VARCHAR(64),            -- NULL nếu là record đầu tiên của subject này (đổi tên từ prev_hash_contract)
    created_at           TIMESTAMP NOT NULL DEFAULT now()
);
CREATE INDEX idx_audit_record_subject ON audit_record(subject_type, subject_id, created_at);

-- TÁCH BẢNG (sửa 18/07/2026): ots_proof cũ nằm trong audit_record buộc UPDATE sau khi
-- có proof — mâu thuẫn trực tiếp với DB user chỉ INSERT+SELECT. Anchor là sự kiện
-- append-only riêng:
CREATE TABLE audit_anchor (
    anchor_id       CHAR(36) PRIMARY KEY,
    record_id        CHAR(36) NOT NULL REFERENCES audit_record(record_id),
    anchored_hash    VARCHAR(64) NOT NULL,      -- record_hash tại thời điểm anchor
    anchor_type      VARCHAR(20) NOT NULL,      -- 'OTS' (Phase 2); mở cho anchor khác
    proof            TEXT NOT NULL,             -- .ots bytes (base64)
    created_at       TIMESTAMP NOT NULL DEFAULT now()
);
-- Weekly anchor (§4.3 tầng 2) cũng INSERT vào bảng này. DB user: INSERT+SELECT cho CẢ 2 bảng.
```

**DB permission (mục 5 gốc):** DB user của `audit-service` chỉ có `INSERT` + `SELECT`, không có `UPDATE`/`DELETE` — giữ nguyên, không đổi.

**Cách tính lúc insert 1 record mới:**

```
prev_hash_global   = record_hash của row có created_at lớn nhất TOÀN BẢNG (bất kể subject)
prev_hash_subject  = record_hash của row có created_at lớn nhất CÙNG (subject_type, subject_id)
record_hash        = SHA256(canonicalJson({
                       recordId, subjectType, subjectId, sourceType, sourceEventType,
                       content, prevHashGlobal, prevHashSubject, createdAt
                     }))
-- SỬA P0 (18/07/2026): công thức cũ SHA256(content + prev_hash_global) KHÔNG commit
-- subject/source metadata và đặc biệt không commit prev_hash_subject — per-subject chain
-- khi đó chỉ là pointer thường, sửa được mà không gãy hash. Giờ mọi field đều được
-- cryptographically committed.
-- canonicalJson BẮT BUỘC chốt format, cấm object.toString():
--   key sort tăng dần, UTF-8, không whitespace; timestamp = ISO-8601 UTC milli;
--   số thập phân serialize dạng chuỗi cố định scale (tiền: 2 số lẻ); null giữ nguyên key.
```

**Lưu ý concurrency:** tính `prev_hash_global` cần đọc record cuối cùng toàn bảng — với nhiều contract insert đồng thời, cần `SELECT ... FOR UPDATE` trên row cuối hoặc 1 sequence riêng để tránh 2 insert cùng lúc tính ra cùng 1 `prev_hash_global` (race condition làm gãy chain ngay từ đầu). Với scale B2B forward contract thật (không phải giao dịch tần suất cao), serialize ở mức này không phải bottleneck.

**Ví dụ minh hoạ (đã thống nhất trong session):**

| Seq | subjectId (CONTRACT) | Event                              | recordHash | prevHashGlobal | prevHashSubject |
| --- | -------------------- | ---------------------------------- | ---------- | -------------- | --------------- |
| 1   | A                    | `milestone.settled`                | h1         | null           | null            |
| 2   | B                    | `milestone.settled`                | h2         | h1             | null            |
| 3   | A                    | `milestone.cancelled_with_penalty` | h3         | h2             | h1              |
| 4   | B                    | `milestone.settled`                | h4         | h3             | h2              |

- **Verify per-subject** (`WHERE subject_type='CONTRACT' AND subject_id = A`, theo `prevHashSubject` — tên cột đồng bộ schema mới, sửa 18/07/2026): dùng để export bằng chứng gọn cho riêng 1 vụ (VIAC/toà), tự-đủ-bằng-chứng, không cần giải thích gì về subject khác.
- **Verify global** (theo `prevHashGlobal`, đọc toàn bảng theo `created_at`): dùng để phát hiện **xoá nguyên cụm record của 1 contract** — per-contract chain đứng một mình không phát hiện được kiểu tấn công này, vì chain đó tự nó biến mất sạch không để lại dấu vết.

---

## 4. Multi-location Hash Storage (chi tiết mục 7 + 8)

**Chốt (02/07/2026):** mục 7 và mục 8 gốc mô tả **cùng 1 hành động** — 1 email gửi lúc `sign()`/report `SUBMITTED` — nhấn mạnh 2 giá trị khác nhau của nó, không phải 2 việc riêng.

### 4.1 Ba nơi lưu, phải khớp nhau

**SỬA SEMANTIC (18/07/2026, round 2) — cái gì khớp với cái gì:** thứ phải khớp 3 nơi là **`Contract.signedContentHash = audit_record.source_hash = hash trong email`** (cùng 1 giá trị = SHA256(ContractTerms), lưu 3 chỗ). Còn `audit_record.record_hash` là **chain-integrity hash** — SHA256(canonicalJson(toàn bộ record + prev hashes), §3), CỐ TÌNH khác signedContentHash và được verify riêng bằng recompute công thức (§4.2/§5). Bản cũ gọi record_hash là "nơi lưu thứ 2 của signedContentHash" — sai từ khi công thức thành canonical; hai hash hai vai, không bao giờ so trực tiếp với nhau.

| #   | Nơi lưu                                                                           | Ai kiểm soát                                      | Giá trị bảo vệ                                                             |
| --- | --------------------------------------------------------------------------------- | ------------------------------------------------- | -------------------------------------------------------------------------- |
| 1   | `contract-service` DB (`Contract.signedContentHash`)                              | Platform                                          | Nguồn gốc — nơi hash được tính ra đầu tiên                                 |
| 2   | `audit-service` DB (**`audit_record.source_hash`**, `source_type = 'CONTRACT_SIGNED'`) | Platform | Bản sao thứ 2 của signedContentHash, nằm trong record đã được chain bảo vệ |
| 3   | Email gửi buyer + seller lúc `sign()`                                             | **Ngoài platform** — hộp thư cá nhân buyer/seller | Ngoài tầm với của bất kỳ ai trong platform, kể cả Admin có full quyền root |

**Nội dung email (mục 7 — "có bản sao ở ngoài"):** snapshot đầy đủ `ContractTerms` (PDF/JSON) + dòng `signedContentHash`, gửi qua `notification-service` ngay khi `sign()` thành công.

**Giá trị timestamp (mục 8 — "có mốc thời gian độc lập"):** thời điểm gửi do hệ thống email bên thứ 3 (Gmail/Outlook) ghi lại, không phải đồng hồ do platform kiểm soát — platform không tự "lùi ngày" được mốc này sau khi email đã gửi.

**Bổ sung (08/07/2026) — email anchor cho External Verifier key (bank-service §3.5.6 lớp 2):** khi đăng ký/đổi public key của External Verifier, gửi email fingerprint của key đó thẳng vào hộp thư External Verifier — cùng cơ chế "bản sao ngoài platform" như email buyer/seller lúc `sign()`. Đây là nơi lưu thứ 3 (ngoài config platform + record `EXTERNAL_VERIFIER_KEY_REGISTERED` trong chain) cho root-of-trust của kill switch: Admin lén đổi public key trong config → lệch với record đã anchor (bắt bởi `VerifyChainJob`) **và** lệch với fingerprint External Verifier tự giữ trong mail của họ.

### 4.2 Verify logic

3 giá trị hash (contract-service DB, audit-service `AuditRecord`, hash trong email đã gửi) phải **khớp tuyệt đối** vì cùng tính từ 1 `ContractTerms` tại đúng 1 thời điểm `sign()`. Attacker muốn qua mặt phải compromise cả 3 cùng lúc — 2 DB nội bộ (khả thi nếu có quyền cao) + email đã nằm ngoài platform (bất khả thi trừ khi hack thêm tài khoản email cá nhân của buyer/seller).

### 4.3 OpenTimestamps Anchor — bổ sung (03/07/2026)

**Vấn đề mục 4.2 chưa cover:** verify logic ở trên chứng minh 3 nơi lưu khớp nhau tại đúng 1 thời điểm `sign()` — nhưng không chứng minh được **global chain hiện tại** (toàn bộ `audit_record`, không riêng 1 hợp đồng) chưa từng bị **cascade tampering**: Admin có quyền root DB xoá 1 record ở giữa/cuối chain, rồi sửa lại `prev_hash_global`/`record_hash` của toàn bộ record phía sau để chain vẫn tự-nhất-quán. `VerifyChainJob` (§5) chỉ đọc và so snapshot **hiện tại** với chính nó — một chain đã bị cascade sạch sẽ pass verify y hệt chain chưa từng bị đụng, vì không có gì để so sánh với quá khứ. Xoá ở cuối chain (record mới nhất) rẻ nhất cho attacker — không ai đang trỏ `prev_hash_global` về nó, nên không cần cascade gì cả.

**Giải pháp:** neo `record_hash` của các event quan trọng lên Bitcoin qua **OpenTimestamps (OTS)** — dịch vụ mã nguồn mở, dùng calendar server công khai miễn phí gom hash của nhiều người dùng thành 1 giao dịch Bitcoin duy nhất, chia phí ra gần như 0 đồng mỗi lần dùng. Không cần ví crypto, không cần thẻ thanh toán — chỉ gọi REST API bình thường. Verify sau này (`ots verify`, chạy offline) không phụ thuộc platform còn sống hay hợp tác hay không — đúng tinh thần multi-location đã chốt ở §4.1, thêm 1 nơi lưu nằm hẳn ngoài mọi hệ thống do platform kiểm soát.

**Trigger — 2 tầng, không chỉ theo sự kiện (sửa lại 03/07/2026, phát hiện gap so với bản đầu):** event-triggered (mỗi event ✅ trong Event Catalog) không đủ tự nó — quy mô B2B forward contract tần suất thấp (§3), hoàn toàn có thể có 1 tuần không phát sinh event ✅ nào cả, lúc đó không có OTS nào được tạo suốt tuần, cửa sổ tấn công mở toang dù cơ chế event-triggered vẫn tồn tại trên giấy. Nên `VerifyChainJob` (§5.1) **luôn** tự tạo thêm 1 OTS độc lập cho head hiện tại mỗi Chủ Nhật, không phụ thuộc tuần đó có event ✅ hay không — đảm bảo trần cứng: cửa sổ tấn công không bao giờ vượt quá 7 ngày, bất kể platform có giao dịch hay ế.

_Tầng 1 (event-triggered):_ mỗi khi 1 event thuộc danh sách ✅ ở Event Catalog (§2.1) được ghi vào `audit_record`, gọi OTS API ngay lập tức lấy `.ots` cho `record_hash` vừa tạo — vì `record_hash` đã cuốn theo toàn bộ lịch sử qua `prev_hash_global`, nó tự nhiên là commitment cho **toàn bộ chain**, không chỉ riêng record đó. Lưu `.ots` bằng cách **INSERT 1 row `audit_anchor`** trỏ `record_id` vừa ghi (sửa 18/07/2026 — không UPDATE `audit_record`, bảng đó bất biến tuyệt đối).

_Tầng 2 (weekly, luôn chạy):_ `VerifyChainJob` mỗi Chủ Nhật tự tạo 1 OTS mới cho `record_hash` của head hiện tại (INSERT `audit_anchor` mới, `record_id` = head), kể cả khi tuần đó không có event ✅ nào — dùng làm điểm neo cho lần verify tuần sau (§5.1).

**Gửi cho buyer/seller — chỉ tại `milestone.settled`:** sau khi ghi audit record và lấy `.ots`, audit-service publish `notification.milestone_anchor_requested` mang `{eventId, sourceEventId, contractId, milestoneId, recipients, recordHash, otsProof, settlementSummary}`. `recipients` được propagate từ payload `milestone.settled` vì audit-service là pure consumer, không Feign ngược user-service. Notification đính proof vào email quyết toán; không gửi ở mọi event ✅ trung gian để tránh noise/spam.

**Cửa sổ tấn công còn lại:** trần cứng 7 ngày nhờ tầng 2 (luôn chạy, không phụ thuộc hoạt động platform); tuần nào có event ✅ thì cửa sổ thực tế hẹp hơn nhiều nhờ tầng 1. Không triệt tiêu hoàn toàn — giới hạn cố hữu của bất kỳ mô hình chốt-theo-mốc nào (kể cả anchor liên tục theo mili-giây cũng còn khe hở, chỉ hẹp gần bằng 0), không phải lỗi thực thi.

**Giới hạn cần nói rõ khi trình bày:** OTS chỉ giải quyết vế "bằng chứng có tồn tại độc lập ngoài platform hay không" — **không** giải quyết vế "có ai chủ động đi đối chiếu hay không". Xem cập nhật ở §6.

### 4.4 External Verifier self-service query — biến "phải có người chủ động nhìn" thành chủ động được (mới, 08/07/2026)

**Vấn đề §4.3 để lại:** OTS đảm bảo bằng chứng tồn tại độc lập, nhưng vế "phải có người chủ động đối chiếu" vẫn treo — trong kiến trúc gốc, `VerifyChainJob` (§5.1, chạy **trong** platform) là nơi duy nhất chủ động chạy phép đối chiếu. Nếu chính job đó bị Admin vô hiệu hoá/kết quả bị lờ đi thì không ai phát hiện.

**Giải pháp — cho External Verifier một watchdog độc lập, chạy NGOÀI platform:** expose `GET /api/v1/security/audit-hash?contractId=...` (**sửa 17/07/2026:** endpoint do **audit-service** serve trực tiếp — data `audit_record` là của nó; để bank-service serve thì phải Feign sang đọc hộ, thêm 1 hop vô nghĩa. Bank §3.5.1 giữ đúng 2 đường lock/unlock. Endpoint chỉ-đọc, auth nhẹ `X-Api-Key`, không cần chữ ký vì hash không phải bí mật) để tổ chức vận hành platform (Software Buyer — VICOFA/VRA/doanh nghiệp bất kỳ, **không cột cứng 1 tên**) tự query `record_hash` hiện tại của platform và đối soát với bản hash "mộc" họ đang giữ (nhận qua email anchor §4.1). Đây là lần đầu phép đối chiếu **không** phụ thuộc job nội bộ platform: verifier tự chạy được bất cứ lúc nào, bằng lịch riêng của họ, platform không can thiệp được.

**Nối vào kill switch:** nếu verifier phát hiện hash lệch → họ ký lệnh `emergency-lock` (bank-service §3.5) bằng private key của họ → đóng băng toàn hệ thống. Đường phát hiện (query) và đường phản ứng (lock) đều nằm ngoài tầm Admin — Admin không có private key nên không giả được lệnh, cũng không tắt được watchdog vì nó chạy ở hạ tầng verifier.

**Thu hẹp §6 tới đâu:** vế "phải có người chủ động nhìn" không còn phụ thuộc **duy nhất** vào job nội bộ — có thêm 1 bên ngoài chủ động nhìn được, với công cụ tự động (query API + đối soát). **Không đóng hoàn toàn:** nếu External Verifier không bao giờ query, hoặc thông đồng với Admin, thì vẫn hở — nhưng đây là bên độc lập, khác người, khác hạ tầng, khác động cơ so với Admin, nên rủi ro thấp hơn hẳn so với chỉ dựa 1 job nội bộ. Giới hạn cố hữu trusted-operator vẫn còn, chỉ nhỏ lại.

---

### 4.5 Internal read API cho ledger reconciliation (mới, 18/07/2026)

`GET /internal/v1/audit/records?contractId&sourceType&from&to` — read-only, service-to-service secret, **không route Gateway** (gateway §3.5). Caller Phase 2 duy nhất: `LedgerAuditReconciliationJob` của bank-service (bank §5b.1) — đối chiếu số tiền trong `audit_record.content` vs ledger, lấp lớp check "tiền đi đúng như record đã anchor chưa" mà chain verify (chỉ soát hash) không cover. Audit-service vẫn là writer duy nhất; API này không có đường ghi.


## 5. Weekly Verify Job

**Chốt (02/07/2026):** không chỉ verify lúc export EUDR report (quá hiếm, phát hiện muộn) — thêm `@Scheduled` job chạy **định kỳ hàng tuần, 2-3h sáng Chủ Nhật** (giờ traffic thấp nhất, cùng convention với Outbox Poller chạy nền).

### 5.1 Thứ tự với Weekly Digest (mục 8)

Verify chạy **trước**, digest gửi **sau**, cùng 1 lần chạy tuần — không phải 2 job độc lập không biết tới nhau:

```
02:00 CN — VerifyChainJob chạy:
  1. Đọc toàn bộ audit_record theo created_at (global chain)
  2. Tính lại record_hash từng row theo công thức §3, so với record_hash đã lưu
  3. Lấy anchoredHash = `anchored_hash` mới nhất trong `audit_anchor` (event-triggered trong tuần, hoặc weekly anchor tuần trước nếu tuần này chưa có event ✅ nào) → query SELECT 1 FROM audit_record WHERE record_hash = anchoredHash. Vì record_hash phụ thuộc đệ quy vào toàn bộ lịch sử phía trước, cascade tampering ở bất kỳ điểm nào trước hoặc tại vị trí đó sẽ làm giá trị này biến mất khỏi bảng — không cần biết attacker sửa bao nhiêu dòng, chỉ 1 query là đủ
  4. Tạo OTS mới cho record_hash của head hiện tại (tầng 2, §4.3) — luôn chạy, không phụ thuộc tuần này có event ✅ hay không, dùng làm anchoredHash cho lần verify tuần sau
  5. Nếu khớp 100% ở bước 2 và anchoredHash vẫn tồn tại ở bước 3 → set flag WEEKLY_VERIFY_OK
  6. Nếu có row lệch (bước 2) hoặc anchoredHash không tìm thấy (bước 3) → set flag WEEKLY_VERIFY_FAILED, ghi lại vị trí lệch đầu tiên tìm được, hoặc note "anchored hash missing — cascade tampering" nếu bước 2 pass nhưng bước 3 fail

02:30 CN — DigestJob chạy (chỉ sau khi VerifyChainJob xong):
  - Nếu WEEKLY_VERIFY_OK → tính hash head và publish `notification.audit_digest_requested`
    tới danh sách Software Buyer contacts
  - Nếu WEEKLY_VERIFY_FAILED → KHÔNG publish digest; publish
    `notification.audit_failure_requested` theo flow alert §5.2
```

**Note quy mô (03/07/2026, ghi nhận có chủ đích, không thiết kế chi tiết — ngoài phạm vi capstone):** bước 1-2 quét toàn bảng mỗi lần chạy — chi phí O(n) theo số record trong bảng, không phải chi phí hash (SHA256 mỗi record luôn O(1), không đổi theo kích thước chain — xem cách tính ở §3). Ở quy mô vài nghìn tới vài chục nghìn record, không đáng lo. Nếu vận hành nhiều năm và bảng phình tới hàng triệu record, `VerifyChainJob` sẽ chậm dần tuyến tính theo thời gian — hướng fix (không detail ở đây) là incremental verify: lưu con trỏ `last_verified_record_id`, mỗi lần chỉ verify phần mới từ lần chạy trước thay vì quét lại từ đầu.

### 5.2 Alert Routing khi Verify Fail

**Vấn đề đã nhận diện trong session:** nếu chỉ báo Admin rồi đợi Admin điều tra mới quyết định có báo tiếp không — trường hợp chính Admin là người sửa data, Admin chỉ cần không bao giờ "điều tra xong" là chặn được cảnh báo vĩnh viễn. Không được để 1 người duy nhất làm gatekeeper.

**Chốt cơ chế:** verify fail → hệ thống **tự động bắn song song, không qua bước duyệt của ai**:

1. **Admin** — để điều tra kỹ thuật, xác định chính xác record/contract nào bị ảnh hưởng.
2. **Nhiều địa chỉ liên hệ phía Software Buyer** (hiệp hội/DN deploy) — **không chỉ 1 người** (giảm rủi ro collusion của đúng 1 cá nhân phía nhận, dù không loại trừ hoàn toàn được nếu tất cả cùng thông đồng — xem §6).

Cả 2 hướng nằm trong cùng payload `notification.audit_failure_requested`, gửi **cùng lúc, tự động qua `notification-service`**, không có bước "Admin xác nhận trước rồi mới gửi tiếp".

**Về việc báo cho buyer/seller trong từng hợp đồng cụ thể:** không báo ngay lúc verify fail (chưa xác định được contract nào bị ảnh hưởng, báo nhầm gây hoang mang). Sau khi Admin điều tra xong, khoanh vùng đúng `contract_id` bị ảnh hưởng → mới thông báo cho đúng buyer/seller của contract đó qua kênh liên lạc thông thường (không phải tự động, cần người xác nhận nội dung trước khi gửi vì đây là thông tin nhạy cảm, cần chính xác).

---

## 6. Known Limitation (ghi nhận có chủ đích, không phải điểm mù)

Toàn bộ thiết kế 3 lớp (hash chain + multi-location + email anchor) đứng trên giả định **trusted operator** đã chốt từ đầu (`services.md`, dòng cuối): _"Platform có trusted operator — bài toán trustless consensus không tồn tại."_

**Kịch bản không giải quyết được trong scope này:** nếu Admin **và** toàn bộ (hoặc đa số) người nhận cảnh báo phía Software Buyer cùng thông đồng — không có cơ chế software nào trong kiến trúc hiện tại chặn được, vì đây chính xác là bài toán trustless consensus mà blockchain giải quyết bằng cách phân tán ra nhiều node độc lập không ai kiểm soát hết, và nhóm đã quyết định không theo hướng đó (5 tháng, 3 người, không thực tế).

**2 lớp vẫn đứng vững kể cả trong kịch bản xấu nhất này:**

- Email anchor lúc `sign()` gửi cho **buyer/seller thật trong từng hợp đồng cụ thể** — người này khác hoàn toàn, thời điểm khác hoàn toàn so với người nhận weekly digest. Không bị ảnh hưởng bởi collusion ở phía hiệp hội.
- Bằng chứng toán học (hash mismatch) tồn tại độc lập với việc có ai chủ động báo hay không — bất kỳ bên thứ 3 nào sau này (luật sư, chuyên gia toà chỉ định) tự chạy lại được đúng phép verify này trên dữ liệu thô, không cần platform "thông báo" mới phát hiện ra.
- OTS anchor (§4.3, bổ sung 03/07/2026) cho global commitment hash — verify được ngay cả khi platform sập hoàn toàn, không domain, không server, vì bằng chứng nằm trên Bitcoin, không nằm trong hệ thống của platform.
- **External Verifier self-service watchdog (§4.4, bổ sung 08/07/2026)** — tổ chức vận hành platform (Software Buyer, không cột cứng VICOFA) tự query hash đối soát bằng lịch riêng, ngoài tầm Admin, và có đường ký lệnh kill switch (bank-service §3.5) nếu phát hiện lệch. Đây là lần đầu phép đối chiếu chủ động **không** phụ thuộc duy nhất vào job nội bộ platform.

**Cập nhật (03/07/2026) — OTS thu hẹp limitation này, không đóng hoàn toàn, cần nói rõ ranh giới khi trình bày:** OTS chỉ giải quyết vế _"bằng chứng toán học có tồn tại độc lập ngoài platform hay không"_ — nếu Admin và toàn bộ (hoặc đa số) người nhận digest cùng thông đồng **và không ai chủ động đối chiếu** OTS proof với chain hiện tại, thì có anchor hay không cũng không tạo ra khác biệt thực tế, vì `VerifyChainJob` (§5.1 bước 3) là nơi duy nhất trong kiến trúc chủ động chạy phép đối chiếu đó — nếu chính job này bị vô hiệu hoá hoặc kết quả bị bỏ qua, OTS proof vẫn tồn tại trên Bitcoin nhưng không ai tra cứu nó. OTS không tạo ra động lực phát hiện — nó chỉ đảm bảo rằng **khi** có người chịu nhìn (luật sư, chuyên gia toà chỉ định, kiểm toán độc lập), họ nhìn được sự thật mà không cần platform "thông báo" hay "cho phép". Vế "phải có người chủ động nhìn" vẫn là giới hạn cố hữu của mô hình trusted-operator, OTS không đổi được vế này.

**Cập nhật (08/07/2026) — External Verifier watchdog thu hẹp thêm vế "phải có người chủ động nhìn", vẫn không đóng hoàn toàn:** §4.4 + kill switch (bank-service §3.5) đưa phép đối chiếu ra **ngoài** platform — một bên độc lập (Software Buyer, khác người/khác hạ tầng/khác động cơ so với Admin) tự query hash và tự đóng băng hệ thống được khi phát hiện lệch, không còn phụ thuộc **duy nhất** vào `VerifyChainJob` nội bộ. Nhưng nếu External Verifier không bao giờ chủ động query, hoặc chính họ thông đồng với Admin, thì vẫn hở — đây là bài toán trustless consensus cố hữu mà nhóm đã quyết định không giải bằng blockchain. Kill switch chuyển "người chủ động nhìn" từ **bên trong** (dễ bị Admin vô hiệu) sang **có thêm 1 bên ngoài** (khó hơn nhiều), không xoá hẳn nhu cầu "phải có người chịu nhìn".

**Khi hội đồng hỏi:** _"Đây là giới hạn cố hữu của mô hình trusted-operator mà nhóm chọn có chủ đích thay vì blockchain, phù hợp ràng buộc thời gian/nhân lực thật của dự án — không phải điểm mù bị bỏ sót. External Verifier kill switch thu hẹp lỗ hổng bằng cách đưa cả phát hiện lẫn phản ứng ra ngoài tầm Admin nội bộ."_

---

## 7. Status — Hash Chain Design

**Chốt (02/07/2026):** 6/8 Milestone event vào chain (loại `flagged`, `milestone.settled.local-check`). Schema `AuditRecord` dual-chain (`prevHashGlobal` + `prevHashSubject` — tên cột đổi 18/07/2026 cùng subject model §3) trên cùng 1 bảng; `audit_anchor` tách riêng cho proof (18/07/2026). Mục 7+8 = cùng 1 email, 2 giá trị (bản sao ngoài platform + timestamp độc lập). 3 nơi lưu source hash (`contract-service` DB, `audit_record.source_hash`, email) phải khớp tuyệt đối; `record_hash` là chain-integrity, verify riêng (sửa semantic 18/07/2026, §4.1). Verify job chạy hàng tuần 2-3h sáng CN, trước digest. Verify fail → alert tự động song song Admin + nhiều contact Software Buyer, không qua gatekeeper 1 người. Buyer/seller từng hợp đồng chỉ được báo sau khi khoanh vùng xong, không tự động. Collusion toàn diện (Admin + hiệp hội) là known limitation có chủ đích, không phải thiếu sót.

**Chốt bổ sung (03/07/2026) — OpenTimestamps Anchor** (~~thêm cột `ots_proof` vào `audit_record`~~ — **superseded 18/07/2026:** proof nằm ở bảng append-only `audit_anchor` riêng, §3; giữ nguyên phần còn lại của chốt này)**:** Anchor lên Bitcoin qua OpenTimestamps theo sự kiện (không theo lịch cố định) cho các event ✅ trong Event Catalog (§4.3) — miễn phí, không cần ví crypto/thẻ thanh toán, dùng calendar server công khai. Gửi `.ots` cho buyer/seller chỉ tại `milestone.settled`, tránh noise email ở các bước trung gian. `VerifyChainJob` (§5.1) thêm bước 3 đối chiếu OTS proof với head hiện tại của chain — bắt được **cascade tampering** (xoá + sửa lại toàn bộ prevHash phía sau để chain tự-nhất-quán), kiểu tấn công mà verify tự-so-với-chính-nó ở bản gốc 02/07 không phát hiện được, vì nó chỉ so sánh trạng thái hiện tại với chính nó, không có điểm neo quá khứ để đối chiếu. Known Limitation §6 được thu hẹp một phần (vế "bằng chứng độc lập ngoài platform"), **không** đóng hoàn toàn — vế "phải có người chủ động đối chiếu" vẫn còn nguyên, đây là giới hạn cố hữu, không phải thiếu sót của lần bổ sung này.

**Chốt bổ sung (08/07/2026, chiều) — thêm External Verifier layer (đã review + đóng lại 13/07/2026):**
- **§2.3** — 3 `source_type` mới vào Event Catalog + `audit_record`: `EXTERNAL_VERIFIER_KEY_REGISTERED` (root-of-trust kill switch), `SECURITY_LOCK_TRIGGERED`/`SECURITY_UNLOCK_TRIGGERED` (quyết định đóng/mở băng). Cả 3 OTS-anchor như event ✅.
- **§4.1** — email anchor mở rộng: gửi fingerprint public key External Verifier lúc đăng ký/đổi key (nơi lưu thứ 3 cho root-of-trust).
- **§4.4** — External Verifier self-service query (`GET /audit-hash`, chỉ-đọc): bên ngoài platform tự đối soát hash bằng lịch riêng, nối vào kill switch (bank-service §3.5). Lần đầu phép đối chiếu chủ động không phụ thuộc **duy nhất** job nội bộ.
- **§6** — Known Limitation thu hẹp thêm (vế "phải có người chủ động nhìn"), **không** đóng hoàn toàn — External Verifier vẫn phải chịu query, và collusion Admin+verifier vẫn là giới hạn cố hữu.

**Generic hoá — KHÔNG cột cứng VICOFA:** mọi phần mới dùng "External Verifier / Software Buyer" (tổ chức mua & vận hành platform bất kỳ), không hardcode tên tổ chức. Trả lời hội đồng câu "nếu VICOFA không mua thì sao": cơ chế chạy với bất kỳ tổ chức vận hành nào, VICOFA chỉ là ví dụ minh hoạ.

**Đã đóng (13/07/2026):** đồng bộ với `bank-service-phase2-design.md` §3.5 xong — 2 `source_type` mới khớp 2 bên; review §2.3/§4.4 end-to-end xong — chain append cho External Verifier key + security lock/unlock nhất quán với 3 lớp gốc. **Sẵn sàng promote lên SDS/Architecture.**

**Cập nhật (17/07/2026):** thêm §2.4 Audit Ingestion Catalog — mọi nguồn vào chain là domain event, audit-service là writer duy nhất của `audit_record`; gom 4 `source_type` lệch catalog (`EXTERNAL_INSPECTION_REPORT`, `LEVEL2_INSPECTION_COMMISSIONED`, `STRUCTURING_REPORT`, `AML_RISK_CLEARED`); `CONTRACT_SIGNED` ingest qua `contract.signed` (payload + `signedContentHash`); endpoint `audit-hash` chuyển về audit-service (§4.4).

Hash Chain — **ĐÓNG SESSION HOÀN TOÀN**: 3 lớp gốc + lớp External Verifier (§2.3/§4.4) đều đã đóng, sẵn sàng đưa vào Architecture/SDS/TechnicalSpec chính thức.

---

_Design session: 02/07/2026 · Bổ sung OpenTimestamps Anchor: 03/07/2026 · Cập nhật 08/07/2026 chiều (MỞ LẠI: External Verifier layer — 3 source_type mới §2.3, email anchor key fingerprint §4.1, self-service query §4.4, thu hẹp Known Limitation §6; generic hoá không cột cứng VICOFA) · Cập nhật 13/07/2026 (review §2.3/§4.4 end-to-end, đồng bộ source_type với bank-service §3.5 — đóng session hoàn toàn) · Cập nhật 17/07/2026 (§2.4 Audit Ingestion Catalog thống nhất transport + gom source_type lệch; audit-hash về audit-service) · Review pass 18/07/2026 (P0: record_hash canonical toàn field + commit prev_hash_subject; tách audit_anchor khỏi audit_record — hết mâu thuẫn INSERT-only; subject_type/subject_id thay contractId sentinel) · Chưa code · **Sẵn sàng đưa vào Architecture/SDS/TechnicalSpec chính thức.**_
