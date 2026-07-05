---
name: milestone-escrow-phase2-design
description: "Milestone Escrow — full domain design cho Phase 2, thay thế two-phase lock escrow đang chạy ở Phase 1. Nguồn: design session 02/07/2026, cập nhật 04/07/2026, 06/07/2026 (event contract.signed; provisional settlement Level 2 — chưa đóng)."
status: DESIGNED — chưa code.
metadata:
  type: design
  phase: 2
  supersedes: "architecture.md § Escrow State Machine (two-phase lock BUYER_LOCKED → FULLY_LOCKED); bản 02/07/2026 của chính file này (Spring ApplicationEvent → Local Outbox, thêm buyer timeout ở BUYER_RECEIVED, thêm buyerDepositRate release paths + event contract.settled/contract.cancelled)"
---

## 1. Bối cảnh & Scope

Phase 1 hiện tại (đang chạy) dùng escrow đơn: buyer lock 100% `totalAmount`, seller lock deposit (`sellerDepositRate`), release toàn bộ một lần khi `confirmDelivery()`. Đây là **two-phase lock**, tự thêm ngoài spec gốc (xem `decisions.md` [2026-06-16]).

Phase 2 thay thế hoàn toàn bằng **Milestone Escrow** — lock/release theo từng đợt giao hàng (batch), không phải một lần. Lý do gốc (Doc2 mục 4.1): lock 100% giá trị hợp đồng ngay từ đầu gây áp lực vốn lưu động nghiêm trọng ở quy mô thương mại thật.

**Thay đổi lớn nhất so với Phase 1:** bỏ hẳn seller deposit. HTX không có tiền mặt ứng trước khi đang nợ phân bón — đây là viability constraint thật, không phải lựa chọn thiết kế (context từ Cường).

---

## 2. Domain Model Changes

### 2.1 `ContractTerms` (Value Object — cập nhật)

| Field | Loại | Ghi chú |
|---|---|---|
| `milestoneSchedule` | `List<MilestoneTerm>` (nested VO) | N batch tự do — buyer/seller tự thoả thuận số lượng batch, không cố định. Snapshot immutable lúc `sign()`, giống `agreedPrice`. |
| `toleranceRate` | BigDecimal | Ngưỡng lệch cân chấp nhận được cho Delta 2 (hao mòn vận chuyển). Mặc định 50/50 chia trách nhiệm nếu vượt ngưỡng — kế thừa nguyên bản Doc2 mục 4.2. |
| `shortfallPenaltyThreshold` | BigDecimal | Ngưỡng % thiếu hàng (Delta 1) mà dưới đó chỉ pro-rata, từ đó trở lên tính penalty. **Chốt: 5%.** (Giá trị mặc định — vẫn negotiate được per-contract giống các field khác trong `ContractTerms`.) |
| `buyerPenaltyRate` / `sellerPenaltyRate` | BigDecimal | Giữ nguyên từ Phase 1, tái dùng cho nhánh Delta 1 penalty. |
| `forceMajeureReportWindowDays` | Integer | Số ngày seller phải báo bất khả kháng kể từ lúc **biết sự kiện** (không neo theo ngày giao). **Chốt: 3 ngày.** |
| `buyerDepositRate` | BigDecimal | Cọc nhỏ của buyer — **Chốt: 5%** `totalAmount`. Lock **một lần duy nhất lúc `SIGNED`**, giữ xuyên suốt tới `SETTLED` cuối cùng. Vai trò "skin in the game", **không phải** T/T-style cọc cover rủi ro tài chính (đã bác bỏ ở `decisions.md` [2026-06-16]) — đó là việc của `batchAmount` lock riêng từng milestone (§6). 3 đường release/seize của field này — §6.3. |
| ~~`sellerDepositRate`~~ | — | **Bỏ hẳn.** Không còn seller deposit trong Milestone Escrow. |

`MilestoneTerm` (nested VO, phần tử của `milestoneSchedule`):

| Field | Loại | Ghi chú |
|---|---|---|
| `milestoneIndex` | Integer | Thứ tự batch (1, 2, ..., N) |
| `committedQuantity` | BigDecimal | Số lượng cam kết giao ở batch này |
| `batchAmount` | Money | `committedQuantity × agreedPrice` — phần tiền ứng với batch này |

### 2.2 `Milestone` (Aggregate riêng — cùng `contract-service`, khác `Contract`)

**Chốt (02/07/2026):** `Milestone` là aggregate root riêng — có `milestoneId` là identity, `MilestoneRepository` riêng, transaction riêng khi load/save. **Không** phải entity con của `Contract`. Lý do: milestone 3 confirm không cần atomic cùng lúc với milestone 1, 2, 4-8 — chúng độc lập nhau về nghiệp vụ, nhét chung 1 aggregate là nhầm foreign key (cùng thuộc 1 `contractId`) với ranh giới transaction thật cần thiết (nguyên tắc *Effective Aggregate Design* — Vernon).

Vẫn nằm chung `contract-service`, chung `contract_db` — không tách service, không thêm RabbitMQ exchange mới.

**Cập nhật (04/07/2026) — Local Outbox Pattern thay Spring `ApplicationEvent`:** bản gốc đồng bộ `Contract` với N `Milestone` con qua Spring `ApplicationEvent` nội bộ. Có 1 bug thật chưa lộ ra tới khi rà kỹ: nếu app crash *ngay sau khi* `Milestone.settle()` commit nhưng *trước khi* listener kịp chạy check `countByContractIdAndStatusNot(SETTLED)`, event bay mất — không có cơ chế retry nào cho `ApplicationEvent` mặc định của Spring. `Contract` kẹt vĩnh viễn ở `ACTIVE` dù thực tế milestone cuối đã xong, và không có exception nào bắn ra để biết mà sửa — bug âm thầm, chỉ lộ ra khi có người thắc mắc sao hợp đồng không đóng.

Thay bằng Local Outbox — cùng nguyên tắc đã dùng cho Outbox Poller RabbitMQ ở Phase 1, chỉ khác là **local, không qua RabbitMQ, không qua network hop**:

```sql
CREATE TABLE milestone_sync_outbox (
    outbox_id       UUID PRIMARY KEY,
    milestone_id    UUID NOT NULL,
    contract_id     UUID NOT NULL,
    processed       BOOLEAN NOT NULL DEFAULT false,
    retry_count     INT NOT NULL DEFAULT 0,
    last_attempt_at TIMESTAMP NULL,
    created_at      TIMESTAMP NOT NULL,
    processed_at    TIMESTAMP NULL
);
```

Cơ chế:

