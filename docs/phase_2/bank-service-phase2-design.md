---
name: bank-service-phase2-design
description: "Bank-service — mock legal custody cho tiền, mô hình FBO/ledger thay vì account-per-contract. Nguồn: design session 03/07/2026."
status: DESIGNED — chưa code.
metadata:
  type: design
  phase: 2
  extends: "services.md § bank-service (8086)"
  related: "milestone-escrow-phase2-design.md §2.1, §2.3, §6 (nguồn amount cần lock/release/seize); AgriContract_02_GiaiPhap_MoHinh_v5.docx Tầng 5 (Escrow Holder — Ngân hàng), §3.3 (superseded, xem Known Limitations)"
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
| `milestoneId` | UUID, nullable | **NULL nếu là `buyerDepositRate` (khoá 1 lần lúc SIGNED, không thuộc milestone nào)**, có giá trị nếu là `batchAmount` của 1 milestone cụ thể — 2 loại khoá này đá vào 2 field khác nhau của `ContractTerms` (`milestone-escrow-phase2-design.md` §2.1), không được gộp chung nếu không sẽ không tách được milestone 3 đã settle với milestone 4 vẫn đang khoá |
| `userId` | UUID | Buyer hoặc seller, tuỳ `entryType` |
| `entryType` | Enum | `LOCK_DEPOSIT` \| `LOCK_MILESTONE` \| `RELEASE_TO_SELLER` \| `SEIZE_PENALTY` \| `REFUND_TO_BUYER` |
| `amount` | Money | |
| `createdAt` | Timestamp | |

`UNIQUE(sourceEventId)` — chặn duplicate xử lý cùng 1 event 2 lần (xem §4).

---

## 3. Interaction với escrow-service — Event Flow

**Chốt (03/07/2026):** escrow-service là **actor duy nhất** gọi bank-service — contract-service không bao giờ nói chuyện trực tiếp với bank-service, giữ nguyên ranh giới đã có ("contract-service quản lý delivery state, escrow-service quản lý tiền", `milestone-escrow-phase2-design.md` §2.3).

**Không fire-and-forget — đợi confirmation, cùng pattern `EscrowEventConsumer` Phase 1 đã dùng** (contract-service đợi `escrow.buyer_locked` mới `activate()`, không tự set trước):

```
escrow-service → bank-service:  bank.lock_requested        (contractId, milestoneId?, userId, amount, sourceEventId)
bank-service   → escrow-service: bank.lock_completed        (thành công → escrow-service set state LOCKED)
                                  bank.lock_failed           (thất bại → escrow-service giữ nguyên state, xử lý theo nhánh fail)
```

Cùng pattern cho `release` (khi milestone `SETTLED`), `seize` (khi `milestone.cancelled_with_penalty`), `refund` (khi force majeure approved, pro-rata theo số thực giao) — 4 cặp request/confirmation, không cặp nào set state trước khi có confirmation.

**Tại sao không được set trước:** nếu escrow-service publish `bank.lock_requested` rồi tự set `LOCKED` ngay, trong khi bank-service (mock) fail vì lý do gì đó — 2 bên lệch state, không ai biết cho tới khi có người kiểm tra thủ công. Đúng dạng dual-write problem đã học ở Phase 1 (`ApplicationEvent` non-durable), chỉ khác hướng: lần trước là sync-call-fail-without-rollback, lần này là async-fire-and-forget-không-đợi-confirm.

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

## 6. Database

```sql
CREATE TABLE ledger_entry (
    entry_id          UUID PRIMARY KEY,
    source_event_id    UUID NOT NULL UNIQUE,   -- idempotency key, xem §4
    contract_id        UUID NOT NULL,
    milestone_id       UUID NULL,               -- NULL = buyerDepositRate, có giá trị = batchAmount của milestone đó
    user_id            UUID NOT NULL,
    entry_type         VARCHAR(20) NOT NULL,    -- LOCK_DEPOSIT | LOCK_MILESTONE | RELEASE_TO_SELLER | SEIZE_PENALTY | REFUND_TO_BUYER
    amount             DECIMAL(15,2) NOT NULL,
    created_at         TIMESTAMP NOT NULL DEFAULT now()
);
CREATE INDEX idx_ledger_entry_contract ON ledger_entry(contract_id, milestone_id);
CREATE INDEX idx_ledger_entry_user ON ledger_entry(user_id);
-- Không có bảng "account" riêng — số dư luôn derive từ SUM(amount) lọc theo contract_id/milestone_id/user_id/entry_type.
```

---

## 7. Known Limitations / Out of Scope (có chủ đích)

- **Không tích hợp API ngân hàng thật** — không ngân hàng nào ký hợp đồng API cho 1 đồ án tốt nghiệp. Mock xuyên suốt Phase 2, interface (event contract §3) thiết kế sạch để nếu sau này có ai tích hợp thật, chỉ cần thay implementation bên trong bank-service, escrow-service không đổi 1 dòng.
- **Vai trò arbitrator (docx §3.3) bị superseded** — ghi vào `decisions.md` `[2026-07-03]`, không xoá docx gốc: mock bank không đủ điều kiện độc lập/trách nhiệm pháp lý cần có cho vai arbitrator, INSPECTOR licensed org thoả điều kiện đó trong giới hạn Phase 2.
- **Roadmap docx §4.2 "Bank Integration — tích hợp escrow thật"** — định vị lại trong `decisions.md` cùng entry `[2026-07-03]`, không xoá: mục tiêu Phase 2 là thiết kế sẵn sàng tích hợp, không phải tích hợp thật.
- **Multi-bank (Agribank vs BIDV)** — không cần model, mock giả định 1 pooled account duy nhất.

---

## 8. Status — Bank-service Design

**Chốt (03/07/2026):** bank-service = mock legal custody duy nhất, không phải arbitrator. Mô hình FBO/Omnibus + `LedgerEntry` append-only thay cho account-per-contract — số dư luôn derive, không lưu sẵn. escrow-service là actor duy nhất gọi bank-service, đợi confirmation event mới đổi state, không fire-and-forget. Idempotency key = `sourceEventId` (Outbox message ID), không phải compound business key. `EscrowAccount`/`EscrowMilestone` không lưu số tiền riêng — tránh dual-write, số tiền thật là single source of truth ở `ledger_entry`.

**Việc còn treo, không block thiết kế này:** viết 2 entry vào `decisions.md` (arbitrator superseded, roadmap reframe) — chưa làm.

Bank-service — **đóng session, sẵn sàng đưa vào Architecture/SDS/TechnicalSpec chính thức.**

---

*Design session: 03/07/2026 · Chưa code · Chưa đưa vào Architecture/SDS/TechnicalSpec chính thức.*
