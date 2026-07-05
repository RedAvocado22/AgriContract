const fs = require("fs");
const { D, T, runs, P, H1, H2, H3, bullet, numbered, quote, callout, legal, risk, src, table, spacer, cover, toc, endMark, buildDoc } = require("./acdocx.js");
const { Packer, AlignmentType, Paragraph, TextRun, BorderStyle, ShadingType } = D;

const body = [];
const push = (...x) => x.forEach(e => body.push(e));

// mono-ish inline code run
const code = (t) => new TextRun({ text: t, font: "Consolas", size: 19, color: T.SUB });
// service attribute mini-table
function svcTable(rows) {
  return table([2100, 7538], ["Thuộc tính", "Giá trị"], rows, { size: 18, headAlign: [null, null] });
}
// code block paragraph
function codeblock(lines) {
  return new Paragraph({
    shading: { type: ShadingType.CLEAR, color: "auto", fill: "F3F4F6" },
    border: { top: { style: BorderStyle.SINGLE, size: 2, color: T.BORDER, space: 4 }, bottom: { style: BorderStyle.SINGLE, size: 2, color: T.BORDER, space: 4 }, left: { style: BorderStyle.SINGLE, size: 2, color: T.BORDER, space: 4 }, right: { style: BorderStyle.SINGLE, size: 2, color: T.BORDER, space: 4 } },
    spacing: { before: 80, after: 140, line: 240 }, indent: { left: 60, right: 60 },
    children: lines.flatMap((l, i) => i === 0 ? [new TextRun({ text: l, font: "Consolas", size: 18, color: T.SUB })] : [new TextRun({ text: l, font: "Consolas", size: 18, color: T.SUB, break: 1 })]),
  });
}

push(...cover("AGRICONTRACT", "Kiến Trúc Kỹ Thuật Hệ Thống",
  "Kiến trúc microservices hướng sự kiện cho nền tảng hợp đồng nông sản B2B — 12 dịch vụ, choreography saga, audit trail bất biến",
  ["Tài liệu nội bộ — Đồ án Tốt nghiệp", "Phiên bản 2.0 · Tháng 7/2026"]));
push(...toc());

// ============================================================
// 0. EXEC SUMMARY
// ============================================================
push(H1("Tóm tắt điều hành"));
push(P("Hệ thống được xây dựng theo kiến trúc microservices, phân rã theo năng lực nghiệp vụ (business capability), gồm một API Gateway và 12 dịch vụ độc lập, mỗi dịch vụ sở hữu cơ sở dữ liệu riêng (database-per-service). Các dịch vụ giao tiếp chủ yếu qua sự kiện bất đồng bộ trên RabbitMQ theo mô hình choreography saga — không có orchestrator trung tâm — và chỉ dùng gọi đồng bộ (OpenFeign) cho các trường hợp cần phản hồi ngay."));
push(P("Ba quyết định kiến trúc chi phối toàn hệ thống. Thứ nhất, tính nhất quán dữ liệu giữa các dịch vụ được đảm bảo bằng Transactional Outbox Pattern kết hợp idempotency, thay vì phân tán transaction — chấp nhận eventual consistency ở những nơi không thuộc đường tiền tệ tới hạn. Thứ hai, tiền tệ có single source of truth duy nhất tại bank-service (mô hình ledger gộp), các dịch vụ khác chỉ giữ trạng thái, không giữ lại con số tiền để tránh dual-write. Thứ ba, tính toàn vẹn bằng chứng được bảo vệ bằng chuỗi hash append-only nhiều lớp thay vì blockchain, phù hợp bối cảnh trusted-operator và ràng buộc thời gian/nhân lực của dự án."));
push(P("Tài liệu này mô tả kiến trúc mục tiêu đầy đủ của hệ thống: nguyên tắc phân rã, chi tiết từng dịch vụ, cơ chế giao tiếp và đảm bảo nhất quán, event catalog, thiết kế cơ sở dữ liệu, luồng nghiệp vụ chính, mô hình bảo mật, hạ tầng triển khai, và các giới hạn kiến trúc có chủ đích."));

push(callout("Ghi chú phạm vi.", "Lớp giữ tiền (bank-service) và một số tích hợp bên thứ ba (ngân hàng thật, đối tác tín dụng) được mô phỏng trong phạm vi đồ án, với ranh giới interface thiết kế sạch để tích hợp thật không đụng business logic. Chi tiết ở Mục 11.", "note"));

