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

const IS_MAIN = require.main === module;
if (IS_MAIN) { push(...cover("AGRICONTRACT", "Software Design Specification (SDS)",
  "Phần 5 — Cụm ngoại vi & tổng hợp: user-service · notification-service · analytics-service + Phụ lục",
  ["Tài liệu nội bộ — Đồ án Tốt nghiệp", "Phiên bản 1.0 · Phần 5/5 · Tháng 7/2026"])); push(...toc()); }

// ============================================================
// 1. SCOPE
// ============================================================
push(H1("1. Phạm vi phần 5"));
push(P("Phần 5 đặc tả cụm ngoại vi và tổng hợp — ba dịch vụ hỗ trợ định danh, liên lạc và phân tích, cùng hai phụ lục hợp nhất toàn hệ thống: user-service (cầu nối định danh, KYC, cưỡng chế khoá), notification-service (thông báo, OTP, neo hash qua email), analytics-service (read model CQRS phục vụ dashboard quản trị). Tuân theo chuẩn dùng chung ở Phần 1."));
push(table(
  [2300, 7338],
  ["Dịch vụ", "Sở hữu"],
  [
    ["user-service", "Cầu nối Keycloak; profile tổ chức; xác minh thẩm quyền đại diện (KYC); cưỡng chế khoá tài khoản theo quyết định từ reputation-service. KHÔNG giữ credentials (Keycloak giữ)"],
    ["notification-service", "Thông báo hướng sự kiện (email/in-app); gửi OTP ký; neo hash qua email; weekly digest; alert khi verify chain fail. Pure consumer"],
    ["analytics-service", "Read model CQRS phục vụ dashboard hiệp hội/Admin. Pure consumer — KHÔNG gọi ngược Feign về dịch vụ nào"],
  ],
  { size: 18 }
));

// ============================================================
// 2. USER-SERVICE
// ============================================================
push(H1("2. user-service"));
push(H2("2.1 Trách nhiệm & mô hình miền"));
push(P("Keycloak là IAM server giữ credentials và cấp JWT; user-service chỉ giữ dữ liệu profile nghiệp vụ và trạng thái xác minh, khoá userId theo claim sub của Keycloak."));
push(codeblock([
  "CREATE TABLE app_user (",
  "  user_id                  UUID PRIMARY KEY,     -- = Keycloak sub",
  "  organization_name        VARCHAR(255) NOT NULL,",
  "  role                     VARCHAR(15) NOT NULL, -- BUYER|SELLER|ADMIN|INSPECTOR",
  "  verification_status      VARCHAR(20) NOT NULL DEFAULT 'PENDING', -- PENDING|VERIFIED|REJECTED",
  "  authorization_expires_at TIMESTAMP NULL,       -- nhập tay từ giấy tờ; NULL = vô thời hạn",
  "  locked_until             TIMESTAMP NULL,       -- cache từ reputation.locked/unlocked",
  "  created_at               TIMESTAMP NOT NULL, updated_at TIMESTAMP NOT NULL",
  ");",
]));

push(H2("2.2 Xác minh thẩm quyền đại diện (KYC)"));
push(P("Gate lúc đăng ký tài khoản, fail-closed — chưa qua duyệt thì không tạo được listing/không ký được hợp đồng. Áp dụng ĐỐI XỨNG cho cả buyer và seller (Điều 142 không phân biệt bên nào yếu thế). Nội dung xác minh khác theo vai trò:"));
push(bullet([runs("BUYER / SELLER: ", { bold: true }), runs("thẩm quyền đại diện — người ký có phải người có quyền đại diện pháp nhân (giấy đăng ký kinh doanh + xác nhận người đại diện/giấy uỷ quyền).", {})]));
push(bullet([runs("INSPECTOR (Level 1.5): ", { bold: true }), runs("giấy phép hoạt động kiểm định thương mại — tổ chức có đủ tư cách phát hành report làm bằng chứng.", {})]));
push(P("authorizationExpiresAt nhập tay bởi Admin lúc duyệt, đọc trực tiếp từ ngày hết hạn ghi trên giấy tờ — KHÔNG hardcode một hằng số chung (giấy tờ khác nhau có hạn khác nhau; hardcode gây chặn nhầm người còn hạn dài hoặc cho ký khi giấy đã hết hạn thật). NULL nếu vô thời hạn. Khác tầng với session freshness (đo bằng phút, xác nhận vừa xác thực) — authorization expiry đo bằng tháng/năm, xác nhận thẩm quyền còn hiệu lực; hai câu hỏi độc lập."));
push(legal("Bộ luật Dân sự 2015, Điều 142", "Giao dịch do người không có thẩm quyền đại diện xác lập có thể bị tuyên vô hiệu. Gate xác minh trước khi kích hoạt tài khoản (fail-closed) là biện pháp kiến trúc trực tiếp chống rủi ro hợp đồng vô hiệu."));

