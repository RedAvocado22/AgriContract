---
name: product-phase2-design
description: "Product-service — Farm Plot Geolocation cho EUDR compliance (Phase 2). Nguồn: design session 02/07/2026, cập nhật 04/07/2026 (Plot Registry + GEOMETRY/JTS validation), 05/07/2026 (province cross-check, varietyName), 06/07/2026 (geo-risk verification qua vệ tinh — chưa đóng)."
status: DESIGNED — chưa code.
metadata:
  type: design
  phase: 2
  extends: "architecture.md § Product Aggregate (Phase 1, đã code + E2E verified)"
  supersedes: "bản 02/07/2026 của chính file này (geo_json TEXT → GEOMETRY; thêm Plot Registry tái sử dụng qua KML import, thay vì khai lại mỗi lần tạo listing)"
---

## 1. Bối cảnh & Scope

> **Role vận hành (18/07/2026):** các use case Admin trong doc này (review/moderate/nhập liệu hằng ngày) nhận thêm role `OPERATOR` theo `data-governance-phase2-design.md` §5 — permission matrix ở đó là source of truth, doc này không lặp lại; flow/state/schema không đổi.


EUDR (EU Deforestation Regulation) áp dụng từ **30/12/2026** cho large/medium operator (Regulation 2025/2650, dời deadline lần 2, đã chốt cuối cùng — không còn dời tiếp). Yêu cầu cốt lõi: mọi lô hàng đưa ra thị trường EU phải truy được về **toạ độ chính xác của (các) plot đất nơi hàng được trồng**, chứng minh không có phá rừng sau 31/12/2020.

**Điểm quan trọng nhất quyết định thiết kế này — EUDR cấm mass balance:** không được gộp hàng từ nhiều nguồn rồi khai đại diện bằng 1 plot. Phải khai đủ **toàn bộ** plot đóng góp vào lô hàng, segregate rõ ràng, không được "coi như" hoặc pha trộn.

Điều này va trực tiếp với bản chất HTX: một listing của HTX thường là hàng **gom từ nhiều hộ thành viên**, mỗi hộ có plot đất riêng — không phải 1 nông trại lớn duy nhất. Nên geolocation không thể là 1 cặp lat/long gắn vào `Product`, mà phải là **mảng plot**, mỗi phần tử ứng với 1 hộ đóng góp.

**Cập nhật (04/07/2026) — vấn đề thứ 2, ngang tầm quan trọng với vấn đề trên:** khai `geoJson` **mỗi lần tạo listing mới** là phi thực tế với HTX gom hàng từ hàng chục tới hàng trăm hộ — mỗi mùa vụ/hợp đồng mới lại bắt gõ lại toạ độ 6 số thập phân cho từng hộ là chắc chắn sinh sai số do con người, không phải rủi ro mà là hệ quả tất yếu. §2.4/§4 giải quyết việc này bằng cách tách khai báo đất (1 lần) khỏi tạo listing (nhiều lần).

**Vị trí trong kiến trúc:** đây là dữ liệu **tĩnh, gắn với nguồn gốc hàng**, không đổi qua vòng đời `OFFERED → ... → SETTLED` của `Contract`/`Milestone`. Không có "bước" nào trong Contract-service hay Milestone Escrow cần chụp GPS — nó phải tồn tại **trước khi** listing được tạo, thuộc về `product-service` (Phase 1, đã code + E2E verify). Additive change — thêm bảng con, không đụng business logic đã test của `Product` aggregate.

**Cập nhật (08/07/2026) — geolocation/EUDR là conditional feature theo commodity, không phải core cho mọi ngành (C3 báo cáo rà soát):** AgriContract định vị 4 ngành — cà phê, gạo, cao su, điều. Nhưng `[VERIFIED ✓]` EUDR (EU 2023/1115) chỉ áp cho cà phê, cao su, gỗ, ca cao (+ vài mặt hàng) — **gạo và điều KHÔNG thuộc EUDR**. Với gạo/điều, toàn bộ tầng geolocation/EUDR (`PlotRegistryEntry`, NDVI, cadastral, province cross-check) **không áp dụng** — giá trị đến từ escrow + dispute + reputation (những thứ core cho *mọi* ngành). Đây là thiết kế module hoá đúng, không phải over-invest: các field geo (`geoVerificationStatus`, `verificationLevel`...) vốn đã nullable/optional. **Cần kiểm tra khi implement:** các luồng có `commodity`-gate cho geolocation chưa? Nếu gạo/điều vẫn bị *bắt buộc* khai plot lúc `CreateListing` thì đó là bug scope — chỉ cà phê/cao su (commodity thuộc EUDR) mới bật geolocation bắt buộc; gạo/điều bỏ qua tầng này hoàn toàn (§4). Framing cho SDS/bảo vệ: geolocation là *"module EUDR bật/tắt theo commodity"*, không phải core universal — biến câu hỏi "sao Product-service đầu tư nặng nhất mà chỉ phục vụ 2/4 ngành?" thành câu trả lời "module hoá đúng, chỉ bật khi luật yêu cầu".

**Cập nhật (08/07/2026) — quan hệ với hạ tầng dữ liệu vùng trồng nhà nước (C2 báo cáo rà soát):** `[VERIFIED ✓]` Bộ NN&PTNT + IDH đã xây và chuyển giao **Hệ thống CSDL rừng và vùng trồng cà phê tuân thủ EUDR** (thí điểm Đắk Lắk/Lâm Đồng/Gia Lai/Kon Tum, đang mở rộng ra cao su/hồ tiêu, cung cấp mã địa chính thống nhất theo nông hộ) — cộng với hệ thống truy xuất do VICOFA đồng hành. **AgriContract KHÔNG cạnh tranh ở tầng "kho dữ liệu vùng trồng"** — nó ở tầng **hợp đồng + ký quỹ + tranh chấp** (thứ CSDL nhà nước không làm). Geolocation trong AgriContract chỉ là *một mắt xích gắn vào hợp đồng*, và có thể **tiêu thụ/liên thông** CSDL nhà nước thay vì tự dựng từ đầu — mã địa chính thống nhất theo nông hộ của hệ thống đó chính là nguồn dữ liệu lý tưởng cho `verificationLevel = CADASTRAL_BACKED` (§2.2.1), thay vì chỉ dựa Sentinel Hub NDVI độc lập. Định vị đúng khi bảo vệ: *"thiết kế để tham chiếu/liên thông hạ tầng dữ liệu nhà nước đang hình thành, không xây lại"* — vừa thực tế, vừa cho thấy hiểu bối cảnh ngành. **[FIELD VALIDATION]:** cơ chế liên thông cụ thể (API, format, quyền truy cập) chưa xác nhận được với Bộ — giữ ở mức "thiết kế sẵn sàng tiêu thụ", không claim đã tích hợp.

---

## 2. Domain Model Changes

### 2.1 `PlotRegistryEntry` (mới, 04/07/2026) — nguồn dữ liệu tái sử dụng, thuộc về Seller

**Chốt (04/07/2026):** tách khai báo đất ra khỏi vòng đời `Product`. `PlotRegistryEntry` thuộc về **Seller** (`sellerId`, plain UUID — không `REFERENCES user_db`, cross-service, cùng nguyên tắc đã áp cho `signerUserId`/`signature.report_id`), không thuộc về 1 `Product` cụ thể nào — 1 hộ đăng ký đất 1 lần, dùng lại cho mọi listing sau này của mùa vụ khác.

Không cần aggregate riêng (không state machine, không lifecycle độc lập, giống lý do `ProductPlot` không tách aggregate ở bản gốc) — nhưng **cũng không phải VO con của `Product`** như `ProductPlot`, vì nó sống độc lập với bất kỳ `Product` nào, có thể tồn tại trước khi listing đầu tiên được tạo. Coi như 1 aggregate root nhỏ, đơn giản, riêng của mình, thuộc `product-service` (cùng bounded context geolocation/EUDR).