// ============================================================
// 1. OVERVIEW
// ============================================================
push(H1("1. Tổng quan kiến trúc"));
push(H2("1.1 Nguyên tắc thiết kế"));
push(table(
  [2600, 7038],
  ["Nguyên tắc", "Áp dụng trong hệ thống"],
  [
    ["Microservices phân rã theo business capability", "Mỗi dịch vụ là một năng lực nghiệp vụ độc lập, triển khai và mở rộng riêng. Tránh nano-service (chia quá nhỏ) và god-service (gộp quá nhiều)"],
    ["Domain-Driven Design (DDD) tactical", "Aggregate root với rich model — trạng thái chỉ đổi qua business method, không public setter. Ranh giới aggregate theo ranh giới transaction thật, không theo quan hệ dữ liệu"],
    ["Database-per-service", "Mỗi dịch vụ sở hữu schema riêng; không dịch vụ nào truy cập DB của dịch vụ khác. Chia sẻ dữ liệu qua REST, event, hoặc denormalization lúc tạo bản ghi"],
    ["Choreography Saga (event-driven)", "Giao dịch phân tán điều phối bằng chuỗi sự kiện, mỗi dịch vụ tự phản ứng — không orchestrator trung tâm. Bù trừ (compensation) qua event ngược"],
    ["Transactional Outbox + Idempotency", "Đảm bảo at-least-once delivery mà không mất/nhân đôi tác dụng; consumer khử trùng lặp bằng khoá idempotency"],
  ],
  { size: 18 }
));

push(H2("1.2 Sơ đồ thành phần tổng thể"));
push(P("Hệ thống chia thành bốn lớp theo hướng đi của một request từ ngoài vào:"));
push(bullet([runs("Lớp biên (edge): ", { bold: true }), runs("Nginx reverse proxy (rate limit per IP) đứng trước API Gateway — là điểm vào duy nhất từ bên ngoài.", {})]));
push(bullet([runs("Lớp cổng (gateway): ", { bold: true }), runs("API Gateway validate JWT qua Keycloak JWKS, định tuyến tới dịch vụ nội bộ, và tiêm định danh người dùng đã xác thực xuống downstream.", {})]));
push(bullet([runs("Lớp dịch vụ (services): ", { bold: true }), runs("12 dịch vụ nghiệp vụ giao tiếp với nhau qua RabbitMQ (async) và OpenFeign (sync), mỗi dịch vụ có MySQL riêng.", {})]));
push(bullet([runs("Lớp hạ tầng dùng chung (infrastructure): ", { bold: true }), runs("Keycloak (IAM), RabbitMQ (message broker), MinIO (object storage), Redis (cache/pub-sub), SendGrid (email/inbound parse).", {})]));

// ============================================================
// 2. DECOMPOSITION
// ============================================================
push(H1("2. Phân rã dịch vụ"));
push(P("Áp dụng nguyên tắc “Decompose by Business Capability” (Chris Richardson, Microservices Patterns): mỗi dịch vụ tương ứng một năng lực nghiệp vụ độc lập, với kiểu DDD (Core / Supporting / Generic) phản ánh mức độ khác biệt cạnh tranh của năng lực đó."));
push(table(
  [800, 2300, 900, 5638],
  ["#", "Dịch vụ", "Port", "Năng lực nghiệp vụ · Kiểu DDD"],
  [
    ["1", "user-service", "8081", "Định danh, KYC, xác minh thẩm quyền đại diện, enforce khoá tài khoản · Generic"],
    ["2", "product-service", "8082", "Catalog, listing, geolocation mảnh đất cho EUDR · Supporting"],
    ["3", "contract-service", "8083", "Vòng đời hợp đồng, milestone, chữ ký điện tử · Core"],
    ["4", "escrow-service", "8084", "Logic khoá/giải ngân ký quỹ theo đợt · Core"],
    ["5", "notification-service", "8085", "Thông báo hướng sự kiện, OTP email, neo hash · Generic"],
    ["6", "bank-service", "8086", "Giữ tiền hợp pháp (mock), ledger gộp · Core"],
    ["7", "inspection-service", "8087", "Giám định độc lập 3 cấp, evidence bất biến · Supporting"],
    ["8", "reputation-service", "8088", "Sổ khoá bất biến + điểm uy tín + tham chiếu tín dụng · Supporting"],
    ["9", "file-service", "8089", "Lưu trữ file tập trung, xử lý async · Generic"],
    ["10", "pricing-service", "8091", "Cache giá tham chiếu nông sản từ nguồn ngoài · Supporting"],
    ["11", "audit-service", "8092", "Chuỗi hash bất biến, neo Bitcoin, xuất báo cáo EUDR · Core"],
    ["12", "analytics-service", "8093", "Read model CQRS, tổng hợp time-series · Generic"],
  ],
  { size: 18, colAlign: [AlignmentType.CENTER, null, AlignmentType.CENTER, null] }
));
push(P([runs("Vì sao 12 dịch vụ, không phải 13. ", { bold: true }), runs("Chức năng tìm kiếm/lọc listing ban đầu dự kiến tách thành search-service riêng (CQRS read model) đã được gộp lại thành hai tham số filter trong product-service — cross-service query cho một tính năng đọc đơn giản không đủ biện minh cho một dịch vụ độc lập với chi phí đồng bộ read-model đi kèm. Port 8090 bỏ trống.", {})]));
push(P([runs("Vì sao tách contract-service và escrow-service. ", { bold: true }), runs("Hai năng lực này có độ thay đổi (rate of change) khác nhau: contract-service là business logic thuần về trạng thái giao hàng, escrow-service là financial operation với yêu cầu audit riêng. Ranh giới rõ ràng: contract-service quản lý trạng thái giao hàng, escrow-service quản lý tiền — không lẫn hai domain.", {})]));

