---
name: bank-service-phase2-design
description: "Bank-service — mock legal custody cho tiền, mô hình FBO/ledger thay vì account-per-contract. Nguồn: design session 03/07/2026, cập nhật 04/07/2026, 08/07/2026 (tách entryType 2 loại cọc, mapping Provisional Settlement Level 2, bank.large_transaction_flagged đúng ngưỡng/chủ thể pháp lý)."
status: DESIGNED — chưa code.
metadata:
  type: design
  phase: 2
  extends: "services.md § bank-service (8086)"
  related: "milestone-escrow-phase2-design.md §2.1, §2.3, §6, §6.3, §7 (nguồn amount cần lock/release/seize + 2 event cấp Contract mới); AgriContract_02_GiaiPhap_MoHinh_v5.docx Tầng 5 (Escrow Holder — Ngân hàng), §3.3 (superseded, xem Known Limitations)"
---

## 1. Bối cảnh & Scope

**Chốt (03/07/2026):** bank-service Phase 2 chỉ giữ **đúng 1 chức năng** — legal custody hợp pháp cho tiền, giải quyết "Rủi ro 1 — nghiêm trọng nhất" trong `AgriContract_02_GiaiPhap_MoHinh_v5.docx` (Nghị định 52/2024, Điều 8, Khoản 7 — cấm cung ứng trung gian thanh toán không phép). Platform (escrow-service) không tự giữ tiền, chỉ ra lệnh; bank-service là nơi tiền (mock) thực sự nằm.

**Không phải arbitrator.** Vai trò "ngân hàng đóng arbitrator độc lập" trong docx gốc (§3.3) bị **superseded** bởi INSPECTOR 3-tier + symmetric escalation đã build (`milestone-escrow-phase2-design.md` §5). Lý do: arbitrator đòi hỏi 3 điều kiện — độc lập, trách nhiệm pháp lý, không skin-in-the-game — chỉ đúng nếu ngân hàng đó **thật**, được NHNN giám sát thật. Bank-service Phase 2 vẫn mock (không ngân hàng nào ký API cho 1 đồ án), nên không thoả được 3 điều kiện đó — dùng mock bank làm arbitrator tạo cảm giác độc lập giả, tệ hơn không có. Đã ghi vào `decisions.md` — `[2026-07-03] Ngân hàng-arbitrator (docx v5 §3.3) — superseded bởi INSPECTOR 3-tier`.

**Không tích hợp thật trong Phase 2.** `AgriContract_02` §4.2 (roadmap) ghi "Bank Integration — Tích hợp escrow thật" như 1 mục tiêu — không đạt được trong giới hạn đồ án. Bank-service thiết kế để **nếu sau này có ai tích hợp thật**, escrow-service không cần sửa business logic — đúng claim docx tự đưa ra ở FAQ ("logic không đổi khi swap mock bằng Agribank API thật"), nhưng claim đó chỉ đúng nếu ranh giới interface sạch ngay từ Phase 2 (xem §3).

---

## 2. Domain Model — Ledger, không phải Account-per-Contract

**Vấn đề với model "mỗi contract 1 account":** không khớp cách ngân hàng/fintech thật vận hành, và tạo race condition lúc provisioning (account phải tồn tại trước khi lock, nhưng trigger tạo account lại đặt sau thời điểm cần lock — gà-trứng).

**Chốt (03/07/2026) — mô hình FBO/Omnibus (chuẩn công nghiệp: PayPal, ví điện tử, escrow bất động sản, marketplace escrow):** chỉ **1 chỗ giữ tiền chung** (mock — không cần model chi tiết, chỉ cần biết tổng luôn khớp với tổng ledger). Toàn bộ chi tiết "ai sở hữu bao nhiêu, cho việc gì" nằm trong **`LedgerEntry`** — append-only, không sửa/xoá sau khi ghi, cùng nguyên tắc `audit_record` đã áp ở hash-chain design.

Số dư (buyer còn bao nhiêu, đang khoá bao nhiêu cho hợp đồng nào) **không lưu sẵn** ở đâu cả — luôn là **kết quả cộng dồn** từ các dòng `LedgerEntry` liên quan, tính lúc cần, không đọc 1 ô có sẵn rồi hy vọng nó đúng.

| Field | Loại | Ghi chú |
|---|---|---|
| `entryId` | UUID | |
| `sourceEventId` | UUID | ID của event gốc kích hoạt entry này (từ Outbox message escrow-service gửi sang) — dùng làm idempotency key, xem §4 |
| `contractId` | UUID | Bắt buộc — tách hợp đồng này với hợp đồng khác |
| `milestoneId` | UUID, nullable | **NULL nếu là cọc cấp Contract** (`buyerDepositRate` hoặc `sellerDepositRate` — khoá 1 lần lúc SIGNED, không thuộc milestone nào — release/seize qua `contract.settled`/`contract.cancelled`, §3.2), có giá trị nếu là `batchAmount` của 1 milestone cụ thể (`milestone.settled`/`milestone.cancelled_with_penalty`, §3.1) — 2 loại khoá này đá vào 2 field khác nhau của `ContractTerms` (`milestone-escrow-phase2-design.md` §2.1), không được gộp chung nếu không sẽ không tách được milestone 3 đã settle với milestone 4 vẫn đang khoá |
| `userId` | UUID | Buyer hoặc seller, tuỳ `entryType` |
| `entryType` | Enum | `LOCK_BUYER_DEPOSIT` \| `LOCK_SELLER_DEPOSIT` \| `LOCK_MILESTONE` \| `RELEASE_TO_SELLER` \| `SEIZE_PENALTY` \| `REFUND_TO_BUYER` (**sửa 08/07/2026** — tách `LOCK_DEPOSIT` cũ thành 2 giá trị, xem ghi chú dưới) |
| `amount` | Money | |
| `createdAt` | Timestamp | |

`UNIQUE(sourceEventId)` — chặn duplicate xử lý cùng 1 event 2 lần (xem §4).

