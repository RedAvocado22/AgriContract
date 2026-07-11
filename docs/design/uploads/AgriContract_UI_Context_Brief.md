# AgriContract — UI Context Brief (dành cho Claude Design)

> **Cách dùng:** Paste **PHẦN 0 → 3** một lần đầu tiên (đây là context nền, Claude Design sẽ giữ trong hội thoại).
> Sau đó mỗi lần dựng 1 màn thì paste **spec màn đó ở PHẦN 5** + câu lệnh `Dựng màn [tên] theo spec dưới đây, dùng đúng design system và component library đã thống nhất.`
> PHẦN 6 (component) và PHẦN 7 (notification) paste khi cần dựng component library hoặc notification center.

---

# PHẦN 0 — SẢN PHẨM LÀ GÌ

AgriContract là nền tảng **B2B số hoá hợp đồng nông sản** cho thị trường Việt Nam, phục vụ 4 mặt hàng: **cà phê, gạo, cao su, điều**.

Vấn đề nó giải: hợp đồng nông sản kỳ hạn (forward contract) ở VN thường bị **bẻ kèo** khi giá thị trường biến động — nông dân/HTX bán ra ngoài khi giá lên, doanh nghiệp ép giá hoặc chậm trả khi giá xuống. Không có cơ chế nào ràng buộc ngoài niềm tin. Bên yếu thế (nông dân/HTX) chịu rủi ro lớn hơn.

Cách nó giải:
1. **Ký quỹ tự thực thi (self-enforcing escrow)** — tiền bị khoá trước, giải ngân tự động theo cột mốc.
2. **Cột mốc giao hàng (milestone)** — mỗi đợt giao là 1 milestone có vòng đời riêng, giải ngân riêng.
3. **Giải quyết tranh chấp 3 tầng** — Admin nội bộ → Vinacontrol/Quatest → SGS/Bureau Veritas.
4. **Điểm uy tín (reputation)** — vi phạm bị khoá tài khoản có thời hạn, lịch sử công khai cho đối tác xem trước khi ký.
5. **Truy xuất nguồn gốc + EUDR** — với cà phê/cao su, phải khai vùng trồng (toạ độ) để tuân thủ EU 2023/1115.

**Nguyên tắc thiết kế xuyên suốt sản phẩm** (phải phản ánh vào UI):
- Khi có xung đột giữa bảo vệ buyer và bảo vệ seller → **nghiêng về seller**, vì seller (nông dân/HTX) là bên yếu thế hơn.
- **Sổ cái chỉ ghi thêm (append-only)** — không có nút sửa/xoá bút toán ở bất kỳ đâu trong UI. Đây là tính năng, không phải hạn chế, và nó phải **nhìn thấy được** trên màn hình.
- **Thất bại thành thật hơn là đúng-một-cách-tự-tin** — nếu hệ thống không chắc, nó hiển thị "chưa xác minh được", không đoán bừa. (VD: `INCONCLUSIVE` ≠ `HIGH_RISK`.)

---

# PHẦN 1 — TONE & DESIGN SYSTEM

## 1.1 Tính cách hình ảnh

Đây là **công cụ tài chính**, không phải app tiêu dùng. Trên màn hình có tiền thật đang bị khoá. Cảm giác cần đạt: **điềm tĩnh, chính xác, kiểm chứng được** — gần với dashboard ngân hàng doanh nghiệp hơn là fintech app.

Cấm:
- Gradient, glow, đổ bóng nặng, hiệu ứng neon.
- Illustration vui nhộn, emoji, badge kiểu game hoá ("Bạn đạt cấp Vàng!").
- Màu dùng để trang trí. Màu chỉ được mang **nghĩa trạng thái**.
- Con số làm tròn cho đẹp. Tiền hiển thị đủ chữ số, có dấu phân cách hàng nghìn.

## 1.2 Token

```
FONT
  Chữ:        "Be Vietnam Pro" (hỗ trợ tiếng Việt tốt, dáng trung tính)
  Số/mã:      "JetBrains Mono" — dùng cho mã hợp đồng, mã bút toán, hash
  Cỡ:         Title 22 / Heading 18 / Subheading 16 / Body 14 / Caption 13 / Micro 11
  Đậm:        chỉ 2 mức — Regular 400 và Medium 500. Không dùng 600/700.
  Số tiền:    font-variant-numeric: tabular-nums (cột số phải thẳng hàng)

MÀU NỀN & VIỀN
  --page-bg        #F8FAF9    nền trang
  --surface        #FFFFFF    mặt card
  --surface-muted  #F1F5F4    card thống kê, nền phụ
  --border         #E2E8F0    viền hairline mặc định (0.5px)
  --border-strong  #CBD5E1    viền nhấn

CHỮ
  --text-primary   #0F172A
  --text-secondary #64748B
  --text-muted     #94A3B8

MÀU NGỮ NGHĨA (chỉ dùng đúng nghĩa, không dùng trang trí)
  Primary / brand      #15803D   hành động chính, nav active
  Success / đã xong    #16A34A   hoàn thành, đã giải ngân, LOW_RISK
  Warning / đang chờ   #D97706   chờ xác nhận, sắp đến hạn, INCONCLUSIVE
  Danger / vi phạm     #DC2626   tranh chấp, phạt, quá hạn, HIGH_RISK
  Info / đang chạy     #2563EB   đang thực hiện, đang xử lý
  Neutral / chưa tới   #94A3B8   chưa bắt đầu, đã đóng, UNVERIFIED

  Nền nhạt tương ứng (dùng cho badge/banner):
  Success #DCFCE7 · Warning #FEF3C7 · Danger #FEE2E2 · Info #DBEAFE · Neutral #F1F5F9
  Chữ trên nền nhạt luôn dùng tông đậm nhất cùng họ màu, không dùng đen.

HÌNH DẠNG
  Bo góc:   card 12px · input/button 8px · badge/pill 999px · avatar 50%
  Viền:     0.5px solid var(--border) — mọi nơi
  Bóng:     không. Trừ focus ring: 0 0 0 2px rgba(21,128,61,0.2)
  Khoảng:   dọc dùng rem (1 / 1.5 / 2) · trong component dùng px (8 / 12 / 16)

ICON
  Bộ outline (Lucide hoặc Material Symbols Outlined). 16-20px inline, 24px tối đa.
  Không dùng icon filled. Không dùng emoji.

LAYOUT
  App shell:  sidebar trái cố định 240px + top bar 64px + content max 1440px
  Grid:       12 cột, gutter 24px
  Mobile:     sidebar → bottom tab bar 5 mục, table → card list
```

## 1.3 Quy tắc viết chữ (microcopy)

- **Sentence case** ở mọi nơi. "Tạo hợp đồng", không phải "Tạo Hợp Đồng".
- Nút = **động từ đứng trước**, 1–3 từ, không dấu chấm: "Tạo hợp đồng", "Xác nhận nhận hàng", "Ký hợp đồng".
- Lỗi = **chuyện gì xảy ra + làm gì tiếp**, một câu, không tiền tố "Lỗi:". VD: `Không khoá được ký quỹ. Kiểm tra số dư rồi thử lại.`
- Không dùng: "vui lòng", "thành công", "chỉ cần", "đơn giản", dấu chấm than.
- Không dùng tiếng lóng cho vi phạm. Dùng "vi phạm hợp đồng", không dùng "bùng kèo".
- Số tiền: `1.250.000.000 ₫` — dấu chấm ngăn nghìn, ký hiệu ₫ đứng sau, cách 1 space.
- Khối lượng: `20 tấn` / `20.500 kg`. Tỷ lệ: `± 2%`.
- Ngày: `12/05/2026`. Ngày + giờ: `12/05/2026 14:30`.

---

# PHẦN 2 — NGƯỜI DÙNG & VAI TRÒ

| Vai trò | Ai | Đăng nhập? | Thấy gì |
|---|---|---|---|
| `BUYER` | Doanh nghiệp thu mua, nhà xuất khẩu | Có | Tìm nguồn hàng, tạo hợp đồng, nạp ký quỹ, xác nhận nhận hàng, flag vấn đề |
| `SELLER` | Nông dân, HTX | Có | Đăng bán, khai vùng trồng, cân hàng + upload bằng chứng, claim bất khả kháng |
| `INSPECTOR` (Level 1.5) | Vinacontrol, Quatest | Có | Chỉ thấy các vụ được giao. Nộp + ký báo cáo giám định |
| `ADMIN` | Vận hành nền tảng | Có | Duyệt bất khả kháng, xử tranh chấp Level 1, duyệt danh mục, đóng băng hệ thống |
| Level 2 (SGS, Bureau Veritas) | Tổ chức quốc tế | **KHÔNG** | Không có tài khoản. Báo cáo về qua email `intake@`, Admin xác nhận thủ công |

Buyer và Seller **dùng chung một app shell**, khác nhau ở nội dung và hành động khả dụng. Inspector và Admin có shell riêng, gọn hơn.

Bối cảnh dùng thật: seller thường là HTX ở nông thôn, dùng **điện thoại**, mạng chậm, thao tác chính là **chụp ảnh cân hàng và upload**. Buyer ngồi văn phòng, dùng **desktop**, cần bảng biểu dày dữ liệu. Thiết kế phải phục vụ cả hai — không ép seller dùng data table.

---

# PHẦN 3 — TỪ ĐIỂN DOMAIN → NHÃN → MÀU

Đây là bảng tra bắt buộc. Mọi trạng thái xuất hiện trên UI phải lấy nhãn và màu từ đây, không tự chế.

## 3.1 Mặt hàng (`commodity`)

| Enum | Nhãn | Ghi chú |
|---|---|---|
| `COFFEE` | Cà phê | **Thuộc EUDR** → bắt buộc khai vùng trồng |
| `RUBBER` | Cao su | **Thuộc EUDR** → bắt buộc khai vùng trồng |
| `RICE` | Gạo | Không thuộc EUDR → **ẩn hoàn toàn** tầng vùng trồng khỏi UI |
| `CASHEW` | Điều | Không thuộc EUDR → **ẩn hoàn toàn** tầng vùng trồng khỏi UI |

> Đây là một **feature gate theo mặt hàng**, không phải field optional. Khi tạo listing gạo/điều, UI không được hiện bước "Khai vùng trồng" rồi cho bỏ qua — phải không có bước đó.

## 3.2 Trạng thái hợp đồng (`Contract.status`)

