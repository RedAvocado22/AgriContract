---
name: pricing-service-phase2-design
description: "Pricing-service — cache giá tham chiếu nông sản từ nguồn ngoài (VNSAT), phục vụ hiển thị tham khảo lúc tạo listing/đàm phán. Nguồn: design session 05/07/2026."
status: DESIGNED — chưa code.
metadata:
    type: design
    phase: 2
    extends: "services.md § pricing-service (8091 — pain point: Redis cache + invalidation, pub-sub broadcast, external data ingestion)"
    related: "product-phase2-design.md (listing creation, nơi hiển thị giá tham khảo); services.md (thứ tự ưu tiên file-service → pricing-service → analytics-service, đã chốt ở file-service-phase2-design.md §1)"
---

## 1. Bối cảnh & Scope

> **Role vận hành — sync SRS v1.6 (12/10/2026, thay note 18/07):** mọi use case vận hành trong doc này (review/moderate/nhập liệu hằng ngày, theo dõi alert) thuộc **OPERATOR**; ADMIN không làm thay (không fallback). Permission matrix ở `data-governance-phase2-design.md` §5.4 là source of truth; flow/state/schema không đổi.

> **Scope Phase 2 (07/10/2026):** chỉ hỗ trợ giao dịch nội địa Việt Nam cho `COFFEE`. Mọi ghi mới, job ingest, API và màn hình vận hành trong tài liệu này phải từ chối commodity khác. Các ghi chú về gạo, cao su và điều chỉ là lịch sử phân tích/roadmap, không phải capability thực thi Phase 2.


**Đã loại hẳn khỏi scope:** tính giá tham chiếu từ `agreedPrice` trung bình các contract `SETTLED` nội bộ. Lý do kép — (1) platform mới, data sparse, vài chục contract đầu không đủ ý nghĩa thống kê; (2) nghiêm trọng hơn: 1 con số "giá thị trường" tính từ chính vài giao dịch trong platform có thể bị 1 buyer lớn lợi dụng — ép giá thấp vài lần đầu để tự tạo ra "giá tham chiếu" giả, rồi dùng chính con số đó ép các HTX khác — đúng nghịch lý với bài toán bất đối xứng quyền lực mà AgriContract sinh ra để giải (AgriContract_01 §1.3). Internal-average không chỉ vô dụng vì thiếu data, mà phản tác dụng nếu implement.

**Chốt: pricing-service chỉ ingest external market reference price, không tự tính gì từ data nội bộ.**

**Nguồn dữ liệu Phase 2:**

| Commodity | Nguồn | Cơ chế |
|---|---|---|
| `COFFEE` | `thitruongnongsan.gov.vn` (VNSAT — Bộ Nông nghiệp & Môi trường) | Scrape tự động, theo tỉnh |

**Quá trình chọn nguồn (05/07/2026):** Loại `giacaphe.com` (blog tư nhân, dù robots.txt sạch) sau khi tìm ra `thitruongnongsan.gov.vn` — nguồn chính thức Bộ NN&MT, tách sẵn theo tỉnh, defend trước hội đồng mạnh hơn. Repo GitHub `nguyenthanh222/Agricultural-Price-Monitor` xác nhận độc lập: scrape `nguonwmy.aspx` khả thi kỹ thuật thật (đã có người làm), dùng làm bằng chứng feasibility, không tái sử dụng code (stack Python/MongoDB không khớp Spring/MySQL).

**Chốt về rủi ro pháp lý (05/07/2026):** scope đồ án, non-commercial, không productize — chấp nhận rủi ro scraping chưa xin phép chính thức. Nếu sau này thương mại hoá thật (bán cho VICOFA/VRA theo đúng business model), cần đàm phán data licensing chính thức hoặc xin nguồn cấp API trực tiếp từ Bộ NN&MT.

---

## 2. Domain Model — `PriceQuote`

| Field | Loại | Ghi chú |
|---|---|---|
| `id` | UUID | |
| `commodity` | Const `COFFEE` | API và persistence Phase 2 từ chối mọi giá trị khác |
| `itemName` | VARCHAR(255) | Tên mặt hàng cà phê theo đúng string nguồn, ví dụ "Cà phê Robusta nhân xô" |
| `province` | VARCHAR | VNSAT tách giá cà phê theo tỉnh (Đắk Lắk, Gia Lai, Lâm Đồng...) |
| `price` | DECIMAL | VNĐ/kg |
| `source` | Enum (`VNSAT_SCRAPE`, `OPERATOR_MANUAL`) | Đổi tên từ `ADMIN_MANUAL` (12/10/2026, SRS v1.6). Code đã có `ADMIN_MANUAL` thì đổi enum + migration `UPDATE price_history SET source='OPERATOR_MANUAL' WHERE source='ADMIN_MANUAL'` — chỉ đổi nhãn nguồn, không đổi giá, attribution người nhập vẫn giữ nguyên |
| `capturedAt` | DATE | Ngày giá **thật sự áp dụng** theo nguồn (cột "Ngày" trong bảng VNSAT) |
| `ingestedAt` | TIMESTAMP | Lúc hệ thống lấy được — có thể trễ hơn `capturedAt` do VNSAT publish không đều |