// ============================================================
// 3. PER-SERVICE
// ============================================================
push(H1("3. Chi tiết từng dịch vụ"));

// 3.1 user
push(H2("3.1 user-service"));
push(svcTable([
  ["Port · DB", "8081 · user_db"],
  ["Trách nhiệm", "Cầu nối định danh với Keycloak; quản lý profile tổ chức; xác minh thẩm quyền đại diện; enforce khoá tài khoản theo quyết định của reputation-service"],
  ["Aggregate", "UserProfile (userId = Keycloak sub UUID, organizationName, role, verificationStatus, authorizationExpiresAt)"],
  ["Vai trò Keycloak", "SELLER, BUYER, ADMIN, INSPECTOR"],
]));
push(P("user-service không lưu credentials (Keycloak giữ) — chỉ giữ dữ liệu profile và trạng thái xác minh. Xác minh thẩm quyền đại diện được thực hiện lúc đăng ký, fail-closed (chưa duyệt thì chưa được đăng listing/ký), áp dụng đối xứng cho buyer và seller. Trường authorizationExpiresAt nhập tay từ giấy tờ thật (giấy uỷ quyền, giấy phép), không hardcode."));
push(legal("BLDS 2015, Điều 142", "Giao dịch do người không có thẩm quyền đại diện xác lập có thể bị tuyên vô hiệu. Việc gate xác minh thẩm quyền ký kết trước khi kích hoạt tài khoản là biện pháp kiến trúc trực tiếp chống rủi ro hợp đồng vô hiệu."));

// 3.2 product
push(H2("3.2 product-service"));
push(svcTable([
  ["Port · DB", "8082 · product_db"],
  ["Trách nhiệm", "Catalog sản phẩm; listing; geolocation mảnh đất phục vụ EUDR; lọc/tìm listing (2 tham số filter)"],
  ["Aggregate", "Product (+ varietyName, ProductPlot VO list), Listing, PlotRegistryEntry (aggregate nhỏ độc lập, thuộc seller, tái sử dụng)"],
]));
push(P("Điểm thiết kế cốt lõi là geolocation cho EUDR. Vì EUDR cấm mass balance, bằng chứng nguồn gốc phải khai đủ toàn bộ mảnh đất của từng hộ đóng góp vào lô hàng — nên geolocation là một mảng plot, không phải một cặp toạ độ đơn. Khai báo đất được tách khỏi việc tạo listing: mỗi hộ đăng ký mảnh đất một lần vào PlotRegistryEntry (thuộc seller), tái sử dụng cho mọi listing sau; lúc tạo listing chỉ chọn từ registry và snapshot sang ProductPlot (cùng nguyên tắc snapshot bất biến như điều khoản hợp đồng)."));
push(P([runs("Kiểu dữ liệu không gian. ", { bold: true }), runs("Toạ độ lưu ở kiểu GEOMETRY (SRID 4326) thay vì TEXT — DB tự chặn sai cú pháp/ring hở; lỗi self-intersection chặn ở tầng ứng dụng qua JTS .isValid(). Đường nhập liệu chính là import KML (khớp thực tế cán bộ địa chính đã đo GPS sẵn); pin thủ công chỉ là fallback, giới hạn cứng ở POINT. Cross-check tỉnh (khai độc lập vs suy từ toạ độ) cảnh báo lỗi convert VN2000→WGS84, non-blocking.", {})]));