1. `Milestone.settle()` insert 1 row vào `milestone_sync_outbox` **trong cùng transaction** với chính nó — atomic, không thể commit milestone mà thiếu outbox row.
2. `@Scheduled` poller (tần suất giống Outbox Poller RabbitMQ đang chạy ở Phase 1) đọc các row `processed = false`, chạy `countByContractIdAndStatusNot(SETTLED) == 0` cho đúng `contractId` của row đó.
3. Nếu `true` → gọi `Contract.completeAllMilestones()` trong transaction riêng của `Contract` (method thay thế `settle()` cũ — guard fix ở §3.1).
4. Đánh dấu `processed = true` **dù kết quả check ở bước 2 là gì** — bản thân việc "đã kiểm tra cho đúng milestone này" là xong nhiệm vụ của row đó. Milestone thật sự cuối cùng sẽ có row riêng của nó tự bắt được điều kiện `== 0`.
5. Fail giữa chừng (exception, crash) → `retry_count += 1`, `last_attempt_at = now()`, row vẫn `processed = false`, poller lần chạy sau xử lý lại — at-least-once, không mất, không cần alert/dead-letter riêng cho scope đồ án.

`Milestone` là instance runtime của `MilestoneTerm`, track trạng thái thực tế của từng đợt giao hàng.

| Field | Loại | Ghi chú |
|---|---|---|
| `milestoneId` | UUID | |
| `contractId` | UUID (FK) | |
| `milestoneIndex` | Integer | Map với `MilestoneTerm.milestoneIndex` |
| `status` | Enum | Xem state machine §3.2 |
| `sellerDeclaredWeight` | BigDecimal, nullable | Seller tự cân trước khi lên xe |
| `sellerEvidenceFileId` | UUID, nullable | Reference `file-service` — ảnh cân hàng |
| `buyerReceivedWeight` | BigDecimal, nullable | Buyer cân lại khi hạ hàng |
| `buyerEvidenceFileId` | UUID, nullable | Reference `file-service` |
| `forceMajeureClaimId` | UUID, nullable | Reference nếu có claim bất khả kháng cho batch này |

### 2.3 `EscrowAccount` (cập nhật)

Bỏ `sellerDeposit`, `mockSellerBalance`. Thêm `EscrowMilestone` (con) — mirror `Milestone` bên contract-service, track riêng phần lock/release tiền của từng batch (giữ nguyên nguyên tắc contract-service quản lý delivery state, escrow-service quản lý tiền — không lẫn 2 domain).

**Lưu ý (04/07/2026, xem `bank-service-phase2-design.md` §5):** `EscrowAccount`/`EscrowMilestone` chỉ giữ **state** (`LOCKED`/`RELEASED`/`PENALIZED`...), không tự lưu lại 1 con số tiền riêng phải đồng bộ tay với bank-service — số tiền thật là single source of truth ở `ledger_entry` bên bank-service. Giữ thêm số tiền ở đây là dual-write, đúng lỗi đã học ở Phase 1.

---

## 3. State Machines

### 3.1 Contract-level (cập nhật)

```
OFFERED → NEGOTIATING → SIGNED → ACTIVE
                                    │
                    (N milestone chạy song song/tuần tự bên trong)
                                    │
                          Tất cả milestone SETTLED
                                    ↓
                                 SETTLED
```

`ACTIVE` giờ không chuyển thẳng `DELIVERED` như Phase 1 — nó ở `ACTIVE` xuyên suốt cho tới khi milestone cuối cùng settle. `cancel()`/`dispute()` ở cấp Contract cần xem xét lại dưới model nhiều milestone — chốt đầy đủ ở §6: `dispute()` bỏ hẳn (thay bằng milestone-level dispute §3.2/§5), `cancel()` giữ lại nhưng đổi nghĩa thành pro-rata trên phần milestone chưa `SETTLED`, không phải huỷ toàn bộ hợp đồng.

**Diễn giải bằng lời:** khác với Phase 1 (nơi cả hợp đồng chỉ có một điểm giao hàng duy nhất, `confirmDelivery()` một lần là xong), ở Milestone Escrow, `ACTIVE` là một trạng thái kéo dài — bên trong nó có N milestone chạy, mỗi milestone tự đi qua vòng đời riêng (chi tiết ở §3.2). Contract chỉ chuyển sang `SETTLED` khi milestone cuối cùng trong `milestoneSchedule` đã `SETTLED`. Nói cách khác, `SIGNED` vẫn giữ nguyên nghĩa (cả hai bên đã ký, điều khoản bất biến), nhưng "giao hàng xong" không còn là một sự kiện tức thời — nó là kết quả cộng dồn của nhiều sự kiện nhỏ.

**Cập nhật (04/07/2026) — dọn dead path, phát hiện qua đối chiếu chéo với `reputation-service-phase2-design.md` KI-1:** `Contract.settle()` hiện tại (code Phase 1) chỉ chạy được từ status `DELIVERED` — di sản two-phase lock. `completeAllMilestones()` (§2.2) phải gọi được `settle()` khi Contract đang `ACTIVE` (không bao giờ đạt `DELIVERED` nữa dưới Milestone Escrow) → **guard cần sửa**, đổi điều kiện cho phép transition từ `ACTIVE` thay vì `DELIVERED`. Kéo theo dead path cần dọn khi code: `confirmDelivery()`, `ContractDeliveredEvent`/`"contract.delivered"`, consumer tương ứng ở `escrow-service.ContractEventConsumer` và `notification-service.NotificationEventConsumer` — các thành phần này thuộc mô hình single-delivery-point Phase 1, không còn đường nào gọi tới dưới Milestone Escrow, cần xoá khi implement chứ không chỉ để đó thành code chết. `"contract.settled"` (`ContractSettledEvent`) **vẫn dùng nguyên được** một khi guard sửa xong — đây chính là event escrow-service sẽ consume ở §6.3/§7.2 để release `buyerDepositRate`.

### 3.2 Milestone-level (mới)

