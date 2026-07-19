---
name: reputation-service-phase2-design
description: "Reputation-service — lock ledger bất biến + reputation score, phục vụ lockout enforcement, đánh giá đối tác hai chiều, và credit reference export. Nguồn: design session 04/07/2026, cập nhật 06/07/2026 (AML: thêm nhóm tín hiệu tuyệt đối theo ngưỡng luật định), 08/07/2026 (hold tuyệt đối cần tín hiệu hành vi; đối xứng hoá buyer/seller + chống flag-abuse), 19/07/2026 (consume theo attribution — review lần 2: remedy.finalized là input negative DUY NHẤT, gỡ contract.terminated khỏi tầng chế tài chặn double-lock; gate đọc reputationEligible flag + remedy_decision_id UNIQUE, §3/§4.3)."
status: DESIGNED — chưa code.
metadata:
  type: design
  phase: 2
  extends: "services.md § reputation-service (8088)"
  related: "milestone-escrow-phase2-design.md §6 (taxonomy/BreachCase §6.4)/§7; bank-service-phase2-design.md §4; inspection-phase2-design.md; user-service-phase2-design.md §2.2/§3"
---

## 1. Bối cảnh & Scope

**Chốt (04/07/2026):** `services.md` mô tả `reputation-service` là "pure event-driven read model" — mô tả này chỉ đúng 1 phần, không phải sai hoàn toàn. Đúng nguyên tắc đã dùng ở `inspection-phase2-design.md` §3.1 (framework gốc không giữ quyết định chi tiết, doc sau override mà không cần sửa lại services.md): `reputation-service` thực chất gánh **3 vai trò khác bản chất**, không phải 1 domain đơn thuần:

1. **Lock ledger** — bất biến, ghi 1 lần, phục vụ lockout enforcement (milestone-escrow §6). Đây là phần *không* thể là pure read model.
2. **Reputation score** — view sống, tính lại được, phục vụ đánh giá đối tác hai chiều trước khi ký.
3. **Credit reference** — export cho bên thứ 3 (khả năng cao nhất: VARI, xem §6.3), vai trò bổ trợ.

Không tách 3 vai trò này thành 3 service riêng trong Phase 2 (over-engineer so với quy mô đồ án) — nhưng thiết kế domain model phải phản ánh đúng 3 loại dữ liệu khác nhau, không gộp chung logic.

---

## 2. Domain Model

### 2.1 Lock Ledger (bất biến — insert-only)

**Chốt (04/07/2026):** `lockDurationDays` snapshot cứng lúc tính, không recompute lại sau đó — kể cả khi input dùng để tính nó (`trackRecordMultiplier`) đổi giá trị sau này. Lý do: đây là bằng chứng pháp lý (Luật TM 2005 Điều 302, penalty debt dùng làm căn cứ truy đòi qua VIAC/toà nếu có tranh chấp) — 1 con số có thể tự đổi sau khi ghi thì không còn giá trị làm bằng chứng.

| Field | Loại | Ghi chú |
|---|---|---|
| `entryId` | UUID | |
| `sourceEventId` | UUID | Idempotency key cấp event. **19/07 lần 2:** nguồn duy nhất là `remedy.finalized`; thêm cột `remedy_decision_id` UNIQUE — chốt nghiệp vụ "một quyết định ≤ 1 lock" đứng ở tầng DB, mạnh hơn dedup theo eventId (redelivery cùng event thì eventId chặn; 2 event khác nhau cùng quyết định thì `remedy_decision_id` chặn). |
| `contractId` | UUID | |
| `userId` | UUID | Người bị khoá (buyer hoặc seller, tuỳ `penalizedRole`) |
| `penalizedRole` | Enum (`BUYER`, `SELLER`) | 1 event duy nhất phân biệt qua field này, không tách 2 event riêng — công thức `lockDurationDays` áp dụng y hệt cho cả 2 bên, chỉ đổi input rate |
| `baseDays` | Int | Mặc định 30 |
| `repeatOffenseMultiplier` | Decimal | 1x/2x/3x — chỉ đếm vi phạm trong cửa sổ `repeatOffenseLookbackMonths` gần nhất, xem §4.2 |
| `trackRecordMultiplier` | Decimal | 0.7x/1.0x/1.3x, gated ngưỡng 5 hợp đồng, KHÔNG có time window, xem §4.1 |
| `lockDurationDays` | Int | = `baseDays × repeatOffenseMultiplier × trackRecordMultiplier`, snapshot lúc tính |
| `lockedUntil` | Timestamp | |
| `status` | Enum (`LOCKED`, `UNLOCKED_EARLY`, `EXPIRED`) | |
| `unlockReason` | Text, nullable | Bắt buộc nếu `status = UNLOCKED_EARLY` |
| `createdAt` | Timestamp | |

```sql
CREATE TABLE lock_entry (
    entry_id                CHAR(36) PRIMARY KEY,
    source_event_id          CHAR(36) NOT NULL UNIQUE,
    remedy_decision_id       CHAR(36) NOT NULL UNIQUE,  -- 19/07 lần 4: invariant "một quyết định ≤ 1 lock" (matrix 11o)
                                                       -- phải đứng ở SCHEMA, không chỉ ở prose — source_event_id chặn
                                                       -- redeliver cùng event, cột này chặn 2 event KHÁC nhau cùng decision
    contract_id              CHAR(36) NOT NULL,
    user_id                  CHAR(36) NOT NULL,
    penalized_role           VARCHAR(10) NOT NULL,   -- BUYER | SELLER
    base_days                INT NOT NULL DEFAULT 30,
    repeat_offense_multiplier DECIMAL(3,2) NOT NULL,
    track_record_multiplier  DECIMAL(3,2) NOT NULL,
    zero_progress_multiplier DECIMAL(3,2) NOT NULL,  -- SỬA 18/07/2026: công thức §5 có 4 input mà snapshot
                                                     -- chỉ lưu 3 — record không tự đứng được làm bằng chứng
                                                     -- nếu thiếu 1 input; giờ đủ 4
    lock_duration_days       INT NOT NULL,
    locked_until             TIMESTAMP NOT NULL,
    created_at                TIMESTAMP NOT NULL DEFAULT now()
    -- SỬA 18/07/2026: BỎ cột status/unlock_reason — chúng buộc UPDATE, mâu thuẫn trực tiếp
    -- với DB permission INSERT+SELECT đã chốt 04/07. lock_entry giờ bất biến THẬT:
    --   EXPIRED  = derive (locked_until < now())
    --   UNLOCKED_EARLY = derive (tồn tại lock_override_event hợp lệ trỏ entry này)

);
CREATE INDEX idx_lock_entry_user ON lock_entry(user_id);
```

**Chốt (04/07/2026, làm chặt 18/07/2026) — Insert-only, enforce ở tầng DB permission:** DB user của `reputation-service` chỉ có quyền `INSERT` + `SELECT` trên `lock_entry` (và `lock_override_event`), không có `UPDATE`/`DELETE`. (18/07: bản cũ tự mâu thuẫn — `UnlockEarlyUseCase` set `status`/`unlock_reason` tức là UPDATE; giờ unlock là 1 event append-only riêng, xem schema.)