| Enum | Nhãn | Màu | Nghĩa |
|---|---|---|---|
| `OFFERED` | Đã gửi đề nghị | Neutral | Một bên đề xuất, chưa ai ký |
| `NEGOTIATING` | Đang đàm phán | Info | Đang chỉnh điều khoản |
| `SIGNED` | Đã ký, chờ khoá ký quỹ | Warning | **Đủ 2 chữ ký, nhưng tiền chưa khoá** |
| `ACTIVE` | Đang thực hiện | Info | Đã ký **và** cọc đã khoá thành công |
| `SETTLED` | Đã tất toán | Success | Milestone cuối đã xong |
| `CANCELLED` | Đã huỷ | Danger | Huỷ pro-rata phần milestone chưa xong |

> **Chỗ dễ sai nhất:** `SIGNED` và `ACTIVE` không giống nhau. `SIGNED` = mốc chữ ký. `ACTIVE` = mốc tiền. Nếu khoá cọc thất bại, hợp đồng **kẹt ở `SIGNED`** — UI phải có state riêng cho tình huống này (banner đỏ + nút thử lại), không được hiện như "đang chạy bình thường".

## 3.3 Trạng thái cột mốc (`Milestone.status`) — trục chính của sản phẩm

| Enum | Nhãn | Màu | Ai hành động tiếp |
|---|---|---|---|
| `CREATED` | Chưa bắt đầu | Neutral | — |
| `IN_PROGRESS` | Đang chuẩn bị hàng | Info | Seller |
| `SELLER_WEIGHED` | Đã cân, đang vận chuyển | Info | Buyer |
| `BUYER_RECEIVED` | Đã nhận, chờ xác nhận | Warning | Buyer |
| `AWAITING_SELLER_RESPONSE` | Chờ bên bán phản hồi | Warning | Seller |
| `CONTESTED` | Đang tranh chấp | Danger | Admin / Inspector |
| `FORCE_MAJEURE_PENDING_REVIEW` | Chờ duyệt bất khả kháng | Warning | Admin |
| `SETTLED` | Đã tất toán | Success | — |
| `CANCELLED_WITH_PENALTY` | Đã huỷ, có phạt | Danger | — |

Đường đi bình thường: `CREATED → IN_PROGRESS → SELLER_WEIGHED → BUYER_RECEIVED → SETTLED`

Các nhánh rẽ:
- Ở `BUYER_RECEIVED`, buyer có 2 lựa chọn + 1 timeout:
  - **Xác nhận đúng** (`CONFIRM_CLEAN`) → `SETTLED` ngay, giải ngân theo Delta 2.
  - **Báo vấn đề** (`FLAG_ISSUE`) → `AWAITING_SELLER_RESPONSE`.
  - Im lặng quá `buyerConfirmWindowDays` (mặc định **2 ngày làm việc**) → hệ thống tự xử như xác nhận đúng.
- Ở `AWAITING_SELLER_RESPONSE`, seller im lặng quá `sellerResponseWindowDays` (2 ngày làm việc) → `SETTLED` theo số buyer báo. Seller phản đối → `CONTESTED`.
- **Bất khả kháng chen ngang được ở bất kỳ đâu** trước `SETTLED` — không gắn cứng vào một bước. UI phải để nút "Báo bất khả kháng" khả dụng cho seller xuyên suốt vòng đời milestone.
- `IN_PROGRESS` quá `expectedDeliveryDate + graceDays` → buyer trigger được nhánh seller quá hạn.
- `SELLER_WEIGHED` quá `buyerReceiveWindowDays` → **thông báo buyer, chưa đổi state**. Buyer vẫn im → đẩy sang Admin/Level 1, **không** tự settle theo số seller tự khai.

## 3.4 Trạng thái ký quỹ

**Cấp cột mốc — `EscrowMilestone.status`:**

| Enum | Nhãn | Màu |
|---|---|---|
| `LOCKED` | Đang khoá | Info |
| `PROVISIONALLY_RELEASED` | Đã giải ngân tạm | Warning |
| `RELEASED` | Đã giải ngân | Success |
| `PENALIZED` | Đã trừ phạt | Danger |
| `SETTLED` | Đã tất toán | Success |

**Cấp hợp đồng — 2 field độc lập:** `buyerDepositState` và `sellerDepositState`, mỗi field có 3 giá trị:

| Enum | Nhãn | Màu |
|---|---|---|
| `DEPOSIT_LOCKED` | Đang khoá | Info |
| `DEPOSIT_RELEASED` | Đã hoàn trả | Success |
| `DEPOSIT_SEIZED` | Đã tịch thu | Danger |

> `sellerDepositRate` là **tuỳ chọn** (chỉ tồn tại khi `> 0`). Nếu bằng 0, UI **ẩn hẳn** khối "ký quỹ bên bán", không hiện `0 ₫`.

## 3.5 Loại bút toán sổ cái (`LedgerEntry.entryType`)

| Enum | Nhãn | Dấu | Màu |
|---|---|---|---|
| `LOCK_BUYER_DEPOSIT` | Khoá cọc bên mua | − | Info |
| `LOCK_SELLER_DEPOSIT` | Khoá cọc bên bán | − | Info |
| `LOCK_MILESTONE` | Khoá tiền cột mốc | − | Info |
| `RELEASE_TO_SELLER` | Giải ngân cho bên bán | + | Success |
| `SEIZE_PENALTY` | Trừ phạt vi phạm | − | Danger |
| `REFUND_TO_BUYER` | Hoàn trả bên mua | + | Neutral |

Mô hình: **FBO / Omnibus, append-only**. Không có tài khoản riêng cho từng hợp đồng. **Số dư luôn được tính ra từ sổ cái, không lưu sẵn.** UI không bao giờ hiện nút sửa/xoá dòng bút toán.

## 3.6 Delta 1 vs Delta 2 — không được gộp

| | Delta 1 | Delta 2 |
|---|---|---|
| So sánh | Khối lượng cam kết (lúc ký) ↔ khối lượng seller cân | Seller cân ↔ buyer cân lại |
| Bản chất | Seller kiểm soát được — xảy ra **trước khi xe chạy** | Hao hụt vận chuyển — **ngoài kiểm soát cả hai** |
| Hệ quả | Có thể bị **phạt** | **Không phạt**, chỉ tính pro-rata theo số thực nhận |
| Nhãn UI | "Chênh lệch cam kết" | "Hao hụt vận chuyển" |

UI phải hiện **hai con số riêng biệt** ở màn xác nhận nhận hàng, không gộp thành "chênh lệch". Gộp lại là làm mất nghĩa: một cái phạt, một cái không.

## 3.7 Giải quyết tranh chấp 3 tầng

| Tầng | Đơn vị | Chi phí | Ai đăng nhập | Chung thẩm? |
|---|---|---|---|---|
| Level 1 | Admin nội bộ | Rẻ nhất | Admin | Không |
| Level 1.5 | Vinacontrol, Quatest | Trung bình | Inspector | **Có**, với tranh chấp bất khả kháng |
| Level 2 | SGS, Bureau Veritas | Đắt nhất | Không ai | Có |

- Tranh chấp **bất khả kháng** dừng ở Level 1.5 — SGS không có chuyên môn xác nhận thiên tai. UI không được hiện nút "Đẩy lên Level 2" ở nhánh này.
- Khi đẩy Level 2, nếu hết `level2BufferWindowDays` (**5–10 ngày làm việc**, neo theo chuẩn lab test cà phê SCA) mà báo cáo chưa về → giải ngân tạm theo phán quyết Level 1.5, giữ lại một phần đệm (`bufferAmount`). Milestone chuyển `PROVISIONALLY_RELEASED`.
- Hết `level2BufferTerminalDays` (**30 ngày**) mà báo cáo vẫn không về → phán quyết 1.5 thành chung thẩm.
- UI ở state `PROVISIONALLY_RELEASED` phải nói rõ: **số này là tạm, còn giữ đệm, có thể điều chỉnh khi báo cáo Level 2 về.** Kèm đồng hồ đếm ngược tới `level2BufferTerminalDays`.

## 3.8 Vùng trồng & EUDR (chỉ áp cho cà phê / cao su)

| Field | Enum | Nhãn UI |
|---|---|---|
| `geometryType` | `POINT` / `POLYGON` | Điểm / Vùng |
| `source` | `KML_IMPORT` / `MANUAL_PIN` | Nhập từ file KML / Ghim thủ công |
| `verificationLevel` | `SELF_DECLARED` / `CADASTRAL_BACKED` | Tự khai / Có trích lục địa chính |
| `geoVerificationStatus` | `PENDING` / `CHECKED` / `UNVERIFIED` | Đang kiểm tra / Đã kiểm tra / Chưa xác minh được |
| `geoRiskLevel` | `LOW_RISK` / `HIGH_RISK` / `INCONCLUSIVE` | Rủi ro thấp / Rủi ro cao / Không kết luận được |
| `declaredProvince` | 34 tỉnh (NQ 202/2025/QH15) | Tỉnh/thành seller tự chọn |

**Hai field này KHÔNG được gộp trên UI.** `geoVerificationStatus` là *tiến trình kiểm tra*, `geoRiskLevel` là *kết quả*. `UNVERIFIED` ("chưa biết") khác hoàn toàn `HIGH_RISK` ("có bằng chứng rủi ro"). Hiện chung một badge là nói dối người dùng.

Ràng buộc UI: không cho chọn vùng trồng đang `PENDING` khi tạo listing — nút bị vô hiệu kèm dòng giải thích "Đang kiểm tra vệ tinh, thường mất vài giờ".

Mốc luật cần nhắc trên UI onboarding: **EUDR áp cho doanh nghiệp lớn từ 30/12/2026, doanh nghiệp nhỏ từ 30/6/2027.** Hồ sơ due diligence phải giữ tối thiểu 5 năm → UI không có nút xoá file bằng chứng.

## 3.9 Uy tín & khoá tài khoản

| `lock_entry.status` | Nhãn | Màu |
|---|---|---|
| `LOCKED` | Đang bị khoá | Danger |
| `UNLOCKED_EARLY` | Đã mở khoá sớm | Warning |
| `EXPIRED` | Đã hết hạn khoá | Neutral |