```
CREATED
   │
   ▼
IN_PROGRESS ──(seller claim bất khả kháng, trong forceMajeureReportWindowDays kể từ lúc biết sự kiện)──▶ FORCE_MAJEURE_PENDING_REVIEW
   │                                                                                                              │
   │ (seller cân trước khi lên xe + upload evidence)                                              Admin xét bằng chứng (file-service)
   ▼                                                                                                              │
SELLER_WEIGHED ──(seller phát hiện thiếu, claim bất khả kháng)──▶ FORCE_MAJEURE_PENDING_REVIEW                    │
   │                                                                        │                          ┌─────────┴─────────┐
   │ (vận chuyển, buyer cân lại + upload evidence)                         │                       APPROVED            REJECTED
   ▼                                                                        │                          │                    │
BUYER_RECEIVED                                                             │                  Delta 1 miễn penalty    Seller escalate?
   │                                                                        │                  → tiếp BUYER_RECEIVED         │
   ├─ CONFIRM_CLEAN ──────────────────────────────────────────▶ SETTLED    │                  với số đã giao         ┌──────┴──────┐
   │                                                                        │                          │             │             │
   ├─ timeout (buyerConfirmWindowDays, mặc định 2 ngày làm việc,           │                  Buyer bất mãn?      Không        Escalate
   │           buyer im lặng) ──────────────────────────────────▶ SETTLED  │                          │          escalate      Level 1.5
   │           (xử lý như CONFIRM_CLEAN)                                   │                          ▼             │       (final, cap)
   │                                                                        │                    Escalate Level 1.5   ▼             │
   └─ FLAG_ISSUE ──▶ AWAITING_SELLER_RESPONSE (sellerResponseWindowDays,   │                    (cap, không Level 2)Coi như       ▼
        │            mặc định 2 ngày làm việc)                            │                          │          shortfall  final
        ├─ timeout (seller im lặng) ──▶ SETTLED (theo số buyer báo)       │                          │          thường →
        │                                                                  │                          │       Delta 1 penalty
        └─ CONTESTED ──▶ DisputeRoutingService (3-tier) ──▶ SETTLED        └──────────────────────────┘
```

**Diễn giải bằng lời — đọc kỹ nếu sơ đồ mũi tên phía trên gây hiểu lầm:**

Mỗi milestone bắt đầu ở `CREATED` khi contract chuyển `ACTIVE`, rồi chuyển ngay sang `IN_PROGRESS` — trạng thái mặc định trong lúc seller chuẩn bị/gom hàng cho batch đó. Từ `IN_PROGRESS` có hai hướng đi, không phải một đường thẳng:

*Hướng bình thường (không có sự cố):* seller cân hàng trước khi lên xe, upload ảnh làm bằng chứng → milestone chuyển `SELLER_WEIGHED`, ghi nhận `sellerDeclaredWeight`. Hàng vận chuyển tới buyer. Buyer cân lại khi hạ hàng, upload ảnh bằng chứng riêng → milestone chuyển `BUYER_RECEIVED`, ghi nhận `buyerReceivedWeight`.

Tại `BUYER_RECEIVED`, buyer có đúng hai lựa chọn chủ động, cộng thêm 1 nhánh timeout. Nếu số lượng và chất lượng đúng như mong đợi, buyer bấm **CONFIRM_CLEAN** — hệ thống tự tính pro-rata theo Delta 2 (so `sellerDeclaredWeight` với `buyerReceivedWeight`) và release tiền ngay, milestone chuyển thẳng `SETTLED`, không ai can thiệp, không tốn phí gì. Nếu buyer thấy có vấn đề (thiếu cân hoặc sai chất lượng), buyer bấm **FLAG_ISSUE** — milestone chuyển `AWAITING_SELLER_RESPONSE`, seller có đúng `sellerResponseWindowDays` (mặc định 2 ngày làm việc) để phản hồi. Seller im lặng hết thời gian này = hệ thống coi như đồng ý với con số buyer báo, tự động `SETTLED` theo số đó. Seller không đồng ý, bấm **CONTESTED** — tranh chấp được đẩy qua `DisputeRoutingService`, dùng đúng cơ chế 3-tier đã có sẵn cho Tiered Dispute Resolution (Admin nội bộ / Vinacontrol-Quatest / SGS-Bureau Veritas tuỳ giá trị và độ phức tạp hàng hoá) — có kết quả rồi milestone mới `SETTLED` theo phán quyết đó.

**Cập nhật (04/07/2026) — timeout đối xứng cho nhánh buyer im lặng:** nếu buyer không bấm gì cả (không `CONFIRM_CLEAN`, không `FLAG_ISSUE`) trong vòng `buyerConfirmWindowDays` (mặc định 2 ngày làm việc, cùng độ dài với cửa sổ phản hồi của seller) kể từ lúc milestone vào `BUYER_RECEIVED`, hệ thống tự động xử lý như `CONFIRM_CLEAN` — release theo đúng `sellerDeclaredWeight`/`buyerReceivedWeight` đã ghi nhận, milestone `SETTLED`. Lý do default về `CONFIRM_CLEAN` chứ không phải treo vô thời hạn: seller đã hoàn thành nghĩa vụ giao hàng tới đúng lúc `BUYER_RECEIVED` (hàng đã tới tay buyer) — buyer im lặng không được phép biến thành công cụ giữ tiền seller vô thời hạn, đúng nguyên tắc "nghiêng về seller khi seller yếu thế hơn" đã dùng xuyên suốt (§6.2).

**Cập nhật (06/07/2026) — provisional settlement khi `CONTESTED` escalate Level 2:** khi `DisputeRoutingService` route milestone `CONTESTED` sang `LEVEL_2` (ngưỡng giá trị/độ phức tạp hàng hoá, §8), platform commission tổ chức Level 2 qua `InitiateLevel2Inspection` (`inspection-phase2-design.md` §3.4) và chờ report thật trong tối đa `level2BufferWindowDays`.

`level2BufferWindowDays` — **đặt tạm 7-14 ngày làm việc.** Chưa validate được với đơn vị Level 2 thật (đã liên hệ qua NHL nhưng chưa có phản hồi) — placeholder dựa trên benchmark turnaround PSI ngành chung (3-7 ngày cho hàng công nghiệp) cộng buffer cho lab test nông sản (độ ẩm, tỷ lệ hạt lỗi, cupping — dòng dịch vụ khác PSI thường, không có số riêng xác nhận). Cấu hình `application.yml`, chỉnh được khi có dữ liệu vận hành thật, không phải hằng số cứng vĩnh viễn — cùng nguyên tắc đã áp cho `inspectionAuthMaxAgeSeconds` (`inspection-phase2-design.md` §2).

Nếu hết `level2BufferWindowDays` mà report Level 2 chưa `CONFIRMED` (`inspection-phase2-design.md` §3.6): platform commission thêm 1 giám định Level 1.5 (Vinacontrol/Quatest) làm phán quyết tạm thời, dùng số đó settle **ngay** phần lớn milestone — release `(1 − level2SafetyBufferRate)` của `batchAmount` cho seller theo đúng số 1.5 chấm — nhưng giữ lại `level2SafetyBufferRate` (đặt tạm 10-15%) khoá nguyên trong escrow, không release, không ghi thành debt.

