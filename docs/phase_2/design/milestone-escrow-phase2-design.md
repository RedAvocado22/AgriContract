---
name: milestone-escrow-phase2-design
description: "Milestone Escrow — full domain design cho Phase 2, thay thế two-phase lock escrow đang chạy ở Phase 1. Nguồn: design session 02/07/2026, cập nhật 04/07/2026, 06/07/2026 (event contract.signed; provisional settlement Level 2), 08/07/2026 (rà soát end-to-end), 19/07/2026 (viết lại attribution/termination: taxonomy 6 hành vi, BreachCase, Rổ A/B, MUTUAL_REPLACEMENT/supersede, milestone funding failure §6b, LegalProfile cap 8% §2.1b, remedyType tách)."
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

**Thay đổi lớn nhất so với Phase 1:** bỏ seller deposit **bắt buộc**. HTX không có tiền mặt ứng trước khi đang nợ phân bón — viability constraint thật, không phải lựa chọn thiết kế (context từ Cường). *(06/07: quyết định "bỏ hẳn" đổi thành **optional per-contract** — `sellerDepositRate` §2.1, mặc định 0; đoạn này giữ làm lý do gốc vì sao không bắt buộc.)*

---

## 2. Domain Model Changes

### 2.1 `ContractTerms` (Value Object — cập nhật)

| Field | Loại | Ghi chú |
|---|---|---|
| `milestoneSchedule` | `List<MilestoneTerm>` (nested VO) | N batch tự do — buyer/seller tự thoả thuận số lượng batch, không cố định. Snapshot immutable lúc `sign()`, giống `agreedPrice`. |
| `toleranceRate` | BigDecimal | Ngưỡng lệch cân chấp nhận được cho Delta 2 (hao mòn vận chuyển). Mặc định 50/50 chia trách nhiệm nếu vượt ngưỡng — kế thừa nguyên bản Doc2 mục 4.2. **Guardrail (mới, 08/07/2026): validate range `[0%, 10%]` lúc `sign()` — xem ghi chú dưới bảng.** |
| `shortfallPenaltyThreshold` | BigDecimal | Ngưỡng % thiếu hàng (Delta 1) mà dưới đó chỉ pro-rata, từ đó trở lên tính penalty. **Chốt: 5%.** (Giá trị mặc định — vẫn negotiate được per-contract giống các field khác trong `ContractTerms`.) **Guardrail (mới, 08/07/2026): validate range `[3%, 15%]` lúc `sign()` — xem ghi chú dưới bảng.** |
| `buyerPenaltyRate` / `sellerPenaltyRate` | BigDecimal | **Là _phạt vi phạm_ theo Luật Thương mại 2005 Điều 300–301 — KHÔNG phải cọc, KHÔNG phải bồi thường thiệt hại.** Tái dùng cho nhánh Delta 1 penalty. **Guardrail sửa (19/07/2026): validate range `[0%, maxContractualPenaltyRate]` lúc `sign()`, mặc định trần `8%` khi `governingLaw = VN_COMMERCIAL_LAW` — xem `LegalProfile` §2.1b và ghi chú cap dưới bảng. Range `[0%, 30%]` cũ (08/07) SAI LUẬT: LTM Điều 301 cap phạt vi phạm ở 8% _giá trị phần nghĩa vụ bị vi phạm_; thực tiễn xét xử VN + trọng tài VIAC tự cắt phần vượt 8% về 8% khi có tranh chấp, nên `30%` là số ảo không defend được.** |
| `forceMajeureReportWindowDays` | Integer | Số ngày seller phải báo bất khả kháng kể từ lúc **biết sự kiện** (không neo theo ngày giao). **Chốt: 3 ngày.** |
| `buyerDepositRate` | BigDecimal | Cọc nhỏ của buyer — **Chốt: 5%** `totalAmount`. Lock **một lần duy nhất lúc `SIGNED`**, giữ xuyên suốt tới `SETTLED` cuối cùng. Vai trò "skin in the game", **không phải** T/T-style cọc cover rủi ro tài chính (đã bác bỏ ở `decisions.md` [2026-06-16]) — đó là việc của `batchAmount` lock riêng từng milestone (§6). 3 đường release/seize của field này — §6.7. |
| `disputeFloorReleaseRate` | BigDecimal, nullable/default `0.50` | **Mới (10/07/2026).** Tỷ lệ **sàn** release cho seller khi provisional settle Level 2 mà buyer từ chối/im lặng ở bước opt-out (§3.2 Bước 1). Đàm phán per-contract, guardrail `[50%, (1 − level2SafetyBufferRate)]` — sàn không được vượt trần `(1 − rate)`, nếu không "từ chối" lại release NHIỀU hơn "đồng ý" (vô nghĩa). Buyer (bên mạnh hơn) không kéo được xuống dưới 50% → seller HTX vẫn nhận ≥ nửa, không kẹt vốn tới terminal. Cùng cơ chế guardrail đã dùng cho `shortfallPenaltyThreshold`/`toleranceRate` — thương lượng có kẹp biên, không thả tự do (chống buyer ép), không áp cứng (không phải hằng số tĩnh). |
| `sellerDepositRate` | BigDecimal, nullable/default `0` | **Mới (06/07/2026), thay thế quyết định "bỏ hẳn" trước đây.** Cọc của seller — **optional, đàm phán per-contract** giữa buyer/seller lúc `NEGOTIATING`, không phải invariant kỹ thuật ép buộc. Platform không quyết định seller có cần cọc hay không — để buyer tự đánh giá mức tin tưởng với seller cụ thể (seller mới/không track record → buyer có động lực đòi cọc; seller uy tín cao → 2 bên có thể thoả thuận `0`, quay về đúng hiện trạng trước đây). Cùng triết lý neutral-party đã dùng cho `verificationLevel`/`geoRiskLevel` (`product-phase2-design.md`) — platform cấp công cụ, không áp luật chung. Lock cùng thời điểm với `buyerDepositRate` (lúc `SIGNED`). Vá đúng lỗ "seller cancel ở 0 milestone hoàn thành, không có gì để seize" — xem §6.3.1. |

**Thêm (08/07/2026) — guardrail cho các field negotiate tự do, chưa có validate range:** `toleranceRate` và `shortfallPenaltyThreshold` (cùng `buyerPenaltyRate`/`sellerPenaltyRate`) nằm trong `ContractTerms`, negotiate tự do per-contract, nhưng trước bản này không có validate range ở đâu. Buyer (bên mạnh hơn — Doc01 §2.3 là luận điểm gốc của cả dự án) hoàn toàn có thể ép `shortfallPenaltyThreshold = 0%` (seller thiếu 1 gram cũng dính penalty) hoặc `toleranceRate = 0%` (seller gánh 100% hao mòn ngay từ gram đầu) — thiết kế sinh ra để chống bất đối xứng quyền lực lại để hở đúng cơ chế đó.

**Chốt guardrail — validate ở use case `Sign`, ngoài range → reject, yêu cầu điều chỉnh trước khi ký:**
- `shortfallPenaltyThreshold` ∈ **[3%, 15%]**, default 5%. (Dưới 3% bất khả thi thực tế cho hao mòn nông sản; trên 15% seller được nuông quá mức.)
- `toleranceRate` ∈ **[0%, 10%]**, default 5%. (0% hợp lệ nếu 2 bên thực sự muốn, cảnh báo UI khi = 0.)
- `sellerPenaltyRate`/`buyerPenaltyRate` ∈ **[0%, `maxContractualPenaltyRate`]** (**sửa 19/07/2026**, trước là `[0%, 30%]` — sai luật). Trần lấy từ `LegalProfile` (§2.1b): mặc định **8%** với `governingLaw = VN_COMMERCIAL_LAW`. Đây là _phạt vi phạm_ (LTM 301), không phải cọc/bồi thường. penalty quá cao vẫn thành công cụ ép, nhưng lý do cứng để kẹp trần là **luật định**, không phải chỉ "hợp lý về thiết kế".
- `disputeFloorReleaseRate` ∈ **[50%, (1 − `level2SafetyBufferRate`)]**, default 50% (mới, 10/07/2026). Sàn ≥ 50% để buyer không kéo release provisional về quá thấp làm kẹt vốn seller; trần = `(1 − level2SafetyBufferRate)` để sàn (lúc buyer từ chối) không vượt mức trần (lúc buyer đồng ý). Xem §3.2 Bước 1.

### 2.1b `LegalProfile` (Value Object — mới, 19/07/2026)

**Bối cảnh:** cơ chế phạt của Phase 2 trước đây trộn ba chế tài pháp lý khác nhau vào một tỷ lệ `%` duy nhất, và đặt trần `30%` không đứng được trước luật Việt Nam. Ba chế tài phải **tách bạch** vì trần và bản chất pháp lý của chúng khác nhau:

| Chế tài | Căn cứ | Trần | Ánh xạ trong hệ |
|---|---|---|---|
| **Đặt cọc** | BLDS 2015 Điều 328 | **KHÔNG cap 8%** — mức do 2 bên thoả thuận | `buyerDepositRate`, `sellerDepositRate`; ledger `DEPOSIT_FORFEITURE` (§6.7 / bank-service) |
| **Phạt vi phạm** | LTM 2005 Điều 300–301 | **≤ 8% giá trị _phần nghĩa vụ bị vi phạm_** (không phải tổng hợp đồng) | `buyerPenaltyRate`, `sellerPenaltyRate`; ledger `CONTRACTUAL_PENALTY` |
| **Phạt kết luận giám định sai** | LTM 2005 Điều 266 | **≤ 10× thù lao dịch vụ giám định** (ngoại lệ riêng, không dính 8%) | phạt INSPECTOR Level 1.5 sai — `inspection-phase2-design.md` (owner) |
| **Bồi thường thiệt hại** | LTM 2005 Điều 302 | **KHÔNG cap**, nhưng **phải chứng minh thiệt hại thực tế** — không áp bằng `%` cố định | ledger `DAMAGES_COMPENSATION`; chỉ ghi khi có bằng chứng thiệt hại, không tự sinh từ tỷ lệ |

`LegalProfile` là VO snapshot immutable trong `ContractTerms` lúc `sign()`:

| Field | Loại | Ghi chú |
|---|---|---|
| `governingLaw` | Enum | `VN_COMMERCIAL_LAW` (mặc định — buyer/seller đều là pháp nhân thương mại, đúng phạm vi điều chỉnh LTM 2005) \| `VN_CIVIL_LAW` (dự phòng nếu 1 bên không phải thương nhân — BLDS Điều 418 không cap % phạt) |
| `contractType` | Enum | Loại hợp đồng, để mở rộng ngoại lệ (vd giám định Điều 266) khi cần |
| `maxContractualPenaltyRate` | BigDecimal | Trần phạt vi phạm suy ra từ `governingLaw`: `8%` với `VN_COMMERCIAL_LAW`. Dùng validate `buyerPenaltyRate`/`sellerPenaltyRate` lúc `sign()`. |
| `damagesPolicy` | Enum (**sửa 19/07/2026 lần 2 — thay boolean `penaltyAndDamagesCumulative`, citation cũ Điều 316 SAI, đúng là Điều 307**) | Quan hệ phạt↔bồi thường **khác default theo governingLaw**, một boolean chung là sai luật ở một trong hai nhánh: **LTM 2005 Điều 307** — có thoả thuận phạt thì bên bị vi phạm được áp **cả** phạt lẫn bồi thường (bồi thường vẫn phải chứng minh), KHÔNG cần thoả thuận cộng dồn riêng; **BLDS Điều 418.3** — ngược lại, chỉ thoả thuận phạt mà không ghi "vừa phạt vừa bồi thường" thì mặc định **chỉ phạt**. Giá trị: `COMMERCIAL_CUMULATIVE_IF_PROVEN` (default khi `governingLaw = VN_COMMERCIAL_LAW`) \| `CIVIL_PENALTY_ONLY_UNLESS_EXPRESSLY_CUMULATIVE` (default khi `VN_CIVIL_LAW`) \| `EXPRESS_PENALTY_ONLY` (hai bên chủ động chốt chỉ-phạt). |

**Vì sao cap `8%` tính trên _phần nghĩa vụ bị vi phạm_, không phải tổng hợp đồng:** LTM Điều 301 ghi rõ "8% giá trị **phần nghĩa vụ hợp đồng bị vi phạm**". Trong Milestone Escrow, cancel/breach đã là **pro-rata trên các milestone chưa `SETTLED`** (§6) — nên cơ sở tính phạt vốn đã là "phần bị vi phạm" (tổng `batchAmount` các milestone còn lại / milestone bị breach), không phải `totalAmount`. Điều này **khớp sẵn** với cơ chế pro-rata, chỉ cần đảm bảo `CONTRACTUAL_PENALTY = penaltyRate × (batchAmount phần bị vi phạm)`, không nhân với tổng hợp đồng.

> **⚠ CẦN LUẬT SƯ XÁC NHẬN (Known Limitation, không hardcode như chân lý):** việc phân loại `buyerDepositRate`/`sellerDepositRate` là **"đặt cọc" theo Điều 328** (thoát cap 8%) dựa trên _tên gọi và ý đồ thiết kế_, chưa được luật sư/trọng tài xác nhận. Cơ chế "khoá 5% lúc ký, mất khi bên đó huỷ" **có thể** bị toà tái phân loại thành _phạt vi phạm trá hình_ và áp cap 8% ngược lại nếu cấu trúc thực tế không khớp bản chất đặt cọc (giao một khoản bảo đảm giao kết/thực hiện). Ghi rõ là điểm cần primary validation với luật sư trước khi productize — đúng tinh thần kỷ luật "công thức trước, xác nhận sau" của cả dự án.

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
    outbox_id       CHAR(36) PRIMARY KEY,
    milestone_id    CHAR(36) NOT NULL,
    contract_id     CHAR(36) NOT NULL,
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
3. Nếu `true` → gọi `Contract.completeAllMilestones()` trong transaction riêng của `Contract`. Method này **không** commit `SETTLED` ngay: nó persist no-fault `AttributionDecision` + normal-completion `RemedyDecision`, ghi `remedy.finalized` vào outbox, rồi để Contract ở `ACTIVE` trong khi escrow/bank thực thi các owner-return legs (§3.1, §6.7).
4. Đánh dấu `processed = true` **dù kết quả check ở bước 2 là gì** — bản thân việc "đã kiểm tra cho đúng milestone này" là xong nhiệm vụ của row đó. Milestone thật sự cuối cùng sẽ có row riêng của nó tự bắt được điều kiện `== 0`. Một completion reconciler idempotent sau đó dùng read contract đã có ở `GET /internal/v1/bank/ledger?contractId=...` để chỉ commit `SETTLED` và publish `contract.settled` khi mọi `remedyLegId` của decision đã có ledger result thành công và tổng remaining lock của contract bằng `0.00`.
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

**State cấp Contract (song song 2 field, độc lập nhau — chỉ có giá trị khi rate tương ứng > 0):** `buyerDepositState` (`DEPOSIT_LOCKED`/`DEPOSIT_RELEASED`/`DEPOSIT_SEIZED`) và `sellerDepositState` (cùng 3 giá trị, mới 06/07/2026) — 2 field tách biệt vì 2 khoản cọc release/seize độc lập nhau theo bảng ở §6.3.1/§6.3.2, không phải cùng 1 lifecycle.