- Khoá **chỉ chặn tạo mới** (đăng bán, tạo hợp đồng, ký). Không hồi tố hợp đồng đang chạy.
- Reputation **hai chiều**: seller xem được uy tín buyer trước khi ký, không chỉ ngược lại. Có chỉ số riêng: **tỷ lệ buyer báo vấn đề rồi thua tranh chấp** — tín hiệu lạm dụng.
- Có endpoint xuất hồ sơ tín dụng (`credit-export`) theo 4 nhóm: lịch sử thanh toán đúng hạn, quy mô/tần suất giao dịch, vi phạm/nợ xấu, thâm niên hoạt động. → cần 1 nút "Xuất hồ sơ tín dụng" trên màn uy tín.

## 3.10 File

| `File.status` | Nhãn | Màu |
|---|---|---|
| `PROCESSING` | Đang xử lý | Warning |
| `READY` | Sẵn sàng | Success |
| `FAILED` | Xử lý thất bại | Danger |

`FAILED` luôn kèm `failureReason` (VD "phát hiện virus", "lỗi đọc email"). UI hiện lý do, không hiện chung chung.

Quan trọng: upload xong **không có nghĩa là dùng được ngay**. UI phải hiện trạng thái `PROCESSING` với spinner, và **khoá nút "Gửi" cho tới khi `READY`**. Đây là ràng buộc kỹ thuật thật (file phải qua quét virus / parse), không phải trang trí.

## 3.11 Cảnh báo tuân thủ

- **Ngưỡng AML: 400 triệu VND** (QĐ 11/2023, TT 27/2025). Giao dịch vượt ngưỡng bắn `bank.large_transaction_flagged` → chỉ Admin thấy, **không hiện cho người dùng cuối**.
- **Đóng băng hệ thống** (`system_lock`): khi bật, mọi thao tác tiền đều thất bại. UI hiện banner đỏ toàn cục. Admin **không có quyền tự mở** — phải có chữ ký của External Verifier. Nút mở khoá trong Admin UI phải hiện rõ điều này, không hiện nút "Mở khoá" như thao tác thường.

---

# PHẦN 4 — SITEMAP ĐẦY ĐỦ

## Công khai (không cần đăng nhập)
1. Trang chủ / landing
2. Sàn nguồn hàng (danh sách listing công khai)
3. Chi tiết listing công khai
4. Hồ sơ uy tín công khai (`/reputation/{userId}/public-summary`)
5. Bảng giá tham khảo (theo mặt hàng + tỉnh)

## Xác thực
6. Đăng nhập
7. Đăng ký doanh nghiệp — wizard 3 bước (thông tin DN → giấy tờ pháp lý → giấy uỷ quyền đại diện)
8. Chờ duyệt tài khoản (fail-closed: chưa duyệt = không dùng được)
9. Quên mật khẩu / đặt lại mật khẩu
10. Xác thực OTP khi ký hợp đồng (modal, phiên tươi ≤ **300 giây**)

## Chung cho Buyer & Seller
11. Tổng quan (dashboard)
12. Danh sách hợp đồng
13. **Chi tiết hợp đồng** ⭐ (tab: Tổng quan / Cột mốc / Ký quỹ / Kiểm định / Chứng từ / Nhật ký)
14. Tạo hợp đồng — wizard 5 bước
15. Đàm phán hợp đồng (so sánh 2 phiên bản điều khoản)
16. Ký hợp đồng (xem lại + OTP)
17. Theo dõi cột mốc (gộp mọi hợp đồng)
18. Chi tiết cột mốc
19. Sổ cái ký quỹ
19b. Huỷ hợp đồng (modal xác nhận nguy hiểm, từ Chi tiết hợp đồng)
20. Hồ sơ uy tín của tôi (+ modal Xuất hồ sơ tín dụng)
21. Trung tâm thông báo
22. Nhật ký kiểm toán (hash chain + OpenTimestamps)
23. Cài đặt tài khoản
24. Kho chứng từ

## Riêng Seller
25. Sản phẩm của tôi (listing)
26. Tạo / sửa listing
27. Sổ vùng trồng (`PlotRegistryEntry`) — bản đồ + danh sách
28. Thêm vùng trồng (nhập KML / ghim thủ công trên bản đồ)
29. Chi tiết vùng trồng (kết quả kiểm tra vệ tinh)
30. **Cân hàng & upload bằng chứng** (ưu tiên mobile)
31. Báo bất khả kháng (form + upload bằng chứng)
32. Phản hồi khi buyer báo vấn đề

## Riêng Buyer
33. Tìm nguồn hàng (bộ lọc: mặt hàng, tỉnh, uy tín, mức rủi ro EUDR)
34. Nạp ký quỹ
35. **Xác nhận nhận hàng** — cân lại + upload + Delta 1 / Delta 2 (ưu tiên mobile)
36. Báo vấn đề (`FLAG_ISSUE`)
37. Theo dõi tranh chấp

## Inspector (Level 1.5)
38. Danh sách vụ được giao
39. Chi tiết vụ + bằng chứng
40. Nộp báo cáo giám định
41. Ký báo cáo (OTP)

## Admin
42. Tổng quan vận hành
43. Duyệt tài khoản doanh nghiệp
44. Duyệt danh mục sản phẩm (`Category` + gán `commodity` bắt buộc lúc duyệt)
45. Hàng chờ duyệt bất khả kháng
46. Xử tranh chấp Level 1
47. Điều phối giám định Level 2 (commission + xác nhận case ID)
48. Duyệt báo cáo Level 2 nhận qua email
48b. Hàng đợi lỗi nhận báo cáo Level 2 (email intake failure)
49. Sổ cái toàn hệ thống
50. Cảnh báo giao dịch lớn (AML)
51. Đóng băng / mở băng hệ thống
52. Quản lý uy tín & khoá tài khoản
53. Nhập giá thủ công (cao su / điều)
54. Analytics

---

# PHẦN 5 — SPEC TỪNG MÀN

> Mỗi màn: **Mục tiêu → Bố cục → Trạng thái → Hành động → Bẫy**.
> Phần "Bẫy" là những chỗ dễ thiết kế sai vì nó đi ngược trực giác UI thông thường. Đọc kỹ.

---

## 5.1 Đăng nhập

**Mục tiêu:** vào nhanh, tạo cảm giác đáng tin.

**Bố cục:** chia đôi. Trái 55% nền xanh `#15803D`: wordmark, tagline "Hợp đồng nông nghiệp minh bạch, thanh toán an toàn qua ký quỹ", 3 điểm tin cậy (Ký quỹ tự thực thi / Kiểm định độc lập / Truy xuất nguồn gốc). Phải 45% card trắng: email doanh nghiệp, mật khẩu (nút hiện/ẩn), ghi nhớ đăng nhập, quên mật khẩu, nút chính "Đăng nhập", phân cách "hoặc", nút phụ "Đăng nhập bằng chữ ký số", link "Chưa có tài khoản? Đăng ký doanh nghiệp".

**Trạng thái:** mặc định · đang gửi (nút spinner, khoá form) · sai thông tin (banner đỏ trên form, không phải toast) · tài khoản chờ duyệt (banner vàng + link xem tiến độ) · **tài khoản đang bị khoá do vi phạm** (banner đỏ, hiện ngày mở khoá, link tới hồ sơ uy tín).

**Bẫy:** trạng thái "chờ duyệt" và "bị khoá" là hai thứ hoàn toàn khác nhau. Đừng gộp thành "Không thể đăng nhập".

---

## 5.2 Đăng ký doanh nghiệp

**Mục tiêu:** thu đủ giấy tờ để gate KYC + thẩm quyền đại diện.

**Bố cục:** stepper 3 bước.
- Bước 1 — Thông tin DN: tên, mã số thuế, loại hình (Doanh nghiệp / Hợp tác xã / Hộ kinh doanh), tỉnh, vai trò dự định (Bên mua / Bên bán / Cả hai).
- Bước 2 — Giấy tờ pháp lý: upload GPKD, CCCD người đại diện.
- Bước 3 — Giấy uỷ quyền đại diện: upload + **nhập tay ngày hết hiệu lực uỷ quyền** (`authorizationExpiresAt`) đọc từ giấy tờ thật.

**Bẫy:** ngày hết hiệu lực uỷ quyền **không được** tự sinh hay mặc định. Đây là dữ liệu pháp lý (BLDS 2015 Điều 142), người dùng phải gõ đúng theo giấy. Field bắt buộc, có tooltip giải thích tìm ở đâu trên giấy.
Sau khi nộp: fail-closed — chuyển thẳng sang màn "Chờ duyệt", không cho vào app.

**Nhánh riêng cho Inspector (Level 1.5 — Vinacontrol, Quatest):** ở Bước 1, khi chọn vai trò "Đơn vị kiểm định", Bước 3 đổi nội dung — thay "Giấy uỷ quyền đại diện" bằng **"Chứng chỉ/Giấy phép hoạt động kiểm định thương mại"** (upload, không cần ngày hết hiệu lực uỷ quyền vì đây là tổ chức, không phải cá nhân đại diện DN). Toàn bộ vẫn fail-closed như trên.

---

## 5.3 Tổng quan (Buyer)

**Mục tiêu:** một cái liếc là biết tiền đang ở đâu, việc gì cần làm.

**Bố cục:**
- 4 thẻ số: Hợp đồng đang hoạt động · Tổng giá trị ký quỹ đang khoá · Việc cần xử lý (accent vàng) · Điểm uy tín (accent xanh).
- Cột trái (rộng) — card "Cột mốc sắp đến hạn": mỗi dòng gồm mã HĐ, mặt hàng + khối lượng, tên cột mốc, hạn, badge trạng thái.
- Cột phải — card "Việc cần làm": danh sách hành động, mỗi item có nút chính nhỏ. Bên dưới card "Ký quỹ theo trạng thái": thanh ngang xếp chồng (Đang khoá / Đã giải ngân / Chờ hoàn).

**Trạng thái:** loading (skeleton, không spinner toàn trang) · rỗng ("Chưa có hợp đồng nào. Bắt đầu bằng cách tìm nguồn hàng." + nút) · có cảnh báo đóng băng hệ thống (banner đỏ trên cùng, đè lên mọi thứ).

---

## 5.4 Tổng quan (Seller)

Khác Buyer ở nội dung:
- 4 thẻ: Hợp đồng đang hoạt động · **Tiền sắp được giải ngân** · Việc cần xử lý · Điểm uy tín.
- Card "Cột mốc cần giao": mỗi dòng có nút "Cân hàng" nổi bật.
- Card "Vùng trồng" (chỉ hiện nếu seller có mặt hàng cà phê/cao su): số vùng đã khai, số vùng đang chờ kiểm tra vệ tinh, số vùng rủi ro cao.
- Nếu tài khoản đang bị khoá: banner đỏ toàn trang, đếm ngược ngày mở khoá, mọi nút "tạo mới" bị vô hiệu kèm tooltip lý do.

