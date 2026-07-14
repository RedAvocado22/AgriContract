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

**Đã loại hẳn khỏi scope:** tính giá tham chiếu từ `agreedPrice` trung bình các contract `SETTLED` nội bộ. Lý do kép — (1) platform mới, data sparse, vài chục contract đầu không đủ ý nghĩa thống kê; (2) nghiêm trọng hơn: 1 con số "giá thị trường" tính từ chính vài giao dịch trong platform có thể bị 1 buyer lớn lợi dụng — ép giá thấp vài lần đầu để tự tạo ra "giá tham chiếu" giả, rồi dùng chính con số đó ép các HTX khác — đúng nghịch lý với bài toán bất đối xứng quyền lực mà AgriContract sinh ra để giải (AgriContract_01 §1.3). Internal-average không chỉ vô dụng vì thiếu data, mà phản tác dụng nếu implement.

**Chốt: pricing-service chỉ ingest external market reference price, không tự tính gì từ data nội bộ.**

**Nguồn dữ liệu — 2 nhánh theo commodity:**

| Commodity | Nguồn | Cơ chế |
|---|---|---|
| `COFFEE` | `thitruongnongsan.gov.vn` (VNSAT — Bộ Nông nghiệp & Môi trường) | Scrape tự động, theo tỉnh |
| `RICE` | `thitruongnongsan.gov.vn` (VNSAT) | Scrape tự động, theo tỉnh |
| `RUBBER` | Không có nguồn mở **tách theo tỉnh** tương đương VNSAT; có nguồn tham chiếu **quốc tế** (anfin/thitruonghanghoa/investing/VRA — xem §5, L5) | Admin nhập tay (giữ, không ingest quốc tế trong Phase 2 vì trade-off đơn vị/tỷ giá) |
| `CASHEW` | Không có nguồn mở tương đương | Admin nhập tay |

**Quá trình chọn nguồn (05/07/2026):** Loại `giacaphe.com` (blog tư nhân, dù robots.txt sạch) sau khi tìm ra `thitruongnongsan.gov.vn` — nguồn chính thức Bộ NN&MT, tách sẵn theo tỉnh, defend trước hội đồng mạnh hơn. Repo GitHub `nguyenthanh222/Agricultural-Price-Monitor` xác nhận độc lập: scrape `nguonwmy.aspx` khả thi kỹ thuật thật (đã có người làm), dùng làm bằng chứng feasibility, không tái sử dụng code (stack Python/MongoDB không khớp Spring/MySQL).

**Chốt về rủi ro pháp lý (05/07/2026):** scope đồ án, non-commercial, không productize — chấp nhận rủi ro scraping chưa xin phép chính thức. Nếu sau này thương mại hoá thật (bán cho VICOFA/VRA theo đúng business model), cần đàm phán data licensing chính thức hoặc xin nguồn cấp API trực tiếp từ Bộ NN&MT.

---

## 2. Domain Model — `PriceQuote`

| Field | Loại | Ghi chú |
|---|---|---|
| `id` | UUID | |
| `commodity` | Enum (`COFFEE`, `RICE`, `RUBBER`, `CASHEW`) | Extensible — không hardcode logic riêng biệt cho từng loại trong domain layer |
| `itemName` | VARCHAR(255) | Tên mặt hàng cụ thể theo đúng string nguồn (VD "Cà phê Robusta nhân xô", "OM 18"). `COFFEE` luôn 1 giá trị cố định; `RICE` là dimension bắt buộc để phân biệt giống — xem §5, nhiều giống cùng tỉnh/ngày với giá khác hẳn nhau. |
| `province` | VARCHAR, nullable | VNSAT tách theo tỉnh (Đắk Lắk, Gia Lai, Lâm Đồng...) cho `COFFEE`/`RICE`. `NULL` cho `RUBBER`/`CASHEW` — Admin nhập 1 giá quốc gia, không tách tỉnh. |
| `price` | DECIMAL | VNĐ/kg |
| `source` | Enum (`VNSAT_SCRAPE`, `ADMIN_MANUAL`) | |
| `capturedAt` | DATE | Ngày giá **thật sự áp dụng** theo nguồn (cột "Ngày" trong bảng VNSAT) |
| `ingestedAt` | TIMESTAMP | Lúc hệ thống lấy được — có thể trễ hơn `capturedAt` do VNSAT publish không đều |