| Field | Loại | Ghi chú |
|---|---|---|
| `registryEntryId` | UUID | |
| `sellerId` | UUID | Chủ sở hữu — HTX/seller account. Không FK cross-service. |
| `householdLabel` | String | Tên/mã hộ thành viên — giữ nguyên nghĩa như bản gốc, không cần map `User` account. `UNIQUE(sellerId, householdLabel)` (04/07/2026) — key dùng để match re-import/update, xem §4. |
| `geometryType` | Enum (`POINT`, `POLYGON`) | Quy tắc §2.3, không đổi |
| `geoJson` | **GEOMETRY, SRID 4326** (đổi từ `TEXT`, xem §2.5) | |
| `areaHectares` | BigDecimal | Cách lấy khác nhau theo `geometryType`/`source` — xem §4 |
| `source` | Enum (`KML_IMPORT`, `MANUAL_PIN`) | Xem §2.2 — quyết định method nào tạo ra entry này, ảnh hưởng tới `geometryType` được phép (§2.2) |
| `accuracyMeters` | Decimal, nullable | **Mới (04/07/2026).** Chỉ có giá trị khi `source = MANUAL_PIN` (từ `coords.accuracy` của Geolocation API) — `NULL` cho `KML_IMPORT` (KML không mang theo metric này). Dùng để cảnh báo UI, không phải điều kiện chặn — xem §4. |
| `verificationLevel` | Enum (`SELF_DECLARED`, `CADASTRAL_BACKED`) | **Mới (04/07/2026).** Mặc định `SELF_DECLARED`. `CADASTRAL_BACKED` khi seller upload kèm "trích lục bản đồ địa chính" xin từ UBND xã/Văn phòng đăng ký đất đai — xem §2.2.1. |
| `cadastralExtractFileId` | UUID, nullable | Reference `file-service` — bản scan/PDF trích lục seller upload. `NULL` nếu `verificationLevel = SELF_DECLARED`. |
| `originalKmlFileId` | UUID, nullable | **Mới (05/07/2026).** Reference `file-service` — file KML gốc mà entry này (hoặc lần re-import gần nhất của nó) được parse ra. `NULL` nếu `source = MANUAL_PIN`. Field phẳng, lặp giá trị cho mọi entry cùng ra từ 1 lần import (không tách bảng `PlotImportBatch` riêng — không có consumer nào cần query ở tầng batch, xem thảo luận session 05/07/2026). Mục đích là provenance ("entry này từng đến từ file nào"), **không** phải cơ chế re-verify toạ độ — file gốc chỉ chứa số WGS84 đã convert xong, không giữ số VN2000 gốc để đối chiếu ngược (xem giới hạn ở §5). |
| `declaredProvince` | Enum (34 tỉnh, theo địa giới hiện hành Nghị quyết 202/2025/QH15) | **Mới (05/07/2026), cập nhật 08/07/2026 — đổi 63→34 tỉnh (L1).** Seller tự chọn dropdown lúc `ImportPlotsFromKML`/`AddPlotManually` — nguồn độc lập với toạ độ, dùng để cross-check ở §4. Không suy ra tự động từ toạ độ (nếu suy ra từ chính toạ độ thì không còn là nguồn độc lập, mất tác dụng đối chiếu). |
| `provinceMismatchFlag` | Boolean, default `FALSE` | **Mới (05/07/2026).** `TRUE` nếu tỉnh suy ra từ toạ độ (point-in-polygon, §4) không khớp `declaredProvince`. Non-blocking — không chặn insert, chỉ cảnh báo UI. Persistent trong DB (không biến mất sau khi seller tắt màn hình) — phục vụ audit trail "hệ thống đã cảnh báo từ lúc import". |
| `importedAt` | Timestamp | Đổi ý nghĩa khi entry được UPDATE qua re-import (§4) — luôn phản ánh lần cập nhật gần nhất, không phải lần tạo đầu tiên |
| `geoVerificationStatus` | Enum (`PENDING`, `CHECKED`, `UNVERIFIED`) | **Mới (06/07/2026).** Trạng thái tiến trình check vệ tinh NDVI, KHÔNG phải kết quả rủi ro — xem §2.2.4. Mặc định `PENDING` lúc tạo entry. |
| `geoRiskLevel` | Enum, nullable (`LOW_RISK`, `HIGH_RISK`, `INCONCLUSIVE`) | **Mới (06/07/2026, mở rộng thêm `INCONCLUSIVE` cùng ngày).** Kết quả thật của NDVI check — chỉ có giá trị khi `geoVerificationStatus = CHECKED`. `NULL` khi `PENDING`/`UNVERIFIED`. `INCONCLUSIVE` khác `UNVERIFIED`: API **đã trả lời**, nhưng dữ liệu nhiễu (mây che, xen canh nhiều tầng) khiến không kết luận được rừng hay không — xem §2.2.4. |

### 2.2.1 `verificationLevel` — tầng bằng chứng bổ sung, tuỳ chọn (mới, 04/07/2026)

**Quan trọng — không được hiểu nhầm thành "đã xác minh sở hữu":** "trích lục bản đồ địa chính" (xin tại UBND xã hoặc Văn phòng đăng ký đất đai cấp huyện/tỉnh, quy định tại Luật Đất đai 2024/Thông tư 34/2014/TT-BTNMT) là dữ liệu chính thức từ nhà nước (số thửa, tờ bản đồ, ranh giới, diện tích) — **nhưng theo đúng quy định, bản thân nó không phải văn bản pháp lý chứng minh quyền sử dụng đất** (đó là vai trò của Giấy chứng nhận quyền sử dụng đất/sổ đỏ, văn bản khác hẳn). `CADASTRAL_BACKED` vì vậy **không** đóng được giới hạn "khai đất giả nhưng hợp lệ hình học" đã nêu ở §5 — chỉ giảm nhẹ nó: người xin trích lục phải chứng minh liên quan tới đúng thửa đất đó khi nộp đơn tại xã, nên có bằng chứng nhà nước tham chiếu được nếu sau này tranh chấp, mạnh hơn hẳn 1 file KML tự đo không ai xác nhận.

**Không đổi pipeline nhập liệu** — toạ độ vẫn qua `ImportPlotsFromKML`/`AddPlotManually` như cũ (§2.2), y hệt validate GEOMETRY/JTS (§2.5). `cadastralExtractFileId` chỉ là 1 field bằng chứng đính kèm thêm, hoàn toàn tuỳ chọn — seller không có vẫn tạo/dùng entry bình thường (`SELF_DECLARED`), không bị chặn gì.

### 2.2.2 Province cross-check — mitigation cho lỗi convert VN2000 → WGS84 (mới, 05/07/2026)

**Bối cảnh:** toạ độ trong KML bắt buộc theo chuẩn là WGS84, nhưng cán bộ địa chính đo gốc bằng VN2000 (hệ chiếu phẳng theo mét, chia múi theo kinh tuyến trục riêng từng tỉnh) rồi convert trước khi xuất file — bước convert này xảy ra hoàn toàn bên ngoài, trước khi file .kml chạm tới `ImportPlotsFromKML`. Nếu phần mềm đo dùng sai tham số kinh tuyến trục (ví dụ đo ở Đắk Lắk nhưng quên đổi config từ tỉnh trước đó), kết quả convert vẫn ra đúng **dạng** toạ độ độ WGS84, vẫn nằm trong bounding box Việt Nam, vẫn pass `JTS .isValid()` — sai lệch tính được tới **81.37km** (verify bằng pyproj, điểm mẫu Đắk Lắk) nhưng không tầng validate cú pháp/hình học nào ở §2.5 bắt được, vì đây là lỗi **nội dung** (giá trị không đúng vị trí thật), không phải lỗi **cú pháp** (giá trị không đúng định dạng) — 2 loại validate khác nhau, và platform chỉ làm được loại đầu.

**Chốt (05/07/2026) — cross-check bằng nguồn độc lập, không cố validate nội dung trực tiếp:** vì platform không có ground-truth để biết ranh giới đất thật, giải pháp là đối chiếu 2 nguồn độc lập thay vì xác minh tuyệt đối 1 nguồn:
- Nguồn 1: `declaredProvince` — seller tự chọn dropdown, không đi qua máy đo/phần mềm convert.
- Nguồn 2: tỉnh suy ra từ toạ độ đã parse, qua point-in-polygon (JTS, đã có sẵn trong stack) với polygon ranh giới tỉnh (§2.2.3).

2 nguồn khớp nhau → không làm gì. Lệch → `provinceMismatchFlag = TRUE`, cảnh báo UI, không chặn insert (seller có thể ở gần ranh giới tỉnh, hoặc tự khai nhầm tỉnh — không nhất thiết là lỗi toạ độ).

**Giới hạn của cross-check này (không đóng hẳn vấn đề):** nếu lỗi convert là **hệ thống** (cùng 1 lần đo, cùng 1 tham số sai áp cho toàn bộ batch), mọi entry trong batch dịch cùng hướng, cùng khoảng cách — vẫn nhất quán *nội bộ* với nhau. Check này chỉ bắt được khi toạ độ suy ra lệch khỏi tỉnh seller khai, không bắt được trường hợp cả batch cùng lệch theo cùng 1 kiểu. Không đóng permanent limitation "khai giả nhưng hợp lệ hình học" (§5) — chỉ giảm nhẹ rủi ro lỗi *vô ý* do config sai, không bắt được fraud có chủ đích.

### 2.2.3 Nguồn polygon ranh giới tỉnh — dùng Free-GIS-Data 34 tỉnh (cập nhật 08/07/2026, L1 — trước đây GADM 63 tỉnh)

**Bối cảnh cũ (chốt 05/07/2026, nay superseded):** lúc đó chưa tìm ra shapefile/GeoJSON polygon 34 tỉnh mở nào — Cục Đo đạc chỉ công bố bản đồ 34 tỉnh dạng PDF/ảnh, không đọc được bằng code. Nên tạm dùng GADM (63 tỉnh cũ, trước sáp nhập) cho point-in-polygon, chấp nhận Known Limitation false-positive mismatch ở vùng vừa sáp nhập.

**Phát hiện (08/07/2026) — ĐÃ CÓ structured data 34 tỉnh mở, gỡ được limitation cũ:**
- **`github.com/nguyenduy1133/Free-GIS-Data`** — shapefile + GeoJSON ranh giới hành chính **34 tỉnh** (gồm Hoàng Sa/Trường Sa), free public use (yêu cầu cite nguồn), cập nhật 10/07/2025, sync từ Tổng cục Thống kê (GSO). Đọc được bằng code, đúng định dạng point-in-polygon cần.
- Backup: **GeoVina API** (`geovina.io.vn/new-provinces`, REST + API key, miễn phí) — danh sách + tra cứu 34 tỉnh, cũng sync từ GSO.

**Chốt (08/07/2026):** swap GADM 63 tỉnh cũ → **Free-GIS-Data 34 tỉnh** cho point-in-polygon ở §2.2.2. `declaredProvince` (§2.1) đổi dropdown 63 → **34 tỉnh mới** để đồng bộ taxonomy với nguồn polygon, tránh lệch tên gọi. **Limitation false-positive mismatch cũ BIẾN MẤT** — cross-check giờ chạy đúng địa giới hiện hành, không còn báo lệch oan ở khu vực vừa sáp nhập.