**Sửa (08/07/2026) — comment schema sai + không phân biệt được 2 loại cọc từ ledger thô:** comment cũ (`bank-service-phase2-design.md` §6 + SDS) ghi *"`milestoneId = NULL` = `buyerDepositRate`"* — sai từ 06/07/2026 khi `sellerDepositRate` optional ra đời, vì seller deposit cũng lock với `milestoneId = NULL`. Có **2 khoản `LOCK` cấp Contract** cùng `milestoneId = NULL`, trước đây phân biệt được qua `userId` (biết ai là buyer/seller của contract) **nhưng** bank-service tự nó agnostic — không biết `userId` nào là buyer/seller nếu chỉ đọc ledger thô, phải tra ngược sang contract-service mới biết. Ngược tinh thần "bằng chứng tự đứng" (ledger tự giải thích được, không cần tra chéo service khác) đã theo suốt thiết kế. **Chốt:** tách `entryType` thành `LOCK_BUYER_DEPOSIT`/`LOCK_SELLER_DEPOSIT` thay vì chung `LOCK_DEPOSIT` — ledger tự giải thích được ngay, không cần biết `userId` là ai. Enum thêm 1 giá trị, rẻ hơn phương án thêm cột `deposit_party` riêng.

---

## 3. Interaction với escrow-service — Event Flow

**Chốt (03/07/2026):** escrow-service là **actor duy nhất** gọi bank-service — contract-service không bao giờ nói chuyện trực tiếp với bank-service, giữ nguyên ranh giới đã có ("contract-service quản lý delivery state, escrow-service quản lý tiền", `milestone-escrow-phase2-design.md` §2.3).

**Không fire-and-forget — đợi confirmation, cùng pattern `EscrowEventConsumer` Phase 1 đã dùng** (contract-service đợi `escrow.buyer_locked` mới `activate()`, không tự set trước):

```
escrow-service → bank-service:  bank.lock_requested        (contractId, milestoneId?, userId, amount, sourceEventId)
bank-service   → escrow-service: bank.lock_completed        (thành công → escrow-service set state LOCKED)
                                  bank.lock_failed           (thất bại → escrow-service giữ nguyên state, xử lý theo nhánh fail)
```

Cùng pattern cho `release`, `seize`, `refund` — 4 loại request/confirmation, không cặp nào set state trước khi có confirmation.

**Tại sao không được set trước:** nếu escrow-service publish `bank.lock_requested` rồi tự set `LOCKED` ngay, trong khi bank-service (mock) fail vì lý do gì đó — 2 bên lệch state, không ai biết cho tới khi có người kiểm tra thủ công. Đúng dạng dual-write problem đã học ở Phase 1 (`ApplicationEvent` non-durable), chỉ khác hướng: lần trước là sync-call-fail-without-rollback, lần này là async-fire-and-forget-không-đợi-confirm.

### 3.1 Ledger entries từ Delta 1/2 pro-rata — `milestone.settled` (mới, 04/07/2026)

**Bối cảnh:** `batchAmount` khoá full theo `committedQuantity × agreedPrice` từ sớm (`milestone-escrow-phase2-design.md` §6.2). Nhưng Delta 1/Delta 2 pro-rata (§4 cùng file) có thể khiến số tiền thật seller đáng nhận **thấp hơn** `batchAmount` đã khoá — phần chênh lệch phải trả lại buyer, không được im lặng giữ trong pooled account.

**Chốt (04/07/2026):** payload `milestone.settled` mang `lockedAmount` (= `batchAmount` gốc) và `actualAmount` (= số tiền thật sau Delta 1/2). escrow-service nhận event, tự tính `diff = lockedAmount - actualAmount`:

```
Nếu diff > 0:
    bank.release_requested   (contractId, milestoneId, userId=sellerId, amount=actualAmount, entryType=RELEASE_TO_SELLER, sourceEventId)
    bank.refund_requested    (contractId, milestoneId, userId=buyerId,  amount=diff,          entryType=REFUND_TO_BUYER,  sourceEventId khác)
Nếu diff == 0 (giao đủ, không shortfall):
    bank.release_requested   (contractId, milestoneId, userId=sellerId, amount=actualAmount, entryType=RELEASE_TO_SELLER, sourceEventId)
```

Mỗi request có `sourceEventId` riêng (idempotency key, §4) dù cùng phát sinh từ 1 `milestone.settled` — 2 `LedgerEntry` là 2 hành động tiền độc lập, không phải 1 hành động ghi 2 dòng.

### 3.2 Ledger entries từ `buyerDepositRate` — `contract.settled` / `contract.cancelled` (mới, 04/07/2026)

**Bối cảnh:** `buyerDepositRate` là khoá cấp Contract, không gắn milestone nào — cần 2 event cấp Contract riêng để trigger, đã thêm vào Event Catalog (`milestone-escrow-phase2-design.md` §6.3, §7.2):

| Event nhận | `entryType` ghi vào ledger | `userId` | `milestoneId` |
|---|---|---|---|
| `contract.settled` (hợp đồng hoàn tất bình thường) | `REFUND_TO_BUYER` | buyerId | `NULL` |
| `contract.cancelled` (initiatedBy=SELLER) | `REFUND_TO_BUYER` | buyerId | `NULL` |
| `contract.cancelled` (initiatedBy=BUYER) | `SEIZE_PENALTY` | buyerId | `NULL` |

Không cần `entryType` mới ngoài phần đã tách ở §2 (08/07/2026) — enum hiện tại (`LOCK_BUYER_DEPOSIT` lúc `SIGNED`, rồi `RELEASE_TO_SELLER`/`SEIZE_PENALTY`/`REFUND_TO_BUYER` tuỳ kết quả) đã đủ diễn tả trọn vòng đời của `buyerDepositRate`. Tương tự, `sellerDepositRate` dùng `LOCK_SELLER_DEPOSIT` lúc `SIGNED`.

### 3.3 Ledger entries từ Provisional Settlement Level 2 — 3 event mới (08/07/2026)

**Bối cảnh:** `milestone-escrow-phase2-design.md` §3.2 định nghĩa 3 event provisional (`milestone.level2_provisional_settled` / `..._buffer_reconciled` / `..._terminal_settled`) và mechanics tiền đầy đủ 3 bước — nhưng bank-service trước bản này hoàn toàn chưa map event nào của luồng này sang `LedgerEntry`. Không cần `entryType` mới — vẫn `RELEASE_TO_SELLER`/`REFUND_TO_BUYER`, mỗi động tác 1 `sourceEventId` riêng (đúng pattern Delta 1/2 ở §3.1).

