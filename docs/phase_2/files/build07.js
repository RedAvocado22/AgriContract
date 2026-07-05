const fs = require("fs");
const { D, T, runs, P, H1, H2, H3, bullet, numbered, quote, callout, legal, risk, src, table, spacer, cover, toc, endMark, buildDoc } = require("./acdocx.js");
const { Packer, AlignmentType, Paragraph, TextRun, BorderStyle, ShadingType } = D;

const body = [];
const push = (...x) => x.forEach(e => body.push(e));
function codeblock(lines) {
  return new Paragraph({
    shading: { type: ShadingType.CLEAR, color: "auto", fill: "F3F4F6" },
    border: { top: { style: BorderStyle.SINGLE, size: 2, color: T.BORDER, space: 4 }, bottom: { style: BorderStyle.SINGLE, size: 2, color: T.BORDER, space: 4 }, left: { style: BorderStyle.SINGLE, size: 2, color: T.BORDER, space: 4 }, right: { style: BorderStyle.SINGLE, size: 2, color: T.BORDER, space: 4 } },
    spacing: { before: 80, after: 140, line: 240 }, indent: { left: 60, right: 60 },
    children: lines.flatMap((l, i) => i === 0 ? [new TextRun({ text: l, font: "Consolas", size: 17, color: T.SUB })] : [new TextRun({ text: l, font: "Consolas", size: 17, color: T.SUB, break: 1 })]),
  });
}
function uc(id, name, fields) {
  push(new Paragraph({ spacing: { before: 140, after: 40 }, children: [new TextRun({ text: id + " — " + name, font: T.FONT, size: 21, bold: true, color: T.HEAD })] }));
  push(table([1800, 7838], ["", ""], fields.map(([k, v]) => [k, v]), { size: 18, boldCol: [true, false] }));
}

if (require.main === module) {
push(...cover("AGRICONTRACT", "Software Design Specification (SDS)",
  "Phần 4 — Cụm dữ liệu & hỗ trợ: product-service (geolocation/EUDR) · file-service · pricing-service",
  ["Tài liệu nội bộ — Đồ án Tốt nghiệp", "Phiên bản 1.0 · Phần 4/5 · Tháng 7/2026"]));
push(...toc());
}

// ============================================================
// 1. SCOPE
// ============================================================
push(H1("1. Phạm vi phần 4"));
push(P("Phần 4 đặc tả cụm dữ liệu và hỗ trợ — ba dịch vụ cung cấp dữ liệu nền cho luồng hợp đồng: product-service (phần mở rộng geolocation mảnh đất phục vụ EUDR — Product/Listing cốt lõi đã có từ trước), file-service (kho lưu trữ file tập trung, xử lý bất đồng bộ), pricing-service (cache giá tham chiếu nông sản từ nguồn ngoài). Tuân theo chuẩn dùng chung ở Phần 1."));
push(table(
  [2300, 7338],
  ["Dịch vụ", "Sở hữu (phạm vi phần 4)"],
  [
    ["product-service", "Geolocation mảnh đất cho EUDR: PlotRegistryEntry (tái sử dụng), ProductPlot (snapshot), validate hình học; varietyName. Product/Listing cốt lõi không đổi"],
    ["file-service", "Lưu trữ file agnostic với nghiệp vụ; xử lý async (parse email + virus-scan); retention EUDR. KHÔNG biết ý nghĩa nghiệp vụ của file"],
    ["pricing-service", "Ingest giá tham chiếu ngoài (VNSAT scrape + admin nhập tay); cache Redis. KHÔNG tính giá từ dữ liệu nội bộ, KHÔNG denormalize giá vào hợp đồng"],
  ],
  { size: 18 }
));

// ============================================================
// 2. PRODUCT-SERVICE (GEOLOCATION)
// ============================================================
push(H1("2. product-service — Geolocation cho EUDR"));
push(P("EUDR (Regulation 2023/1115, sửa đổi 2025/2650, hiệu lực 30/12/2026) yêu cầu truy được toạ độ chính xác của mảnh đất nơi hàng được trồng, chứng minh không phá rừng sau 31/12/2020. Hai ràng buộc quyết định thiết kế:"));
push(bullet([runs("Cấm mass balance → geolocation là MẢNG plot. ", { bold: true }), runs("Không được gộp hàng nhiều nguồn rồi khai đại diện một plot; phải khai đủ toàn bộ plot. Một listing của HTX thường gom từ nhiều hộ, mỗi hộ một plot riêng — nên geolocation không thể là một cặp lat/long gắn vào Product, mà là mảng ProductPlot.", {})]));
push(bullet([runs("Khai lại mỗi listing là phi thực tế → tách Plot Registry. ", { bold: true }), runs("HTX gom từ hàng chục–hàng trăm hộ; gõ lại toạ độ 6 số thập phân mỗi mùa vụ chắc chắn sinh sai số. Tách khai báo đất (một lần, thuộc seller) khỏi tạo listing (nhiều lần).", {})]));