**MySQL là nguồn sự thật (`price_history`, append-only), Redis chỉ là cache — không phải ngược lại.** Giữ lịch sử để phục vụ biểu đồ giá sau này (VNSAT không đảm bảo cho query lại xa về trước).

**Redis key:** `price:{commodity}:{province}:{itemSlug}` (VD `price:coffee:dak-lak:ca-phe-robusta-nhan-xo`, `price:rice:can-tho:om-18`) — `itemSlug` = normalize `itemName` (bỏ dấu, lowercase, khoảng trắng → gạch ngang). Với `COFFEE` chỉ có đúng 1 `itemSlug` cố định nên format này tương đương key cũ; với `RICE` bắt buộc phải có, nếu không nhiều giống cùng tỉnh/ngày sẽ ghi đè lẫn nhau (xem §5).

**Không set TTL cứng.** Lý do: nếu key tự hết hạn đúng lúc job ingest fail (site đổi layout, mất mạng...), key biến mất hoàn toàn → seller tạo listing không thấy giá tham chiếu nào, tệ hơn thấy giá cũ có cảnh báo. Thay vào đó, "cũ hay mới" tính động bằng so `capturedAt` với `now()` lúc trả response.

**`stale` threshold — theo từng `commodity`, config trong `application.yml`, không hardcode trong code:**

```yaml
pricing:
  stale-threshold-days:
    coffee: 14   # lag quan sát thực tế ~9 ngày (26/6 lúc check 5/7), +buffer
    rice: 30     # lag quan sát thực tế ~24 ngày (11/6 lúc check 5/7), +buffer
```

Lý do lệch nhau nhiều giữa 2 commodity dù cùng 1 nguồn: VNSAT publish theo batch không đều, không phải daily thật dù giao diện trông như vậy — đã verify qua dữ liệu lịch sử (đủ 12 tháng liên tục các năm trước, không phải nguồn từng chết hẳn 1 giai đoạn). Không cần cơ chế phát hiện "nguồn đã chết" (cân nhắc rồi bỏ — xem §5) — chỉ cần ngưỡng `stale` đúng theo lag tự nhiên quan sát được của từng loại hàng. `RUBBER`/`CASHEW` (Admin manual) không áp dụng `stale` — hiển thị thẳng "cập nhật lúc {ngày Admin nhập}".

---

## 3. Ingestion

### 3.1 VNSAT scraping (`COFFEE`, `RICE`)

**Nguồn kỹ thuật:** `thitruongnongsan.gov.vn/vn/nguonwmy.aspx` — ASP.NET WebForms, không có REST API JSON. Cùng 1 URL vừa serve HTML vừa nhận lại chính request POST (postback pattern).

**Cơ chế 2 bước, dùng Jsoup `Connection` (không phải RestTemplate/WebClient — Jsoup gộp sẵn cookie-jar + HTML parser trong 1 object, đúng việc cho kiểu postback này):**

1. GET `nguonwmy.aspx` → parse `__VIEWSTATE`, `__EVENTVALIDATION`, `__VIEWSTATEGENERATOR` từ hidden input.
2. POST lại cùng URL, mang cookie từ bước 1, kèm **toàn bộ** field của form gốc — không chỉ field mình "quan tâm":
   - `__VIEWSTATE`, `__EVENTVALIDATION`, `__VIEWSTATEGENERATOR` (từ bước 1)
   - `ctl00$maincontent$tu_ngay`, `ctl00$maincontent$den_ngay` (bắt buộc — thiếu gây 500, xem §3.2)
   - `ctl00$maincontent$Ngành_hàng` (`"Cà phê"` hoặc `"Lúa gạo"`)
   - `ctl00$maincontent$Theo_thời_gian` (`"ngay"`)
   - `ctl00$maincontent$Xem` (`"Xem"`)
3. Response POST là HTML chứa bảng kết quả (`#ctl00_maincontent_GridView1`) — parse bằng Jsoup CSS selector, mỗi `<tr>` → 1 `PriceQuote` (tên mặt hàng, thị trường/tỉnh, ngày, giá).