| Event nhận | Ký hiệu (xem `milestone-escrow-phase2-design.md` §3.2) | `LedgerEntry` bắn ra |
|---|---|---|
| `milestone.level2_provisional_settled` | Bước 1 — release `X15×(1−rate)` cho seller, giữ khoá phần còn lại (`X15×rate` + `batchAmount−X15`) | `RELEASE_TO_SELLER(X15×(1−rate))` — 1 entry duy nhất, không refund buyer ở bước này |
| `milestone.level2_buffer_reconciled` | Bước 2 — bù thêm seller `max(0, min(X2,batchAmount) − X15×(1−rate))`, refund buyer phần còn lại | `RELEASE_TO_SELLER(bù thêm)` + `REFUND_TO_BUYER(còn lại)` — 2 entry, `sourceEventId` riêng mỗi entry dù cùng 1 event gốc |
| `milestone.level2_terminal_settled` | Bước 3 — seller nhận nốt `X15×rate` (buffer của chính seller), buyer nhận `batchAmount − X15` | `RELEASE_TO_SELLER(X15×rate)` + `REFUND_TO_BUYER(batchAmount − X15)` — 2 entry riêng |

Payload mỗi event mang sẵn số tiền đã tính (không phải rate thô) — `contract-service`/`escrow-service` tính theo mechanics ở `milestone-escrow-phase2-design.md` §3.2, bank-service chỉ ghi ledger theo số nhận được, không tự tính lại `X15`/`X2`/`rate`.

### 3.4 `bank.large_transaction_flagged` — báo cáo giao dịch giá trị lớn, không hold (mới, 08/07/2026)

**Bối cảnh:** `reputation-service-phase2-design.md` §8 (bản trước) đặt hold cứng `CONFIRM_CLEAN` cho mọi giao dịch vượt ngưỡng tuyệt đối, không phân biệt hành vi — giết happy path (đa số hợp đồng nông sản thật vượt xa ngưỡng chỉ vì khối lượng lớn) và sai chủ thể pháp lý (nghĩa vụ báo cáo giao dịch giá trị lớn thuộc **tổ chức tài chính giữ tiền**, không phải platform số hoá hợp đồng — Luật PCRT 2022 Điều 4, Thông tư 27/2025/TT-NHNN). Đã tách lại đúng vai trò ở `reputation-service-phase2-design.md` §8 — bank-service chỉ **ghi nhận + audit trail cho nghĩa vụ báo cáo**, không hold giao dịch.

**Ngưỡng — [VERIFIED ✓, 08/07/2026]:** `LedgerEntry` nào ≥ **500.000.000 VNĐ (giao dịch chuyển tiền điện tử trong nước)** → publish `bank.large_transaction_flagged`. Đây **không phải** ngưỡng "giao dịch có giá trị lớn phải báo cáo" chung (400 triệu, Điều 6 TT27/2025/TT-NHNN, kế thừa QĐ 11/2023/QĐ-TTg — áp dụng chủ yếu ngữ cảnh nộp/rút tiền mặt). Các operation của `ledger_entry` (lock/release/refund) là **chuyển khoản giữa các tài khoản ngân hàng** — đúng định nghĩa "giao dịch chuyển tiền điện tử" ở **Điều 9 Thông tư 27/2025/TT-NHNN**, ngưỡng 500 triệu (giữ nguyên từ 2007, TT27 chỉ làm rõ thêm trách nhiệm kỹ thuật, không đổi số) — 1.000 USD nếu có bên quốc tế tham gia (không áp dụng ở scope hiện tại, mock 1 pooled account nội địa). **Không dùng 400 triệu** — đó là ngưỡng cho loại giao dịch khác (tiền mặt), không phải loại giao dịch của bank-service.

**Cơ chế:** bắn ngay khi `LedgerEntry` được ghi (không hold, không chờ Admin) — cùng transaction với insert `ledger_entry`. Payload: `{entryId, contractId, userId, amount, entryType, createdAt}`. Consumer: reputation-service (composite fraud score, không phải trigger hold độc lập — xem `reputation-service-phase2-design.md` §8), audit-service (bằng chứng cho nghĩa vụ báo cáo nếu sau này tích hợp ngân hàng thật). Thực tế ngân hàng thật vẫn cho giao dịch chạy rồi báo cáo Cục Phòng, chống rửa tiền trong 1 ngày làm việc (dữ liệu điện tử) — không đóng băng giao dịch chỉ vì vượt ngưỡng.

### 3.4b Consume `analytics.structuring_pattern_detected` — báo cáo giao dịch **khả nghi** (structuring), khác §3.4 (mới, 10/07/2026)

**Phân biệt với §3.4 — 2 nghĩa vụ báo cáo khác nhau, đừng gộp:** §3.4 là báo cáo **giao dịch giá trị lớn** (ngưỡng đơn ≥500 triệu, bank-service tự phát hiện lúc ghi `LedgerEntry`, publish). Mục này là báo cáo **giao dịch khả nghi** (structuring — hành vi rải mỏng nhiều hợp đồng dưới ngưỡng để né §3.4). Structuring không thể phát hiện từ 1 `LedgerEntry` đơn lẻ (mỗi entry đều dưới ngưỡng, hợp lệ) — chỉ thấy khi nhìn cộng dồn qua thời gian trên data warehouse. `analytics-service.AmlPatternScanJob` (batch, `analytics-service-phase2-design.md` §3.5) làm việc đó và publish `analytics.structuring_pattern_detected`.

**Vì sao bank-service là consumer, không phải reputation-service:** phát hiện structuring **mà không báo cáo cơ quan** tự nó là lỗi tuân thủ trong khung AML. Nghĩa vụ báo cáo giao dịch khả nghi (STR) thuộc **tổ chức tài chính giữ tiền** (Luật PCRT 2022 Điều 4, Điều 26 — báo cáo giao dịch đáng ngờ), cùng chủ thể đã lo báo cáo giá trị lớn ở §3.4. `reputation-service` chỉ xử lý *nội bộ* (set cặp `ELEVATED_RISK`, chặn giao dịch tương lai — `reputation-service-phase2-design.md` §8); bank-service lo nghĩa vụ *ra ngoài*. Cùng 1 event, 2 consumer, 2 vai độc lập — không phụ thuộc nhau.

