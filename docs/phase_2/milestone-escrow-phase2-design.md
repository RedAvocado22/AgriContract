---
name: milestone-escrow-phase2-design
description: "Milestone Escrow — full domain design cho Phase 2, thay thế two-phase lock escrow đang chạy ở Phase 1. Nguồn: design session 02/07/2026, cập nhật 04/07/2026, 06/07/2026 (event contract.signed; provisional settlement Level 2), 08/07/2026 (rà soát end-to-end: payload contract.signed + escrow.deposit_locked, mechanics Provisional Settlement, expectedDeliveryDate/timeout SELLER_WEIGHED+IN_PROGRESS, Delta 2 tolerance split, guardrail range, SIGNED vs ACTIVE)."
status: DESIGNED — chưa code.
metadata:
  type: design
  phase: 2
  supersedes: "architecture.md § Escrow State Machine (two-phase lock BUYER_LOCKED → FULLY_LOCKED); bản 02/07/2026 của chính file này (Spring ApplicationEvent → Local Outbox, thêm buyer timeout ở BUYER_RECEIVED, thêm buyerDepositRate release paths + event contract.settled/contract.cancelled)"
  related: "notification-service-phase2-design.md §4; api-gateway-phase2-design.md §3"
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
| `toleranceRate` | BigDecimal | Ngưỡng lệch cân chấp nhận được cho Delta 2 (hao mòn vận chuyển). Mặc định 50/50 chia trách nhiệm nếu vượt ngưỡng — kế thừa nguyên bản Doc2 mục 4.2. **Guardrail (mới, 08/07/2026): validate range `[0%, 10%]` lúc `sign()` — xem ghi chú dưới bảng.** |
| `shortfallPenaltyThreshold` | BigDecimal | Ngưỡng % thiếu hàng (Delta 1) mà dưới đó chỉ pro-rata, từ đó trở lên tính penalty. **Chốt: 5%.** (Giá trị mặc định — vẫn negotiate được per-contract giống các field khác trong `ContractTerms`.) **Guardrail (mới, 08/07/2026): validate range `[3%, 15%]` lúc `sign()` — xem ghi chú dưới bảng.** |
| `buyerPenaltyRate` / `sellerPenaltyRate` | BigDecimal | Giữ nguyên từ Phase 1, tái dùng cho nhánh Delta 1 penalty. **Guardrail (mới, 08/07/2026): validate range `[0%, 30%]` lúc `sign()` — xem ghi chú dưới bảng.** |
| `forceMajeureReportWindowDays` | Integer | Số ngày seller phải báo bất khả kháng kể từ lúc **biết sự kiện** (không neo theo ngày giao). **Chốt: 3 ngày.** |
| `buyerDepositRate` | BigDecimal | Cọc nhỏ của buyer — **Chốt: 5%** `totalAmount`. Lock **một lần duy nhất lúc `SIGNED`**, giữ xuyên suốt tới `SETTLED` cuối cùng. Vai trò "skin in the game", **không phải** T/T-style cọc cover rủi ro tài chính (đã bác bỏ ở `decisions.md` [2026-06-16]) — đó là việc của `batchAmount` lock riêng từng milestone (§6). 3 đường release/seize của field này — §6.3. |
| `disputeFloorReleaseRate` | BigDecimal, nullable/default `0.50` | **Mới (10/07/2026).** Tỷ lệ **sàn** release cho seller khi provisional settle Level 2 mà buyer từ chối/im lặng ở bước opt-out (§3.2 Bước 1). Đàm phán per-contract, guardrail `[50%, (1 − level2SafetyBufferRate)]` — sàn không được vượt trần `(1 − rate)`, nếu không "từ chối" lại release NHIỀU hơn "đồng ý" (vô nghĩa). Buyer (bên mạnh hơn) không kéo được xuống dưới 50% → seller HTX vẫn nhận ≥ nửa, không kẹt vốn tới terminal. Cùng cơ chế guardrail đã dùng cho `shortfallPenaltyThreshold`/`toleranceRate` — thương lượng có kẹp biên, không thả tự do (chống buyer ép), không áp cứng (không phải hằng số tĩnh). |
| `sellerDepositRate` | BigDecimal, nullable/default `0` | **Mới (06/07/2026), thay thế quyết định "bỏ hẳn" trước đây.** Cọc của seller — **optional, đàm phán per-contract** giữa buyer/seller lúc `NEGOTIATING`, không phải invariant kỹ thuật ép buộc. Platform không quyết định seller có cần cọc hay không — để buyer tự đánh giá mức tin tưởng với seller cụ thể (seller mới/không track record → buyer có động lực đòi cọc; seller uy tín cao → 2 bên có thể thoả thuận `0`, quay về đúng hiện trạng trước đây). Cùng triết lý neutral-party đã dùng cho `verificationLevel`/`geoRiskLevel` (`product-phase2-design.md`) — platform cấp công cụ, không áp luật chung. Lock cùng thời điểm với `buyerDepositRate` (lúc `SIGNED`). Vá đúng lỗ "seller cancel ở 0 milestone hoàn thành, không có gì để seize" — xem §6.1. |

**Thêm (08/07/2026) — guardrail cho các field negotiate tự do, chưa có validate range:** `toleranceRate` và `shortfallPenaltyThreshold` (cùng `buyerPenaltyRate`/`sellerPenaltyRate`) nằm trong `ContractTerms`, negotiate tự do per-contract, nhưng trước bản này không có validate range ở đâu. Buyer (bên mạnh hơn — Doc01 §2.3 là luận điểm gốc của cả dự án) hoàn toàn có thể ép `shortfallPenaltyThreshold = 0%` (seller thiếu 1 gram cũng dính penalty) hoặc `toleranceRate = 0%` (seller gánh 100% hao mòn ngay từ gram đầu) — thiết kế sinh ra để chống bất đối xứng quyền lực lại để hở đúng cơ chế đó.

**Chốt guardrail — validate ở use case `Sign`, ngoài range → reject, yêu cầu điều chỉnh trước khi ký:**
- `shortfallPenaltyThreshold` ∈ **[3%, 15%]**, default 5%. (Dưới 3% bất khả thi thực tế cho hao mòn nông sản; trên 15% seller được nuông quá mức.)
- `toleranceRate` ∈ **[0%, 10%]**, default 5%. (0% hợp lệ nếu 2 bên thực sự muốn, cảnh báo UI khi = 0.)
- `sellerPenaltyRate`/`buyerPenaltyRate` ∈ **[0%, 30%]** — penalty quá cao thành công cụ ép, không phải cơ chế bồi thường hợp lý.
- `disputeFloorReleaseRate` ∈ **[50%, (1 − `level2SafetyBufferRate`)]**, default 50% (mới, 10/07/2026). Sàn ≥ 50% để buyer không kéo release provisional về quá thấp làm kẹt vốn seller; trần = `(1 − level2SafetyBufferRate)` để sàn (lúc buyer từ chối) không vượt mức trần (lúc buyer đồng ý). Xem §3.2 Bước 1.

Bản thân việc **có** guardrail là câu trả lời cho câu hỏi hội đồng dễ hỏi: "làm sao chống buyer ép điều khoản?" — có số cụ thể defend được dễ hơn khoảng trống. Guardrail thu hẹp biên ép xuống mức chấp nhận được, **không** xoá được bất đối xứng quyền lực hoàn toàn (buyer vẫn thương lượng được trong biên) — đây là điểm honest defend trước hội đồng, không claim giải quyết triệt để.

`MilestoneTerm` (nested VO, phần tử của `milestoneSchedule`):

| Field | Loại | Ghi chú |
|---|---|---|
| `milestoneIndex` | Integer | Thứ tự batch (1, 2, ..., N) |
| `committedQuantity` | BigDecimal | Số lượng cam kết giao ở batch này |
| `batchAmount` | Money | `committedQuantity × agreedPrice` — phần tiền ứng với batch này |
| `expectedDeliveryDate` | Date | **Mới (08/07/2026), lỗ hổng gốc phát hiện khi rà timeout.** Snapshot immutable lúc `sign()`, giống mọi field khác trong `MilestoneTerm`. Trước bản này, `MilestoneTerm` không có bất kỳ mốc ngày nào — hệ quả: không neo được timeout nào ở giai đoạn giao hàng, và hệ thống **không định nghĩa được "seller giao trễ"** (Delta 1 chỉ đo *thiếu lượng*, không đo *trễ hạn* — trong khi trễ hạn là vi phạm phổ biến nhất của forward contract nông sản, Doc01 §2.1). |
| `graceDays` | Integer | **Mới (08/07/2026).** Số ngày ân hạn sau `expectedDeliveryDate` trước khi coi là quá hạn — cửa sổ thực tế là `[expectedDeliveryDate, expectedDeliveryDate + graceDays]`, không phải mốc cứng. **Chốt: để trong `ContractTerms`/`MilestoneTerm` (per-contract), không phải `application.yml`** — độ nhạy thời gian khác nhau theo mặt hàng (cà phê khô để lâu, giao trễ vài ngày không sao; rau quả tươi trễ là hỏng), cùng lý do `forceMajeureReportWindowDays` đã để per-contract — xem §8. |

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

### 2.3 `EscrowAccount` (cập nhật, 06/07/2026)