push(H2("2.1 Mô hình miền"));
push(table(
  [2500, 2300, 4838],
  ["Thành phần", "Loại", "Vai trò"],
  [
    ["PlotRegistryEntry", "Aggregate root nhỏ (thuộc Seller)", "Nguồn khai báo đất tái sử dụng; một hộ đăng ký một lần, dùng cho mọi listing sau. UNIQUE(sellerId, householdLabel) là khoá match re-import"],
    ["ProductPlot", "Value object (trong Product)", "Snapshot COPY từ PlotRegistryEntry lúc CreateListing — không phải tham chiếu sống (sửa registry sau không đổi listing cũ)"],
    ["Product.varietyName", "Field mới (nullable)", "Giống lúa cụ thể để match giá tham chiếu (pricing-service); không liên quan geolocation"],
  ],
  { size: 18 }
));
push(P([runs("Vì sao snapshot, không phải link sống: ", { bold: true }), runs("cùng nguyên tắc đã dùng cho agreedPrice/milestoneSchedule/level2InspectorOrg — khai báo tại đúng thời điểm tạo listing. Seller đo lại chính xác hơn sau này thì PlotRegistryEntry gốc đổi, nhưng ProductPlot đã snapshot cho listing cũ giữ nguyên.", {})]));

push(H2("2.2 Validate hình học — 2 tầng (GEOMETRY + JTS)"));
push(P("Lưu TEXT không chặn được gì (sai cú pháp, ring hở, polygon tự cắt đều insert được, tới lúc dùng mới lộ). Chia trách nhiệm hai tầng, không đổ hết cho DB:"));
push(table(
  [2600, 7038],
  ["Tầng", "Bắt lỗi gì"],
  [
    ["DB — GEOMETRY, SRID 4326", "Free, tự động lúc INSERT: sai cú pháp, ring không khép kín, thiếu điểm. Không cần code thêm"],
    ["Application — JTS .isValid() trong RAM trước persist", "Self-intersection (polygon tự cắt). Đây là nơi DUY NHẤT bắt được — vì MySQL ST_IsValid() chỉ chạy SRID=0, lỗi trên SRID=4326; JTS không quan tâm SRID của DB nên bypass được"],
  ],
  { size: 18 }
));
push(callout("Giới hạn permanent (không công nghệ nào đóng được):", "DB + JTS chặn hết lỗi cú pháp/hình học, nhưng KHÔNG chặn được seller khai một plot hợp lệ 100% về hình học nhưng không có thật/không phải đất của họ. Đây là giới hạn của mô hình self-declared, trusted-operator — không phải thiếu sót thiết kế.", "note"));

push(H2("2.3 Nguồn nhập liệu & quy tắc geometryType"));
push(P("KML import là đường chính (khớp thực tế: cán bộ địa chính/công ty xuất khẩu/NGO đã đo GPS sẵn ra file KML cho EUDR); pin thủ công là fallback hẹp."));
push(table(
  [2200, 2400, 5038],
  ["Nguồn (source)", "geometryType cho phép", "Cơ chế"],
  [
    ["KML_IMPORT (chính)", "POINT và POLYGON", "Parse KML, mỗi placemark → 1 PlotRegistryEntry; householdLabel lấy từ tag <name>"],
    ["MANUAL_PIN (fallback)", "CHỈ POINT (cấm POLYGON)", "navigator.geolocation.getCurrentPosition(); vẽ polygon tay sai số cấp mét, lệch sang ranh giới hộ khác/rừng phòng hộ — plot cần POLYGON (>4ha) phải có file KML thật"],
  ],
  { size: 18 }
));
push(P([runs("Ngưỡng geometryType (theo EUDR, áp từng plot riêng): ", { bold: true }), runs("≤ 4 hecta → POINT (một toạ độ, 6 số thập phân); > 4 hecta → POLYGON (viền khép kín).", {})]));