**Cơ chế:** consume `analytics.structuring_pattern_detected` (payload `{buyerId, sellerId, contractIds[], nearThresholdCount, windowStart, windowEnd, detectedAt}`) → tạo `SuspiciousTransactionReport` (bản ghi nội bộ, append-only, cùng tinh thần `ledger_entry`) + publish `bank.suspicious_report_created {eventId, buyerId, sellerId, windowEnd, reportHash, occurredAt}` cho audit-service nối chain (`source_type: STRUCTURING_REPORT`; transport chốt 17/07/2026 — hash-chain §2.4, bank không INSERT thẳng `audit_record`) làm bằng chứng due diligence. **Không hold** gì ở đây (batch chạy hồi cứu, các giao dịch trong `contractIds[]` đã settle — không có gì để hold; việc chặn giao dịch *tương lai* của cặp là của `ELEVATED_RISK` bên reputation). Idempotent theo `(buyerId, sellerId, windowEnd)` — batch chạy lại cùng cửa sổ không đẻ báo cáo trùng.

**Scope đồ án (honest):** platform mock 1 pooled account nội địa, chưa tích hợp cổng báo cáo thật của Cục PCRT — `SuspiciousTransactionReport` là bản ghi sẵn sàng export (đúng field STR cần), nếu sau này tích hợp ngân hàng/cơ quan thật chỉ cần thêm adapter, không đổi business logic. Cùng tinh thần "mock legal custody" đã dùng cho toàn bộ bank-service.

### 3.5 Emergency Lock — Zero-Trust Kill Switch cho External Verifier (mới, 08/07/2026, đã review + đóng 13/07/2026)

**Bối cảnh — lỗ hổng đã ghi nhận ở `hash-chain-phase2-design.md` §6:** toàn bộ 3 lớp bảo vệ hash chain đứng trên giả định trusted-operator, và cơ chế phát hiện tampering (`VerifyChainJob`) chạy **bên trong** platform — nếu chính Admin sửa data DB rồi vô hiệu hoá/lờ job đó đi, không có gì chặn được. Kill switch này thu hẹp đúng lỗ hổng đó: cho **bên xác minh ngoài** (External Verifier) một đường tự query hash để đối soát độc lập, và một đường tự đóng băng toàn hệ thống khi phát hiện lệch — không phụ thuộc bất kỳ job nào chạy trong platform.

**Ai là External Verifier — KHÔNG cột cứng vào VICOFA:** đây là **tổ chức mua & vận hành platform** (Software Buyer — dùng đúng thuật ngữ đã có ở `hash-chain-phase2-design.md` §5.2), có thể là VICOFA, VRA, VINACAS, hoặc doanh nghiệp thu mua uy tín bất kỳ tuỳ deployment. Cơ chế chạy y hệt bất kể ai đóng vai — VICOFA chỉ là ví dụ minh hoạ thực tế nhất (đang có liên kết ngành), **không phải điều kiện bắt buộc** để cơ chế hoạt động. Mọi field/event/config dưới đây đặt tên generic (`verifierOrgId`, `EXTERNAL_VERIFIER_*`), không hardcode tên riêng tổ chức nào.

**Vì sao không dùng API key/JWT thường bảo vệ 2 endpoint này:** Admin có quyền chui DB hoặc đọc environment variable → lấy được mọi secret đối xứng (API key, JWT signing secret) lưu trong tầm với runtime. Vũ khí duy nhất trị được Admin nội bộ ở case này là **mật mã bất đối xứng** — External Verifier giữ private key trong hạ tầng của họ (platform không bao giờ biết), platform chỉ giữ **public key** để verify chữ ký. Admin xem được public key cũng vô dụng: public key chỉ kiểm tra được chữ ký, không tạo ra chữ ký.

#### 3.5.1 Ba đường, phân biệt rõ đọc vs thay-đổi-trạng-thái

| Đường | Method | Auth | Bản chất |
|---|---|---|---|
| Query hash đối soát | `GET /api/v1/security/audit-hash?contractId=...` | Nhẹ (`X-Api-Key` + rate limit) | **Chỉ đọc** — hash không phải bí mật, không cần chữ ký. **Sửa 17/07/2026: đường này do audit-service serve** (chủ data `audit_record`, hash-chain §4.4); liệt kê ở đây để giữ bức tranh "3 đường của External Verifier", bank-service chỉ implement 2 đường lock/unlock |
| Emergency lock | `POST /api/v1/security/emergency-lock` | **Chữ ký bất đối xứng** External Verifier | **Đổi trạng thái** (đóng băng tiền) — bắt buộc asymmetric |
| Emergency unlock | `POST /api/v1/security/emergency-unlock` | **Chữ ký bất đối xứng** External Verifier | **Đổi trạng thái** (mở băng) — mirror với lock, không có đường tắt cho Admin |

Query endpoint chỉ cần auth nhẹ vì đọc hash không thay đổi gì; 2 endpoint lock/unlock đổi trạng thái tiền nên bắt buộc asymmetric. **Không** gộp chung cơ chế cho cả 3 — đọc mà bắt asymmetric là thừa, đổi-trạng-thái mà chỉ API key là hở đúng lỗ Admin.

#### 3.5.2 Chống replay attack (lock & unlock)

Payload bắt buộc mang `timestamp` + `nonce`, và **cả hai nằm trong chuỗi được ký**, không để rời ngoài header:

```
signedPayload = { action: "LOCK" | "UNLOCK", reason, timestamp, nonce }
signature     = sign(SHA256(canonical_json(signedPayload)), verifierPrivateKey)   // phía External Verifier
```

Platform verify: (1) chữ ký khớp public key đang đăng ký; (2) `timestamp` trong cửa sổ cho phép (VD ±300s) — chặn gói tin cũ bị bắt lại bắn sau; (3) `nonce` chưa từng thấy (lưu bảng `used_nonce`, TTL theo cửa sổ timestamp) — chặn bắn lại y hệt trong cửa sổ. Ký gộp `timestamp`+`nonce` vào chính chuỗi ký (không để header rời) để verify 1 lần là xong, không phải verify chữ ký rồi lại check riêng field khác có bị sửa không — attacker MITM bắt được request hợp lệ vẫn không tạo được chữ ký mới cho `nonce` mới vì không có private key.