**Lý do dùng buffer khoá sẵn thay vì ghi nợ:** seller (HTX) không có tài sản đối ứng để đòi nếu số Level 2 thật (về sau) thấp hơn số 1.5 đã dùng settle tạm — không có `sellerDepositRate` để seize (đã bỏ hẳn từ Phase 2, §2.1). Giữ tiền sẵn trong escrow xử lý đúng rủi ro tại nguồn, không phụ thuộc khả năng seller trả nợ sau này — khác hẳn việc ghi một khoản "nợ" trừu tượng rồi hy vọng thu hồi được ở hợp đồng sau.

Khi report Level 2 thật về sau đó (dù đã qua `level2BufferWindowDays`): nếu có chênh lệch so với số 1.5 đã dùng settle tạm, chênh lệch trừ/bù thẳng từ buffer đang khoá; phần buffer còn dư (nếu có) release nốt cho seller.

**Điểm còn treo, chưa chốt (06/07/2026):** nếu report Level 2 không bao giờ về (org từ chối nhận job, mất liên lạc, phá sản...), cơ chế hiện chưa quyết định buffer nằm khoá vô thời hạn hay có 1 cutoff tuyệt đối để tự release theo số 1.5 — cần 1 session riêng, không suy đoán ở đây. Chưa xác định `level2SafetyBufferRate` chính xác (10-15% là khoảng đề xuất, chưa có dữ liệu variance thật giữa đánh giá Level 1.5 và Level 2 để tính số tối ưu).

*Hướng bất khả kháng (có sự cố):* ở **bất kỳ thời điểm nào** từ `IN_PROGRESS` cho tới trước khi milestone `SETTLED` — kể cả trước khi seller kịp cân hàng, hoặc ngay sau khi seller cân xong và phát hiện thiếu — seller có quyền claim bất khả kháng, miễn là claim được nộp trong vòng `forceMajeureReportWindowDays` **kể từ lúc seller biết về sự kiện** (không phải kể từ ngày giao hàng — xem lý do ở §5). Milestone chuyển sang `FORCE_MAJEURE_PENDING_REVIEW`, kèm bằng chứng nộp qua `file-service` (xác nhận thiên tai của chính quyền địa phương, ảnh, tin tức).

Admin xem xét bằng chứng trước tiên, vì đây là bước rẻ nhất trong hệ thống. Hai kết quả có thể xảy ra:

- **Admin APPROVE:** nếu batch này đã từng bị thiếu ở bước cân (tức có Delta 1 shortfall), phần thiếu đó được miễn penalty hoàn toàn — milestone quay lại tiếp tục flow bình thường (`BUYER_RECEIVED`) nhưng tính theo đúng số lượng thực tế đã giao, không phạt seller. Nếu buyer không đồng ý với quyết định APPROVE này (nghi ngờ seller không thực sự gặp thiên tai), buyer có quyền escalate lên **Level 1.5** — đây là điểm dừng cuối cùng cho loại tranh chấp này, **không** được đẩy lên Level 2, vì SGS/Bureau Veritas không có chuyên môn xác nhận thiên tai.
- **Admin REJECT:** seller có quyền escalate lên Level 1.5 để phản đối quyết định từ chối — đối xứng với quyền buyer contest APPROVE, đã chốt ở §5. Nếu sau Level 1.5 vẫn bị từ chối, claim coi như không hợp lệ — batch quay lại xử lý như một trường hợp thiếu hàng thông thường, rơi vào nhánh Delta 1 penalty ở bảng mục 4.

Điểm mấu chốt cần nhớ: sơ đồ mũi tên ở trên vẽ `FORCE_MAJEURE_PENDING_REVIEW` như một nhánh rẽ tại hai điểm cụ thể (`IN_PROGRESS` và `SELLER_WEIGHED`) chỉ để dễ đọc — về bản chất, quyền claim bất khả kháng không gắn cứng vào một bước cụ thể, mà là một **sự kiện có thể chen ngang bất cứ lúc nào** trong vòng đời milestone, miễn còn trong cửa sổ thời gian cho phép.

---

## 4. Business Rules — Delta 1 vs Delta 2

Hai delta khác bản chất, **không được gộp chung**:

| | Delta 1 | Delta 2 |
|---|---|---|
| So sánh | `committedQuantity` (lúc ký) vs `sellerDeclaredWeight` (lúc cân trước khi lên xe) | `sellerDeclaredWeight` vs `buyerReceivedWeight` (lúc hạ hàng) |
| Bản chất | Seller tự kiểm soát được — xảy ra *trước khi* xe chạy | Ngoài kiểm soát cả hai bên — hao mòn *trong lúc* vận chuyển |
| Ai chịu | Seller (trừ khi có bất khả kháng) | Chia theo `toleranceRate` đã negotiate, mặc định 50/50 |
| Xử lý | 3 nhánh — xem bảng dưới | Luôn pro-rata tự động |

**Delta 1 — 3 nhánh:**

| Nhánh | Điều kiện | Kết quả |
|---|---|---|
| 1 | Thiếu trong `shortfallPenaltyThreshold` | Pro-rata bình thường, không penalty |
| 2 | Thiếu vượt threshold, **không** chứng minh được bất khả kháng | Áp `sellerPenaltyRate` — đúng bản chất bẻ kèo |
| 3 | Thiếu (bất kỳ mức nào), **chứng minh được** bất khả kháng (Admin/Level 1.5 approve) | Pro-rata theo số thực giao, không penalty |

---

## 5. Force Majeure Sub-flow

**Căn cứ pháp lý:** Điều 156 + Điều 351 BLDS 2015. Bất khả kháng phải hội đủ 3 điều kiện: khách quan, không thể lường trước, không thể khắc phục dù đã nỗ lực hết mức. Mất mùa/sâu bệnh thông thường **không** đạt ngưỡng này — chỉ thiên tai lớn (lũ, bão), dịch bệnh, hoặc lệnh cấm nhà nước mới đủ điều kiện. Hệ quả pháp lý: các bên **tự chịu thiệt hại của mình**, không bên nào được đòi bồi thường hay chia sẻ tổn thất — tức là buyer không "chịu thay" seller, mà là **không ai nợ ai**, hợp đồng giảm xuống đúng số lượng thực giao.

**Quy tắc thời điểm báo cáo:** neo theo **lúc seller biết về sự kiện**, không neo theo ngày giao hàng. Seller phải claim trong vòng `forceMajeureReportWindowDays` kể từ lúc biết — bất kể lúc đó còn cách ngày giao bao lâu. Thiên tai xảy ra sát ngày giao mà seller báo ngay vẫn hợp lệ; thiên tai xảy ra sớm mà seller im lặng tới sát deadline mới khai thì claim yếu/reject.