push(H2("2.4 verificationLevel & cross-check tỉnh"));
push(P("verificationLevel: SELF_DECLARED (mặc định) / CADASTRAL_BACKED (seller upload trích lục bản đồ địa chính). Trích lục là dữ liệu nhà nước (số thửa, ranh giới) nhưng KHÔNG phải văn bản chứng minh quyền sử dụng đất (đó là sổ đỏ) — chỉ giảm nhẹ giới hạn \"khai đất giả\", không đóng hẳn; nền tảng không OCR/đối chiếu nội dung file với toạ độ."));
push(P([runs("Cross-check tỉnh (giảm nhẹ lỗi convert VN2000→WGS84): ", { bold: true }), runs("cán bộ đo gốc bằng VN2000 rồi convert sang WGS84 trước khi xuất KML; nếu sai tham số kinh tuyến trục, toạ độ vẫn đúng DẠNG WGS84, vẫn trong bounding box Việt Nam, vẫn pass JTS (lệch tới ~81km nhưng là lỗi NỘI DUNG, không phải cú pháp). Đối chiếu hai nguồn độc lập: declaredProvince (seller chọn dropdown) vs tỉnh suy ra từ point-in-polygon (JTS + polygon GADM 63 tỉnh cũ). Lệch → provinceMismatchFlag=TRUE, cảnh báo UI, KHÔNG chặn insert.", {})]));
push(callout("Giới hạn cross-check tỉnh:", "chỉ bắt lỗi VÔ Ý đơn lẻ (một plot lệch khỏi tỉnh khai). Không bắt lỗi hệ thống (cả batch cùng lệch cùng hướng, nhất quán nội bộ) và không bắt fraud có chủ đích. Dùng GADM 63 tỉnh cũ (chưa có polygon 34 tỉnh mở sau sáp nhập 12/6/2025) → có thể false-positive ở khu vực vừa sáp nhập; chấp nhận được vì non-blocking.", "note"));

push(H2("2.4.1 Geo-risk verification qua vệ tinh — Sentinel-2 NDVI (mới, 06/07/2026, chưa đóng)"));
push(P([runs("Cơ chế: ", { bold: true }), runs("ngay sau khi PlotRegistryEntry được tạo/update, hệ thống tự động (async, không chặn seller) gọi Sentinel Hub Statistical API (Copernicus Data Space Ecosystem — dữ liệu Sentinel-2 miễn phí, không giới hạn thương mại) hỏi giá trị NDVI tại toạ độ/polygon đó, dùng ảnh gần nhất mốc 31/12/2020 (đã có sẵn trong archive từ 2015, không phải chờ vệ tinh bay qua chụp mới). NDVI dưới ngưỡng rừng (chưa chốt, cần thực nghiệm) → geoRiskLevel = HIGH_RISK; trên ngưỡng → LOW_RISK. Không chặn giao dịch — chỉ dán nhãn cảnh báo cho buyer tự quyết định, đúng nguyên tắc neutral-party.", {})]));
push(P([runs("2 field mới, tách biệt rõ tiến trình vs kết quả: ", { bold: true }), runs("geoVerificationStatus (PENDING/CHECKED/UNVERIFIED) là trạng thái check đã chạy xong chưa; geoRiskLevel (LOW_RISK/HIGH_RISK, nullable) là kết quả thật, chỉ có giá trị khi CHECKED. geoVerificationTimeoutHours (đề xuất 4-6 giờ) — tính bằng GIỜ chứ không phải ngày, vì dữ liệu đã tồn tại sẵn, timeout chỉ hứng lỗi tạm thời (mây che, rate limit). Hết timeout mà chưa có kết quả → UNVERIFIED (khác HIGH_RISK — đây là \"chưa biết\", không phải \"có bằng chứng rủi ro\"). CreateListing reject nếu registryEntryId đang PENDING (tránh race condition listing lên sàn trước khi biết risk).", {})]));
push(callout("Giới hạn, chưa đóng (06/07/2026):", "NDVI 10m resolution + ngưỡng nhị phân là proxy thô, KHÔNG phải xác minh EUDR pháp lý đầy đủ — không được trình bày với VICOFA/buyer như \"đã verify EUDR\". Ngưỡng NDVI cụ thể chưa chốt, cần thực nghiệm. EXIF photo cross-check (chụp ảnh tại chỗ, đối chiếu GPS) đã thống nhất nguyên tắc nhưng field/flow chưa spec, để session riêng.", "note"));