Bỏ `mockSellerBalance` (dual-write, không cần — xem note dưới). **`sellerDeposit` không còn "bỏ hẳn" như quyết định trước** — nay là state tuỳ chọn, đối xứng với `buyerDepositRate`, chỉ tồn tại khi `ContractTerms.sellerDepositRate > 0` (§2.1). Thêm `EscrowMilestone` (con) — mirror `Milestone` bên contract-service, track riêng phần lock/release tiền của từng batch (giữ nguyên nguyên tắc contract-service quản lý delivery state, escrow-service quản lý tiền — không lẫn 2 domain).

**State cấp Contract (song song 2 field, độc lập nhau — chỉ có giá trị khi rate tương ứng > 0):** `buyerDepositState` (`DEPOSIT_LOCKED`/`DEPOSIT_RELEASED`/`DEPOSIT_SEIZED`) và `sellerDepositState` (cùng 3 giá trị, mới 06/07/2026) — 2 field tách biệt vì 2 khoản cọc release/seize độc lập nhau theo bảng ở §6.1/§6.2, không phải cùng 1 lifecycle.

**Lưu ý (04/07/2026, xem `bank-service-phase2-design.md` §5):** `EscrowAccount`/`EscrowMilestone` chỉ giữ **state** (`LOCKED`/`RELEASED`/`PENALIZED`...), không tự lưu lại 1 con số tiền riêng phải đồng bộ tay với bank-service — số tiền thật là single source of truth ở `ledger_entry` bên bank-service. Giữ thêm số tiền ở đây là dual-write, đúng lỗi đã học ở Phase 1.

**Cập nhật (08/07/2026) — thêm state cho Provisional Settlement (§3.2):** `EscrowMilestone.status` thêm giá trị `PROVISIONALLY_RELEASED` (giữa `LOCKED` và `SETTLED`/`RELEASED` — dùng khi milestone đã release tạm theo phán quyết Level 1.5 nhưng còn giữ buffer chờ Level 2). Thêm field `bufferAmount` (Money, nullable) — **ngoại lệ hợp lệ** cho nguyên tắc "không giữ số tiền" ở trên, vì đây là *snapshot của phán quyết* (bất biến sau khi tính ở Bước 1, không phải số dư cần đồng bộ liên tục với bank), cùng logic với `lockDurationDays` bên reputation-service.

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

**Làm rõ (08/07/2026) — `SIGNED` ≠ mốc tiền, `ACTIVE` mới là mốc tiền:** `SIGNED` = mốc **chữ ký** — `transitionTo(SIGNED)` khi đủ 2 chữ ký hợp lệ, độc lập hoàn toàn với tiền (`signature-phase2-design.md`). `contract.signed` publish ngay tại đây là đúng — "hợp đồng đã được ký kết" là sự thật kể cả nếu bước lock cọc ngay sau đó thất bại. `ACTIVE` = đã ký **VÀ** cọc đã khoá thành công — đúng lúc `escrow.deposit_locked` (§7.2) về tới contract-service. Nếu lock cọc fail (bank-service trả `bank.lock_failed`, `bank-service-phase2-design.md` §3) → Contract kẹt ở `SIGNED`, chưa `ACTIVE`; nhánh xử lý fail (retry lock, hoặc rollback về trạng thái trước ký) cần định nghĩa khi implement, hiện chưa có. Hệ quả cho tầng phân tích: `contract.signed` đo "đã ký", `contract.settled`/milestone events đo "đã vận hành thật" — 2 câu hỏi khác nhau, `analytics-service` không được lẫn 2 mốc này khi tính conversion rate hay tương tự.

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

**[Mới, 08/07/2026] 2 timeout còn thiếu, không vẽ lại toàn bộ sơ đồ trên để tránh vỡ layout — chi tiết đầy đủ ở phần diễn giải bằng lời ngay dưới:**
- `IN_PROGRESS` quá hạn (`expectedDeliveryDate + graceDays`, mới) → nhánh seller-quá-hạn, buyer trigger được, seller còn cửa force majeure.
- `SELLER_WEIGHED` quá hạn (`buyerReceiveWindowDays`, mới) → notify buyer, im lặng tiếp → Admin/Level 1 quyết theo bằng chứng hiện có — **không** auto-settle theo số seller tự khai.

**Diễn giải bằng lời — đọc kỹ nếu sơ đồ mũi tên phía trên gây hiểu lầm:**

Mỗi milestone bắt đầu ở `CREATED` khi contract chuyển `ACTIVE`, rồi chuyển ngay sang `IN_PROGRESS` — trạng thái mặc định trong lúc seller chuẩn bị/gom hàng cho batch đó. Từ `IN_PROGRESS` có hai hướng đi, không phải một đường thẳng:

*Hướng bình thường (không có sự cố):* seller cân hàng trước khi lên xe, upload ảnh làm bằng chứng → milestone chuyển `SELLER_WEIGHED`, ghi nhận `sellerDeclaredWeight`. Hàng vận chuyển tới buyer. Buyer cân lại khi hạ hàng, upload ảnh bằng chứng riêng → milestone chuyển `BUYER_RECEIVED`, ghi nhận `buyerReceivedWeight`.

Tại `BUYER_RECEIVED`, buyer có đúng hai lựa chọn chủ động, cộng thêm 1 nhánh timeout. Nếu số lượng và chất lượng đúng như mong đợi, buyer bấm **CONFIRM_CLEAN** — hệ thống tự tính pro-rata theo Delta 2 (so `sellerDeclaredWeight` với `buyerReceivedWeight`) và release tiền ngay, milestone chuyển thẳng `SETTLED`, không ai can thiệp, không tốn phí gì. Nếu buyer thấy có vấn đề (thiếu cân hoặc sai chất lượng), buyer bấm **FLAG_ISSUE** — milestone chuyển `AWAITING_SELLER_RESPONSE`, seller có đúng `sellerResponseWindowDays` (mặc định 2 ngày làm việc) để phản hồi. Seller im lặng hết thời gian này = hệ thống coi như đồng ý với con số buyer báo, tự động `SETTLED` theo số đó. Seller không đồng ý, bấm **CONTESTED** — tranh chấp được đẩy qua `DisputeRoutingService`, dùng đúng cơ chế 3-tier đã có sẵn cho Tiered Dispute Resolution (Admin nội bộ / Vinacontrol-Quatest / SGS-Bureau Veritas tuỳ giá trị và độ phức tạp hàng hoá) — có kết quả rồi milestone mới `SETTLED` theo phán quyết đó.

**Cập nhật (04/07/2026) — timeout đối xứng cho nhánh buyer im lặng:** nếu buyer không bấm gì cả (không `CONFIRM_CLEAN`, không `FLAG_ISSUE`) trong vòng `buyerConfirmWindowDays` (mặc định 2 ngày làm việc, cùng độ dài với cửa sổ phản hồi của seller) kể từ lúc milestone vào `BUYER_RECEIVED`, hệ thống tự động xử lý như `CONFIRM_CLEAN` — release theo đúng `sellerDeclaredWeight`/`buyerReceivedWeight` đã ghi nhận, milestone `SETTLED`. Lý do default về `CONFIRM_CLEAN` chứ không phải treo vô thời hạn: seller đã hoàn thành nghĩa vụ giao hàng tới đúng lúc `BUYER_RECEIVED` (hàng đã tới tay buyer) — buyer im lặng không được phép biến thành công cụ giữ tiền seller vô thời hạn, đúng nguyên tắc "nghiêng về seller khi seller yếu thế hơn" đã dùng xuyên suốt (§6.2).

**Sửa (08/07/2026) — `SELLER_WEIGHED` không có timeout, lỗ hổng để buyer né toàn bộ cơ chế:** state machine có timeout ở `BUYER_RECEIVED` (trên) và `AWAITING_SELLER_RESPONSE` (dưới), nhưng **`SELLER_WEIGHED` không có** — nếu hàng đã cân/gửi mà buyer không bao giờ cân lại/ghi nhận (tức không bao giờ bước vào `BUYER_RECEIVED`), milestone kẹt ở `SELLER_WEIGHED` vĩnh viễn, `batchAmount` khoá vĩnh viễn. Buyer chỉ cần *không bấm gì ở bước nhận hàng* là né sạch toàn bộ timeout đã dựng — và vì `batchAmount` lock sớm (§6.2), lối thoát duy nhất trước đây là `cancel()`, khiến **buyer bị seize cọc dù mình mới là bên gây kẹt** — bất công ngược đúng chỗ luận điểm gốc "bảo vệ bên yếu" muốn tránh.

**Không bắt chước cơ chế `BUYER_RECEIVED` (auto CONFIRM_CLEAN):** ở `BUYER_RECEIVED` đã có **cả 2 số cân** (bằng chứng đầy đủ, buyer im lặng chỉ = không phản đối) nên auto-settle an toàn. Ở `SELLER_WEIGHED` chỉ có số seller **tự khai**, chưa có bằng chứng hàng đã tới tay buyer — auto-settle theo số đó sẽ tạo lỗ hổng ngược: seller cân xong không cần giao thật, đợi timeout là có tiền. Đường đúng, dùng field `expectedDeliveryDate`/`graceDays` mới thêm ở `MilestoneTerm` (§2.1):

- Hết `buyerReceiveWindowDays` (mới, mặc định 2 ngày làm việc — §8) mà buyer chưa cân → **notify** buyer, chưa đổi state.
- Buyer vẫn im lặng hoàn toàn → đẩy milestone vào nhánh **Admin/Level 1** (bước rẻ nhất, đúng triết lý 3-tier) — Admin quyết theo bằng chứng hiện có (ảnh cân seller + bằng chứng vận chuyển nếu có), **default nghiêng seller** chỉ khi seller đã chứng minh giao và buyer từ chối tham gia hoàn toàn.
- Kết quả: buyer im lặng **không** tự động thắng (không kẹt tiền seller vô thời hạn để né phí), seller tự khai **cũng không** tự động thắng (không thể cân khống rồi chờ timeout ăn tiền).