// 3.3 contract
push(H2("3.3 contract-service"));
push(svcTable([
  ["Port · DB", "8083 · contract_db"],
  ["Trách nhiệm", "Vòng đời hợp đồng (state machine), milestone, chữ ký điện tử, nguồn hash nội dung hợp đồng"],
  ["Aggregate", "Contract (rich model, ContractTerms VO + milestoneSchedule, Signature VO); Milestone (aggregate riêng, cùng service/DB)"],
  ["Publishes", "contract.signed, contract.settled, contract.cancelled, milestone.* (weighed / confirmed / settled / cancelled_with_penalty / force_majeure_*)"],
  ["Consumes", "escrow event xác nhận trạng thái tiền để chuyển state hợp đồng"],
]));
push(P("Đây là dịch vụ phức tạp nhất (Core domain). Milestone là aggregate riêng chứ không phải entity con của Contract: milestone thứ 3 quyết toán không cần atomic cùng lúc với các milestone khác — chúng độc lập về nghiệp vụ, nhét chung một aggregate là nhầm quan hệ khoá ngoại với ranh giới transaction thật cần thiết (nguyên tắc Effective Aggregate Design — Vernon)."));
push(P([runs("Đồng bộ Milestone → Contract bằng Local Outbox. ", { bold: true }), runs("Khi milestone cuối quyết toán, Contract phải chuyển SETTLED. Dùng Spring ApplicationEvent nội bộ có một bug thật: nếu app crash sau khi milestone commit nhưng trước khi listener chạy, event bay mất — ApplicationEvent không có retry mặc định — và Contract kẹt vĩnh viễn ở ACTIVE mà không có exception nào báo. Thay bằng Local Outbox: mỗi lần milestone quyết toán, ghi một row outbox trong cùng transaction; một @Scheduled poller đọc row chưa xử lý, kiểm tra điều kiện “mọi milestone đã SETTLED” rồi gọi completeAllMilestones(). At-least-once, không mất, không qua network hop (khác với Outbox Pattern qua RabbitMQ).", {})]));
push(P([runs("Chữ ký điện tử. ", { bold: true }), runs("Signature là value object trong Contract (sống/chết cùng hợp đồng). Ràng buộc UNIQUE(contractId, signerRole) thay cho hai cờ riêng; Contract chuyển SIGNED khi đủ hai dòng BUYER + SELLER. Sole control đảm bảo qua step-up re-auth lúc bấm “Ký” (session freshness signatureAuthMaxAgeSeconds = 300s) cộng OTP email — hai yếu tố knowledge-based + possession-based.", {})]));
push(legal("Luật GDĐT 2023, Điều 22–23", "Chữ ký JWT/OTP thuộc chữ ký điện tử cơ bản — không bị phủ nhận giá trị pháp lý (Điều 23 khoản 1) nhưng không tương đương chữ ký tay (khoản 2, cần chứng thư CA). Hợp đồng vẫn có hiệu lực đầy đủ (Điều 34–36 tách biệt khỏi loại chữ ký); khác biệt nằm ở gánh nặng chứng minh — được bù bằng audit trail và hash."));

// 3.4 escrow
push(H2("3.4 escrow-service"));
push(svcTable([
  ["Port · DB", "8084 · escrow_db"],
  ["Trách nhiệm", "Logic ký quỹ theo đợt: tính toán khoá/giải ngân/phạt; điều phối bank-service"],
  ["Aggregate", "EscrowAccount, EscrowMilestone (chỉ giữ state LOCKED/RELEASED/PENALIZED, không giữ số tiền)"],
  ["Consumes", "milestone.settled, milestone.buyer_confirmed, milestone.cancelled_with_penalty, contract.settled, contract.cancelled"],
]));
push(P("escrow-service là actor duy nhất gọi bank-service; contract-service không bao giờ nói chuyện trực tiếp với bank. Nguyên tắc chống dual-write: EscrowAccount/EscrowMilestone chỉ giữ trạng thái, không tự lưu con số tiền phải đồng bộ tay với bank — số tiền thật là single source of truth ở ledger bên bank-service. Khi nhận milestone.settled (mang lockedAmount và actualAmount sau pro-rata), escrow-service tự tính chênh lệch và bắn cặp lệnh RELEASE_TO_SELLER + REFUND_TO_BUYER nếu số thực nhận thấp hơn số đã khoá."));