#### 3.5.3 Gate — chốt chặn duy nhất, tận dụng "escrow-service là actor duy nhất"

Vì escrow-service là **actor duy nhất** gọi bank-service (§3), không cần sửa contract-service/escrow-service để "đóng băng toàn hệ thống" — chỉ cần **1 chốt chặn ngay đầu bank-service**: trước khi xử lý bất kỳ `bank.lock_requested`/`release_requested`/`seize_requested`/`refund_requested` nào, check `SELECT 1 FROM system_lock WHERE status='ACTIVE'`. Có row ACTIVE → **không ghi ledger**, publish `bank.*_failed` tương ứng (đúng pattern request/confirmation §3, không cần state mới). escrow-service/contract-service không cần biết gì về freeze — chúng chỉ thấy `bank.*_failed` và tự xử theo nhánh fail sẵn có.

#### 3.5.4 Tách freeze (kỹ thuật) khỏi notify (con người) — giữ nguyên tinh thần hash-chain §5.2

- **Freeze:** tự động, tức thì, toàn cục, không ai duyệt. Freeze toàn cục (không phải chỉ 1 contract) vì tamper 1 điểm trong chain nghĩa là không còn tin được record nào khác cho tới khi điều tra xong — không có cơ sở khoanh vùng lúc mới phát hiện.
- **Notify tự động:** chỉ 2 nơi — Admin (điều tra kỹ thuật) + External Verifier (xác nhận đã nhận lệnh). **KHÔNG** tự động email buyer/seller thật của từng hợp đồng — giữ đúng quyết định `hash-chain-phase2-design.md` §5.2: chỉ báo buyer/seller sau khi Admin khoanh vùng đúng `contract_id` bị ảnh hưởng, tránh hoảng loạn oan cho hàng loạt hợp đồng không liên quan.
- **Cơ chế:** sau khi lock/unlock commit, bank-service publish `notification.security_lock_changed_requested` mang Admin + External Verifier contacts, action, reason, timestamp và sourceEventId. Không gọi mail provider trực tiếp. **Bổ sung (17/07/2026):** song song, publish domain event `bank.security_lock_changed {eventId, action: LOCKED|UNLOCKED, verifierOrgId, reason, signedPayload, occurredAt}` — audit-service consume để nối chain (§3.5.7, source_type theo action; hash-chain §2.4). Notification command và domain event tách vai như mọi chỗ khác.

#### 3.5.5 Unlock — mirror lock, không ngoại lệ cho Admin

Admin **không có** API/quyền nào set `system_lock.status='RELEASED'` trực tiếp. Muốn mở băng bắt buộc có request unlock **ký bởi External Verifier** (cùng cơ chế §3.5.2). Quy trình: Admin điều tra xong → báo External Verifier (ngoài hệ thống) → họ xem xét, đồng ý thì tự ký request unlock. Platform không tự động unlock trong **bất kỳ** trường hợp nào, kể cả false-positive đã chứng minh — vẫn cần chữ ký thật. Một khi có 1 ngoại lệ cho Admin (dù lý do chính đáng), cơ chế mất hết ý nghĩa vì Admin chính là bên đang phòng.

#### 3.5.6 Public key của External Verifier — root-of-trust không nằm trong tầm Admin runtime

Đây là điểm quyết định cơ chế đứng vững hay sụp. Public key **không** được lưu chỗ Admin sửa được lúc chạy (DB config table / env var) — nếu lưu đó, Admin swap public key bằng key của chính họ → tự ký giả mạo External Verifier, hoặc âm thầm đổi key để mọi cảnh báo thật bị verify fail và bị bỏ qua. Ba lớp bảo vệ root-of-trust:

1. **Genesis key baked deploy-time:** public key ban đầu nằm trong config đi theo build artifact / secret do bên vận hành hạ tầng quản lý — **không phải** "Admin" (vai trò nghiệp vụ có quyền chui DB/env runtime). Đây là 2 vai trò khác nhau; Admin runtime không có quyền redeploy hay đổi secret hạ tầng.
2. **Mọi lần rotation là 1 event anchor vào hash chain:** đăng ký/đổi public key = `source_type = 'EXTERNAL_VERIFIER_KEY_REGISTERED'` mới trong `audit_record` (`hash-chain-phase2-design.md` §2, đang bổ sung song song), **và** gửi email fingerprint key thẳng vào hộp thư External Verifier lúc đăng ký — ngoài tầm platform y hệt email anchor lúc `sign()`. Admin lén đổi giá trị config → lệch với record đã anchor trong chain (`VerifyChainJob` bắt được) và lệch với fingerprint External Verifier tự giữ.
   Email fingerprint đi qua `notification.verifier_key_anchor_requested` do bank-service publish sau khi rotation hợp lệ được ghi nhận. Ghi chain đi qua domain event `bank.verifier_key_registered {eventId, publicKeyFingerprint, rotationSignedByOldKey?, occurredAt}` (**mới 17/07/2026** — trước đây record `EXTERNAL_VERIFIER_KEY_REGISTERED` chưa có transport; audit-service là consumer, hash-chain §2.4).
3. **Rotation hợp lệ phải ký bởi key CŨ:** muốn đổi sang public key mới, cần message ký bởi private key **đang có hiệu lực** ("tôi xác nhận đổi sang public key = XYZ"). Admin không có private key nào của External Verifier → không tạo được rotation hợp lệ, chỉ đổi được giá trị DB thô và giá trị đó sẽ lệch chain/email như lớp 2.

#### 3.5.7 Lock & unlock cũng vào hash chain

Cả 2 hành động vào `audit_record` như `source_type` mới (`SECURITY_LOCK_TRIGGERED` / `SECURITY_UNLOCK_TRIGGERED`) — đúng tiêu chí Event Catalog (`hash-chain-phase2-design.md` §2: "quyết định có thể bị tranh chấp làm bằng chứng sau này"). Kill switch tự nó cũng phải tamper-evident, không chỉ tiền mới cần.

---

## 4. Idempotency

**Vấn đề — `services.md` tự ghi đây là pain point CHÍNH của bank-service, không phải phụ:** RabbitMQ at-least-once — bank-service có thể nhận cùng 1 `bank.lock_requested` 2 lần (retry, consumer restart giữa chừng). Không check → trừ/khoá tiền 2 lần cho cùng 1 giao dịch.