push(H2("2.5 Use case chính"));
uc("UC-P1", "ImportPlotsFromKML", [
  ["Actor", "SELLER"],
  ["Input", "sellerId, kmlFile, declaredProvince (dropdown 63 tỉnh, áp cho cả file)"],
  ["Luồng chính", "Lưu KML qua file-service → originalKmlFileId. Mỗi placemark: match (sellerId, householdLabel) → UPDATE nếu trùng (đo lại), INSERT nếu mới (ON DUPLICATE KEY UPDATE); xác định geometryType theo areaHectares; convert sang GeoJSON; JTS .isValid() trước persist (fail thì báo đúng placemark đó, không chặn cả file); point-in-polygon so declaredProvince → set provinceMismatchFlag"],
  ["areaHectares (Point)", "Điểm không có diện tích; ưu tiên đọc từ <ExtendedData> theo quy ước tên field; không có → bắt seller nhập tay (không mặc định 0 — gây hiểu nhầm)"],
]);
uc("UC-P2", "AddPlotManually (fallback)", [
  ["Actor", "SELLER"],
  ["Input", "sellerId, householdLabel, lat, long, accuracyMeters, declaredProvince"],
  ["Luồng chính", "Tạo/update PlotRegistryEntry (geometryType=POINT, source=MANUAL_PIN, originalKmlFileId=NULL); cùng cross-check tỉnh. accuracyMeters từ coords.accuracy; > ngưỡng (đề xuất 20m) → cảnh báo UI, KHÔNG chặn cứng (thiết bị rẻ/trong nhà kính không đạt ngưỡng vẫn phải dùng được)"],
]);
uc("UC-P3", "CreateListing / UpdateListing", [
  ["Actor", "SELLER"],
  ["Input", "List<registryEntryId> (checkbox chọn từ registry của seller) — KHÔNG nhập geoJson trực tiếp mỗi lần"],
  ["Luồng chính", "Validate registryEntryId thuộc đúng sellerId; ít nhất 1 plot (cấm mass balance); copy dữ liệu từ PlotRegistryEntry sang ProductPlot (snapshot). UpdateListing cho tick/untick plot trước khi listing khoá sửa"],
  ["Validate mới (06/07/2026)", "Reject nếu bất kỳ registryEntryId nào đang geoVerificationStatus = PENDING (§2.4.1) — tránh race condition listing lên sàn trước khi có kết quả satellite check"],
]);
uc("UC-P4", "UploadCadastralExtract", [
  ["Actor", "SELLER"],
  ["Luồng chính", "Upload scan/PDF trích lục cho một entry đã có → file-service → cadastralExtractFileId → verificationLevel=CADASTRAL_BACKED. KHÔNG đối chiếu nội dung file với geoJson (giới hạn self-declared)"],
]);

push(H2("2.6 API & lược đồ dữ liệu"));
push(table(
  [4400, 1400, 3838],
  ["Endpoint", "Vai trò", "Mô tả"],
  [
    ["POST /api/v1/plots/import-kml", "SELLER", "Import KML (UC-P1)"],
    ["POST /api/v1/plots/manual", "SELLER", "Pin thủ công (UC-P2)"],
    ["POST /api/v1/plots/{id}/cadastral-extract", "SELLER", "Upload trích lục (UC-P4)"],
    ["GET /api/v1/plots", "SELLER", "Danh sách registry của seller (chọn lúc tạo listing)"],
    ["POST /api/v1/listings", "SELLER", "Tạo listing từ registryEntryId (UC-P3)"],
  ],
  { size: 17, colAlign: [null, AlignmentType.CENTER, null] }
));
push(codeblock([
  "-- product_db · additive, không đụng bảng product gốc",
  "CREATE TABLE plot_registry_entry (",
  "  registry_entry_id       UUID PRIMARY KEY,",
  "  seller_id               UUID NOT NULL,          -- plain UUID (không cross-DB FK)",
  "  household_label         VARCHAR(255) NOT NULL,",
  "  geometry_type           VARCHAR(20) NOT NULL,   -- POINT | POLYGON",
  "  geo_json                GEOMETRY NOT NULL SRID 4326,",
  "  area_hectares           DECIMAL(10,4) NOT NULL,",
  "  source                  VARCHAR(20) NOT NULL,   -- KML_IMPORT | MANUAL_PIN",
  "  accuracy_meters         DECIMAL(6,2) NULL,      -- chỉ MANUAL_PIN; cảnh báo, không constraint",
  "  verification_level      VARCHAR(20) NOT NULL DEFAULT 'SELF_DECLARED',",
  "  cadastral_extract_file_id UUID NULL,            -- ref file-service",
  "  original_kml_file_id    UUID NULL,              -- ref file-service; phẳng, lặp theo batch",
  "  declared_province       VARCHAR(50) NOT NULL,   -- seller khai, 63 tỉnh cũ",
  "  province_mismatch_flag  BOOLEAN NOT NULL DEFAULT FALSE,  -- non-blocking",
  "  imported_at             TIMESTAMP NOT NULL,     -- cập nhật lại mỗi lần re-import",
  "  geo_verification_status VARCHAR(20) NOT NULL DEFAULT 'PENDING',  -- PENDING | CHECKED | UNVERIFIED (§2.4.1, mới 06/07/2026)",
  "  geo_risk_level          VARCHAR(20) NULL,       -- LOW_RISK | HIGH_RISK, chỉ có giá trị khi CHECKED (§2.4.1, mới 06/07/2026)",
  "  UNIQUE (seller_id, household_label),  SPATIAL INDEX (geo_json)",
  ");",
  "-- Invariant 'MANUAL_PIN → POINT' check ở use-case layer, không phải DB constraint.",
  "",
  "CREATE TABLE product_plot (",
  "  plot_id                  UUID PRIMARY KEY,",
  "  product_id               UUID NOT NULL REFERENCES product(product_id),",
  "  source_registry_entry_id UUID NULL REFERENCES plot_registry_entry(registry_entry_id),",
  "  household_label          VARCHAR(255) NOT NULL,",
  "  geometry_type            VARCHAR(20) NOT NULL,",
  "  geo_json                 GEOMETRY NOT NULL SRID 4326,",
  "  area_hectares            DECIMAL(10,4) NOT NULL,",
  "  verification_level       VARCHAR(20) NOT NULL DEFAULT 'SELF_DECLARED',  -- copy lúc snapshot",
  "  cadastral_extract_file_id UUID NULL,",
  "  declared_at              TIMESTAMP NOT NULL,  SPATIAL INDEX (geo_json)",
  ");",
  "",
  "ALTER TABLE product ADD COLUMN variety_name VARCHAR(100) NULL;  -- chỉ ý nghĩa với gạo",
]));
push(legal("EU Regulation 2023/1115 (EUDR), Điều 9 & yêu cầu geolocation", "Không mass balance: List<registryEntryId> không được rỗng (validate use case). Không cần tỷ lệ đóng góp: chỉ cần khai đủ danh sách plot, nên ProductPlot không có field khối lượng/tỷ lệ. Dữ liệu self-declared, không có xác minh thực địa: declaredAt/importedAt + hash chain là cơ chế chống sửa-sau, không phải xác minh đúng-đắn-ban-đầu. Bổ sung 06/07/2026 (§2.4.1): có thêm lớp geo-risk verification qua vệ tinh (Sentinel-2 NDVI) — chỉ là tín hiệu cảnh báo tham khảo, KHÔNG phải xác minh EUDR pháp lý đầy đủ, không thay đổi bản chất self-declared ở trên."));