---

## 5.5 Danh sách hợp đồng

**Bố cục:** tiêu đề + nút "Tạo hợp đồng". Thanh lọc: ô tìm kiếm, dropdown mặt hàng, dropdown trạng thái, khoảng ngày, segmented control "Tất cả | Bên mua | Bên bán".

Bảng: `Mã HĐ | Đối tác | Mặt hàng | Giá trị | Ký quỹ | Tiến độ cột mốc | Trạng thái | ⋯`
- Mã HĐ dùng font mono.
- "Tiến độ cột mốc" là thanh mini + `3/5`.
- Trạng thái là badge theo bảng 3.2.

**Trạng thái:** loading skeleton rows · rỗng có lọc ("Không có hợp đồng khớp bộ lọc" + nút xoá lọc) · rỗng hoàn toàn (khác hẳn: hướng dẫn tạo cái đầu tiên).

**Mobile:** bảng → danh sách card, mỗi card 2 dòng: `mã + badge` / `đối tác + giá trị`.

---

## 5.6 Chi tiết hợp đồng ⭐

Màn quan trọng nhất. Đây là nơi hội đồng bảo vệ sẽ nhìn lâu nhất.

**Bố cục:**
- Header: mã HĐ (mono) + badge trạng thái, dòng tóm tắt mặt hàng/khối lượng/địa điểm, **một** nút hành động chính, nội dung đổi theo state (Ký hợp đồng / Nạp ký quỹ / Cân hàng / Xác nhận nhận hàng / Xem phán quyết).
- Hai card thông tin bên mua & bên bán, mỗi bên kèm chip điểm uy tín (bấm vào mở hồ sơ công khai).
- Tabs: **Tổng quan · Cột mốc · Ký quỹ · Kiểm định · Chứng từ · Nhật ký**.

**Tab Tổng quan:**
- Cột trái — **timeline cột mốc dọc**. Mỗi node: tên, ngày, trạng thái, **và số tiền giải ngân tại cột mốc đó**. Đã xong = tick xanh; đang chạy = vòng xanh; chưa tới = xám.
- Cột phải — card "Tình trạng ký quỹ": số đang khoá (cỡ lớn), thanh tiến trình, bảng chi tiết (cọc bên mua / cọc bên bán nếu có / đã giải ngân), và dòng khoá: "Sổ cái chỉ ghi thêm, không thể sửa".
- Bên dưới — card "Điều khoản chính": dung sai khối lượng, tiêu chuẩn chất lượng, ngày ân hạn, mức phạt vi phạm.

**Bẫy #1 — không tách timeline và tiền ra hai màn.** Cột mốc *chính là* trigger giải ngân. Tách ra là làm mất luận điểm trung tâm của sản phẩm.

**Bẫy #2 — state `SIGNED` nhưng chưa `ACTIVE`.** Phải có biến thể riêng: banner vàng "Hợp đồng đã ký nhưng chưa khoá được ký quỹ" + nút "Thử khoá lại". Đừng hiện timeline như đang chạy bình thường.

**Bẫy #3 — chỉ có một nút hành động chính.** Nhiều nút chính = người dùng không biết bước tiếp theo là gì.

**Nút trigger khi cột mốc quá hạn (dựa vào `expectedDeliveryDate + graceDays`):**
- Milestone kẹt ở `IN_PROGRESS` quá hạn giao → hiện cho **buyer** nút phụ màu vàng "Báo cáo bên bán trễ hạn" ngay trên node cột mốc đó trong timeline. Bấm vào mở modal xác nhận, kèm dòng nhắc "Bên bán vẫn còn quyền báo bất khả kháng."
- Milestone kẹt ở `SELLER_WEIGHED` quá `buyerReceiveWindowDays` mà buyer chưa cân lại → hiện nút phụ "Đẩy tranh chấp lên quản trị viên" (không phải nút chính, đặt cạnh trạng thái, không nổi bật bằng nút hành động chính) — vì đây là Admin/Level 1 xử theo bằng chứng hiện có, không phải hành động buyer tự thắng.

---

## 5.6b Đàm phán hợp đồng

**Mục tiêu:** cho hai bên chỉnh điều khoản trước khi ký, trong khi backend chặn cứng các dải guardrail để bên mạnh hơn (thường là buyer) không ép bên yếu về mức bất lợi tuyệt đối.

**Bố cục:** split-view hai cột — "Đề xuất của bạn" / "Đề xuất của đối tác", đồng bộ cuộn. Mỗi điều khoản khác nhau giữa hai phiên bản được **bôi vàng nhạt** cả dòng, kèm icon mũi tên chỉ chiều thay đổi (tăng/giảm). Phía dưới mỗi cột là ô nhập trực tiếp cho các field negotiate được:

| Field | Nhãn | Dải hợp lệ | Hành vi khi vượt dải |
|---|---|---|---|
| `toleranceRate` | Ngưỡng hao hụt vận chuyển chấp nhận | 0% – 10% | Input tự chặn, không cho gõ ngoài dải; hiện dòng đỏ nhỏ dưới field |
| `shortfallPenaltyThreshold` | Ngưỡng thiếu hàng bắt đầu tính phạt | 3% – 15% | Cùng cơ chế |
| `buyerPenaltyRate` / `sellerPenaltyRate` | Mức phạt vi phạm | 0% – 30% | Cùng cơ chế |
| `sellerDepositRate` | Tỷ lệ cọc bên bán | Tuỳ chọn, mặc định 0% | Không có dải chặn — 0% là giá trị hợp lệ, không đánh dấu lỗi |

Mỗi input có slider kèm số, hai đầu slider khoá cứng theo dải — không thể kéo ra ngoài. Tooltip cạnh mỗi field giải thích một câu vì sao có giới hạn này, ví dụ: "Giới hạn để bên mua không thể ép về 0%, khiến bên bán chịu phạt từ gram thiếu đầu tiên."

Nút "Gửi đề xuất" (đẩy bản nháp cho đối tác) và "Chấp nhận đề xuất của đối tác" (chuyển sang màn Ký hợp đồng).

**Trạng thái:** đang chờ đối tác phản hồi (banner vàng nhạt, khoá input, chỉ xem) · đối tác vừa sửa (đẩy noti + tự bôi vàng phần mới đổi) · đã đồng thuận cả hai bên (nút "Ký hợp đồng" sáng lên).

**Bẫy:** input không được **cảnh báo rồi vẫn cho gửi** — phải chặn cứng tại chỗ (disable nút gửi + không cho gõ số ngoài dải), vì đây là guardrail chống ép giá, không phải gợi ý.

> **Ghi chú riêng:** có thông tin nhắc tới một field `disputeFloorReleaseRate` (dải đề xuất 50%–85%, sàn giải ngân tối thiểu khi có tranh chấp) — **chưa xác nhận được field này trong bộ tài liệu thiết kế hiện có.** Nếu team xác nhận field này tồn tại, bổ sung thêm 1 dòng vào bảng trên theo đúng dải đã chốt; nếu không, bỏ qua để tránh đưa số chưa kiểm chứng vào UI.

---

## 5.6c Huỷ hợp đồng (Contract-level Cancel)

**Mục tiêu:** đây là hành động hậu quả nặng nhất mà một bên tự tay bấm — huỷ **pro-rata phần chưa giao**, không phải huỷ toàn bộ, và luôn kèm phạt.

**Nơi xuất hiện:** nút "Huỷ hợp đồng" (ghost, chữ đỏ, không nổi bật) nằm cuối màn Chi tiết hợp đồng, tách xa khỏi nút hành động chính.

**Bố cục modal xác nhận nguy hiểm (2 bước):**

*Bước 1 — Xem hậu quả (chỉ đọc, không có ô nhập):*
```
Huỷ hợp đồng HĐ-2026-0142

Bạn sẽ:
  • Bị tịch thu cọc:            500.000.000 ₫
  • Ghi nhận nợ phạt:           320.000.000 ₫
  • Bị khoá tài khoản:          45 ngày (đến 24/08/2026)

  ⚠ Đây là hợp đồng chưa có cột mốc nào hoàn thành — mức phạt
    bị nhân thêm hệ số 1,5 lần so với huỷ giữa chừng thông thường.
```
Số `lockDurationDays` và nợ phạt phải được **tính và hiển thị thật** trước khi cho qua bước 2 — không hiện placeholder rồi tính sau.

*Bước 2 — Xác nhận:* ô nhập bắt gõ lại đúng cụm `HUỶ HĐ-2026-0142`, nút "Xác nhận huỷ" chỉ sáng khi gõ khớp.

**Bẫy:** đừng gộp 2 bước thành 1 modal — tách riêng để người dùng buộc phải đọc hậu quả trước khi có cơ hội gõ xác nhận. Không dùng nút "Huỷ" màu đỏ đặc ở bước 1 (chưa phải lúc xác nhận), chỉ bước 2 mới dùng nút đỏ đặc.

**Biến thể:** nếu là bên **bị** huỷ (không phải bên chủ động bấm), màn hiện thông báo khác — không có modal xác nhận, chỉ có banner kết quả: "Đối tác đã huỷ hợp đồng. Bạn nhận lại: [số tiền]."

---

## 5.7 Tạo hợp đồng (wizard 5 bước)

Stepper: `1. Thông tin chung → 2. Mặt hàng & khối lượng → 3. Cột mốc & giao hàng → 4. Điều khoản ký quỹ → 5. Xem lại & ký`

- **Bước 2:** mặt hàng, khối lượng, đơn giá (kèm link "Giá tham khảo hôm nay: 118.500 ₫/kg — VNSAT"), tổng giá trị (tự tính, chỉ đọc, nổi bật), tiêu chuẩn chất lượng, dung sai khối lượng (± % và kg).
- **Bước 3:** danh sách cột mốc lặp lại được. Mỗi cột mốc: tên, khối lượng đợt, **ngày giao dự kiến**, **số ngày ân hạn**, địa điểm giao. Tổng khối lượng các cột mốc phải khớp bước 2 — validate ngay, hiện thanh "Đã phân bổ 18/20 tấn".
- **Bước 4:** tỷ lệ cọc bên mua (bắt buộc, > 0), **tỷ lệ cọc bên bán (tuỳ chọn, mặc định 0 — kèm dòng giải thích rằng để 0 sẽ giảm gánh nặng vốn cho bên bán)**, mức phạt vi phạm, cửa sổ báo bất khả kháng.
- **Bước 5:** xem lại toàn văn + checkbox xác nhận + nút "Ký hợp đồng" → mở modal OTP.

