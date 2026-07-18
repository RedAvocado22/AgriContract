const fs = require("fs");
const { writeDocx } = require("./docx_output.js");
const { D, T, runs, P, H1, H2, H3, bullet, numbered, quote, callout, legal, risk, src, table, spacer, cover, toc, endMark, buildDoc } = require("./acdocx.js");
const { Packer, AlignmentType, Paragraph, TextRun, BorderStyle, ShadingType } = D;

const IS_MERGED = process.env.AGRICONTRACT_SDS_MERGED === "1";

const body = [];
const push = (...x) => x.forEach(e => body.push(e));

function codeblock(lines) {
  return new Paragraph({
    shading: { type: ShadingType.CLEAR, color: "auto", fill: "F3F4F6" },
    border: { top: { style: BorderStyle.SINGLE, size: 2, color: T.BORDER, space: 4 }, bottom: { style: BorderStyle.SINGLE, size: 2, color: T.BORDER, space: 4 }, left: { style: BorderStyle.SINGLE, size: 2, color: T.BORDER, space: 4 }, right: { style: BorderStyle.SINGLE, size: 2, color: T.BORDER, space: 4 } },
    spacing: { before: 80, after: 140, line: 240 }, indent: { left: 60, right: 60 },
    children: lines.flatMap((l, i) => i === 0 ? [new TextRun({ text: l, font: "Consolas", size: 18, color: T.SUB })] : [new TextRun({ text: l, font: "Consolas", size: 18, color: T.SUB, break: 1 })]),
  });
}

if (require.main === module) {
push(...cover("AGRICONTRACT", "Software Design Specification (SDS)",
  "Phần 1 — Nền tảng thiết kế và chuẩn dùng chung · Tài liệu thiết kế chi tiết hệ thống",
  ["Tài liệu nội bộ — Đồ án Tốt nghiệp", "Phiên bản 1.0 · Phần 1/n · Tháng 7/2026"]));
push(...toc());
}

// ============================================================
// 0. ABOUT THIS DOCUMENT
// ============================================================
push(H1("Về tài liệu này"));
push(P("Software Design Specification (SDS) mô tả thiết kế chi tiết của hệ thống AgriContract ở mức đủ để hiện thực hoá bằng mã nguồn — chi tiết hơn tài liệu Kiến trúc (mô tả cấu trúc tổng thể) và tài liệu Giải pháp (mô tả nghiệp vụ và mô hình kinh doanh)."));
if (IS_MERGED) {
  push(P("Tài liệu này gồm năm phần hợp nhất. Phần 1 đặt nền tảng chung: phạm vi, thuật ngữ, actors, các chuẩn thiết kế xuyên suốt (API, sự kiện, dữ liệu, lỗi, bảo mật), mô hình dữ liệu tổng thể và yêu cầu phi chức năng; các phần 2–5 tiếp nối ngay trong cùng file với thiết kế chi tiết từng cụm dịch vụ."));
  push(table(
    [3000, 6638],
    ["Phần", "Nội dung"],
    [
      ["Phần 1 — Nền tảng", "Giới thiệu, actors, chuẩn API/sự kiện/dữ liệu/lỗi/bảo mật, mô hình dữ liệu tổng và yêu cầu phi chức năng"],
      ["Phần 2–5 — Tiếp nối trong tài liệu", "Use case, sequence, API spec, schema và verification matrix theo từng cụm dịch vụ"],
    ],
    { size: 18 }
  ));
} else {
  push(P("Tài liệu được chia thành nhiều phần để dễ theo dõi và bảo trì. Phần 1 (tài liệu này) đặt nền tảng chung mà mọi phần sau gác lên: phạm vi, thuật ngữ, actors, các chuẩn thiết kế xuyên suốt (API, sự kiện, dữ liệu, lỗi, bảo mật), mô hình dữ liệu tổng thể ở mức khái niệm, và yêu cầu phi chức năng. Các phần tiếp theo mô tả thiết kế chi tiết từng dịch vụ: use case, sơ đồ trình tự (sequence), đặc tả API và lược đồ dữ liệu ở mức bảng."));
  push(table(
    [3000, 6638],
    ["Phần", "Nội dung"],
    [
      ["Phần 1 (tài liệu này)", "Nền tảng & chuẩn dùng chung: giới thiệu, actors, chuẩn API/sự kiện/dữ liệu/lỗi/bảo mật, mô hình dữ liệu tổng, yêu cầu phi chức năng"],
      ["Phần 2–5 (tài liệu kế tiếp)", "Thiết kế chi tiết theo cụm dịch vụ: use case, sequence, API spec, schema từng dịch vụ"],
    ],
    { size: 18 }
  ));
}