**Limitation còn lại (nhẹ hơn hẳn):** Free-GIS-Data là nguồn **cộng đồng** (sync GSO), không phải bản đồ pháp lý chính thức của Cục Đo đạc — dùng cho cross-check tương đối là đủ (cơ chế non-blocking, không cần chính xác pháp lý), nhưng nếu productize thật cần bản chính thức của Cục. Limitation thu từ **"không có data → phải dùng 63 tỉnh sai địa giới"** xuống **"có data đúng địa giới nhưng chưa có giá trị pháp lý chính thức"** — nhẹ hơn nhiều.

**Giá trị hiển thị cho buyer (tuỳ thuộc §4 quyết định cuối):** khi màn hình tìm kiếm/bản đồ hiển thị listing theo vị trí địa lý, `verificationLevel` là field đủ để tô màu marker khác nhau (`CADASTRAL_BACKED` nổi bật hơn `SELF_DECLARED`) — vừa cho buyer tín hiệu tin cậy thêm, vừa tạo động lực thật cho seller chủ động đi xin trích lục (tăng uy tín, không phải thủ tục bắt buộc). Field này copy nguyên vào `ProductPlot` (§2.4) và được trả trực tiếp trong response truy vấn listing của `product-service`, không cần gọi thêm service khác khi render map.

### 2.2.4 Geo-risk verification qua baseline rừng 2020 — ForTy/Natural Forests (mới 06/07/2026; cập nhật 08/07/2026 L2 — đổi từ tự tính NDVI sang baseline làm sẵn)

**Bối cảnh:** GEOMETRY/JTS validation (§2.5) và province cross-check (§2.2.2) chỉ bắt lỗi cú pháp/hình học và lỗi convert toạ độ — không bắt được trường hợp toạ độ hợp lệ 100% nhưng nằm trong vùng rừng thật (permanent limitation, §5). Đây là lớp bổ sung độc lập, không thay thế 2 lớp trên, nhắm đúng câu hỏi lõi của EUDR: "đất này có phải rừng tính đến 31/12/2020 không."

**Cơ chế (cập nhật 08/07/2026, L2 — đổi từ "tự tính NDVI + tự đặt ngưỡng" sang "query baseline rừng làm sẵn"):** ngay sau khi `PlotRegistryEntry` được tạo/update (qua `ImportPlotsFromKML`/`AddPlotManually`), hệ thống tự động (async, không chặn response cho seller) query **bản đồ rừng baseline 2020 làm sẵn cho đúng EUDR** theo toạ độ/polygon plot — **không** tự tính NDVI thô rồi tự đặt ngưỡng nữa. Hai nguồn mở, miễn phí:
- **ForTy 2020 (Forest Typology v1.0)** trong Google Earth Engine Data Catalog: phân 6 class, trong đó **Class 5 = Tree Crops & Agroforestry (cà phê, cao su, cây ăn quả) được phân loại thẳng là "agricultural land use under EUDR"**, tách khỏi class rừng tự nhiên. Trả về probability theo toạ độ — đúng bài toán "đất này là cây trồng hay rừng" mà không cần threshold thủ công.
- **Natural Forests 2020 baseline** (Nature Scientific Data, 10m resolution, **92% accuracy**, thiết kế thẳng cho EUDR due diligence): bản đồ xác suất rừng tự nhiên tại mốc 31/12/2020.

Rơi vào class rừng tự nhiên 2020 → `geoRiskLevel = HIGH_RISK`; rơi vào class cây trồng/nông nghiệp → `LOW_RISK`. Dữ liệu baseline dựng từ mosaic Sentinel-2 2020 đã có sẵn trong archive, không phải chờ vệ tinh chụp mới. **Không còn ngưỡng NDVI thủ công** — dùng nhãn rừng/không-rừng đã được peer-review làm sẵn tại đúng mốc 31/12/2020.

**`INCONCLUSIVE` — outcome thứ 3, mới 06/07/2026 (vẫn giữ sau L2):** baseline làm sẵn (ForTy/Natural Forests) dựng từ mosaic cloudless 2020 nên đã **giảm** phần nhiễu do mây so với tự tính NDVI thô — nhưng chưa xoá hẳn nguồn nhiễu thứ 2: nông dân Việt Nam phổ biến xen canh nhiều tầng (cà phê dưới tán sầu riêng, cao su tái canh xen cây ngắn ngày), khiến class rừng/cây-trồng chồng lấn ở ranh giới, baseline trả về probability không đủ dứt khoát. Khi probability không đủ tin cậy để phân loại nhị phân → `geoRiskLevel = INCONCLUSIVE`, khác `HIGH_RISK` (có bằng chứng rủi ro) và khác `UNVERIFIED` (chưa có câu trả lời gì — lỗi kỹ thuật/timeout, §2.2.4 dưới) — đây là **baseline đã trả lời, nhưng câu trả lời không đủ rõ để kết luận**.

**Không chặn giao dịch:** `HIGH_RISK` chỉ là nhãn cảnh báo hiển thị cho buyer lúc xem listing, không tự động từ chối/khoá plot. Đúng nguyên tắc nền tảng là neutral-party cung cấp minh bạch, không đóng vai tòa án — buyer tự quyết định có chấp nhận rủi ro hay không, cùng tinh thần đã áp cho `verificationLevel`/điểm uy tín reputation-service (hiển thị tín hiệu, không tự động cấm).

**Timeout — `geoVerificationTimeoutHours`** (chốt 6 giờ, `application.yml`, tinh chỉnh được): vì dữ liệu Sentinel-2 tại mốc 31/12/2020 đã tồn tại sẵn trong archive (không phải giám sát real-time), timeout ở đây chỉ để hứng lỗi tạm thời (mây che phải ghép nhiều ảnh lân cận, rate limit, lỗi mạng) — tính bằng **giờ**, khác hẳn các timeout khác trong hệ thống dựa trên hành vi người (`sellerResponseWindowDays`...) vốn tính bằng ngày. Hết `geoVerificationTimeoutHours` mà API chưa trả kết quả → `geoVerificationStatus = UNVERIFIED` (khác `HIGH_RISK` — đây là "chưa biết", không phải "có bằng chứng rủi ro").

**Chặn `CreateListing` khi đang `PENDING` (§4):** seller không thể chọn 1 `registryEntryId` đang `geoVerificationStatus = PENDING` để tạo listing — validate reject, yêu cầu chờ check xong (thường vài giờ, xem timeout ở trên). Đánh đổi: chậm listing đầu tiên vài giờ, đổi lấy loại bỏ hoàn toàn race condition listing lên sàn trước khi biết risk.

**Giới hạn chi phí/kỹ thuật (Known Limitation, xem thêm §5):**
- Dữ liệu Sentinel-2 miễn phí, không giới hạn dùng thương mại (chính sách dữ liệu Copernicus). Sentinel Hub (qua Copernicus Data Space Ecosystem) có free tier với quota Processing Unit hàng tháng — chi phí mỗi lần check tỷ lệ với diện tích polygon; plot hộ nông dân nhỏ hơn nhiều so với ví dụ đo lường của Copernicus (polygon cỡ thành phố ~34 PU), nên chi phí mỗi lần check không đáng kể. Giữ chi phí thấp bằng cách chỉ check **1 lần/`PlotRegistryEntry`** (không lặp lại mỗi lần tạo listing mới) — khớp đúng thiết kế đã tách khai báo đất khỏi tạo listing (§2.1).
- NDVI 10m resolution + ngưỡng nhị phân là proxy thô, **KHÔNG** phải xác minh EUDR pháp lý đầy đủ (dịch vụ thương mại chuyên dụng dùng layer mịn hơn kèm audit trail riêng) — không được trình bày với VICOFA/buyer như "đã verify EUDR", chỉ là tín hiệu tham khảo bổ sung, cùng nhóm với `verificationLevel` (§2.2.1).
- **Không còn tự đặt ngưỡng NDVI (L2, 08/07/2026 — điểm treo cũ đã GỠ):** trước đây phải tự tính NDVI rồi tự đặt threshold — khó defend ("số này ở đâu ra?") và dễ sai vì cà phê dưới tán và rừng tự nhiên có NDVI gần nhau. Nay dùng baseline ForTy/Natural Forests 2020 làm sẵn, đã phân loại cây-trồng vs rừng-tự-nhiên bằng nghiên cứu peer-review (92% accuracy) — vừa gỡ điểm treo "ngưỡng chưa chốt", vừa nâng cấp cơ chế từ "tự đặt ngưỡng chưa kiểm chứng" lên "baseline chuẩn EUDR".

**Luồng "yêu cầu đánh giá lại" bằng ảnh EXIF (mới, 06/07/2026 — đóng điểm treo trước đây):** không bắt buộc chụp ảnh lúc đăng ký plot (tránh over-engineer, bắt nông dân thao tác lằng nhằng — đúng nguyên tắc đã dùng xuyên suốt tài liệu này). Chỉ kích hoạt khi `geoRiskLevel = HIGH_RISK` hoặc `INCONCLUSIVE` **và** seller muốn khiếu nại:

- Use case mới `RequestGeoReevaluation` (§4) — seller mở app, chụp ảnh trực tiếp tại đúng khu đất qua camera app (không cho chọn ảnh có sẵn trong thư viện — chặn đường nộp ảnh chụp từ trước/chụp chỗ khác).
- `file-service` nhận ảnh, bóc tách EXIF — lấy toạ độ GPS lúc bấm máy và timestamp, 2 giá trị khó giả mạo hơn nếu không chủ đích can thiệp file.
- Đối chiếu toạ độ EXIF với polygon/point đã đăng ký ở `PlotRegistryEntry` (point-in-polygon, JTS — tái dùng thư viện đã có, không thêm dependency) — nằm trong ranh giới đã khai → cập nhật `geoVerificationStatus`/audit trail ghi nhận "đã xác minh bằng ảnh thực địa", hiển thị cho buyer cùng nhãn risk gốc (không xoá nhãn `HIGH_RISK`/`INCONCLUSIVE`, chỉ bổ sung bằng chứng đối chiếu — buyer tự quyết định).
- Toạ độ ngoài ranh giới đã khai → reject, không cập nhật gì, ghi nhận vào audit trail (chống seller chụp ảnh vườn hàng xóm/nơi khác nộp giả).