Sidebar phải: card tóm tắt dính (sticky) cập nhật trực tiếp.

**Bẫy:** `sellerDepositRate = 0` là **mặc định hợp lệ và được khuyến khích**, không phải "chưa điền". Đừng đánh dấu đỏ.

---

## 5.8 Ký hợp đồng + OTP

Modal. Xem lại hash nội dung hợp đồng (mono, rút gọn `a3f9...c21b`, có nút sao chép), ô nhập 6 số OTP, đếm ngược **05:00**, link gửi lại.

Sau khi hết giờ: OTP vô hiệu, phải khởi tạo lại. Không cho gõ tiếp.

Trạng thái đặc biệt: **ký xong nhưng khoá cọc thất bại**. Modal chuyển sang màn kết quả: "Đã ghi nhận chữ ký. Chưa khoá được ký quỹ." + nút "Thử lại". Vì `contract.signed` đã bắn — chữ ký là sự thật kể cả khi tiền chưa khoá.

---

## 5.9 Cân hàng & upload bằng chứng (Seller, mobile-first)

**Mục tiêu:** một tay, ngoài kho, mạng chậm.

**Bố cục dọc:** tên cột mốc + mã HĐ → ô nhập khối lượng cân được (bàn phím số, đơn vị kg cố định bên phải) → **so sánh trực tiếp với khối lượng cam kết**, hiện Delta 1 ngay: nếu thiếu, chip vàng "Thiếu 320 kg so với cam kết — có thể bị tính phạt" kèm link "Báo bất khả kháng" → khu vực upload ảnh (chụp trực tiếp, tối thiểu 1 ảnh cân) → nút chính "Xác nhận đã cân".

**Trạng thái upload:** ảnh hiện thumbnail + trạng thái `PROCESSING` (spinner nhỏ) → `READY` (tick) → `FAILED` (đỏ + lý do + nút thử lại). **Nút "Xác nhận đã cân" bị khoá cho tới khi mọi ảnh `READY`.**

**Bẫy:** đừng để nút gửi khả dụng ngay sau khi ảnh được chọn. File chưa quét virus xong thì chưa phải bằng chứng hợp lệ.

---

## 5.10 Xác nhận nhận hàng (Buyer)

**Bố cục:** khối lượng seller khai (chỉ đọc) → ô nhập khối lượng buyer cân lại → upload ảnh cân của buyer → **khối so sánh hai delta, tách bạch:**

```
Chênh lệch cam kết (Delta 1)    20.000 kg → 19.680 kg    −320 kg   Có thể tính phạt
Hao hụt vận chuyển (Delta 2)    19.680 kg → 19.550 kg    −130 kg   Không tính phạt
```

Bên dưới: số tiền sẽ giải ngân, tính pro-rata, hiện công thức rút gọn.

Ba hành động: **Xác nhận đúng** (nút chính xanh) · **Báo vấn đề** (nút viền đỏ) · liên kết phụ "Tính lại".

Kèm dòng nhắc: "Nếu không thao tác trong 2 ngày làm việc, hệ thống tự xác nhận theo số đã ghi nhận." — đây là timeout thật, phải nói trước, không âm thầm.

**Bẫy:** hai delta phải là **hai dòng riêng, hai nhãn riêng, hai hệ quả riêng**. Gộp lại thành "chênh lệch tổng −450 kg" là sai bản chất thiết kế.

---

## 5.11 Báo bất khả kháng (Seller)

Form: loại sự kiện (thiên tai / dịch bệnh / khác) · ngày xảy ra · **ngày seller biết về sự kiện** (đây mới là mốc tính cửa sổ, không phải ngày giao hàng) · mô tả · upload bằng chứng (xác nhận của chính quyền địa phương, ảnh, tin tức).

Trên form hiện rõ đồng hồ: "Còn 3 ngày trong cửa sổ báo bất khả kháng".

Sau khi nộp: milestone → `FORCE_MAJEURE_PENDING_REVIEW`. Màn theo dõi hiện: chờ Admin duyệt → nếu Admin từ chối, seller có nút "Khiếu nại lên Level 1.5". Nếu Admin duyệt, buyer có nút "Khiếu nại lên Level 1.5". **Cả hai nhánh đều không có nút Level 2.**

---

## 5.12 Theo dõi cột mốc (gộp mọi hợp đồng)

Segmented: `Sắp đến hạn | Chờ xác nhận | Đã hoàn thành | Quá hạn`

Danh sách card nhóm theo ngày. Mỗi card: mã HĐ + mặt hàng · tên cột mốc · dòng trạng thái ngắn · **số tiền giải ngân khi hoàn thành** · pill hạn ("Còn 2 ngày" vàng / "Quá hạn 1 ngày" đỏ) · nút hành động theo ngữ cảnh.

Ở tab **Quá hạn**, nút hành động theo ngữ cảnh cụ thể là: "Báo cáo bên bán trễ hạn" (milestone kẹt `IN_PROGRESS`, buyer bấm) hoặc "Đẩy tranh chấp lên quản trị viên" (milestone kẹt `SELLER_WEIGHED` quá `buyerReceiveWindowDays`) — cùng logic với node cột mốc trong Chi tiết hợp đồng (§5.6), chỉ khác là gộp nhìn theo tất cả hợp đồng.

Rail phải: lịch tháng, chấm màu ở ngày có cột mốc.

---

## 5.13 Sổ cái ký quỹ

**Mục tiêu:** trông như sổ cái ngân hàng. Đây là màn bán được sự tin tưởng.

- 3 thẻ số dư: Tổng đang khoá · Đã giải ngân · Chờ hoàn về bên mua.
- Hai khối tách bạch: "Ký quỹ bên mua" và "Ký quỹ bên bán" (ẩn nếu rate = 0), mỗi khối màu tag riêng.
- Bảng bút toán: `Thời gian | Mã bút toán | Hợp đồng | Loại | Bên | Số tiền | Số dư sau | Trạng thái`
  - Mã bút toán font mono, có icon khoá nhỏ.
  - Số tiền: `+` xanh / `−` đỏ, tabular-nums, căn phải.
  - Không có cột thao tác. Không có nút sửa/xoá. Không có menu `⋯`.
- Banner nhạt phía trên bảng: "Sổ cái chỉ ghi thêm — không thể sửa hoặc xoá bút toán."
- Nút "Xuất CSV" và "Đối chiếu số dư" (số dư luôn tính lại từ bút toán, hiện dấu tick khi khớp).

---

## 5.14 Kiểm định

Hai card cạnh nhau:

**Card A — Yêu cầu kiểm định:** đơn vị kiểm định (dropdown), loại (Lấy mẫu tại kho / Kiểm định phòng lab), ngày, ghi chú, nút "Gửi yêu cầu".

**Card B — Kết quả kiểm định:** header có tên đơn vị + badge "Đã xác thực chữ ký" (xanh, icon khiên) · kết quả lớn `ĐẠT` xanh hoặc `KHÔNG ĐẠT` đỏ · bảng chỉ tiêu:

| Chỉ tiêu | Đo được | Tiêu chuẩn | |
|---|---|---|---|
| Độ ẩm | 12,5% | ≤ 13% | Đạt |
| Tạp chất | 0,5% | ≤ 1% | Đạt |
| Hạt lỗi | 2% | ≤ 5% | Đạt |

Footer: nút "Chấp nhận kết quả" (xanh) và "Khiếu nại kết quả" (viền đỏ), kèm dòng nhắc: "Chấp nhận sẽ giải ngân cột mốc liên quan."

**Biến thể Level 2 đang chờ:** card riêng, nền vàng nhạt, "Đang chờ báo cáo từ SGS. Đã giải ngân tạm 2.100.000.000 ₫ theo phán quyết Vinacontrol. Còn giữ đệm 240.000.000 ₫." + đồng hồ đếm ngược tới hạn chót 30 ngày + dòng "Số tiền có thể được điều chỉnh khi báo cáo về."

---

## 5.15 Hồ sơ uy tín

Header: tên đơn vị, loại hình, tỉnh, **đồng hồ điểm lớn** `88 / 100` + nhãn "Uy tín tốt".

Hàng số liệu: Hợp đồng hoàn thành · Tỷ lệ đúng hạn · Số lần vi phạm · Thành viên từ.

Với **buyer** hiện thêm chỉ số riêng: **"Tỷ lệ báo vấn đề rồi thua tranh chấp"** — tín hiệu chống lạm dụng, seller cần thấy trước khi ký.

Hai card dưới: biểu đồ đường điểm uy tín 24 tháng · timeline sự kiện (mỗi sự kiện có delta màu: `+2`, `−5`).

Ghi chú nhỏ: "Vi phạm được tính trong cửa sổ 24 tháng."

Nút: "Xuất hồ sơ tín dụng" (4 nhóm chỉ số, dùng để nộp ngân hàng).

**Bẫy:** không gamify. Không huy hiệu, không cấp bậc Vàng/Bạc, không icon vui. Đây là dữ liệu ảnh hưởng tới khả năng vay vốn của người ta.

---

## 5.16 Sổ vùng trồng (Seller — chỉ cà phê / cao su)

Nửa trái: bản đồ với polygon vùng trồng, tô màu theo `geoRiskLevel`. Nửa phải: danh sách vùng.

Mỗi vùng hiện **hai badge tách biệt**:
- Badge tiến trình: `Đang kiểm tra` / `Đã kiểm tra` / `Chưa xác minh được`
- Badge kết quả (chỉ hiện khi đã kiểm tra): `Rủi ro thấp` / `Rủi ro cao` / `Không kết luận được`

Thêm chip: `Tự khai` hoặc `Có trích lục địa chính`.

Nút: "Nhập từ file KML" và "Ghim thủ công trên bản đồ".