```sql
-- SỬA 18/07/2026: unlock sớm là SỰ KIỆN append-only, không phải UPDATE lên lock_entry
CREATE TABLE lock_override_event (
    event_id        CHAR(36) PRIMARY KEY,
    lock_entry_id    CHAR(36) NOT NULL REFERENCES lock_entry(entry_id),
    override_type    VARCHAR(20) NOT NULL,     -- 'UNLOCK_EARLY'
    reason           TEXT NOT NULL,
    proposed_by      CHAR(36) NOT NULL,            -- two-person rule, governance §5.3
    approved_by      CHAR(36) NOT NULL,            -- ADMIN, != proposed_by
    created_at       TIMESTAMP NOT NULL DEFAULT now()
);
-- Effective lock của user = MAX(locked_until) trên các lock_entry chưa hết hạn
-- và KHÔNG có override hợp lệ.

-- Maker-checker có model thật (18/07/2026 — trước chỉ có prose governance §5.3):
CREATE TABLE governance_action_request (
    request_id     CHAR(36) PRIMARY KEY,
    action_type     VARCHAR(30) NOT NULL,      -- 'UNLOCK_EARLY' | 'CLEAR_ELEVATED_RISK'
    target_id       VARCHAR(100) NOT NULL,     -- lockEntryId | "buyerId:sellerId"
    status          VARCHAR(15) NOT NULL,      -- PROPOSED | APPROVED | REJECTED
    reason          TEXT NOT NULL,
    proposed_by     CHAR(36) NOT NULL,
    approved_by     CHAR(36) NULL,
    proposed_at     TIMESTAMP NOT NULL DEFAULT now(),
    decided_at      TIMESTAMP NULL
);

-- Pair AML state có chỗ persist (18/07/2026 — ELEVATED_RISK trước đây không có bảng nào giữ):
CREATE TABLE pair_risk_state (
    buyer_id        CHAR(36) NOT NULL,
    seller_id       CHAR(36) NOT NULL,
    status          VARCHAR(20) NOT NULL,      -- NORMAL | ELEVATED_RISK
    detected_at     TIMESTAMP NULL,
    review_due_at   TIMESTAMP NULL,            -- detected_at + elevatedRiskReviewMonths
    source_event_id CHAR(36) NULL,
    PRIMARY KEY (buyer_id, seller_id)
);
-- pair_risk_state là projection có UPDATE (không phải evidence ledger); lịch sử
-- defensible nằm ở governance_action_request + audit chain (AML_RISK_CLEARED).
```