**Giới hạn của lớp EXIF này, đã thống nhất từ đầu — không đóng hoàn toàn:** vẫn là lớp phòng thủ yếu (GPS spoofing bằng app giả lập vị trí vẫn khả thi về mặt kỹ thuật), không phải bằng chứng tuyệt đối — chỉ đủ lọc case sơ hở (nộp nhầm/nộp bừa), đúng vai trò bổ sung tín hiệu chứ không thay thế xác minh EUDR pháp lý đầy đủ (xem giới hạn NDVI ở trên).

### 2.2 Nguồn nhập liệu — KML import là đường chính, pin thủ công là fallback hẹp

**Chốt (04/07/2026), phát hiện qua đối chiếu thực tế ngành (nguồn: cross-check độc lập giữa web search và NotebookLM podcast — 2 nguồn khác nhau ra cùng kết luận):** cán bộ địa chính, công ty xuất khẩu, hoặc NGO ở Việt Nam đã và đang đo GPS chuyên dụng cho đúng mục đích chuẩn bị EUDR, ra file KML/Excel toạ độ sẵn có. Bắt HTX tự đo lại từ đầu là làm lại việc đã có người làm.

- **`KML_IMPORT` (đường chính, cho cả `POINT` lẫn `POLYGON`):** use case `ImportPlotsFromKML` (§4) parse file KML, mỗi placemark → 1 `PlotRegistryEntry`. `householdLabel` lấy từ tên placemark trong file (`<name>` tag) — quy ước, không bắt HTX gõ tay lại.
- **`MANUAL_PIN` (fallback hẹp, chỉ cho `POINT`):** dùng `navigator.geolocation.getCurrentPosition()` (API trình duyệt có sẵn, không thêm dependency) khi không có file KML sẵn. **Chỉ cho phép `geometryType = POINT`** — cấm dùng cho `POLYGON`. Lý do cấm: vẽ polygon bằng cách thả ghim tay trên bản đồ mang sai số cấp mét, đủ để lệch sang ranh giới hộ hàng xóm hoặc rừng phòng hộ — rủi ro không đáng đánh đổi với chi phí xây UI vẽ polygon trong scope 5 tháng. Nếu plot cần `POLYGON` (>4ha) mà không có file KML → yêu cầu seller đi xin file KML thật, không có đường tắt nào khác.

### 2.3 Quy tắc `geometryType` (theo ngưỡng EUDR) — không đổi

| Diện tích plot | `geometryType` | Format |
|---|---|---|
| ≤ 4 hecta | `POINT` | 1 toạ độ lat/long, 6 số thập phân |
| > 4 hecta | `POLYGON` | GeoJSON polygon, các điểm khép kín viền plot, 6 số thập phân mỗi điểm |

Áp dụng theo từng plot riêng lẻ, không phải theo tổng diện tích cả listing.

### 2.3b Global overlap check + yield anomaly — chống tái sử dụng tọa độ CHÉO seller (mới, 18/07/2026)

**Gap:** mọi lớp hiện có đều per-seller — `UNIQUE(sellerId, householdLabel)` không bắt Seller B đăng ký lại đúng polygon X của Seller A; province check và baseline rừng cũng pass vì tọa độ hoàn toàn hợp lệ. Đây là attack rẻ nhất để "mượn" đất sạch EUDR của người khác.

**Check khi tạo/update `PlotRegistryEntry`:**
1. Spatial index (GEOMETRY index sẵn có) lấy candidate có bounding box giao nhau, loại chính entry hiện tại.
2. Với candidate `POLYGON`: tính exact geometry hash (bắt copy nguyên xi), diện tích giao, **IoU = giao/hợp** VÀ **coverage ratio = giao/min(diện tích 2 plot)** — coverage ratio bắt case plot nhỏ nằm lọt trong plot lớn mà IoU vẫn thấp; chỉ IoU là lọt lưới.
3. Với `POINT` (không có IoU): khoảng cách toạ độ < epsilon + `areaHectares` xấp xỉ + owner khác.
4. Owner khác và overlap vượt policy (config `plotOverlapIoUThreshold`/`plotOverlapCoverageThreshold`, `application.yml`) → set `plotReuseRisk = HIGH` → **đẩy OPERATOR review, KHÔNG auto-reject và không tự tuyên bố fraud** — overlap hợp pháp có thật: cùng hộ thuộc 2 HTX, thuê/chuyển quyền sử dụng đất, đo lại lệch nhẹ, polygon cadastral vs GPS không trùng khít. Human-in-the-loop, cùng nguyên tắc evidence review.

**Yield anomaly — signal, không phải gate:** `declaredQuantity ≤ Σ(plotArea × maxYield)` với `maxYield` config theo commodity/vùng — vượt → `yieldRisk = SUSPICIOUS|EXTREME`, yêu cầu seller bổ sung bằng chứng hoặc OPERATOR review. **Không hard-reject**: hard-code "x tấn/ha" từ 1 nguồn chung tự tạo false positive cho vườn năng suất cao/nhiều vụ; khi chưa có dữ liệu yield đáng tin theo giống/vùng/mùa vụ thì đây chỉ được phép là anomaly signal.

**Naming EUDR output (18/07/2026, đồng bộ governance §6b):** mọi chỗ export gọi là **"EUDR evidence package / DDS-supporting export"** — platform cung cấp traceability data cho due diligence, KHÔNG claim output là Due Diligence Statement hoàn chỉnh (risk assessment/mitigation/submission là nghĩa vụ của operator/trader tại EU).


### 2.4 `ProductPlot` (cập nhật, 04/07/2026) — snapshot copy từ `PlotRegistryEntry`, không phải nhập trực tiếp

**Chốt (04/07/2026):** `ProductPlot` **giữ nguyên** vai trò VO list bên trong `Product` aggregate (không tách aggregate — lý do gốc không đổi, xem §2.6). Điểm đổi: dữ liệu của nó giờ **copy từ `PlotRegistryEntry`** tại thời điểm `CreateListing`, không phải nhập tay/API nhận trực tiếp `geoJson` nữa.

Snapshot, không phải tham chiếu sống — cùng nguyên tắc đã dùng xuyên suốt hệ thống (`agreedPrice`, `milestoneSchedule` snapshot lúc `sign()`; `level2InspectorOrg` snapshot lúc `sign()`): nếu seller sau này sửa lại `PlotRegistryEntry` gốc (đo lại chính xác hơn, hộ đổi ranh giới), listing cũ **không** tự đổi theo — đúng bản chất "khai báo tại đúng thời điểm tạo listing", không phải link động.

| Field | Loại | Ghi chú |
|---|---|---|
| `plotId` | UUID | Định danh nội bộ trong `Product` |
| `sourceRegistryEntryId` | UUID, nullable | Tham chiếu tới `PlotRegistryEntry` đã copy từ đó — cùng DB (`product-service`), REFERENCES hợp lệ (không cross-service). `NULL` chỉ cho trường hợp hiếm cần nhập tay ngoài registry (không khuyến khích, không phải luồng chính). |
| `householdLabel` | String | Copy tại thời điểm tạo listing |
| `geometryType` | Enum | Copy |
| `geoJson` | **GEOMETRY, SRID 4326** | Copy |
| `areaHectares` | BigDecimal | Copy |
| `verificationLevel` | Enum | Copy — snapshot đúng trạng thái lúc tạo listing, không đổi theo nếu registry gốc sau này upload thêm trích lục |
| `cadastralExtractFileId` | UUID, nullable | Copy |
| `declaredAt` | Timestamp | Thời điểm tạo listing (không phải thời điểm đo GPS gốc — đó là `PlotRegistryEntry.importedAt`) |

### 2.5 GeoJSON storage — `GEOMETRY` (SRID 4326), không phải `TEXT` (chốt 04/07/2026, session riêng)

**Vấn đề:** lưu `TEXT` không chặn được gì — sai cú pháp, ring hở, polygon tự cắt đều insert được vô tư, tới lúc dùng mới lộ lỗi.

**Đã verify (không suy đoán):** MySQL 8 tách 2 khái niệm — *syntactically well-formed* (tự động check lúc INSERT: ring phải khép kín, đủ điểm tối thiểu) và *geometrically valid* (polygon không tự cắt chính nó — **KHÔNG** tự động check, phải gọi `ST_IsValid()` tay, theo đúng doc MySQL: *"Due to the computational expense, MySQL does not check explicitly for geometric validity"*). Thêm 1 giới hạn quan trọng: `ST_IsValid()`/`ST_Validate()` chỉ chạy được trên SRID=0 (Cartesian phẳng), **lỗi thẳng** (`ER_WRONG_ARGUMENTS`) trên SRID=4326 (toạ độ GPS thật) — xác nhận còn đúng tới bản 8.4.5.

**Chốt kiến trúc — chia trách nhiệm 2 tầng, không đổ hết cho DB:**
- **DB (`GEOMETRY`, SRID 4326):** free, tự động — chặn sai cú pháp, ring không khép kín, thiếu điểm. Không cần code thêm.
- **Application layer (JTS, thư viện lõi Hibernate Spatial vốn đã có sẵn trong dependency tree, không thêm lib mới):** gọi `Geometry.isValid()` **trong RAM, trước khi persist**, ở use case `ImportPlotsFromKML`/`AddPlotManually` — bypass hoàn toàn giới hạn SRID=0 của MySQL vì JTS không quan tâm SRID của DB. Đây là nơi duy nhất bắt được self-intersection.
- **`ST_GeomFromGeoJSON()`** (MySQL 8 built-in) parse thẳng chuỗi GeoJSON từ API/KML-parse ra geometry, không cần tự convert qua WKT tay.

