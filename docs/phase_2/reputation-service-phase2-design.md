---
name: reputation-service-phase2-design
description: "Reputation-service — lock ledger bất biến + reputation score, phục vụ lockout enforcement, search ranking, và credit reference export. Nguồn: design session 04/07/2026, cập nhật 06/07/2026 (AML: thêm nhóm tín hiệu tuyệt đối theo ngưỡng luật định)."
status: DESIGNED — chưa code.
metadata:
  type: design
  phase: 2
  extends: "services.md § reputation-service (8088)"
  related: "milestone-escrow-phase2-design.md §6, §6.3 (nguồn trigger lockout, công thức lockDurationDays, buyerDepositRate release paths); bank-service-phase2-design.md §4 (pattern idempotency/insert-only tương tự); inspection-phase2-design.md (routing INSPECTOR cho AML trigger); AgriContract_01 mục 6 (credit infra); AgriContract_02 §5.2 Rủi ro 4 (giao dịch khống)"
---

## 1. Bối cảnh & Scope

**Chốt (04/07/2026):** `services.md` mô tả `reputation-service` là "pure event-driven read model" — mô tả này chỉ đúng 1 phần, không phải sai hoàn toàn. Đúng nguyên tắc đã dùng ở `inspection-phase2-design.md` §3.1 (framework gốc không giữ quyết định chi tiết, doc sau override mà không cần sửa lại services.md): `reputation-service` thực chất gánh **3 vai trò khác bản chất**, không phải 1 domain đơn thuần:

1. **Lock ledger** — bất biến, ghi 1 lần, phục vụ lockout enforcement (milestone-escrow §6). Đây là phần *không* thể là pure read model.
2. **Reputation score** — view sống, tính lại được, phục vụ search ranking (search-service, chưa thiết kế).
3. **Credit reference** — export cho bên thứ 3 (khả năng cao nhất: VARI, xem §6.3), vai trò bổ trợ.

Không tách 3 vai trò này thành 3 service riêng trong Phase 2 (over-engineer so với quy mô đồ án) — nhưng thiết kế domain model phải phản ánh đúng 3 loại dữ liệu khác nhau, không gộp chung logic.

---

## 2. Domain Model

### 2.1 Lock Ledger (bất biến — insert-only)

**Chốt (04/07/2026):** `lockDurationDays` snapshot cứng lúc tính, không recompute lại sau đó — kể cả khi input dùng để tính nó (`trackRecordMultiplier`) đổi giá trị sau này. Lý do: đây là bằng chứng pháp lý (Luật TM 2005 Điều 302, penalty debt dùng làm căn cứ truy đòi qua VIAC/toà nếu có tranh chấp) — 1 con số có thể tự đổi sau khi ghi thì không còn giá trị làm bằng chứng.