// ============================================================
// 3. FILE-SERVICE
// ============================================================
push(H1("3. file-service"));
push(P("Aggregate File duy nhất, agnostic với nghiệp vụ — file-service không biết một file dùng để làm gì (evidence, cadastral, report), chỉ biết blob + metadata kỹ thuật. Ý nghĩa nghiệp vụ nằm ở tên field bên dịch vụ giữ fileId. Sáu dịch vụ đã phụ thuộc: evidence cân hàng, bằng chứng bất khả kháng, trích lục địa chính, KML gốc, report Level 2, output DDS."));

push(H2("3.1 Ba entrypoint theo trust boundary"));
push(P("Không dùng một API generic store(bytes, ingestChannel) — vì ingestChannel khi đó là param caller tự khai, ai cũng có thể tự xưng SYSTEM_GENERATED để né virus-scan. Thay bằng ba entrypoint mỏng, ingestChannel do chính entrypoint quyết định (không giả mạo được):"));
push(table(
  [2100, 2200, 1900, 3438],
  ["Entrypoint", "Ai gọi", "ingestChannel", "Validate"],
  [
    ["UploadFile", "Public API, JWT seller/admin", "DIRECT_UPLOAD", "contentType ∈ {JPG,PNG,PDF}, size ≤ 10MB"],
    ["Email intake bridge", "Job nội bộ (IMAP polling), không expose API", "EMAIL_INTAKE", "size ≤ 20MB (streaming check, tránh OOM); vượt cap → email_intake_failure"],
    ["store() nội bộ", "Service-to-service (mTLS/token)", "SYSTEM_GENERATED", "Không cần virus-scan (nội dung do hệ thống tạo)"],
  ],
  { size: 17 }
));

push(H2("3.2 Pipeline xử lý async"));
push(P("Tách hai consumer theo LOẠI CÔNG VIỆC (parse email vs virus-scan), không theo nguồn gốc file — và nối chuỗi, không chạy song song độc lập:"));
push(codeblock([
  "file.uploaded.direct → queue file-service.virus-scan → VirusScanConsumer → markReady/markFailed",
  "file.email.received  → queue file-service.email-parse → EmailParseConsumer",
  "                          ├─ parse OK   → publish vào file-service.virus-scan (dùng lại) → markReady/markFailed",
  "                          └─ parse FAIL → markFailed + insert email_intake_failure",
]));
push(P([runs("Vì sao nối chuỗi, không song song: ", { bold: true }), runs("nếu tách hai pipeline song song độc lập (mỗi file đi đúng một), file từ EMAIL_INTAKE (nguồn ngoài internet, unauthenticated — đúng vector tấn công phổ biến nhất) sẽ KHÔNG bao giờ được virus-scan. Fix: EmailParseConsumer parse xong publish tiếp vào cùng queue virus-scan, để VirusScanConsumer là điểm chốt duy nhất quyết định markReady — bất kể file từ đâu. status ở PROCESSING xuyên suốt cả hai bước; consumer downstream vẫn chỉ chờ một event file.ready/file.failed.", {})]));
push(P([runs("Email intake bridge: ", { bold: true }), runs("IMAP polling định kỳ (@Scheduled, mỗi 5 phút, Jakarta Mail) — KHÔNG webhook (webhook cần domain thật + verify DNS + public HTTPS, quá nặng cho scope). Retry 3 lần qua dead-letter-exchange + TTL. Hai DLQ khác ý nghĩa: virus-scan.dlq (lỗi hệ thống → alert dev), email-parse.dlq (định dạng lạ từ tổ chức giám định → báo Admin liên hệ gửi lại).", {})]));