**Bug đã fix trong quá trình debug (05/07/2026):** POST ban đầu chỉ gửi field `Ngành_hàng`, bỏ qua `tu_ngay`/`den_ngay` → 500 (ASP.NET WebForms coi form là 1 khối phải gửi đủ, không tự điền default cho field thiếu). Cà phê/Rau quả từng chạy "được" chỉ vì tình cờ không đụng đúng code path lỗi — không phải vì code đúng.

### 3.2 Admin manual entry (`RUBBER`, `CASHEW`)

`POST /api/prices/manual` (role ADMIN) — ghi thẳng `price_history` với `source = ADMIN_MANUAL`, `province = NULL`, update Redis cùng cơ chế cache-aside như §4.

### 3.3 Scheduled job

`VnsatPriceIngestionJob` (`@Scheduled`, daily — cần `@EnableScheduling` bật ở config, không tự động có sẵn). Chạy riêng cho từng commodity (`COFFEE`, `RICE`), không gộp 1 job xử lý cả 2 bằng if/else.

**Fail handling:** exception (parse lỗi, mất mạng, site đổi layout, 500) → insert `price_ingestion_failure`, **không throw tiếp** — job phải sống sót, không kéo sập scheduler. Không xoá cache Redis cũ khi fail.

```sql
CREATE TABLE price_ingestion_failure (
    id              UUID PRIMARY KEY,
    commodity       VARCHAR(20) NOT NULL,
    failure_reason  TEXT NOT NULL,
    detected_at     TIMESTAMP NOT NULL DEFAULT now()
);
```

---

## 4. Read API

`GET /api/prices/{commodity}/{province}?item={itemSlug}` — public, không cần auth (thông tin tham khảo, không phải business data nhạy cảm). `item` bắt buộc cho `RICE` (nhiều giống/tỉnh/ngày, xem §5), optional cho `COFFEE` (chỉ 1 `itemSlug` cố định, tự suy ra nếu bỏ trống).

`GET /api/prices/{commodity}/{province}/items` — liệt kê toàn bộ `itemName` có sẵn cho tỉnh đó, phục vụ autocomplete gợi ý bên `product-service` lúc seller nhập `varietyName` (không bắt buộc khớp, xem §5).

**Cache-aside chuẩn:** đọc Redis trước. Miss (key chưa từng có, hoặc Redis mất data do restart không persistence/evict) → query `price_history` (`ORDER BY capturedAt DESC LIMIT 1` theo `commodity + province`), trả về, đồng thời ghi lại Redis để lần sau hit. MySQL luôn là nguồn thật; Redis là bản sao nhanh tự vá lại được từ MySQL bất cứ lúc nào — không phải 2 nguồn ngang hàng.

Ingest job ghi MySQL trong transaction, Redis ngay sau khi commit — không phải ghi Redis rồi để API tự đồng bộ lại từ MySQL.

**Không denormalize giá vào Contract/Listing.** Giữ đúng nguyên tắc "chỉ mang tính tham khảo" — tránh trở thành con số bị lợi dụng trong đàm phán (xem §1).

---

## 5. Known Limitations / Out of Scope (có chủ đích)

- **Scraping `thitruongnongsan.gov.vn` chưa xin phép chính thức từ Bộ NN&MT** — chấp nhận được cho scope đồ án non-commercial. Nếu productize thật (bán cho VICOFA/VRA theo business model đã định), cần đàm phán data licensing hoặc xin cấp API trực tiếp.
- **HTML/ASP.NET postback dễ vỡ khi nguồn đổi layout hoặc field name** — không có cơ chế tự phát hiện, chỉ dựa vào exception khi parse fail → `price_ingestion_failure`.
- **Không có cơ chế phát hiện chủ động "nguồn đã ngừng cập nhật hẳn"** (cân nhắc `price_source_health` + `consecutive_stale_runs`, quyết định bỏ 05/07/2026) — vì dữ liệu lịch sử VNSAT cho thấy publish đều hàng tháng qua các năm, rủi ro nguồn chết hẳn thấp so với công sức thêm cơ chế theo dõi. Chỉ dựa vào ngưỡng `stale` tĩnh theo lag quan sát được.
- **`RUBBER`/`CASHEW` phụ thuộc Admin nhập tay — thu hẹp (08/07/2026, L5): cao su CÓ nguồn tham chiếu quốc tế, chủ động chọn giữ Admin vì trade-off scope, không phải vì thiếu nguồn.**
  - **Cao su:** có nguồn giá online — `hanghoa.anfin.vn/gia-thi-truong/cao-su/` (RSS3/TSR20 real-time), `thitruonghanghoa.com` (SICOM/TOCOM), `investing.com` (RSS3 futures + lịch sử), và VRA (Hiệp hội Cao su VN) công bố giá. **Quyết định (08/07/2026): KHÔNG ingest tự động trong Phase 2** — giá cao su quốc tế tính bằng JPY/kg (TOCOM) hoặc USD/tấn (SICOM), khác đơn vị VNĐ/kg của VNSAT, cần chuẩn hoá đơn vị + tỷ giá real-time cho đúng 1 commodity → thêm phức tạp không đáng đánh đổi cho scope đồ án, đúng nguyên tắc tránh over-engineer đã áp cho dead-source detection. Giữ Admin manual; ghi nhận nguồn quốc tế là **enhancement path đã cân nhắc có chủ đích**, không phải điểm mù. Defend hội đồng: "biết có nguồn X/Y/Z, chọn Admin vì chuẩn hoá tỷ giá không đáng cho scope, Admin đủ cho mục đích tham khảo."
  - **Điều:** giữ nguyên — không có nguồn tham chiếu online đủ tốt tương đương, phụ thuộc hoàn toàn Admin nhập tay, sai sót là lỗi con người, không có validation chéo.