// ============================================================
// 1. INTRODUCTION
// ============================================================
push(H1("1. Giới thiệu"));
push(H2("1.1 Mục đích"));
push(P("Cung cấp bản thiết kế chi tiết, nhất quán và có thể kiểm chứng cho đội phát triển hiện thực hoá hệ thống, đồng thời làm cơ sở để hội đồng thẩm định đánh giá tính đúng đắn và khả thi của thiết kế. Tài liệu định nghĩa các chuẩn chung để 12 dịch vụ được phát triển độc lập nhưng vẫn tương thích với nhau ở ranh giới giao tiếp."));
push(H2("1.2 Phạm vi hệ thống"));
push(P("AgriContract số hoá vòng đời hợp đồng mua bán nông sản B2B — từ đàm phán, ký kết điện tử, ký quỹ theo đợt giao hàng, đến giải ngân sau xác nhận giao nhận — với audit trail bất biến phục vụ bằng chứng pháp lý và kiểm toán EUDR. Phạm vi giới hạn có chủ đích ở tầng hợp đồng: không xử lý logistics, không phải sàn thương mại điện tử, không thay thế hệ thống kế toán."));
push(H2("1.3 Thuật ngữ và viết tắt"));
push(table(
  [2200, 7438],
  ["Thuật ngữ", "Định nghĩa"],
  [
    ["HTX", "Hợp tác xã — bên bán (Seller) trong hệ thống"],
    ["EUDR", "EU Deforestation Regulation (2023/1115, sửa đổi 2025/2650) — quy định chống phá rừng của EU"],
    ["Ký quỹ (Escrow)", "Cơ chế bên thứ ba giữ tiền theo điều khoản đã ký; ở đây là ngân hàng (mock)"],
    ["Milestone", "Một đợt giao hàng trong hợp đồng; mỗi milestone có vòng đời cân/giao/quyết toán riêng"],
    ["Saga (Choreography)", "Mẫu điều phối giao dịch phân tán bằng chuỗi sự kiện, không orchestrator trung tâm"],
    ["Outbox Pattern", "Ghi sự kiện vào bảng outbox trong cùng transaction với thay đổi state để tránh dual-write"],
    ["Idempotency", "Tính chất xử lý một message nhiều lần cho kết quả như xử lý một lần"],
    ["INSPECTOR", "Tổ chức giám định độc lập; 3 cấp Level 1 / 1.5 / 2"],
    ["FBO / Omnibus", "Mô hình ledger gộp: một tài khoản chung, chi tiết sở hữu nằm ở các dòng ledger"],
    ["VNSAT", "Nguồn giá nông sản của Bộ NN&MT (thitruongnongsan.gov.vn) mà pricing-service scrape"],
    ["JTS", "Java Topology Suite — thư viện kiểm tra tính hợp lệ hình học của polygon"],
    ["OTS", "OpenTimestamps — neo hash lên Bitcoin làm bằng chứng thời gian độc lập"],
    ["DDS", "Due Diligence Statement — bản khai thẩm định EUDR; AgriContract chỉ xuất gói bằng chứng hỗ trợ DDS"],
  ],
  { size: 18 }
));
push(H2("1.4 Tài liệu tham chiếu"));
push(bullet("AgriContract — Phân Tích Thị Trường & Luận Cứ Kinh Doanh (v5.0)."));
push(bullet("AgriContract — Giải Pháp, Người Dùng & Mô Hình Kinh Doanh (v5.0)."));
push(bullet("AgriContract — Kiến Trúc Kỹ Thuật Hệ Thống (v2.0)."));
push(bullet("Các tài liệu thiết kế chi tiết theo dịch vụ (design sessions) làm nguồn quyết định thiết kế."));