**Routing:** dùng lại `DisputeRoutingService` nhưng **cap ở Level 1.5** — không có Level 2 (SGS/Bureau Veritas không có nghiệp vụ xác nhận thiên tai, sai chuyên môn). Level 1.5 (Vinacontrol/Quatest/kiểm định tỉnh) gần với loại bằng chứng cần verify (xác nhận UBND xã/huyện về thiên tai).

**Bằng chứng bắt buộc** (qua `file-service`): xác nhận thiên tai của chính quyền địa phương, ảnh, tin tức — Admin xác minh trước khi công nhận, không tự động miễn chỉ vì seller khai.

**Quyền contest — đối xứng cả hai chiều:** buyer contest quyết định APPROVE của Admin (nghi ngờ seller không thực sự gặp thiên tai) → escalate Level 1.5. Seller contest quyết định REJECT của Admin (cho rằng Admin đánh giá sai mức độ nghiêm trọng) → escalate Level 1.5. Cả hai đều cap ở Level 1.5, không lên Level 2.

Lý do cần đối xứng, không chỉ cho buyer: `decisions.md` [2026-06-17] đã ghi nhận Admin không trung lập tuyệt đối trong Multi-tenant deployment model — Admin có thể là người của hiệp hội/doanh nghiệp buyer, gần phía buyer hơn về cấu trúc. Nếu chỉ buyer được contest còn seller thì REJECT là final, một Admin thiên vị (dù không cố ý) có thể liên tục bác claim thật của seller mà không ai kiểm tra lại — lặp lại đúng bất đối xứng quyền lực mà Doc1 mục 2.3 mô tả là vấn đề gốc.

**Consent trước:** cơ chế escalation cap ở Level 1.5 nằm trong Terms of Service lúc activate account (giống pattern Doc2 mục 5.2 Rủi ro 2) — không phải thứ buyer/seller phát hiện ra giữa chừng rồi bất mãn.

---

## 6. Contract-level Cancel — Seller & Buyer

**Chốt (02/07/2026):** Bỏ hẳn `dispute()` cấp Contract — chức năng đã được thay thế đầy đủ bởi milestone-level dispute (§3.2, §5). `cancel()` giữ lại nhưng đổi nghĩa hoàn toàn so với Phase 1.

**Pro-rata cancel (áp cho cả 2 bên):** cancel chỉ tác động các milestone **chưa `SETTLED`**. Milestone đã xong giữ nguyên, không truy thu lại. Penalty tính trên tổng `batchAmount` của các milestone còn lại, không phải `totalAmount` toàn hợp đồng.

### 6.1 Seller-initiated Cancel

**Vấn đề cốt lõi — không còn seller deposit để seize:** Phase 1 cũ, penalty tự động vì tiền đã khoá sẵn trong escrow. Milestone Escrow bỏ deposit (viability constraint cho HTX) — nên khi seller cancel phần còn lại, không có gì để escrow tự động trừ. Đây là cái giá thật của quyết định bỏ deposit, không phải lỗi thiết kế cancel.

**Cập nhật (04/07/2026) — `buyerDepositRate` tách hoàn toàn khỏi penalty debt của seller:** buyer không phải bên phá kèo trong case này — `buyerDepositRate` (khoá từ lúc `SIGNED`, §2.1) được refund về buyer ngay khi `cancel()` do seller khởi xướng được xác nhận, độc lập hoàn toàn với cơ chế penalty debt/lockout ở dưới (đó là hình phạt nhắm vào hành vi của seller, không liên quan gì tới tiền của buyer). Trigger + event cụ thể — §6.3.

**Cơ chế thay thế — không qua escrow, dựa vào Reputation (Doc2 mục 4.5) làm đòn bẩy enforce thật:**

1. **Penalty debt được ghi nhận** — `sellerPenaltyRate × giá trị milestone còn lại`, lưu vào audit trail bất biến, có giá trị làm bằng chứng bồi thường thiệt hại theo Luật TM 2005 Điều 302 nếu buyer muốn truy đòi qua VIAC/toà án. Platform không tự thu hộ được — đây chỉ là bằng chứng.
2. **Khoá account seller ngay lập tức** — chặn tạo listing/hợp đồng mới, không đợi kết quả toà (Doc1 mục 2.2: tố tụng 1–3 năm, chờ toà là tự mâu thuẫn với lý do sản phẩm tồn tại).
3. **Thời gian khoá = 2 trục hành vi, không tính giá trị hợp đồng** — giá trị đã được phản ánh riêng ở `penalty debt` (mục 1, tỷ lệ theo `sellerPenaltyRate × giá trị`); nhét giá trị vào cả `lockDurationDays` là tính 2 lần cho cùng 1 vi phạm qua 2 kênh khác nhau. Tách bạch: **penalty debt phản ánh thiệt hại tài chính, số ngày khoá phản ánh mức độ hành vi tái phạm** — hai câu hỏi khác nhau, không dùng chung 1 biến:

   ```
   lockDurationDays = baseDays × repeatOffenseMultiplier × trackRecordMultiplier
   ```
   - `repeatOffenseMultiplier` — theo **số lần từng cancel-có-penalty trước đó**, tuyến tính theo số lần vi phạm tuyệt đối (không phải tỷ lệ, nên không có vấn đề mẫu nhỏ).
   - `trackRecordMultiplier` — **gated theo ngưỡng mẫu tối thiểu**, không dùng % thô ngay từ đầu. Lý do: % với mẫu số nhỏ cực kỳ không ổn định — seller mới (0-1 hợp đồng) vi phạm 1 lần sẽ ra 0% clean, trong khi seller có 20 hợp đồng sạch vi phạm hợp đồng thứ 21 vẫn ra 95% clean, dù hành vi vi phạm giống hệt nhau (1 lần). Dưới ngưỡng mẫu tối thiểu → seller "chưa đủ dữ liệu", dùng multiplier trung tính, không bị đẩy xuống đáy chỉ vì mới tham gia. Từ ngưỡng trở lên → dùng % sạch thật để giảm nhẹ hoặc tăng nặng.
   - **Baseline cụ thể, chốt luôn:** `baseDays = 30`. Ngưỡng mẫu tối thiểu = 5 hợp đồng hoàn thành. `repeatOffenseMultiplier`: 1x (lần 1) / 2x (lần 2) / 3x (lần 3+). `trackRecordMultiplier` (chỉ áp dụng từ hợp đồng thứ 6 trở đi): 0.7x nếu ≥90% sạch, 1.0x nếu 70–90%, 1.3x nếu dưới 70%; dưới ngưỡng 5 hợp đồng → mặc định 1.0x.
   - Toàn bộ nằm trong `application.yml`, chỉnh được sau khi có dữ liệu thật — không cần đúng tuyệt đối ngay từ đầu.