push(H2("2.3 Cưỡng chế khoá tài khoản"));
push(P("Không thể chặn ở Keycloak (chỉ biết identity + role, không biết business lock state). user-service là nơi enforce thật:"));
push(numbered("Consume reputation.locked / reputation.unlocked → cập nhật cache lockedUntil trên app_user (không gọi sync mỗi lần check — cache state flag là ổn, không phải dual-write)."));
push(numbered("Expose CheckLockStatus qua Feign cho contract-service sign() (fail-closed) — trả lockedUntil hiện tại."));
push(numbered("product-service CreateListing gọi kiểm tra fail-open (chưa có Feign client, thêm mới); sign() fail-closed + @CircuitBreaker (ưu tiên đóng gap này trước mọi Feign call khác)."));
push(P("Hai tầng không thừa nhau: khoá chỉ chặn tạo mới, không hồi tố hợp đồng đã ACTIVE/SIGNED. Seller sạch lúc tạo offer, dính khoá giữa đàm phán — chỉ sign() check lại mới bắt được."));

push(H2("2.4 API"));
push(table(
  [4400, 1500, 3738],
  ["Endpoint", "Vai trò", "Mô tả"],
  [
    ["POST /api/v1/users/register", "công khai", "Đăng ký + nộp hồ sơ KYC (PENDING)"],
    ["POST /api/v1/users/{id}/verify", "ADMIN", "Duyệt KYC, nhập authorizationExpiresAt"],
    ["GET /api/v1/users/{id}/lock-status", "nội bộ (Feign)", "CheckLockStatus — trả lockedUntil"],
    ["GET /api/v1/users/{id}", "các bên", "Profile (không lộ dữ liệu nhạy cảm ngoài phạm vi vai trò)"],
  ],
  { size: 17, colAlign: [null, AlignmentType.CENTER, null] }
));

// ============================================================
// 3. NOTIFICATION-SERVICE
// ============================================================
push(H1("3. notification-service"));
push(P("Pure consumer sự kiện — không sở hữu nghiệp vụ lõi. Ngoài thông báo thường (email/in-app), gánh bốn vai trò gắn với bảo mật/bằng chứng:"));
push(table(
  [3000, 6638],
  ["Vai trò", "Cơ chế"],
  [
    ["Gửi OTP ký", "InitiateSign → gửi email OTP kèm signedContentHash (vừa xác thực người, vừa gắn đúng nội dung ký)"],
    ["Neo hash qua email", "Sau mỗi sign() và mỗi inspection report submit → gửi hash cho cả hai bên (điểm neo NGOÀI nền tảng); đính .ots tại milestone.settled"],
    ["Weekly digest", "Hash toàn chuỗi gửi nhiều địa chỉ phía Software Buyer (không chỉ một người)"],
    ["Alert verify-fail", "VerifyChainJob fail → gửi SONG SONG Admin + nhiều contact Software Buyer, không qua gatekeeper"],
  ],
  { size: 18 }
));
push(P([runs("Idempotency & retry: ", { bold: true }), runs("dedup theo eventId (một event chỉ gửi một lần dù RabbitMQ at-least-once). Retry 3 lần exponential backoff trước khi vào dead-letter exchange. Gửi email qua SendGrid (hạ tầng dùng chung với OTP và IMAP intake).", {})]));
push(codeblock([
  "CREATE TABLE notification_log (",
  "  notification_id UUID PRIMARY KEY,",
  "  event_id        UUID NOT NULL UNIQUE,   -- dedup key (at-least-once)",
  "  recipient_id    UUID NOT NULL,          -- userId người nhận",
  "  channel         VARCHAR(10) NOT NULL,   -- EMAIL | IN_APP",
  "  template_code   VARCHAR(50) NOT NULL,",
  "  status          VARCHAR(10) NOT NULL,   -- SENT | FAILED",
  "  sent_at         TIMESTAMP NOT NULL",
  ");",
  "CREATE TABLE email_template (",
  "  template_code VARCHAR(50) PRIMARY KEY, subject VARCHAR(255) NOT NULL, body_html TEXT NOT NULL",
  ");",
]));