**Sửa (08/07/2026) — timeout ở `IN_PROGRESS` khi seller quá hạn giao (dùng `expectedDeliveryDate`/`graceDays` mới, §2.1):** trước bản này, `MilestoneTerm` không có mốc ngày nào — hệ thống không định nghĩa được "seller giao trễ", dù trễ hạn là vi phạm phổ biến nhất của forward contract nông sản (Doc01 §2.1: bẻ kèo do biến động giá theo mùa vụ). Quá `expectedDeliveryDate + graceDays` mà milestone chưa rời `IN_PROGRESS` → **buyer trigger được** nhánh seller-quá-hạn. Seller còn cửa claim force majeure trong `forceMajeureReportWindowDays` (cơ chế đã có, không đổi). Không claim được/claim bị bác → xử như thiếu hàng (Delta 1 penalty nhánh 2, §4) hoặc cancel pro-rata milestone đó — **buyer không bị seize cọc** vì buyer không phải bên phá kèo trong nhánh này.

**Cập nhật (06/07/2026) — provisional settlement khi `CONTESTED` escalate Level 2:** khi `DisputeRoutingService` route milestone `CONTESTED` sang `LEVEL_2` (ngưỡng giá trị/độ phức tạp hàng hoá, §8), platform commission tổ chức Level 2 qua `InitiateLevel2Inspection` (`inspection-phase2-design.md` §3.4) và chờ report thật trong tối đa `level2BufferWindowDays`.

`level2BufferWindowDays` — **chốt 10 ngày làm việc.** Neo theo benchmark turnaround lab test chất lượng chuyên sâu chuẩn SCA (cupping cà phê, độ ẩm, phân tích cảm quan — đúng loại cho tranh chấp chất lượng nông sản): 5 ngày làm việc kể từ khi nhận mẫu, cộng buffer lịch hẹn/vận chuyển mẫu. Cấu hình `application.yml`, chỉnh được khi có dữ liệu vận hành thật — cùng nguyên tắc đã áp cho `inspectionAuthMaxAgeSeconds` (`inspection-phase2-design.md` §2).

Nếu hết `level2BufferWindowDays` mà report Level 2 chưa `CONFIRMED` (`inspection-phase2-design.md` §3.6): platform commission thêm 1 giám định Level 1.5 (Vinacontrol/Quatest) làm phán quyết tạm thời, dùng số đó settle **ngay** phần lớn milestone theo mechanics đầy đủ dưới đây.

**Sửa (08/07/2026) — mechanics tiền viết lại đầy đủ, phát hiện thiếu chân ở tầng escrow/bank khi rà cross-service:** bản trước chỉ có prose ("release `(1-rate)` của `batchAmount`... release nốt `bufferAmount` cho seller") — không đủ cụ thể để escrow-service/bank-service implement, và câu "release nốt bufferAmount cho seller" ở bản cũ **sai** nếu hiểu là toàn bộ phần đang giữ, vì phần giữ có cả phần thuộc buyer (xem Bước 1). Ký hiệu: `batchAmount` = số đã khoá cho milestone; `X15` = số tiền seller đáng nhận theo phán quyết Level 1.5; `rate` = `level2SafetyBufferRate`; `X2` = số Level 2 thật (khi report về).

**Bước 0 — Opt-out của buyer (mới, 10/07/2026):** trước bản này, provisional release chạy **tự động** ở mức `X15 × (1 − rate)` (~85%), buyer không được hỏi. Vấn đề: nếu Level 2 sau đó phán seller sai nặng, số đền vượt buffer → phần đã release **không đòi lại được** (HTX không tài sản đối ứng — xem Bước 2, case `X2 < released`). Buyer là bên **gánh rủi ro** đó nhưng không được quyết. Opt-out trả quyền quyết về đúng bên chịu rủi ro — **nhưng không nhị phân xả-hết/khoá-cứng** (khoá cứng tới terminal ~37-44 ngày sẽ giết đúng thanh khoản HTX mà cả cơ chế này sinh ra để cứu). Thay vào đó là **2 mức release**, buyer chọn:

- Hết `level2BufferWindowDays`, commission Level 1.5 xong (có `X15`), **trước khi release** → notify buyer + hỏi có chấp nhận mức release cao không (giám định Level 2 còn chờ, biến động cao, buffer có thể không đủ cover).
- **Buyer đồng ý** → `relRate = (1 − level2SafetyBufferRate)` (**trần**, ~85% — đúng hành vi cũ, giữ nguyên).
- **Buyer từ chối HOẶC im lặng** hết `disputeOptOutWindowDays` (§8) → `relRate = disputeFloorReleaseRate` (**sàn**, ≥50%, guardrail §2.1) — release ít hơn, giữ buffer lớn hơn. **Không** khoá sạch: seller HTX vẫn nhận ≥ nửa ngay, không kẹt vốn.
- **Default khi buyer im lặng = sàn** (nghiêng bảo vệ buyer). Đây **ngược** với timeout ở `BUYER_RECEIVED` (im lặng → `CONFIRM_CLEAN`, release) — và ngược là đúng: `BUYER_RECEIVED` không tranh chấp, im lặng nghĩa "ổn"; ở đây đang tranh chấp Level 2 + biến động cao, im lặng nghĩa "chưa đồng ý gánh rủi ro xả cao" → sàn. Nghĩa của "im lặng" khác nhau theo ngữ cảnh, default nghiêng theo nghĩa đúng của từng chỗ.
- **Lưu ý rủi ro còn lại:** ngay cả mức sàn cũng đẩy một phần rủi ro sang buyer (phần đã release ở sàn vẫn không đòi lại được nếu Level 2 lật) — "từ chối" giảm exposure, **không** về 0. Đây là cái giá của việc không giết seller, ghi rõ.

**3 bước dưới đây giữ nguyên mechanics, chỉ tham số hoá tỷ lệ release: thay `(1 − rate)` → `relRate` (mức buyer đã chọn ở Bước 0), và `rate` → `(1 − relRate)` ở phần buffer của seller.** Khi buyer đồng ý, `relRate = (1 − rate)` → toàn bộ số học về đúng bản cũ.

**Bước 1 — Provisional settle (hết `level2BufferWindowDays`, chưa có report Level 2):**
- Release ngay cho seller = `X15 × relRate` — dùng `X15`, **không phải** `batchAmount` (nếu `X15 < batchAmount` mà release theo `batchAmount` thì trả thừa, không đòi lại được).
- Giữ khoá phần còn lại = `batchAmount − X15×relRate`, gồm 2 khối: `X15 × (1−relRate)` (buffer của seller, chờ Level 2 xác nhận — dày hơn khi buyer chọn sàn) + `batchAmount − X15` (phần thuộc buyer theo phán quyết 1.5 — **không** refund buyer sớm ở bước này, vì nếu Level 2 sau đó phán seller đáng nhận nhiều hơn thì đòi lại từ buyer cũng khó y hệt đòi seller).
- `EscrowMilestone.status = PROVISIONALLY_RELEASED` (state mới, thêm vào §2.3). Field `bufferAmount` (= `batchAmount − X15×relRate`) snapshot lên `EscrowMilestone` lúc này — đây là snapshot phán quyết bất biến, **không** phải số dư phải đồng bộ tay với bank-service (không vi phạm nguyên tắc "escrow không giữ số tiền" ở `bank-service-phase2-design.md` §5, cùng logic với `lockDurationDays` bên reputation-service).
- Ledger: `RELEASE_TO_SELLER(X15×relRate)`. Không refund gì cho buyer ở bước này.

**Lý do dùng buffer khoá sẵn thay vì ghi nợ:** seller (HTX) không có tài sản đối ứng để đòi nếu số Level 2 thật (về sau) thấp hơn số đã release tạm — không có `sellerDepositRate` bắt buộc để seize. Giữ tiền sẵn trong escrow xử lý đúng rủi ro tại nguồn, không phụ thuộc khả năng seller trả nợ sau này.

**Bước 2 — Reconcile (report Level 2 `CONFIRMED` về trong `level2BufferTerminalDays`):**
- Seller tổng cộng đáng nhận = `min(X2, batchAmount)`.
- Bù thêm cho seller = `max(0, min(X2,batchAmount) − X15×relRate)`, lấy từ phần đang giữ.
- Refund buyer = phần giữ còn lại sau khi bù seller.
- **Nếu `X2 < X15×relRate`** (Level 2 phán thấp hơn số đã lỡ release ở Bước 1): seller đã nhận thừa, **không đòi lại được** — đây chính là rủi ro mà `rate` (buffer) sinh ra để hấp thụ; phần thiếu hụt do buyer chịu, đúng nguyên tắc "nghiêng về seller yếu thế hơn" đã dùng xuyên suốt (§6.2), không phải bất công ngẫu nhiên.
- Ledger: `RELEASE_TO_SELLER(bù thêm)` + `REFUND_TO_BUYER(còn lại)`.

**Cập nhật (06/07/2026) — chốt hạn cứng, đóng điểm treo về buffer vô thời hạn:** thêm `level2BufferTerminalDays` (chốt 30 ngày, §8; tính từ lúc `level2BufferWindowDays` hết hạn — tổng thời gian chờ tối đa ~40 ngày trước khi có điểm dừng cứng).