| Field | Loại | Ghi chú |
|---|---|---|
| `entryId` | UUID | |
| `sourceEventId` | UUID | Idempotency key — ID của event gốc kích hoạt (`milestone.cancelled_with_penalty`). `UNIQUE`, cùng pattern `bank-service` đã dùng. |
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
    entry_id                UUID PRIMARY KEY,
    source_event_id          UUID NOT NULL UNIQUE,
    contract_id              UUID NOT NULL,
    user_id                  UUID NOT NULL,
    penalized_role           VARCHAR(10) NOT NULL,   -- BUYER | SELLER
    base_days                INT NOT NULL DEFAULT 30,
    repeat_offense_multiplier DECIMAL(3,2) NOT NULL,
    track_record_multiplier  DECIMAL(3,2) NOT NULL,
    lock_duration_days       INT NOT NULL,
    locked_until             TIMESTAMP NOT NULL,
    status                   VARCHAR(20) NOT NULL,   -- LOCKED | UNLOCKED_EARLY | EXPIRED
    unlock_reason            TEXT NULL,
    created_at                TIMESTAMP NOT NULL DEFAULT now()
);
CREATE INDEX idx_lock_entry_user ON lock_entry(user_id);
```

**Chốt (04/07/2026) — Insert-only, enforce ở tầng DB permission:** DB user của `reputation-service` chỉ có quyền `INSERT` + `SELECT` trên bảng `lock_entry`, không có `UPDATE`/`DELETE`. Lý do không tin ở tầng application code: "không bao giờ xoá" là lời hứa yếu nếu chỉ dựa vào việc code không gọi hàm xoá — 1 dòng code sai ở đâu đó trong tương lai có thể phá vỡ mà không ai biết. Chặn ở DB permission thì kể cả bug code cũng không phá được. Cùng nguyên tắc `audit-service` đã áp (services.md gap #5).

### 2.2 Reputation Score (view sống — không lưu riêng)

Không có bảng riêng lưu sẵn con số. Mọi truy vấn (completion count, clean rate) tính trực tiếp từ `lock_entry` (phần vi phạm) + input event `contract.settled` (phần hoàn thành) tại thời điểm cần — lý do xem §5.2.

---

## 3. Input Events

| Event | Loại | Nguồn | Trạng thái |
|---|---|---|---|
| `milestone.cancelled_with_penalty` | Negative | `contract-service`, đã có trong Event Catalog milestone-escrow §7.1 | Sẵn sàng dùng |
| `contract.settled` | Positive | `contract-service` (`ContractSettledEvent`), đã tồn tại trong code Phase 1 | **Đã fix (04/07/2026) — xem cập nhật KI-1 ở §10.** Guard `Contract.settle()` được sửa để chạy từ `ACTIVE`, và `escrow-service` cũng consume event này để release `buyerDepositRate` — xem `milestone-escrow-phase2-design.md` §3.1, §6.3, §7.2. |

---

## 4. Multiplier Formulas

**Bảng tra nhanh** (nguồn gốc: `milestone-escrow-phase2-design.md` §6.1, copy nguyên vào đây để file này tự đứng được, không phải mở file khác giữa chừng):

```
lockDurationDays = baseDays(30) × repeatOffenseMultiplier × trackRecordMultiplier
```

| Multiplier | Giá trị | Điều kiện |
|---|---|---|
| `baseDays` | 30 | Cố định |
| `repeatOffenseMultiplier` | 1x / 2x / 3x | Lần vi phạm thứ mấy trong `repeatOffenseLookbackMonths` gần nhất (§4.2) |
| `trackRecordMultiplier` | 0.7x / 1.0x / 1.3x | Dưới 5 hợp đồng hoàn thành: mặc định 1.0x. Đủ 5 trở lên: tính theo % sạch thật (0.7x nếu track record tốt, 1.3x nếu kém) — không time window (§4.1) |

### 4.1 `trackRecordMultiplier` — KHÔNG có time window

Đo hoạt động **tích cực** (hợp đồng hoàn thành sạch) — mùa vụ nông sản tự nhiên tạo khoảng trống dài giữa các hợp đồng (không phải dấu hiệu seller nghỉ giao dịch có vấn đề gì), nên không áp time window, chỉ áp ngưỡng số lượng tối thiểu (5 hợp đồng).

### 4.2 `repeatOffenseMultiplier` — CÓ time window 24 tháng

Đo hành vi **tiêu cực** (vi phạm) — ngược lại với §4.1, khoảng cách dài giữa 2 lần vi phạm mang ý nghĩa thật (seller đã sửa hành vi), nên chỉ đếm vi phạm trong 24 tháng gần nhất, không cộng dồn vĩnh viễn.

---

## 5. Insert-only & Reputation Score — chi tiết bổ sung

### 5.1 Vì sao Lock Ledger phải insert-only

Xem §2.1 — bằng chứng pháp lý, DB permission enforce, không tin code.

### 5.2 Vì sao Reputation Score là view sống, không lưu bảng riêng

Tính lại mỗi lần cần, từ `lock_entry` + `contract.settled` — tránh dual-write giữa "con số cache" và "sự thật từ 2 nguồn dữ liệu insert-only", đúng bài học đã rút ở `bank-service` §5 (cache số tiền là dual-write, cache state flag thì ổn).

---

## 6. Tích hợp liên service

### 6.1 `user-service` — enforcement

**Chốt (04/07/2026):** không thể chặn ở tầng Gateway/Keycloak — Keycloak chỉ cấp JWT (identity + role), không biết business lock state. `UserContextInjectionFilter` (api-gateway) không gọi `user-service` để hỏi trạng thái — verify thuần chữ ký JWT.

Check phải nằm ở đúng use case tạo nghĩa vụ mới:

- **`CreateListing` (product-service) / tạo offer** — fail-open. Chưa có Feign client tới `user-service` (cần thêm mới).
- **`sign()` (contract-service)** — fail-closed, bắt buộc. Đã có sẵn `UserPort`/`UserServiceClient` (Feign) — chỉ cần thêm `lockedUntil` vào response `UserInfo` + `@CircuitBreaker` với fallback throw (services.md gap #1, **ưu tiên đóng gap này trước tất cả Feign call khác trong Phase 2** — chốt 04/07/2026 ở `inspection-phase2-design.md`/session review: `sign()` fail-closed nằm trên đường ký hợp đồng, `user-service` down không có breaker sẽ chặn toàn bộ platform ký hợp đồng, không phải lỗi cục bộ 1 case).

Lý do 2 tầng không thừa nhau: khoá chỉ chặn **tạo mới**, không hồi tố hợp đồng đã ACTIVE/SIGNED (milestone-escrow §6.1 mục 2). Seller sạch lúc tạo offer, dính khoá giữa chừng đàm phán — chỉ `sign()` check lại mới bắt được; `CreateListing`-only sẽ bỏ sót case này.

`reputation-service` publish event (`reputation.locked`/`reputation.unlocked`), `user-service` cache 1 field trên `UserProfile` (không gọi sync mỗi lần cần check) — tránh dual-write, đúng bài học đã rút từ `bank-service` §5 (cache state flag là ổn, cache số tiền mới là dual-write).

### 6.2 `search-service`

Chưa thiết kế — để dành session riêng. Kỳ vọng: `reputation-service` publish event mỗi khi score đổi, `search-service` tự consume để cập nhật bản denormalized, không gọi sync mỗi lần search.

### 6.3 Credit reference export

**Research (04/07/2026):** CIC (Trung tâm Thông tin Tín dụng Quốc gia) không áp dụng trực tiếp — seller trên platform không có lịch sử vay ngân hàng để CIC đối chiếu. Hướng đúng: **VARI** (Chương trình xếp hạng tín nhiệm doanh nghiệp nông nghiệp Việt Nam, www.vari.org.vn) — tổ chức trung gian đã tồn tại sẵn cho đúng ngành, đóng vai trò "người đọc dữ liệu platform rồi đưa cho ngân hàng", không cần platform tự thuyết phục từng ngân hàng riêng lẻ. Có tín hiệu chính sách ủng hộ: Luật Hỗ trợ DNVVN (sửa đổi) đang xây dựng cơ chế sandbox cho vay dựa trên dữ liệu/dòng tiền.

**Định vị lại vai trò (04/07/2026):** `reputation-service` export là **reference/reputation attestation**, không phải nguồn chấm điểm tín dụng chính — reputation (đáng tin để giao dịch tiếp) và cash-flow data (đủ tiền trả nợ) đo 2 chuyện khác nhau, không thay thế nhau.

Thiết kế:
- Endpoint riêng `GET /reputation/{userId}/credit-export`, JSON theo 4 nhóm phổ quát (lịch sử thanh toán đúng hạn, quy mô/tần suất giao dịch, vi phạm/nợ xấu, thâm niên hoạt động — bao gồm `firstContractDate`/`activeMonthsSpan`, không chỉ đếm số lượng).
- **Chỉ chạy khi seller chủ động yêu cầu** (consent rõ ràng), không phải endpoint mở tự động đẩy dữ liệu ra ngoài — đúng cơ chế CIC vận hành (người vay tự tra cứu mang đi).
- **Gate theo counterparty diversity** trước khi cho export — seller chỉ giao dịch với 1-2 đối tác dù đủ 5+ hợp đồng "sạch" cũng không đủ điều kiện, giảm động cơ tạo hợp đồng giả để làm đẹp hồ sơ vay vốn (xem §7).

---

## 7. Use Case Changes

- **`ProcessLockoutUseCase`** — consume `milestone.cancelled_with_penalty`, tính `repeatOffenseMultiplier` + `trackRecordMultiplier`, ghi `lock_entry` mới (insert-only), publish `reputation.locked`.
- **`UnlockEarlyUseCase`** — Admin trigger, set `status = UNLOCKED_EARLY` + `unlockReason` bắt buộc, publish `reputation.unlocked`. Dùng cho nhánh 2, 3 ở §5.
- **`CheckLockStatusUseCase`** — expose cho `user-service` gọi qua Feign (§6.1), trả `lockedUntil` hiện tại của 1 `userId`.
- **`GetCreditExportUseCase`** — seller tự trigger (consent-based, §6.3), check counterparty diversity gate trước khi trả JSON export.
- **`FlagSuspiciousPatternUseCase`** — tính composite fraud score (§8), theo cặp buyer-seller/account, publish event hold khi vượt ngưỡng. **Cập nhật (06/07/2026):** tách 2 nhóm — tín hiệu tương đối (cần lịch sử, hold giao dịch kế tiếp) và tín hiệu tuyệt đối (ngưỡng luật định ~500 triệu, hold ngay trên chính giao dịch, kể cả giao dịch đầu tiên của account mới).

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
   - **Ngưỡng giá trị tuyệt đối theo luật định** — giao dịch chuyển khoản từ 500 triệu đồng trở lên (Thông tư 27/2025/TT-NHNN, hiệu lực 01/11/2025, thay ngưỡng 400 triệu cũ) thuộc nhóm phải báo cáo/nhận biết khách hàng, bất kể account có lịch sử hay không — đây là con số so với luật, không phải so với chính account đó ngày hôm qua.
   - **Trigger ngay trên chính giao dịch đó**, không chờ "giao dịch kế tiếp" như nhóm tín hiệu tương đối — vì không cần baseline để đánh giá, nhóm này có thể áp dụng ngay cả cho giao dịch đầu tiên của account hoàn toàn mới.
   - **Không thay thế nhóm tương đối** — 2 nhóm giải 2 loại rủi ro khác nhau: nhóm tương đối bắt fraud tích luỹ nhiều giao dịch nhỏ qua thời gian (giá trị vẫn dưới ngưỡng tuyệt đối); nhóm tuyệt đối bắt đúng kịch bản "đánh nhanh rút gọn" (one-shot, tài khoản giả, 1 hợp đồng khủng rồi bỏ) mà nhóm tương đối theo định nghĩa không bắt được lần đầu.

2. **`CONFIRM_CLEAN` hold đồng bộ** khi composite score vượt ngưỡng — chuyển chờ Admin duyệt thay vì auto-`SETTLED` như mặc định. Căn cứ: "biện pháp trì hoãn giao dịch" (Luật PCRT 2022) không cần chờ giao dịch hoàn tất mới áp dụng được. **Với nhóm tín hiệu tương đối:** hold chỉ áp cho giao dịch **kế tiếp** của đúng cặp bị flag, không hồi tố hợp đồng đã `SETTLED`, không vạ lây cặp khác. **Xác nhận lại (04/07/2026, session review):** hệ quả chấp nhận được của cách chốt này là chính giao dịch làm score vượt ngưỡng **không** bị hold — chỉ giao dịch tiếp theo mới bị. Đây là giới hạn cấu trúc của kiểu detect dựa trên pattern lặp lại (cần đủ lịch sử mới nhận ra), không sửa được bằng cách đổi threshold. Giữ nguyên chủ đích, không đổi. **Với nhóm tín hiệu tuyệt đối (mới, 06/07/2026):** hold áp ngay trên **chính giao dịch đang vượt ngưỡng**, không đợi giao dịch kế tiếp — đóng đúng phần gap "one-shot fraud" mà nhóm tương đối không đóng được.

3. **Admin trigger inspection đột xuất** khi nghi ngờ, nhưng **không tự chọn tổ chức inspect** — hệ thống random từ danh sách đã vet sẵn (đồng nhất cơ chế Level 1.5 hiện có: Vinacontrol/Quatest, route tự động, không phải chọn tay). Tránh tái tạo đúng lỗ hổng đang có ở Level 2 (`level2InspectorOrg` negotiate tự do — xem KI-2).

4. **Chi phí inspection do nghi ngờ pattern = platform chịu**, không đổ cho buyer/seller. Lý do: nghĩa vụ Điều 29 NĐ 52/2024 là nghĩa vụ của platform, không phải user; tránh phạt oan false positive, đặc biệt nhóm HTX nhỏ vốn có counterparty concentration cao tự nhiên (không phải dấu hiệu gian lận).

5. **Mục tiêu thiết kế = đạt bar "có biện pháp quản trị rủi ro"** (Điều 29 NĐ 52/2024) — không phải bắt 100% fraud (bất khả thi với collusion đủ nguồn lực). Audit trail bất biến (§2.1 insert-only) là bằng chứng due diligence nếu bị điều tra, không phải cơ chế ngăn chặn tuyệt đối.

---

## 9. Out of Scope (có chủ đích, không phải thiếu sót)

- **Tích hợp thật với `search-service`** (§6.2) — chưa thiết kế, để dành session riêng. Chỉ note kỳ vọng interface (publish event khi score đổi), không code.
- **Tích hợp thật với VARI hoặc bất kỳ ngân hàng nào** (§6.3) — chưa ký kết, chưa có đối tác thật. Thiết kế sẵn interface export (`GetCreditExportUseCase`) để nếu sau này có đối tác thật, chỉ cần thêm adapter, không đổi business logic. Cùng tinh thần `bank-service` doc đã làm với vai trò arbitrator.
- **Multi-currency** — không cần, platform chỉ giao dịch VNĐ.
- **Giải quyết dứt điểm ngân sách chi phí inspection** (§8 mục 4) — chỉ model đúng field (`triggerType`, `costBearer`) trong `InspectionRequest` (thuộc `inspection-service`, không phải `reputation-service`), không giải quyết nguồn ngân sách thật trong đồ án.
- **KI-2, KI-3** (§10) — đã phát hiện, chưa fix trong session này, không thuộc phạm vi thiết kế `reputation-service` (KI-2 thuộc `inspection-service`, KI-3 thuộc `user-service`). **KI-1 đã đóng — xem §10.**
- **Đòn bẩy kinh tế thay thế cho `lock_entry` lockout ở năm đầu vận hành (bảo hiểm, gắn quyền lợi hội viên VICOFA...)** — đã verify (04/07/2026): điều lệ công khai của VICOFA (`vicofa.org.vn`) chỉ cho phép khai trừ/đình chỉ hội viên khi vi phạm ảnh hưởng tới uy tín/tài sản/tài chính **của chính VICOFA** hoặc vi phạm quy chế **của VICOFA** — không có điều khoản nào gắn hành vi bẻ kèo hợp đồng thương mại ghi nhận qua 1 hệ thống bên ngoài. Không phải giả định lửng lơ — đây là 1 câu hỏi cụ thể có thể mang đi hỏi thăm qua kênh sẵn có (NHL, business validation outreach): VICOFA có sẵn sàng ban hành quy chế mới gắn dữ liệu `lock_entry` vào hệ quả hội viên (quota, đoàn xúc tiến...), tách biệt khỏi việc phân xử đúng-sai trong tranh chấp cụ thể (giữ neutrality đã chốt ở `bank-service-phase2-design.md`) hay không — nhưng nằm ngoài khả năng đồ án tự xác nhận hay quyết định. Giữ nguyên `lock_entry`/`lockDurationDays` hiện tại làm baseline, không thiết kế thêm cơ chế phụ thuộc 1 giả định thể chế chưa xác nhận.
- **One-shot fraud dưới ngưỡng tuyệt đối (mới, 06/07/2026)** — nhóm tín hiệu tuyệt đối (§8 mục 1) chỉ đóng gap cho giao dịch **vượt** 500 triệu đồng. Giao dịch giả mạo giá trị vừa/nhỏ, giao dịch đầu tiên của account mới, vẫn còn nguyên giới hạn cấu trúc cũ (chỉ hold được giao dịch kế tiếp, không hold được chính giao dịch đó) — không có cách đóng bằng công cụ pattern-based, đã xác nhận rõ ở §8.

---

## 10. Known Issues (phát hiện qua đối chiếu code Phase 1 — không block thiết kế này, cần nhớ khi implement)

**KI-1 — ĐÃ FIX (04/07/2026), không còn treo.** Vấn đề gốc: `Contract.settle()` (contract-service) chỉ chạy được nếu status hiện tại là `DELIVERED` — di sản two-phase lock Phase 1. `completeAllMilestones()` (method thay thế Phase 2) sẽ gọi `settle()` khi Contract đang `ACTIVE` (không bao giờ đạt `DELIVERED` nữa) → throw ngay. Kéo theo dead path: `confirmDelivery()`, `ContractDeliveredEvent`/`"contract.delivered"`, consumer ở `escrow-service.ContractEventConsumer` và `notification-service.NotificationEventConsumer`.

Fix + dead-path cleanup đã chốt tại `milestone-escrow-phase2-design.md` §3.1 (guard sửa để chạy từ `ACTIVE`) và §6.3/§7.2 (`"contract.settled"`/`ContractSettledEvent` được dùng lại nguyên, thêm `escrow-service` làm consumer mới để release `buyerDepositRate`). `§3` của file này đã cập nhật theo.

**KI-2 — ĐÃ FIX (04/07/2026), không còn treo.** Vấn đề gốc: buyer/seller tự thoả thuận chọn tổ chức Level 2 lúc `sign()` hoàn toàn tự do, không allowlist — 2 bên thông đồng có thể tự chọn tổ chức dễ dãi/tự dựng.

Fix đã chốt tại `inspection-phase2-design.md` §3.2: allowlist 3 nhóm (major quốc tế hardcode / trong nước verify qua BoA-VIAS / "lạ" thì Admin duyệt case-by-case, private, không lưu danh sách dùng chung), verify qua accreditation certificate number tra đúng cơ quan công nhận quốc gia. Tự động hoá tra cứu API BoA/ILAC để sau — deferred có chủ đích.

**KI-3 — `GET /api/v1/users/{userId}` (user-service) vẫn lộ `email`.** IDOR đã fix `phone`/`address` nhưng `UserInfoResponse` vẫn còn field `email`. Endpoint cũng không có role/ownership check — bất kỳ user nào qua Gateway đều gọi được, không riêng service nội bộ. Không thuộc phạm vi `reputation-service` nhưng phát hiện lúc verify code liên quan Feign integration (§6.1) nên ghi lại đây. Vẫn treo.

---

## 11. Status — Reputation-service Design

**Chốt (04/07/2026):** Lock ledger insert-only, `lockDurationDays` snapshot bất biến. `trackRecordMultiplier` không time-decay (đo hoạt động tích cực, mùa vụ nông sản làm gap tự nhiên). `repeatOffenseMultiplier` có time window 24 tháng (đo hành vi tiêu cực, gap dài mang nghĩa thật). Enforcement qua use-case level (`sign()` fail-closed — ưu tiên đóng circuit-breaker gap ở đây trước, `CreateListing` fail-open), không qua Gateway. Credit export định vị lại thành reference attestation, đối tác khả thi nhất là VARI, gate theo counterparty diversity. AML: composite score đa tín hiệu (luật định + suy luận), hold đồng bộ ở `CONFIRM_CLEAN` áp cho giao dịch kế tiếp (chấp nhận giao dịch làm lộ pattern không bị hold — giới hạn cấu trúc, đã xác nhận lại 04/07/2026), Admin trigger nhưng không tự chọn inspector, chi phí platform chịu.

**Chốt bổ sung (06/07/2026) — tách 2 nhóm tín hiệu AML (§8, §7):** phát hiện "đột biến doanh số" (tín hiệu tương đối) không thể đánh giá được trên account chưa có lịch sử — không phải lỗi threshold, mà là thiếu baseline để so sánh. Thêm nhóm tín hiệu **tuyệt đối** song song: ngưỡng giá trị chuyển khoản theo luật định (500 triệu đồng, Thông tư 27/2025/TT-NHNN) trigger `CONFIRM_CLEAN` hold **ngay trên chính giao dịch**, không cần chờ giao dịch kế tiếp như nhóm tương đối — đóng đúng phần gap "one-shot fraud" (tài khoản giả, 1 hợp đồng khủng, rút rồi bỏ) mà composite score cũ chỉ bắt được từ giao dịch thứ 2 trở đi. **Không đóng toàn bộ giới hạn cấu trúc cũ** — one-shot fraud dưới ngưỡng 500 triệu vẫn còn nguyên gap, ghi rõ ở §9.

**1 Known Issue còn treo, không block session này:** KI-3 (`email` IDOR còn sót). **KI-1 và KI-2 đã đóng** (§10) — fix KI-1 nằm ở `milestone-escrow-phase2-design.md`, fix KI-2 nằm ở `inspection-phase2-design.md` §3.2.

Reputation-service — **đóng session, sẵn sàng đưa vào Architecture/SDS/TechnicalSpec chính thức**, ngoại trừ bổ sung 06/07/2026 (tách 2 nhóm tín hiệu AML, xem ngay trên) — phần này về nguyên tắc đã chốt, không có điểm treo số liệu (ngưỡng 500 triệu là số luật định thật, không phải placeholder đoán).

---

*Design session: 04/07/2026 · Cập nhật: 04/07/2026 (đóng KI-1, note ưu tiên circuit breaker `sign()`, xác nhận lại giới hạn AML hold) · Cập nhật: 06/07/2026 (thêm nhóm tín hiệu AML tuyệt đối — ngưỡng luật định 500 triệu, trigger hold ngay trên giao dịch đầu tiên, §7/§8/§9) · Chưa code · Chưa đưa vào Architecture/SDS/TechnicalSpec chính thức.*