**Lưu ý (04/07/2026, xem `bank-service-phase2-design.md` §5):** `EscrowAccount`/`EscrowMilestone` chỉ giữ **state** (`LOCKED`/`RELEASED`/`PENALIZED`...), không tự lưu lại 1 con số tiền riêng phải đồng bộ tay với bank-service — số tiền thật là single source of truth ở `ledger_entry` bên bank-service. Giữ thêm số tiền ở đây là dual-write, đúng lỗi đã học ở Phase 1.

**Cập nhật (08/07/2026, contract freeze 18/07/2026) — thêm state cho Provisional Settlement (§3.2):** `EscrowMilestone.status` thêm giá trị `PROVISIONALLY_RELEASED` (giữa `LOCKED` và `SETTLED`/`RELEASED` — dùng khi milestone đã release tạm theo phán quyết Level 1.5 nhưng còn giữ tiền chờ Level 2). Snapshot được đổi tên thành `remainingLockedAmount` (Money, nullable) để khớp explicit event legs; đây là immutable decision/conservation snapshot, không phải competing balance.

---

## 3. State Machines

### 3.1 Contract-level (cập nhật)

```
OFFERED → NEGOTIATING → SIGNED → ACTIVE
    │          │
    └──────────┴──────────────→ WITHDRAWN (WITHDRAW_OFFER)
                                    │
                    (N milestone chạy song song/tuần tự bên trong)
                                    │
                          Tất cả milestone SETTLED
                                    ↓
                 normal remedy + bank reconciliation = zero
                                    ↓
                                 SETTLED
```

`ACTIVE` giờ không chuyển thẳng `DELIVERED` như Phase 1 — nó ở `ACTIVE` xuyên suốt cho tới khi milestone cuối cùng settle. Chấm dứt/tranh chấp ở cấp Contract — chốt đầy đủ ở §6: `dispute()` bỏ hẳn (thay bằng milestone-level dispute §3.2/§5); `cancel()` một-nút **bị thay bằng termination taxonomy 6 hành vi** (§6.1 — sửa 19/07/2026), pro-rata trên phần milestone chưa `SETTLED` giữ nguyên làm nguyên tắc tiền cho mọi nhánh. Terminal states của Contract theo taxonomy: `SETTLED` · `TERMINATED` (breach/mutual/FM — phân biệt qua `terminationType`+`finalBreachingRole` trên event, §6.7) · `WITHDRAWN` (pre-signature `WITHDRAW_OFFER`) · `SUPERSEDED` (§6.6) · `ACTIVATION_FAILED` (§3.1 dưới). Non-terminal trung gian trên đường supersede: `REPLACEMENT_PENDING`, `SUPERSEDE_REFUND_PENDING` (§6.6 saga).

**Diễn giải bằng lời:** khác với Phase 1 (nơi cả hợp đồng chỉ có một điểm giao hàng duy nhất, `confirmDelivery()` một lần là xong), ở Milestone Escrow, `ACTIVE` là một trạng thái kéo dài — bên trong nó có N milestone chạy, mỗi milestone tự đi qua vòng đời riêng (chi tiết ở §3.2). Milestone cuối cùng `SETTLED` chỉ **khởi động** normal-completion remedy; Contract vẫn `ACTIVE` cho tới khi bank ledger chứng minh các contract-level deposits đã về đúng chủ và remaining lock bằng zero, rồi mới chuyển `SETTLED`. Trước chữ ký thứ hai, `OFFERED/NEGOTIATING` có thể chuyển sang terminal `WITHDRAWN` qua `WITHDRAW_OFFER`; đây là lifecycle-only và không có remedy leg. Nói cách khác, `SIGNED` vẫn giữ nguyên nghĩa (cả hai bên đã ký, điều khoản bất biến), nhưng "giao hàng xong" không còn là một sự kiện tức thời — nó là kết quả cộng dồn của nhiều sự kiện nhỏ.

**Cập nhật (04/07/2026; freeze completion guard 20/07/2026) — dọn dead path, phát hiện qua đối chiếu chéo với `reputation-service-phase2-design.md` KI-1:** `Contract.settle()` hiện tại (code Phase 1) chỉ chạy được từ status `DELIVERED` — di sản two-phase lock. `completeAllMilestones()` (§2.2) chạy khi Contract đang `ACTIVE` (không bao giờ đạt `DELIVERED` nữa dưới Milestone Escrow), nhưng Phase 2 tách method này thành hai bước idempotent: (1) tạo normal-completion decision/outbox; (2) `commitSettledAfterMoneyReconciliation()` chỉ cho phép `ACTIVE -> SETTLED` sau read reconciliation thành công. Kéo theo dead path cần dọn khi code: `confirmDelivery()`, `ContractDeliveredEvent`/`"contract.delivered"`, consumer tương ứng ở `escrow-service.ContractEventConsumer` và `notification-service.NotificationEventConsumer` — các thành phần này thuộc mô hình single-delivery-point Phase 1, không còn đường nào gọi tới dưới Milestone Escrow, cần xoá khi implement chứ không chỉ để đó thành code chết. `"contract.settled"` (`ContractSettledEvent`) là event lifecycle/positive-history; **không phải money trigger** và chỉ publish sau zero-lock confirmation.

**Producer rule bắt buộc cho normal completion:** khi milestone cuối cùng settle, contract-service persist một `AttributionDecision` hiện có với `breachCaseId = NULL`, `decisionSource = SYSTEM`, `finalBreachingRole = NULL`, `breachReasonCode = NULL`, và evidence refs trỏ tới tập milestone đã settle. Remedy calculator persist đúng một `RemedyDecision` với `affectedMilestoneIds = []`, `penaltyEligible = false`, `reputationEligible = false`; legs gồm `PAYMENT_REFUND/BUYER_DEPOSIT/BUYER` trả cọc buyer và, nếu `sellerDepositAmount > 0`, `PAYMENT_SETTLEMENT/SELLER_DEPOSIT/SELLER` trả cọc seller. Mỗi leg có `remedyLegId` ổn định và amount đúng số contract-level deposit còn lock. `remedy.finalized` được ghi outbox trước; `contract.settled` chỉ được ghi/publish sau reconciliation guard ở đoạn trên. Không có money consumer thứ hai trên `contract.settled`.

**Làm rõ (08/07/2026) — `SIGNED` ≠ mốc tiền, `ACTIVE` mới là mốc tiền:** `SIGNED` = mốc **chữ ký** — `transitionTo(SIGNED)` khi đủ 2 chữ ký hợp lệ, độc lập hoàn toàn với tiền (`signature-phase2-design.md`). `contract.signed` publish ngay tại đây là đúng — "hợp đồng đã được ký kết" là sự thật kể cả nếu bước lock cọc ngay sau đó thất bại. `ACTIVE` = đã ký **VÀ** cọc đã khoá thành công — đúng lúc `escrow.deposit_locked` (§7.2) về tới contract-service. Nếu lock cọc fail (bank-service trả `bank.lock_failed`, `bank-service-phase2-design.md` §3) → Contract kẹt ở `SIGNED`, chưa `ACTIVE`; **nhánh xử lý fail — chốt 17/07/2026 (trước đây để trống):**