**Bước 3 — Terminal (hết `level2BufferTerminalDays`, report Level 2 không bao giờ về):**
- Phán quyết 1.5 thành chung thẩm. Seller nhận nốt **`X15 × (1−relRate)`** (buffer của chính seller, không phải toàn bộ phần đang giữ). Buyer nhận **`batchAmount − X15`** (phần thuộc buyer theo 1.5, tách riêng khỏi buffer của seller — đây chính là chỗ bản cũ gộp nhầm thành "release nốt bufferAmount cho seller").
- Ledger: `RELEASE_TO_SELLER(X15×(1−relRate))` + `REFUND_TO_BUYER(batchAmount − X15)`.
- Rủi ro số 1.5 sai lệch so với số Level 2 thật (nếu report từng về muộn) dồn hết sang buyer — chấp nhận được, đúng nguyên tắc đã dùng xuyên suốt.

**Về `entryType`:** không cần enum mới ở bank-service — vẫn `RELEASE_TO_SELLER`/`REFUND_TO_BUYER`, mỗi động tác 1 `sourceEventId` riêng (đúng pattern Delta 1/2 đã dùng, `bank-service-phase2-design.md` §3.1).

**Chốt (13/07/2026) — 3 số cấu hình neo theo benchmark, đưa vào SDS:** cả 3 số neo theo căn cứ ngành, cấu hình `application.yml`, tinh chỉnh được khi có dữ liệu vận hành thật:
- `level2BufferWindowDays` = **10 ngày làm việc** — neo benchmark lab test chất lượng chuẩn SCA (5 ngày làm việc/mẫu, `[REFERENCE]`) + buffer lịch hẹn/vận chuyển mẫu.
- `level2SafetyBufferRate` = **15%** — chọn mức trên của dải cân nhắc (10-15%), nghiêng bảo vệ buyer (bên gánh rủi ro nếu Level 2 lật, tiền không đòi được từ HTX), nhất quán với default dispute nghiêng buyer ở §8.
- `level2BufferTerminalDays` = **30 ngày** — điểm dừng cứng để không kẹt buffer vô thời hạn (tổng chờ tối đa ~40 ngày).

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

**Sửa (08/07/2026) — Delta 2 đang để seller gánh 100% hao mòn, mâu thuẫn với chính Doc02 §3.3:** Doc02 ghi rõ Delta 2 *"chia theo `toleranceRate` đã đàm phán, mặc định 50/50 nếu vượt ngưỡng tolerance"*, nhưng cơ chế tiền trước bản này chỉ có 1 đường — `milestone.settled` release theo `buyerReceivedWeight`, tức seller nhận đúng số buyer cân được, gánh 100% phần hao mòn. Không có `entryType`/phép tính nào thể hiện "buyer chịu phần của mình". Diễn giải đúng ý đồ gốc: **trong ngưỡng tolerance, buyer chấp nhận hao mòn là bình thường (settle theo `buyerReceivedWeight` như cũ); vượt ngưỡng, phần vượt mới chia theo `toleranceRate`** — seller không gánh 100% phần bất thường, buyer chia sẻ trách nhiệm vì rủi ro vận chuyển ngoài kiểm soát cả hai.

**Công thức (khi Delta 2 vượt tolerance):**
```
delta2   = sellerDeclaredWeight − buyerReceivedWeight
within   = min(delta2, toleranceRate × sellerDeclaredWeight)   // phần trong ngưỡng: buyer chịu như cũ
excess   = delta2 − within                                      // phần vượt: chia theo toleranceRate
sellerBears = excess × 0.5   // hoặc theo tỷ lệ khác nếu 2 bên đàm phán riêng ngoài mặc định 50/50
buyerBears  = excess × 0.5
actualAmount = (buyerReceivedWeight + buyerBears) × agreedPrice
```
Nếu `delta2 ≤ within` (chưa vượt ngưỡng) → `actualAmount = buyerReceivedWeight × agreedPrice` như cơ chế cũ, không đổi gì. **Chốt (08/07/2026):** chia **phần vượt ngưỡng** (không phải toàn bộ Delta 2) — vì `toleranceRate` theo định nghĩa là "ngưỡng hao mòn chấp nhận được", hao mòn trong ngưỡng buyer đã ngầm chấp nhận lúc ký, chỉ phần vượt mới là bất thường cần chia sẻ; nếu chia cả phần trong ngưỡng thì `toleranceRate` mất nghĩa "ngưỡng" và biến thành "tỷ lệ chia" thuần. Chia trên **khối lượng** (không phải tiền) — vì `agreedPrice` cố định suốt hợp đồng nên chia khối lượng rồi × giá cho kết quả y hệt chia tiền, nhưng khớp với cách Delta 1 đã thao tác (đều tính trên `weight` rồi × `agreedPrice` ở bước cuối), nhất quán 1 kiểu tính, dễ test. `contract-service` là nơi tính `actualAmount` cuối cùng (có đủ `ContractTerms`), truyền số đã tính xuống `escrow-service` qua payload `milestone.settled` — không để escrow-service tự tính lại tolerance split.

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

**Cập nhật (06/07/2026) — không còn tuyệt đối "không có gì để seize":** trước đây seller cancel không có gì để escrow tự động trừ, vì Milestone Escrow bỏ hẳn seller deposit (viability constraint cho HTX). Quyết định đó **đổi thành optional** — `sellerDepositRate` (§2.1) giờ tồn tại nếu buyer/seller tự đàm phán có, mặc định vẫn `0` (giữ nguyên hiện trạng cho ai không cần). Cơ chế dưới đây tách theo 2 case:

**Case A — có `sellerDepositRate > 0` đã khoá:** seize ngay lập tức, tự động, không cần chờ Admin — giống cơ chế `buyerDepositRate` seize ở §6.2. Số tiền seize offset trực tiếp vào `penalty debt` ở mục 1 dưới: `phần còn nợ = (sellerPenaltyRate × giá trị milestone còn lại) − sellerDepositRate đã seize`. Nếu deposit ≥ penalty tính ra, phần dư (nếu buyer/seller có thoả thuận riêng) hoặc phần penalty debt = 0 — không âm.

**Case B — `sellerDepositRate = 0` (mặc định, hoặc buyer chọn không đòi):** giữ nguyên cơ chế cũ dưới đây — không qua escrow, dựa hoàn toàn vào Reputation làm đòn bẩy enforce thật. Đây là **lựa chọn buyer đã biết trước và chấp nhận** (đàm phán lúc `NEGOTIATING`), không phải lỗ hổng platform bỏ sót.

**`buyerDepositRate` tách hoàn toàn khỏi penalty debt của seller (04/07/2026, áp dụng cho cả 2 case A/B trên):** buyer không phải bên phá kèo trong case này — `buyerDepositRate` (khoá từ lúc `SIGNED`, §2.1) được refund về buyer ngay khi `cancel()` do seller khởi xướng được xác nhận, độc lập hoàn toàn với cơ chế penalty debt/lockout/`sellerDepositRate` seize ở trên (đó là hình phạt nhắm vào hành vi của seller, không liên quan gì tới tiền của buyer). Trigger + event cụ thể — §6.3.

**Cơ chế Case B — không qua escrow, dựa vào Reputation (Doc2 mục 4.5) làm đòn bẩy enforce thật:**

1. **Penalty debt được ghi nhận** — `sellerPenaltyRate × giá trị milestone còn lại`, lưu vào audit trail bất biến, có giá trị làm bằng chứng bồi thường thiệt hại theo Luật TM 2005 Điều 302 nếu buyer muốn truy đòi qua VIAC/toà án. Platform không tự thu hộ được — đây chỉ là bằng chứng.
2. **Khoá account seller ngay lập tức** — chặn tạo listing/hợp đồng mới, không đợi kết quả toà (Doc1 mục 2.2: tố tụng 1–3 năm, chờ toà là tự mâu thuẫn với lý do sản phẩm tồn tại).
3. **Thời gian khoá = 2 trục hành vi, không tính giá trị hợp đồng** — giá trị đã được phản ánh riêng ở `penalty debt` (mục 1, tỷ lệ theo `sellerPenaltyRate × giá trị`); nhét giá trị vào cả `lockDurationDays` là tính 2 lần cho cùng 1 vi phạm qua 2 kênh khác nhau. Tách bạch: **penalty debt phản ánh thiệt hại tài chính, số ngày khoá phản ánh mức độ hành vi tái phạm** — hai câu hỏi khác nhau, không dùng chung 1 biến:

   ```
   lockDurationDays = baseDays × repeatOffenseMultiplier × trackRecordMultiplier × zeroProgressMultiplier
   ```
   **⟢ Công thức + baseline number của `lockDurationDays` do `reputation-service-phase2-design.md` §4 chốt (owner — nơi implement thật). Phần dưới đây chỉ diễn giải *vì sao* các thừa số tách bạch như vậy, phục vụ lập luận của escrow; con số cụ thể lấy ở reputation §4, nếu lệch thì reputation thắng.** (`zeroProgressMultiplier` thêm 08/07/2026, xem cuối §6.1 — đã đưa vào công thức để không lệch với reputation §4.)
   - `repeatOffenseMultiplier` — theo **số lần từng cancel-có-penalty trước đó**, tuyến tính theo số lần vi phạm tuyệt đối (không phải tỷ lệ, nên không có vấn đề mẫu nhỏ); chỉ đếm trong cửa sổ `repeatOffenseLookbackMonths` (24 tháng) gần nhất — chi tiết reputation §4.2.
   - `trackRecordMultiplier` — **gated theo ngưỡng mẫu tối thiểu**, không dùng % thô ngay từ đầu. Lý do: % với mẫu số nhỏ cực kỳ không ổn định — seller mới (0-1 hợp đồng) vi phạm 1 lần sẽ ra 0% clean, trong khi seller có 20 hợp đồng sạch vi phạm hợp đồng thứ 21 vẫn ra 95% clean, dù hành vi vi phạm giống hệt nhau (1 lần). Dưới ngưỡng mẫu tối thiểu → seller "chưa đủ dữ liệu", dùng multiplier trung tính, không bị đẩy xuống đáy chỉ vì mới tham gia. Từ ngưỡng trở lên → dùng % sạch thật để giảm nhẹ hoặc tăng nặng.
   - **Baseline cụ thể (`baseDays = 30`, ngưỡng mẫu 5 hợp đồng, các multiplier 1x/2x/3x, 0.7x/1.0x/1.3x, `zeroProgressMultiplier` 1.5x): chốt ở `reputation-service-phase2-design.md` §4 — không lặp lại số ở đây để tránh 2 bản lệch nhau.**
   - Toàn bộ nằm trong `application.yml`, chỉnh được sau khi có dữ liệu thật — không cần đúng tuyệt đối ngay từ đầu.