**MySQL là nguồn sự thật (`price_history`, append-only), Redis chỉ là cache — không phải ngược lại.** Giữ lịch sử để phục vụ biểu đồ giá sau này (VNSAT không đảm bảo cho query lại xa về trước).

**Redis key:** `price:coffee:{province}:{itemSlug}` (VD `price:coffee:dak-lak:ca-phe-robusta-nhan-xo`) — `itemSlug` = normalize `itemName` (bỏ dấu, lowercase, khoảng trắng → gạch ngang).

**Không set TTL cứng.** Lý do: nếu key tự hết hạn đúng lúc job ingest fail (site đổi layout, mất mạng...), key biến mất hoàn toàn → seller tạo listing không thấy giá tham chiếu nào, tệ hơn thấy giá cũ có cảnh báo. Thay vào đó, "cũ hay mới" tính động bằng so `capturedAt` với `now()` lúc trả response.

**`stale` threshold** được cấu hình trong `application.yml`, không hardcode trong code:

```yaml
pricing:
  stale-threshold-days:
    coffee: 14   # lag quan sát thực tế ~9 ngày (26/6 lúc check 5/7), +buffer
```

VNSAT publish theo batch không đều, không phải daily thật dù giao diện trông như vậy. Ngưỡng trên phản ánh lag cà phê đã quan sát cộng buffer; khi nguồn lỗi, hệ thống giữ bản gần nhất và hiển thị rõ thời điểm cập nhật.

---

## 3. Ingestion

### 3.1 VNSAT scraping (`COFFEE`)

**Nguồn kỹ thuật:** `thitruongnongsan.gov.vn/vn/nguonwmy.aspx` — ASP.NET WebForms, không có REST API JSON. Cùng 1 URL vừa serve HTML vừa nhận lại chính request POST (postback pattern).

**Cơ chế 2 bước, dùng Jsoup `Connection` (không phải RestTemplate/WebClient — Jsoup gộp sẵn cookie-jar + HTML parser trong 1 object, đúng việc cho kiểu postback này):**

1. GET `nguonwmy.aspx` → parse `__VIEWSTATE`, `__EVENTVALIDATION`, `__VIEWSTATEGENERATOR` từ hidden input.
2. POST lại cùng URL, mang cookie từ bước 1, kèm **toàn bộ** field của form gốc — không chỉ field mình "quan tâm":
   - `__VIEWSTATE`, `__EVENTVALIDATION`, `__VIEWSTATEGENERATOR` (từ bước 1)
   - `ctl00$maincontent$tu_ngay`, `ctl00$maincontent$den_ngay` (bắt buộc — thiếu gây 500, xem §3.2)
   - `ctl00$maincontent$Ngành_hàng` (`"Cà phê"`)
   - `ctl00$maincontent$Theo_thời_gian` (`"ngay"`)
   - `ctl00$maincontent$Xem` (`"Xem"`)
3. Response POST là HTML chứa bảng kết quả (`#ctl00_maincontent_GridView1`) — parse bằng Jsoup CSS selector, mỗi `<tr>` → 1 `PriceQuote` (tên mặt hàng, thị trường/tỉnh, ngày, giá).

**Bug đã fix trong quá trình debug (05/07/2026):** POST ban đầu chỉ gửi field `Ngành_hàng`, bỏ qua `tu_ngay`/`den_ngay` → 500 (ASP.NET WebForms coi form là 1 khối phải gửi đủ, không tự điền default cho field thiếu). Cà phê/Rau quả từng chạy "được" chỉ vì tình cờ không đụng đúng code path lỗi — không phải vì code đúng.

### 3.2 Operator manual fallback (`COFFEE`)

`POST /api/v1/prices/manual` (role OPERATOR) chỉ nhận `commodity = COFFEE`, dùng khi nguồn VNSAT gián đoạn hoặc cần hiệu chỉnh có kiểm soát. Request bắt buộc tỉnh, ngày áp dụng, nguồn tham chiếu và lý do; ghi append-only vào `price_history` với `source = OPERATOR_MANUAL`, rồi update Redis sau commit. Không sửa/xoá bản giá cũ.

### 3.3 Scheduled job

`VnsatPriceIngestionJob` (`@Scheduled`, daily — cần `@EnableScheduling` bật ở config, không tự động có sẵn) chỉ ingest `COFFEE`.

**Fail handling:** exception (parse lỗi, mất mạng, site đổi layout, 500) → insert `price_ingestion_failure`, **không throw tiếp** — job phải sống sót, không kéo sập scheduler. Không xoá cache Redis cũ khi fail.

```sql
CREATE TABLE price_ingestion_failure (
    id              CHAR(36) PRIMARY KEY,
    commodity       VARCHAR(20) NOT NULL,
    failure_reason  TEXT NOT NULL,
    detected_at     TIMESTAMP NOT NULL DEFAULT now()
);
```