// ============================================================
// 4. ANALYTICS-SERVICE
// ============================================================
push(H1("4. analytics-service"));
push(H2("4.1 Nguyên tắc pure consumer"));
push(P("analytics-service là PURE CONSUMER — không sở hữu quy trình nghiệp vụ lõi, KHÔNG gọi ngược (REST/Feign) về dịch vụ khác. Mọi dữ liệu đến từ việc nghe RabbitMQ. Thiết kế cứng này vì analytics là non-critical path: dù analytics sập/quá tải vì query báo cáo nặng, nó tuyệt đối không được tạo tải ngược (cascading failure) lên contract-service/bank-service đang xử lý giao dịch. Stack giữ ở mức MySQL + Redis + RabbitMQ + @Scheduled — loại hẳn ClickHouse/Elasticsearch/Kafka/Spark (over-engineer với tần suất giao dịch thấp của B2B forward contract)."));

push(H2("4.2 Read model — Star schema thu nhỏ (hybrid)"));
push(P("Tách bảng Dimension (thông tin tĩnh) và Fact/Aggregate (số liệu). Kết hợp hai chiến lược để vừa ghi nhanh vừa đọc nhanh:"));
push(bullet([runs("Dimension (dim_contract): ", { bold: true }), runs("populate từ contract.signed (mới, xem Phần 2 §7) — event cấp Contract publish đúng 1 lần lúc SIGNED, mang commodity/buyerId/sellerId/totalAmount. Đây là điểm ingest duy nhất của dim_contract; trước rà soát 06/07/2026, bảng này tồn tại trên schema nhưng chưa từng được wire vào event nào.", {})]));
push(bullet([runs("Fact tables (ghi incremental, không khoá bảng): ", { bold: true }), runs("lưu event thô append-only — fact_milestone_performance (granularity Milestone), fact_contract_settlement (mới, granularity Contract, nguồn contract.settled), fact_contract_cancellation (granularity Contract, nguồn contract.cancelled). Ghi nhanh, triệt tiêu table-lock contention.", {})]));
push(bullet([runs("Aggregate tables (pre-computed, đọc O(1)): ", { bold: true }), runs("agg_monthly_commodity_stats — do job @Scheduled quét fact mỗi đêm, tính sẵn rate (%) ghi thẳng vào cột, API chỉ SELECT.", {})]));
push(callout("Idempotency bắt buộc.", "RabbitMQ at-least-once: nếu milestone.settled bị gửi lại 2 lần, số tiền báo cáo bị nhân đôi — analytics nhạy cảm không kém bank. Bảng analytics_idempotency_log theo message_id chặn xử lý trùng.", "note"));
push(callout("Bug fix rà soát 06/07/2026 — granularity sai + race condition.", "(1) total_contracts_settled không thể đếm đúng từ fact_milestone_performance (granularity Milestone ≠ Contract — một hợp đồng dở dang vẫn có milestone đã SETTLED). Cần fact_contract_settlement riêng, nguồn contract.settled. (2) has_force_majeure hầu như luôn sai nếu chỉ UPDATE trực tiếp: milestone.force_majeure_resolved luôn tới TRƯỚC khi milestone đạt trạng thái cuối, nên fact row chưa tồn tại lúc UPDATE chạy. Giải pháp: bảng staging pending_force_majeure_flag — ghi tạm lúc resolved tới sớm, merge vào đúng lúc INSERT fact row.", "risk"));
push(codeblock([
  "CREATE TABLE dim_contract (",
  "  contract_id UUID PRIMARY KEY, commodity VARCHAR(50) NOT NULL,",
  "  buyer_id UUID NOT NULL, seller_id UUID NOT NULL, total_amount DECIMAL(15,2) NOT NULL,",
  "  signed_at TIMESTAMP NOT NULL",
  ");",
  "CREATE TABLE fact_milestone_performance (",
  "  fact_id UUID PRIMARY KEY, contract_id UUID NOT NULL, milestone_id UUID NOT NULL,",
  "  status VARCHAR(20) NOT NULL,           -- SETTLED | CANCELLED_WITH_PENALTY",
  "  locked_amount DECIMAL(15,2) NOT NULL, actual_amount DECIMAL(15,2) NOT NULL,",
  "  delta_shortfall_amount DECIMAL(15,2) NOT NULL,  -- locked - actual",
  "  has_force_majeure BOOLEAN NOT NULL DEFAULT FALSE, resolved_at TIMESTAMP NOT NULL",
  ");",
  "CREATE TABLE fact_contract_settlement (         -- mới 06/07/2026, xem 4.2",
  "  fact_id UUID PRIMARY KEY, contract_id UUID NOT NULL, settled_at TIMESTAMP NOT NULL",
  ");",
  "CREATE TABLE fact_contract_cancellation (",
  "  fact_id UUID PRIMARY KEY, contract_id UUID NOT NULL,",
  "  initiated_by VARCHAR(10) NOT NULL, cancelled_at TIMESTAMP NOT NULL",
  ");",
  "CREATE TABLE pending_force_majeure_flag (       -- staging, mới 06/07/2026, xem 4.2",
  "  milestone_id UUID PRIMARY KEY, flagged_at TIMESTAMP NOT NULL",
  ");",
  "CREATE TABLE agg_monthly_commodity_stats (",
  "  month_id VARCHAR(7) NOT NULL, commodity VARCHAR(50) NOT NULL,",
  "  total_contracts_settled INT NOT NULL DEFAULT 0, total_contracts_cancelled INT NOT NULL DEFAULT 0,",
  "  cancellation_rate DECIMAL(5,4) NOT NULL DEFAULT 0, force_majeure_incidents INT NOT NULL DEFAULT 0,",
  "  total_value_settled DECIMAL(15,2) NOT NULL DEFAULT 0, escrow_efficiency_rate DECIMAL(5,4) NOT NULL DEFAULT 0,",
  "  PRIMARY KEY (month_id, commodity)",
  ");",
  "CREATE TABLE analytics_idempotency_log ( message_id VARCHAR(255) PRIMARY KEY, processed_at TIMESTAMP NOT NULL DEFAULT now() );",
]));