push(H2("3.3 Event, access control, retention"));
push(P("Event contract: file.ready(fileId, uploadedBy, ingestChannel, contentType, fileSize) và file.failed(fileId, ..., failureReason). Idempotency key = fileId (một fileId chỉ ra đúng một file.ready/file.failed). Bắt buộc event, không polling: nếu consumer coi fileId lúc UploadFile trả về là evidence hợp lệ ngay trong khi file còn PROCESSING rồi vài giây sau FAILED (virus) — hai dịch vụ lệch state (dual-write). Event bắt buộc consumer chờ file.ready mới đổi state."));
push(bullet([runs("Access control (download): ", { bold: true }), runs("file-service KHÔNG tự kiểm tra quyền nghiệp vụ (chỉ dịch vụ sở hữu fileId mới có kiến thức đó). Pattern proxy: dịch vụ sở hữu tự expose endpoint + validate quyền, rồi mới gọi GetFile(fileId) nội bộ (service-to-service) và proxy bytes ngược lại. GetFile không public cho end-user gọi thẳng.", {})]));
push(bullet([runs("Retention: ", { bold: true }), runs("neo vào EUDR Điều 9 (giữ tối thiểu 5 năm) → default GIỮ vĩnh viễn, không TTL tự xoá. DeleteFile chỉ dịch vụ sở hữu gọi khi biết an toàn. Orphan cleanup qua ConfirmAttached: dịch vụ nhận fileId phải lưu vào entity của mình TRƯỚC rồi mới gọi ConfirmAttached(fileId) set attached=TRUE; file attached=FALSE quá 1 tuần → tự xoá (chưa từng có chủ, không compliance nào áp).", {})]));
push(codeblock([
  "CREATE TABLE file (",
  "  file_id        UUID PRIMARY KEY,",
  "  storage_hash   VARCHAR(64) NOT NULL,   -- MinIO tampering detection (tách signedContentHash)",
  "  uploaded_by    VARCHAR(255) NOT NULL,  -- userId hoặc serviceId",
  "  ingest_channel VARCHAR(20) NOT NULL,   -- DIRECT_UPLOAD|EMAIL_INTAKE|SYSTEM_GENERATED",
  "  content_type   VARCHAR(100) NOT NULL, file_size BIGINT NOT NULL,",
  "  status         VARCHAR(20) NOT NULL DEFAULT 'PROCESSING', -- PROCESSING|READY|FAILED",
  "  failure_reason TEXT NULL, attached BOOLEAN NOT NULL DEFAULT FALSE,",
  "  created_at     TIMESTAMP NOT NULL DEFAULT now()",
  ");",
  "CREATE INDEX idx_file_attached_created ON file(attached, created_at);  -- orphan cleanup",
  "CREATE TABLE email_intake_failure (",
  "  failure_id UUID PRIMARY KEY, file_id UUID NULL REFERENCES file(file_id),",
  "  source_mailbox VARCHAR(255) NOT NULL, failure_reason TEXT NOT NULL,",
  "  detected_at TIMESTAMP NOT NULL DEFAULT now(), reviewed_by_admin BOOLEAN NOT NULL DEFAULT FALSE",
  ");",
]));

// ============================================================
// 4. PRICING-SERVICE
// ============================================================
push(H1("4. pricing-service"));
push(P("Chỉ ingest giá tham chiếu NGOÀI — loại hẳn việc tính giá từ giao dịch nội bộ. Lý do kép: data sparse (platform mới, vài chục hợp đồng đầu không đủ ý nghĩa thống kê); và nghiêm trọng hơn, một con số \"giá thị trường\" tính từ chính vài giao dịch trong platform có thể bị buyer lớn lợi dụng — ép giá thấp vài lần đầu để tự tạo giá tham chiếu giả rồi ép các HTX khác, đúng nghịch lý với bài toán bất đối xứng quyền lực mà nền tảng sinh ra để giải."));
push(table(
  [2000, 4600, 3038],
  ["Commodity", "Nguồn", "Cơ chế"],
  [
    ["COFFEE, RICE", "thitruongnongsan.gov.vn (VNSAT — Bộ NN&MT), tách theo tỉnh", "Scrape tự động (Jsoup 2 bước)"],
    ["RUBBER, CASHEW", "Không có nguồn mở tương đương", "Admin nhập tay, một giá quốc gia"],
  ],
  { size: 18 }
));
push(H2("4.1 Mô hình miền & cache"));
push(P("PriceQuote: commodity, itemName (tên mặt hàng cụ thể — RICE có nhiều giống OM 18/ST25/IR 50404 cùng tỉnh/ngày giá khác hẳn, nên itemName là dimension bắt buộc; COFFEE một giá trị cố định), province (nullable — NULL cho rubber/cashew), price (VNĐ/kg), source, capturedAt (ngày giá áp dụng theo nguồn), ingestedAt. MySQL append-only (price_history) là nguồn thật, Redis chỉ là cache."));
push(bullet([runs("Redis key: ", { bold: true }), runs("price:{commodity}:{province}:{itemSlug} (itemSlug = normalize itemName: bỏ dấu, lowercase, khoảng trắng → gạch ngang).", {})]));
push(bullet([runs("KHÔNG set TTL cứng: ", { bold: true }), runs("nếu key tự hết hạn đúng lúc job ingest fail, key biến mất → seller không thấy giá nào, tệ hơn thấy giá cũ có cảnh báo. \"Cũ hay mới\" tính động bằng so capturedAt với now, ngưỡng stale riêng từng commodity trong application.yml (coffee ~14 ngày, rice ~30 ngày — VNSAT publish theo batch không đều).", {})]));