**Khiếu nại bằng ảnh chụp thực địa (chỉ hiện khi `geoRiskLevel = HIGH_RISK` hoặc `INCONCLUSIVE`):** nút "Khiếu nại bằng ảnh chụp" trên từng vùng bị gắn cờ. Mở thẳng **camera thiết bị** — không cho chọn ảnh có sẵn từ thư viện, vì hệ thống đối chiếu toạ độ GPS lấy từ EXIF của ảnh với polygon đã khai (point-in-polygon); cho chọn ảnh cũ/ảnh tải từ nơi khác sẽ vô hiệu hoá mục đích chống gian lận của bước này. Sau khi chụp, hiện màn xem lại ảnh + toạ độ EXIF trích được (đọc được thì hiện số, đọc không được thì báo lỗi "Ảnh không có dữ liệu vị trí, chụp lại"), rồi gửi. Kết quả: badge rủi ro **không bị xoá**, chỉ thêm dòng "Đã bổ sung bằng chứng đối chiếu thực địa — buyer tự xem và quyết định."

**Bẫy:** vùng đang `PENDING` không được chọn khi tạo listing → trong list, checkbox bị vô hiệu + dòng phụ "Đang kiểm tra vệ tinh, thường mất vài giờ". Không ẩn nó đi — người dùng cần biết nó tồn tại.

---

## 5.17 Tạo listing (Seller)

Bước: Thông tin sản phẩm → Danh mục → Giá & khối lượng → **[Vùng trồng]** → Xem lại.

Bước "Vùng trồng" **chỉ tồn tại khi danh mục thuộc cà phê hoặc cao su.** Với gạo/điều, wizard chỉ có 4 bước. Không hiện bước rồi cho bỏ qua.

**Riêng cho Gạo (bước "Thông tin sản phẩm"):** thêm text input "Tên giống lúa cụ thể" (VD OM 18, ST25, IR 50404), không bắt buộc chọn từ danh sách cứng — field tự do để hệ thống pricing khớp đúng giá tham khảo theo giống (cùng tỉnh, cùng ngày nhưng khác giống thì giá lệch nhau đáng kể). Không hiện field này cho cà phê/cao su/điều.

---

## 5.18 Nhật ký kiểm toán

Danh sách sự kiện, mỗi dòng: thời gian, loại sự kiện, actor, **hash (mono, rút gọn)**, và trạng thái neo thời gian OpenTimestamps (`Đã neo` xanh / `Đang chờ neo` xám).

Nút "Kiểm tra tính toàn vẹn chuỗi" → chạy verify, hiện kết quả: chuỗi liền mạch (tick xanh) hoặc phát hiện đứt gãy (banner đỏ, chỉ rõ vị trí).

Hiển thị đây là **chuỗi hash kép** + neo vào Bitcoin qua OpenTimestamps. Có link "Xem chứng cứ neo thời gian".

---

## 5.19 Admin — Duyệt bất khả kháng

Bảng hàng chờ. Mở 1 vụ → panel: thông tin milestone, số liệu Delta 1, bằng chứng seller nộp (gallery ảnh + file), ô ghi lý do quyết định (**bắt buộc**), hai nút "Chấp thuận" / "Từ chối".

Sau quyết định, hiện rõ hệ quả cho cả hai bên: "Seller được miễn phạt cho phần thiếu 320 kg. Buyer có quyền khiếu nại lên Level 1.5 trong 3 ngày."

---

## 5.20 Admin — Điều phối giám định Level 2

Ba tab: `Đã gửi yêu cầu` (`REQUESTED`) · `Đã xác nhận mã vụ` (`CASE_ID_CONFIRMED`) · `Báo cáo chờ duyệt` (`PENDING_REVIEW`).

Ở tab 1, khi email phản hồi về từ đơn vị: hiện email gốc + trường "Mã vụ (case ID)" để Admin **gõ tay xác nhận** — không tự động điền.

Ở tab 3, báo cáo nhận qua email: hiện file, tín hiệu SPF/DKIM (badge "Email đã xác thực" hoặc "Không xác thực được nguồn"), dropdown ghép với commission, nút "Xác nhận" / "Từ chối".

**Bẫy:** không auto-match mã vụ. Email thật chứa nhiều con số (báo giá, PO, ngày tháng). Đoán sai → gắn bằng chứng nhầm hợp đồng, chỉ lộ ra đúng lúc tranh chấp cần dùng. UI phải bắt Admin xác nhận thủ công 1 click.

---

## 5.21 Admin — Đóng băng hệ thống (Zero-Trust Kill Switch)

**Sửa quan trọng so với bản trước:** đây **không phải** cơ chế Admin tự khoá + chỉ External Verifier mới mở. Cả hai chiều — **đóng băng** lẫn **mở băng** — đều bắt buộc chữ ký bất đối xứng từ External Verifier, đối xứng hoàn toàn. Admin **không có bất kỳ nút nào tự thực thi thay đổi trạng thái**, kể cả khoá. Lý do: nếu Admin tự khoá được, Admin cũng có thể tự dựng "khủng hoảng giả" rồi lợi dụng lúc hệ thống đóng băng để làm việc khác — cơ chế phải đối xứng ở cả hai đầu mới kín.

**Bố cục:**
- Card trạng thái: Bình thường / **Đang đóng băng** (đỏ, dính đầu trang) — thời điểm, `verifierOrgId` đã ký lệnh, lý do đính kèm trong lệnh ký.
- Không có nút "Đóng băng hệ thống" hay "Mở khoá" nào để Admin tự bấm. Thay vào đó là **một form duy nhất, dùng chung cho cả hai chiều**: "Dán lệnh đã ký từ bên xác minh độc lập" — ô dán payload JSON + chữ ký, nút "Xác minh chữ ký và thực thi". Hệ thống tự đọc trong payload đây là lệnh khoá hay mở, hiện preview trước khi Admin bấm xác nhận cuối.
- Khối giải thích cố định trên màn (không đổi theo trạng thái): "Nền tảng chỉ xác minh chữ ký, không tự tạo ra lệnh khoá hoặc mở khoá. Cả hai thao tác đều phải đến từ bên xác minh độc lập, ngoài tầm kiểm soát của quản trị viên."
- Bên dưới: nút phụ (không phải hành động thay đổi state) **"Tự tra cứu hash đối soát"** — gọi endpoint chỉ-đọc để Admin/bất kỳ ai cũng xem được hash hiện tại, dùng để đối chiếu độc lập.
- Lịch sử: bảng các lần khoá/mở trước đó, mỗi dòng có `verifierOrgId`, thời điểm, kết quả xác minh chữ ký (`Hợp lệ` / `Từ chối — chữ ký sai`).

**Bẫy:** đừng thiết kế đây như một toggle, kể cả một chiều. Admin chính là bên mà cơ chế này đang phòng — không có ngoại lệ nào cho Admin, kể cả trường hợp đã chứng minh là báo động giả.

---

## 5.22 Admin — Cảnh báo giao dịch lớn (AML)

Bảng giao dịch vượt **400 triệu VND**. Cột: thời gian, hợp đồng, bên, số tiền, trạng thái xử lý (`Chờ xem xét` / `Đã báo cáo` / `Đã loại trừ`).

Chỉ Admin thấy màn này. Không có bất kỳ dấu hiệu nào lộ ra cho buyer/seller rằng giao dịch của họ bị gắn cờ.

---

## 5.23 Bảng giá tham khảo

Chọn mặt hàng + tỉnh → biểu đồ đường giá 30/90/365 ngày + bảng giá theo ngày.

Mỗi dòng có badge nguồn: `VNSAT` (tự động thu thập) hoặc `Nhập tay` (admin nhập, dùng cho cao su & điều).

Với gạo, thêm bộ lọc **giống lúa** (OM 18, ST25, IR 50404...) — cùng tỉnh cùng ngày mà khác giống thì giá khác hẳn.

Dòng chú thích: "Giá tham khảo, không phải giá giao dịch trên nền tảng."

---

## 5.24 Nhập giá thủ công (Admin — cao su / điều)

**Mục tiêu:** `pricing-service` tự cào giá cà phê/gạo từ VNSAT, nhưng cao su/điều không cào tự động (lệch tỷ giá/đơn vị quốc tế) — Admin phải nhập tay mỗi ngày.

**Bố cục:** form đơn giản, không phải wizard. Dropdown "Mặt hàng" (chỉ hiện Cao su / Điều — không hiện cà phê/gạo ở đây), ô nhập "Giá" (đơn vị cố định ₫/kg), date picker "Ngày áp dụng". **Không có ô chọn tỉnh** — giá nhập tay là giá chung toàn quốc (`province = NULL`), khác hẳn giá VNSAT vốn theo tỉnh.

Bên dưới form: bảng lịch sử giá đã nhập tay, cột `Mặt hàng | Giá | Ngày áp dụng | Người nhập`, mọi dòng đều có badge nguồn `Nhập tay`.

**Bẫy:** đừng thêm dropdown tỉnh "cho giống với màn giá VNSAT" — đây là hai luồng dữ liệu có ý nghĩa khác nhau, thêm tỉnh vào đây là bịa dữ liệu không tồn tại.

---

## 5.25 Xuất hồ sơ tín dụng (Modal, đi kèm màn 5.15)

**Mục tiêu:** seller tự xuất hồ sơ để nộp ngân hàng/VARI, nhưng có cổng chặn chống tạo hợp đồng giả làm đẹp hồ sơ.

**Bố cục:** modal 2 bước.

*Bước 1 — Xin phép (consent):* giải thích ngắn dữ liệu nào sẽ được xuất (4 nhóm: lịch sử thanh toán đúng hạn, quy mô/tần suất giao dịch, vi phạm/nợ xấu, thâm niên hoạt động), checkbox "Tôi đồng ý xuất hồ sơ này", nút "Tiếp tục".

*Bước 2 — Kết quả:*
- **Nếu đủ điều kiện:** hiện file JSON/PDF sẵn sàng tải, nút "Tải hồ sơ".
- **Nếu bị chặn bởi gate đa dạng đối tác:** thay bằng error state — icon cảnh báo, tiêu đề "Hồ sơ chưa đủ điều kiện xuất", mô tả: `Hồ sơ chưa đủ điều kiện xuất do thiếu đa dạng đối tác giao dịch (chỉ giao dịch với 2 đối tác). Mở rộng giao dịch với nhiều đối tác hơn để tích luỹ hồ sơ.` — số `2` lấy động theo dữ liệu thật của seller đó. Không có nút "Thử lại" ngay (vì không có gì để thử lại tức thì) — chỉ có nút đóng modal.

**Bẫy:** đừng hiện lý do chặn chung chung kiểu "Không đủ điều kiện". Phải nói rõ nguyên nhân (đa dạng đối tác) và có con số cụ thể, vì đây là tín hiệu hành vi seller cần hiểu để cải thiện, không phải lỗi hệ thống.