**Chốt (03/07/2026) — idempotency key = `sourceEventId`, không phải compound business key.** Lý do không dùng `contractId + milestoneId + entryType`: 1 `(contractId, milestoneId)` có thể trải qua nhiều `entryType` khác nhau theo thời gian (LOCK rồi sau đó RELEASE) — compound key đó không unique per message, chỉ unique per "loại hành động", không phân biệt được lần gọi thứ mấy. `sourceEventId` là ID của chính outbox message escrow-service gửi — mỗi message có 1 ID duy nhất theo đúng Outbox Pattern đã dùng ở contract-service — unique tuyệt đối, đúng bản chất "record request này đã xử lý chưa", không phải "loại hành động này đã xảy ra chưa".

**Xử lý duplicate:** nhận `bank.lock_requested` với `sourceEventId` đã tồn tại trong `ledger_entry` → **không insert lại**, nhưng **vẫn re-publish confirmation** tương ứng (không silent-drop) — vì lý do consumer gửi lại có thể là do confirmation lần trước bị mất trên đường về, không chỉ vì request bị gửi lại. Silent-drop sẽ khiến escrow-service treo mãi chờ confirmation không bao giờ tới.

---

## 5. Quan hệ với `EscrowAccount`/`EscrowMilestone` (escrow-service) — tránh dual-write

**Chốt (03/07/2026):** `EscrowAccount`/`EscrowMilestone` (đã có ở `milestone-escrow-phase2-design.md` §2.3) **không tự lưu lại số tiền thật** phải đồng bộ tay với bank-service. Phân tách rõ:

- **Số tiền "đã thoả thuận"** (bao nhiêu cần khoá) — đã nằm sẵn, immutable, snapshot lúc `sign()` trong `ContractTerms.buyerDepositRate` và `MilestoneTerm.batchAmount` (`milestone-escrow-phase2-design.md` §2.1). Không cần lưu lại lần 2 ở đâu khác.
- **Số tiền "thật đã di chuyển"** — sự thật duy nhất nằm ở `ledger_entry` bên bank-service.
- `EscrowAccount`/`EscrowMilestone` chỉ giữ **state** (`LOCKED`/`RELEASED`/`PENALIZED`...), không giữ thêm 1 con số tiền riêng nào phải giữ đồng bộ tay với bank-service — nếu giữ, đó chính là dual-write problem, chỉ là biến thể mới của lỗi đã học.

---

## 5b. Ledger ↔ Audit Chain Reconciliation + Statement Export (mới, 18/07/2026)

### 5b.1 `LedgerAuditReconciliationJob` — đối chiếu SỐ TIỀN, lấp lớp check còn thiếu

3 lớp check hiện có không lớp nào trả lời "tiền đã đi đúng như quyết toán đã anchor chưa": bank reconciliation soát ledger nội bộ; chain verify (weekly + watchdog §3.5) chỉ chứng minh **record không bị sửa**, không chứng minh **ledger khớp record**; escrow projection luôn theo ledger. Nếu bug/tamper làm release lệch số so với `milestone.settled` đã anchor → không ai bắt được tới khi có tranh chấp.

- Job `@Scheduled` (hằng ngày, sau OTS window): với mỗi milestone `SETTLED` trong kỳ, so `sum(ledger entries RELEASE/SEIZE theo milestoneId)` vs `settledAmount`/`seizedAmount` trong `audit_record.content` (content minimal hoá vẫn giữ số liệu cần verify — governance §6, nên đủ dữ liệu để so).
- Đọc audit record qua internal API read-only mới của audit-service (hash-chain §4.5): `GET /internal/v1/audit/records?contractId&sourceType&from&to` — service-to-service secret, **không route Gateway**.
- **Mismatch → alert, KHÔNG auto-fix:** publish `notification.reconciliation_mismatch_requested` (Admin only) + metric. Đường sửa duy nhất là reversal entry có attribution (§nguyên tắc ledger) — job không có quyền ghi.
- Job fail/audit-service unavailable → alert vận hành, không block dòng tiền (đây là lớp phát hiện, không phải gate).

### 5b.2 Statement export cho Buyer/Seller — bank vẫn không lộ ra ngoài

Buyer/Seller tải bảng kê bút toán liên quan tới mình (cọc khoá, release/seize từng milestone, hoàn cọc) dạng CSV/PDF làm chứng từ cho kế toán của họ — platform **không** làm kế toán hộ, **không** xuất hoá đơn (Seller tự xuất theo NĐ123).

- Mặt tiền là **escrow-service**: `GET /api/v1/escrows/statements?from&to` (nằm dưới route `/api/v1/escrows/**` sẵn có, ownership check theo participant) → Feign nội bộ `GET /internal/v1/bank/ledger?participantId&from&to` (read-only, service secret, không route Gateway — bổ sung vào nhóm internal APIs sẵn có) → render file. Bank-service giữ nguyên không có external route (gateway §3.5).
- Read-only, số liệu luôn theo ledger → không dual-write, không state/event mới.

## 6. Database

```sql
CREATE TABLE ledger_entry (
    entry_id          CHAR(36) PRIMARY KEY,
    source_event_id    CHAR(36) NOT NULL UNIQUE,   -- idempotency key, xem §4
    contract_id        CHAR(36) NOT NULL,
    milestone_id       CHAR(36) NULL,               -- NULL = cọc cấp Contract (buyer hoặc seller, phân biệt qua entry_type — sửa 08/07/2026, xem §2), có giá trị = batchAmount của milestone đó
    user_id            CHAR(36) NOT NULL,
    entry_type         VARCHAR(20) NOT NULL,    -- LOCK_BUYER_DEPOSIT | LOCK_SELLER_DEPOSIT | LOCK_MILESTONE | RELEASE_TO_SELLER | SEIZE_PENALTY | REFUND_TO_BUYER
    amount             DECIMAL(15,2) NOT NULL,
    created_at         TIMESTAMP NOT NULL DEFAULT now()
);
CREATE INDEX idx_ledger_entry_contract ON ledger_entry(contract_id, milestone_id);
CREATE INDEX idx_ledger_entry_user ON ledger_entry(user_id);
-- Không có bảng "account" riêng — số dư luôn derive từ SUM(amount) lọc theo contract_id/milestone_id/user_id/entry_type.
```