push(H2("4.3 Batch job & API"));
push(P("MonthlyAggregationJob (@Scheduled) chạy 1:00 sáng mỗi ngày (off-peak, trước VerifyChainJob của audit lúc 2:00 để tránh giành tài nguyên DB). Quét fact_contract_settlement + fact_contract_cancellation (JOIN dim_contract lấy commodity — granularity Contract, cho total_contracts_settled/total_contracts_cancelled) và fact_milestone_performance (JOIN dim_contract, granularity Milestone, cho total_value_settled/escrow_efficiency_rate/force_majeure_incidents) — hai nguồn khác granularity, upsert đúng cột tương ứng vào agg_monthly_commodity_stats tháng hiện tại, tính và ghi đè rate. API cache Redis TTL 1 giờ (phân tích vĩ mô không cần fresh tới giây)."));
push(table(
  [4700, 4938],
  ["Endpoint", "Ý nghĩa quản trị (VICOFA/VRA/Admin)"],
  [
    ["GET /api/v1/analytics/cancellations?year={YYYY}", "Tỷ lệ phá vỡ hợp đồng theo ngành hàng — đo lòng tin vào hợp đồng forward"],
    ["GET /api/v1/analytics/escrow-effectiveness?month={YYYY-MM}", "Hiệu quả ký quỹ theo đợt — tiền khoá có bám sát thực nhận không, hay khoá dư"],
    ["GET /api/v1/analytics/force-majeure-trends?year={YYYY}", "Xu hướng bất khả kháng theo mùa vụ/ngành — báo cáo chuỗi cung ứng cho Bộ NN&MT"],
  ],
  { size: 18 }
));