---

## 4. Read API

`GET /api/v1/prices/{commodity}/{province}?item={itemSlug}` — public, không cần auth (thông tin tham khảo, không phải business data nhạy cảm). `{commodity}` bắt buộc là `COFFEE`; `item` optional và được suy ra khi nguồn chỉ có một dòng cà phê phù hợp.

`GET /api/v1/prices/{commodity}/{province}/items` — liệt kê toàn bộ `itemName` có sẵn cho tỉnh đó, phục vụ autocomplete gợi ý bên `product-service` lúc seller nhập `varietyName` (không bắt buộc khớp, xem §5).

**Cache-aside chuẩn:** đọc Redis trước. Miss (key chưa từng có, hoặc Redis mất data do restart không persistence/evict) → query `price_history` (`ORDER BY capturedAt DESC LIMIT 1` theo `commodity + province`), trả về, đồng thời ghi lại Redis để lần sau hit. MySQL luôn là nguồn thật; Redis là bản sao nhanh tự vá lại được từ MySQL bất cứ lúc nào — không phải 2 nguồn ngang hàng.

Ingest job ghi MySQL trong transaction, Redis ngay sau khi commit — không phải ghi Redis rồi để API tự đồng bộ lại từ MySQL.

**Không denormalize giá vào Contract/Listing.** Giữ đúng nguyên tắc "chỉ mang tính tham khảo" — tránh trở thành con số bị lợi dụng trong đàm phán (xem §1).

### 4.1 Boundary với signed price band (mới, 23/07/2026)

Pricing-service **advisory-only**. UI có thể hiển thị `PriceQuote` khi hai bên nhập `agreedPrice`/`priceBand`, nhưng contract-service không gọi pricing-service trong create/sign/funding/settlement và không lưu `quoteId` như một điều kiện thực thi. Source stale, unavailable hoặc không match `varietyName` không được block contract.

Milestone price adjustment trong band là workflow của contract-service: hai bên authenticated propose/accept một giá đã nằm trong biên ký trước. Pricing-service không propose tự động, không xác nhận giá, không publish funding command và không trở thành dependency của escrow. Đặc biệt, không có nhánh "lấy latest market price lúc funding" — hành vi đó sẽ biến advisory data thành index pricing ngoài signed rule.

---

## 5. Known Limitations / Out of Scope (có chủ đích)

- **Scraping `thitruongnongsan.gov.vn` chưa xin phép chính thức từ Bộ NN&MT** — chấp nhận được cho scope đồ án non-commercial. Nếu productize thật (bán cho VICOFA/VRA theo business model đã định), cần đàm phán data licensing hoặc xin cấp API trực tiếp.
- **HTML/ASP.NET postback dễ vỡ khi nguồn đổi layout hoặc field name** — không có cơ chế tự phát hiện, chỉ dựa vào exception khi parse fail → `price_ingestion_failure`.
- **Không có cơ chế phát hiện chủ động "nguồn đã ngừng cập nhật hẳn"** (cân nhắc `price_source_health` + `consecutive_stale_runs`, quyết định bỏ 05/07/2026) — vì dữ liệu lịch sử VNSAT cho thấy publish đều hàng tháng qua các năm, rủi ro nguồn chết hẳn thấp so với công sức thêm cơ chế theo dõi. Chỉ dựa vào ngưỡng `stale` tĩnh theo lag quan sát được.
- **Commodity ngoài cà phê là roadmap, không phải Phase 2.** Phân tích trước đây từng khảo sát gạo, cao su và điều. Nếu mở rộng sau này phải thiết kế lại nguồn, đơn vị, tỷ giá, độ tươi dữ liệu và quality schema; không được tái kích hoạt chỉ bằng cách mở enum.
- **`stale-threshold-days` dựa trên 1 lần quan sát lag thực tế (05/07/2026), không phải trung bình dài hạn** — có thể cần điều chỉnh lại sau khi chạy thật vài tuần/tháng.

---

## 6. Status — Pricing-service Design

**Chốt (07/10/2026):** pricing-service Phase 2 chỉ nhận `COFFEE` tại Việt Nam, ingest giá tham khảo ngoài từ VNSAT và có manual fallback có lý do/audit. Bỏ hẳn internal aggregation vì data sparse và rủi ro bị lợi dụng làm giá giả. MySQL append-only là nguồn thật, Redis cache không TTL, `stale` tính động. Không denormalize giá vào Contract — giữ tính tham khảo thuần tuý.

Pricing-service — **đóng session, sẵn sàng đưa vào Architecture/SDS/TechnicalSpec chính thức.**

---

_Design session: 05/07/2026 · Sync role SRS v1.6: 12/10/2026 · Cập nhật 07/10/2026: khóa scope Phase 2 ở Việt Nam + cà phê; gạo/cao su/điều chuyển thành roadmap, không còn là executable scope · Chưa code._