**Kill switch — `system_lock` + `used_nonce` (§3.5):**

```sql
CREATE TABLE system_lock (
    lock_id         CHAR(36) PRIMARY KEY,
    status          VARCHAR(20) NOT NULL,    -- 'ACTIVE' | 'RELEASED'
    verifier_org_id CHAR(36) NOT NULL,           -- External Verifier (Software Buyer) đã trigger — generic, không hardcode tên tổ chức
    reason          TEXT,
    triggered_at    TIMESTAMP NOT NULL,
    released_at     TIMESTAMP NULL
);
-- Gate check §3.5.3: SELECT 1 FROM system_lock WHERE status='ACTIVE' trước mọi bank.*_requested.

CREATE TABLE used_nonce (
    nonce       VARCHAR(64) PRIMARY KEY,      -- chống replay §3.5.2
    seen_at     TIMESTAMP NOT NULL DEFAULT now()
);
-- Dọn định kỳ nonce quá cửa sổ timestamp cho phép (±300s) — không cần giữ mãi.
```

**`wallet_snapshot` — known scaling path, KHÔNG build trong Phase 2 (ghi nhận có chủ đích):**

Ở quy mô đồ án (vài trăm hợp đồng × vài milestone → vài nghìn `ledger_entry` là max thực tế), `SUM(amount)` với 2 index sẵn có chạy dưới 50ms thoải mái — không cần snapshot. Đây là scaling path đã nghĩ tới, ghi lại để defend "đã tính tới scale" khi hội đồng hỏi, cùng cách `hash-chain-phase2-design.md` §5.1 xử lý chi phí O(n) của `VerifyChainJob`:

- **Vấn đề nếu chain phình:** derive số dư qua `SUM()` là O(n) theo số entry của contract/user — với hàng triệu giao dịch (kịch bản productize dài hạn, không phải scope hiện tại) sẽ chậm dần.
- **Hướng fix — Snapshotting:** chốt sổ số dư định kỳ vào `wallet_snapshot(scope_key, total, cutoff_entry_id, snapshotted_at)`. Balance query = `snapshot.total + SUM(amount WHERE entry_id > snapshot.cutoff_entry_id)` — chỉ cộng phần mới sau snapshot.
- **Ràng buộc để không thành dual-write (§5):** snapshot phải là **derived, single-writer, idempotent** — đúng 1 job tự tính lại từ `ledger_entry` (không service nào khác ghi tay vào), cutoff dùng `cutoff_entry_id` (không dùng timestamp — timestamp có race ở biên). Snapshot chỉ là bản tính sẵn tự vá lại được từ `ledger_entry` bất cứ lúc nào, không phải nguồn số dư thứ hai phải đồng bộ tay.
- **Không code trong Phase 2** — nêu hướng + ràng buộc là đủ; build 1 job không ai stress-test nổi ở quy mô triệu-dòng chỉ tạo rủi ro "sao biết cần" mà không chứng minh được.

---

## 7. Known Limitations / Out of Scope (có chủ đích)

- **Không tích hợp API ngân hàng thật** — không ngân hàng nào ký hợp đồng API cho 1 đồ án tốt nghiệp. Mock xuyên suốt Phase 2, interface (event contract §3) thiết kế sạch để nếu sau này có ai tích hợp thật, chỉ cần thay implementation bên trong bank-service, escrow-service không đổi 1 dòng.
- **Vai trò arbitrator (docx §3.3) bị superseded** — ghi vào `decisions.md` `[2026-07-03]`, không xoá docx gốc: mock bank không đủ điều kiện độc lập/trách nhiệm pháp lý cần có cho vai arbitrator, INSPECTOR licensed org thoả điều kiện đó trong giới hạn Phase 2.
- **Roadmap docx §4.2 "Bank Integration — tích hợp escrow thật"** — định vị lại trong `decisions.md` cùng entry `[2026-07-03]`, không xoá: mục tiêu Phase 2 là thiết kế sẵn sàng tích hợp, không phải tích hợp thật.
- **Multi-bank (Agribank vs BIDV)** — không cần model, mock giả định 1 pooled account duy nhất.
- **Accounting đầy đủ / hoá đơn điện tử (18/07/2026)** — ngoài scope có chủ đích: giao dịch mua bán là giữa Buyer–Seller, hoá đơn do Seller xuất theo NĐ123; platform chỉ cung cấp chứng từ hỗ trợ (statement export §5b.2) + ledger 10 năm. Kế toán của chính platform chưa có gì để hạch toán vì chưa có fee model.
- **Kill switch phụ thuộc External Verifier tồn tại & giữ private key an toàn (§3.5)** — cơ chế chỉ chạy khi deployment có 1 tổ chức vận hành (Software Buyer) đóng vai verifier và giữ private key trong hạ tầng của họ. Không cột cứng VICOFA — bất kỳ tổ chức mua platform nào cũng đóng vai này. Nếu External Verifier để lộ private key hoặc chính họ thông đồng với Admin, cơ chế không còn tác dụng — đây là giới hạn cố hữu của mô hình trusted-operator (`hash-chain-phase2-design.md` §6), không phải lỗi thực thi. Genesis public key baked deploy-time nằm ngoài tầm Admin runtime là điều kiện tiên quyết để cơ chế đứng vững (§3.5.6).

---

## 8. Status — Bank-service Design

**Chốt (03/07/2026):** bank-service = mock legal custody duy nhất, không phải arbitrator. Mô hình FBO/Omnibus + `LedgerEntry` append-only thay cho account-per-contract — số dư luôn derive, không lưu sẵn. escrow-service là actor duy nhất gọi bank-service, đợi confirmation event mới đổi state, không fire-and-forget. Idempotency key = `sourceEventId` (Outbox message ID), không phải compound business key. `EscrowAccount`/`EscrowMilestone` không lưu số tiền riêng — tránh dual-write, số tiền thật là single source of truth ở `ledger_entry`.