push(H2("4.2 Ingestion & Read API"));
push(P("VNSAT là ASP.NET WebForms (không REST JSON), postback pattern. Jsoup 2 bước: (1) GET parse __VIEWSTATE/__EVENTVALIDATION/__VIEWSTATEGENERATOR; (2) POST lại cùng URL mang cookie + TOÀN BỘ field form (thiếu tu_ngay/den_ngay gây 500). Job VnsatPriceIngestionJob (@Scheduled daily, riêng từng commodity) sống sót qua lỗi — exception → insert price_ingestion_failure, KHÔNG throw tiếp (không kéo sập scheduler), không xoá cache Redis cũ khi fail."));
push(P([runs("Read API cache-aside: ", { bold: true }), runs("GET /api/prices/{commodity}/{province}?item={itemSlug} — public. Đọc Redis trước; miss → query price_history (ORDER BY capturedAt DESC LIMIT 1), trả về + ghi lại Redis. Match giá tham chiếu với Product.varietyName là best-effort: pricing-service normalize varietyName theo cùng thuật toán itemSlug — khớp thì hiện, không khớp thì im lặng (không lỗi, không block tạo listing). KHÔNG denormalize giá vào Contract/Listing — giữ tính tham khảo thuần tuý, tránh bị lợi dụng trong đàm phán.", {})]));
push(codeblock([
  "CREATE TABLE price_history (          -- pricing_db, append-only, nguồn sự thật",
  "  id UUID PRIMARY KEY, commodity VARCHAR(20) NOT NULL, item_name VARCHAR(255) NOT NULL,",
  "  province VARCHAR(50) NULL, price DECIMAL(15,2) NOT NULL,",
  "  source VARCHAR(20) NOT NULL,        -- VNSAT_SCRAPE | ADMIN_MANUAL",
  "  captured_at DATE NOT NULL, ingested_at TIMESTAMP NOT NULL",
  ");",
  "CREATE TABLE price_ingestion_failure (",
  "  id UUID PRIMARY KEY, commodity VARCHAR(20) NOT NULL,",
  "  failure_reason TEXT NOT NULL, detected_at TIMESTAMP NOT NULL DEFAULT now()",
  ");",
]));
push(callout("Rủi ro pháp lý scraping (ghi rõ):", "scrape thitruongnongsan.gov.vn chưa xin phép chính thức Bộ NN&MT — chấp nhận được cho scope đồ án non-commercial. Nếu productize thật (bán cho VICOFA/VRA), cần đàm phán data licensing hoặc xin cấp API trực tiếp.", "note"));

// ============================================================
// 5. SEQUENCE FLOWS
// ============================================================
push(H1("5. Luồng trình tự xuyên dịch vụ"));
push(H2("5.1 Vòng đời file bằng chứng (evidence)"));
push(table(
  [700, 2200, 3500, 3238],
  ["#", "Actor / Dịch vụ", "Hành động / Sự kiện", "Kết quả"],
  [
    ["1", "SELLER → file-service", "UploadFile (ảnh cân hàng) → DIRECT_UPLOAD, status=PROCESSING", "file.uploaded.direct"],
    ["2", "file-service", "VirusScanConsumer scan → markReady → file.ready", "status=READY"],
    ["3", "contract-service", "Consume file.ready → lưu sellerEvidenceFileId vào milestone", "Milestone SELLER_WEIGHED"],
    ["4", "contract-service → file-service", "ConfirmAttached(fileId)", "attached=TRUE (thoát orphan cleanup)"],
  ],
  { size: 17, colAlign: [AlignmentType.CENTER, null, null, null] }
));
push(H2("5.2 Import KML → tạo listing"));
push(table(
  [700, 2200, 3500, 3238],
  ["#", "Actor / Dịch vụ", "Hành động / Sự kiện", "Kết quả"],
  [
    ["1", "SELLER → file-service", "Upload KML → originalKmlFileId", "file.ready"],
    ["2", "product-service", "Parse placemark → JTS validate → point-in-polygon vs declaredProvince → UPSERT PlotRegistryEntry", "Registry cập nhật; provinceMismatchFlag nếu lệch"],
    ["3", "SELLER → product-service", "CreateListing với List<registryEntryId>", "Copy registry → ProductPlot (snapshot); ≥1 plot (cấm mass balance)"],
    ["4", "pricing-service", "Match varietyName (best-effort) → hiện giá tham khảo", "Giá tham chiếu hiển thị (không denormalize)"],
  ],
  { size: 17, colAlign: [AlignmentType.CENTER, null, null, null] }
));