4. **Mở khoá qua 1 trong 3 đường, đường nào tới trước:**
   - Buyer tự báo đã giải quyết xong (ngoài platform) → tự đóng.
   - Seller upload bằng chứng kết quả ràng buộc (bản án, phán quyết VIAC, thoả thuận hoà giải hai bên ký) qua `file-service` → Admin verify → đóng theo kết quả (seller thắng có thể phục hồi reputation, không chỉ mở khoá).
   - Timeout cố định (platform tự đặt, không phụ thuộc tốc độ toà) → tự mở khoá dù chưa có phán quyết nào.
5. **Penalty debt + lịch sử vi phạm không bao giờ bị xoá khỏi reputation**, dù account có mở khoá hay không — mở khoá chỉ là "cho giao dịch tiếp", không phải "xoá tiền án".

### 6.2 Buyer-initiated Cancel

**Bất đối xứng có chủ đích:** buyer (doanh nghiệp thu mua, thường có vốn) có deposit thật để mất — seller (HTX) thì không, vì lý do viability đã chốt ở §1. Buyer bị enforce bằng cả tiền thật lẫn reputation; seller chỉ enforce được bằng reputation vì không có tiền để giữ.

Buyer huỷ bất kỳ lúc nào sau `SIGNED`:

1. **Mất toàn bộ `buyerDepositRate` (5% `totalAmount`)** — chuyển cho seller, escrow tự động seize ngay, không cần chờ Admin. (Event cụ thể — §6.3.)
2. **Nếu đang có `batchAmount` của milestone hiện tại đang lock** — seize luôn theo `buyerPenaltyRate`, chuyển cho seller như bồi thường, y hệt logic `PENALIZED_BUYER` của Phase 1 nhưng chỉ scope đúng 1 batch.
3. **Khoá account + xấu track record** — áp đúng công thức `lockDurationDays` ở §6.1, dùng chung cho cả buyer và seller (đổi `sellerPenaltyRate` thành `buyerPenaltyRate` trong input tính penalty debt).

**Thời điểm lock `batchAmount`: chốt sớm** — buyer lock ngay khi milestone trước đó chuyển `SETTLED` (hoặc ngay khi contract `ACTIVE` cho milestone đầu tiên). Lý do chọn sớm thay vì muộn: nhất quán với hướng đã chọn xuyên suốt session — mọi lần có tension giữa bảo vệ buyer và bảo vệ seller, thiết kế đều nghiêng về seller khi seller là bên yếu thế hơn (bỏ seller deposit vì viability, force majeure bảo vệ seller khỏi bị buộc tội oan, `buyerDepositRate` bắt buộc buyer luôn có "cái để mất" từ `SIGNED`) — đúng luận điểm gốc Doc1 mục 2.3 về bất đối xứng quyền lực. Lock sớm cho seller đảm bảo tối đa trước khi họ bỏ công/vốn chuẩn bị hàng cho batch tiếp theo.