**Giới hạn không đổi, permanent, không công nghệ nào đóng được (giữ nguyên từ bản gốc):** dù DB + JTS bắt hết lỗi cú pháp/hình học, **không có cách nào chặn seller khai 1 plot hợp lệ 100% về hình học nhưng không có thật/không phải đất của họ** — đây là giới hạn của mô hình self-declared, trusted-operator, không phải thiếu sót của lần cập nhật này.

### 2.6 `Product` aggregate — không đổi cho geolocation, có 1 field mới không liên quan (xem §8)

Vẫn giữ quan hệ 1-N với `ProductPlot`, không đổi field nào hiện có, không đổi state machine hiện tại của `Product` — **cho phần geolocation/EUDR của tài liệu này.** Có 1 field mới (`varietyName`) phát sinh từ nhu cầu khác hẳn (match giá tham khảo bên pricing-service), không liên quan gì tới plot/toạ độ — xem §8, tách riêng để không làm loãng scope geolocation của tài liệu này. `ProductPlot` không tách aggregate riêng — không state machine, không lifecycle độc lập, đúng nguyên tắc Vernon đã áp từ bản gốc.

---

## 3. Database Migration

Additive — không sửa bảng `product` gốc:

```sql
CREATE TABLE plot_registry_entry (
    registry_entry_id  CHAR(36) PRIMARY KEY,
    seller_id           CHAR(36) NOT NULL,          -- plain UUID lưu bằng CHAR(36), KHÔNG REFERENCES user_db (cross-service)
    household_label     VARCHAR(255) NOT NULL,
    geometry_type       VARCHAR(20) NOT NULL,   -- POINT | POLYGON
    geo_json            GEOMETRY NOT NULL SRID 4326,
    area_hectares       DECIMAL(10,4) NOT NULL,
    source              VARCHAR(20) NOT NULL,   -- KML_IMPORT | MANUAL_PIN
    accuracy_meters     DECIMAL(6,2) NULL,      -- chỉ MANUAL_PIN, từ coords.accuracy — cảnh báo UI, không phải constraint
    verification_level  VARCHAR(20) NOT NULL DEFAULT 'SELF_DECLARED',  -- SELF_DECLARED | CADASTRAL_BACKED
    cadastral_extract_file_id CHAR(36) NULL,        -- reference file-service, NULL nếu SELF_DECLARED
    original_kml_file_id CHAR(36) NULL,             -- reference file-service, NULL nếu MANUAL_PIN. Field phẳng, lặp giá trị theo batch (§2.1)
    declared_province    VARCHAR(50) NOT NULL,  -- seller tự chọn dropdown, 34 tỉnh (NQ 202/2025) — nguồn độc lập cho cross-check (§2.2.2), L1 08/07/2026
    province_mismatch_flag BOOLEAN NOT NULL DEFAULT FALSE,  -- TRUE nếu point-in-polygon suy ra tỉnh khác declared_province — non-blocking
    imported_at         TIMESTAMP NOT NULL,     -- cập nhật lại mỗi lần re-import/update, không chỉ lần đầu
    geo_verification_status VARCHAR(20) NOT NULL DEFAULT 'PENDING',  -- PENDING | CHECKED | UNVERIFIED (§2.2.4)
    geo_risk_level      VARCHAR(20) NULL,       -- LOW_RISK | HIGH_RISK, chỉ có giá trị khi geo_verification_status = CHECKED (§2.2.4)
    UNIQUE (seller_id, household_label),
    SPATIAL INDEX (geo_json)
);
CREATE INDEX idx_plot_registry_seller ON plot_registry_entry(seller_id);
-- province_mismatch_flag KHÔNG chặn INSERT/UPDATE — set song song, cảnh báo UI, xem §2.2.2.
-- Invariant "source = MANUAL_PIN → geometry_type phải = POINT" check ở use-case layer,
-- không phải DB constraint — cùng pattern active-flag đã dùng ở product-service.
-- UNIQUE(seller_id, household_label) chính là cơ chế match re-import/update ở §4 —
-- INSERT ... ON DUPLICATE KEY UPDATE, không cần query tìm rồi update tay.

CREATE TABLE product_plot (
    plot_id                    CHAR(36) PRIMARY KEY,
    product_id                 CHAR(36) NOT NULL REFERENCES product(product_id),
    source_registry_entry_id   CHAR(36) NULL REFERENCES plot_registry_entry(registry_entry_id),
                                                  -- cùng DB product-service, REFERENCES hợp lệ.
                                                  -- NULL = hiếm, nhập tay ngoài registry, không phải luồng chính.
    household_label            VARCHAR(255) NOT NULL,
    geometry_type               VARCHAR(20) NOT NULL,
    geo_json                    GEOMETRY NOT NULL SRID 4326,   -- đổi từ TEXT
    area_hectares                DECIMAL(10,4) NOT NULL,
    verification_level           VARCHAR(20) NOT NULL DEFAULT 'SELF_DECLARED',  -- copy từ registry lúc snapshot
    cadastral_extract_file_id    CHAR(36) NULL,                                     -- copy từ registry lúc snapshot
    declared_at                  TIMESTAMP NOT NULL,
    SPATIAL INDEX (geo_json)
);
CREATE INDEX idx_product_plot_product_id ON product_plot(product_id);
```

Không có `UPDATE`/migration nào chạy trên data cũ — bảng mới, rỗng, không ảnh hưởng listing đã tồn tại.

---

## 4. Use Case Changes

- **`ImportPlotsFromKML(sellerId, kmlFile, declaredProvince)`** (mới, cập nhật 05/07/2026 — thêm `declaredProvince`) — parse KML, mỗi placemark → 1 `PlotRegistryEntry`.
  - **Matching cho re-import (04/07/2026):** match theo cặp `(sellerId, householdLabel)` — placemark trùng `householdLabel` với entry đã có của đúng seller đó → **UPDATE** entry hiện có (coi như đo lại chính xác hơn/hộ đổi ranh giới), không tạo bản trùng. Không trùng → tạo mới (`source = KML_IMPORT`). `ProductPlot` đã snapshot cho listing cũ không đổi theo (§2.4) — 2 việc tách biệt đúng như đã chốt.
  - **`areaHectares` khi placemark là `Point` (04/07/2026):** hình học điểm không có diện tích để tính (0 chiều) — khác `Polygon` (tự tính trực tiếp từ hình học bằng `JTS Polygon.getArea()`, cùng lib đã dùng để validate, không cần thêm gì). Ưu tiên đọc từ `<ExtendedData>` của placemark nếu KML có field diện tích theo quy ước đặt tên (tài liệu hướng dẫn seller ghi rõ tên field cần có, ví dụ `dien_tich`/`area_ha`). Không có field đó trong KML → bắt buộc seller nhập tay 1 số lúc import (không mặc định `NULL`/`0` — `0` gây hiểu nhầm "diện tích bằng 0" thay vì "chưa có dữ liệu").
  - Với mỗi placemark: xác định `geometryType` theo `areaHectares` (§2.3), convert toạ độ sang GeoJSON, chạy JTS `.isValid()` trước khi `INSERT`/`UPDATE` (§2.5) — placemark nào fail validation báo lỗi rõ tên đó, không chặn cả file, cho seller sửa/bỏ qua từng cái.
  - **`declaredProvince` + province cross-check (mới, 05/07/2026):** seller chọn 1 tỉnh (dropdown, danh sách 34 tỉnh, §2.2.3) cho cả file — áp dụng chung cho mọi placemark trong lần import đó, lưu vào `declaredProvince` của từng `PlotRegistryEntry` tạo/update ra. Sau khi parse toạ độ mỗi placemark, chạy point-in-polygon (JTS) với polygon Free-GIS-Data 34 tỉnh (§2.2.3) suy ra tỉnh thực tế — lệch với `declaredProvince` thì set `provinceMismatchFlag = TRUE` cho đúng entry đó, không chặn insert (§2.2.2).
  - **`originalKmlFileId` (mới, 05/07/2026):** file KML upload lưu qua `file-service` trước, `fileId` trả về gán vào `originalKmlFileId` của mọi `PlotRegistryEntry` tạo/update trong lần import này — cùng giá trị lặp lại cho cả batch (§2.1).
- **`AddPlotManually(sellerId, householdLabel, lat, long, accuracyMeters, declaredProvince)`** (mới, cập nhật 05/07/2026 — thêm `declaredProvince`) — fallback, tạo/update `PlotRegistryEntry` (cùng matching rule theo `householdLabel` như KML) với `geometryType = POINT`, `source = MANUAL_PIN`. Không nhận polygon qua đường này (§2.2). `originalKmlFileId` luôn `NULL` (không qua KML). Cùng cơ chế province cross-check như trên (§2.2.2) — áp dụng cho từng entry đơn lẻ thay vì cả batch.
  - **Kiểm tra độ chính xác (04/07/2026):** `accuracyMeters` lấy trực tiếp từ `coords.accuracy` của Geolocation API trình duyệt (bán kính sai số 1-độ-lệch-chuẩn tính bằng mét, trả kèm toạ độ, không cần tính thêm) — lưu lại, không bỏ qua. Nếu `accuracyMeters > accuracyWarningThresholdMeters` (chốt 20m, `application.yml` — 1 cạnh plot 4ha điển hình ~200m, sai số 20m vẫn dùng được nhưng đáng cảnh báo), UI hiển thị cảnh báo trước khi seller xác nhận lưu ("độ chính xác thấp, thử lại ở nơi thoáng hoặc dùng file KML"). Không chặn cứng — thiết bị rẻ/trong nhà kính có thể không bao giờ đạt ngưỡng tốt hơn, chặn cứng loại bỏ hẳn 1 nhóm user thay vì chỉ cảnh báo.