4. **Mở khoá qua 1 trong 3 đường, đường nào tới trước:**
   - Buyer tự báo đã giải quyết xong (ngoài platform) → tự đóng.
   - Seller upload bằng chứng kết quả ràng buộc (bản án, phán quyết VIAC, thoả thuận hoà giải hai bên ký) qua `file-service` → Admin verify → đóng theo kết quả (seller thắng có thể phục hồi reputation, không chỉ mở khoá).
   - Timeout cố định (platform tự đặt, không phụ thuộc tốc độ toà) → tự mở khoá dù chưa có phán quyết nào.
5. **Penalty debt + lịch sử vi phạm không bao giờ bị xoá khỏi reputation**, dù account có mở khoá hay không — mở khoá chỉ là "cho giao dịch tiếp", không phải "xoá tiền án".

**Chốt (08/07/2026) — `lockDurationDays` nặng hơn cho case cancel khi 0 milestone nào từng `SETTLED`:** áp dụng — ký xong bỏ ngay (0 milestone hoàn thành) là tín hiệu xấu nhất có thể có, khác hẳn cancel giữa chừng do mâu thuẫn phát sinh (đã có ít nhất 1 batch chạy), nên xứng đáng nặng hơn. Cụ thể: thêm 1 hệ số `zeroProgressMultiplier` (chốt 1.5x, `application.yml`, chỉnh được) nhân vào công thức `lockDurationDays` khi và chỉ khi milestone chưa có cái nào `SETTLED` tại thời điểm cancel. Đây cũng chính là lớp ma sát kỹ thuật cho rủi ro disintermediation (2 bên quen nhau qua platform rồi rủ nhau cancel-ở-milestone-đầu để tự giao dịch tay ngoài né phí) — không chặn tuyệt đối được (rủi ro cấu trúc của mọi marketplace), nhưng tăng chi phí lockout cho đúng cái pattern đáng nghi nhất.

### 6.2 Buyer-initiated Cancel

**Bất đối xứng có chủ đích (mặc định, không tuyệt đối từ 06/07/2026):** buyer (doanh nghiệp thu mua, thường có vốn) có deposit thật để mất — seller (HTX) mặc định thì không, vì lý do viability đã chốt ở §1, nhưng giờ **có thể có** nếu `sellerDepositRate > 0` được đàm phán riêng (§6.1). Buyer luôn bị enforce bằng cả tiền thật lẫn reputation; seller enforce bằng reputation là mặc định, cộng thêm tiền thật nếu buyer chủ động đòi cọc lúc đàm phán.

Buyer huỷ bất kỳ lúc nào sau `SIGNED`:

1. **Mất toàn bộ `buyerDepositRate` (5% `totalAmount`)** — chuyển cho seller, escrow tự động seize ngay, không cần chờ Admin. (Event cụ thể — §6.3.)
2. **Nếu đang có `batchAmount` của milestone hiện tại đang lock** — seize luôn theo `buyerPenaltyRate`, chuyển cho seller như bồi thường, y hệt logic `PENALIZED_BUYER` của Phase 1 nhưng chỉ scope đúng 1 batch.
3. **Khoá account + xấu track record** — áp đúng công thức `lockDurationDays` ở §6.1, dùng chung cho cả buyer và seller (đổi `sellerPenaltyRate` thành `buyerPenaltyRate` trong input tính penalty debt).

**Thời điểm lock `batchAmount`: chốt sớm** — buyer lock ngay khi milestone trước đó chuyển `SETTLED` (hoặc ngay khi contract `ACTIVE` cho milestone đầu tiên). Lý do chọn sớm thay vì muộn: nhất quán với hướng đã chọn xuyên suốt session — mọi lần có tension giữa bảo vệ buyer và bảo vệ seller, thiết kế đều nghiêng về seller khi seller là bên yếu thế hơn (bỏ seller deposit vì viability, force majeure bảo vệ seller khỏi bị buộc tội oan, `buyerDepositRate` bắt buộc buyer luôn có "cái để mất" từ `SIGNED`) — đúng luận điểm gốc Doc1 mục 2.3 về bất đối xứng quyền lực. Lock sớm cho seller đảm bảo tối đa trước khi họ bỏ công/vốn chuẩn bị hàng cho batch tiếp theo.