// ============================================================
// 2. SYSTEM DESIGN OVERVIEW
// ============================================================
push(H1("2. Tổng quan thiết kế hệ thống"));
push(H2("2.1 Kiến trúc tham chiếu"));
push(P("Hệ thống theo kiến trúc microservices hướng sự kiện: một API Gateway và 12 dịch vụ nghiệp vụ, mỗi dịch vụ sở hữu cơ sở dữ liệu riêng (database-per-service), giao tiếp qua RabbitMQ (async, choreography saga) và OpenFeign (sync). Chi tiết cấu trúc, phân rã dịch vụ và luồng saga nằm trong tài liệu Kiến trúc v2.0; SDS này không lặp lại mà đặc tả chi tiết hiện thực hoá."));
push(H2("2.2 Ràng buộc thiết kế"));
push(table(
  [2600, 7038],
  ["Nhóm ràng buộc", "Nội dung"],
  [
    ["Nguồn lực", "Đội nhỏ, thời gian phát triển 5 tháng — ưu tiên giải pháp pragmatic có đường nâng cấp rõ ràng, tránh over-engineer"],
    ["Công nghệ", "Java 21 / Spring Boot 3.3, React + Vite + TypeScript, MySQL 8, RabbitMQ, Keycloak, Docker Compose"],
    ["Pháp lý", "Nền tảng không giữ tiền thật (ngân hàng giữ); chữ ký điện tử cơ bản bù bằng audit trail; gate xác minh thẩm quyền đại diện; retention EUDR ≥ 5 năm"],
    ["Mô phỏng có chủ đích", "bank-service (giữ tiền) là mock; tiếp nhận report Level 2 giả định qua hòm thư — interface thiết kế sạch để thay bằng tích hợp thật mà không đổi business logic"],
  ],
  { size: 18 }
));
push(H2("2.3 Actors và vai trò"));
push(P("Các stakeholder nghiệp vụ không ánh xạ một-một sang role runtime. Keycloak có năm role BUYER, SELLER, INSPECTOR, OPERATOR và ADMIN. OPERATOR xử lý thao tác vận hành hằng ngày có thể đảo ngược; ADMIN giữ quyết định rủi ro cao, security/audit và bước approve maker-checker. External Verifier và System là actor kỹ thuật; Software Buyer là chủ thể tổ chức, không phải role runtime."));
push(table(
  [2200, 2200, 5238],
  ["Actor", "Vai trò Keycloak", "Quyền hạn chính"],
  [
    ["Bên mua (Platform Buyer)", "BUYER", "Tạo offer, đàm phán, khoá tiền ký quỹ, xác nhận nhận hàng, cancel/dispute theo quyền"],
    ["Bên bán / HTX (Platform Seller)", "SELLER", "Đăng listing, đàm phán, ký hợp đồng, cân/giao hàng, nhận thanh toán"],
    ["Giám định (Level 1.5)", "INSPECTOR", "Nhận assignment, nộp inspection report có hash; không phán xử"],
    ["Vận hành hằng ngày", "OPERATOR", "KYC, review/moderation, nhập giá thủ công; có thể propose governance nhưng không tự approve"],
    ["Quản trị rủi ro cao", "ADMIN", "Approve/reject maker-checker; security, audit, analytics admin; fallback cho thao tác vận hành"],
    ["Ngân hàng (Escrow Holder)", "— (tích hợp hệ thống)", "Giữ tiền, thực thi lệnh lock/release/seize/refund từ escrow-service"],
  ],
  { size: 18 }
));

// ============================================================
// 3. CROSS-CUTTING STANDARDS
// ============================================================
push(H1("3. Chuẩn thiết kế dùng chung"));
push(P("Các chuẩn dưới đây bắt buộc áp dụng cho mọi dịch vụ để đảm bảo tính nhất quán ở ranh giới giao tiếp. Thiết kế chi tiết từng dịch vụ (các phần sau) tuân theo chuẩn này."));