- **`CreateListing`:** đổi input — nhận `List<registryEntryId>` (chọn từ danh sách plot đã có sẵn trong registry của seller, UI dạng checkbox) thay vì `List<ProductPlotRequest>` với `geoJson` đầy đủ. Use case tự copy dữ liệu từ `PlotRegistryEntry` đã chọn sang `ProductPlot` mới (snapshot, §2.4). Validate: `registryEntryId` phải thuộc đúng `sellerId` đang tạo listing, ít nhất 1 plot được chọn (giữ nguyên yêu cầu ≥1 plot từ bản gốc).
  - **Commodity-gate (mới, 08/07/2026 — C3):** yêu cầu ≥1 plot **chỉ áp dụng cho commodity thuộc EUDR** (cà phê, cao su). Với gạo/điều (không thuộc EUDR, xem §1), toàn bộ tầng geolocation là **optional** — seller không bị bắt buộc khai plot, `CreateListing` không reject vì thiếu plot. Gate đọc `Category.commodity` (enum cứng `COFFEE/RICE/RUBBER/CASHEW`, field trên aggregate `Category` Phase 1 — admin gán lúc `approve()`, xem đồng bộ ở `milestone-escrow-phase2-design.md` §7.2) của category gắn với product: `COFFEE`/`RUBBER` → bật geolocation bắt buộc; `RICE`/`CASHEW` → bỏ qua tầng này. Không hardcode "luôn cần plot" — nếu để nguyên yêu cầu ≥1 plot cho mọi commodity thì gạo/điều bị ép khai plot vô nghĩa, đúng bug scope báo cáo rà soát nêu.
  - **Validate mới (06/07/2026):** reject nếu bất kỳ `registryEntryId` nào đang `geoVerificationStatus = PENDING` (§2.2.4) — tránh race condition listing lên sàn trước khi biết kết quả satellite check. Không áp dụng cho `CHECKED` (dù `HIGH_RISK`) hay `UNVERIFIED` — cả hai đều được coi là "đã có câu trả lời" (dù câu trả lời là rủi ro cao hay không xác minh được), chỉ `PENDING` (chưa có câu trả lời gì) mới bị chặn. **Chỉ áp dụng khi commodity thuộc EUDR** (gạo/điều không có bước satellite check nên không có state `PENDING` để chặn).
- **`UploadCadastralExtract(sellerId, registryEntryId, extractFile)`** (mới, 04/07/2026) — seller upload file scan/PDF trích lục bản đồ địa chính cho 1 entry đã có sẵn (không tạo entry mới). Nhận `JPG`/`PNG`/`PDF`, cùng giới hạn dung lượng đã áp cho evidence file khác trong hệ thống (`sellerEvidenceFileId`...) — không thêm constraint riêng. Lưu qua `file-service` → `cadastralExtractFileId`, set `verificationLevel = CADASTRAL_BACKED`.
  - **Giới hạn thật, không phải chi tiết vặt (04/07/2026):** platform **không đối chiếu** nội dung file (số thửa, tờ bản đồ ghi trong trích lục) với `geoJson` đã khai cho đúng entry đó — không có OCR, không tra hệ thống địa chính thật để xác nhận khớp. Seller về lý thuyết có thể upload trích lục của 1 thửa đất bất kỳ, không liên quan gì tới toạ độ đã nhập, vẫn được gắn `CADASTRAL_BACKED`. Đây là giới hạn cùng nhóm với "self-declared" đã nêu ở §2.2.1 — file chỉ là bằng chứng đính kèm, không phải cơ chế xác minh khớp tự động.
- **`RequestGeoReevaluation(sellerId, registryEntryId, photoFile)`** (mới, 06/07/2026) — seller khiếu nại khi `geoRiskLevel = HIGH_RISK`/`INCONCLUSIVE` (§2.2.4). Chỉ nhận ảnh chụp trực tiếp qua camera app (chặn chọn ảnh có sẵn trong thư viện máy). Lưu qua `file-service` → bóc EXIF (GPS + timestamp) → point-in-polygon đối chiếu với `geoJson` đã đăng ký của đúng `registryEntryId` đó.
  - **Trong ranh giới:** ghi audit trail "đã xác minh bằng ảnh thực địa" kèm timestamp, không xoá `geoRiskLevel` gốc — chỉ bổ sung bằng chứng đối chiếu hiển thị cùng nhãn cho buyer.
  - **Ngoài ranh giới:** reject, không cập nhật gì, ghi nhận audit trail (chống nộp ảnh chụp nơi khác/vườn hàng xóm).
  - **Giới hạn (06/07/2026):** không giải quyết được GPS spoofing bằng app giả lập vị trí — lớp phòng thủ bổ sung, không phải bằng chứng tuyệt đối (§2.2.4).
- **`UpdateListing`:** cho phép đổi tập `registryEntryId` được chọn trước khi listing chuyển khỏi trạng thái cho phép sửa (theo state machine `Product` hiện có, không đổi) — thêm/bớt plot bằng cách tick/untick, không sửa trực tiếp `geoJson` của `ProductPlot` đã snapshot.
- **`GetListing`/`SearchListing`:** không đổi logic.

Không đụng tới `contract-service`, `escrow-service`, hay bất kỳ use case nào của Milestone Escrow.

---

## 5. EUDR Compliance Notes

- **Không mass balance:** listing phải khai đủ toàn bộ plot đóng góp, không được chọn 1 plot đại diện. Validate ở tầng use case: `List<registryEntryId>` không được rỗng.
- **Không cần tỷ lệ đóng góp:** EUDR không đòi biết chính xác bao nhiêu kg đến từ hộ nào — chỉ cần khai đủ danh sách plot, miễn không plot nào bị phá rừng sau 31/12/2020. Nên `ProductPlot`/`PlotRegistryEntry` **không có** field khối lượng/tỷ lệ đóng góp.
- **Dữ liệu self-declared:** platform không có khả năng verify vệ tinh/thực địa (ngoài scope 5 tháng/3 người). `declaredAt`/`importedAt` + hash chain (`services.md` mục 5) là cơ chế chống sửa sau khi khai, **không phải** cơ chế xác minh tính đúng đắn ban đầu — và GEOMETRY/JTS validation (§2.5) cũng vậy: chặn sai cú pháp/hình học, không chặn "khai đất giả nhưng hợp lệ". Giữ nhất quán với lập luận "trusted operator, không phải trustless".
- **Verify thêm (04/07/2026):** validation hiện tại (JTS `.isValid()` + bounding box Việt Nam) không phát hiện được toạ độ bị convert sai kinh tuyến trục VN2000 sang WGS84 (ví dụ nhầm 108°30' của Đắk Lắk thành 107°45' của Lâm Đồng — 2 tỉnh liền kề, lỗi nhập nhầm tham số thực tế xảy ra khi thiết bị đo/phần mềm export cấu hình sai tỉnh) — kiểm chứng bằng tính toán thật (pyproj, điểm mẫu Đắk Lắk): sai lệch **81.37km**, kết quả vẫn là toạ độ hợp lệ 100% về hình học và vẫn nằm gọn trong bounding box Việt Nam, nên không có tầng validation nào ở §2.5 bắt được — cùng nhóm giới hạn permanent với "khai đất giả nhưng hợp lệ hình học" đã nêu ở trên, không giải bằng code thêm.
- **Mitigation cho lỗi VN2000/WGS84 — province cross-check (mới, 05/07/2026, xem §2.2.2/§2.2.3):** không đóng được permanent limitation ở trên, nhưng giảm nhẹ trường hợp lỗi *vô ý* (config sai tỉnh) bằng cách đối chiếu `declaredProvince` (seller tự khai, nguồn độc lập) với tỉnh suy ra từ toạ độ qua point-in-polygon. Không bắt được nếu lỗi convert là hệ thống (cả batch cùng lệch theo cùng 1 hướng, nhất quán nội bộ) và không bắt được fraud có chủ đích — chỉ bắt được khi toạ độ đơn lẻ lệch khỏi tỉnh seller khai. Dùng polygon Free-GIS-Data 34 tỉnh (§2.2.3, cập nhật 08/07/2026 — đã có nguồn mở đúng địa giới hiện hành, gỡ false-positive cũ) — nguồn cộng đồng sync GSO, đủ cho cross-check tương đối non-blocking, chưa phải bản đồ pháp lý chính thức của Cục.
- **Geo-risk verification qua baseline rừng 2020 — ForTy/Natural Forests (mới 06/07/2026; cập nhật 08/07/2026 L2, xem §2.2.4):** lớp bổ sung độc lập, nhắm đúng câu hỏi "đất có phải rừng tính đến 31/12/2020 không" mà JTS/province cross-check không trả lời được. Không chặn giao dịch — chỉ dán nhãn `HIGH_RISK`/`LOW_RISK` cho buyer tự quyết định. Baseline 10m 92% accuracy vẫn là proxy tham khảo, không phải xác minh EUDR pháp lý đầy đủ (dịch vụ thương mại chuyên dụng dùng layer mịn hơn + audit trail riêng) — xem giới hạn chi tiết ở §2.2.4.

---

## 6. Out of Scope (có chủ đích, không phải thiếu sót)