// 3.5 bank
push(H2("3.5 bank-service"));
push(svcTable([
  ["Port · DB", "8086 · bank_db"],
  ["Trách nhiệm", "Giữ tiền hợp pháp (mock) — legal custody; thực thi lệnh khoá/giải ngân/phạt/hoàn"],
  ["Aggregate", "LedgerEntry (append-only): entryId, sourceEventId (idempotency key), contractId, milestoneId?, userId, entryType, amount"],
]));
push(P("Mô hình FBO/Omnibus (chuẩn công nghiệp: ví điện tử, escrow bất động sản): chỉ một chỗ giữ tiền chung, toàn bộ chi tiết “ai sở hữu bao nhiêu, cho việc gì” nằm trong LedgerEntry append-only. Số dư không lưu sẵn ở đâu — luôn là kết quả cộng dồn (SUM) từ các dòng ledger liên quan, tính lúc cần."));
push(P([runs("Không fire-and-forget. ", { bold: true }), runs("escrow-service gửi lệnh (bank.lock_requested…) và đợi confirmation (bank.lock_completed / bank.lock_failed) mới đổi state — không tự set trước. Nếu set trước rồi bank fail, hai bên lệch state không ai biết (đúng dạng dual-write problem). Idempotency key là sourceEventId (ID của outbox message escrow gửi sang), UNIQUE — nhận trùng thì không insert lại nhưng vẫn re-publish confirmation (tránh escrow treo chờ mãi).", {})]));
push(legal("Nghị định 52/2024/NĐ-CP, Điều 8 Khoản 7", "Nghiêm cấm cung ứng dịch vụ trung gian thanh toán không phép. Tách bank-service làm nơi giữ tiền (ngân hàng có license trong triển khai thật) khiến nền tảng chỉ ra lệnh, không giữ tiền — không thuộc phạm vi phải xin giấy phép. Đây là quyết định kiến trúc phục vụ trực tiếp một ràng buộc pháp lý."));

// 3.6 inspection
push(H2("3.6 inspection-service"));
push(svcTable([
  ["Port · DB", "8087 · inspection_db"],
  ["Trách nhiệm", "Giám định độc lập 3 cấp; evidence record bất biến với reportHash"],
  ["Aggregate", "InspectionReport (reportHash = SHA-256(content + timestamp + inspectorId)), level2_inspection_commission"],
]));
push(P("Level 1.5 và Level 2 là hai mô hình định danh khác nhau. Level 1.5 (Vinacontrol/Quatest) là actor thật trên nền tảng — dùng chung schema Signature (mở rộng signerRole thêm INSPECTOR, thêm reportId với UNIQUE(reportId)), KYC xác minh chứng chỉ hành nghề, session freshness riêng inspectionAuthMaxAgeSeconds = 1800s (thao tác thực địa cần thời gian, khác hành vi ký tức thời của buyer/seller). Level 2 (SGS/Bureau Veritas) không tích hợp đăng nhập — report ingest tự động qua hòm thư platform (SendGrid Inbound Parse), hash đóng băng ngay khi nhận, Admin xác nhận sau (human-confirm). Chọn tổ chức Level 2 giới hạn qua allowlist 3 nhóm để chặn thông đồng chọn tổ chức dễ dãi."));
push(P([runs("Ranh giới cross-service. ", { bold: true }), runs("signature.reportId là FK logic (không REFERENCES cross-database) vì signature ở contract_db còn inspection_report ở inspection_db — giữ integrity ở application layer, đúng nguyên tắc database-per-service.", {})]));

// 3.7 reputation
push(H2("3.7 reputation-service"));
push(svcTable([
  ["Port · DB", "8088 · reputation_db"],
  ["Trách nhiệm", "Sổ khoá bất biến (enforce lockout); điểm uy tín (search ranking); tham chiếu tín dụng (export)"],
  ["Aggregate", "LockEntry (insert-only, lockDurationDays snapshot bất biến, sourceEventId idempotency key)"],
  ["Consumes", "milestone.cancelled_with_penalty, contract.settled"],
]));
push(P("Dịch vụ này gánh ba loại dữ liệu khác bản chất, không gộp chung logic: sổ khoá (không thể là pure read model vì là bằng chứng pháp lý — lockDurationDays snapshot cứng lúc tính, không recompute), điểm uy tín (view sống, tính lại được), và tham chiếu tín dụng (export cho bên thứ ba). Enforcement thực hiện ở tầng use-case chứ không ở Gateway: sign() fail-closed (ưu tiên đóng circuit-breaker gap ở hành động rủi ro nhất), CreateListing fail-open. user-service là nơi enforce khoá thật dựa trên quyết định từ reputation-service."));

module.exports = { body, push, code, svcTable, codeblock };