**Chốt bổ sung (04/07/2026):** ledger mechanics cho 2 luồng trước đây chưa map rõ:
- **Delta 1/2 pro-rata** (§3.1) — payload `milestone.settled` mang `lockedAmount`/`actualAmount`, escrow-service tự tính diff, bắn cặp `RELEASE_TO_SELLER` + `REFUND_TO_BUYER` (chỉ khi `diff > 0`) cùng `milestoneId`.
- **`buyerDepositRate`** (§3.2) — 2 event cấp Contract mới (`contract.settled`, `contract.cancelled`, xem `milestone-escrow-phase2-design.md` §6.3) map thẳng vào `REFUND_TO_BUYER`/`SEIZE_PENALTY` với `milestoneId = NULL`, không cần `entryType` mới.

**Chốt bổ sung (10/07/2026) — consume `analytics.structuring_pattern_detected`, báo cáo giao dịch khả nghi (§3.4b):** bank-service thêm 1 consumer mới cho event batch AML từ analytics-service, tạo `SuspiciousTransactionReport` (append-only) + hash audit (`source_type: STRUCTURING_REPORT`). Đây là nghĩa vụ báo cáo giao dịch **đáng ngờ** (STR, Luật PCRT 2022 Điều 4/Điều 26), tách khỏi báo cáo giá trị lớn ở §3.4 — 2 nghĩa vụ khác nhau, cùng chủ thể pháp lý giữ tiền. Không hold (batch hồi cứu, giao dịch đã settle; chặn tương lai của cặp là việc `ELEVATED_RISK` bên reputation-service). Idempotent theo `(buyerId, sellerId, windowEnd)`. Scope đồ án: bản ghi sẵn sàng export đúng field STR, chưa nối cổng Cục PCRT thật — thêm adapter sau, không đổi logic.

**Ghi nhận quyết định (đã chốt trong doc này):** arbitrator superseded + roadmap reframe — nội dung đã nằm ở phần Status; đồng bộ sang `decisions.md` là housekeeping, không block SDS.

**Chốt bổ sung (08/07/2026) — rà soát end-to-end, 3 điểm áp dụng:**
- **B6** — comment schema sai từ khi `sellerDepositRate` optional ra đời (`milestoneId=NULL` không còn duy nhất nghĩa buyer deposit); tách `entryType` thành `LOCK_BUYER_DEPOSIT`/`LOCK_SELLER_DEPOSIT` để ledger tự giải thích được, không cần tra chéo contract-service (§2, §6).
- **A3** — thêm mapping event→ledger cho Provisional Settlement Level 2 (3 event mới từ `milestone-escrow-phase2-design.md` §3.2), trước đây bank-service hoàn toàn chưa đụng luồng này (§3.3).
- **A5** — thêm `bank.large_transaction_flagged` — đúng chủ thể pháp lý (bank ghi nhận + báo cáo, không hold), đúng ngưỡng 500 triệu (Điều 9 TT27/2025/TT-NHNN — giao dịch chuyển tiền điện tử, không phải 400 triệu của Điều 6 vốn áp dụng cho giao dịch tiền mặt) (§3.4).

**Chốt bổ sung (08/07/2026, chiều) — thêm Kill Switch cho External Verifier (đã review + đóng lại 13/07/2026):**
- **§3.5 — Emergency Lock / Zero-Trust Kill Switch cho External Verifier:** thu hẹp lỗ hổng `hash-chain-phase2-design.md` §6 (phát hiện tampering phụ thuộc job chạy trong platform). External Verifier (generic — Software Buyer bất kỳ, không cột cứng VICOFA) tự query hash đối soát độc lập + tự đóng băng toàn hệ thống qua chữ ký bất đối xứng khi phát hiện lệch. Gate = 1 chốt chặn `system_lock` ở đầu bank-service (tận dụng escrow-service là actor duy nhất). Freeze tự động toàn cục, tách khỏi notify buyer/seller (giữ hash-chain §5.2). Unlock mirror lock, không ngoại lệ Admin. Root-of-trust (public key) baked deploy-time ngoài tầm Admin runtime, rotation ký bởi key cũ + anchor vào chain.
- **`wallet_snapshot`** (§6) — known scaling path, nêu hướng + ràng buộc chống dual-write, KHÔNG build Phase 2.

**Đã đóng (13/07/2026):** (1) đồng bộ với `hash-chain-phase2-design.md` — 2 `source_type` mới (`EXTERNAL_VERIFIER_KEY_REGISTERED`, `SECURITY_LOCK_TRIGGERED`/`_UNLOCK_`) đã có bên đó (§2.3); (2) review §3.5 end-to-end xong — mechanics freeze/unlock, root-of-trust deploy-time, rotation ký bởi key cũ + anchor chain đều nhất quán với hash-chain §5.2/§6. **Sẵn sàng promote lên SDS/Architecture.**

**Cập nhật (17/07/2026):** (1) đường đọc `audit-hash` chuyển về audit-service — bank giữ đúng 2 đường lock/unlock (§3.5.1); (2) đặt tên transport vào chain: `bank.security_lock_changed`, `bank.verifier_key_registered`, `bank.suspicious_report_created` — audit-service consume, bank không INSERT thẳng `audit_record` (hash-chain §2.4).

Bank-service — **ĐÓNG SESSION HOÀN TOÀN**: ledger mechanics + Kill Switch (§3.5) đều đã đóng, sẵn sàng đưa vào Architecture/SDS/TechnicalSpec chính thức.

---

*Design session: 03/07/2026 · Cập nhật: 04/07/2026 (ledger mechanics cho Delta 1/2 + buyerDepositRate) · Cập nhật: 08/07/2026 sáng (rà soát end-to-end: tách entryType 2 loại cọc — B6; mapping Provisional Settlement Level 2 — A3; thêm bank.large_transaction_flagged đúng ngưỡng/chủ thể pháp lý — A5) · Cập nhật: 08/07/2026 chiều (thêm §3.5 Emergency Lock Kill Switch cho External Verifier + system_lock/used_nonce schema + wallet_snapshot scaling note) · Cập nhật: 13/07/2026 (review §3.5 end-to-end, đồng bộ source_type với hash-chain §2.3 — đóng session hoàn toàn) · Cập nhật 17/07/2026 (audit-hash về audit-service; 3 domain event transport vào chain) · Chưa code · **Sẵn sàng đưa vào Architecture/SDS/TechnicalSpec chính thức.***