- **Luồng export sang `audit-service` để tạo báo cáo EUDR** — để dành cho session hash chain (đã chốt: Feign, không phải read model riêng — xem `hash-chain-phase2-design.md` §2.3).
- **Vẽ polygon tay trong app** — cấm hẳn (§2.2), không phải "chưa làm tới". Rủi ro sai số không đáng đánh đổi trong scope đồ án.
- **Xác minh KML có đúng là do "cán bộ địa chính/NGO thật" đo hay không** — platform tin nội dung file KML seller upload, không xác minh nguồn gốc file. Cùng giới hạn "self-declared, trusted operator" đã nêu ở §5.
- **Đòn bẩy kinh tế thay thế cho reputation lockout ở năm đầu vận hành** — đã verify (04/07/2026, kết quả và lý do đầy đủ ở `reputation-service-phase2-design.md` §9): điều lệ VICOFA hiện không hỗ trợ cơ chế này. Không lặp lại chi tiết ở đây — đây là câu hỏi thuộc phạm vi enforcement/reputation, không phải geolocation.
- **EXIF photo cross-check (mới, 06/07/2026)** — đã spec đầy đủ thành use case `RequestGeoReevaluation` (§2.2.4, §4), không còn deferred. Vẫn giữ nguyên giới hạn đã thống nhất từ đầu: lớp phòng thủ yếu, không giải quyết được GPS spoofing.

---

## 7. Status — Product Phase 2 (Farm Plot Geolocation)

**Chốt (02/07/2026):** `ProductPlot` là nested VO list trong `Product` aggregate, không tách aggregate riêng. `geometryType` quyết theo ngưỡng 4ha/plot. Migration additive, không đụng data/logic Phase 1 đã test.

**Chốt bổ sung (04/07/2026):**
- **`geo_json`: `TEXT` → `GEOMETRY` (SRID 4326)** ở cả `product_plot` và `plot_registry_entry` — DB tự chặn sai cú pháp/ring hở; self-intersection chặn ở application layer qua JTS `.isValid()` (bypass giới hạn `ST_IsValid()` chỉ chạy SRID=0 của MySQL). Giới hạn "khai đất giả nhưng hợp lệ hình học" vẫn permanent, không đổi.
- **`PlotRegistryEntry` (mới)** — tách khai báo đất (1 lần, thuộc seller) khỏi tạo listing (nhiều lần) — giải quyết friction "gõ lại toạ độ mỗi mùa vụ cho hàng trăm hộ", phát hiện qua đối chiếu độc lập giữa web search và NotebookLM podcast review.
- **Re-import/update qua `UNIQUE(sellerId, householdLabel)`** — KML import lại hoặc `AddPlotManually` cho cùng hộ tự update entry hiện có (INSERT ON DUPLICATE KEY UPDATE), không tạo bản trùng, không mồ côi entry cũ. `ProductPlot` đã snapshot cho listing cũ không đổi theo — 2 việc tách biệt.
- **`accuracyMeters` cho `MANUAL_PIN`** — lấy từ `coords.accuracy` của Geolocation API, cảnh báo UI nếu vượt ngưỡng (chốt 20m), không chặn cứng.
- **`areaHectares` cho `Point` placemark trong KML** — ưu tiên đọc từ `ExtendedData` nếu KML có, không có thì bắt buộc seller nhập tay lúc import (không mặc định NULL/0).
- **`verificationLevel` (mới, 04/07/2026)** — `SELF_DECLARED` mặc định / `CADASTRAL_BACKED` nếu seller upload "trích lục bản đồ địa chính" (xin từ UBND xã/Văn phòng đăng ký đất đai, quy trình nhà nước có sẵn, không cần AgriContract làm gì để nó tồn tại). **Không** phải bằng chứng sở hữu đầy đủ (đó là vai trò của sổ đỏ, khác văn bản) — chỉ giảm nhẹ giới hạn "khai đất giả nhưng hợp lệ hình học", không đóng hẳn. Copy xuyên suốt `PlotRegistryEntry` → `ProductPlot`; response truy vấn listing của `product-service` trả field này để map hiển thị marker khác màu cho buyer.
- **KML import là đường nhập liệu chính** (`ImportPlotsFromKML`), khớp thực tế cán bộ địa chính/công ty xuất khẩu/NGO đã đo GPS sẵn cho EUDR. Pin thủ công (`AddPlotManually`) chỉ là fallback, giới hạn cứng ở `POINT`, cấm dùng cho `POLYGON`.
- **`CreateListing`/`UpdateListing` đổi input** — chọn từ registry (checkbox) thay vì nhập/gửi `geoJson` trực tiếp mỗi lần. `ProductPlot` giờ là snapshot copy từ `PlotRegistryEntry`, cùng nguyên tắc snapshot đã dùng cho `agreedPrice`/`milestoneSchedule`/`level2InspectorOrg`.

**Chốt bổ sung (05/07/2026) — dependency file-service + mitigation VN2000/WGS84:**
- **`originalKmlFileId` (mới)** — giữ lại file KML gốc qua `file-service`, field phẳng trên `PlotRegistryEntry`, lặp giá trị theo batch import. Không tách `PlotImportBatch` riêng — không có consumer nào cần query ở tầng batch (log lỗi fail lúc import không ai đọc lại; traceability đã đủ qua cơ chế UPDATE-tại-chỗ khi re-import). Mục đích là provenance, không phải re-verify toạ độ — đã xác nhận file gốc không giúp phát hiện lỗi VN2000/WGS84 vì chỉ chứa số đã convert xong.
- **`declaredProvince` + `provinceMismatchFlag` (mới)** — cross-check độc lập cho lỗi convert VN2000→WGS84 (permanent limitation đã nêu ở §5, không đóng được bằng validate cú pháp/hình học). Seller khai tỉnh (dropdown, độc lập với toạ độ) đối chiếu với tỉnh suy ra từ point-in-polygon. Non-blocking, chỉ cảnh báo. Không bắt được lỗi hệ thống (cả batch cùng lệch nhất quán) hay fraud có chủ đích — chỉ giảm nhẹ lỗi vô ý do config sai.
- **Nguồn polygon ranh giới — Free-GIS-Data 34 tỉnh** (cập nhật 08/07/2026, L1 — trước đây GADM 63 tỉnh cũ, nay đã có structured data 34 tỉnh mở sync GSO, xem §2.2.3). False-positive mismatch ở vùng vừa sáp nhập (limitation cũ) đã **gỡ**. Còn lại: nguồn cộng đồng, chưa phải bản đồ pháp lý chính thức của Cục Đo đạc — đủ cho cross-check non-blocking, cần bản chính thức nếu productize.

**Chốt bổ sung (06/07/2026) — geo-risk verification qua vệ tinh:**
- **`geoVerificationStatus`/`geoRiskLevel` (mới)** — tách trạng thái tiến trình check (`PENDING`/`CHECKED`/`UNVERIFIED`) khỏi kết quả rủi ro thật (`LOW_RISK`/`HIGH_RISK`/`INCONCLUSIVE`), 2 field riêng biệt để tránh gộp nhầm "chưa biết" với "có bằng chứng rủi ro" (§2.1, §2.2.4).
- **`INCONCLUSIVE` (mới)** — outcome thứ 3 cho case mây che/xen canh nhiều tầng làm NDVI đọc nhiễu (cà phê dưới tán sầu riêng, cao su tái canh) — khác `UNVERIFIED` (API đã trả lời nhưng không đủ tin cậy để phân loại, chứ không phải lỗi kỹ thuật/timeout).
- **Baseline rừng 2020 check — ForTy (GEE Data Catalog) / Natural Forests (cập nhật 08/07/2026 L2, thay Sentinel Hub NDVI tự tính)** — async, chạy 1 lần/`PlotRegistryEntry` ngay sau tạo/update, không lặp lại mỗi lần tạo listing. Nguồn mở miễn phí, không giới hạn thương mại (Copernicus/GEE). Không chặn giao dịch — chỉ dán nhãn cho buyer tự quyết định (§2.2.4).
- **`geoVerificationTimeoutHours`** (chốt 6 giờ, `application.yml`) — timeout tính bằng giờ chứ không phải ngày, vì dữ liệu Sentinel-2 tại mốc 31/12/2020 đã có sẵn trong archive (từ 2015), timeout chỉ hứng lỗi tạm thời (mây che, rate limit), không phải chờ dữ liệu tồn tại (§2.2.4).
- **`CreateListing` chặn khi `PENDING`** — tránh race condition listing lên sàn trước khi có kết quả check (§4).
- **Đã đóng điểm treo EXIF** — spec đầy đủ thành `RequestGeoReevaluation` (§2.2.4, §4): chỉ kích hoạt khi bị gắn `HIGH_RISK`/`INCONCLUSIVE`, không bắt buộc lúc đăng ký plot; chụp qua camera app, đối chiếu EXIF GPS với polygon đã khai.

**Các điểm treo đã đóng hết (13/07/2026):** ngưỡng NDVI đã **GỠ** nhờ chuyển sang baseline ForTy/Natural Forests làm sẵn (L2, §2.2.4). `geoVerificationTimeoutHours` chốt **6 giờ** (`application.yml`), tinh chỉnh được khi có dữ liệu vận hành thật (§2.2.4). Không còn điểm treo số liệu.

**Cập nhật (08/07/2026) — rà soát end-to-end, 2 điểm định vị (không đụng code lõi geolocation):**
- **C3 — geolocation là conditional theo commodity:** EUDR chỉ áp cà phê/cao su, không áp gạo/điều — thêm commodity-gate ở `CreateListing` (§4), gạo/điều không bị bắt buộc khai plot. Field geo vốn đã nullable, đây là làm rõ + kiểm tra khi implement để tránh bug scope (ép gạo/điều khai plot vô nghĩa). Framing: "module EUDR bật/tắt theo commodity", không phải over-invest (§1).
- **C2 — quan hệ với CSDL vùng trồng nhà nước (Bộ NN&PTNT + IDH + VICOFA):** AgriContract không cạnh tranh tầng traceability — ở tầng contract/escrow/dispute. Geolocation thiết kế để liên thông/tiêu thụ CSDL nhà nước (nguồn `CADASTRAL_BACKED` lý tưởng) thay vì tự dựng, không thay thế nó (§1). **Known Limitation:** cơ chế liên thông cụ thể (API, format, quyền truy cập) không xác nhận được với Bộ trong phạm vi đồ án — thiết kế giữ ở mức "sẵn sàng tiêu thụ", không claim đã tích hợp. Defend hội đồng: nắm bối cảnh hạ tầng nhà nước, thiết kế để liên thông khi có điều kiện, không tự dựng lại tầng dữ liệu.