push(H2("3.1 Chuẩn API REST"));
push(bullet([runs("Điểm vào duy nhất: ", { bold: true }), runs("mọi request từ ngoài đi qua API Gateway (:8080); client không gọi trực tiếp port dịch vụ.", {})]));
push(bullet([runs("Versioning & path: ", { bold: true }), runs("tiền tố /api/v1/{resource}, danh từ số nhiều, phân cấp theo tài nguyên (vd /api/v1/contracts/{id}/milestones).", {})]));
push(bullet([runs("Xác thực: ", { bold: true }), runs("Authorization: Bearer {JWT}; Gateway validate JWT, xoá Authorization/X-User-*/internal secret do client gửi rồi tiêm đúng X-User-Id, X-User-Role, X-Correlation-Id và X-Gateway-Secret. X-Api-Key của route audit-hash được giữ nguyên. Public match theo method + exact path; protected route authenticated mặc định; /internal/** không route.", {})]));
push(bullet([runs("Phân trang: ", { bold: true }), runs("query params page, size, sort; response bọc content + metadata (page, size, totalElements, totalPages).", {})]));
push(bullet([runs("Idempotency: ", { bold: true }), runs("thao tác ghi liên quan tiền hoặc trạng thái nhạy cảm nhận header Idempotency-Key hoặc dùng khoá nghiệp vụ tương đương.", {})]));
push(P("Cấu trúc phản hồi lỗi chuẩn (error envelope) — đồng nhất mọi dịch vụ:"));
push(codeblock([
  "{",
  '  "timestamp": "2026-07-05T09:12:33Z",',
  '  "status": 409,',
  '  "error": "Conflict",',
  '  "code": "CONTRACT_ALREADY_SIGNED",',
  '  "message": "Hợp đồng đã ở trạng thái SIGNED",',
  '  "path": "/api/v1/contracts/{id}/sign",',
  '  "traceId": "b7f3..."',
  "}",
]));

push(H2("3.2 Chuẩn sự kiện (RabbitMQ)"));
push(P("DLQ replay giữ nguyên eventId và payload; không sửa trực tiếp DB để ‘chữa’ message. Với tiền/security/evidence, OPERATOR đề xuất replay và ADMIN approve; message không thể replay được chuyển PARKED kèm reason. RabbitMQ không phải nguồn lịch sử và không backup: recovery dựa trên transactional outbox + queue backlog."));
push(bullet([runs("Envelope bắt buộc: ", { bold: true }), runs("mỗi message mang eventId (UUID) và occurredAt (ISO-8601) ở top-level, cùng aggregateId liên quan.", {})]));
push(bullet([runs("Routing key: ", { bold: true }), runs("theo convention {aggregate}.{actor}_{past_tense_verb} (vd milestone.buyer_confirmed).", {})]));
push(bullet([runs("Exchange: ", { bold: true }), runs("topic exchange theo aggregate; một dead-letter exchange (fanout) nhận message fail sau 3 lần retry.", {})]));
push(bullet([runs("Publish an toàn: ", { bold: true }), runs("qua Transactional Outbox — không publish trực tiếp trong business transaction.", {})]));
push(bullet([runs("Consume an toàn: ", { bold: true }), runs("khử trùng lặp theo eventId/sourceEventId trước khi xử lý (at-least-once).", {})]));
push(bullet([runs("Tương thích ngược: ", { bold: true }), runs("consumer là tolerant reader — bỏ qua field lạ, không vỡ khi payload thêm field mới (event schema versioning).", {})]));

push(H2("3.3 Chuẩn dữ liệu"));
push(P("Mỗi enum/state machine có đúng một owner service. Dịch vụ khác chỉ giữ projection hoặc giá trị trong event contract, không tự định nghĩa enum cạnh tranh. Snapshot đã ký và artefact evidence là immutable; dữ liệu mutable chỉ đổi qua use case có actor attribution."));
push(table(
  [2900, 6738],
  ["Quy tắc", "Chi tiết"],
  [
    ["Khoá chính", "UUID cho mọi aggregate/entity chính"],
    ["Tham chiếu cross-service", "Plain UUID, KHÔNG REFERENCES cross-database; integrity giữ ở application layer"],
    ["Tiền tệ", "DECIMAL(15,2), đơn vị VNĐ; số dư luôn derive từ ledger, không lưu sẵn"],
    ["Bảng append-only", "ledger_entry, audit_record, audit_anchor, lock_entry, lock_override_event, price_history, negotiation/domain events — chỉ INSERT/SELECT, không UPDATE/DELETE"],
    ["Cột audit", "created_at bắt buộc; updated_at chỉ ở entity mutable"],
    ["Dữ liệu không gian", "GEOMETRY, SRID 4326; validate hình học qua JTS ở tầng ứng dụng"],
    ["Snapshot bất biến", "ContractTerms, milestoneSchedule, ProductPlot, level2InspectorOrg snapshot lúc tạo/ký — không đổi theo nguồn gốc sau này"],
    ["Đặt tên", "snake_case cho bảng/cột (DB), camelCase cho domain (Java)"],
  ],
  { size: 18 }
));