// ============================================================
// 6. LIMITATIONS
// ============================================================
push(H1("6. Giới hạn có chủ đích"));
push(bullet([runs("Khai đất giả nhưng hợp lệ hình học. ", { bold: true }), runs("DB + JTS + cross-check tỉnh không đóng được — giới hạn permanent của mô hình self-declared. verificationLevel/CADASTRAL_BACKED chỉ giảm nhẹ (nền tảng không OCR đối chiếu trích lục với toạ độ).", {})]));
push(bullet([runs("Polygon 34 tỉnh mở chưa tồn tại. ", { bold: true }), runs("Dùng GADM 63 tỉnh cũ cho cross-check → false-positive ở khu vực vừa sáp nhập; non-blocking nên chấp nhận được.", {})]));
push(bullet([runs("Orphan cleanup phụ thuộc ConfirmAttached. ", { bold: true }), runs("Nếu dịch vụ consumer có bug quên gọi ConfirmAttached dù đã lưu fileId, file hợp lệ có thể bị xoá nhầm sau 1 tuần — cần lưu ý khi code consumer.", {})]));
push(bullet([runs("file-service không phải lớp phòng thủ thứ 2. ", { bold: true }), runs("Access control dựa vào dịch vụ sở hữu tự validate đúng; nếu dịch vụ đó có lỗ hổng auth, file-service không double-check.", {})]));
push(bullet([runs("Nguồn VNSAT dễ vỡ khi đổi layout. ", { bold: true }), runs("HTML/ASP.NET postback không có cơ chế tự phát hiện; chỉ dựa exception → price_ingestion_failure. rubber/cashew phụ thuộc hoàn toàn Admin nhập tay (không validation chéo).", {})]));
push(bullet([runs("NDVI vệ tinh là proxy thô, không phải xác minh EUDR pháp lý (mới, 06/07/2026). ", { bold: true }), runs("Sentinel-2 10m resolution + ngưỡng nhị phân chỉ đủ làm tín hiệu cảnh báo tham khảo, không thay thế được dịch vụ verification EUDR chuyên dụng. Không được trình bày với VICOFA/buyer như đã \"verify EUDR\".", {})]));

// ============================================================
// 7. OPEN ITEMS
// ============================================================
push(H1("7. Điểm còn treo"));
push(bullet([runs("Cơ chế nhận mail intake@ ", { bold: true }), runs("— file-service (chủ sở hữu) chốt IMAP polling; phần inspection (Phần 3) từng nhắc webhook. Thống nhất theo file-service (IMAP polling) khi hợp nhất tài liệu.", {})]));
push(bullet([runs("migration varietyName ", { bold: true }), runs("gộp chung với PR Category/ProductImage/coverImageUrl đang làm (cùng đụng bảng products), tránh 2 lần touch schema.", {})]));
push(bullet([runs("Khả năng dùng IMAP với mailbox thật của tổ chức giám định ", { bold: true }), runs("— giả định đơn giản hoá, chưa khảo sát thực tế.", {})]));
push(bullet([runs("Geo-risk verification qua vệ tinh (mới, 06/07/2026, chưa đóng). ", { bold: true }), runs("Ngưỡng NDVI phân biệt rừng/không rừng chưa chốt (cần thực nghiệm); geoVerificationTimeoutHours (4-6h) là placeholder chưa vận hành thật; EXIF photo cross-check mới thống nhất nguyên tắc, chưa spec field/flow — cần session riêng. Chi tiết đầy đủ ở product-phase2-design.md §2.2.4.", {})]));

module.exports = { body };

if (require.main === module) {
push(endMark());

const doc = buildDoc(body, {
  title: "AgriContract — SDS — Phần 4: Dữ liệu & Hỗ trợ (Product · File · Pricing)",
  headerText: "AgriContract · SDS — Phần 4",
  footerText: "SDS v1.0 · Phần 4 · Tháng 7/2026",
});
Packer.toBuffer(doc).then(buf => { fs.writeFileSync("/tmp/AgriContract_07_SDS_Part4_v1.docx", buf); console.log("written", buf.length); });

}