**Ownership giữa các service:** `contract-service` tính penalty + publish event khi milestone bị cancel-có-penalty (cả 2 chiều buyer/seller). `reputation-service` (service #8, Phase 2 — services.md) consume event này cùng lịch sử hợp đồng hoàn thành, tính `lockDurationDays` theo công thức trên, và là nguồn quyết định khoá/mở khoá. `user-service` enforce khoá thật (chặn tạo listing/contract) dựa trên quyết định từ `reputation-service`. Chi tiết state machine đầy đủ của `reputation-service` (aggregate từ nhiều nguồn, eventually consistent theo services.md) để dành cho design session riêng — ở đây chỉ chốt input/trigger nó cần nhận.

### 6.3 `buyerDepositRate` — 3 đường release/seize, event cấp Contract (mới, 04/07/2026)

**Vấn đề phát hiện khi rà lại luồng tiền:** `buyerDepositRate` là khoá **cấp Contract** (`milestoneId = NULL` ở `ledger_entry`, `bank-service-phase2-design.md` §2), không thuộc về milestone nào — nên không thể dùng `milestone.cancelled_with_penalty` (event cấp milestone, §7.1) làm trigger cho nó. Event Catalog bản 02/07/2026 chưa có event nào ở cấp Contract để xử lý riêng field này — lỗ hổng thật, không phải chi tiết vụn.

**Chốt (04/07/2026) — 3 đường, 2 event cấp Contract:**

| Trigger | Event | Kết quả cho `buyerDepositRate` |
|---|---|---|
| Hợp đồng hoàn tất bình thường — tất cả milestone `SETTLED` | `contract.settled` — đã tồn tại trong code Phase 1 (`ContractSettledEvent`), **không phải event mới**, chỉ thêm consumer mới. Cần guard fix ở §3.1 trước khi dùng lại được. | `REFUND_TO_BUYER` |
| Seller khởi xướng `cancel()` (§6.1) | `contract.cancelled` (initiatedBy=SELLER) — **event mới** | `REFUND_TO_BUYER` — độc lập với penalty debt |
| Buyer khởi xướng `cancel()` (§6.2) | `contract.cancelled` (initiatedBy=BUYER) — **event mới** | `SEIZE_PENALTY` (chuyển cho seller) |

`contract.cancelled` — publisher `contract-service`, bắn đúng 1 lần mỗi khi `Contract.cancel()` được gọi, bất kể cancel kéo theo bao nhiêu milestone bị `milestone.cancelled_with_penalty` riêng lẻ (đó là event khác cấp, không gộp — mỗi milestone còn lại vẫn tự bắn `milestone.cancelled_with_penalty` của nó cho phần `batchAmount`, còn `contract.cancelled` chỉ lo phần `buyerDepositRate`). Payload tối thiểu: `{contractId, initiatedBy: BUYER|SELLER}`. Consumer: `escrow-service` (xử lý `buyerDepositRate` theo bảng trên), `notification-service`. Chi tiết event catalog — §7.2.

**Điểm cần Cường review kỹ trước khi đưa vào SDS:** đây là phát hiện mới lúc viết lại tài liệu (04/07/2026), chưa qua vòng thảo luận riêng như các mục khác trong file này — logic khớp với những gì đã chốt ở §6.1/§6.2 (seller cancel → refund buyer, buyer cancel → seize), chỉ là lần đầu đặt tên/thiết kế event cụ thể cho nó.

---

## 7. Event Catalog

**Chốt (02/07/2026):** theo đúng convention `{aggregate}.{actor}_{past_tense_verb}` đã có tiền lệ ở `escrow.buyer_locked`.

### 7.1 Milestone-level

| Loại | Tên | Publisher | Consumer(s) |
|---|---|---|---|
| Local Outbox (nội bộ, không RabbitMQ — cơ chế ở §2.2) | `milestone_sync_outbox` row | `Milestone.settle()` | `@Scheduled` poller → `Contract.completeAllMilestones()` |
| RabbitMQ | `milestone.seller_weighed` | contract-service | file-service (evidence), notification-service |
| RabbitMQ | `milestone.buyer_confirmed` | contract-service | escrow-service (trigger release), notification-service |
| RabbitMQ | `milestone.flagged` | contract-service | notification-service |
| RabbitMQ | `milestone.force_majeure_claimed` | contract-service | notification-service (Admin) |
| RabbitMQ | `milestone.force_majeure_resolved` | contract-service | escrow-service, notification-service |
| RabbitMQ | `milestone.settled` | contract-service | escrow-service, notification-service, reputation-service |
| RabbitMQ | `milestone.cancelled_with_penalty` | contract-service | escrow-service (seize nếu có `batchAmount` lock), notification-service, reputation-service (tính `lockDurationDays`) |

**Cập nhật (04/07/2026) — payload `milestone.settled` mở rộng:** thêm `lockedAmount` (= `batchAmount` gốc đã khoá) và `actualAmount` (= số tiền thật sau pro-rata Delta 1/2, §4). `escrow-service` tự tính `diff = lockedAmount - actualAmount`, bắn 2 `LedgerEntry` cùng `milestoneId` nếu `diff > 0`: `RELEASE_TO_SELLER(actualAmount)` + `REFUND_TO_BUYER(diff)`. Không cần event/entryType mới ở bank-service — chỉ là payload trước đây chưa đủ chi tiết để escrow-service tự tính được.

**Cập nhật (06/07/2026) — 2 event mới cho provisional settlement Level 2 (§3.2):**

| Loại | Tên | Publisher | Consumer(s) |
|---|---|---|---|
| RabbitMQ | `milestone.level2_provisional_settled` | contract-service — bắn khi hết `level2BufferWindowDays` mà report Level 2 chưa `CONFIRMED`, đã commission Level 1.5 fallback và settle tạm. Payload: `{milestoneId, provisionalAmount, bufferAmount, level1_5ReportId}` | escrow-service (release `provisionalAmount` cho seller, giữ khoá `bufferAmount`), notification-service |
| RabbitMQ | `milestone.level2_buffer_reconciled` | contract-service — bắn khi report Level 2 thật về sau đó (`CONFIRMED`), so sánh với số 1.5 đã dùng settle tạm. Payload: `{milestoneId, bufferAmount, finalAdjustment}` | escrow-service (release phần buffer còn lại theo `finalAdjustment`), notification-service |

### 7.2 Contract-level (mới, 04/07/2026; bổ sung `contract.signed` + consumer analytics-service, 06/07/2026)

| Loại | Tên | Publisher | Consumer(s) |
|---|---|---|---|
| RabbitMQ | `contract.signed` | contract-service — **mới (06/07/2026, phát sinh từ session `analytics-service`)**. Bắn đúng 1 lần khi `Contract.transitionTo(SIGNED)` xảy ra — trigger cụ thể là bước 4 của `VerifyOtpAndSign` (`signature-phase2-design.md` §6, bước 5 nối tiếp cũng publish event này, tách biệt với việc push `signedContentHash` riêng cho audit-service). Payload: `{contractId, commodity, buyerId, sellerId, totalAmount, signedAt}`. `commodity` dùng chung enum `COFFEE / RICE / RUBBER / CASHEW` đã có tiền lệ ở `pricing-service-phase2-design.md` §2 — contract-service tự **map** từ `Product.category` (enum tầng rộng, tiếng Việt, VD "Gạo") của sản phẩm gắn với hợp đồng sang enum chung này ngay lúc publish, không đổi field gốc bên `product-service`. **[CẦN XÁC NHẬN: bảng mapping đầy đủ `Product.category` (tiếng Việt) → `commodity` enum (tiếng Anh) — ví dụ "Cà phê"→COFFEE, "Gạo"→RICE — cần Cường/team liệt kê đủ giá trị `category` hiện có trước khi code, tránh map thiếu case rơi vào `NULL`/exception lúc publish.]** | analytics-service (populate `dim_contract`, `analytics-service-phase2-design.md` §2.1/§3.1) |
| RabbitMQ | `contract.settled` | contract-service (`ContractSettledEvent`, đã có sẵn trong code Phase 1 — chỉ thêm consumer mới, cần guard fix §3.1) | escrow-service (`REFUND_TO_BUYER` cho `buyerDepositRate`, §6.3), reputation-service (input positive — đã dùng ở `reputation-service-phase2-design.md` §3), **analytics-service (mới, 06/07/2026 — `fact_contract_settlement`, `analytics-service-phase2-design.md` §3.3)** |
| RabbitMQ | `contract.cancelled` | contract-service — **mới**, xem §6.3 | escrow-service (seize/refund `buyerDepositRate` theo `initiatedBy`), notification-service, **analytics-service (mới, 06/07/2026 — `fact_contract_cancellation`, `analytics-service-phase2-design.md` §3.3)** |

---

## 8. Config: `application.yml` vs `ContractTerms`

| Setting | Nơi lưu | Lý do |
|---|---|---|
| Escalation cap của force majeure = Level 1.5 (không Level 2) | `application.yml` | Invariant kỹ thuật — sai chuyên môn nếu lên Level 2, không nên để buyer/seller tự thoả thuận khác mỗi hợp đồng |
| `forceMajeureReportWindowDays` | `ContractTerms` (per-contract) | Độ nhạy cảm thời gian khác nhau theo mặt hàng (cà phê khô lâu hơn rau quả tươi) |
| `shortfallPenaltyThreshold`, `toleranceRate`, `sellerPenaltyRate` | `ContractTerms` (per-contract) | Negotiate theo từng hợp đồng, giống pattern `penaltyRate` Phase 1 |
| Ngưỡng giá trị/loại hàng kích hoạt Level 1.5 vs Level 2 cho dispute thường (quantity/quality) | `application.yml` | Đã quyết từ Doc2 mục 4.3 — per-deployment config |
| `sellerResponseWindowDays` (mặc định 2 ngày làm việc) | `application.yml` | **Chính thức hoá (04/07/2026):** trước đây chỉ ghi "2 ngày làm việc" trong prose ở §3.2, chưa đặt tên config. Invariant kỹ thuật — không có lý do hợp đồng khác nhau cần cửa sổ phản hồi khác nhau. |
| `buyerConfirmWindowDays` (mặc định 2 ngày làm việc) | `application.yml` | **Mới (04/07/2026):** đối xứng với `sellerResponseWindowDays` — buyer im lặng ở `BUYER_RECEIVED` quá thời gian này → auto `CONFIRM_CLEAN` (§3.2). |
| `level2BufferWindowDays` (đặt tạm 7-14 ngày làm việc) | `application.yml` | **Mới (06/07/2026):** chưa validate với đơn vị Level 2 thật (đã liên hệ NHL, chưa có phản hồi) — placeholder theo benchmark PSI ngành, không phải số đo thực tế cho cà phê/gạo. Hết window mà chưa có report `CONFIRMED` → commission Level 1.5 fallback, settle tạm (§3.2). |
| `level2SafetyBufferRate` (đặt tạm 10-15%) | `application.yml` | **Mới (06/07/2026):** % `batchAmount` giữ khoá khi settle tạm theo Level 1.5, chờ report Level 2 thật đối chiếu (§3.2). Chưa có dữ liệu variance thật giữa Level 1.5 và Level 2 để tính số tối ưu — placeholder. |

---

## 9. Status — Milestone Escrow Design

**Chốt (02/07/2026):** `shortfallPenaltyThreshold` = 5%. `forceMajeureReportWindowDays` = 3 ngày. Seller có quyền contest REJECT, đối xứng với buyer, cùng cap Level 1.5. `Milestone` = aggregate riêng. `dispute()` cấp Contract bị bỏ. `cancel()` cấp Contract = pro-rata + penalty debt + lockout, áp cho cả seller lẫn buyer (§6). `buyerDepositRate` = 5% `totalAmount`, lock lúc `SIGNED`. Event catalog theo convention `escrow.buyer_locked` đã có. `lockDurationDays = baseDays(30) × repeatOffenseMultiplier(1x/2x/3x) × trackRecordMultiplier(0.7x/1.0x/1.3x, gated ngưỡng 5 hợp đồng)`. `batchAmount` lock sớm — ngay khi milestone trước `SETTLED`.

**Chốt bổ sung (04/07/2026):**
- **Local Outbox Pattern thay Spring `ApplicationEvent`** cho đồng bộ `Milestone` → `Contract` (§2.2) — sửa đúng bug crash-window đã phát hiện, giữ nguyên tinh thần local/không RabbitMQ.
- **Guard `Contract.settle()` cần fix** để chạy được từ `ACTIVE` thay vì chỉ `DELIVERED` (§3.1) — kèm dọn dead path `confirmDelivery()`/`ContractDeliveredEvent` khi implement. Phát hiện qua đối chiếu chéo với `reputation-service-phase2-design.md` KI-1.
- **Buyer timeout ở `BUYER_RECEIVED`** — `buyerConfirmWindowDays` = 2 ngày làm việc, mặc định `CONFIRM_CLEAN` nếu buyer im lặng (§3.2, §8).
- **`buyerDepositRate` — 3 đường release/seize chính thức hoá** qua 2 event cấp Contract mới thêm vào catalog: `contract.settled` (event có sẵn, thêm consumer escrow-service) và `contract.cancelled` (event hoàn toàn mới) (§6.3, §7.2).
- **Payload `milestone.settled` mở rộng** thêm `lockedAmount`/`actualAmount` để escrow-service tự tách `RELEASE_TO_SELLER` + `REFUND_TO_BUYER` khi Delta 1/2 pro-rata làm số tiền thực nhận thấp hơn số đã khoá (§7.1).

**Chỉ còn 1 điểm để ngoài phạm vi có chủ đích, không phải thiếu sót:** state machine đầy đủ của `reputation-service` ngoài phần input/trigger đã chốt — thiết kế chi tiết nằm ở `reputation-service-phase2-design.md` (session riêng, 04/07/2026).

**Chốt bổ sung (06/07/2026) — provisional settlement khi `CONTESTED` escalate Level 2 (§3.2, §7.1, §8):** thêm cơ chế buffer để giải quyết bottleneck report Level 2 chậm (email/admin-confirm, `inspection-phase2-design.md` §3) mà không kẹt tiền seller vô thời hạn. Hết `level2BufferWindowDays` (placeholder 7-14 ngày, chưa validate) chưa có report → commission Level 1.5 fallback, settle tạm `(1 − level2SafetyBufferRate)` của `batchAmount`, giữ khoá phần còn lại chờ đối chiếu với report thật. Dùng buffer khoá sẵn trong escrow thay vì ghi debt, vì seller không có tài sản đối ứng để đòi. **Chưa đóng** — 2 điểm còn treo: (1) số `level2BufferWindowDays`/`level2SafetyBufferRate` là placeholder, chưa validate với đơn vị Level 2 thật; (2) chưa quyết định buffer xử lý ra sao nếu report Level 2 không bao giờ về (khoá vô thời hạn hay có cutoff tuyệt đối) — xem chi tiết trong §3.2.

Milestone Escrow — **đóng session, sẵn sàng đưa vào Architecture/SDS/TechnicalSpec chính thức**, ngoại trừ bổ sung 06/07/2026 (provisional settlement Level 2, xem ngay trên) — phần này **chưa đóng**, còn 2 điểm treo cần giải quyết trước khi coi là chốt cứng. §6.3 (event `contract.cancelled`) cần Cường xác nhận riêng trước khi coi là chốt cứng — xem ghi chú trong mục đó. §7.2 (event `contract.signed`, mới 06/07/2026) cũng cần Cường xác nhận bảng mapping `Product.category` → `commodity` trước khi chốt cứng — xem ghi chú trong dòng đó.

---

*Design session: 02/07/2026 · Cập nhật: 04/07/2026 (Local Outbox, buyer timeout, buyerDepositRate release paths) · Cập nhật: 06/07/2026 (thêm event `contract.signed` cấp Contract + đăng ký analytics-service làm consumer của `contract.settled`/`contract.cancelled`, phát sinh từ rà soát chéo `analytics-service-phase2-design.md`; thêm provisional settlement + buffer cho Level 2 chậm report, §3.2/§7.1/§8 — chưa đóng, còn 2 điểm treo) · Chưa code · Chưa đưa vào Architecture/SDS/TechnicalSpec chính thức.*