push(H2("3.4 Xử lý lỗi và trạng thái HTTP"));
push(P("Mã lỗi nghiệp vụ (code) tách khỏi HTTP status: status cho tầng vận chuyển, code cho tầng nghiệp vụ để client xử lý chính xác. Ánh xạ chuẩn:"));
push(table(
  [2000, 3000, 4638],
  ["HTTP", "Ý nghĩa", "Ví dụ tình huống"],
  [
    ["400", "Bad Request — dữ liệu vào không hợp lệ", "geoJson sai hình học (JTS .isValid() fail)"],
    ["401 / 403", "Chưa xác thực / không đủ quyền", "SELLER gọi endpoint chỉ dành cho ADMIN"],
    ["404", "Không tìm thấy tài nguyên", "contractId không tồn tại"],
    ["409", "Conflict — vi phạm ràng buộc trạng thái/optimistic lock", "Ký hợp đồng đã SIGNED; version mismatch"],
    ["422", "Không xử lý được về mặt nghiệp vụ", "Cancel milestone đã SETTLED"],
    ["500", "Lỗi hệ thống nội bộ", "Unhandled exception không thuộc lỗi nghiệp vụ"],
    ["503", "Service Unavailable", "Dependency bắt buộc (KYC/lock/provider OTP) không sẵn sàng; fail-closed"],
    ["504", "Gateway Timeout", "Downstream vượt timeout định tuyến"],
  ],
  { size: 18, colAlign: [AlignmentType.CENTER, null, null] }
));

push(H2("3.5 Bảo mật xuyên suốt"));
push(bullet([runs("Xác thực tập trung: ", { bold: true }), runs("Keycloak cấp JWT RS256; Gateway validate qua JWKS, strip/overwrite identity headers, inject correlation ID + gateway secret; downstream chỉ tin context đi qua ranh giới này.", {})]));
push(bullet([runs("Phân quyền theo vai trò: ", { bold: true }), runs("RBAC ở từng dịch vụ; ngoài role còn kiểm tra ownership (vd chỉ buyer của hợp đồng mới confirm được).", {})]));
push(bullet([runs("Ranh giới nội bộ: ", { bold: true }), runs("giao tiếp service-to-service siết bằng service secret/mTLS; downstream chỉ tin identity headers khi X-Gateway-Secret hợp lệ.", {})]));
push(bullet([runs("Bảo toàn dữ liệu nhạy cảm: ", { bold: true }), runs("không log số dư/giao dịch; audit trail bất biến làm evidence khi điều tra.", {})]));
push(P("Route policy Gateway: public chỉ GET catalog/listing, price và GET security/audit-hash được liệt kê exact method/path; reputation public-summary là authenticated. Daily admin paths cho ADMIN|OPERATOR; reputation propose cho ADMIN|OPERATOR nhưng approve/reject, security, audit và analytics admin chỉ ADMIN; /api/v1/inspector/** gate INSPECTOR; /internal/** và notification API không có external route. Emergency lock/unlock dùng chữ ký External Verifier, không có Admin bypass. Gateway không enforce ownership/KYC/reputation lock — các quyết định đó thuộc domain service."));
push(legal("Nghị định 52/2024/NĐ-CP, Điều 8 Khoản 4", "Nghiêm cấm tiết lộ thông tin số dư và giao dịch của khách hàng. RBAC + không-log-nhạy-cảm + mTLS là biện pháp kỹ thuật đáp ứng yêu cầu này."));
push(H2("3.6 Quản trị dữ liệu, maker-checker và trust boundary"));
push(P("Không tạo governance-service. Mỗi domain service chịu trách nhiệm policy trên dữ liệu mình sở hữu; audit-service là writer duy nhất của audit DB và bank-service là source of truth duy nhất cho tiền. Threat model gồm cả hạ tầng Admin độc hại: protected branch/CI approval, image digest pin, secret không bake vào image, quyền DB tối thiểu và External Verifier + OTS là các lớp giảm thiểu; prototype không tuyên bố chống được khi CI, runtime, DB và key policy cùng bị compromise."));
push(table([2600, 3100, 3938], ["Hành động", "Maker", "Checker / invariant"], [
  ["Mở khoá sớm", "OPERATOR hoặc ADMIN propose", "ADMIN approve/reject; approvedBy ≠ proposedBy; ghi governance_action_request + lock_override_event"],
  ["Clear ELEVATED_RISK", "OPERATOR hoặc ADMIN propose", "ADMIN approve/reject; approvedBy ≠ proposedBy; publish override event cho audit"],
], { size: 17 }));
push(H2("3.7 Retention, legal hold và deletion workflow"));
push(P("Retention matrix thuộc data governance: hợp đồng/ledger/audit 10 năm; KYC 5 năm sau khi quan hệ chấm dứt; evidence EUDR ít nhất 5 năm; domain giữ file lâu nhất thắng. legalHold do ADMIN đặt và chặn mọi xoá. File deletion hai bước idempotent: tombstone deletedAt/deletionReason trước, xoá blob MinIO sau; lifecycle job của owning service gọi DeleteFile. Audit content tối giản, không chứa PII để source record vẫn có thể xoá/anonymize theo workflow."));