push(H2("4.4 Giới hạn (tradeoff CQRS/event-driven)"));
push(bullet([runs("Eventual consistency & data lag. ", { bold: true }), runs("Dashboard không khớp tới từng VNĐ theo thời gian thực: lag hệ thống (RabbitMQ) + lag nghiệp vụ (agg chỉ cập nhật qua batch 1:00 sáng — thay đổi trong ngày phản ánh vào biểu đồ % từ ngày mai).", {})]));
push(bullet([runs("Không có historical backfill. ", { bold: true }), runs("Pure consumer không gọi ngược — deploy giữa chừng thì chỉ ghi nhận data từ ngày deploy; hợp đồng cũ không có trên dashboard trừ khi replay event từ audit-service. Đây là cái giá thật của event-driven.", {})]));
push(bullet([runs("Không bắt được data-fix tay. ", { bold: true }), runs("Nếu Admin sửa lịch sử giao dịch bằng SQL trực tiếp, không event nào sinh ra → analytics vĩnh viễn lệch so với DB gốc.", {})]));

// ============================================================
// 5. APPENDIX A — EVENT CATALOG
// ============================================================
push(H1("Phụ lục A — Event Catalog hợp nhất"));
push(P("Toàn bộ sự kiện RabbitMQ xuyên hệ thống. Convention: {aggregate}.{actor}_{past_tense_verb}; mỗi message mang eventId + occurredAt; consumer dedup theo eventId/sourceEventId. Sự kiện Local Outbox (đồng bộ Milestone→Contract) không qua RabbitMQ, không liệt kê ở đây."));
push(table(
  [3300, 2100, 4238],
  ["Routing key", "Publisher", "Consumer(s)"],
  [
    ["contract.signed", "contract-service", "escrow, notification, audit, analytics"],
    ["contract.settled", "contract-service", "escrow (hoàn cọc), reputation, notification, analytics"],
    ["contract.cancelled", "contract-service", "escrow (seize/refund cọc), notification, analytics"],
    ["milestone.seller_weighed", "contract-service", "file, notification, audit"],
    ["milestone.buyer_confirmed", "contract-service", "escrow, notification, audit"],
    ["milestone.flagged", "contract-service", "notification"],
    ["milestone.force_majeure_claimed", "contract-service", "notification (Admin), audit"],
    ["milestone.force_majeure_resolved", "contract-service", "escrow, notification, audit"],
    ["milestone.settled", "contract-service", "escrow, notification, reputation, analytics, audit"],
    ["milestone.cancelled_with_penalty", "contract-service", "escrow, reputation, analytics, notification, audit"],
    ["bank.lock/release/seize/refund_requested", "escrow-service", "bank-service"],
    ["bank.lock/release/seize/refund_completed | _failed", "bank-service", "escrow-service"],
    ["reputation.locked | reputation.unlocked", "reputation-service", "user-service"],
    ["file.uploaded.direct | file.email.received", "file-service", "file-service (queue nội bộ)"],
    ["file.ready | file.failed", "file-service", "dịch vụ sở hữu fileId (contract, inspection, product)"],
  ],
  { size: 17 }
));
push(P("Ghi hash vào audit-service (source_type: MILESTONE_EVENT, CONTRACT_SIGNED, INSPECTION_REPORT, EXTERNAL_INSPECTION_REPORT, LEVEL2_INSPECTION_COMMISSIONED) đi qua consumer của audit trên các event tương ứng, không phải routing key riêng."));