**Cập nhật (08/07/2026) — L1/L2 từ báo cáo rà soát Limitations, gỡ 2 giới hạn:**
- **L1 — polygon ranh giới tỉnh:** đổi GADM 63 tỉnh cũ → **Free-GIS-Data 34 tỉnh** (structured data mở, sync GSO, đúng địa giới hiện hành NQ 202/2025). Gỡ hẳn false-positive mismatch ở vùng vừa sáp nhập (§2.2.3). `declaredProvince` dropdown đổi 63→34 tỉnh.
- **L2 — geo-risk baseline:** đổi "tự tính NDVI + tự đặt ngưỡng" → **query baseline rừng 2020 làm sẵn (ForTy Class 5 / Natural Forests, 92% accuracy, chuẩn EUDR)** (§2.2.4). Gỡ điểm treo "ngưỡng NDVI chưa chốt" + nâng cấp defensibility ("số này ở đâu ra" → "baseline peer-review").

Product Phase 2 (Farm Plot Geolocation) — **ĐÓNG SESSION HOÀN TOÀN, sẵn sàng đưa vào Architecture/SDS/TechnicalSpec chính thức.** `geoVerificationTimeoutHours` chốt 6 giờ (§2.2.4), ngưỡng NDVI đã đóng nhờ L2. Không còn điểm treo số liệu. Liên thông CSDL nhà nước (C2) ghi ở Known Limitation — ngoài phạm vi xác nhận được của đồ án, không phải điểm treo thiết kế.

---

## 8. Cross-service Note — `Product.varietyName` (không thuộc phạm vi Geolocation, ghi lại để đồng bộ với pricing-service)

**Chốt (05/07/2026), phát sinh từ session thiết kế `pricing-service`, không liên quan gì tới EUDR/geolocation:** pricing-service cần phân biệt giống lúa cụ thể để hiện đúng giá tham khảo (VNSAT trả nhiều giống — OM 18, ST25, IR 50404... — cùng tỉnh/cùng ngày với giá khác hẳn nhau, không phải 1 giá/tỉnh/ngày như cà phê). `Product` hiện chỉ có `category` (ENUM tầng rộng, VD "Gạo"), không có field nào ở tầng chi tiết giống.

**Thêm `Product.varietyName`** (`VARCHAR(100)`, nullable — chỉ có ý nghĩa với category "Gạo", `NULL` cho cà phê/cao su/điều):
- Additive migration, không đụng `Product` aggregate logic đã test.
- Free text, **không** lock cứng theo danh sách `itemName` mà pricing-service quan sát được từ VNSAT — tránh tạo dependency đồng bộ giữa 2 service chỉ cho 1 field phụ trợ tham khảo. FE có thể gợi ý autocomplete qua `GET /api/prices/rice/{province}/items` (pricing-service) nhưng không bắt buộc khớp.
- Match giá tham khảo là best-effort: pricing-service tự normalize `varietyName` theo cùng thuật toán `itemSlug` nó dùng nội bộ — khớp thì hiện giá tham khảo, không khớp thì im lặng không hiện, không phải lỗi, không block tạo listing.

**Timing:** gộp chung migration này với PR `Category`/`ProductImage`/`Listing.coverImageUrl` mà NMC đang làm (cùng đụng bảng `products`), tránh 2 lần touch schema gần nhau. Chi tiết đầy đủ nằm ở `pricing-service-phase2-design.md` §5 (Known Limitations) — tài liệu này chỉ ghi nhận phần việc phía `product-service` cần làm, không lặp lại toàn bộ lý do.

---

## 9. Cross-service Note — `Category` aggregate + quan hệ 2 tầng `commodity`/`category` (đồng bộ với Phase 1 Category redesign, 08/07/2026)

**⟢ SOURCE OF TRUTH cho quan hệ `Category`/`commodity` (redesign Phase 1) là mục này.** `Category` thuộc `product-service`/Phase 1 nên quyết định `Category.commodity` (enum cứng, admin gán lúc `approve()`, bỏ bảng mapping) chốt ở đây. Các file khác chỉ *dùng* `commodity` để gate logic của riêng chúng và tham chiếu về mục này thay vì tự mô tả lại cơ chế map: `milestone-escrow` §7.2 (đọc để publish `contract.signed`), `analytics-service` §2.1 (populate `dim_contract`). Nếu 2 file lệch nhau về cách map → bản ở đây thắng.

**Bối cảnh:** Phase 1 đang redesign `Product.category` từ String tự do thành `categoryId` (FK) trỏ tới aggregate `Category` mới (`PENDING → APPROVED | REJECTED`, admin duyệt/seller propose động). Điều này đụng tới nhiều service Phase 2 vốn dùng `commodity` để gate logic — cần làm rõ 2 tầng để không lẫn:

**2 tầng khác mục đích, giữ cả hai:**
- **`commodity`** (enum cứng `COFFEE/RICE/RUBBER/CASHEW`) — tầng "loại hàng hoá gốc", gắn với **luật/nguồn giá/EUDR**. Dùng để gate logic ở nhiều service: `pricing-service` (COFFEE/RICE scrape VNSAT vs RUBBER/CASHEW admin nhập tay), `product-service` EUDR gate (chỉ COFFEE/RUBBER bắt khai plot), `analytics-service` (`agg_monthly_commodity_stats` gộp theo commodity, PK `(month_id, commodity)`). Cần đúng 1 tập cố định 4 giá trị — không thay bằng category động được, vì các logic này phân nhánh cứng theo 4 nhóm.
- **`category`** (aggregate động, admin/seller thêm) — tầng "phân loại marketplace" cho filter/hiển thị. Linh hoạt, không giới hạn số lượng ("Cà phê Arabica Cầu Đất", "Gạo ST25 hữu cơ"...).

**Điểm nối 2 tầng — `Category.commodity`:** aggregate `Category` (Phase 1) cần thêm **field `commodity`** (enum cứng 4 giá trị), admin **gán bắt buộc lúc `approve()`** category. Đây là điểm map duy nhất, 1 lần, đúng người có thẩm quyền — thay cho mọi bảng tra cứng `category (tiếng Việt) → commodity` mà các bản Phase 2 docs trước đây (06-08/07) giả định. Vì `approve()` bắt buộc gán `commodity` và category chỉ dùng được khi `APPROVED`, mọi `Product` hợp lệ luôn truy ra được `commodity` non-null qua `Category.commodity` — không có case rơi `NULL`/exception khi publish `contract.signed` hay gate EUDR.

**Việc cần làm (đồng bộ Phase 1 ↔ Phase 2):**
- Phase 1 `Category` aggregate thêm field `commodity` (enum, non-null, validate ∈ 4 giá trị) + admin gán lúc `approve()`. Migration `categories` thêm cột `commodity`.
- Các service Phase 2 (`contract-service` publish `contract.signed`, `product-service` EUDR gate, `analytics-service` dimension) đọc `commodity` qua `Category.commodity` thay vì tự map từ enum cũ.
- **Duplicate check của `Category` (Phase 1 §1.2) không đụng tầng `commodity`** — 2 category khác tên nhưng cùng `commodity` là hợp lệ ("Cà phê Robusta" và "Cà phê Arabica" đều `commodity = COFFEE`, không phải trùng). `commodity` là thuộc tính phân nhóm, không phải khoá unique.

Chi tiết mapping `commodity` ở `contract.signed` xem `milestone-escrow-phase2-design.md` §7.2.

---

*Design session: 02/07/2026 · Cập nhật: 04/07/2026 (GEOMETRY/JTS validation, Plot Registry + KML import) · 05/07/2026 (file-service dependency, province cross-check mitigation) · 05/07/2026 (thêm `varietyName`, cross-reference `pricing-service`) · 06/07/2026 (geo-risk verification qua vệ tinh Sentinel-2 NDVI, `geoVerificationStatus`/`geoRiskLevel`, §2.2.4) · 06/07/2026 (thêm outcome `INCONCLUSIVE`, spec đầy đủ luồng EXIF `RequestGeoReevaluation` — đóng điểm treo EXIF, còn 2 điểm treo khác) · 08/07/2026 (rà soát end-to-end: commodity-gate cho geolocation — EUDR chỉ áp cà phê/cao su không áp gạo/điều — C3; quan hệ liên thông với CSDL vùng trồng nhà nước — C2; đồng bộ Phase 1 Category redesign — `Category.commodity` là điểm nối 2 tầng, §9) · 08/07/2026 (L1: polygon GADM 63 tỉnh → Free-GIS-Data 34 tỉnh, gỡ false-positive sáp nhập; L2: tự tính NDVI + tự đặt ngưỡng → baseline rừng 2020 ForTy/Natural Forests làm sẵn, gỡ điểm treo ngưỡng — còn 1 điểm treo timeout) · Cập nhật: 13/07/2026 (chốt `geoVerificationTimeoutHours` = 6h, đóng nốt điểm treo timeout; C2 liên thông CSDL nhà nước chuyển hẳn vào Known Limitation — đóng session hoàn toàn) · Chưa code · **Sẵn sàng đưa vào Architecture/SDS/TechnicalSpec chính thức.***