**Ownership giữa các service:** `contract-service` tính penalty + publish event khi milestone bị cancel-có-penalty (cả 2 chiều buyer/seller). `reputation-service` (service #8, Phase 2 — services.md) consume event này cùng lịch sử hợp đồng hoàn thành, tính `lockDurationDays` theo công thức trên, và là nguồn quyết định khoá/mở khoá. `user-service` enforce khoá thật (chặn tạo listing/contract) dựa trên quyết định từ `reputation-service`. Chi tiết state machine đầy đủ của `reputation-service` (aggregate từ nhiều nguồn, eventually consistent theo services.md) để dành cho design session riêng — ở đây chỉ chốt input/trigger nó cần nhận.

### 6.3 `buyerDepositRate`/`sellerDepositRate` — 3 đường release/seize, event cấp Contract (mới, 04/07/2026; mở rộng cho `sellerDepositRate`, 06/07/2026)

**Vấn đề phát hiện khi rà lại luồng tiền:** `buyerDepositRate` là khoá **cấp Contract** (`milestoneId = NULL` ở `ledger_entry`, `bank-service-phase2-design.md` §2), không thuộc về milestone nào — nên không thể dùng `milestone.cancelled_with_penalty` (event cấp milestone, §7.1) làm trigger cho nó. Event Catalog bản 02/07/2026 chưa có event nào ở cấp Contract để xử lý riêng field này — lỗ hổng thật, không phải chi tiết vụn.

**Chốt (04/07/2026) — 3 đường, 2 event cấp Contract:**

| Trigger | Event | Kết quả cho `buyerDepositRate` | Kết quả cho `sellerDepositRate` (mới, 06/07/2026) |
|---|---|---|---|
| Hợp đồng hoàn tất bình thường — tất cả milestone `SETTLED` | `contract.settled` — đã tồn tại trong code Phase 1 (`ContractSettledEvent`), **không phải event mới**, chỉ thêm consumer mới. Cần guard fix ở §3.1 trước khi dùng lại được. | `REFUND_TO_BUYER` | `RELEASE_TO_SELLER` (nếu có khoá — dùng lại đúng `entryType` đã có, không phải field mới) |
| Seller khởi xướng `cancel()` (§6.1) | `contract.cancelled` (initiatedBy=SELLER) — **event mới** | `REFUND_TO_BUYER` — độc lập với penalty debt | `SEIZE_PENALTY` (nếu có khoá — offset vào penalty debt, §6.1 Case A) |
| Buyer khởi xướng `cancel()` (§6.2) | `contract.cancelled` (initiatedBy=BUYER) — **event mới** | `SEIZE_PENALTY` (chuyển cho seller) | `RELEASE_TO_SELLER` (nếu có khoá — seller không phải bên phá kèo, độc lập penalty của buyer) |

Không cần event mới riêng cho `sellerDepositRate` — tận dụng đúng 2 event `contract.settled`/`contract.cancelled` đã có, chỉ thêm nhánh xử lý (escrow-service tự kiểm tra `sellerDepositRate > 0` trước khi bắn thêm `LedgerEntry` tương ứng, `entryType` dùng lại enum sẵn có `bank-service-phase2-design.md` §2, không cần thêm giá trị mới). `contract.cancelled` — publisher `contract-service`, bắn đúng 1 lần mỗi khi `Contract.cancel()` được gọi, bất kể cancel kéo theo bao nhiêu milestone bị `milestone.cancelled_with_penalty` riêng lẻ. Payload tối thiểu: `{contractId, initiatedBy: BUYER|SELLER}`. Domain consumers: escrow-service + analytics-service; email đi qua `notification.contract_cancelled_requested` riêng để payload domain không phải mang template/contact data. Chi tiết catalog — §7.2.

**Đã review + chốt (08/07/2026):** event `contract.cancelled` (cả 2 nhánh `buyerDepositRate`/`sellerDepositRate`) — logic khớp với §6.1/§6.2 (seller cancel → refund buyer, buyer cancel → seize), tên/thiết kế event nhất quán với convention `escrow.buyer_locked` đã có. Không còn treo, sẵn sàng vào SDS.

---

## 7. Event Catalog

**Chốt (02/07/2026):** theo đúng convention `{aggregate}.{actor}_{past_tense_verb}` đã có tiền lệ ở `escrow.buyer_locked`.

### 7.1 Milestone-level

| Loại | Tên | Publisher | Consumer(s) |
|---|---|---|---|
| Local Outbox (nội bộ, không RabbitMQ — cơ chế ở §2.2) | `milestone_sync_outbox` row | `Milestone.settle()` | `@Scheduled` poller → `Contract.completeAllMilestones()` |
| RabbitMQ | `milestone.seller_weighed` | contract-service | file-service (evidence), audit-service |
| RabbitMQ | `milestone.buyer_confirmed` | contract-service | audit (**sửa 08/07/2026 — bỏ escrow-service**, xem ghi chú dưới) |
| RabbitMQ | `milestone.flagged` | contract-service | Không có domain consumer bắt buộc; mail đi qua `notification.milestone_status_requested` |
| RabbitMQ | `milestone.force_majeure_claimed` | contract-service | audit-service |
| RabbitMQ | `milestone.force_majeure_resolved` | contract-service | escrow-service, audit-service |
| RabbitMQ | `milestone.settled` | contract-service | escrow-service, reputation-service, analytics-service, audit-service |
| RabbitMQ | `milestone.cancelled_with_penalty` | contract-service | escrow-service (seize nếu có `batchAmount` lock), reputation-service, analytics-service, audit-service |
| RabbitMQ | `milestone.dispute_resolved` (mới, 08/07/2026 — phát sinh từ session `reputation-service` B4) | contract-service — bắn khi `DisputeRoutingService` (3-tier, §3.2) ra phán quyết cho milestone từng `CONTESTED`. Payload: `{milestoneId, flaggedBy: BUYER, resolutionFavors: BUYER\|SELLER}` (chỉ buyer flag được ở state machine hiện tại — mở rộng field này nếu sau này seller cũng có quyền flag) | reputation-service (đếm tỷ lệ buyer flag-rồi-thua, tín hiệu chống lạm dụng `FLAG_ISSUE` — `reputation-service-phase2-design.md` §6.1b) |

**Sửa (08/07/2026) — `milestone.buyer_confirmed` không được trigger tiền:** buyer `CONFIRM_CLEAN` → milestone `SETTLED` → contract-service publish `milestone.settled`; đây là nguồn trigger tiền DUY NHẤT. `milestone.buyer_confirmed` chỉ vào audit trail; mail trạng thái đi qua `notification.milestone_status_requested`. Escrow-service tuyệt đối không nghe event confirmed, tránh release hai lần.

**Cập nhật (04/07/2026) — payload `milestone.settled` mở rộng:** thêm `lockedAmount` (= `batchAmount` gốc đã khoá) và `actualAmount` (= số tiền thật sau pro-rata Delta 1/2, §4). `escrow-service` tự tính `diff = lockedAmount - actualAmount`, bắn 2 `LedgerEntry` cùng `milestoneId` nếu `diff > 0`: `RELEASE_TO_SELLER(actualAmount)` + `REFUND_TO_BUYER(diff)`. Không cần event/entryType mới ở bank-service — chỉ là payload trước đây chưa đủ chi tiết để escrow-service tự tính được. **Bổ sung contract notification (16/07/2026):** payload còn mang `recipients[{userId,email,role}]` để audit-service — vốn là pure consumer, không Feign ngược user-service — có thể publish `notification.milestone_anchor_requested` sau khi OTS proof sẵn sàng. Các consumer tiền/analytics bỏ qua field này theo tolerant-reader.

**Sửa (08/07/2026) — `actualAmount` phải áp cả tolerance split của Delta 2 (§4):** `contract-service` (không phải `escrow-service`) tính `actualAmount` cuối cùng, đã áp công thức chia tolerance khi Delta 2 vượt ngưỡng (§4) — `escrow-service` chỉ nhận số cuối và tính `diff` như cơ chế sẵn có, không tự tính lại tolerance split.

**Cập nhật (06/07/2026) — 2 event mới cho provisional settlement Level 2 (§3.2):**

| Loại | Tên | Publisher | Consumer(s) |
|---|---|---|---|
| RabbitMQ | `milestone.level2_provisional_settled` | contract-service — bắn khi hết `level2BufferWindowDays` mà report Level 2 chưa `CONFIRMED`, đã commission Level 1.5 fallback và settle tạm. Payload: `{milestoneId, provisionalAmount, bufferAmount, level1_5ReportId}` | escrow-service (release `provisionalAmount` cho seller, giữ khoá `bufferAmount`) |
| RabbitMQ | `milestone.level2_buffer_reconciled` | contract-service — bắn khi report Level 2 thật về sau đó (`CONFIRMED`), so sánh với số 1.5 đã dùng settle tạm. Payload: `{milestoneId, bufferAmount, finalAdjustment}` | escrow-service (release phần buffer còn lại theo `finalAdjustment`) |
| RabbitMQ | `milestone.level2_terminal_settled` (mới, 06/07/2026) | contract-service — bắn khi hết `level2BufferTerminalDays` mà report Level 2 vẫn chưa `CONFIRMED`, phán quyết Level 1.5 tự động thành chung thẩm. Payload: `{milestoneId, bufferAmount}` | escrow-service (release nốt `bufferAmount` còn khoá cho seller) |

### 7.2 Contract-level (mới, 04/07/2026; bổ sung `contract.signed` + consumer analytics-service, 06/07/2026)

| Loại | Tên | Publisher | Consumer(s) |
|---|---|---|---|
| RabbitMQ | `contract.signed` | contract-service — **mới (06/07/2026, phát sinh từ session `analytics-service`); payload mở rộng (08/07/2026, phát hiện qua rà end-to-end)**. Bắn đúng 1 lần khi `Contract.transitionTo(SIGNED)` xảy ra — trigger cụ thể là bước 4 của `VerifyOtpAndSign` (`signature-phase2-design.md` §6, bước 5 nối tiếp cũng publish event này, tách biệt với việc push `signedContentHash` riêng cho audit-service). Payload: `{contractId, commodity, buyerId, sellerId, totalAmount, buyerDepositAmount, sellerDepositAmount, signedAt}`. `commodity` là enum cứng `COFFEE / RICE / RUBBER / CASHEW`. contract-service đọc `Category.commodity` của category gắn với sản phẩm → publish thẳng (không có case `NULL` vì category chỉ dùng được khi `APPROVED` và `approve()` bắt buộc gán `commodity`). **Cơ chế `Category`/`commodity` (redesign Phase 1, vì sao bỏ bảng mapping, quan hệ 2 tầng) chốt ở `product-phase2-design.md` §9 (owner) — không mô tả lại ở đây.** | analytics-service (populate `dim_contract`, `analytics-service-phase2-design.md` §2.1/§3.1), **escrow-service (mới, 08/07/2026 — trigger lock, xem ghi chú payload ngay dưới)** |

**Sửa (08/07/2026) — payload thiếu dữ liệu cho chính consumer của nó:** bản 06/07/2026 chỉ mang `totalAmount`, không mang `buyerDepositRate`/`sellerDepositRate`. `escrow-service` **cũng** consume event này (để lock cọc lúc `SIGNED`, xem `escrow.deposit_locked` §7.2 ngay dưới) nhưng không có đường nào lấy được 2 rate đó — nó là *pure event consumer*, không Feign ngược theo đúng nguyên tắc đã chốt. Không tính nổi số tiền cần khoá. **Chốt:** mang thẳng `buyerDepositAmount`/`sellerDepositAmount` đã tính sẵn (= rate × `totalAmount`, tính ở contract-service — nơi có đủ `ContractTerms`) thay vì mang rate thô, để escrow-service không phải tự nhân và 2 service không lệch cách làm tròn số. `sellerDepositAmount = 0` nếu `sellerDepositRate` không đàm phán (giữ default) — escrow-service chỉ bắn thêm `LedgerEntry` cho seller deposit khi amount `> 0`. `analytics-service` dùng `totalAmount`, không cần 2 field mới này.
| RabbitMQ | `contract.settled` | contract-service (`ContractSettledEvent`, đã có sẵn trong code Phase 1 — chỉ thêm consumer mới, cần guard fix §3.1) | escrow-service (`REFUND_TO_BUYER` cho `buyerDepositRate`, §6.3), reputation-service (input positive — đã dùng ở `reputation-service-phase2-design.md` §3), **analytics-service (mới, 06/07/2026 — `fact_contract_settlement`, `analytics-service-phase2-design.md` §3.3)** |
| RabbitMQ | `contract.cancelled` | contract-service — **mới**, xem §6.3 | escrow-service (seize/refund `buyerDepositRate` theo `initiatedBy`), **analytics-service (mới, 06/07/2026 — `fact_contract_cancellation`, `analytics-service-phase2-design.md` §3.3)** |
| RabbitMQ | `escrow.deposit_locked` (mới, 08/07/2026) | escrow-service — kế thừa `escrow.buyer_locked` Phase 1, mở rộng cho cả 2 cọc. Bắn khi **cả 2 khoản cọc cần khoá** đã confirm xong với bank-service (`bank.lock_completed`, `bank-service-phase2-design.md` §3) — nếu `sellerDepositAmount = 0` thì chỉ chờ xác nhận khoá cọc buyer. Payload: `{contractId, buyerDepositState, sellerDepositState}`. | contract-service (`transitionTo(ACTIVE)` — xem ghi chú dưới), escrow-service tự dùng làm tín hiệu nối tiếp lock `batchAmount` milestone đầu tiên (§6.2) |

**Sửa (08/07/2026) — event kích hoạt ACTIVE biến mất khỏi Event Catalog:** luồng ký→kích hoạt mô tả *"contract-service nhận xác nhận cọc đã khoá → Contract `ACTIVE`"* nhưng không có event nào đi chiều escrow→contract để mang xác nhận này — Phase 1 có `escrow.buyer_locked` làm việc đó, rà lại thấy nó biến mất khỏi catalog Phase 2. Hệ quả kép trước khi sửa: (1) contract-service không có tín hiệu để chuyển `ACTIVE`; (2) escrow-service không có tín hiệu để lock `batchAmount` milestone đầu tiên (§6.2 ghi trigger *"Contract `ACTIVE`"* nhưng "Contract ACTIVE" không phát event nào). `escrow.deposit_locked` đóng cả 2 vai — contract-service consume để chuyển `ACTIVE`; escrow-service tự nối tiếp lock `batchAmount` milestone index 1 ngay sau khi chính nó xác nhận deposit-locked (đã ở đúng ngữ cảnh, không cần round-trip thêm qua contract-service).

**Notification commands (16/07/2026):** các event nghiệp vụ ở §7 giữ payload/domain consumer hiện có. Khi cần mail giao dịch, contract-service publish thêm `notification.contract_cancelled_requested` hoặc `notification.milestone_status_requested` với recipient email + dữ liệu template. Riêng email quyết toán có OTS không gửi trực tiếp từ `milestone.settled`; audit-service publish `notification.milestone_anchor_requested` sau khi ghi chain và lấy proof (§4.3 hash-chain). `contract.delivered` không được phục hồi.

---

## 8. Config: `application.yml` vs `ContractTerms`

| Setting | Nơi lưu | Lý do |
|---|---|---|
| Escalation cap của force majeure = Level 1.5 (không Level 2) | `application.yml` | Invariant kỹ thuật — sai chuyên môn nếu lên Level 2, không nên để buyer/seller tự thoả thuận khác mỗi hợp đồng |
| `forceMajeureReportWindowDays` | `ContractTerms` (per-contract) | Độ nhạy cảm thời gian khác nhau theo mặt hàng (cà phê khô lâu hơn rau quả tươi) |
| `shortfallPenaltyThreshold`, `toleranceRate`, `sellerPenaltyRate` | `ContractTerms` (per-contract) | Negotiate theo từng hợp đồng, giống pattern `penaltyRate` Phase 1. **Guardrail (mới, 08/07/2026):** validate range lúc `sign()` — `shortfallPenaltyThreshold` ∈ [3%,15%], `toleranceRate` ∈ [0%,10%], `sellerPenaltyRate`/`buyerPenaltyRate` ∈ [0%,30%] — xem §2.1. |
| Ngưỡng giá trị/loại hàng kích hoạt Level 1.5 vs Level 2 cho dispute thường (quantity/quality) | `application.yml` | Đã quyết từ Doc2 mục 4.3 — per-deployment config |
| `sellerResponseWindowDays` (mặc định 2 ngày làm việc) | `application.yml` | **Chính thức hoá (04/07/2026):** trước đây chỉ ghi "2 ngày làm việc" trong prose ở §3.2, chưa đặt tên config. Invariant kỹ thuật — không có lý do hợp đồng khác nhau cần cửa sổ phản hồi khác nhau. |
| `buyerConfirmWindowDays` (mặc định 2 ngày làm việc) | `application.yml` | **Mới (04/07/2026):** đối xứng với `sellerResponseWindowDays` — buyer im lặng ở `BUYER_RECEIVED` quá thời gian này → auto `CONFIRM_CLEAN` (§3.2). |
| `buyerReceiveWindowDays` (mặc định 2 ngày làm việc) | `application.yml` | **Mới (08/07/2026):** vá lỗ hổng `SELLER_WEIGHED` không timeout (§3.2) — hết cửa sổ này mà buyer chưa cân nhận → notify, im lặng tiếp → Admin/Level 1 quyết theo bằng chứng hiện có, **không** auto-settle theo số seller tự khai. Invariant kỹ thuật, không cần khác nhau theo hợp đồng. |
| `graceDays` (giao hàng, per-`MilestoneTerm`) | `ContractTerms`/`MilestoneTerm` (per-contract) | **Mới (08/07/2026), chốt:** số ngày ân hạn sau `expectedDeliveryDate` trước khi coi seller quá hạn (§2.1, §3.2). Để per-contract vì độ nhạy cảm thời gian khác nhau theo mặt hàng (cà phê khô lâu hơn rau quả tươi) — cùng lý do `forceMajeureReportWindowDays` đã để per-contract. |
| `level2BufferWindowDays` (10 ngày làm việc) | `application.yml` | **Chốt (13/07/2026):** neo theo benchmark lab test chất lượng chuẩn SCA (5 ngày làm việc/mẫu, `[REFERENCE]`) + buffer lịch hẹn/vận chuyển mẫu. Hết window mà chưa có report `CONFIRMED` → commission Level 1.5 fallback, settle tạm theo mechanics 3 bước (§3.2). Tinh chỉnh được khi có dữ liệu vận hành thật. |
| `level2SafetyBufferRate` (15%) | `application.yml` | **Chốt (13/07/2026):** % `batchAmount` giữ khoá khi settle tạm theo Level 1.5, chờ report Level 2 thật đối chiếu (§3.2). Chọn mức trên của dải cân nhắc (10-15%), nghiêng bảo vệ buyer — bên gánh rủi ro nếu Level 2 lật. Tinh chỉnh được khi có variance vận hành thật. |
| `level2BufferTerminalDays` (30 ngày) | `application.yml` | **Chốt (13/07/2026):** hết hạn tính từ lúc `level2BufferWindowDays` hết mà vẫn chưa có report `CONFIRMED` → phán quyết Level 1.5 tự động thành chung thẩm, release nốt buffer (§3.2). Điểm dừng cứng chống kẹt buffer vô thời hạn. |
| `sellerDepositRate` (mặc định `0`, optional) | `ContractTerms` (per-contract) | **Mới (06/07/2026):** thay quyết định "bỏ hẳn" trước đây — đàm phán per-contract giữa buyer/seller lúc `NEGOTIATING`, không phải invariant kỹ thuật (§2.1, §6.1). |
| `disputeFloorReleaseRate` (mặc định `50%`, optional) | `ContractTerms` (per-contract) | **Mới (10/07/2026):** tỷ lệ **sàn** release provisional Level 2 khi buyer từ chối/im lặng opt-out (§3.2 Bước 0/1). Đàm phán per-contract, guardrail `[50%, (1 − level2SafetyBufferRate)]` (§2.1). Để per-contract vì mức chấp nhận rủi ro của từng buyer khác nhau — cùng triết lý neutral-party với `sellerDepositRate`. |
| `disputeOptOutWindowDays` (mặc định 2 ngày làm việc) | `application.yml` | **Mới (10/07/2026):** cửa sổ buyer trả lời opt-out ở Bước 0 provisional Level 2 (§3.2). Hết cửa sổ, buyer im lặng → default **sàn** `disputeFloorReleaseRate` (nghiêng bảo vệ buyer, ngược với `buyerConfirmWindowDays` default `CONFIRM_CLEAN` — lý do ở §3.2). Invariant kỹ thuật, không cần khác theo hợp đồng. |

---

## 9. Status — Milestone Escrow Design

**Chốt (02/07/2026):** `shortfallPenaltyThreshold` = 5%. `forceMajeureReportWindowDays` = 3 ngày. Seller có quyền contest REJECT, đối xứng với buyer, cùng cap Level 1.5. `Milestone` = aggregate riêng. `dispute()` cấp Contract bị bỏ. `cancel()` cấp Contract = pro-rata + penalty debt + lockout, áp cho cả seller lẫn buyer (§6). `buyerDepositRate` = 5% `totalAmount`, lock lúc `SIGNED`. Event catalog theo convention `escrow.buyer_locked` đã có. công thức `lockDurationDays` + baseline chốt ở `reputation-service-phase2-design.md` §4 (owner), escrow chỉ giữ input/trigger. `batchAmount` lock sớm — ngay khi milestone trước `SETTLED`.

**Chốt bổ sung (04/07/2026):**
- **Local Outbox Pattern thay Spring `ApplicationEvent`** cho đồng bộ `Milestone` → `Contract` (§2.2) — sửa đúng bug crash-window đã phát hiện, giữ nguyên tinh thần local/không RabbitMQ.
- **Guard `Contract.settle()` cần fix** để chạy được từ `ACTIVE` thay vì chỉ `DELIVERED` (§3.1) — kèm dọn dead path `confirmDelivery()`/`ContractDeliveredEvent` khi implement. Phát hiện qua đối chiếu chéo với `reputation-service-phase2-design.md` KI-1.
- **Buyer timeout ở `BUYER_RECEIVED`** — `buyerConfirmWindowDays` = 2 ngày làm việc, mặc định `CONFIRM_CLEAN` nếu buyer im lặng (§3.2, §8).
- **`buyerDepositRate` — 3 đường release/seize chính thức hoá** qua 2 event cấp Contract mới thêm vào catalog: `contract.settled` (event có sẵn, thêm consumer escrow-service) và `contract.cancelled` (event hoàn toàn mới) (§6.3, §7.2).
- **Payload `milestone.settled` mở rộng** thêm `lockedAmount`/`actualAmount` để escrow-service tự tách `RELEASE_TO_SELLER` + `REFUND_TO_BUYER` khi Delta 1/2 pro-rata làm số tiền thực nhận thấp hơn số đã khoá (§7.1).

**Chỉ còn 1 điểm để ngoài phạm vi có chủ đích, không phải thiếu sót:** state machine đầy đủ của `reputation-service` ngoài phần input/trigger đã chốt — thiết kế chi tiết nằm ở `reputation-service-phase2-design.md` (session riêng, 04/07/2026).

**Chốt bổ sung (06/07/2026) — provisional settlement khi `CONTESTED` escalate Level 2 (§3.2, §7.1, §8):** thêm cơ chế buffer để giải quyết bottleneck report Level 2 chậm (email/admin-confirm, `inspection-phase2-design.md` §3) mà không kẹt tiền seller vô thời hạn. Hết `level2BufferWindowDays` (10 ngày làm việc) chưa có report → commission Level 1.5 fallback, settle tạm `(1 − level2SafetyBufferRate)` của `batchAmount`, giữ khoá phần còn lại chờ đối chiếu với report thật. Dùng buffer khoá sẵn trong escrow thay vì ghi debt, vì seller không có tài sản đối ứng để đòi. `level2BufferTerminalDays` (30 ngày) đóng câu hỏi buffer xử lý sao nếu report Level 2 không bao giờ về — hết hạn thì phán quyết Level 1.5 tự động thành chung thẩm, release nốt buffer. **Cả 3 số chốt ở §8** (neo benchmark, cấu hình `application.yml`).

**Chốt bổ sung (06/07/2026) — `sellerDepositRate` optional, thay quyết định "bỏ hẳn" (§2.1, §2.3, §6.1, §6.2, §6.3):** seller cancel ở 0 milestone hoàn thành trước đây không có gì để seize (đúng cái giá của quyết định bỏ seller deposit vì viability HTX). Giải pháp: không bắt buộc, để buyer/seller tự đàm phán `sellerDepositRate > 0` nếu buyer muốn — cùng triết lý neutral-party đã dùng cho `verificationLevel`/`geoRiskLevel` (platform cấp công cụ, không áp luật chung). Mặc định vẫn `0`, giữ nguyên hiện trạng cho ai không cần. Tái dùng nguyên schema/event/entryType đã có cho `buyerDepositRate` (không cần entry mới ở `bank-service`). **Chốt (08/07/2026):** `lockDurationDays` nặng hơn cho case cancel-ở-0-milestone — **áp dụng** qua `zeroProgressMultiplier` (1.5x), ký xong bỏ ngay là tín hiệu xấu nhất + là lớp ma sát cho disintermediation risk (§6.1).

**Chốt bổ sung (08/07/2026) — rà soát end-to-end toàn bộ Phase 2, 7 điểm PHẢI SỬA đã áp dụng vào file này:**
- **A1** — payload `contract.signed` mở rộng thêm `buyerDepositAmount`/`sellerDepositAmount`, thêm escrow-service làm consumer (§7.2).
- **A2** — event `escrow.deposit_locked` (chiều escrow→contract) thêm lại vào catalog — tín hiệu duy nhất để `Contract` chuyển `ACTIVE` và để escrow-service lock `batchAmount` milestone đầu (§7.2).
- **A3** — mechanics tiền đầy đủ cho Provisional Settlement Level 2, viết lại thành 3 bước rõ ràng (Provisional/Reconcile/Terminal), sửa lỗi "release nốt bufferAmount cho seller" (không tách được phần thuộc buyer) (§3.2, §2.3). `level2BufferWindowDays` điều chỉnh 7-14 → 5-10 ngày, neo theo benchmark SCA (§8).
- **A4** — thêm `expectedDeliveryDate`/`graceDays` vào `MilestoneTerm` (lỗ hổng gốc: không định nghĩa được "giao trễ"); thêm timeout ở `SELLER_WEIGHED` (đẩy Admin/Level 1, không auto-settle theo số seller tự khai) và `IN_PROGRESS` quá hạn (buyer trigger được, seller còn cửa force majeure) (§2.1, §3.2, §8).
- **B1** — Delta 2 thêm công thức chia `toleranceRate` cho phần vượt ngưỡng — trước đây seller gánh 100% hao mòn, mâu thuẫn với Doc02 (§4). **Chốt: chia phần vượt ngưỡng, chia trên khối lượng** (lý do ở §4).
- **B2** — bỏ escrow-service khỏi consumer của `milestone.buyer_confirmed` — event thừa, nguy cơ release tiền 2 lần cho cùng milestone (§7.1).
- **B3** — thêm guardrail range cho `toleranceRate` [0%,10%], `shortfallPenaltyThreshold` [3%,15%], `buyerPenaltyRate`/`sellerPenaltyRate` [0%,30%] — vá lỗ buyer ép điều khoản về 0% (§2.1, §8).
- **B5** — định nghĩa rõ `SIGNED` (mốc chữ ký) tách khỏi `ACTIVE` (mốc chữ ký + tiền), thêm nhánh xử lý khi lock cọc fail (§3.1).

**Chốt bổ sung (10/07/2026) — buyer opt-out cho provisional settlement Level 2 (§2.1, §3.2 Bước 0, §8):** trước bản này provisional release chạy tự động ~85%, buyer (bên gánh rủi ro nếu Level 2 lật, tiền không đòi được từ HTX) không được quyết. Thêm opt-out **2 mức** (không nhị phân xả-hết/khoá-cứng): buyer đồng ý → trần `(1 − level2SafetyBufferRate)`; buyer từ chối/im lặng → sàn `disputeFloorReleaseRate` (≥50%, guardrail, seller vẫn nhận ≥ nửa, không kẹt vốn tới terminal). Default im lặng = sàn (nghiêng bảo vệ buyer — ngược `BUYER_RECEIVED` vì ngữ cảnh tranh chấp + biến động khác hẳn). Mechanics 3 bước cũ giữ nguyên, chỉ tham số hoá `(1−rate) → relRate`. `disputeFloorReleaseRate` guardrail `[50%, (1−rate)]` — thương lượng có kẹp biên, chống buyer ép nhưng không thả tự do. Rủi ro còn lại: mức sàn vẫn đẩy chút rủi ro sang buyer kể cả khi từ chối (không về 0) — cái giá của việc không giết seller, ghi honest. **Không đụng** `level2SafetyBufferRate` (chốt 15%, §8) — opt-out là lớp quyết định cấp buyer *phía trên* buffer, không thay công thức buffer.

Milestone Escrow — **ĐÓNG SESSION HOÀN TOÀN, sẵn sàng đưa vào Architecture/SDS/TechnicalSpec chính thức.** Provisional settlement Level 2: cả 3 số cấu hình (`level2BufferWindowDays` = 10 ngày, `level2SafetyBufferRate` = 15%, `level2BufferTerminalDays` = 30 ngày) đã chốt theo benchmark ngành ở §8, cấu hình `application.yml` tinh chỉnh được khi có dữ liệu vận hành thật. **Đã chốt hết các quyết định thiết kế:** Delta 2 chia phần-vượt/khối-lượng (B1), `graceDays` per-contract (A4), guardrail range (B3), `lockDurationDays` nặng hơn cho cancel-ở-0-milestone **áp dụng** (§6.1 — ký xong bỏ ngay là tín hiệu xấu nhất). **`contract.signed` mapping `commodity`:** đã đóng — `commodity` đọc thẳng từ `Category.commodity`; cơ chế chốt ở `product-phase2-design.md` §9 (owner).

---

*Design session: 02/07/2026 · Cập nhật: 04/07/2026 (Local Outbox, buyer timeout, buyerDepositRate release paths) · Cập nhật: 06/07/2026 (thêm event `contract.signed` cấp Contract + đăng ký analytics-service làm consumer của `contract.settled`/`contract.cancelled`, phát sinh từ rà soát chéo `analytics-service-phase2-design.md`; thêm provisional settlement + buffer cho Level 2 chậm report, §3.2/§7.1/§8; thêm chốt hạn cứng `level2BufferTerminalDays`; thêm `sellerDepositRate` optional thay quyết định "bỏ hẳn", §2.1/§2.3/§6.1/§6.2/§6.3 — chưa đóng, còn các điểm treo) · Cập nhật: 08/07/2026 (rà soát end-to-end: payload `contract.signed` mở rộng + event `escrow.deposit_locked` thêm lại — A1/A2; mechanics đầy đủ Provisional Settlement, sửa lỗi bufferAmount — A3; `expectedDeliveryDate`/`graceDays` + 2 timeout mới `SELLER_WEIGHED`/`IN_PROGRESS` — A4; Delta 2 chia tolerance thay vì seller gánh 100% — B1; bỏ escrow-service khỏi consumer `milestone.buyer_confirmed` — B2; guardrail range cho tolerance/threshold/penalty rate — B3; định nghĩa rõ SIGNED vs ACTIVE — B5) · Cập nhật: 13/07/2026 (chốt 3 số cấu hình Level 2 buffer theo benchmark, gỡ toàn bộ trạng thái placeholder — đóng session hoàn toàn) · Chưa code · **Sẵn sàng đưa vào Architecture/SDS/TechnicalSpec chính thức.***