1. escrow-service tự retry `bank.lock_requested` tối đa `depositLockRetryMaxAttempts` (chốt 3, backoff 5 phút / 30 phút / 2 giờ, `application.yml` — §8) — hứng lỗi tạm thời (bank restart, network). Idempotency giữ nguyên theo `sourceEventId` mỗi lần gửi.
2. **Retry theo LEG, không theo contract (sửa 18/07/2026 — đóng partial success):** buyer deposit và seller deposit là 2 lệnh lock độc lập; retry CHỈ leg đang fail, không tạo request mới cho leg đã `LOCKED`. Hết retry leg fail → escrow-service publish `escrow.deposit_lock_failed {contractId, failedLeg: BUYER|SELLER|BOTH, buyerDepositState, sellerDepositState, reason}` (§7.2). contract-service consume: Contract **giữ nguyên `SIGNED`** — chữ ký là sự thật đã xảy ra, không rollback; publish `notification.contract_activation_failed_requested` cho Buyer + Seller + Admin. **Không penalty, không đụng reputation** — không bên nào phá kèo; lỗi thuộc hạ tầng/platform, gồm cả case kill switch đang ACTIVE làm mọi `bank.*_requested` fail có chủ đích (bank §3.5.3).
3. Admin có 2 use case tường minh: `RetryDepositLock(contractId)` — kích lại chu kỳ lock **cho leg còn thiếu** sau khi nguyên nhân được xử lý (VD hệ thống được unlock); `MarkActivationFailed(contractId)` — bắt đầu terminal khi 2 bên xác nhận không tiếp tục, 2 bên tự ký hợp đồng mới nếu muốn.
   **Compensation cho leg đã LOCKED (sửa 18/07/2026 — bản 17/07 chuyển thẳng `ACTIVATION_FAILED` mà quên tiền buyer đang nằm trong lock):** `MarkActivationFailed` → với mọi leg đã `LOCKED`, gửi `bank.refund_requested` (compensating); `SIGNED → ACTIVATION_REFUND_PENDING` → chỉ khi mọi refund `confirmed` mới `→ ACTIVATION_FAILED` (terminal, không penalty/`lockDurationDays`). Refund fail → **đứng lại ở `ACTIVATION_REFUND_PENDING`** + alert Admin, không giả vờ đã kết thúc sạch; `LedgerAuditReconciliationJob` + invariant "contract terminal ⇒ tổng lock = 0" (verification matrix #8) là lưới bắt sót. **Không dùng timer tự động cho bước terminal** — huỷ 1 hợp đồng đã đủ 2 chữ ký cần người xác nhận, đúng nguyên tắc human-in-the-loop đã dùng cho evidence (inspection §3.6). Hệ quả cho tầng phân tích: `contract.signed` đo "đã ký", `contract.settled`/milestone events đo "đã vận hành thật" — 2 câu hỏi khác nhau, `analytics-service` không được lẫn 2 mốc này khi tính conversion rate hay tương tự.

### 3.2 Milestone-level (mới)

#### 3.2.0 Authoritative HTTP action catalog

The following paths are part of the authoritative contract surface:

- `POST /api/v1/contracts/{contractId}/milestones/{milestoneId}/weigh`
- `POST /api/v1/contracts/{contractId}/milestones/{milestoneId}/confirm`
- `POST /api/v1/contracts/{contractId}/milestones/{milestoneId}/flag`
- `POST /api/v1/contracts/{contractId}/milestones/{milestoneId}/respond`
- `POST /api/v1/contracts/{contractId}/milestones/{milestoneId}/force-majeure`
- `POST /api/v1/contracts/{contractId}/milestones/{milestoneId}/force-majeure/resolve`

Activation recovery is ADMIN-only and uses the same SDS error envelope and idempotent write policy:

- `POST /api/v1/contracts/{contractId}/activation/retry-deposit-lock`
- `POST /api/v1/contracts/{contractId}/activation/mark-failed`

The recovery actions have no caller-supplied business state; the service reads the current contract/escrow projection and applies the named use case.

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
- `IN_PROGRESS` quá hạn (`effectiveDeliveryDeadline` — sửa 19/07 sync §6b: = `expectedDeliveryDate + fundingDelayBusinessDays + graceDays`; milestone đầu fundingDelay = 0 nên tương đương công thức cũ) → nhánh seller-quá-hạn, buyer trigger được, seller còn cửa force majeure.
- `SELLER_WEIGHED` quá hạn (`buyerReceiveWindowDays`, mới) → notify buyer, im lặng tiếp → Admin/Level 1 quyết theo bằng chứng hiện có — **không** auto-settle theo số seller tự khai.

**Diễn giải bằng lời — đọc kỹ nếu sơ đồ mũi tên phía trên gây hiểu lầm:**

Mỗi milestone bắt đầu ở `CREATED` khi contract chuyển `ACTIVE`, rồi chuyển ngay sang `IN_PROGRESS` — trạng thái mặc định trong lúc seller chuẩn bị/gom hàng cho batch đó. Từ `IN_PROGRESS` có hai hướng đi, không phải một đường thẳng:

*Hướng bình thường (không có sự cố):* seller cân hàng trước khi lên xe, upload ảnh làm bằng chứng → milestone chuyển `SELLER_WEIGHED`, ghi nhận `sellerDeclaredWeight`. Hàng vận chuyển tới buyer. Buyer cân lại khi hạ hàng, upload ảnh bằng chứng riêng → milestone chuyển `BUYER_RECEIVED`, ghi nhận `buyerReceivedWeight`.

Tại `BUYER_RECEIVED`, buyer có đúng hai lựa chọn chủ động, cộng thêm 1 nhánh timeout. Nếu số lượng và chất lượng đúng như mong đợi, buyer bấm **CONFIRM_CLEAN** — hệ thống tự tính pro-rata theo Delta 2 (so `sellerDeclaredWeight` với `buyerReceivedWeight`) và release tiền ngay, milestone chuyển thẳng `SETTLED`, không ai can thiệp, không tốn phí gì. Nếu buyer thấy có vấn đề (thiếu cân hoặc sai chất lượng), buyer bấm **FLAG_ISSUE** — milestone chuyển `AWAITING_SELLER_RESPONSE`, seller có đúng `sellerResponseWindowDays` (mặc định 2 ngày làm việc) để phản hồi. Seller im lặng hết thời gian này = hệ thống coi như đồng ý với con số buyer báo, tự động `SETTLED` theo số đó. Seller không đồng ý, bấm **CONTESTED** — tranh chấp được đẩy qua `DisputeRoutingService`, dùng đúng cơ chế 3-tier đã có sẵn cho Tiered Dispute Resolution (Admin nội bộ / Vinacontrol-Quatest / SGS-Bureau Veritas tuỳ giá trị và độ phức tạp hàng hoá) — có kết quả rồi milestone mới `SETTLED` theo phán quyết đó.

**Cập nhật (04/07/2026) — timeout đối xứng cho nhánh buyer im lặng:** nếu buyer không bấm gì cả (không `CONFIRM_CLEAN`, không `FLAG_ISSUE`) trong vòng `buyerConfirmWindowDays` kể từ lúc milestone vào `BUYER_RECEIVED`, hệ thống tự động xử lý như `CONFIRM_CLEAN` — release theo đúng `sellerDeclaredWeight`/`buyerReceivedWeight` đã ghi nhận, milestone `SETTLED`. Lý do default về `CONFIRM_CLEAN` chứ không phải treo vô thời hạn: seller đã hoàn thành nghĩa vụ giao hàng tới đúng lúc `BUYER_RECEIVED` (hàng đã tới tay buyer) — buyer im lặng không được phép biến thành công cụ giữ tiền seller vô thời hạn, đúng nguyên tắc "nghiêng về seller khi seller yếu thế hơn" đã dùng xuyên suốt (§6.2).

**Bổ sung (19/07/2026) — auto-confirm không được release tiền khi kiểm nghiệm chưa xong (điểm 7 research):** hợp đồng cân-đo đơn giản thì 2 ngày là đủ, nhưng hợp đồng yêu cầu **kiểm nghiệm** (đo ẩm, grading, an toàn thực phẩm) cần thời gian lab dài hơn — dùng chung một cửa sổ 2 ngày cho mọi commodity/testing profile là ép buyer chọn giữa (a) flag bừa để dừng đồng hồ hoặc (b) mất quyền kiểm nghiệm. Hai sửa:

1. **Guard cứng (invariant):** auto-`CONFIRM_CLEAN` chỉ chạy khi milestone **vẫn ở `BUYER_RECEIVED`** và **không có inspection nào đang pending** cho milestone đó. Về cơ học, `FLAG_ISSUE` đã chuyển milestone sang `CONTESTED` (thoát `BUYER_RECEIVED`) nên timeout tự vô hiệu — ghi rõ thành invariant để implement/test không "tiện tay" cho timer chạy xuyên state (verification matrix 26i). Buyer cần lab test dài hơn cửa sổ: flag trước khi hết window là cơ chế dừng đồng hồ hợp lệ — flag-rồi-thua đã có cost qua tín hiệu flag-abuse (reputation §6.1b), nên không tạo động cơ flag bừa.
2. **`buyerConfirmWindowDays` chuyển per-contract** (từ config chung `application.yml` sang `ContractTerms`, thương lượng lúc `NEGOTIATING` — đúng pattern `graceDays`/`expectedDeliveryDate` per-contract đã có từ A4). Default 2 ngày làm việc; guardrail range **[2, 10] ngày làm việc** validate lúc `sign()` — sàn 2 giữ tốc độ mặc định, trần 10 tái dùng đúng con số `level2BufferWindowDays` đã benchmark (buyer không kéo dài giam tiền seller quá mức một chu kỳ đối chiếu ngành). Hợp đồng cà phê cần đo ẩm có thể chốt 5-7 ngày ngay lúc đàm phán, không cần flag thủ tục.

**Sửa (08/07/2026) — `SELLER_WEIGHED` không có timeout, lỗ hổng để buyer né toàn bộ cơ chế:** state machine có timeout ở `BUYER_RECEIVED` (trên) và `AWAITING_SELLER_RESPONSE` (dưới), nhưng **`SELLER_WEIGHED` không có** — nếu hàng đã cân/gửi mà buyer không bao giờ cân lại/ghi nhận (tức không bao giờ bước vào `BUYER_RECEIVED`), milestone kẹt ở `SELLER_WEIGHED` vĩnh viễn, `batchAmount` khoá vĩnh viễn. Buyer chỉ cần *không bấm gì ở bước nhận hàng* là né sạch toàn bộ timeout đã dựng — và vì `batchAmount` lock sớm (§6.2), lối thoát duy nhất trước đây là `cancel()`, khiến **buyer bị seize cọc dù mình mới là bên gây kẹt** — bất công ngược đúng chỗ luận điểm gốc "bảo vệ bên yếu" muốn tránh.

**Không bắt chước cơ chế `BUYER_RECEIVED` (auto CONFIRM_CLEAN):** ở `BUYER_RECEIVED` đã có **cả 2 số cân** (bằng chứng đầy đủ, buyer im lặng chỉ = không phản đối) nên auto-settle an toàn. Ở `SELLER_WEIGHED` chỉ có số seller **tự khai**, chưa có bằng chứng hàng đã tới tay buyer — auto-settle theo số đó sẽ tạo lỗ hổng ngược: seller cân xong không cần giao thật, đợi timeout là có tiền. Đường đúng, dùng field `expectedDeliveryDate`/`graceDays` mới thêm ở `MilestoneTerm` (§2.1):

- Hết `buyerReceiveWindowDays` (mới, mặc định 2 ngày làm việc — §8) mà buyer chưa cân → **notify** buyer, chưa đổi state.
- Buyer vẫn im lặng hoàn toàn → đẩy milestone vào nhánh **Admin/Level 1** (bước rẻ nhất, đúng triết lý 3-tier) — Admin quyết theo bằng chứng hiện có (ảnh cân seller + bằng chứng vận chuyển nếu có), **default nghiêng seller** chỉ khi seller đã chứng minh giao và buyer từ chối tham gia hoàn toàn.
- Kết quả: buyer im lặng **không** tự động thắng (không kẹt tiền seller vô thời hạn để né phí), seller tự khai **cũng không** tự động thắng (không thể cân khống rồi chờ timeout ăn tiền).

**Sửa (08/07/2026) — timeout ở `IN_PROGRESS` khi seller quá hạn giao (dùng `expectedDeliveryDate`/`graceDays` mới, §2.1; mốc so sánh sync 19/07 → `effectiveDeliveryDeadline` §6b — cộng thêm `fundingDelayBusinessDays` với milestone 2+ bị buyer nạp tiền trễ, milestone đầu delay = 0 nên không đổi hành vi):** trước bản này, `MilestoneTerm` không có mốc ngày nào — hệ thống không định nghĩa được "seller giao trễ", dù trễ hạn là vi phạm phổ biến nhất của forward contract nông sản (Doc01 §2.1: bẻ kèo do biến động giá theo mùa vụ). Quá `effectiveDeliveryDeadline` mà milestone chưa rời `IN_PROGRESS` → **buyer trigger được** nhánh seller-quá-hạn. Seller còn cửa claim force majeure trong `forceMajeureReportWindowDays` (cơ chế đã có, không đổi). Không claim được/claim bị bác → xử như thiếu hàng (Delta 1 penalty nhánh 2, §4) hoặc cancel pro-rata milestone đó — **buyer không bị seize cọc** vì buyer không phải bên phá kèo trong nhánh này.

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
- Contract-service tính `level1_5EntitlementAmount = X15`, chọn `releaseRate = relRate`, rồi tính `sellerReleaseAmount = X15 × relRate` và `remainingLockedAmount = batchAmount − sellerReleaseAmount`.
- Không refund buyer ở bước này. `EscrowMilestone.status = PROVISIONALLY_RELEASED`; `remainingLockedAmount` chỉ là snapshot immutable để kiểm tra conservation, không phải competing balance.
- Contract-service publish `milestone.level2_provisional_settled {contractId, milestoneId, level1_5ReportId, level1_5EntitlementAmount, releaseRate, sellerReleaseAmount, remainingLockedAmount}`.
- Escrow-service kiểm tra `sellerReleaseAmount + remainingLockedAmount = batchAmount`, sau đó chỉ phát `bank.release_requested(RELEASE_TO_SELLER, sellerReleaseAmount)` khi số tiền dương.

**Lý do dùng buffer khoá sẵn thay vì ghi nợ:** seller (HTX) không có tài sản đối ứng để đòi nếu số Level 2 thật (về sau) thấp hơn số đã release tạm — không có `sellerDepositRate` bắt buộc để seize. Giữ tiền sẵn trong escrow xử lý đúng rủi ro tại nguồn, không phụ thuộc khả năng seller trả nợ sau này.

**Bước 2 — Reconcile (report Level 2 `CONFIRMED` về trong `level2BufferTerminalDays`):**
- Contract-service tính `finalSellerEntitlementAmount = min(X2, batchAmount)`, `alreadyReleasedAmount`, `sellerAdditionalReleaseAmount`, `buyerRefundAmount` và `overReleaseAmount`.
- Contract-service publish `milestone.level2_buffer_reconciled {contractId, milestoneId, level1_5ReportId, level2ReportId, finalSellerEntitlementAmount, alreadyReleasedAmount, sellerAdditionalReleaseAmount, buyerRefundAmount, overReleaseAmount}`.
- `overReleaseAmount = max(0, alreadyReleasedAmount − finalSellerEntitlementAmount)` chỉ là immutable evidence/audit field; không tạo debit hoặc negative ledger.
- Escrow-service kiểm tra conservation và phát các bank command có amount dương. Zero-value command bị omit.

**Cập nhật (06/07/2026) — chốt hạn cứng, đóng điểm treo về buffer vô thời hạn:** thêm `level2BufferTerminalDays` (chốt 30 ngày, §8; tính từ lúc `level2BufferWindowDays` hết hạn — tổng thời gian chờ tối đa ~40 ngày trước khi có điểm dừng cứng).

**Bước 3 — Terminal (hết `level2BufferTerminalDays`, report Level 2 không bao giờ về):**
- Phán quyết 1.5 thành chung thẩm. Contract-service publish `milestone.level2_terminal_settled {contractId, milestoneId, level1_5ReportId, finalSellerEntitlementAmount, alreadyReleasedAmount, sellerAdditionalReleaseAmount, buyerRefundAmount}`.
- Escrow-service kiểm tra conservation rồi phát release/refund command dương; zero-value command bị omit.
- Rủi ro số 1.5 sai lệch so với số Level 2 thật (nếu report từng về muộn) dồn hết sang buyer — chấp nhận được, đúng nguyên tắc đã dùng xuyên suốt.

**Về `entryType`:** không cần enum mới ở bank-service — vẫn `RELEASE_TO_SELLER`/`REFUND_TO_BUYER`, mỗi động tác 1 `sourceEventId` riêng (đúng pattern Delta 1/2 đã dùng, `bank-service-phase2-design.md` §3.1). Mọi amount trong bank command phải `> 0`; không dùng số âm để biểu diễn reversal.

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
| 2 | Thiếu vượt threshold, **không** chứng minh được bất khả kháng | **Mở `BreachCase` chờ attribution (§6.4) — KHÔNG auto-áp penalty ngay (sửa 19/07/2026).** Pro-rata theo số thực giao vẫn chạy để không kẹt tiền; nhưng `CONTRACTUAL_PENALTY` chỉ áp **sau** khi attribution kết luận `finalBreachingRole = SELLER` với reason cố ý/chiến lược. |
| 3 | Thiếu (bất kỳ mức nào), **chứng minh được** bất khả kháng (Admin/Level 1.5 approve) | Pro-rata theo số thực giao, không penalty |

**Sửa (19/07/2026) — "thiếu vượt threshold" KHÔNG đồng nghĩa "bẻ kèo":** bản trước nhánh 2 áp thẳng `sellerPenaltyRate` với nhãn "đúng bản chất bẻ kèo". Đây là một phép **gán attribution quá vội** — chính literature side-selling mà Doc01 §2.1 trích dẫn (và bằng chứng bổ sung: production shock ở cà phê specialty) cho thấy shortfall vượt ngưỡng có thể đến từ nhiều nguyên nhân **khác nhau về lỗi**, cùng một _kết quả_ (giao thiếu) nhưng khác _attribution_: sốc sản lượng chưa đủ điều kiện bất khả kháng pháp lý, buyer chậm thanh toán/chậm nhận làm seller gãy vốn, sốc thanh khoản, lỗi nội bộ HTX, hay đúng là bán ra ngoài ăn chênh giá (strategic). Chỉ nhóm cuối mới là "phá hợp đồng" đáng áp penalty + reputation. **Outcome giống nhau không cho phép suy ra attribution giống nhau** — nên nhánh 2 mở `BreachCase` (Rổ B — §6.4), để Admin/Level 1.5 phân loại `breachReasonCode` trước khi tiền/reputation bị đụng. Pro-rata số thực giao vẫn release ngay (không giam tiền của phần đã giao thật); chỉ **penalty** chờ attribution.

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

## 6. Contract-level Termination — Taxonomy & Attribution

**Chốt (02/07/2026):** Bỏ hẳn `dispute()` cấp Contract — thay bằng milestone-level dispute (§3.2, §5).

**Viết lại (19/07/2026) — `cancel(initiatedBy)` một-nút gán sai người vi phạm:** bản trước có đúng một hành động `cancel(initiatedBy)` và gán thẳng `initiatedBy = kẻ phá kèo` → seize/khoá reputation ngay. Đây là **lỗi domain**: người **yêu cầu chấm dứt** không đồng nghĩa người **vi phạm**. Buyer bấm chấm dứt có thể vì seller không giao; seller dừng thực hiện có thể vì buyer không nạp tiền, không nhận hàng hoặc không thanh toán. Gán initiatedBy = breaching là tự tay tái tạo đúng bất đối xứng quyền lực mà Doc01 §2.3 mô tả là vấn đề gốc — bên yếu (HTX) bị buyer bỏ đói funding rồi buộc phải bấm dừng, và ăn nguyên penalty. File này đã tự vá cục bộ chỗ đó (§3.2: buyer bỏ đói/để tồn đọng → buyer bị xử, không phải seller), nhưng chưa nâng thành **một khái niệm attribution thống nhất**.

### 6.0 Ba khái niệm tách bạch

Mọi termination phải phân biệt:

- **`requestedBy`** — ai bấm nút yêu cầu chấm dứt. Chỉ là hành vi khởi xướng, **không** mang phán xét lỗi.
- **`allegedBreachingRole`** — bên bị cáo buộc vi phạm (khi có cáo buộc). Mới là _cáo buộc_, **chưa** được seize tiền/khoá reputation.
- **`finalBreachingRole`** — kết luận attribution cuối cùng. **Chỉ khi có giá trị này** mới được kích hoạt penalty/forfeiture/reputation lock. `NULL` với mutual/force-majeure/technical → không ai bị phạt.

### 6.1 Sáu hành vi chấm dứt (thay `cancel()` một-nút)

| Hành vi | Khi nào | Penalty mặc định | Tác động |
|---|---|---|---|
| `WITHDRAW_OFFER` | Trước khi đủ 2 chữ ký | Không | `OFFERED/NEGOTIATING → WITHDRAWN`, publish `contract.terminated` with no remedy legs |
| `MUTUAL_TERMINATION` | Hai bên cùng đồng ý dừng | Không | **Toàn bộ** phần nghĩa vụ còn lại chưa `SETTLED` (chốt full-scope 19/07 lần 2 — partial mutual removal từng milestone là Future Work §8c); không phạt, không reputation (§6.5) |
| `MUTUAL_REPLACEMENT` (supersede) | Hai bên muốn đổi điều khoản (vd chốt lại giá khi thị trường biến động) | Không | Đóng hợp đồng cũ `SUPERSEDED` sạch + ký hợp đồng mới nối con trỏ (§6.6) |
| `TERMINATION_FOR_BREACH` | Sau khi có **`AttributionDecision`** kết luận `finalBreachingRole` (sửa 19/07 lần 3 — Rổ B qua `BreachCase` §6.4, Rổ A máy tự quyết `decisionSource=SYSTEM` không cần case; trước đó taxonomy đòi BreachCase cho MỌI breach → Rổ A không có đường hợp lệ sinh termination) | Theo `LegalProfile` + attribution | Pro-rata phần chưa `SETTLED`; seize/lock bên có lỗi (§6.4/§6.4b) |
| `TERMINATION_FOR_FORCE_MAJEURE` | FM được duyệt (§5) | Không | Pro-rata số thực giao, không ai bồi thường ai |
| `ACTIVATION_FAILURE` | **CHỈ** lỗi kỹ thuật khoá cọc để kích hoạt contract **trước `ACTIVE`** (§3.1) — sửa 19/07 lần 2, KHÔNG gồm milestone funding fail giữa hợp đồng (đó là §6b: hết cure window → `TERMINATION_FOR_BREACH` buyer; fail do bank outage → retry, không phạt ai) | Không | Refund mọi leg đã lock, không penalty/reputation |

#### 6.1.1 Authoritative command transport

`contracts/openapi/golden-flow-api.yaml` freezes the transport contract; implementations must not invent aliases. Every write requires `Idempotency-Key`, JWT identity is authoritative, `403` covers role/ownership denial, and invalid state/duplicate-with-different-payload uses the shared `409` error envelope.

| Use case | Method/path | Role / request / successful response |
|---|---|---|
| Withdraw pre-sign offer | `POST /api/v1/contracts/{contractId}/withdraw` | Buyer or Seller participant; empty body; `204`, state `WITHDRAWN`, no remedy leg |
| Propose / confirm mutual termination | `POST .../termination/mutual/propose`, `POST .../termination/mutual/confirm` | Buyer or Seller participant; empty body; `204`; confirm starts no-fault remedy and terminal reconciliation guard |
| Propose / confirm mutual replacement | `POST .../replacement/propose`, `POST .../replacement/confirm` | Buyer or Seller participant; proposal carries frozen replacement listing/terms and returns `201 ContractResponse`; confirm returns `204` |
| Report / review / resolve breach | `POST .../breach-cases`, `POST .../breach-cases/{breachCaseId}/review`, `POST .../resolve` | Participant/SYSTEM report; ADMIN/INSPECTOR review/resolve; request and response schemas are `BreachCaseReportRequest`, `BreachCaseResolutionRequest`, `BreachCase` |
| Execute final termination | `POST .../termination/execute` | Buyer/Seller/Admin with final `attributionDecisionId`; `204` starts remedy/reconciliation and does not imply terminal commit |

`WITHDRAW_OFFER` is the canonical action label but its persisted terminal state is `WITHDRAWN`; it maps to lifecycle `contract.terminated.terminationType = WITHDRAW_OFFER` only for observation. It is outside breach attribution and outside money consequences.

**Pro-rata (áp cho mọi nhánh có tác động milestone):** chỉ đụng milestone **chưa `SETTLED`**. Milestone đã xong giữ nguyên, không truy thu. Penalty (nếu có) tính trên `batchAmount` phần bị vi phạm — **là "phần nghĩa vụ bị vi phạm" theo LTM Điều 301** (§2.1b), không phải `totalAmount`.

### 6.2 Ranh giới tự-thực-thi: Rổ A (seize ngay) vs Rổ B (chờ attribution)

**Nguyên tắc cứu value prop** — "escrow tự thực thi, không chờ tố tụng 1–3 năm" (Doc01 §2.2) **không được chết** vì thêm attribution. Ranh giới:

> **Rổ A — máy seize/lock NGAY, không chờ người:** vi phạm được xác lập bằng **mốc thời gian đã qua + sự kiện vắng mặt đo được** (không có lock / không có cân / không có giao hàng quá deadline), **và** bên kia đã có window để khắc phục. Attribution là hàm của đồng hồ, không cần ai phán.
>
> **Rổ B — mở `BreachCase`, chờ attribution của Admin/inspector:** vi phạm cần **phán đoán về chất lượng, mức độ, hay nguyên nhân**.

Đối xứng buyer/seller — thuộc **Rổ A**:
- Buyer không lock funding milestone quá `fundingDeadline` sau khi đã được nhắc + retry window → buyer breach (§6b).
- Buyer để hàng tồn đọng quá `buyerReceiveWindowDays` sau khi seller đã `SELLER_WEIGHED` (§3.2), **VÀ seller có bằng chứng giao hàng khách quan** (carrier receipt / mốc thời gian tới nơi / xác nhận địa điểm giao / buyer acknowledgment / nhân chứng trung lập — ít nhất một), **VÀ buyer đã nhận notice + hết window** → Rổ A, `FAILURE_TO_RECEIVE`. **Thiếu bằng chứng khách quan → Rổ B** (mở `BreachCase` UNDER_REVIEW): số cân do seller tự khai một mình chưa đủ để máy tự phạt buyer — sửa 19/07 lần 2, khớp lại với workflow `SELLER_WEIGHED` cũ (08/07: buyer im lặng → đánh giá evidence, không auto kết luận).
- Seller quá `effectiveDeliveryDeadline` (§6b — đã cộng funding delay nếu có) chưa `SELLER_WEIGHED`, không claim FM trong window (§3.2) → seller breach về thời hạn.
*(Gỡ 19/07 lần 3 — "explicit repudiation" bị bỏ khỏi Rổ A: tự attribution từ một tuyên bố là anticipatory breach, thứ §8c đã chốt hoàn toàn ngoài scope; một dòng trong Rổ A không đủ gọi là hỗ trợ CISG 71-73. Bên nhận tuyên bố dùng đường Rổ B — `reportBreach` với tuyên bố đó làm evidence, người phán quyết.)*

Thuộc **Rổ B** (mở `BreachCase`):
- Delta 1 thiếu vượt threshold (§4 nhánh 2) — nguyên nhân đa dạng, không auto-gán.
- Buyer flag chất lượng → seller contest (§3.2) — cần inspector.
- Claim force majeure/hardship.
- Buyer từ chối hàng bằng lý do chất lượng mà seller cho là vô căn cứ (wrongful rejection — buyer-side breach).

Rổ A giữ được "escrow có răng" (tự động theo timeout — cơ chế phần lớn **đã có** ở §3.2, chỉ gom lại dưới một tên); Rổ B vá được "không gán oan" (cần người phán). Đây là hai lực research kéo — file cân bằng được cả hai, không hy sinh cái nào.

### 6.3 Seize/refund tiền khi termination có lỗi — Seller & Buyer

*(Áp dụng cho `TERMINATION_FOR_BREACH` sau khi **`AttributionDecision`** (§6.4b) kết luận `finalBreachingRole` — từ `BreachCase` §6.4 với Rổ B, hoặc `SYSTEM` với Rổ A. Cơ chế tiền dưới đây giữ nguyên; điểm khác duy nhất so với bản cũ: **trigger không còn là `initiatedBy` mà là `finalBreachingRole`** — máy không tự suy người phá kèo từ người bấm nút.)*

#### 6.3.1 `finalBreachingRole = SELLER`

**Cập nhật (06/07/2026) — không còn tuyệt đối "không có gì để seize":** trước đây seller cancel không có gì để escrow tự động trừ, vì Milestone Escrow bỏ hẳn seller deposit (viability constraint cho HTX). Quyết định đó **đổi thành optional** — `sellerDepositRate` (§2.1) giờ tồn tại nếu buyer/seller tự đàm phán có, mặc định vẫn `0` (giữ nguyên hiện trạng cho ai không cần). Cơ chế dưới đây tách theo 2 case:

**Case A — có `sellerDepositRate > 0` đã khoá:** seize ngay lập tức, tự động, không cần chờ Admin — giống cơ chế `buyerDepositRate` seize ở §6.3.2. Số tiền seize offset trực tiếp vào `penalty debt` ở mục 1 dưới: `phần còn nợ = (sellerPenaltyRate × giá trị milestone còn lại) − sellerDepositRate đã seize`. Nếu deposit ≥ penalty tính ra, phần dư (nếu buyer/seller có thoả thuận riêng) hoặc phần penalty debt = 0 — không âm.

**Case B — `sellerDepositRate = 0` (mặc định, hoặc buyer chọn không đòi):** giữ nguyên cơ chế cũ dưới đây — không qua escrow, dựa hoàn toàn vào Reputation làm đòn bẩy enforce thật. Đây là **lựa chọn buyer đã biết trước và chấp nhận** (đàm phán lúc `NEGOTIATING`), không phải lỗ hổng platform bỏ sót.

**`buyerDepositRate` tách hoàn toàn khỏi penalty debt của seller (04/07/2026, áp dụng cho cả 2 case A/B trên):** buyer không phải bên phá kèo trong case này — `buyerDepositRate` (khoá từ lúc `SIGNED`, §2.1) được refund về buyer ngay khi termination có `finalBreachingRole = SELLER` được xác nhận (§6.4), độc lập hoàn toàn với cơ chế penalty debt/lockout/`sellerDepositRate` seize ở trên (đó là hình phạt nhắm vào hành vi của seller, không liên quan gì tới tiền của buyer). Trigger + event cụ thể — §6.7.

**Cơ chế Case B — không qua escrow, dựa vào Reputation (Doc2 mục 4.5) làm đòn bẩy enforce thật:**

1. **Penalty debt được ghi nhận** — `sellerPenaltyRate × giá trị phần nghĩa vụ bị vi phạm` (milestone còn lại), lưu audit trail bất biến, là **`CONTRACTUAL_PENALTY_CLAIM` — khoản PHẠT VI PHẠM theo Điều 300-301 chưa thu được** (sửa 19/07 lần 3: bản cũ gọi nhầm là "bằng chứng bồi thường thiệt hại Điều 302" — sai loại chế tài ngay tại record, mâu thuẫn với chính §2.1b; phạt là phạt, tính bằng tỷ lệ thoả thuận, cap 8%). Buyer muốn đòi **bồi thường** (Điều 302) là một claim RIÊNG: cần `actualLossAmount` + bằng chứng nhân quả + bằng chứng hạn chế tổn thất, theo `damagesPolicy` §2.1b — không mang penalty debt đi claim thành damages. Platform không tự thu hộ được — cả hai chỉ là bằng chứng cho VIAC/toà.
2. **Khoá account seller ngay lập tức** — chặn tạo listing/hợp đồng mới, không đợi kết quả toà (Doc1 mục 2.2: tố tụng 1–3 năm, chờ toà là tự mâu thuẫn với lý do sản phẩm tồn tại).
3. **Thời gian khoá = 2 trục hành vi, không tính giá trị hợp đồng** — giá trị đã được phản ánh riêng ở `penalty debt` (mục 1, tỷ lệ theo `sellerPenaltyRate × giá trị`); nhét giá trị vào cả `lockDurationDays` là tính 2 lần cho cùng 1 vi phạm qua 2 kênh khác nhau. Tách bạch: **penalty debt phản ánh thiệt hại tài chính, số ngày khoá phản ánh mức độ hành vi tái phạm** — hai câu hỏi khác nhau, không dùng chung 1 biến:

   ```
   lockDurationDays = baseDays × repeatOffenseMultiplier × trackRecordMultiplier × zeroProgressMultiplier
   ```
   **⟢ Công thức + baseline number của `lockDurationDays` do `reputation-service-phase2-design.md` §4 chốt (owner — nơi implement thật). Phần dưới đây chỉ diễn giải *vì sao* các thừa số tách bạch như vậy, phục vụ lập luận của escrow; con số cụ thể lấy ở reputation §4, nếu lệch thì reputation thắng.** (`zeroProgressMultiplier` thêm 08/07/2026, xem cuối §6.3.1 — đã đưa vào công thức để không lệch với reputation §4.)
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

#### 6.3.2 `finalBreachingRole = BUYER`

**Bất đối xứng có chủ đích (mặc định, không tuyệt đối từ 06/07/2026):** buyer (doanh nghiệp thu mua, thường có vốn) có deposit thật để mất — seller (HTX) mặc định thì không, vì lý do viability đã chốt ở §1, nhưng giờ **có thể có** nếu `sellerDepositRate > 0` được đàm phán riêng (§6.3.1). Buyer luôn bị enforce bằng cả tiền thật lẫn reputation; seller enforce bằng reputation là mặc định, cộng thêm tiền thật nếu buyer chủ động đòi cọc lúc đàm phán.

Buyer huỷ bất kỳ lúc nào sau `SIGNED`:

1. **Mất toàn bộ `buyerDepositRate` (5% `totalAmount`)** — chuyển cho seller, escrow tự động seize ngay, không cần chờ Admin. (Event cụ thể — §6.7.)
2. **Nếu đang có `batchAmount` của milestone hiện tại đang lock** — seize luôn theo `buyerPenaltyRate`, chuyển cho seller như bồi thường, y hệt logic `PENALIZED_BUYER` của Phase 1 nhưng chỉ scope đúng 1 batch.
3. **Khoá account + xấu track record** — áp đúng công thức `lockDurationDays` ở §6.3.1, dùng chung cho cả buyer và seller (đổi `sellerPenaltyRate` thành `buyerPenaltyRate` trong input tính penalty debt).

**Thời điểm lock `batchAmount`: chốt sớm** — buyer lock ngay khi milestone trước đó chuyển `SETTLED` (hoặc ngay khi contract `ACTIVE` cho milestone đầu tiên). Lý do chọn sớm thay vì muộn: nhất quán với hướng đã chọn xuyên suốt session — mọi lần có tension giữa bảo vệ buyer và bảo vệ seller, thiết kế đều nghiêng về seller khi seller là bên yếu thế hơn (bỏ seller deposit vì viability, force majeure bảo vệ seller khỏi bị buộc tội oan, `buyerDepositRate` bắt buộc buyer luôn có "cái để mất" từ `SIGNED`) — đúng luận điểm gốc Doc1 mục 2.3 về bất đối xứng quyền lực. Lock sớm cho seller đảm bảo tối đa trước khi họ bỏ công/vốn chuẩn bị hàng cho batch tiếp theo.

**Ownership giữa các service:** `contract-service` tính penalty + publish event khi milestone bị cancel-có-penalty (cả 2 chiều buyer/seller). `reputation-service` (service #8, Phase 2 — services.md) consume event này cùng lịch sử hợp đồng hoàn thành, tính `lockDurationDays` theo công thức trên, và là nguồn quyết định khoá/mở khoá. `user-service` enforce khoá thật (chặn tạo listing/contract) dựa trên quyết định từ `reputation-service`. Chi tiết state machine đầy đủ của `reputation-service` (aggregate từ nhiều nguồn, eventually consistent theo services.md) để dành cho design session riêng — ở đây chỉ chốt input/trigger nó cần nhận.

### 6.4 `BreachCase` — aggregate attribution (mới, 19/07/2026)

**Chốt:** `BreachCase` là **aggregate riêng trong `contract-service`, cùng `contract_db`** — không đẻ service thứ 13, không phá bản đồ 12 service. Cùng kiểu với `Milestone` (đã là aggregate riêng cùng service). Nó là nơi giữ vòng đời của một cáo buộc vi phạm cho tới khi có attribution cuối, để **không nhánh nào seize tiền/khoá reputation trước khi kết luận**.

**Phạm vi có chủ đích — bản RÚT GỌN cho đồ án:** KHÔNG dựng full state machine `REPORTED → NOTICE_SERVED → CURE_PENDING → REMEDY_SELECTION` 8 nhánh của contract-law lý thuyết (notice/cure/remedy selection đầy đủ là **Known Limitation §8c**, dẫn UNIDROIT/CISG). Bản Phase 2 gom về đúng cái luồng vàng cần: một cáo buộc → phân loại nguyên nhân → kết luận ai lỗi → thực thi. Luồng vàng đã có "milestone dispute → Level 1.5 → settle theo phán quyết" — `BreachCase` chính là cái đó, đặt tên và gắn attribution.

```
REPORTED ──▶ UNDER_REVIEW ──▶ RESOLVED
   │              │              ├─ finalBreachingRole = SELLER  → TERMINATION_FOR_BREACH / Delta1 penalty
   │              │              ├─ finalBreachingRole = BUYER   → buyer-side breach (§6.3.2)
   │              │              └─ finalBreachingRole = NULL     → no-fault (FM / mutual / kỹ thuật) → không phạt
   │              └─ (Admin nội bộ / Level 1.5 / Level 2 theo DisputeRoutingService §3.2)
   └─ nguồn: Delta1 vượt threshold (§4), buyer flag→seller contest (§3.2), wrongful rejection, FM contest (§5)
```

| Field | Loại | Ghi chú |
|---|---|---|
| `breachCaseId` | UUID | |
| `contractId` / `milestoneId?` | UUID | milestoneId `NULL` nếu breach ở cấp contract |
| `requestedBy` | Enum `BUYER\|SELLER\|SYSTEM` | Ai khởi (SYSTEM khi timeout Rổ A tự mở) |
| `allegedBreachingRole` | Enum `BUYER\|SELLER`, nullable | Cáo buộc — chưa phạt |
| `breachReasonCode` | Enum | Taxonomy §6.4.1 |
| `severity` | Enum `MINOR\|MATERIAL` | Material mới cho phép full termination |
| `evidenceFileIds` | List<UUID> | Reference `file-service` |
| `finalBreachingRole` | Enum, nullable | **Chỉ set khi RESOLVED**; `NULL` = no-fault |
| `decisionSource` | Enum `ADMIN\|INSPECTOR_L1_5\|INSPECTOR_L2\|MUTUAL` | Ai kết luận attribution |
| `status` | Enum | `REPORTED\|UNDER_REVIEW\|RESOLVED` |

**Invariant cứng:** khi `status != RESOLVED`, **cấm** phát bất kỳ `bank.seize_requested`/`CONTRACTUAL_PENALTY`/`DEPOSIT_FORFEITURE` nào và **cấm** phát event vào `reputation-service`. Pro-rata release của phần hàng **đã giao thật** vẫn chạy độc lập (không giam tiền của nghĩa vụ đã hoàn thành) — chỉ **chế tài** mới chờ.

#### 6.4b `AttributionDecision` — điểm hội tụ Rổ A và Rổ B (mới, 19/07 lần 3)

Rổ A tự attribution không dựng `BreachCase`; Rổ B qua `BreachCase`. Cả hai hội tụ về **một record persist chung** trong `contract_db` — nếu không, Rổ A "có attribution nhưng không có đường hợp lệ" sinh canonical remedy:

| Field | Ghi chú |
|---|---|
| `attributionDecisionId` | UUID |
| `breachCaseId` | nullable — `NULL` với Rổ A |
| `decisionSource` | `SYSTEM` (timeout Rổ A) \| `ADMIN` \| `INSPECTOR_L1_5` \| `INSPECTOR_L2` \| `MUTUAL` |
| `finalBreachingRole` | nullable (`NULL` = no-fault) |
| `breachReasonCode` | nullable — §6.4.1 với breach; `NULL` cho normal completion/no-fault |
| `evidenceRefs` | fileIds / timeout metadata (deadline nào, event vắng mặt nào) |

Luồng thống nhất: `AttributionDecision` → remedy calculator (§6.7) sinh `RemedyDecision` → phát `remedy.finalized`. Rổ B: BreachCase `RESOLVED` tạo AttributionDecision với `breachCaseId`. Rổ A: timeout job tạo thẳng với `breachCaseId = NULL`, `decisionSource = SYSTEM`, evidenceRefs = metadata timeout. Normal completion cũng dùng record hiện có này với nullable breach fields như §3.1; không tạo decision/event type mới.

**Canonical `RemedyDecision`/producer DTO — sao chép nguyên sang event/OpenAPI/notification command:** mọi field dưới đây là required trên wire; các field ghi nullable vẫn phải xuất hiện với giá trị `null`.

| Field | Cardinality / nullability |
|---|---|
| `remedyDecisionId` | UUID, non-null |
| `attributionDecisionId` | UUID, non-null |
| `breachCaseId` | UUID, nullable |
| `contractId` | UUID, non-null |
| `buyerId` / `sellerId` | UUID, non-null |
| `affectedMilestoneIds` | List<UUID>, non-null, unique items; `[]` cho normal completion contract-level only |
| `finalBreachingRole` | `BUYER\|SELLER`, nullable |
| `breachReasonCode` | taxonomy §6.4.1, nullable |
| `decisionSource` | `SYSTEM\|ADMIN\|INSPECTOR_L1_5\|INSPECTOR_L2\|MUTUAL`, non-null |
| `penaltyEligible` / `reputationEligible` | Boolean, non-null |
| `remedyLegs` | List of `{remedyLegId, remedyType, fundType, role, amount}`, non-null; `amount` uses canonical money string and zero-value bank commands are omitted |

#### 6.4.1 `breachReasonCode` taxonomy (tối thiểu)

Cùng một _kết quả_ (giao thiếu / không giao / từ chối) có thể ánh xạ nhiều _nguyên nhân_ khác lỗi — attribution phải phân biệt để reputation/penalty không đánh oan (§4, §6.5, và `reputation-service` §6):

- **Seller-side:** `NON_DELIVERY`, `SHORTFALL`, `LATE_DELIVERY`, `NON_CONFORMING_QUALITY`, `SIDE_SELLING`, `PRODUCTION_SHOCK_NON_FM` *(giao thiếu do sốc sản lượng chưa đủ điều kiện FM — KHÔNG áp penalty chiến lược, chỉ pro-rata)*.
- **Buyer-side:** `FUNDING_FAILURE`, `LATE_PAYMENT`, `FAILURE_TO_RECEIVE`, `WRONGFUL_REJECTION`.
- **No-fault:** `FORCE_MAJEURE`, `MUTUAL_EXIT`, `ACTIVATION_FAILURE`.

**2 quyết định tách bạch, KHÔNG để consumer suy từ reason (sửa 19/07 lần 2):** contract-service phát trên `remedy.finalized` hai flag — `penaltyEligible` (có được áp `CONTRACTUAL_PENALTY` không; với `PRODUCTION_SHOCK_NON_FM` do LegalProfile/phán quyết quyết định, không auto) và `reputationEligible` (có được lock không; `false` cho `PRODUCTION_SHOCK_NON_FM`, buyer-caused với seller, no-fault; `true` cho `SIDE_SELLING`/`NON_DELIVERY` cố ý/`LATE_DELIVERY` không lý do và các buyer-breach `FUNDING_FAILURE`/`FAILURE_TO_RECEIVE`/`WRONGFUL_REJECTION` với buyer). Reputation chỉ đọc flag (`reputation-service` §4.3), escrow chỉ đọc `remedyLegs` — bảng reason ở trên là input cho calculator, không phải contract của consumer.

### 6.5 `MUTUAL_TERMINATION` (mới, 19/07/2026)

Hai bên cùng đồng ý dừng — **không ai vi phạm**, và chấm dứt **toàn bộ phần nghĩa vụ còn lại chưa `SETTLED`** (không partial-scope trong Phase 2 — chọn một vài milestone để bỏ, giữ phần còn lại, là Future Work §8c; muốn đổi cấu trúc milestone thì dùng `MUTUAL_REPLACEMENT` §6.6). `finalBreachingRole = NULL`. Không penalty, không `DEPOSIT_FORFEITURE`, không reputation lock. Cọc (`buyerDepositRate`/`sellerDepositRate`) **refund về đúng chủ**; milestone đã `SETTLED` giữ nguyên; milestone đang lock `batchAmount` → refund buyer. Cần **cả hai** bấm đồng ý (một bên đề nghị, bên kia xác nhận) — không một bên tự quyết. Event `contract.terminated` với `terminationType = MUTUAL_TERMINATION` (§6.7).

### 6.6 `MUTUAL_REPLACEMENT` — supersede (mới, 19/07/2026)

**Vấn đề vá:** khi thị trường biến động (siêu chu kỳ cà phê +125%, Doc01 §1.1) và hai bên **thiện chí** muốn chốt lại giá để giữ quan hệ, bản cũ ép họ đi đường "huỷ-có-phạt rồi ký lại" — biến một cuộc điều chỉnh thiện chí thành "phá hợp đồng" giả tạo, khoá reputation oan. `MUTUAL_REPLACEMENT` sửa đúng chỗ đó.

**Cơ chế — supersede, KHÔNG amendment tại chỗ:**
- Hợp đồng cũ chuyển terminal state mới **`SUPERSEDED`** (sạch: `finalBreachingRole = NULL`, không penalty, không reputation), mang con trỏ `supersededByContractId`.
- Hai bên ký một **hợp đồng mới hoàn toàn** với điều khoản mới, đi qua đúng flow `sign()` + hash + escrow đã có, mang con trỏ `replacesContractId`.
- Cần **cả hai** đồng ý (giống mutual termination). Một bên không đơn phương supersede được.

**Vì sao supersede chứ không sửa tại chỗ (đường rẻ, không phá invariant):** `ContractTerms` đóng băng + `signedContentHash` là nền của toàn bộ evidence model (hash chain, audit, chữ ký). Sửa giá **tại chỗ** trên hợp đồng đã ký = phá invariant đó → kéo theo `ContractVersion` + re-sign giữa chừng + hash chain cho amendment (đắt, đã cố tình để ngoài Phase 2 — §8c). Supersede **không đụng** hợp đồng cũ (nó chỉ *đóng lại*), hợp đồng mới tái dùng nguyên hạ tầng ký/hash/escrow — **cấm tuyệt đối** `PATCH /terms` sau ký, không versioning.

**Xử lý tiền/milestone khi supersede:** milestone đã `SETTLED` của hợp đồng cũ **giữ nguyên, không double-count sang hợp đồng mới** (tái dùng nguyên tắc pro-rata §6). Cọc + `batchAmount` đang lock của hợp đồng cũ → refund về chủ (không seize — không ai phá kèo), hợp đồng mới lock cọc/batch của riêng nó từ đầu. Đây là điểm dễ sót khi code: **không** cho phép "chuyển tiền lock từ contract cũ sang mới" — đóng sạch cái cũ, mở mới từ số 0, để ledger mỗi contract tự giải thích được (đúng nguyên tắc `bank-service` §2).

**Commit point & saga (mới, 19/07 lần 3 — flow trên chưa định nghĩa lỗi giữa chừng):** 2 state trung gian trên hợp đồng cũ: `ACTIVE → REPLACEMENT_PENDING → SUPERSEDE_REFUND_PENDING → SUPERSEDED`. Trình tự:

1. Tạo replacement draft mang `replacesContractId`; hai bên đồng ý replacement (mutual gate).
2. Hợp đồng mới đạt `SIGNED` (đủ 2 chữ ký). **Commit point:** chỉ TẠI ĐÂY hợp đồng cũ mới rời `ACTIVE` → `REPLACEMENT_PENDING`. Mới không ký được/hết hạn draft → cũ **vẫn `ACTIVE` nguyên vẹn**, draft huỷ, không sự kiện tiền nào đã xảy ra.
3. Cũ `REPLACEMENT_PENDING` → bắt đầu refund toàn bộ lock (qua `remedy.finalized` no-fault legs) → `SUPERSEDE_REFUND_PENDING` khi lệnh đã phát, chờ bank confirm từng leg (khuôn `ACTIVATION_REFUND_PENDING`). Refund fail → đứng lại + alert, không nhảy `SUPERSEDED`.
4. Mọi refund confirmed → cũ `SUPERSEDED` (ghi `supersededByContractId`), phát `contract.terminated` (`MUTUAL_REPLACEMENT`).
5. Chỉ SAU đó kích hoạt lock cọc/funding hợp đồng mới. Activation mới fail → xử đúng như hợp đồng-đã-ký-activation-fail thường (§3.1: retry → `ACTIVATION_REFUND_PENDING` → `ACTIVATION_FAILED`), **không rollback supersede, không giả vờ replacement thành công** — cũ đã đóng sạch, mới chết theo đường chuẩn của chính nó, hai bên ký lại nếu còn muốn.

Event `contract.terminated` với `terminationType = MUTUAL_REPLACEMENT` + `supersededByContractId` phát ở bước 4; hợp đồng mới phát `contract.signed` như bình thường. Crash windows test ở matrix 11p.

### 6.7 `buyerDepositRate`/`sellerDepositRate` — 3 đường release/seize, event cấp Contract (mới, 04/07/2026; mở rộng cho `sellerDepositRate`, 06/07/2026)

**Vấn đề phát hiện khi rà lại luồng tiền:** `buyerDepositRate` là khoá **cấp Contract** (`milestoneId = NULL` ở `ledger_entry`, `bank-service-phase2-design.md` §2), không thuộc về milestone nào — nên không thể dùng `milestone.cancelled_with_penalty` (event cấp milestone, §7.1) làm trigger cho nó. Event Catalog bản 02/07/2026 chưa có event nào ở cấp Contract để xử lý riêng field này — lỗ hổng thật, không phải chi tiết vụn.

**Chốt (04/07/2026; trigger sửa 19/07/2026 — theo `finalBreachingRole`, không `initiatedBy`):**

| Trigger | Event | `buyerDepositRate` | `sellerDepositRate` (nếu > 0) |
|---|---|---|---|
| Hợp đồng hoàn tất — mọi milestone `SETTLED` | `remedy.finalized` (normal settlement decision) | `REFUND_TO_BUYER` | `RELEASE_TO_SELLER` |
| `MUTUAL_TERMINATION` / `MUTUAL_REPLACEMENT` (§6.5/§6.6) | `remedy.finalized` (no-fault; `contract.terminated` follows as lifecycle) | `REFUND_TO_BUYER` | `RELEASE_TO_SELLER` (refund về chủ, không seize) |
| `TERMINATION_FOR_BREACH`, `finalBreachingRole=SELLER` (§6.3.1) | `remedy.finalized` (attribution; `contract.terminated` follows as lifecycle) | `REFUND_TO_BUYER` — độc lập penalty debt | `DEPOSIT_FORFEITURE` (nếu có khoá — offset vào penalty debt, §6.3.1 Case A) |
| `TERMINATION_FOR_BREACH`, `finalBreachingRole=BUYER` (§6.3.2) | `remedy.finalized` (attribution; `contract.terminated` follows as lifecycle) | `DEPOSIT_FORFEITURE` (chuyển cho seller) | `RELEASE_TO_SELLER` (seller không phá kèo) |
| `ACTIVATION_FAILURE` (§3.1) / funding-fail kỹ thuật (§6b) | `escrow.deposit_lock_failed` / `escrow.milestone_funding_failed` | `REFUND_TO_BUYER` (fundType `BUYER_DEPOSIT`) | `RELEASE_TO_SELLER` (fundType `SELLER_DEPOSIT`) — **sửa 19/07 lần 3, P0: bản trước ghi `REFUND_TO_BUYER` cho CẢ cọc seller = trả nhầm tiền seller cho buyer.** Nguyên tắc: technical fail → mọi cọc **về đúng chủ** theo `fundType`, đúng invariant matrix 11i/11b |

**`remedyType` tách (mới, 19/07/2026) — thay `SEIZE_PENALTY` gộp chung:** ledger cũ dùng một `entryType = SEIZE_PENALTY` cho mọi khoản seize → không phân biệt được đâu là mất cọc, đâu là phạt vi phạm, đâu là bồi thường, và **không chặn được double recovery** (thu trùng một tổn thất qua nhiều khoản). Tách 4 loại, mỗi loại một chế tài pháp lý riêng (§2.1b), owner enum ở `bank-service-phase2-design.md`:
- `DEPOSIT_FORFEITURE` — mất cọc (BLDS 328, không cap 8%).
- `CONTRACTUAL_PENALTY` — phạt vi phạm (LTM 301, cap 8% phần bị vi phạm).
- `DAMAGES_COMPENSATION` — bồi thường (LTM 302, phải chứng minh thiệt hại; điều kiện ghi theo `damagesPolicy` §2.1b — với `COMMERCIAL_CUMULATIVE_IF_PROVEN` chỉ cần phán quyết/bằng chứng thiệt hại, không cần thoả thuận cộng dồn riêng).
- (giữ `REFUND_TO_BUYER`/`RELEASE_TO_SELLER` sẵn có cho hoàn tiền không mang tính phạt — cọc về chủ, refund leg technical-fail; không đẻ tên mới cho nghĩa đã có.)

**Remedy calculator chống double recovery:** một tổn thất không được bồi hoàn trùng qua nhiều khoản — `DEPOSIT_FORFEITURE` offset vào penalty (đã có §6.3.1 Case A), mở rộng cho `DAMAGES_COMPENSATION`; việc phạt và bồi thường có được cùng tồn tại hay không quyết bởi `damagesPolicy` (§2.1b), không phải calculator tự quyết. Calculator gắn mọi legs vào **một `remedyDecisionId`** — xem invariant ở §7.2 (`remedy.finalized`).

`contract.terminated` — publisher `contract-service`, bắn 1 lần mỗi termination. **Phân vai event:** `remedy.finalized` là nguồn **DUY NHẤT** cho mọi hậu quả tiền (escrow→bank legs) và reputation; `contract.terminated` chỉ mang **trạng thái** cho audit + analytics + notification, KHÔNG kích seize/refund/lock nào. Payload canonical: `{contractId, terminationType, requestedBy, finalBreachingRole, breachReasonCode, remedyDecisionId, breachCaseId, affectedMilestoneIds, supersededByContractId, replacesContractId}`; nullable fields vẫn xuất hiện trên wire. `WITHDRAW_OFFER` giữ state `WITHDRAWN` và chỉ map sang field `terminationType` trên lifecycle observation, không có remedy leg. Chi tiết catalog — §7.2.

**Terminal publication guard (freeze 20/07/2026):** `executeTermination`/mutual confirmation chỉ persist decision + publish `remedy.finalized`; contract hiện tại giữ state non-terminal (`ACTIVE`, hoặc pending state đã có cho replacement/activation). Một idempotent completion reconciler đọc ledger qua existing `GET /internal/v1/bank/ledger?contractId=...`, đối chiếu đúng `remedyDecisionId`/toàn bộ `remedyLegId`, và tính remaining lock từ append-only entries. Chỉ khi mọi expected leg đã thành công **và** remaining lock = `0.00` mới được commit `TERMINATED` rồi publish `contract.terminated`. Bank leg fail/missing giữ contract non-terminal và retry/alert đúng policy T5; lifecycle event không được publish sớm. `WITHDRAW_OFFER` là ngoại lệ pre-sign không có lock: commit `WITHDRAWN` và có thể publish lifecycle `contract.terminated` ngay vì zero-lock đúng theo construction.

**Đã review + chốt (19/07/2026):** trigger theo `finalBreachingRole` khớp §6.2 (Rổ A/B) — mutual/FM/technical → `NULL` → không phạt; chỉ breach có attribution mới seize. `remedyType` tách khớp `bank-service` §2. Sẵn sàng sync sang bank/reputation/analytics.

---

## 6b. Milestone Funding Failure (mới, 19/07/2026)

**Lỗ hổng đối xứng đã phát hiện khi rà attribution:** §3.1 defend **rất kỹ** nhánh funding fail lúc *activation* (khoá cọc sau `SIGNED` fail → retry per-leg → `ACTIVATION_REFUND_PENDING` → `ACTIVATION_FAILED`). Nhưng nhánh **lock `batchAmount` cho milestone thứ 2+** khi milestone trước vừa `SETTLED` (§6.2/§7.2 chỉ ghi "lock sớm", không định nghĩa fail) thì **không có** flow tương đương. Đây chính là **buyer funding failure giữa hợp đồng** — kịch bản buyer-breach phổ biến nhất (Doc01: buyer ép giá/chậm vốn khi giá xuống), mà lại là chỗ trống. Bất đối xứng ngay trong chính design: activation-fail defend 5 lớp, milestone-fail defend 0 lớp.

**Fix — tái dùng nguyên khuôn `ACTIVATION_REFUND_PENDING`, không phát minh cơ chế mới.** `EscrowMilestone.status` thêm 3 giá trị đầu chu kỳ funding:

| State | Nghĩa |
|---|---|
| `FUNDING_PENDING` | **Mới.** Milestone trước `SETTLED`, hệ đã yêu cầu buyer lock `batchAmount` milestone này, chờ `bank.lock_completed`. Đứng **trước** `LOCKED` trong lifecycle |
| `FUNDING_FAILED` | **Mới.** Hết retry mà buyer không nạp được tiền. Từ đây: `RetryMilestoneFunding` → quay lại `FUNDING_PENDING`, hoặc hết cure window → buyer breach (bước 4) |
| `LOCKED` | **Giữ nguyên tên và nghĩa cũ** (§2.3 — không đổi tên: `PROVISIONALLY_RELEASED` và các ref khác đều định nghĩa tương đối với `LOCKED`). `FUNDING_PENDING → LOCKED` khi `bank.lock_completed` về |

**Cơ chế (đối xứng với §3.1):**
1. Milestone trước `SETTLED` → escrow yêu cầu lock `batchAmount` milestone kế → `FUNDING_PENDING`. Fail → escrow retry `depositLockRetryMaxAttempts` (3 lần, backoff 5m/30m/2h, cùng `sourceEventId` — §8), hứng lỗi tạm thời.
2. Hết retry → `escrow.milestone_funding_failed {contractId, milestoneId, reason}` → milestone `FUNDING_FAILED`; notify Buyer+Seller+Admin (`notification.milestone_funding_status_requested`, statusType `FUNDING_FAILED` — command riêng 3 statusType, xem `notification-service-phase2-design.md` §4; contract-service publish sau khi consume `escrow.milestone_funding_failed`). **Không seize gì ngay** — chưa kết luận buyer breach cho tới khi qua window sửa.
3. **Invariant bảo vệ seller (mới; cơ chế dữ liệu bổ sung 19/07 lần 2 — "tạm dừng đồng hồ" phải có field, không chỉ prose):** seller **không có nghĩa vụ** chuẩn bị/giao milestone kế khi funding của nó chưa `LOCKED`. `expectedDeliveryDate` là ngày immutable đã ký — không "pause" được; thay vào đó Milestone thêm 2 field: `fundingDelayBusinessDays = businessDaysBetween(fundingRequestedAt, fundingLockedAt)` (tính khi funding `LOCKED` xong) và **`effectiveDeliveryDeadline = expectedDeliveryDate + fundingDelayBusinessDays + graceDays`**. Mọi timeout seller (§3.2 `IN_PROGRESS`, `SELLER_WEIGHED`) so với `effectiveDeliveryDeadline`, KHÔNG so `expectedDeliveryDate` thô — nếu không, timer vừa resume đã coi seller quá hạn ngay, đúng lỗi "phạt oan seller" mà mục này định vá. Không bắt seller "đoán" buyer sẽ nạp tiền rồi gom hàng trước.
4. **Rổ A — sau window sửa, buyer thành breach:** hết `fundingCureWindowDays` (§8) mà buyer vẫn `FUNDING_FAILED` → **đây là Rổ A** (mốc thời gian qua + sự kiện vắng mặt "no lock" + đã cho window): buyer breach về funding, `finalBreachingRole = BUYER`, `breachReasonCode = FUNDING_FAILURE` — máy tự kết luận, **không cần dựng `BreachCase`**, nhưng vẫn phát **`remedy.finalized` với `breachCaseId = null`, `decisionSource = SYSTEM`** (§7.2 — mọi hậu quả tiền/reputation đi qua đúng một canonical decision event, kể cả Rổ A). **Loại trừ (19/07 lần 2):** fail do lỗi hệ thống/bank (kill switch `system_lock ACTIVE`, incident bank được Admin xác nhận) **không tính vào cure window** — đó không phải buyer không có tiền; Admin `RetryMilestoneFunding` sau khi hệ phục hồi, không attribution ai. Seller có quyền: (a) yêu cầu terminate phần còn lại → `TERMINATION_FOR_BREACH` (buyer), seize `buyerDepositRate` + `batchAmount` đang lock nếu có; hoặc (b) chờ tiếp nếu muốn giữ quan hệ. **Seller KHÔNG bị bắt bấm `cancel()` rồi tự ăn phạt** — đúng lỗi gốc §6 muốn tránh.
5. Admin có `RetryMilestoneFunding(contractId, milestoneId)` — mirror `RetryDepositLock`, kích lại chu kỳ lock sau khi buyer xác nhận nạp được (vd bank khôi phục). Refund leg đã lock (nếu có) theo đúng khuôn `ACTIVATION_REFUND_PENDING`: `REFUND_TO_BUYER`, không penalty cho tới khi qua bước 4.

Event mới: `escrow.milestone_funding_failed` (§7.1). `fundingCureWindowDays` — `application.yml`, mặc định 3 ngày làm việc (§8).

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
| RabbitMQ | `milestone.settled` | contract-service | escrow-service, analytics-service, audit-service |
| RabbitMQ | `milestone.cancelled_with_penalty` | contract-service — chỉ là outcome sau attribution, không phải money trigger | analytics-service (`fact_milestone_performance`), audit-service |
| RabbitMQ | `milestone.dispute_resolved` (mới, 08/07/2026) | contract-service — bắn khi `DisputeRoutingService` ra phán quyết. Payload: `{contractId, milestoneId, flaggedBy: BUYER, flaggedByUserId, resolutionFavors: BUYER\|SELLER}` | reputation-service (đếm tỷ lệ buyer flag-rồi-thua) |
| RabbitMQ | `escrow.milestone_funding_failed` (mới, 19/07/2026) | escrow-service — bắn khi hết retry lock `batchAmount` milestone 2+ (§6b bước 2). Payload: `{contractId, milestoneId, reason}` | contract-service (milestone `FUNDING_FAILED`, tạm dừng đồng hồ giao hàng, notify 3 bên; Rổ A breach sau `fundingCureWindowDays`) |

**Sửa (08/07/2026) — `milestone.buyer_confirmed` không được trigger tiền:** buyer `CONFIRM_CLEAN` → milestone `SETTLED` → contract-service publish `milestone.settled`; đây là nguồn trigger tiền DUY NHẤT. `milestone.buyer_confirmed` chỉ vào audit trail; mail trạng thái đi qua `notification.milestone_status_requested`. Escrow-service tuyệt đối không nghe event confirmed, tránh release hai lần.

**Cập nhật (04/07/2026) — payload `milestone.settled` mở rộng:** payload canonical bắt buộc `{contractId, milestoneId, lockedAmount, actualAmount, recipients}`. `contract-service` tính `actualAmount`; escrow-service kiểm tra `lockedAmount >= actualAmount`, phát `RELEASE_TO_SELLER(actualAmount)` và nếu phần chênh dương thì `REFUND_TO_BUYER(lockedAmount - actualAmount)`. **Bổ sung contract notification (16/07/2026):** payload còn mang `recipients[{userId,email,role}]` để audit-service — vốn là pure consumer, không Feign ngược user-service — có thể publish `notification.milestone_anchor_requested` sau khi OTS proof sẵn sàng. Các consumer tiền/analytics bỏ qua contact field; schema không cho phép thêm PII tùy ý.

**Sửa (08/07/2026) — `actualAmount` phải áp cả tolerance split của Delta 2 (§4):** `contract-service` (không phải `escrow-service`) tính `actualAmount` cuối cùng, đã áp công thức chia tolerance khi Delta 2 vượt ngưỡng (§4) — `escrow-service` chỉ nhận số cuối và tính `diff` như cơ chế sẵn có, không tự tính lại tolerance split.

**Cập nhật (06/07/2026) — 2 event mới cho provisional settlement Level 2 (§3.2):**

| Loại | Tên | Publisher | Consumer(s) |
|---|---|---|---|
| RabbitMQ | `milestone.level2_provisional_settled` | contract-service — bắn khi hết `level2BufferWindowDays` mà report Level 2 chưa `CONFIRMED`, đã commission Level 1.5 fallback và settle tạm. Payload: `{contractId, milestoneId, level1_5ReportId, level1_5EntitlementAmount, releaseRate, sellerReleaseAmount, remainingLockedAmount}` | escrow-service (validate conservation, release `sellerReleaseAmount`, không refund buyer) |
| RabbitMQ | `milestone.level2_buffer_reconciled` | contract-service — bắn khi report Level 2 thật về sau đó (`CONFIRMED`). Payload: `{contractId, milestoneId, level1_5ReportId, level2ReportId, finalSellerEntitlementAmount, alreadyReleasedAmount, sellerAdditionalReleaseAmount, buyerRefundAmount, overReleaseAmount}` | escrow-service (validate conservation, phát release/refund legs dương) |
| RabbitMQ | `milestone.level2_terminal_settled` (mới, 06/07/2026) | contract-service — bắn khi hết `level2BufferTerminalDays` mà report Level 2 vẫn chưa `CONFIRMED`, phán quyết Level 1.5 tự động thành chung thẩm. Payload: `{contractId, milestoneId, level1_5ReportId, finalSellerEntitlementAmount, alreadyReleasedAmount, sellerAdditionalReleaseAmount, buyerRefundAmount}` | escrow-service (validate conservation, phát release/refund legs dương) |

### 7.2 Contract-level (mới, 04/07/2026; bổ sung `contract.signed` + consumer analytics-service, 06/07/2026)

| Loại | Tên | Publisher | Consumer(s) |
|---|---|---|---|
| RabbitMQ | `contract.signed` | contract-service — **mới (06/07/2026, phát sinh từ session `analytics-service`); payload mở rộng (08/07/2026, phát hiện qua rà end-to-end)**. Bắn đúng 1 lần khi `Contract.transitionTo(SIGNED)` xảy ra — trigger cụ thể là bước 4 của `VerifyOtpAndSign` (`signature-phase2-design.md` §6, bước 5 nối tiếp cũng publish event này, tách biệt với việc push `signedContentHash` riêng cho audit-service). Payload: `{contractId, commodity, buyerId, sellerId, totalAmount, buyerDepositAmount, sellerDepositAmount, signedContentHash, signedAt}` (`signedContentHash` thêm 17/07/2026 — audit-service consume chính event này để nối chain `CONTRACT_SIGNED`, thay "đường push riêng" cũ; hash-chain §2.4). `commodity` là enum cứng `COFFEE / RICE / RUBBER / CASHEW`. contract-service đọc `Category.commodity` của category gắn với sản phẩm → publish thẳng (không có case `NULL` vì category chỉ dùng được khi `APPROVED` và `approve()` bắt buộc gán `commodity`). **Cơ chế `Category`/`commodity` (redesign Phase 1, vì sao bỏ bảng mapping, quan hệ 2 tầng) chốt ở `product-phase2-design.md` §9 (owner) — không mô tả lại ở đây.** | analytics-service (populate `dim_contract`, `analytics-service-phase2-design.md` §2.1/§3.1), **escrow-service (mới, 08/07/2026 — trigger lock, xem ghi chú payload ngay dưới)**, **audit-service (mới, 17/07/2026 — `source_type = CONTRACT_SIGNED`, hash-chain §2.4)** |

**Sửa (08/07/2026) — payload thiếu dữ liệu cho chính consumer của nó:** bản 06/07/2026 chỉ mang `totalAmount`, không mang `buyerDepositRate`/`sellerDepositRate`. `escrow-service` **cũng** consume event này (để lock cọc lúc `SIGNED`, xem `escrow.deposit_locked` §7.2 ngay dưới) nhưng không có đường nào lấy được 2 rate đó — nó là *pure event consumer*, không Feign ngược theo đúng nguyên tắc đã chốt. Không tính nổi số tiền cần khoá. **Chốt:** mang thẳng `buyerDepositAmount`/`sellerDepositAmount` đã tính sẵn (= rate × `totalAmount`, tính ở contract-service — nơi có đủ `ContractTerms`) thay vì mang rate thô, để escrow-service không phải tự nhân và 2 service không lệch cách làm tròn số. `sellerDepositAmount = 0` nếu `sellerDepositRate` không đàm phán (giữ default) — escrow-service chỉ bắn thêm `LedgerEntry` cho seller deposit khi amount `> 0`. `analytics-service` dùng `totalAmount`, không cần 2 field mới này.
| RabbitMQ | `contract.settled` | contract-service (`ContractSettledEvent`, guard fix §3.1) | reputation-service (input positive), analytics-service (`fact_contract_settlement`), audit-service; **không escrow** |
| RabbitMQ | `contract.terminated` (mới, 19/07/2026 — **thay `contract.cancelled`**) | contract-service — bắn 1 lần mỗi termination. Payload canonical gồm `{contractId, terminationType: WITHDRAW_OFFER\|MUTUAL_TERMINATION\|MUTUAL_REPLACEMENT\|TERMINATION_FOR_BREACH\|TERMINATION_FOR_FORCE_MAJEURE\|ACTIVATION_FAILURE, requestedBy, finalBreachingRole, breachReasonCode, remedyDecisionId, breachCaseId, affectedMilestoneIds, supersededByContractId, replacesContractId}`; nullable fields vẫn xuất hiện | **Chỉ state/quan sát:** audit-service, analytics-service, notification (qua command). **Không escrow, không reputation** — mọi hậu quả đi qua `remedy.finalized` duy nhất |
| RabbitMQ | `breach.reported` (mới, 19/07/2026) | contract-service — bắn khi `BreachCase` mở (§6.4). Payload: `{breachCaseId, contractId, milestoneId?, requestedBy, allegedBreachingRole?, breachReasonCode, severity}` | audit-service (`source_type = BREACH_REPORTED`, hash-chain); **KHÔNG** reputation/escrow (chưa attribution — §6.4 invariant) |
| RabbitMQ | `remedy.finalized` (canonical) | contract-service — **nguồn DUY NHẤT cho tiền + reputation**, bắn cho mọi final/no-fault decision, gồm normal settlement. Payload bắt buộc `{remedyDecisionId, attributionDecisionId, breachCaseId, contractId, buyerId, sellerId, affectedMilestoneIds, finalBreachingRole, breachReasonCode, decisionSource, penaltyEligible, reputationEligible, remedyLegs:[{remedyLegId, remedyType, fundType, role, amount}]}`; nullable fields vẫn xuất hiện. | escrow-service (thực thi legs), reputation-service (negative lock khi `reputationEligible=true`), audit-service |
| RabbitMQ | `escrow.deposit_locked` (mới, 08/07/2026) | escrow-service — kế thừa `escrow.buyer_locked` Phase 1, mở rộng cho cả 2 cọc. Bắn khi **cả 2 khoản cọc cần khoá** đã confirm xong với bank-service (`bank.lock_completed`, `bank-service-phase2-design.md` §3) — nếu `sellerDepositAmount = 0` thì chỉ chờ xác nhận khoá cọc buyer. Payload: `{contractId, buyerDepositState, sellerDepositState}`. | contract-service (`transitionTo(ACTIVE)` — xem ghi chú dưới), escrow-service tự dùng làm tín hiệu nối tiếp lock `batchAmount` milestone đầu tiên (§6.2) |
| RabbitMQ | `escrow.deposit_lock_failed` (mới, 17/07/2026) | escrow-service — bắn khi hết `depositLockRetryMaxAttempts` mà khoá cọc vẫn fail (§3.1 nhánh fail). Payload: `{contractId, failedLeg: BUYER|SELLER|BOTH, buyerDepositState, sellerDepositState, reason}` (leg states thêm 18/07/2026 — partial success) | contract-service (giữ `SIGNED`, publish `notification.contract_activation_failed_requested`; Admin xử lý qua `RetryDepositLock`/`MarkActivationFailed`; terminal đi qua `ACTIVATION_REFUND_PENDING` chờ refund confirm — §3.1) |

**Sửa (08/07/2026) — event kích hoạt ACTIVE biến mất khỏi Event Catalog:** luồng ký→kích hoạt mô tả *"contract-service nhận xác nhận cọc đã khoá → Contract `ACTIVE`"* nhưng không có event nào đi chiều escrow→contract để mang xác nhận này — Phase 1 có `escrow.buyer_locked` làm việc đó, rà lại thấy nó biến mất khỏi catalog Phase 2. Hệ quả kép trước khi sửa: (1) contract-service không có tín hiệu để chuyển `ACTIVE`; (2) escrow-service không có tín hiệu để lock `batchAmount` milestone đầu tiên (§6.2 ghi trigger *"Contract `ACTIVE`"* nhưng "Contract ACTIVE" không phát event nào). `escrow.deposit_locked` đóng cả 2 vai — contract-service consume để chuyển `ACTIVE`; escrow-service tự nối tiếp lock `batchAmount` milestone index 1 ngay sau khi chính nó xác nhận deposit-locked (đã ở đúng ngữ cảnh, không cần round-trip thêm qua contract-service).

**Notification commands (16/07/2026):** các event nghiệp vụ ở §7 giữ payload/domain consumer hiện có. Khi cần mail giao dịch, contract-service publish thêm `notification.contract_terminated_requested` hoặc `notification.milestone_status_requested` với recipient email + dữ liệu template. Riêng email quyết toán có OTS không gửi trực tiếp từ `milestone.settled`; audit-service publish `notification.milestone_anchor_requested` sau khi ghi chain và lấy proof (§4.3 hash-chain). `contract.delivered` không được phục hồi.

---

## 8. Config: `application.yml` vs `ContractTerms`

| Setting | Nơi lưu | Lý do |
|---|---|---|
| Escalation cap của force majeure = Level 1.5 (không Level 2) | `application.yml` | Invariant kỹ thuật — sai chuyên môn nếu lên Level 2, không nên để buyer/seller tự thoả thuận khác mỗi hợp đồng |
| `forceMajeureReportWindowDays` | `ContractTerms` (per-contract) | Độ nhạy cảm thời gian khác nhau theo mặt hàng (cà phê khô lâu hơn rau quả tươi) |
| `shortfallPenaltyThreshold`, `toleranceRate`, `sellerPenaltyRate` | `ContractTerms` (per-contract) | Negotiate theo từng hợp đồng, giống pattern `penaltyRate` Phase 1. **Guardrail (mới, 08/07/2026):** validate range lúc `sign()` — `shortfallPenaltyThreshold` ∈ [3%,15%], `toleranceRate` ∈ [0%,10%], `sellerPenaltyRate`/`buyerPenaltyRate` ∈ [0%,30%] — xem §2.1. |
| Ngưỡng giá trị/loại hàng kích hoạt Level 1.5 vs Level 2 cho dispute thường (quantity/quality) | `application.yml` | Đã quyết từ Doc2 mục 4.3 — per-deployment config |
| `sellerResponseWindowDays` (mặc định 2 ngày làm việc) | `application.yml` | **Chính thức hoá (04/07/2026):** trước đây chỉ ghi "2 ngày làm việc" trong prose ở §3.2, chưa đặt tên config. Invariant kỹ thuật — không có lý do hợp đồng khác nhau cần cửa sổ phản hồi khác nhau. |
| `buyerConfirmWindowDays` (default 2, range [2,10] ngày làm việc) | `ContractTerms` (per-contract — **chuyển 19/07/2026**, trước ở `application.yml`) | **Mới (04/07/2026); per-contract từ 19/07:** buyer im lặng ở `BUYER_RECEIVED` quá thời gian này → auto `CONFIRM_CLEAN`, nhưng KHÔNG chạy khi inspection pending (§3.2 bổ sung 19/07). Per-contract vì testing profile khác nhau theo commodity — cùng pattern `graceDays`. |
| `buyerReceiveWindowDays` (mặc định 2 ngày làm việc) | `application.yml` | **Mới (08/07/2026):** vá lỗ hổng `SELLER_WEIGHED` không timeout (§3.2) — hết cửa sổ này mà buyer chưa cân nhận → notify, im lặng tiếp → Admin/Level 1 quyết theo bằng chứng hiện có, **không** auto-settle theo số seller tự khai. Invariant kỹ thuật, không cần khác nhau theo hợp đồng. |
| `graceDays` (giao hàng, per-`MilestoneTerm`) | `ContractTerms`/`MilestoneTerm` (per-contract) | **Mới (08/07/2026), chốt:** số ngày ân hạn sau `expectedDeliveryDate` trước khi coi seller quá hạn (§2.1, §3.2). Để per-contract vì độ nhạy cảm thời gian khác nhau theo mặt hàng (cà phê khô lâu hơn rau quả tươi) — cùng lý do `forceMajeureReportWindowDays` đã để per-contract. |
| `level2BufferWindowDays` (10 ngày làm việc) | `application.yml` | **Chốt (13/07/2026):** neo theo benchmark lab test chất lượng chuẩn SCA (5 ngày làm việc/mẫu, `[REFERENCE]`) + buffer lịch hẹn/vận chuyển mẫu. Hết window mà chưa có report `CONFIRMED` → commission Level 1.5 fallback, settle tạm theo mechanics 3 bước (§3.2). Tinh chỉnh được khi có dữ liệu vận hành thật. |
| `level2SafetyBufferRate` (15%) | `application.yml` | **Chốt (13/07/2026):** % `batchAmount` giữ khoá khi settle tạm theo Level 1.5, chờ report Level 2 thật đối chiếu (§3.2). Chọn mức trên của dải cân nhắc (10-15%), nghiêng bảo vệ buyer — bên gánh rủi ro nếu Level 2 lật. Tinh chỉnh được khi có variance vận hành thật. |
| `level2BufferTerminalDays` (30 ngày) | `application.yml` | **Chốt (13/07/2026; payload freeze 18/07):** hết hạn tính từ lúc `level2BufferWindowDays` hết mà vẫn chưa có report `CONFIRMED` → phán quyết Level 1.5 tự động thành chung thẩm; contract-service phát explicit seller additional release + buyer refund legs (§3.2). |
| `depositLockRetryMaxAttempts` (3, backoff 5m/30m/2h) | `application.yml` | **Mới (17/07/2026):** số lần escrow-service retry khoá cọc sau `SIGNED` trước khi bắn `escrow.deposit_lock_failed` (§3.1). Invariant kỹ thuật, không cần khác theo hợp đồng. Cùng số retry/backoff áp cho lock `batchAmount` milestone (§6b). |
| `fundingCureWindowDays` (3 ngày làm việc) | `application.yml` | **Mới (19/07/2026):** cửa sổ buyer khắc phục funding milestone sau `FUNDING_FAILED` trước khi thành buyer breach Rổ A (§6b bước 4). Trong lúc này đồng hồ giao hàng của seller tạm dừng. Invariant kỹ thuật. |
| `sellerDepositRate` (mặc định `0`, optional) | `ContractTerms` (per-contract) | **Mới (06/07/2026):** thay quyết định "bỏ hẳn" trước đây — đàm phán per-contract giữa buyer/seller lúc `NEGOTIATING`, không phải invariant kỹ thuật (§2.1, §6.1). |
| `disputeFloorReleaseRate` (mặc định `50%`, optional) | `ContractTerms` (per-contract) | **Mới (10/07/2026):** tỷ lệ **sàn** release provisional Level 2 khi buyer từ chối/im lặng opt-out (§3.2 Bước 0/1). Đàm phán per-contract, guardrail `[50%, (1 − level2SafetyBufferRate)]` (§2.1). Để per-contract vì mức chấp nhận rủi ro của từng buyer khác nhau — cùng triết lý neutral-party với `sellerDepositRate`. |
| `disputeOptOutWindowDays` (mặc định 2 ngày làm việc) | `application.yml` | **Mới (10/07/2026):** cửa sổ buyer trả lời opt-out ở Bước 0 provisional Level 2 (§3.2). Hết cửa sổ, buyer im lặng → default **sàn** `disputeFloorReleaseRate` (nghiêng bảo vệ buyer, ngược với `buyerConfirmWindowDays` default `CONFIRM_CLEAN` — lý do ở §3.2). Invariant kỹ thuật, không cần khác theo hợp đồng. |

---

## 8b. Enhancement ngoài scope (ghi nhận 18/07/2026, không làm Phase 2)

- **Shipment tracking (logistics):** hàng commodity chạy vận tải thuê ngoài, không có API tracking để tích hợp — tracking tự khai không có giá trị bằng chứng và không đổi quyết định nào của hệ (mọi mốc có ý nghĩa tiền/pháp lý đã cover bởi milestone status + notification, gồm reminder `buyerReceiveWindowDays`). Enhancement: status tự khai non-authoritative hoặc tích hợp 3PL khi có đối tác vận tải thật.
- **Statement export cho Buyer/Seller** làm ở Phase 2 nhưng owner là bank/escrow — xem bank-service §5b.2 (`GET /api/v1/escrows/statements`, đọc ledger, ownership theo participant).
- **Contract amendment (18/07/2026):** Phase 2 hỗ trợ đổi điều khoản qua `MUTUAL_REPLACEMENT`/supersede (§6.6 — đóng cũ + ký mới, không sửa tại chỗ). KHÔNG hỗ trợ sửa đổi *tại chỗ* trên hợp đồng đã ký — `ContractTerms` đóng băng + hash là nền của evidence model. Roadmap production: `ContractVersion` chain — amendment v2 mang `previousVersionHash` nối bản trước, cần đủ 2 chữ ký, **không được** sửa milestone đã `SETTLED`; cần design session riêng (đụng signature flow, hash chain, recalc escrow), không nhét vào Phase 2.

## 8c. Known Limitations — nghiệp vụ có thật, hoãn có chủ đích (mới, 19/07/2026)

Các mẫu nghiệp vụ dưới đây được literature/standard-contract xác nhận là **có thật** (UNIDROIT/FAO/IFAD Legal Guide, CISG, GAFTA/ESCC, các nghiên cứu contract-farming Việt Nam), nhưng build đầy đủ trong 5 tháng là viết lại nửa hệ thống và **không cần cho luồng vàng demo**. Ghi rõ để defend trước hội đồng — nhận diện được + có nguồn + có lý do hoãn mạnh hơn cố code rồi vỡ:

- **Full breach state machine (notice → cure → remedy selection):** `BreachCase` Phase 2 là bản rút gọn (REPORTED→UNDER_REVIEW→RESOLVED, §6.4). Đầy đủ có right-to-cure, remedy tiered (buộc thực hiện đúng / thay thế / giảm giá / mua bù / chấm dứt từng phần) theo UNIDROIT Ch.5-6 + CISG Điều 73. Hoãn — cần design session riêng.
- **Quality-settlement engine (giảm giá theo grade/độ ẩm/tạp chất):** dispute chất lượng Phase 2 dừng ở "inspector phán accept X kg giá gốc, reject phần còn lại" (nhị phân). Đầy đủ cần số hoá thang quy đổi grade/moisture/deduction theo từng chuẩn ngành (SCA cà phê, TCVN gạo) + `settlementUnitPrice`/`priceAdjustmentAmount` + chain of custody (sample/seal/witness, ISO 17020/17025, ESCC). Hoãn — cần chuyên gia ngành + tiêu chuẩn thật, ngoài phạm vi lập trình.
- **INDEXED/HYBRID pricing + price reopener (giá tự chạy theo chỉ số thị trường):** `agreedPrice` Phase 2 cố định. Giá thả nổi theo VNSAT/chỉ số + reopener tự động khi biến động vượt ngưỡng (UNIDROIT price mechanisms; sốc giá cà phê Doc01 §1.1) **phá invariant hợp đồng-bất-biến** mà cả evidence model dựng lên → cần `ContractVersion` (§8b). Hoãn. **Lưu ý:** đổi giá *thiện chí* vẫn làm được ngay qua `MUTUAL_REPLACEMENT` (§6.6) — chỉ *tự-động-theo-chỉ-số* mới hoãn.
- **PaymentTerms / SellerSecurityPackage đầy đủ:** Phase 2 có `buyerDepositRate`/`sellerDepositRate` (cash) + payment-per-milestone. Đầy đủ gồm advance/post-delivery mode, late-payment interest, và 9 loại security thay cash deposit (cooperative/third-party guarantee, retention, receivable assignment... — UNIDROIT). Hoãn; `sellerDepositRate` optional hiện tại là bản tối giản đủ dùng.
- **Conflict-of-interest enforce bằng org graph:** Phase 2 chống xung đột (Software Buyer = Platform Buyer) bằng prose + maker-checker + audit + phân phối qua hiệp hội trung lập (governance §5). Enforce **tự động** (máy tự route vụ khỏi OPERATOR cùng tổ chức buyer) cần `organizationId`/`deploymentId`/org-relationship graph — chỉ có ý nghĩa ở quy mô nhiều tổ chức thật, vô nghĩa trong mock. Hoãn (SF-07: completeness ≠ fairness).
- **Anticipatory breach / adequate assurance (CISG 71-73):** khi một bên có dấu hiệu sắp vi phạm nhưng chưa vi phạm — suspend → đòi cam kết → resume/terminate. Cố tình out-of-scope: luật VN nội địa chưa có cơ chế assurance tương đương UCC/CISG; áp SLA cứng (vd 30 ngày UCC) là copy sai bối cảnh. Ghi nhận là hướng cần legal review.


## 9. Status — Milestone Escrow Design

**Chốt (02/07/2026):** `shortfallPenaltyThreshold` = 5%. `forceMajeureReportWindowDays` = 3 ngày. Seller có quyền contest REJECT, đối xứng với buyer, cùng cap Level 1.5. `Milestone` = aggregate riêng. `dispute()` cấp Contract bị bỏ. `cancel()` cấp Contract = pro-rata + penalty debt + lockout, áp cho cả seller lẫn buyer (§6). *(19/07/2026: `cancel()` một-nút thay bằng termination taxonomy 6 hành vi §6.1 — pro-rata/penalty debt/lockout giữ nguyên cơ chế nhưng chỉ kích sau attribution `finalBreachingRole`, không theo người bấm nút.)* `buyerDepositRate` = 5% `totalAmount`, lock lúc `SIGNED`. Event catalog theo convention `escrow.buyer_locked` đã có. công thức `lockDurationDays` + baseline chốt ở `reputation-service-phase2-design.md` §4 (owner), escrow chỉ giữ input/trigger. `batchAmount` lock sớm — ngay khi milestone trước `SETTLED`.

**Chốt bổ sung (04/07/2026):**
- **Local Outbox Pattern thay Spring `ApplicationEvent`** cho đồng bộ `Milestone` → `Contract` (§2.2) — sửa đúng bug crash-window đã phát hiện, giữ nguyên tinh thần local/không RabbitMQ.
- **Guard `Contract.settle()` cần fix** để chạy được từ `ACTIVE` thay vì chỉ `DELIVERED` (§3.1) — kèm dọn dead path `confirmDelivery()`/`ContractDeliveredEvent` khi implement. Phát hiện qua đối chiếu chéo với `reputation-service-phase2-design.md` KI-1.
- **Buyer timeout ở `BUYER_RECEIVED`** — `buyerConfirmWindowDays` = 2 ngày làm việc, mặc định `CONFIRM_CLEAN` nếu buyer im lặng (§3.2, §8).
- **`buyerDepositRate` — 3 đường release/seize chính thức hoá** qua `remedy.finalized` legs. `contract.settled`/`contract.terminated` chỉ là lifecycle events và không có escrow money consumer (§6.7, §7.2).
- **Payload `milestone.settled` mở rộng** thêm `lockedAmount`/`actualAmount` để escrow-service tự tách `RELEASE_TO_SELLER` + `REFUND_TO_BUYER` khi Delta 1/2 pro-rata làm số tiền thực nhận thấp hơn số đã khoá (§7.1).

**Chỉ còn 1 điểm để ngoài phạm vi có chủ đích, không phải thiếu sót:** state machine đầy đủ của `reputation-service` ngoài phần input/trigger đã chốt — thiết kế chi tiết nằm ở `reputation-service-phase2-design.md` (session riêng, 04/07/2026).

**Chốt bổ sung (06/07/2026; payload freeze 18/07) — provisional settlement khi `CONTESTED` escalate Level 2 (§3.2, §7.1, §8):** hết `level2BufferWindowDays` chưa có Level 2 report → dùng Level 1.5 entitlement `X15`, release `X15 × releaseRate`, không refund buyer và giữ phần còn lại. Reconcile/terminal luôn mang explicit seller-release/buyer-refund legs; contract-service tính, escrow validates conservation, bank chỉ ghi positive commands. `level2BufferTerminalDays` giữ điểm dừng cứng 30 ngày.

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

**Chốt bổ sung (17/07/2026) — nhánh khoá cọc fail sau `SIGNED` (§3.1, §7.2, §8):** đóng điểm treo B5 để lại ("cần định nghĩa khi implement, hiện chưa có"): escrow retry 3 lần backoff → `escrow.deposit_lock_failed` → Contract giữ `SIGNED`, notify Buyer+Seller+Admin qua `notification.contract_activation_failed_requested`, Admin `RetryDepositLock`/`MarkActivationFailed` (state terminal `ACTIVATION_FAILED`, không penalty/reputation — không bên nào phá kèo, gồm cả case kill switch ACTIVE). Kèm 2 sửa cross-service: payload `contract.signed` mang thêm `signedContentHash` + audit-service thành consumer (thay đường push riêng — hash-chain §2.4); notification command mới vào catalog notification §4.

**Chốt bổ sung (19/07/2026) — viết lại attribution/termination từ file research ScholarFirst (đợt 6):**
- **Termination taxonomy** thay `cancel(initiatedBy)` một-nút: 6 hành vi (`WITHDRAW_OFFER`/`MUTUAL_TERMINATION`/`MUTUAL_REPLACEMENT`/`TERMINATION_FOR_BREACH`/`TERMINATION_FOR_FORCE_MAJEURE`/`ACTIVATION_FAILURE`), §6.1. Tách `requestedBy` ≠ `allegedBreachingRole` ≠ `finalBreachingRole` — chỉ `finalBreachingRole != NULL` mới seize/lock (§6.0).
- **Ranh giới tự-thực-thi Rổ A/Rổ B** (§6.2) — timeout + sự kiện vắng mặt đo được → seize ngay (giữ value prop "escrow có răng"); cần phán đoán chất lượng/nguyên nhân → `BreachCase` chờ attribution. Đối xứng buyer/seller.
- **`BreachCase`** aggregate rút gọn trong contract-service (§6.4) + `breachReasonCode` taxonomy (§6.4.1) — Delta 1 vượt threshold không auto-gán bẻ kèo nữa (§4).
- **`MUTUAL_REPLACEMENT`/supersede** (§6.6) — đổi giá thiện chí không còn bị ép "huỷ-có-phạt rồi ký lại"; đóng cũ `SUPERSEDED` sạch + ký mới, cấm sửa tại chỗ.
- **Milestone Funding Failure** (§6b) — vá lỗ hổng đối xứng: buyer bỏ funding milestone 2+ (`FUNDING_PENDING`/`FUNDING_FAILED` mới, `LOCKED` giữ nguyên), seller không bị coi trễ khi funding chưa xong, buyer breach Rổ A sau `fundingCureWindowDays`.
- **`LegalProfile` + cap 8%** (§2.1b) — cọc (BLDS 328, thoát cap) / phạt (LTM 301, cap 8% phần bị vi phạm) / giám định (LTM 266, 10× phí) / bồi thường (LTM 302, chứng minh thật); `remedyType` tách 4 loại chống double recovery (§6.7). Phân loại cọc cần luật sư xác nhận (ghi rõ Known Limitation).
- **Event catalog**: `contract.cancelled` → `contract.terminated` (mang `terminationType`/`finalBreachingRole`); thêm `breach.reported`, `remedy.finalized`, `escrow.milestone_funding_failed` (§7).
- **Known Limitations §8c** — quality-settlement engine, INDEXED pricing, PaymentTerms/SellerSecurityPackage đầy đủ, conflict-of-interest org graph, anticipatory breach: có nguồn, hoãn có chủ đích.
- **Review lần 2 cùng ngày (19/07):** damagesPolicy enum thay boolean (Điều 307 LTM ≠ 418.3 BLDS — default cộng dồn khác nhau theo governingLaw, citation 316 cũ sai); `remedy.finalized` canonical hoá thành nguồn DUY NHẤT cho tiền+reputation với `remedyDecisionId` + `penaltyEligible`/`reputationEligible` (Rổ A phát với breachCaseId=null/SYSTEM, contract.terminated chỉ còn state/audit/analytics/notification — chặn double-consume); `effectiveDeliveryDeadline = expectedDeliveryDate + fundingDelayBusinessDays + graceDays` (pause phải có field); Rổ A FAILURE_TO_RECEIVE cần objective delivery evidence, thiếu → Rổ B; MUTUAL_TERMINATION chốt full-scope; ACTIVATION_FAILURE giới hạn lỗi kỹ thuật trước ACTIVE, bank-outage không tính cure window.
- **Review lần 3 cùng ngày (19/07):** `AttributionDecision` §6.4b — điểm hội tụ Rổ A/Rổ B (Rổ A tạo với `breachCaseId=NULL, decisionSource=SYSTEM`, hết cảnh "có attribution nhưng không có đường sinh termination hợp lệ"); gỡ explicit repudiation khỏi Rổ A (anticipatory breach ngoài scope thật — đường Rổ B với tuyên bố làm evidence); Case B penalty debt sửa thành `CONTRACTUAL_PENALTY_CLAIM` Điều 300-301 (bản cũ gọi nhầm Điều 302 — sai loại chế tài tại record); **P0**: `ACTIVATION_FAILURE` seller deposit về `RELEASE_TO_SELLER` (bản cũ trả nhầm cọc seller cho buyer); supersede saga §6.6 — `REPLACEMENT_PENDING → SUPERSEDE_REFUND_PENDING → SUPERSEDED`, commit point tại SIGNED của hợp đồng mới, activation-fail sau supersede không rollback.
- **Cần sync sang (đã sync xong cùng ngày, gồm review lần 2 + lần 3):** bank-service (remedyType enum + fundType + remedyDecisionId), reputation-service (input DUY NHẤT `remedy.finalized`, đọc `reputationEligible`, `remedy_decision_id` UNIQUE), analytics (`fact_contract_termination` đọc từ `contract.terminated`), hash-chain (source_type `BREACH_REPORTED`/`REMEDY_FINALIZED`/`CONTRACT_TERMINATED`), notification (4 command), verification-matrix (55 rows — +11p supersede crash, +11q audit dedup), hash-chain (source_event_id UNIQUE + anchor outbox), analytics (rolling recompute D-7..D-1). SDS/Architecture regeneration is a downstream publication step after this frozen contract set, not an unresolved design decision.

Milestone Escrow — **ĐÓNG SESSION HOÀN TOÀN, sẵn sàng đưa vào Architecture/SDS/TechnicalSpec chính thức.** Provisional settlement Level 2: cả 3 số cấu hình (`level2BufferWindowDays` = 10 ngày, `level2SafetyBufferRate` = 15%, `level2BufferTerminalDays` = 30 ngày) đã chốt theo benchmark ngành ở §8, cấu hình `application.yml` tinh chỉnh được khi có dữ liệu vận hành thật. **Đã chốt hết các quyết định thiết kế:** Delta 2 chia phần-vượt/khối-lượng (B1), `graceDays` per-contract (A4), guardrail range (B3), `lockDurationDays` nặng hơn cho cancel-ở-0-milestone **áp dụng** (§6.1 — ký xong bỏ ngay là tín hiệu xấu nhất). **`contract.signed` mapping `commodity`:** đã đóng — `commodity` đọc thẳng từ `Category.commodity`; cơ chế chốt ở `product-phase2-design.md` §9 (owner).

---

*Design session: 02/07/2026 · Cập nhật: 04/07/2026 (Local Outbox, buyer timeout, buyerDepositRate release paths) · Cập nhật: 06/07/2026 (thêm event `contract.signed` cấp Contract + đăng ký analytics-service làm consumer của `contract.settled`/`contract.cancelled`, phát sinh từ rà soát chéo `analytics-service-phase2-design.md`; thêm provisional settlement + buffer cho Level 2 chậm report, §3.2/§7.1/§8; thêm chốt hạn cứng `level2BufferTerminalDays`; thêm `sellerDepositRate` optional thay quyết định "bỏ hẳn", §2.1/§2.3/§6.1/§6.2/§6.3 — chưa đóng, còn các điểm treo) · Cập nhật: 08/07/2026 (rà soát end-to-end: payload `contract.signed` mở rộng + event `escrow.deposit_locked` thêm lại — A1/A2; mechanics đầy đủ Provisional Settlement, sửa lỗi bufferAmount — A3; `expectedDeliveryDate`/`graceDays` + 2 timeout mới `SELLER_WEIGHED`/`IN_PROGRESS` — A4; Delta 2 chia tolerance thay vì seller gánh 100% — B1; bỏ escrow-service khỏi consumer `milestone.buyer_confirmed` — B2; guardrail range cho tolerance/threshold/penalty rate — B3; định nghĩa rõ SIGNED vs ACTIVE — B5) · Cập nhật: 13/07/2026 (chốt 3 số cấu hình Level 2 buffer theo benchmark, gỡ toàn bộ trạng thái placeholder — đóng session hoàn toàn) · Cập nhật 17/07/2026 (nhánh khoá cọc fail sau SIGNED + escrow.deposit_lock_failed + ACTIVATION_FAILED; contract.signed mang signedContentHash cho audit-service) · Cập nhật 19/07/2026 (đợt 6 — termination taxonomy + BreachCase + Rổ A/B + supersede + funding failure §6b + LegalProfile/cap 8% + remedyType, từ file research ScholarFirst) · Chưa code · **Sẵn sàng đưa vào Architecture/SDS/TechnicalSpec chính thức.***