**API maker-checker (18/07/2026):** `POST /api/v1/admin/reputation/actions/propose` (ADMIN|OPERATOR) · `POST /api/v1/admin/reputation/actions/{id}/approve` · `POST .../{id}/reject` (ADMIN, service enforce `approved_by != proposed_by`). Gateway route tách tương ứng (gateway §3.3). Lý do không tin ở tầng application code: "không bao giờ xoá" là lời hứa yếu nếu chỉ dựa vào việc code không gọi hàm xoá — 1 dòng code sai ở đâu đó trong tương lai có thể phá vỡ mà không ai biết. Chặn ở DB permission thì kể cả bug code cũng không phá được. Cùng nguyên tắc `audit-service` đã áp (services.md gap #5).

### 2.2 Reputation Score (view sống — không lưu riêng)

Không có bảng riêng lưu sẵn **con số score** — score vẫn derive lúc query (§5.2). Nhưng (**sửa 19/07 lần 3**) "tính trực tiếp từ event" là prose không chạy được: event không nằm trong RAM vĩnh viễn, RabbitMQ không phải historical database — restart/ack xong là mất dữ liệu để tính completion count. Phải persist **immutable facts** (không phải score):

```sql
CREATE TABLE completed_contract_fact (
  contract_id      CHAR(36) PRIMARY KEY,
  buyer_id         CHAR(36) NOT NULL,
  seller_id        CHAR(36) NOT NULL,
  settled_at       DATETIME(6) NOT NULL,
  source_event_id  CHAR(36) NOT NULL UNIQUE   -- từ contract.settled, dedup redelivery
);
CREATE TABLE dispute_outcome_fact (
  milestone_id        CHAR(36) PRIMARY KEY,
  contract_id         CHAR(36) NOT NULL,
  flagged_by_user_id  CHAR(36) NOT NULL,
  resolution_favors   VARCHAR(10) NOT NULL,    -- BUYER | SELLER
  resolved_at         DATETIME(6) NOT NULL,
  source_event_id     CHAR(36) NOT NULL UNIQUE -- từ milestone.dispute_resolved
);
```

Truy vấn (completion count, clean rate, tỷ lệ flag-rồi-thua) tính từ `lock_entry` + 2 bảng facts này tại thời điểm cần — insert-only, cùng triết lý ledger, không dual-write score.

---

## 3. Input Events

| Event | Loại | Nguồn | Trạng thái |
|---|---|---|---|
| `remedy.finalized` (**sửa 19/07/2026 — nguồn negative CHÍNH, thay `milestone.cancelled_with_penalty` trực tiếp**) | Negative | `contract-service` — bắn khi `AttributionDecision` được chốt: Rổ B sau khi `BreachCase` `RESOLVED`; Rổ A do `SYSTEM` kết luận trực tiếp với `breachCaseId = null`. Payload mang `finalBreachingRole` + `breachReasonCode` + `decisionSource` | **Gate cứng (sửa 19/07 lần 2 — đọc flag, không tự suy từ reason):** chỉ tạo `lock_entry` khi payload có **`reputationEligible = true`** (contract-service quyết qua remedy calculator — milestone-escrow §6.4.1/§7.2; bảng §4.3 dưới là default mapping để hiểu, không phải logic consumer tự chạy). Idempotency **theo `remedyDecisionId`** (một quyết định ≤ 1 lock — matrix 11o), không chỉ theo eventId. Allegation (`breach.reported`) KHÔNG BAO GIỜ là input. |
| ~~`contract.terminated`~~ (**GỠ khỏi input chế tài — 19/07 lần 2**) | — | — | **Reputation KHÔNG consume `contract.terminated` nữa.** Trước đó cả `remedy.finalized` lẫn `contract.terminated` cùng là input negative → cùng một breach có thể sinh **2 `lock_entry`** (sourceEventId khác nhau, UNIQUE không chặn được). `remedy.finalized` là nguồn DUY NHẤT (dòng trên); `contract.terminated` chỉ đi audit/analytics/notification (milestone-escrow §7.2). |
| `contract.settled` | Positive | `contract-service` (`ContractSettledEvent`), đã tồn tại trong code Phase 1 | **Đã fix (04/07/2026) — xem cập nhật KI-1 ở §10.** Guard `Contract.settle()` được sửa để chạy từ `ACTIVE`, và `escrow-service` cũng consume event này để release `buyerDepositRate` — xem `milestone-escrow-phase2-design.md` §3.1, §6.7, §7.2. |
| `milestone.dispute_resolved` (mới, 08/07/2026) | Tín hiệu hành vi (flag-abuse) | `contract-service` — bắn khi `DisputeRoutingService` ra phán quyết cho milestone `CONTESTED` | **Đã có trong Event Catalog `milestone-escrow-phase2-design.md` §7.1 (cập nhật 18/07/2026 — bỏ trạng thái "cần thêm").** Xem §6.1b. |
| `bank.large_transaction_flagged` (mới, 08/07/2026) | Tín hiệu AML (1 phần input composite score) | `bank-service` (`bank-service-phase2-design.md` §3.4) | Không tự trigger hold — chỉ hold khi đi kèm ≥1 tín hiệu hành vi khác, xem §8 mục 2. |

---

## 4. Multiplier Formulas

**⟢ SOURCE OF TRUTH cho công thức `lockDurationDays` là mục này (reputation-service §4).** Trước đây công thức được "chốt" ở `milestone-escrow-phase2-design.md` §6.1 và copy sang đây — nay đảo lại đúng chủ sở hữu: `reputation-service` là nơi tính và implement công thức này, nên nó chốt số ở đây. `milestone-escrow` §6.3.1 (số mục mới 19/07/2026, cũ §6.1) chỉ giữ **input/trigger** (khi nào `remedy.finalized`/`milestone.cancelled_with_penalty` sau-attribution kích lockout, vì sao tách penalty debt khỏi số ngày khoá) và tham chiếu về mục này cho baseline. Nếu 2 file lệch nhau về con số → bản ở đây thắng.

```
lockDurationDays = baseDays(30) × repeatOffenseMultiplier × trackRecordMultiplier × zeroProgressMultiplier
```

| Multiplier | Giá trị | Điều kiện |
|---|---|---|
| `baseDays` | 30 | Cố định |
| `repeatOffenseMultiplier` | 1x / 2x / 3x | Lần vi phạm thứ mấy trong `repeatOffenseLookbackMonths` gần nhất (§4.2) |
| `trackRecordMultiplier` | 0.7x / 1.0x / 1.3x | Dưới 5 hợp đồng hoàn thành: mặc định 1.0x. Đủ 5 trở lên: tính theo % sạch thật (0.7x nếu track record tốt, 1.3x nếu kém) — không time window (§4.1) |
| `zeroProgressMultiplier` | 1.5x / 1.0x | **Mới (08/07/2026); siết điều kiện 19/07/2026:** 1.5x khi breach lúc **0 milestone nào từng `SETTLED`** **VÀ** `breachReasonCode` thuộc nhóm strategic (§4.3) — ký xong bỏ ngay *một cách cố ý* mới là tín hiệu xấu nhất + ma sát disintermediation. **KHÔNG áp** khi zero-progress do mutual/FM/activation-failure/buyer-caused (những đường này vốn không tạo `lock_entry` cho bên kia — §3, §4.3). 1.0x cho mọi trường hợp còn lại. Trigger chốt tại `milestone-escrow-phase2-design.md` §6.3.1. |

### 4.1 `trackRecordMultiplier` — KHÔNG có time window

Đo hoạt động **tích cực** (hợp đồng hoàn thành sạch) — mùa vụ nông sản tự nhiên tạo khoảng trống dài giữa các hợp đồng (không phải dấu hiệu seller nghỉ giao dịch có vấn đề gì), nên không áp time window, chỉ áp ngưỡng số lượng tối thiểu (5 hợp đồng).

### 4.2 `repeatOffenseMultiplier` — CÓ time window 24 tháng

Đo hành vi **tiêu cực** (vi phạm) — ngược lại với §4.1, khoảng cách dài giữa 2 lần vi phạm mang ý nghĩa thật (seller đã sửa hành vi), nên chỉ đếm vi phạm trong 24 tháng gần nhất, không cộng dồn vĩnh viễn.

### 4.3 Gate `breachReasonCode` — reputation chỉ phạt hành vi cố ý (mới, 19/07/2026)

Lý do tồn tại của gate: cùng một *kết quả* (không giao / giao thiếu / hợp đồng chấm dứt) có nhiều *nguyên nhân* khác nhau về lỗi (milestone-escrow §6.4.1); reputation là hình phạt hành vi nên chỉ áp cho nguyên nhân **cố ý/chiến lược**:

| `breachReasonCode` (từ `remedy.finalized`) | Tạo `lock_entry`? |
|---|---|
| `SIDE_SELLING`, `NON_DELIVERY` (cố ý), `LATE_DELIVERY` (không lý do), `WRONGFUL_REJECTION`, `FUNDING_FAILURE` (buyer, sau cure window), `FAILURE_TO_RECEIVE`, `LATE_PAYMENT` | **Có** — đúng role bị kết luận |
| `PRODUCTION_SHOCK_NON_FM` | **KHÔNG lock** (`reputationEligible = false` luôn) — sốc sản lượng chưa đủ FM pháp lý nhưng không phải cố ý phá. Penalty tiền là chuyện riêng của flag `penaltyEligible` (LegalProfile/phán quyết quyết, milestone-escrow §6.4.1) — 2 quyết định độc lập, reputation không suy từ việc có/không có penalty |
| `FORCE_MAJEURE`, `MUTUAL_EXIT`, `ACTIVATION_FAILURE` | **KHÔNG** — no-fault, `finalBreachingRole = NULL`, event không tới được tầng này |
| Allegation bị bác (case `RESOLVED` với `finalBreachingRole = NULL`) | **KHÔNG** cho bên bị cáo buộc; tỷ lệ flag-rồi-thua của bên cáo buộc vẫn đếm qua `milestone.dispute_resolved` (§6.1b) |

---

## 5. Insert-only & Reputation Score — chi tiết bổ sung

### 5.1 Vì sao Lock Ledger phải insert-only

Xem §2.1 — bằng chứng pháp lý, DB permission enforce, không tin code.

### 5.2 Vì sao Reputation Score là view sống, không lưu bảng riêng

Tính lại mỗi lần cần từ `lock_entry` + `completed_contract_fact` + `dispute_outcome_fact` — tránh dual-write giữa "con số cache" và các immutable facts insert-only, đúng bài học đã rút ở `bank-service` §5 (cache số tiền là dual-write, cache state flag thì ổn).

---

## 6. Tích hợp liên service

### 6.1 `user-service` — enforcement

**Chốt (04/07/2026):** không thể chặn ở tầng Gateway/Keycloak — Keycloak chỉ cấp JWT (identity + role), không biết business lock state. `UserContextInjectionFilter` (api-gateway) không gọi `user-service` để hỏi trạng thái — verify thuần chữ ký JWT.

Check phải nằm ở đúng use case tạo nghĩa vụ mới:

- **`CreateListing` (product-service) / tạo offer** — **fail-closed (sửa 18/07/2026, round 2 — đồng bộ user-service §3):** user-service down → 503; fail-open cũ cho người đang khoá lách đúng lúc dependency chết. Feign client tới `user-service` cần thêm mới.
- **`sign()` (contract-service)** — fail-closed, bắt buộc. Đã có sẵn `UserPort`/`UserServiceClient` (Feign) — chỉ cần thêm `lockedUntil` vào response `UserInfo` + `@CircuitBreaker` với fallback throw (services.md gap #1, **ưu tiên đóng gap này trước tất cả Feign call khác trong Phase 2** — chốt 04/07/2026 ở `inspection-phase2-design.md`/session review: `sign()` fail-closed nằm trên đường ký hợp đồng, `user-service` down không có breaker sẽ chặn toàn bộ platform ký hợp đồng, không phải lỗi cục bộ 1 case).

Lý do 2 tầng không thừa nhau: khoá chỉ chặn **tạo mới**, không hồi tố hợp đồng đã ACTIVE/SIGNED (milestone-escrow §6.3.1 mục 2 — số mục mới 19/07/2026). Seller sạch lúc tạo offer, dính khoá giữa chừng đàm phán — chỉ `sign()` check lại mới bắt được; `CreateListing`-only sẽ bỏ sót case này.

`reputation-service` publish event (`reputation.locked`/`reputation.unlocked`), `user-service` cache 1 field trên `UserProfile` (không gọi sync mỗi lần cần check) — tránh dual-write, đúng bài học đã rút từ `bank-service` §5 (cache state flag là ổn, cache số tiền mới là dual-write).

### 6.1b Đối xứng hoá — buyer reputation hiển thị cho seller (mới, 08/07/2026)

**Vấn đề:** mọi tín hiệu minh bạch đã xây (`verificationLevel`, `geoRiskLevel` bên `product-service`, reputation score ở đây) đều một chiều **buyer-xem-seller**. Seller không có tín hiệu nào để đánh giá buyer trước khi ký (buyer có hay bùng không, có hay lạm dụng `FLAG_ISSUE` ép seller vào dispute không). Với luận điểm "bảo vệ bên yếu" xuyên suốt dự án, đây là chỗ hội đồng dễ hỏi: *"seller được gì để tự bảo vệ, ngoài việc bị chấm điểm?"*

**Tin tốt: dữ liệu đã có sẵn, chỉ thiếu chiều hiển thị.** `lock_entry` (§2.1) **đã** insert-only cho cả `penalizedRole = BUYER` lẫn `SELLER` từ đầu — dữ liệu "buyer này từng bùng mấy lần, lock bao lâu" đã tồn tại, không cần cơ chế mới. Chỉ cần:

1. **Endpoint mới `GET /api/v1/reputation/{userId}/public-summary`** — query theo đúng `userId` (không phân biệt buyer hay seller gọi), trả reputation score + lock history cho user đã đăng nhập (**17/07/2026:** authenticated qua Gateway, không còn public no-auth — mọi bên cần xem đối tác đều đã login, hạ exposure scraping lock-history; vẫn không cần consent như `credit-export` §6.3, vì đây là thông tin đối tác cần biết *trước khi* quyết định ký, không phải hồ sơ tín dụng riêng tư). Seller dùng endpoint này để xem uy tín buyer trước khi nhận offer/đàm phán — đối xứng thật với việc buyer xem seller lúc chọn listing.
2. **Framing lại `buyerDepositRate`/`sellerDepositRate` là công cụ 2 chiều theo mức tin tưởng** (đã đúng về cơ chế ở `milestone-escrow-phase2-design.md` §2.1/§6.3.1, chỉ cần nói rõ ra): seller mới, gặp buyer lạ, xem `public-summary` thấy buyer có track record xấu → seller có quyền đàm phán `buyerDepositRate` cao hơn 5% mặc định lúc `NEGOTIATING` — không phải chỉ buyer mới có quyền đòi cọc seller.

**Tín hiệu mới — chống buyer lạm dụng `FLAG_ISSUE` vô cớ (mới, 08/07/2026):** lỗ thật seller chưa được bảo vệ — buyer có thể flag bừa để ép seller vào dispute/kéo dài mà không mất gì (chi phí giám định do bên thua chịu, §8 milestone-escrow, nhưng buyer có thể chấp nhận rủi ro đó để gây áp lực). Thêm 1 input event mới:

| Event | Loại | Nguồn | Ghi chú |
|---|---|---|---|
| `milestone.dispute_resolved` (mới, 08/07/2026 — đã có trong `milestone-escrow-phase2-design.md` §7.1) | Tín hiệu hành vi | `contract-service` — bắn khi `DisputeRoutingService` (3-tier) ra phán quyết cho milestone từng `CONTESTED`. Payload: `{milestoneId, flaggedBy: BUYER, resolutionFavors: BUYER\|SELLER}` (chỉ buyer flag được ở state machine hiện tại — nếu sau này seller cũng có quyền flag, mở rộng field `flaggedBy`) | `reputation-service` đếm tỷ lệ `resolutionFavors = SELLER` trên tổng số lần buyer đó từng `FLAG_ISSUE` — tỷ lệ cao (flag rồi thua nhiều lần) là tín hiệu buyer lạm dụng, hiển thị trong `public-summary` để seller thấy trước khi ký |

Dùng đúng bộ máy reputation đã có (view sống — score derive lúc query từ `lock_entry` + facts tables §2.2, không lưu sẵn score, §5.2) — chỉ thêm 1 loại tín hiệu đầu vào (`dispute_outcome_fact`), không phải cơ chế mới.

**Tổng kết B4:** đối xứng hoá reputation (dữ liệu đã có, chỉ thêm chiều hiển thị) + framing 2 deposit rate là công cụ 2 chiều (đã đúng cơ chế, chỉ cần nói rõ) + tín hiệu chống flag-abuse (nhỏ, tái dùng hạ tầng sẵn có). Không phải known-limitation cần chấp nhận — là hoàn chỉnh hoá luận điểm gốc "bảo vệ bên yếu thế" cho đúng cả 2 chiều.

### 6.2 `product-service` — tìm kiếm/lọc listing

`product-service` sở hữu truy vấn tìm kiếm/lọc listing bằng các query parameter trên dữ liệu sản phẩm. `reputation-service` không sở hữu listing index hoặc ranking; điểm uy tín được cung cấp qua `GET /api/v1/reputation/{userId}/public-summary` ở bước xem đối tác/đàm phán trước khi ký.

### 6.3 Credit reference export

**Research (04/07/2026):** CIC (Trung tâm Thông tin Tín dụng Quốc gia) không áp dụng trực tiếp — seller trên platform không có lịch sử vay ngân hàng để CIC đối chiếu. Hướng đúng: **VARI** (Chương trình xếp hạng tín nhiệm doanh nghiệp nông nghiệp Việt Nam, www.vari.org.vn) — tổ chức trung gian đã tồn tại sẵn cho đúng ngành, đóng vai trò "người đọc dữ liệu platform rồi đưa cho ngân hàng", không cần platform tự thuyết phục từng ngân hàng riêng lẻ. Có tín hiệu chính sách ủng hộ: Luật Hỗ trợ DNVVN (sửa đổi) đang xây dựng cơ chế sandbox cho vay dựa trên dữ liệu/dòng tiền.

**Định vị lại vai trò (04/07/2026):** `reputation-service` export là **reference/reputation attestation**, không phải nguồn chấm điểm tín dụng chính — reputation (đáng tin để giao dịch tiếp) và cash-flow data (đủ tiền trả nợ) đo 2 chuyện khác nhau, không thay thế nhau.

Thiết kế:
- Endpoint riêng `GET /api/v1/reputation/{userId}/credit-export`, JSON theo 4 nhóm phổ quát (lịch sử thanh toán đúng hạn, quy mô/tần suất giao dịch, vi phạm/nợ xấu, thâm niên hoạt động — bao gồm `firstContractDate`/`activeMonthsSpan`, không chỉ đếm số lượng).
- **Chỉ chạy khi seller chủ động yêu cầu** (consent rõ ràng), không phải endpoint mở tự động đẩy dữ liệu ra ngoài — đúng cơ chế CIC vận hành (người vay tự tra cứu mang đi).
- **Gate theo counterparty diversity** trước khi cho export — seller chỉ giao dịch với 1-2 đối tác dù đủ 5+ hợp đồng "sạch" cũng không đủ điều kiện, giảm động cơ tạo hợp đồng giả để làm đẹp hồ sơ vay vốn (xem §7).

---

## 7. Use Case Changes

- **`ProcessLockoutUseCase`** — consume **`remedy.finalized` DUY NHẤT** (**sửa 19/07 lần 4** — bản trước còn ghi "/ `contract.terminated`", mâu thuẫn trực tiếp với §3 đã gỡ terminated khỏi input chế tài); chỉ chạy khi `reputationEligible = true` (không tự suy từ reason — §4.3); idempotent theo `remedyDecisionId` (`remedy_decision_id UNIQUE`, §2.1); tính `repeatOffenseMultiplier` + `trackRecordMultiplier` + `zeroProgressMultiplier`, ghi `lock_entry` mới (insert-only), publish `reputation.locked`.
- **`UnlockEarlyUseCase`** — chạy khi `governance_action_request` được APPROVE (two-person rule §7-schema): **INSERT `lock_override_event`** (không UPDATE `lock_entry` — sửa 18/07/2026, bảng đó bất biến), **tính lại effective lock còn lại của user từ các `lock_entry` chưa hết hạn và chưa có override**, rồi publish `reputation.unlocked {eventId, userId, effectiveLockedUntil (nullable), reasonCode, lockRevision, occurredAt}` — `lockRevision` (18/07/2026) là sequence tăng dần per user do reputation cấp (đếm event lock/unlock đã phát cho user), user-service persist để ordering sống qua restart; `reputation.locked` cũng mang field này. **Sửa 17/07/2026:** payload cũ chỉ mang `unlockedAt`, khiến projection bên user-service "clear" mù và mở khoá nhầm khi user có nhiều lock chồng nhau — source of truth phải tự tính, projection chỉ set theo (xem `user-service-phase2-design.md` §2.2). `reputation.locked` cũng thêm `occurredAt` cùng lý do ordering. Dùng cho nhánh 2, 3 ở §5. **Maker-checker (18/07/2026, governance §5.3):** unlock sớm không còn đơn phương — OPERATOR propose (reason bắt buộc, trạng thái `PROPOSED`) → ADMIN approve/reject; ADMIN không tự approve đề xuất của chính mình. Payload thêm `proposedByOperatorId` + `approvedByAdminId`; cả propose lẫn approve vào audit chain.
- **`CheckLockStatusUseCase`** — expose cho `user-service` gọi qua Feign (§6.1), trả `lockedUntil` hiện tại của 1 `userId`.
- **`GetCreditExportUseCase`** — seller tự trigger (consent-based, §6.3), check counterparty diversity gate trước khi trả JSON export.
- **`FlagSuspiciousPatternUseCase`** — tính composite fraud score (§8), theo cặp buyer-seller/account, publish event hold khi vượt ngưỡng. **Cập nhật (06/07/2026):** tách 2 nhóm — tín hiệu tương đối (cần lịch sử, hold giao dịch kế tiếp) và tín hiệu tuyệt đối (ngưỡng luật định 500 triệu, hold ngay trên chính giao dịch, kể cả giao dịch đầu tiên của account mới). **Sửa (08/07/2026):** consume thêm `bank.large_transaction_flagged` (`bank-service-phase2-design.md` §3.4) làm 1 input, không phải trigger hold độc lập — hold chỉ kích hoạt khi ngưỡng tuyệt đối **đi kèm** ≥1 tín hiệu hành vi khác (track record mỏng / zero-variance / counterparty mới), tránh hold thuần theo giá trị giết happy path — xem §8 mục 2.

---

## 8. AML / Anti-fraud

**Bối cảnh:** Doc2 §5.2 Rủi ro 4 (nền tảng bị lợi dụng tạo giao dịch khống để rửa tiền) đã có biện pháp — KYC + INSPECTOR xác nhận hàng hoá thực tế. Nhưng biện pháp này **chỉ kích hoạt khi có tranh chấp** (`FLAG_ISSUE` → `CONTESTED`, milestone-escrow §3.2). Giao dịch thông đồng (2 bên hợp tác, không ai dispute) không bao giờ chạm INSPECTOR — lỗ hổng cấu trúc, không phải thiếu sót nhỏ. Credit export (§6.3) làm rủi ro này nặng hơn — hợp đồng giả không chỉ làm đẹp reputation mà còn hợp thức hoá dòng tiền bẩn thành lịch sử "sạch" dùng xin vay thật.

**Chốt (04/07/2026):**

1. **Composite fraud score**, không dựa vào 1 signal đơn — kết hợp:
   - Tín hiệu luật định (Điều 29 Nghị định 52/2024, dẫn Luật Phòng chống rửa tiền 2022): structuring (nhiều giao dịch gần mức giá trị lớn phải báo cáo), đột biến doanh số giao dịch, tốc độ khởi tạo giao dịch nhanh bất thường.
   - Tín hiệu tự suy luận từ đặc thù nông sản: counterparty concentration (tỷ lệ tập trung đối tác), zero-variance pattern (`sellerDeclaredWeight` khớp tuyệt đối `buyerReceivedWeight` lặp lại nhiều lần — nông sản thật luôn có hao hụt tự nhiên).
   - Tính theo **cặp buyer-seller hoặc account**, không theo từng contract lẻ — pattern cần lịch sử lặp lại mới thấy được.

**Cập nhật (06/07/2026) — tách 2 nhóm tín hiệu, không gộp chung 1 rổ:** rà lại kỹ hơn phát hiện "đột biến doanh số" (mục 1 trên) tự nó ngầm định phải có **baseline lịch sử của chính account đó** để so sánh — account mới tinh, giao dịch đầu tiên, không có gì để so, nên dù giá trị giao dịch có lớn cỡ nào, nó không "đột biến" theo đúng nghĩa thống kê. Đây chính là gốc rễ giới hạn cấu trúc đã nêu ở mục 2 dưới (chỉ hold giao dịch kế tiếp) — không sửa được bằng đổi threshold, vì vấn đề không nằm ở *vị trí* ngưỡng mà ở việc *không có dữ liệu lịch sử để chấm điểm ngay từ đầu*.

Giải pháp: thêm **nhóm tín hiệu tuyệt đối, không cần lịch sử** — song song với nhóm tương đối ở mục 1, không thay thế:
   - **Ngưỡng giá trị tuyệt đối theo luật định** — giao dịch chuyển khoản từ 500 triệu đồng trở lên (Điều 9 Thông tư 27/2025/TT-NHNN, "giao dịch chuyển tiền điện tử", hiệu lực 01/11/2025 — ngưỡng này giữ nguyên từ 2007, không phải số mới) thuộc nhóm phải báo cáo, bất kể account có lịch sử hay không — đây là con số so với luật, không phải so với chính account đó ngày hôm qua.
   - **Trigger ngay trên chính giao dịch đó**, không chờ "giao dịch kế tiếp" như nhóm tín hiệu tương đối — vì không cần baseline để đánh giá, nhóm này có thể áp dụng ngay cả cho giao dịch đầu tiên của account hoàn toàn mới.
   - **Không thay thế nhóm tương đối** — 2 nhóm giải 2 loại rủi ro khác nhau: nhóm tương đối bắt fraud tích luỹ nhiều giao dịch nhỏ qua thời gian (giá trị vẫn dưới ngưỡng tuyệt đối); nhóm tuyệt đối bắt đúng kịch bản "đánh nhanh rút gọn" (one-shot, tài khoản giả, 1 hợp đồng khủng rồi bỏ) mà nhóm tương đối theo định nghĩa không bắt được lần đầu.
   - **Sửa (08/07/2026; đồng bộ 19/07 lần 4) — nguồn phát hiện dời sang bank-service:** `reputation-service` không tự query `ledger_entry` để so giá trị — `bank-service` (bên giữ tiền thật, đúng chủ thể pháp lý theo Luật PCRT 2022 Điều 4) publish `bank.large_transaction_flagged` tại **bank-adapter boundary cho giao dịch chuyển tiền thực** ≥ 500 triệu, không phát per-`LedgerEntry`. `reputation-service` consume event này làm 1 trong các input cho composite score, không phải tự trigger hold độc lập — xem mục 2 dưới.

2. **Sửa (08/07/2026) — ngưỡng tuyệt đối một mình không còn đủ để hold, phải đi kèm ≥1 tín hiệu hành vi khác:** bản 06/07/2026 để giá trị tuyệt đối tự nó trigger `CONFIRM_CLEAN` hold, không điều kiện gì thêm. Vấn đề phát hiện khi rà cross-check với ví dụ hợp đồng thật ở Doc02 (100-1000 tấn cà phê, tương đương 13,5-135 tỷ VNĐ, đối chiếu giá Robusta thị trường ~92.000-97.000đ/kg) — **gần như 100% hợp đồng thật của platform vượt xa 500 triệu chỉ vì khối lượng thương mại lớn**, không liên quan gì tới hành vi khả nghi. Hold cứng theo giá trị đơn thuần sẽ khiến phần lớn giao dịch điển hình bị treo chờ Admin duyệt — giết chết đúng selling point lõi "escrow tự thực thi, release tự động, không ai can thiệp" (Doc02 §3.3, §8), biến Admin thành nút cổ chai cho mọi giao dịch lớn, ngược triết lý neutral-party.

   **`CONFIRM_CLEAN` hold đồng bộ**, chuyển chờ Admin duyệt thay vì auto-`SETTLED`, chỉ áp khi: **(ngưỡng tuyệt đối ≥500 triệu, từ `bank.large_transaction_flagged`) AND (≥1 tín hiệu hành vi khác)** — ví dụ: account có `< N` hợp đồng hoàn thành (chưa đủ track record), **OR** zero-variance pattern (mục 1 phần tương đối), **OR** counterparty hoàn toàn mới (chưa từng giao dịch với nhau). Bắt đúng kịch bản one-shot fraud thật (account mới + hợp đồng khủng = to *và* vô danh), **không** phạt cặp buyer-seller có track record sạch đang giao dịch bình thường ở quy mô thương mại thật.

   **Với nhóm tín hiệu tương đối (không đổi):** hold chỉ áp cho giao dịch **kế tiếp** của đúng cặp bị flag, không hồi tố hợp đồng đã `SETTLED`, không vạ lây cặp khác. Chính giao dịch làm score vượt ngưỡng **không** bị hold — chỉ giao dịch tiếp theo mới bị (giới hạn cấu trúc của detect dựa trên pattern lặp lại, đã xác nhận 04/07/2026, không đổi). **Với nhóm tín hiệu tuyệt đối + điều kiện hành vi (sửa 08/07/2026):** hold áp ngay trên **chính giao dịch đang vượt ngưỡng VÀ có tín hiệu hành vi đi kèm** — vẫn đóng đúng phần gap "one-shot fraud", nhưng không còn đánh đồng "giao dịch lớn" với "giao dịch khả nghi".

3. **Admin trigger inspection đột xuất** khi nghi ngờ, nhưng **không tự chọn tổ chức inspect** — hệ thống random từ danh sách đã vet sẵn (đồng nhất cơ chế Level 1.5 hiện có: Vinacontrol/Quatest, route tự động, không phải chọn tay). Tránh tái tạo đúng lỗ hổng đang có ở Level 2 (`level2InspectorOrg` negotiate tự do — xem KI-2).

4. **Chi phí inspection do nghi ngờ pattern = platform chịu**, không đổ cho buyer/seller. Lý do: nghĩa vụ Điều 29 NĐ 52/2024 là nghĩa vụ của platform, không phải user; tránh phạt oan false positive, đặc biệt nhóm HTX nhỏ vốn có counterparty concentration cao tự nhiên (không phải dấu hiệu gian lận).

5. **Mục tiêu thiết kế = đạt bar "có biện pháp quản trị rủi ro"** (Điều 29 NĐ 52/2024) — không phải bắt 100% fraud (bất khả thi với collusion đủ nguồn lực). Audit trail bất biến (§2.1 insert-only) là bằng chứng due diligence nếu bị điều tra, không phải cơ chế ngăn chặn tuyệt đối.

6. **Mới (10/07/2026) — tầng batch bắt structuring chậm + trạng thái `ELEVATED_RISK` (Enhanced Due Diligence):** 2 tầng ở mục 1-2 chạy realtime và đều chỉ với tay tới **giao dịch kế tiếp** — không bắt được kẻ rải mỏng nhiều hợp đồng ~490 triệu (dưới ngưỡng tuyệt đối) giãn cách nhiều tháng (ngoài cửa sổ tương đối). Đây chính là gap ghi ở §9. Đóng bằng tầng batch trên data warehouse: `analytics-service.AmlPatternScanJob` (`analytics-service-phase2-design.md` §3.5) quét cụm near-threshold theo cặp buyer-seller (band `[475tr, 500tr)`, min 5 hợp đồng, lookback 90 ngày) và publish `analytics.structuring_pattern_detected`.

   **`reputation-service` consume event này → set cặp buyer-seller sang trạng thái `ELEVATED_RISK`.** Đây là điểm khác biệt cốt lõi so với cơ chế hold ở mục 2, phải nắm rõ để không implement nhầm:

   - **`ELEVATED_RISK` là một *state* bền của cặp, KHÔNG phải trigger hold một-lần.** Cơ chế hold ở mục 1-2 là *forward-looking*: pattern đang diễn ra → chặn mắt xích kế tiếp. Batch thì *backward-looking*: nhìn lại 90 ngày, pattern **đã xong**, tiền các hợp đồng cũ đã settle — không có gì để hold ngược, và "hold đúng giao dịch kế tiếp" là vô nghĩa vì cặp có thể đã im nhiều tháng. Thay vào đó, batch **kết luận về cả cặp** và nhuộm trạng thái.
   - **Còn `ELEVATED_RISK` → MỌI giao dịch của cặp** (bất kể to nhỏ, kể cả dưới 500 triệu) route qua review đồng bộ trước khi auto-`SETTLED`, cho tới khi Admin clear. Đây là đúng cơ chế Enhanced Due Diligence (EDD) chuẩn AML: cặp rủi ro cao thì soi tăng cường suốt vòng đời quan hệ, không phải chặn đúng 1 phát rồi thôi.
   - **Đây là đường enforcement RIÊNG, không sửa luật composite ở mục 2.** Gate composite (mục 2) vẫn giữ nguyên "(tuyệt đối ≥500tr) AND (≥1 hành vi)" cho từng giao dịch lẻ. `ELEVATED_RISK` là lớp phủ cấp-cặp, độc lập ngưỡng 500 triệu — nên đóng được đúng phần slow-drip *dưới* ngưỡng (490 triệu đều đặn) mà gate composite không chạm. Không cần chế ngoại lệ "batch tự-hold không cần absolute" trong gate — `state` chính là cơ chế.
   - **Bắt buộc có clear-path + expiry (nếu thiếu = nhà tù không án):** Admin gỡ `ELEVATED_RISK` → `NORMAL`, **ghi lý do vào audit-service hash chain** (defensible closure — chuẩn AML: thanh tra soi đúng việc quyết định đóng case có được ghi lại và defend được không). Transport (**17/07/2026**): publish `reputation.elevated_risk_cleared {eventId, buyerId, sellerId, clearedByAdminId, reason, occurredAt}` — audit-service consume, `source_type = AML_RISK_CLEARED` (hash-chain §2.4); reputation-service không INSERT thẳng `audit_record`. **Maker-checker (18/07/2026, governance §5.3):** clear đi luồng OPERATOR propose → ADMIN approve như unlock sớm; payload event mang cả `proposedByOperatorId` + `approvedByAdminId` — defensible closure đúng nghĩa: 2 người, 2 role, 2 vết trên chain. Và tự động rà: cặp không tái phạm sau `elevatedRiskReviewMonths` (chốt **6 tháng**, `application.yml`, chỉnh được) → hạ về `NORMAL`. Không có expiry thì cặp làm ăn thật (bị false-positive) bị soi vĩnh viễn.

   **Nghĩa vụ báo cáo cơ quan (KHÔNG được bỏ):** trong khung AML, phát hiện structuring mà không báo cáo tự nó là lỗi tuân thủ. `analytics.structuring_pattern_detected` còn có consumer thứ 2 là **`bank-service`** — bên báo cáo giao dịch khả nghi cho cơ quan (Cục PCRT, đúng chủ thể pháp lý giữ tiền theo Luật PCRT 2022 Điều 4). `reputation-service` lo xử lý *nội bộ* (ELEVATED, chặn giao dịch tương lai của cặp); `bank-service` lo nghĩa vụ *ra ngoài* (khai báo cơ quan). 2 nhánh song song, độc lập — consumer bên `bank-service` cần thêm vào `bank-service-phase2-design.md`.

---

## 9. Out of Scope (có chủ đích, không phải thiếu sót)

- **Tích hợp thật với VARI hoặc bất kỳ ngân hàng nào** (§6.3) — chưa ký kết, chưa có đối tác thật. Thiết kế sẵn interface export (`GetCreditExportUseCase`) để nếu sau này có đối tác thật, chỉ cần thêm adapter, không đổi business logic. Cùng tinh thần `bank-service` doc đã làm với vai trò arbitrator.
- **Multi-currency** — không cần, platform chỉ giao dịch VNĐ.
- **Giải quyết dứt điểm ngân sách chi phí inspection** (§8 mục 4) — chỉ model đúng field (`triggerType`, `costBearer`) trong `InspectionRequest` (thuộc `inspection-service`, không phải `reputation-service`), không giải quyết nguồn ngân sách thật trong đồ án.
- **KI-2, KI-3** (§10) — đã phát hiện, chưa fix trong session này, không thuộc phạm vi thiết kế `reputation-service` (KI-2 thuộc `inspection-service`, KI-3 thuộc `user-service`). **KI-1 đã đóng — xem §10.**
- **Đòn bẩy kinh tế thay thế cho `lock_entry` lockout ở năm đầu vận hành (bảo hiểm, gắn quyền lợi hội viên VICOFA...)** — đã verify (04/07/2026): điều lệ công khai của VICOFA (`vicofa.org.vn`) chỉ cho phép khai trừ/đình chỉ hội viên khi vi phạm ảnh hưởng tới uy tín/tài sản/tài chính **của chính VICOFA** hoặc vi phạm quy chế **của VICOFA** — không có điều khoản nào gắn hành vi bẻ kèo hợp đồng thương mại ghi nhận qua 1 hệ thống bên ngoài. Không phải giả định lửng lơ — đây là 1 câu hỏi cụ thể có thể mang đi hỏi thăm qua kênh sẵn có (NHL, business validation outreach): VICOFA có sẵn sàng ban hành quy chế mới gắn dữ liệu `lock_entry` vào hệ quả hội viên (quota, đoàn xúc tiến...), tách biệt khỏi việc phân xử đúng-sai trong tranh chấp cụ thể (giữ neutrality đã chốt ở `bank-service-phase2-design.md`) hay không — nhưng nằm ngoài khả năng đồ án tự xác nhận hay quyết định. Giữ nguyên `lock_entry`/`lockDurationDays` hiện tại làm baseline, không thiết kế thêm cơ chế phụ thuộc 1 giả định thể chế chưa xác nhận.
- **One-shot fraud dưới ngưỡng tuyệt đối (mới, 06/07/2026; thu hẹp 10/07/2026)** — nhóm tín hiệu tuyệt đối (§8 mục 1) chỉ đóng gap cho giao dịch **vượt** 500 triệu đồng. **Cập nhật (10/07/2026):** phần structuring chậm *có lặp lại* (nhiều hợp đồng near-threshold cùng cặp qua nhiều tháng) **nay đã đóng** bằng tầng batch + `ELEVATED_RISK` (§8 mục 6). Residual còn lại, không đóng được bằng bất kỳ công cụ pattern-based nào (ghi rõ, honest):
  - **Vài mảnh đầu trước khi pattern thành hình** — batch chỉ thấy cụm sau khi đủ ≥5 hợp đồng đã settle; các hợp đồng đầu (dưới ngưỡng min-count) chắc chắn lọt, tiền đã đi. Đây là giới hạn *toán học* của detect dựa trên pattern lặp: pattern cần tối thiểu nhiều điểm mới thành hình, không có "điểm 0".
  - **One-shot nhỏ lẻ thật sự** — 1 giao dịch giả giá trị vừa/nhỏ, đầu tiên của account mới, rồi biến mất: dưới ngưỡng tuyệt đối (không chạm nhóm 1), không baseline (không chạm nhóm tương đối), không đủ cụm (không chạm batch). Bất khả với mọi công cụ pattern-based — nằm cùng nhóm limitation trusted-operator đã chấp nhận (§8 mục 5).

---

## 10. Known Issues (phát hiện qua đối chiếu code Phase 1 — không block thiết kế này, cần nhớ khi implement)

**KI-1 — ĐÃ FIX (04/07/2026), không còn treo.** Vấn đề gốc: `Contract.settle()` (contract-service) chỉ chạy được nếu status hiện tại là `DELIVERED` — di sản two-phase lock Phase 1. `completeAllMilestones()` (method thay thế Phase 2) sẽ gọi `settle()` khi Contract đang `ACTIVE` (không bao giờ đạt `DELIVERED` nữa) → throw ngay. Kéo theo dead path: `confirmDelivery()`, `ContractDeliveredEvent`/`"contract.delivered"`, consumer ở `escrow-service.ContractEventConsumer` và `notification-service.NotificationEventConsumer`.

Fix + dead-path cleanup đã chốt tại `milestone-escrow-phase2-design.md` §3.1 (guard sửa để chạy từ `ACTIVE`) và §6.3/§7.2 (`"contract.settled"`/`ContractSettledEvent` được dùng lại nguyên, thêm `escrow-service` làm consumer mới để release `buyerDepositRate`). `§3` của file này đã cập nhật theo.

**KI-2 — ĐÃ FIX (04/07/2026), không còn treo.** Vấn đề gốc: buyer/seller tự thoả thuận chọn tổ chức Level 2 lúc `sign()` hoàn toàn tự do, không allowlist — 2 bên thông đồng có thể tự chọn tổ chức dễ dãi/tự dựng.

Fix đã chốt tại `inspection-phase2-design.md` §3.2: allowlist 3 nhóm (major quốc tế hardcode / trong nước verify qua BoA-VIAS / "lạ" thì Admin duyệt case-by-case, private, không lưu danh sách dùng chung), verify qua accreditation certificate number tra đúng cơ quan công nhận quốc gia. Tự động hoá tra cứu API BoA/ILAC để sau — deferred có chủ đích.

**KI-3 — [RESOLVED 16/07/2026] `GET /api/v1/users/{userId}` (user-service) lộ `email`.** Đã đóng bởi `user-service-phase2-design.md` §4.1: `PublicUserResponse` không email/phone/address, contact chỉ qua `/me` và internal API. Giữ đoạn dưới làm lịch sử phát hiện. IDOR đã fix `phone`/`address` nhưng `UserInfoResponse` vẫn còn field `email`. Endpoint cũng không có role/ownership check — bất kỳ user nào qua Gateway đều gọi được, không riêng service nội bộ. Không thuộc phạm vi `reputation-service` nhưng phát hiện lúc verify code liên quan Feign integration (§6.1) nên ghi lại đây. **Yêu cầu fix lúc phát hiện (ĐÃ THỰC HIỆN ở user-service §4.1 — 16/07/2026):** bỏ `email` khỏi DTO ra Gateway (chỉ giữ ở kênh service-to-service nếu cần), thêm ownership/role check — chỉ chính chủ `userId` hoặc service nội bộ (mTLS/service token) mới gọi được, đúng pattern proxy-qua-service-sở-hữu ở `file-service-phase2-design.md` §5. Không block đóng session reputation-service (bug nằm ở service khác).

---

## 11. Status — Reputation-service Design

**Chốt (04/07/2026):** Lock ledger insert-only, `lockDurationDays` snapshot bất biến. `trackRecordMultiplier` không time-decay (đo hoạt động tích cực, mùa vụ nông sản làm gap tự nhiên). `repeatOffenseMultiplier` có time window 24 tháng (đo hành vi tiêu cực, gap dài mang nghĩa thật). Enforcement qua use-case level (`sign()` fail-closed — ưu tiên đóng circuit-breaker gap ở đây trước, `CreateListing` cũng fail-closed từ 18/07/2026 — mọi gate eligibility cùng policy), không qua Gateway. Credit export định vị lại thành reference attestation, đối tác khả thi nhất là VARI, gate theo counterparty diversity. AML: composite score đa tín hiệu (luật định + suy luận), hold đồng bộ ở `CONFIRM_CLEAN` áp cho giao dịch kế tiếp (chấp nhận giao dịch làm lộ pattern không bị hold — giới hạn cấu trúc, đã xác nhận lại 04/07/2026), Admin trigger nhưng không tự chọn inspector, chi phí platform chịu.

**Chốt bổ sung (06/07/2026) — tách 2 nhóm tín hiệu AML (§8, §7):** phát hiện "đột biến doanh số" (tín hiệu tương đối) không thể đánh giá được trên account chưa có lịch sử — không phải lỗi threshold, mà là thiếu baseline để so sánh. Thêm nhóm tín hiệu **tuyệt đối** song song: ngưỡng giá trị chuyển khoản theo luật định (500 triệu đồng, Thông tư 27/2025/TT-NHNN) trigger `CONFIRM_CLEAN` hold **ngay trên chính giao dịch**, không cần chờ giao dịch kế tiếp như nhóm tương đối — đóng đúng phần gap "one-shot fraud" (tài khoản giả, 1 hợp đồng khủng, rút rồi bỏ) mà composite score cũ chỉ bắt được từ giao dịch thứ 2 trở đi. **Không đóng toàn bộ giới hạn cấu trúc cũ** — one-shot fraud dưới ngưỡng 500 triệu vẫn còn nguyên gap, ghi rõ ở §9.

**Không còn Known Issue treo (cập nhật 18/07/2026):** KI-3 đã RESOLVED bởi user-service §4.1 (16/07). **KI-1 và KI-2 đã đóng** (§10) — fix KI-1 nằm ở `milestone-escrow-phase2-design.md`, fix KI-2 nằm ở `inspection-phase2-design.md` §3.2.

**Chốt bổ sung (08/07/2026) — rà soát end-to-end, 2 điểm PHẢI SỬA áp dụng vào file này:**
- **A5** — ngưỡng tuyệt đối 500 triệu (Điều 9 TT27/2025/TT-NHNN, giao dịch chuyển tiền điện tử — số này **vẫn đúng, không đổi**, đã verify lại độc lập) không còn tự nó trigger hold. Detection dời sang `bank-service` (`bank.large_transaction_flagged`, đúng chủ thể pháp lý — bank giữ tiền, không phải platform). Hold chỉ kích hoạt khi ngưỡng tuyệt đối **đi kèm** ≥1 tín hiệu hành vi khác (track record mỏng/zero-variance/counterparty mới) — trước đó giá trị đơn thuần đã trigger hold, khiến gần như mọi hợp đồng thật (13,5-135 tỷ VNĐ theo ví dụ Doc02) bị treo chờ Admin, giết chết selling point "escrow tự thực thi" (§8).
- **B4** — reputation trước đây một chiều (buyer xem seller, seller không có công cụ tự bảo vệ). Đối xứng hoá: expose reputation của buyer cho seller xem trước khi ký (dữ liệu `lock_entry` đã insert-only cho cả 2 role từ đầu, chỉ thiếu chiều hiển thị); thêm tín hiệu chống buyer lạm dụng `FLAG_ISSUE` vô cớ (tỷ lệ flag-rồi-thua/rút) — xem §6.1, §7 mục mới.

**Chốt bổ sung (10/07/2026) — tầng batch AML + `ELEVATED_RISK` (§8 mục 6, §9):** đóng phần gap structuring chậm *có lặp lại* mà 2 tầng realtime không bắt được. `analytics-service.AmlPatternScanJob` (batch 90 ngày, cụm near-threshold) publish `analytics.structuring_pattern_detected` → `reputation-service` consume → set cặp `ELEVATED_RISK` (state bền, không phải trigger một-lần), route mọi giao dịch của cặp qua review tới khi Admin clear (cơ chế EDD chuẩn). Đường enforcement riêng, KHÔNG sửa gate composite mục 2. Bắt buộc clear-path (Admin gỡ, ghi audit chain) + expiry (`elevatedRiskReviewMonths`, chốt 6 tháng — tránh soi vĩnh viễn cặp false-positive). Nghĩa vụ báo cáo cơ quan tách sang `bank-service` (consumer thứ 2 của cùng event — cần thêm vào `bank-service-phase2-design.md`). Residual còn lại (vài mảnh đầu chưa đủ cụm + one-shot nhỏ lẻ thật) là giới hạn toán học của pattern-based detection, ghi honest ở §9.

**Cập nhật (17/07/2026):** (1) `reputation.unlocked` payload đổi — mang `effectiveLockedUntil` (nullable) tính lại từ các `lock_entry` còn `LOCKED` + `occurredAt` (cả locked lẫn unlocked) — đóng bug projection user-service mở khoá nhầm khi user có nhiều lock chồng nhau (§7). (2) Gỡ `ELEVATED_RISK` publish `reputation.elevated_risk_cleared` cho audit chain, `source_type = AML_RISK_CLEARED` (§8 mục 6, hash-chain §2.4). (3) `public-summary` chuyển authenticated ở Gateway (§6.1b).

**Chốt bổ sung (19/07/2026) — consume theo attribution (đợt 6, từ file research ScholarFirst):**
- **Input negative đổi nguồn (§3):** `remedy.finalized` là input negative **DUY NHẤT**, thay cho `milestone.cancelled_with_penalty`/`contract.cancelled` trực tiếp. `contract.terminated` chỉ phục vụ audit/analytics/notification, không tạo lock. Người bấm nút chấm dứt ≠ người vi phạm — reputation chỉ phạt theo attribution cuối, không theo `requestedBy`. `breach.reported` (allegation) KHÔNG BAO GIỜ là input.
- **Gate `breachReasonCode` (§4.3):** chỉ nhóm strategic tạo `lock_entry`; `PRODUCTION_SHOCK_NON_FM` không lock; mutual/FM/activation/allegation-bị-bác không lock. `zeroProgressMultiplier` siết điều kiện — chỉ áp strategic zero-progress.
- **Review lần 2 (19/07):** gỡ `contract.terminated` khỏi input chế tài (double-lock risk — 2 event, 2 sourceEventId, UNIQUE không chặn); gate đổi sang đọc `reputationEligible` flag từ payload thay tự suy reason; thêm `remedy_decision_id` UNIQUE ở `lock_entry` — một quyết định ≤ 1 lock đứng ở tầng DB.
- Đối xứng giữ nguyên: buyer bị lock qua cùng cơ chế khi `finalBreachingRole = BUYER` (`FUNDING_FAILURE` sau cure window là Rổ A, máy tự attribution — milestone-escrow §6b).

Reputation-service — **ĐÓNG SESSION HOÀN TOÀN, sẵn sàng đưa vào Architecture/SDS/TechnicalSpec chính thức.** Không có điểm treo số liệu: ngưỡng 500 triệu là số luật định thật; `elevatedRiskReviewMonths` chốt 6 tháng (`application.yml`, tinh chỉnh được khi có dữ liệu vận hành thật). KI-3 (`email` IDOR) đã RESOLVED bởi user-service §4.1 — 18/07/2026. VICOFA lever giữ ở Known Limitation §9 (giả định thể chế ngoài scope đồ án).

---

*Design session: 04/07/2026 · Cập nhật: 04/07/2026 (đóng KI-1, note ưu tiên circuit breaker `sign()`, xác nhận lại giới hạn AML hold) · Cập nhật: 06/07/2026 (thêm nhóm tín hiệu AML tuyệt đối — ngưỡng luật định 500 triệu, trigger hold ngay trên giao dịch đầu tiên, §7/§8/§9) · Cập nhật: 08/07/2026 (rà soát end-to-end: hold tuyệt đối cần đi kèm tín hiệu hành vi, detection dời sang bank-service — A5; đối xứng hoá reputation buyer/seller + tín hiệu chống flag-abuse — B4) · Cập nhật: 13/07/2026 (KI-3 ghi rõ thành must-fix ở user-service, không block; VICOFA lever giữ ở Known Limitation §9) · Cập nhật 17/07/2026 (effectiveLockedUntil + occurredAt trên lock events; reputation.elevated_risk_cleared cho audit chain; public-summary authenticated) · Review pass 18/07/2026 (lock_entry bất biến thật + lock_override_event; zero_progress_multiplier vào snapshot; governance_action_request + pair_risk_state; lockRevision; KI-3/dispute_resolved đánh dấu RESOLVED) · Cập nhật 19/07/2026 (đợt 6 — `remedy.finalized` là input negative duy nhất theo `AttributionDecision`; gate `reputationEligible`; persist immutable facts + `remedy_decision_id` UNIQUE) · Chưa code · **Sẵn sàng đưa vào Architecture/SDS/TechnicalSpec chính thức.***