---

## 5.26 Admin — Hàng đợi lỗi nhận báo cáo Level 2

**Mục tiêu:** SGS/Bureau Veritas gửi PDF qua email `intake@`, không đăng nhập hệ thống. Nếu file sai định dạng hoặc vượt 20MB, nó rơi vào hàng đợi lỗi (`email_intake_failure`) — không có màn này thì file lỗi biến mất, hợp đồng liên quan kẹt vĩnh viễn.

**Bố cục:** bảng hàng chờ, cột: `Thời gian nhận | Địa chỉ email gửi | Lý do lỗi | Hợp đồng nghi liên quan (nếu match được) | Trạng thái xử lý`.

Lý do lỗi hiển thị nguyên văn kỹ thuật nhưng dịch sang câu dễ hiểu: "Tệp vượt quá 20MB", "Không tìm thấy tệp đính kèm trong email", "Không đọc được định dạng tệp".

Mở 1 dòng → panel: xem lại toàn bộ email gốc (subject, thời gian, người gửi), nút "Sao chép email liên hệ" (để Admin chủ động gọi/email yêu cầu gửi lại), nút "Đánh dấu đã xử lý".

**Bẫy:** đây phải là màn Admin **chủ động thấy được**, không chỉ nằm trong log kỹ thuật — nếu không có UI này, lỗi nghiệp vụ thật (không phải bug) sẽ bị hiểu nhầm là "chưa ai làm" và hợp đồng liên quan kẹt mà không ai biết tại sao.

---

## 5.27 Analytics — Tổng quan vận hành (Admin & Hiệp hội)

**Mục tiêu:** 3 báo cáo B2B đinh cho Admin và các hiệp hội ngành (VICOFA, VRA...).

**Bố cục:** dòng chú thích cố định đầu trang: **"Dữ liệu được tổng hợp định kỳ vào 1:00 sáng hàng ngày."** — vì backend chạy batch, không query real-time. Bộ lọc: khoảng thời gian, mặt hàng.

Ba khối biểu đồ:
1. **Tỷ lệ bẻ kèo theo ngành hàng** — biểu đồ cột nhóm theo mặt hàng (cà phê/gạo/cao su/điều), trục % hợp đồng bị huỷ giữa chừng.
2. **Hiệu quả ký quỹ theo cột mốc** — biểu đồ đường/vùng đối chiếu "Tổng tiền đã khoá" vs "Tổng tiền thực nhận" theo tháng, minh hoạ chia nhỏ đợt giúp giải phóng vốn lưu động ra sao.
3. **Xu hướng bất khả kháng** — bản đồ nhiệt hoặc biểu đồ cột theo tỉnh + theo mùa vụ, dùng để báo cáo Bộ NN&PTNT.

**Bẫy:** đừng thêm nút "Làm mới dữ liệu" ngụ ý real-time — dữ liệu chỉ cập nhật sau 1:00 sáng hôm sau, thay đổi trong ngày hôm nay sẽ không phản ánh cho tới ngày mai. Nói rõ điều này ngay trên UI, đừng để người dùng tưởng đang xem số sống.

---

# PHẦN 6 — THƯ VIỆN COMPONENT

Dựng đủ các component dưới đây thành một trang "Component library" để tái sử dụng.

## 6.1 Điều hướng
- **Sidebar** 240px. Mục: Tổng quan · Hợp đồng · Cột mốc · Ký quỹ · Kiểm định · Sản phẩm *(seller)* · Vùng trồng *(seller, cà phê/cao su)* · Uy tín · Nhật ký · Cài đặt. Mục active có nền xanh nhạt + viền trái xanh đậm.
- **Top bar** 64px: ô tìm kiếm toàn cục, chuông thông báo (badge số đỏ), chip điểm uy tín, avatar + dropdown.
- **Bottom tab bar** (mobile) 5 mục: Tổng quan · Hợp đồng · Cột mốc · Thông báo · Tài khoản.
- **Breadcrumb**: `Hợp đồng / HĐ-2026-0142 / Cột mốc 2`.
- **Tabs** ngang, viền dưới 2px xanh cho tab active.
- **Stepper** ngang (wizard) và dọc (timeline cột mốc).

## 6.2 Hiển thị dữ liệu
- **Thẻ số (metric card)**: nhãn 13px muted trên, số 24px/500 dưới, nền `--surface-muted`, không viền.
- **Card nổi**: nền trắng, viền 0.5px, bo 12px, padding 16/20.
- **Data table**: header nền `--surface-muted`, hàng cách nhau bằng hairline, hover nhạt. Cột số căn phải, tabular-nums. Có sort, phân trang, chọn nhiều dòng.
- **Ledger table**: giống data table nhưng **không có cột thao tác**, mã bút toán mono + icon khoá.
- **Timeline dọc**: node tròn 16px + đường nối 1px. Ba biến thể node: hoàn thành (tick xanh) / đang chạy (vòng xanh rỗng) / chưa tới (chấm xám).
- **Biểu đồ**: đường (uy tín, giá), thanh xếp chồng ngang (ký quỹ theo trạng thái), donut (tỷ lệ giải ngân). Màu chỉ lấy từ bảng ngữ nghĩa.
- **Bản đồ**: polygon vùng trồng, tô theo mức rủi ro, popup khi click.
- **Key–value list**: nhãn muted trái, giá trị primary phải, hairline giữa các dòng.
- **Hiển thị hash**: mono, rút gọn `a3f9…c21b`, nút sao chép, tooltip hiện đầy đủ.
- **Hiển thị tiền**: component riêng, nhận `amount` + `sign` + `emphasis`. Tự format, tự tô màu theo dấu.

## 6.3 Trạng thái & phản hồi
- **Badge trạng thái**: pill, nền màu nhạt + chữ đậm cùng họ. 6 biến thể theo bảng màu ngữ nghĩa.
- **Pill hạn**: "Còn 2 ngày" (vàng) · "Quá hạn 1 ngày" (đỏ) · "Đúng hạn" (xanh).
- **Chip xác minh**: `Đã xác thực chữ ký` (icon khiên xanh) · `Không xác thực được nguồn` (icon cảnh báo vàng).
- **Banner** toàn trang: 4 biến thể (info / warning / danger / success). Có icon, tiêu đề, mô tả, tối đa 1 nút hành động, nút đóng nếu không phải cảnh báo bắt buộc.
- **Toast**: góc phải trên, tự tắt 5 giây, không dùng cho lỗi form.
- **Skeleton loader**: hình chữ nhật xám nhạt, dùng thay spinner cho card/table.
- **Spinner**: chỉ dùng trong nút và trong ô upload.
- **Empty state**: icon outline 40px, tiêu đề, một câu mô tả, một nút. **Phân biệt rỗng-vì-lọc và rỗng-vì-chưa-có-gì** — hai copy khác nhau.
- **Error state**: giống empty state nhưng icon đỏ + nút "Thử lại".
- **Progress bar**: mảnh 8px, bo tròn, có biến thể xếp chồng nhiều màu.
- **Đồng hồ đếm ngược**: dùng cho OTP (05:00), cửa sổ xác nhận (2 ngày làm việc), buffer Level 2 (30 ngày).

## 6.4 Nhập liệu
- Text input · Number input (có đơn vị dính bên phải: `kg`, `₫`, `%`) · Select · Multi-select · Date picker · Date range · Textarea · Checkbox · Radio · Toggle · Segmented control · Search input.
- **Money input**: tự chèn dấu phân cách khi gõ, hiện chữ đọc số bên dưới ("Năm tỷ đồng").
- **File upload**: kéo thả + chọn file. Mỗi file là một dòng có thumbnail, tên, dung lượng, và **badge trạng thái `PROCESSING`/`READY`/`FAILED`**. Nếu `FAILED`, hiện `failureReason` + nút thử lại.
- **OTP input**: 6 ô riêng, tự nhảy ô, dán được cả chuỗi.
- **Repeatable field group**: dùng cho danh sách cột mốc, có nút "Thêm cột mốc" và icon xoá từng dòng.
- Mọi field bắt buộc có dấu `*`. Lỗi hiện **dưới field**, chữ đỏ 13px, không dùng toast.

## 6.5 Hành động
- **Nút chính** (xanh đặc) — tối đa **một** nút chính mỗi màn.
- **Nút phụ** (viền, nền trắng).
- **Nút ghost** (chỉ chữ).
- **Nút nguy hiểm** (viền đỏ, chữ đỏ; nền đỏ đặc chỉ dùng khi xác nhận trong modal).
- **Icon button** — luôn có `aria-label`.
- **Modal**: 3 cỡ (sm 400 / md 560 / lg 720). Header + body + footer nút phải.
- **Modal xác nhận nguy hiểm**: bắt gõ lại từ khoá (VD gõ `ĐÓNG BĂNG`) trước khi nút bật.
- **Drawer** trượt phải — dùng cho chi tiết vùng trồng, chi tiết bút toán.
- **Dropdown menu** — dùng cho hành động phụ trên hàng bảng.
- **Tooltip** — giải thích thuật ngữ (Delta 1, buffer, EUDR). Nhấn được trên mobile.

## 6.6 Chuyên biệt domain
- **Thẻ đối tác**: avatar/logo, tên, loại hình, chip uy tín, link hồ sơ.
- **Đồng hồ uy tín**: cung tròn, số ở giữa, nhãn định tính dưới.
- **Node cột mốc**: tên + ngày + trạng thái + số tiền giải ngân.
- **Khối so sánh Delta**: hai dòng riêng, mỗi dòng có nhãn, hai số, hiệu số, và **nhãn hệ quả** (Có thể tính phạt / Không tính phạt).
- **Badge kiểm tra vệ tinh**: cặp hai badge (tiến trình + kết quả), không bao giờ gộp.
- **Chỉ số nguồn giá**: `VNSAT` / `Nhập tay`.
- **Banner đóng băng hệ thống**: đỏ, dính đỉnh trang, không đóng được, đè lên mọi nội dung.

---

# PHẦN 7 — CATALOG THÔNG BÁO

Mỗi thông báo dưới đây map 1-1 với một event thật trong hệ thống. Dựng đủ trong Trung tâm thông báo và dưới dạng toast/banner tương ứng.