// ============================================================
// 4. CONCEPTUAL DATA MODEL
// ============================================================
push(H1("4. Mô hình dữ liệu tổng thể"));
push(P("Vì mỗi dịch vụ sở hữu DB riêng, không tồn tại một ERD toàn cục với khoá ngoại xuyên dịch vụ. Thay vào đó, mô hình dữ liệu tổng thể là một bản đồ tham chiếu cross-service: aggregate nào thuộc dịch vụ nào, và tham chiếu ra ngoài bằng plain UUID tới aggregate nào. Lược đồ bảng chi tiết của từng dịch vụ nằm ở các phần SDS kế tiếp."));
push(table(
  [2600, 2100, 4938],
  ["Aggregate / Bảng", "Dịch vụ sở hữu", "Tham chiếu ra ngoài (plain UUID)"],
  [
    ["UserProfile", "user-service", "Keycloak sub (nội bộ Keycloak)"],
    ["Product, Listing, PlotRegistryEntry, ProductPlot", "product-service", "sellerId → user; originalKmlFileId, cadastralExtractFileId → file"],
    ["Contract, Milestone, Signature, SignatureOtp", "contract-service", "buyerId/sellerId → user; listingId → product; evidenceFileId → file; signature chỉ BUYER/SELLER; OTP bind user/contract/role/terms hash"],
    ["EscrowAccount, EscrowMilestone", "escrow-service", "contractId → contract; milestoneId → contract.milestone"],
    ["LedgerEntry", "bank-service", "contractId, milestoneId, userId; sourceEventId (idempotency)"],
    ["InspectionReport, InspectorSignature, Level2Commission", "inspection-service", "contractId, milestoneId; signerUserId → user; reportFileId → file"],
    ["LockEntry, LockOverrideEvent, GovernanceActionRequest, PairRiskState", "reputation-service", "userId/pair → user; contractId; sourceEventId"],
    ["File", "file-service", "uploadedBy; retentionUntil; legalHold; tombstone deletedAt/deletionReason"],
    ["PriceQuote", "pricing-service", "— (không tham chiếu cross-service)"],
    ["AuditRecord, AuditAnchor", "audit-service", "subjectType/subjectId; sourceEventType; sourceHash; canonical recordHash; prevHashSubject"],
    ["NotificationLog", "notification-service", "eventId; recipient userId/email; notificationType; templateVersion"],
    ["dim_contract, fact_*, agg_*", "analytics-service", "contractId (denormalized từ event)"],
  ],
  { size: 17 }
));
push(P([runs("Nguyên tắc bảo toàn integrity không có FK cross-DB: ", { bold: true }), runs("mỗi tham chiếu được xác thực ở tầng use-case của dịch vụ sở hữu (vd escrow-service kiểm tra contractId tồn tại qua event đã nhận, không JOIN sang contract_db). Dữ liệu cần cho hiển thị/nghiệp vụ được đưa qua event payload hoặc denormalize lúc tạo bản ghi, không truy vấn chéo DB.", {})]));