- **`RICE` không phải 1 giá/tỉnh/ngày như `COFFEE`** — nhiều giống (OM 18, ST25, IR 50404...) cùng tồn tại 1 tỉnh 1 ngày với giá khác hẳn nhau (đã verify qua data thật). `PriceQuote` cần thêm field `itemName` (tên mặt hàng cụ thể theo đúng string nguồn), Redis key đổi thành `price:{commodity}:{province}:{itemSlug}` (itemSlug = normalize itemName: bỏ dấu, lowercase, bỏ khoảng trắng). Nguồn VNSAT tự nó cũng không sạch 100% — quan sát thấy `"IR 50404"` và `"IR50404"` cùng tỉnh/ngày với giá khác nhau, không rõ 2 giống thật hay lỗi nhập liệu nguồn, không tự suy diễn.
- **Cross-service dependency với `product-service` — đã chốt (05/07/2026):** `product-service` cần thêm `Product.varietyName` (VARCHAR nullable, additive migration, không đụng `Product` aggregate logic đã test) để seller khai giống lúa cụ thể. Free text, KHÔNG lock cứng theo danh sách `itemName` của pricing-service — tránh tạo dependency đồng bộ giữa 2 service chỉ cho 1 field phụ trợ. Match giá tham khảo là best-effort: pricing-service normalize `varietyName` theo cùng thuật toán `itemSlug`, khớp thì hiện giá, không khớp thì im lặng không hiện — không phải lỗi, không block tạo listing.
- **`stale-threshold-days` dựa trên 1 lần quan sát lag thực tế (05/07/2026), không phải trung bình dài hạn** — có thể cần điều chỉnh lại sau khi chạy thật vài tuần/tháng.

---

## 6. Status — Pricing-service Design

**Chốt (05/07/2026):** pricing-service chỉ ingest external reference price (VNSAT scrape cho coffee/rice, Admin manual cho rubber/cashew) — bỏ hẳn internal aggregation vì data sparse và rủi ro bị lợi dụng làm giá giả. MySQL append-only là nguồn thật, Redis cache không TTL, `stale` tính động theo threshold riêng từng commodity. Ingestion qua Jsoup 2-bước GET→POST mô phỏng đúng ASP.NET WebForms postback, đã fix bug thiếu field ngày tháng gây 500. Không denormalize giá vào Contract — giữ tính tham khảo thuần tuý.

Pricing-service — **đóng session, sẵn sàng đưa vào Architecture/SDS/TechnicalSpec chính thức.**

---

_Design session: 05/07/2026 · Cập nhật 08/07/2026 (L5: thu hẹp limitation cao su/điều — ghi nhận cao su CÓ nguồn tham chiếu quốc tế anfin/thitruonghanghoa/investing/VRA là enhancement path, chủ động giữ Admin manual vì trade-off đơn vị/tỷ giá; điều giữ nguyên) · Chưa code · Chưa đưa vào Architecture/SDS/TechnicalSpec chính thức._