| Event | Người nhận | Loại | Nội dung (tiếng Việt) | Hành động |
|---|---|---|---|---|
| `contract.signed` | Cả hai bên | Info | Hợp đồng HĐ-2026-0142 đã được ký bởi cả hai bên. | Xem hợp đồng |
| `escrow.deposit_locked` | Cả hai bên | Success | Đã khoá ký quỹ 5.000.000.000 ₫. Hợp đồng bắt đầu thực hiện. | Xem ký quỹ |
| `bank.lock_failed` | Bên phải nạp | **Danger** | Không khoá được ký quỹ. Hợp đồng đang chờ, chưa thực hiện. | Thử khoá lại |
| `milestone.seller_weighed` | Buyer | Info | Bên bán đã cân 19.680 kg cho cột mốc 2. Hàng đang vận chuyển. | Xem cột mốc |
| — *(hết `buyerReceiveWindowDays`)* | Buyer | Warning | Bạn chưa cân lại hàng cột mốc 2. Quá hạn sẽ chuyển sang xử lý bởi quản trị viên. | Xác nhận nhận hàng |
| `milestone.buyer_confirmed` | Seller | Success | Bên mua đã xác nhận nhận hàng cột mốc 2. | Xem giải ngân |
| `milestone.flagged` | Seller | **Warning** | Bên mua báo vấn đề ở cột mốc 2. Bạn có 2 ngày làm việc để phản hồi. | Phản hồi |
| — *(hết `sellerResponseWindowDays`)* | Cả hai | Info | Không có phản hồi. Cột mốc 2 được tất toán theo số bên mua ghi nhận. | Xem chi tiết |
| `milestone.settled` | Cả hai | Success | Cột mốc 2 đã tất toán. Giải ngân 2.500.000.000 ₫ cho bên bán. | Xem sổ cái |
| `milestone.force_majeure_claimed` | Admin, Buyer | Warning | Bên bán báo bất khả kháng ở cột mốc 2. Đang chờ xem xét. | Xem bằng chứng |
| `milestone.force_majeure_resolved` | Cả hai | Info | Yêu cầu bất khả kháng đã được chấp thuận. Phần thiếu 320 kg được miễn phạt. | Xem quyết định |
| `milestone.dispute_resolved` | Cả hai | Info | Tranh chấp cột mốc 2 đã có phán quyết, nghiêng về bên bán. | Xem phán quyết |
| `milestone.cancelled_with_penalty` | Cả hai | **Danger** | Cột mốc 2 bị huỷ. Trừ phạt 400.000.000 ₫. | Xem sổ cái |
| `contract.settled` | Cả hai | Success | Hợp đồng HĐ-2026-0142 đã tất toán. Hoàn trả cọc 500.000.000 ₫. | Xem hợp đồng |
| `contract.cancelled` | Cả hai | **Danger** | Hợp đồng HĐ-2026-0142 đã huỷ. Xử lý cọc theo điều khoản. | Xem chi tiết |
| `file.ready` | Người upload | *(không thông báo)* | Cập nhật trạng thái tại chỗ trong ô upload. | — |
| `file.failed` | Người upload | **Danger** | Không xử lý được tệp `can-hang-01.jpg`: phát hiện virus. | Tải lại tệp |
| `reputation.locked` | Người bị khoá | **Danger** | Tài khoản bị hạn chế đến 15/09/2026 do vi phạm hợp đồng HĐ-2026-0137. | Xem hồ sơ uy tín |
| `reputation.unlocked` | Người được mở | Success | Hạn chế tài khoản đã được gỡ. | Bắt đầu giao dịch |
| `bank.large_transaction_flagged` | **Chỉ Admin** | Warning | Giao dịch 4.200.000.000 ₫ vượt ngưỡng báo cáo 400 triệu. | Xem cảnh báo |
| *(inspection commission)* | Inspector | Info | Bạn được giao vụ giám định GD-2026-018. | Xem vụ |
| *(level 2 buffer expiring)* | Cả hai | Warning | Còn 5 ngày trước khi phán quyết tạm thành chung thẩm. | Xem tình trạng |
| *(system freeze)* | Tất cả | **Danger, banner dính** | Hệ thống tạm dừng mọi giao dịch tiền. Các thao tác khác vẫn hoạt động. | — |

**Quy tắc thông báo:**
- `Danger` → banner trong app + email. `Warning` → banner. `Info`/`Success` → toast + mục trong trung tâm thông báo.
- Trung tâm thông báo nhóm theo ngày, có bộ lọc theo loại, có "Đánh dấu đã đọc tất cả".
- Mỗi thông báo có: icon theo loại, tiêu đề một dòng, mô tả một dòng, thời gian tương đối ("2 giờ trước"), link hành động.
- **Không bao giờ** thông báo cho người dùng cuối về cờ AML.

---

# PHẦN 8 — MICROCOPY DÙNG LẠI

**Nút:** Tạo hợp đồng · Ký hợp đồng · Nạp ký quỹ · Cân hàng · Xác nhận đã cân · Xác nhận nhận hàng · Báo vấn đề · Báo bất khả kháng · Phản hồi · Chấp nhận kết quả · Khiếu nại kết quả · Xuất hồ sơ tín dụng · Kiểm tra tính toàn vẹn chuỗi · Đối chiếu số dư

**Empty state:**
- Chưa có hợp đồng: `Chưa có hợp đồng nào.` / `Bắt đầu bằng cách tìm nguồn hàng phù hợp.` / [Tìm nguồn hàng]
- Rỗng do lọc: `Không có hợp đồng khớp bộ lọc.` / [Xoá bộ lọc]
- Chưa có vùng trồng: `Chưa khai vùng trồng nào.` / `Cà phê và cao su cần khai vùng trồng để tuân thủ EUDR.` / [Nhập từ file KML]
- Chưa có thông báo: `Chưa có thông báo.`

**Lỗi:**
- `Không khoá được ký quỹ. Kiểm tra số dư rồi thử lại.`
- `Mã OTP không đúng. Còn 2 lần thử.`
- `Phiên ký đã hết hạn. Khởi tạo lại để nhận mã mới.`
- `Không xử lý được tệp: phát hiện virus.`
- `Tổng khối lượng các cột mốc chưa khớp khối lượng hợp đồng.`
- `Vùng trồng đang được kiểm tra vệ tinh. Chờ có kết quả rồi chọn lại.`
- `Tài khoản đang bị hạn chế đến 15/09/2026. Không thể tạo hợp đồng mới.`

**Nhắc timeout (hiện trước, không âm thầm):**
- `Nếu không thao tác trong 2 ngày làm việc, hệ thống tự xác nhận theo số đã ghi nhận.`
- `Không phản hồi trong 2 ngày làm việc đồng nghĩa với chấp nhận số bên mua ghi nhận.`
- `Còn 3 ngày trong cửa sổ báo bất khả kháng.`

**Chú thích tin cậy:**
- `Sổ cái chỉ ghi thêm — không thể sửa hoặc xoá bút toán.`
- `Số dư được tính lại từ toàn bộ bút toán, không lưu sẵn.`
- `Số tiền này là tạm tính theo phán quyết Vinacontrol. Có thể điều chỉnh khi báo cáo SGS về.`
- `Vi phạm được tính trong cửa sổ 24 tháng.`
- `Giá tham khảo, không phải giá giao dịch trên nền tảng.`

---

# PHẦN 9 — THỨ TỰ DỰNG

Dựng theo thứ tự này để design system ổn định trước khi nhân bản:

1. **Component library** (PHẦN 6) — chốt badge, table, timeline, money display.
2. **Chi tiết hợp đồng** (5.6) — màn khó nhất, ép mọi component ra mặt.
3. Tổng quan buyer + seller (5.3, 5.4)
4. Danh sách hợp đồng (5.5)
5. Sổ cái ký quỹ (5.13)
6. Xác nhận nhận hàng (5.10) + Cân hàng (5.9) — **dựng bản mobile**
7. Tạo hợp đồng wizard (5.7) + Đàm phán hợp đồng (5.6b)
8. Huỷ hợp đồng modal (5.6c) — dùng luôn component modal xác nhận nguy hiểm 2 bước
9. Kiểm định (5.14) + Hồ sơ uy tín (5.15) + modal Xuất hồ sơ tín dụng (5.25)
10. Vùng trồng (5.16, gồm nút khiếu nại ảnh EXIF) + Tạo listing (5.17, có nhánh varietyName)
11. Các màn Admin (5.19 → 5.22, đã sửa 5.21 đối xứng) + Hàng đợi lỗi email intake (5.26) + Nhập giá thủ công (5.24)
12. Analytics dashboard (5.27)
13. Trung tâm thông báo (PHẦN 7)
14. Đăng nhập / Đăng ký (5.1, 5.2 — gồm nhánh Inspector)

---

# PHỤ LỤC — SỐ LIỆU MẪU DÙNG NHẤT QUÁN

Dùng đúng bộ số này ở mọi màn để mockup trông liền mạch.

```
Hợp đồng      HĐ-2026-0142
Mặt hàng      Cà phê Robusta, 20 tấn
Đơn giá       250.000.000 ₫/tấn
Tổng giá trị  5.000.000.000 ₫
Bên mua       Công ty TNHH Cà phê Tây Nguyên — uy tín 92
Bên bán       HTX Nông nghiệp Ea Kar, Đắk Lắk — uy tín 88
Giao tại      Kho Buôn Ma Thuột
Cọc bên mua   5.000.000.000 ₫ (100%)
Cọc bên bán   500.000.000 ₫ (10%)
Phạt vi phạm  8% giá trị hợp đồng
Dung sai      ± 2%
Tiêu chuẩn    TCVN 4193:2014

Cột mốc 1  Giao hàng đợt 1 — 10 tấn — 28/05/2026 — Đã tất toán — 2.500.000.000 ₫
Cột mốc 2  Giao hàng đợt 2 — 10 tấn — 20/06/2026 — Chờ xác nhận — 2.500.000.000 ₫

Delta 1    20.000 kg cam kết → 19.680 kg seller cân   (−320 kg, có thể tính phạt)
Delta 2    19.680 kg seller  → 19.550 kg buyer cân    (−130 kg, không tính phạt)

Mã bút toán mẫu   LE-8f3a91c2
Hash mẫu          a3f9e1d4…c21b7f80
Vùng trồng        Ea Kar, Đắk Lắk — POLYGON — Tự khai — Đã kiểm tra — Rủi ro thấp
Giá tham khảo     118.500 ₫/kg (VNSAT, 08/07/2026, Đắk Lắk)
```