// ============================================================
// 6. APPENDIX B — SERVICE MATRIX
// ============================================================
push(H1("Phụ lục B — Ma trận dịch vụ"));
push(table(
  [2100, 700, 2000, 2100, 2738],
  ["Dịch vụ", "Port", "DB", "Aggregate chính", "Căn cứ pháp lý liên quan"],
  [
    ["user-service", "8081", "user_db", "UserProfile", "BLDS Điều 142 (KYC)"],
    ["product-service", "8082", "product_db", "Product, PlotRegistryEntry", "EUDR (geolocation)"],
    ["contract-service", "8083", "contract_db", "Contract, Milestone, Signature", "GDĐT Điều 22-23, 34; BLDS 156/351"],
    ["escrow-service", "8084", "escrow_db", "EscrowAccount, EscrowMilestone", "—"],
    ["notification-service", "8085", "notification_db", "NotificationLog", "GDĐT 14.2 (email anchor)"],
    ["bank-service", "8086", "bank_db", "LedgerEntry", "NĐ 52/2024 Điều 8.7"],
    ["inspection-service", "8087", "inspection_db", "InspectionReport, Level2Commission", "NĐ 98/2018 Điều 15"],
    ["reputation-service", "8088", "reputation_db", "LockEntry", "LTM Điều 302; NĐ 52/2024 Điều 29 (AML)"],
    ["file-service", "8089", "file_db + MinIO", "File", "EUDR Điều 9 (retention 5 năm)"],
    ["pricing-service", "8091", "pricing_db + Redis", "PriceQuote", "—"],
    ["audit-service", "8092", "audit_db", "AuditRecord", "GDĐT Điều 14.2"],
    ["analytics-service", "8093", "analytics_db", "dim/fact/agg", "—"],
  ],
  { size: 16 }
));
push(P("(Port 8090 bỏ trống — search-service đã gộp thành 2 tham số filter trong product-service.)"));

// ============================================================
// 7. OPEN ITEMS
// ============================================================
push(H1("Điểm còn treo tổng hợp"));
push(bullet([runs("Payload event mang commodity — đã giải quyết (06/07/2026). ", { bold: true }), runs("contract.signed (mới) mang {commodity, buyerId, sellerId, totalAmount} để analytics-service populate dim_contract không cần Feign ngược. Còn treo cấp thấp hơn: bảng mapping Product.category (tiếng Việt) → commodity enum dùng chung (COFFEE/RICE/RUBBER/CASHEW) cần xác nhận nghiệp vụ trước khi contract-service code phần publish.", {})]));
push(bullet([runs("Checklist KYC theo loại hình doanh nghiệp ", { bold: true }), runs("(buyer TNHH/cổ phần/hộ kinh doanh; giấy tờ INSPECTOR) — nguyên tắc đã chốt, danh mục cụ thể cần xác nhận nghiệp vụ.", {})]));
push(bullet([runs("search-service ", { bold: true }), runs("chưa thiết kế — kỳ vọng consume event khi reputation score / listing đổi để cập nhật bản denormalized.", {})]));

if (IS_MAIN) push(endMark());

module.exports = { body };

if (IS_MAIN) {
  const doc = buildDoc(body, {
    title: "AgriContract — SDS — Phần 5: Ngoại vi & Tổng hợp (User · Notification · Analytics)",
    headerText: "AgriContract · SDS — Phần 5",
    footerText: "SDS v1.0 · Phần 5 · Tháng 7/2026",
  });
  Packer.toBuffer(doc).then(buf => { fs.writeFileSync("/tmp/AgriContract_08_SDS_Part5_v1.docx", buf); console.log("written", buf.length); });
}