// ============================================================
// 5. NON-FUNCTIONAL REQUIREMENTS
// ============================================================
push(H1("5. Yêu cầu phi chức năng"));
push(P("Khôi phục sau sự cố: backup MySQL hằng ngày; bank/audit dùng binlog để đạt RPO mục tiêu ≤15 phút; MinIO mirror/versioning. Sau restore phải chạy VerifyChainJob và LedgerAuditReconciliationJob pass trước khi mở traffic; maintenance mode đặt tại Gateway/deployment, không lạm dụng system_lock ngân hàng."));
push(table(
  [2500, 7138],
  ["Nhóm", "Yêu cầu & cơ chế đáp ứng"],
  [
    ["Tính đúng đắn giao dịch", "Nhất quán mạnh trong phạm vi một dịch vụ (DB transaction + optimistic lock @Version); nhất quán cuối (eventual) giữa các dịch vụ qua Outbox + idempotency. Đường tiền tệ không dùng eventual consistency mà đợi confirmation event"],
    ["Khả năng kiểm toán & tuân thủ", "AuditRecord/AuditAnchor append-only, sourceHash tách recordHash, neo email + OTS; retention theo ma trận + legal hold; xuất gói bằng chứng DDS-supporting PDF/CSV"],
    ["Bảo mật", "Keycloak RBAC + ownership check; JWT RS256; X-Internal-Secret → mTLS; không log dữ liệu nhạy cảm; hash chống sửa DB"],
    ["Khả năng mở rộng", "Stateless service phía sau Gateway; database-per-service cho phép scale độc lập; RabbitMQ tách tải async khỏi request đồng bộ"],
    ["Tính sẵn sàng & chịu lỗi", "Circuit breaker (Resilience4j) trên Feign; retry + DLX cho message; job nền (scheduler) tự sống sót qua lỗi, không kéo sập"],
    ["Hiệu năng đọc", "CQRS read model (analytics) + pre-compute @Scheduled; Redis cache cho giá tham chiếu; tránh query tổng hợp nặng real-time"],
    ["Khả năng bảo trì", "DDD rich model, ranh giới dịch vụ rõ; chuẩn API/event/dữ liệu thống nhất; Flyway migration versioned per-service"],
    ["Khả năng quan sát (observability)", "Correlation/trace id xuyên request; Micrometer Tracing với OpenTelemetry hoặc Brave, export sang Zipkin/OTLP; ELK cho log; Prometheus + Grafana cho metrics"],
  ],
  { size: 18 }
));

// ============================================================
// 6. LIMITATIONS / NEXT
// ============================================================
push(H1(IS_MERGED ? "6. Cách đọc các phần tiếp theo" : "6. Giới hạn của phần này và nội dung kế tiếp"));
push(P(IS_MERGED
  ? "Phần 1 đặt nền tảng và chuẩn chung; các nội dung dưới đây được đặc tả tại các phần 2–5 ngay sau phần này:"
  : "Phần 1 chỉ đặt nền tảng và chuẩn chung; các nội dung sau thuộc phạm vi các phần kế tiếp và chưa được đặc tả ở đây:"));
push(bullet("Use case chi tiết và sơ đồ trình tự (sequence) cho từng luồng nghiệp vụ của mỗi dịch vụ."));
push(bullet("Đặc tả API mức endpoint (request/response schema, mã lỗi cụ thể) cho từng dịch vụ."));
push(bullet("Lược đồ dữ liệu mức bảng (DDL đầy đủ, index, ràng buộc) cho từng dịch vụ."));
push(bullet("Chi tiết cấu hình theo môi trường (application.yml) và các tham số có thể chỉnh (thresholds, windows, rates)."));
push(callout("Ghi chú.", "Các giả định mô phỏng (bank-service, tiếp nhận report Level 2) và các giới hạn cần xác nhận nghiệp vụ được đánh dấu rõ tại phần dịch vụ tương ứng; các quyết định đã đóng không được liệt kê lại như điểm treo.", "note"));

module.exports = { body };

if (require.main === module) {
push(endMark());

const doc = buildDoc(body, {
  title: "AgriContract — Software Design Specification (SDS) — Phần 1",
  headerText: "AgriContract · SDS — Phần 1",
  footerText: "SDS v1.0 · Phần 1 · Tháng 7/2026",
});
Packer.toBuffer(doc).then(buf => { writeDocx("/tmp/AgriContract_04_SDS_Part1_v1.docx", buf); });

}
