const fs = require("fs");
const { writeDocx } = require("./docx_output.js");
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
push(P("Phần 5 đặc tả cụm ngoại vi và tổng hợp — ba dịch vụ hỗ trợ định danh, liên lạc và phân tích, cùng hai phụ lục hợp nhất toàn hệ thống: user-service (cầu nối định danh, KYC, cưỡng chế khoá), notification-service (thông báo, OTP, neo hash qua email), analytics-service (event-driven projection/CQRS read model kiêm derived-signal producer). Tuân theo chuẩn dùng chung ở Phần 1."));
push(table(
  [2300, 7338],
  ["Dịch vụ", "Sở hữu"],
  [
    ["user-service", "Cầu nối Keycloak; profile tổ chức; xác minh thẩm quyền đại diện (KYC); cưỡng chế khoá tài khoản theo quyết định từ reputation-service. KHÔNG giữ credentials (Keycloak giữ)"],
    ["notification-service", "Thông báo hướng sự kiện; gửi OTP qua internal sync API; neo hash qua email; weekly digest; alert khi verify chain fail. Không sở hữu OTP/hash hay quyết định recipient"],
    ["analytics-service", "Event-driven projection/CQRS read model kiêm derived-signal producer. Không trên transaction critical path; không gọi sync REST/Feign ngược core service"],
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
  "CREATE TABLE user_profiles (",
  "  id                       BIGINT AUTO_INCREMENT PRIMARY KEY,",
  "  user_id                  VARCHAR(255) NOT NULL UNIQUE, -- = Keycloak sub",
  "  organization_name        VARCHAR(255) NOT NULL,",
  "  role                     VARCHAR(15) NOT NULL,       -- BUYER|SELLER|INSPECTOR|OPERATOR|ADMIN",
  "  email                    VARCHAR(255) NOT NULL, phone VARCHAR(50) NULL, address TEXT NULL,",
  "  verification_status      VARCHAR(20) NOT NULL DEFAULT 'PENDING', -- PENDING|VERIFIED|REJECTED",
  "  authorization_expires_at TIMESTAMP NULL,       -- nhập tay từ giấy tờ; NULL = vô thời hạn",
  "  locked_until             TIMESTAMP(3) NULL,      -- projection từ reputation",
  "  last_lock_revision       BIGINT NOT NULL DEFAULT 0,",
  "  verified_by_actor_id     VARCHAR(255) NULL, verified_at TIMESTAMP(3) NULL,",
  "  rejection_reason         TEXT NULL,",
  "  created_at               TIMESTAMP NOT NULL, updated_at TIMESTAMP NOT NULL",
  ");",
]));

push(H2("2.2 Xác minh thẩm quyền đại diện (KYC)"));
push(P("Gate lúc đăng ký tài khoản, fail-closed — chưa qua duyệt thì không tạo được listing/không ký được hợp đồng. Áp dụng ĐỐI XỨNG cho cả buyer và seller (Điều 142 không phân biệt bên nào yếu thế). Nội dung xác minh khác theo vai trò:"));
push(bullet([runs("BUYER / SELLER: ", { bold: true }), runs("thẩm quyền đại diện — người ký có phải người có quyền đại diện pháp nhân (giấy đăng ký kinh doanh + xác nhận người đại diện/giấy uỷ quyền).", {})]));
push(bullet([runs("INSPECTOR (Level 1.5): ", { bold: true }), runs("giấy phép hoạt động kiểm định thương mại — tổ chức có đủ tư cách phát hành report làm bằng chứng.", {})]));
push(P("authorizationExpiresAt do OPERATOR/ADMIN nhập lúc duyệt, đọc trực tiếp từ ngày hết hạn ghi trên giấy tờ — KHÔNG hardcode một hằng số chung (giấy tờ khác nhau có hạn khác nhau; hardcode gây chặn nhầm người còn hạn dài hoặc cho ký khi giấy đã hết hạn thật). NULL nếu vô thời hạn. Khác tầng với session freshness (đo bằng phút, xác nhận vừa xác thực) — authorization expiry đo bằng tháng/năm, xác nhận thẩm quyền còn hiệu lực; hai câu hỏi độc lập."));
push(legal("Bộ luật Dân sự 2015, Điều 142", "Giao dịch do người không có thẩm quyền đại diện xác lập có thể bị tuyên vô hiệu. Gate xác minh trước khi kích hoạt tài khoản (fail-closed) là biện pháp kiến trúc trực tiếp chống rủi ro hợp đồng vô hiệu."));

push(H2("2.3 Cưỡng chế khoá tài khoản"));
push(P("Không thể chặn ở Keycloak (chỉ biết identity + role, không biết business lock state). user-service là nơi enforce thật:"));
push(numbered("Consume reputation.locked/unlocked kèm lockRevision; chỉ apply atomic khi revision > lastLockRevision. locked dùng max(current,incoming); unlocked set đúng effectiveLockedUntil do reputation tính lại. Sau commit publish notification.user_lock_changed_requested đã enrich recipient email."));
push(numbered("Expose InternalUserInfo qua endpoint /internal/v1/users/{id} cho contract-service sign() (fail-closed) — trả verificationStatus, authorizationExpiresAt và lockedUntil cùng DTO nội bộ."));
push(numbered("CreateListing, CreateOffer và sign() đều kiểm KYC/thẩm quyền/lock fail-closed; user-service down → 503/reject qua circuit-breaker, không cho người chưa KYC/đang khoá lách policy."));
push(P("Hai tầng không thừa nhau: khoá chỉ chặn tạo mới, không hồi tố hợp đồng đã ACTIVE/SIGNED. Seller sạch lúc tạo offer, dính khoá giữa đàm phán — chỉ sign() check lại mới bắt được."));

push(H2("2.4 API"));
push(table(
  [4400, 1500, 3738],
  ["Endpoint", "Vai trò", "Mô tả"],
  [
    ["POST /api/v1/users/register", "authenticated", "Đăng ký profile KYC (PENDING), idempotent theo Keycloak sub"],
    ["GET /api/v1/users/me", "chính chủ", "Profile đầy đủ: contact, KYC, authorization, lock"],
    ["GET /api/v1/users/{id}", "authenticated", "PublicUserResponse — KHÔNG email/phone/address"],
    ["GET /api/v1/admin/users?verificationStatus=PENDING", "ADMIN/OPERATOR", "Hàng đợi duyệt KYC"],
    ["POST /api/v1/admin/users/{id}/verify | /reject", "ADMIN/OPERATOR", "Duyệt hoặc từ chối; verifiedByActorId"],
    ["GET /internal/v1/users/{id}", "service nội bộ", "InternalUserInfoResponse có email/KYC/authorization/lock; Gateway không route"],
  ],
  { size: 17, colAlign: [null, AlignmentType.CENTER, null] }
));

// ============================================================
// 3. NOTIFICATION-SERVICE
// ============================================================
push(H1("3. notification-service"));
push(P("Không sở hữu nghiệp vụ lõi, OTP hay hash. Notification command đi qua RabbitMQ; riêng OTP dùng internal synchronous API để caller chỉ báo đã gửi khi provider accepted. Không có end-user API và Gateway không route bất kỳ notification endpoint nào; recipient/attachment reference do publisher quyết định."));
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
push(P([runs("Recipient, idempotency & retry: ", { bold: true }), runs("publisher mang recipient email trong event; notification không Feign ngược user-service. Dedup theo (eventId hoặc sourceEventId, recipientEmail, notificationType) để một event gửi đúng một lần cho từng người/loại mail. Async retry 3 lần exponential backoff trước DLX. OTP sync không tự gửi muộn sau khi caller đã nhận failure.", {})]));
push(P("Evidence attachment: milestone anchor mail đính .ots; contract anchor render ContractTerms snapshot + signedContentHash trong body. NotificationLog chỉ lưu attachment SHA-256/size, không lưu bytes hay OTP/full body. notification.reconciliation_mismatch_requested chỉ gửi ADMIN, không fan-out và không auto-fix."));
push(codeblock([
  "CREATE TABLE notification_log (",
  "  notification_id CHAR(36) PRIMARY KEY,",
  "  event_id         VARCHAR(255) NOT NULL, event_type VARCHAR(100) NOT NULL,",
  "  notification_type VARCHAR(100) NOT NULL, recipient_id CHAR(36) NULL,",
  "  recipient_email VARCHAR(255) NOT NULL, channel VARCHAR(10) NOT NULL,",
  "  template_version VARCHAR(50) NOT NULL, status VARCHAR(10) NOT NULL, -- PENDING|SENT|FAILED",
  "  retry_count INT NOT NULL DEFAULT 0, provider_message_id VARCHAR(255) NULL,",
  "  attachment_sha256 VARCHAR(64) NULL, attachment_size BIGINT NULL,",
  "  failure_reason TEXT NULL, sent_at TIMESTAMP(3) NULL,",
  "  UNIQUE(event_id, recipient_email, notification_type)",
  ");",
  "-- Evidence/security template version trong code/resources; DB log đúng version đã gửi.",
]));

// ============================================================
// 4. ANALYTICS-SERVICE
// ============================================================
push(H1("4. analytics-service"));
push(H2("4.1 Event-driven projection + derived-signal producer"));
push(P("analytics-service là event-driven projection/CQRS read model kiêm derived-signal producer: dữ liệu vào từ RabbitMQ và AmlPatternScanJob có thể publish signal, nhưng service không nằm trên transaction critical path và không gọi đồng bộ REST/Feign ngược core service. Stack MySQL 8 + Redis + RabbitMQ + @Scheduled; analytics sập không được làm contract/bank sập theo."));

push(H2("4.2 Read model — Star schema thu nhỏ (hybrid)"));
push(P("Tách bảng Dimension (thông tin tĩnh) và Fact/Aggregate (số liệu). Kết hợp hai chiến lược để vừa ghi nhanh vừa đọc nhanh:"));
push(bullet([runs("Dimension (dim_contract): ", { bold: true }), runs("populate từ contract.signed (mới, xem Phần 2 §7) — event cấp Contract publish đúng 1 lần lúc SIGNED, mang commodity/buyerId/sellerId/totalAmount. Đây là điểm ingest duy nhất của dim_contract; trước rà soát 06/07/2026, bảng này tồn tại trên schema nhưng chưa từng được wire vào event nào.", {})]));
push(bullet([runs("Fact tables (ghi incremental, không khoá bảng): ", { bold: true }), runs("lưu event thô append-only — fact_milestone_performance (granularity Milestone), fact_contract_settlement (mới, granularity Contract, nguồn contract.settled), fact_contract_cancellation (granularity Contract, nguồn contract.cancelled). Ghi nhanh, triệt tiêu table-lock contention.", {})]));
push(bullet([runs("Aggregate tables (pre-computed, đọc O(1)): ", { bold: true }), runs("agg_monthly_commodity_stats — do job @Scheduled quét fact mỗi đêm, tính sẵn rate (%) ghi thẳng vào cột, API chỉ SELECT.", {})]));
push(callout("Idempotency bắt buộc.", "RabbitMQ at-least-once: nếu milestone.settled bị gửi lại 2 lần, số tiền báo cáo bị nhân đôi — analytics nhạy cảm không kém bank. Bảng analytics_idempotency_log theo message_id chặn xử lý trùng.", "note"));
push(callout("Bug fix rà soát 06/07/2026 — granularity sai + race condition.", "(1) total_contracts_settled không thể đếm đúng từ fact_milestone_performance (granularity Milestone ≠ Contract — một hợp đồng dở dang vẫn có milestone đã SETTLED). Cần fact_contract_settlement riêng, nguồn contract.settled. (2) has_force_majeure hầu như luôn sai nếu chỉ UPDATE trực tiếp: milestone.force_majeure_resolved luôn tới TRƯỚC khi milestone đạt trạng thái cuối, nên fact row chưa tồn tại lúc UPDATE chạy. Giải pháp: bảng staging pending_force_majeure_flag — ghi tạm lúc resolved tới sớm, merge vào đúng lúc INSERT fact row.", "risk"));
push(codeblock([
  "CREATE TABLE dim_contract (",
  "  contract_id CHAR(36) PRIMARY KEY, commodity VARCHAR(50) NOT NULL,",
  "  buyer_id CHAR(36) NOT NULL, seller_id CHAR(36) NOT NULL, total_amount DECIMAL(15,2) NOT NULL,",
  "  signed_at TIMESTAMP NOT NULL",
  ");",
  "CREATE TABLE fact_milestone_performance (",
  "  fact_id CHAR(36) PRIMARY KEY, contract_id CHAR(36) NOT NULL, milestone_id CHAR(36) NOT NULL,",
  "  status VARCHAR(20) NOT NULL,           -- SETTLED | CANCELLED_WITH_PENALTY",
  "  locked_amount DECIMAL(15,2) NOT NULL, actual_amount DECIMAL(15,2) NOT NULL,",
  "  delta_shortfall_amount DECIMAL(15,2) NOT NULL,  -- locked - actual",
  "  has_force_majeure BOOLEAN NOT NULL DEFAULT FALSE, resolved_at TIMESTAMP NOT NULL",
  ");",
  "CREATE TABLE fact_contract_settlement (         -- mới 06/07/2026, xem 4.2",
  "  fact_id CHAR(36) PRIMARY KEY, contract_id CHAR(36) NOT NULL, settled_at TIMESTAMP NOT NULL",
  ");",
  "CREATE TABLE fact_contract_cancellation (",
  "  fact_id CHAR(36) PRIMARY KEY, contract_id CHAR(36) NOT NULL,",
  "  initiated_by VARCHAR(10) NOT NULL, cancelled_at TIMESTAMP NOT NULL",
  ");",
  "CREATE TABLE pending_force_majeure_flag (       -- staging, mới 06/07/2026, xem 4.2",
  "  milestone_id CHAR(36) PRIMARY KEY, flagged_at TIMESTAMP NOT NULL",
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
push(P([runs("AmlPatternScanJob (@Scheduled, bổ sung 10/07/2026) — tầng batch AML: ", { bold: true }), runs("quét data warehouse tìm cụm near-threshold theo cặp buyer-seller: giá trị trong band [475tr, 500tr), tối thiểu 5 hợp đồng, lookback 90 ngày — bắt structuring chậm rải mỏng dưới ngưỡng tuyệt đối mà 2 tầng realtime (Phần 3, reputation) không với tới. Phát hiện cụm → publish analytics.structuring_pattern_detected; consumer: reputation-service (set cặp ELEVATED_RISK — cơ chế EDD, chi tiết Phần 3) và bank-service (nghĩa vụ báo cáo cơ quan). Analytics chỉ quét projection mình đã có và publish derived signal; không tự hold/flag/báo cáo và không gọi sync ngược core service.", {})]));
push(codeblock([
  "SELECT dc.buyer_id, dc.seller_id, COUNT(*) near_threshold_count,",
  "       JSON_ARRAYAGG(fcs.contract_id) contract_ids",
  "FROM fact_contract_settlement fcs JOIN dim_contract dc USING(contract_id)",
  "WHERE fcs.settled_at >= DATE_SUB(NOW(), INTERVAL 90 DAY)",
  "  AND dc.total_amount >= 475000000 AND dc.total_amount < 500000000",
  "GROUP BY dc.buyer_id, dc.seller_id HAVING COUNT(*) >= 5;",
  "-- MySQL 8 dialect: JSON_ARRAYAGG + DATE_SUB.",
]));
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
push(bullet([runs("Catch-up và backup. ", { bold: true }), runs("Outage ngắn catch-up từ RabbitMQ queue backlog. Không hỗ trợ cold rebuild/full-history và RabbitMQ không phải event archive vô hạn, nên analytics_db phải backup daily như MySQL khác; mất DB không thể chỉ dựa vào queue để phục hồi lịch sử dashboard.", {})]));
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
    ["contract.signed", "contract-service", "escrow, audit, analytics — payload có signedContentHash + deposit amounts"],
    ["contract.settled", "contract-service", "escrow (hoàn cọc), reputation, analytics"],
    ["contract.cancelled", "contract-service", "escrow (seize/refund cọc), analytics"],
    ["milestone.seller_weighed", "contract-service", "file, audit"],
    ["milestone.buyer_confirmed", "contract-service", "audit (bỏ escrow, tránh release tiền 2 lần)"],
    ["milestone.flagged", "contract-service", "không có domain consumer bắt buộc; mail qua notification command"],
    ["milestone.force_majeure_claimed", "contract-service", "audit"],
    ["milestone.force_majeure_resolved", "contract-service", "escrow, audit"],
    ["milestone.settled", "contract-service", "escrow, reputation, analytics, audit (payload propagate recipients để gửi OTS)"],
    ["milestone.cancelled_with_penalty", "contract-service", "escrow, reputation, analytics, audit"],
    ["milestone.dispute_resolved (mới, 08/07/2026)", "contract-service", "reputation (đếm tỷ lệ buyer flag-rồi-thua — tín hiệu chống lạm dụng FLAG_ISSUE, Phần 3 §3.5)"],
    ["bank.lock/release/seize/refund_requested", "escrow-service", "bank-service"],
    ["bank.lock/release/seize/refund_completed | _failed", "bank-service", "escrow-service"],
    ["escrow.deposit_lock_failed", "escrow-service", "contract-service — giữ SIGNED; activation compensation/admin actions"],
    ["escrow.deposit_locked (mới, 08/07/2026)", "escrow-service", "contract-service (chuyển ACTIVE); escrow-service tự dùng để lock batchAmount milestone đầu"],
    ["bank.large_transaction_flagged", "bank-service", "reputation + audit; không tự hold"],
    ["bank.security_lock_changed", "bank-service", "audit-service; action LOCKED|UNLOCKED"],
    ["bank.verifier_key_registered", "bank-service", "audit-service"],
    ["bank.suspicious_report_created", "bank-service", "audit-service; STRUCTURING_REPORT"],
    ["milestone.level2_provisional_settled | _buffer_reconciled | _terminal_settled (08/07/2026)", "contract-service", "escrow (mail qua notification.milestone_status_requested)"],
    ["reputation.locked | reputation.unlocked", "reputation-service", "user-service; payload lockRevision/effectiveLockedUntil/occurredAt"],
    ["reputation.elevated_risk_cleared", "reputation-service", "audit-service; AML_RISK_CLEARED, maker-checker actor IDs"],
    ["analytics.structuring_pattern_detected", "analytics-service", "reputation-service + bank-service"],
    ["inspection.level2_commissioned | inspection.report_confirmed", "inspection-service", "audit-service"],
    ["notification.user_kyc_result_requested | user_lock_changed_requested", "user-service", "notification-service"],
    ["notification.contract_anchor_requested | contract_cancelled_requested | milestone_status_requested", "contract-service", "notification-service"],
    ["notification.contract_activation_failed_requested", "contract-service", "notification-service — Buyer+Seller+Admin"],
    ["notification.milestone_anchor_requested | audit_digest_requested | audit_failure_requested", "audit-service", "notification-service"],
    ["notification.level2_commission_requested", "inspection-service", "notification-service"],
    ["notification.verifier_key_anchor_requested | security_lock_changed_requested | reconciliation_mismatch_requested", "bank-service", "notification-service"],
    ["file.uploaded.direct | file.email.received", "file-service", "file-service (queue nội bộ)"],
    ["file.ready | file.failed | file.email_notice", "file-service", "dịch vụ sở hữu fileId; inspection nhận email handshake"],
  ],
  { size: 17 }
));
push(P("Audit ingestion đầy đủ qua domain event; audit-service là writer duy nhất. source_type: MILESTONE_EVENT, CONTRACT_SIGNED, INSPECTION_REPORT, EXTERNAL_INSPECTION_REPORT, LEVEL2_INSPECTION_COMMISSIONED, EXTERNAL_VERIFIER_KEY_REGISTERED, SECURITY_LOCK_TRIGGERED, SECURITY_UNLOCK_TRIGGERED, STRUCTURING_REPORT, AML_RISK_CLEARED. Subject là CONTRACT/USER_PAIR/SYSTEM; sourceHash tách recordHash."));
push(P([runs("Kill switch trigger và transport audit: ", { bold: true }), runs("External Verifier gọi REST emergency-lock/unlock bằng chữ ký bất đối xứng; bank-service gate mọi lệnh tiền qua system_lock, rồi publish bank.security_lock_changed cho audit và notification command cho người cần biết. Key rotation publish bank.verifier_key_registered.", {})]));

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
    ["inspection-service", "8087", "inspection_db", "InspectionReport, InspectorSignature, Level2Commission", "NĐ 98/2018 Điều 15"],
    ["reputation-service", "8088", "reputation_db", "LockEntry, LockOverrideEvent, GovernanceActionRequest", "LTM Điều 302; NĐ 52/2024 Điều 29 (AML)"],
    ["file-service", "8089", "file_db + MinIO", "File", "EUDR Điều 9 (retention 5 năm)"],
    ["pricing-service", "8091", "pricing_db + Redis", "PriceQuote", "—"],
    ["audit-service", "8092", "audit_db", "AuditRecord, AuditAnchor", "GDĐT khoản 2 Điều 14"],
    ["analytics-service", "8093", "analytics_db", "dim/fact/agg", "—"],
  ],
  { size: 16 }
));
push(P("Tìm kiếm/lọc listing được xử lý trực tiếp bằng query parameter trong product-service."));

// ============================================================
// 7. APPENDIX C — VERIFICATION MATRIX
// ============================================================
push(H1("Phụ lục C — Verification Matrix P0/P1/P2"));
push(P("39 invariant trong bảng design source-of-truth. P0 ưu tiên tiền/immutability/bằng chứng; P1 access control/luồng an toàn; P2 đúng nghiệp vụ. Đây là test plan trước khi viết test coverage thông thường."));
push(table(
  [900, 3000, 3900, 1838],
  ["Mức/#", "Invariant", "Test chứng minh", "Nguồn"],
  [
    ["P0 · 1", "Event lặp không release/lock tiền hai lần", "Publish cùng `eventId` nhiều lần → đúng 1 bộ ledger entry", "bank §4 idempotency"],
    ["P0 · 2", "Ledger append-only — không UPDATE/DELETE", "Repository chỉ có insert/select; test lớp repo + quyền DB user", "bank ledger, governance nguyên tắc 3"],
    ["P0 · 3", "Hợp đồng đã `SIGNED` không sửa được terms", "Gọi update terms sau ký → `409`; hash không đổi", "signature §3, milestone §3.1"],
    ["P0 · 4", "`audit_record` bị sửa phải bị phát hiện", "Tamper trực tiếp DB (đổi amount/hash) → `VerifyChainJob` fail + đúng vị trí", "hash-chain §4.2/§5"],
    ["P0 · 5", "Ledger lệch với record đã anchor phải bị phát hiện", "Sửa 1 ledger entry lệch `settledAmount` → `LedgerAuditReconciliationJob` bắn `reconciliation_mismatch`", "bank §5b.1"],
    ["P0 · 6", "Bank completion callback lặp không nhân đôi", "Gửi duplicate callback → 1 bộ entry, trạng thái không đổi lần 2", "bank §4"],
    ["P0 · 7", "DLQ replay không side effect lặp", "Replay `eventId` gốc sau khi đã xử lý → consumer skip, không entry mới", "governance §8b"],
    ["P0 · 8", "Contract settled không còn tiền lock treo", "Sau `contract.settled`/`cancelled`: tổng lock theo contract = 0 (reconciliation test)", "bank §3.2, escrow"],
    ["P0 · 9", "Kill switch chặn toàn bộ dòng tiền", "`system_lock ACTIVE` → mọi `bank.*_requested` fail có kiểm soát; unlock sai chữ ký/replay nonce → reject", "bank §3.5.2-3.5.5"],
    ["P0 · 10", "Provisional Level 2 không release quá mức sàn khi buyer im lặng", "Hết `disputeOptOutWindowDays` không phản hồi → release mức default sàn", "milestone §3.2 Bước 0"],
    ["P0 · 11", "Khoá cọc fail không kẹt im lặng", "Bank fail liên tục → hết 3 retry → `escrow.deposit_lock_failed` + contract giữ `SIGNED` + notification 3 bên; `RetryDepositLock` sau khi hết lỗi → `ACTIVE`", "milestone §3.1"],
    ["P0 · 11b", "Partial lock không nuốt tiền: buyer LOCKED + seller fail → terminal phải refund", "Mock seller-leg fail vĩnh viễn → `MarkActivationFailed` → `bank.refund_requested` cho leg buyer, qua `ACTIVATION_REFUND_PENDING`, chỉ `ACTIVATION_FAILED` khi refund confirmed; refund fail → đứng lại + alert", "milestone §3.1 (18/07)"],
    ["P0 · 11c", "2 chữ ký phải cùng bản terms", "Buyer ký, đổi terms (phải bị 409); ép seller ký hash khác → REJECT, không SIGNED", "signature §6 bước 4 (18/07)"],
    ["P0 · 11d", "Mọi field record đều được hash commit", "Tamper riêng `source_type`/`subject_id`/`prev_hash_subject` (giữ nguyên content) → `VerifyChainJob` recompute fail", "hash-chain §3 canonical (18/07 r2)"],
    ["P0 · 11e", "`audit_anchor` cũng bất biến", "Repository/DB user chỉ INSERT+SELECT trên audit_anchor; UPDATE/DELETE → denied", "hash-chain §3 (18/07 r2)"],
    ["P0 · 11f", "source_hash 3-way khớp, record_hash verify riêng", "`Contract.signedContentHash` == `audit_record.source_hash` == hash email; sửa 1 trong 3 → reconcile bắt; record_hash KHÔNG so trực tiếp với signedContentHash", "hash-chain §4.1 (18/07 r2)"],
    ["P1 · 12", "User khác không thao tác contract người khác", "Ownership check → `403` (contract, escrow, statement)", "contract/escrow, gateway §4"],
    ["P1 · 13", "Client không tự tiêm identity", "Gửi kèm `X-User-Id`/`X-User-Role`/`X-Gateway-Secret` giả → bị strip/overwrite, downstream nhận identity từ JWT", "gateway §2/§7"],
    ["P1 · 14", "`/internal/**` không ra ngoài", "Gọi từ external → `404/403` tại Gateway, không forward", "gateway §3.5"],
    ["P1 · 15", "File nhiễm virus không thành evidence", "Upload EICAR qua `StoreOnBehalfOf` và qua email intake → `FAILED`, không có `file.ready`, không gắn được vào milestone/report", "file-service §3-4"],
    ["P1 · 16", "OPERATOR không chạm được đường ADMIN", "OPERATOR gọi approve maker-checker / `RetryDepositLock` / `/admin/audit` → `403`", "governance §5.4"],
    ["P1 · 17", "ADMIN không tự approve đề xuất của chính mình", "Cùng 1 account propose rồi approve → reject", "governance §5.3"],
    ["P1 · 18", "Unlock sớm không mở khoá nhầm khi nhiều lock chồng", "2 lock (T+60, T+30), unlock sớm lock 1 → `lockedUntil` user-service = T+30, `sign()` vẫn bị chặn", "reputation §7, user §2.2"],
    ["P1 · 19", "`sign()` fail-closed khi user-service chết", "Stop user-service → sign bị reject, không fallback anonymous", "user §3/§7"],
    ["P1 · 20", "OTP fail không gửi muộn", "Provider trả lỗi → contract-service nhận 5xx; xác nhận không có mail nào được gửi sau đó với `requestId` cũ trừ khi caller tự retry", "notification §2.2/§5"],
    ["P1 · 21", "Public DTO không lộ PII", "`GET /users/{id}` → không email/phone/address; public-summary yêu cầu JWT", "user §4.1, gateway §3.2"],
    ["P1 · 21b", "OTP challenge binding", "Verify với JWT user khác / otpId khác contract / terms đã đổi sau phát OTP → 403/409/invalidate; không lấy được \"OTP mới nhất\" của người khác", "signature §6-§7 (18/07)"],
    ["P1 · 21c", "Lock ordering sống qua restart", "Apply unlock revision N, restart service, deliver locked revision N-1 (eventId mới) → bị bỏ qua nhờ `last_lock_revision` persist", "user §2.2 (18/07)"],
    ["P1 · 21d", "`legalHold` chặn xoá tuyệt đối", "Set legalHold + retention đã hết → lifecycle job gọi `DeleteFile` → REJECT, file còn nguyên", "file-service §7, governance §8 (18/07 r2)"],
    ["P1 · 21e", "Xoá file 2 bước tự lành", "Mock MinIO delete fail sau tombstone → job re-run → cả DB lẫn blob về trạng thái nhất quán, không rác", "file-service §7 (18/07 r2)"],
    ["P1 · 21f", "Listing/offer fail-closed khi user-service chết", "Stop user-service → CreateListing/CreateOffer trả 503, user đang khoá không lách được", "user §3 (18/07 r2)"],
    ["P2 · 22", "Cà phê/cao su bắt buộc plot; gạo/điều không", "Commodity-gate test 2 chiều: tạo listing thiếu plot → fail/pass đúng theo commodity", "product (EUDR gate)"],
    ["P2 · 23", "Analytics chết không ảnh hưởng giao dịch", "Stop analytics-service → settle milestone vẫn hoàn tất; analytics **catch-up từ queue backlog** khi sống lại (outage ngắn — KHÔNG phải cold rebuild, sửa 18/07/2026)", "governance §3 (read model)"],
    ["P2 · 24", "Notification dedup theo recipient + type", "1 event 2 recipients + retry → mỗi (recipient, type) đúng 1 mail `SENT`", "notification §3.1/§5"],
    ["P2 · 25", "Restore đụng tiền phải qua verify mới mở", "Restore `bank_db` test env → 2 job verify chạy pass trước khi mở traffic (demo DR)", "governance §8c"],
    ["P2 · 26", "Event cũ không ghi đè quyết định mới", "Deliver `reputation.locked/unlocked` sai thứ tự (`lockRevision` nhỏ hơn — sửa 18/07) → bị bỏ qua", "user §2.2"],
    ["P2 · 26b", "Plot overlap chéo seller bị bắt", "Seller B đăng ký polygon trùng/lọt trong polygon Seller A → `plotReuseRisk = HIGH` + OPERATOR review, không auto-reject", "product §2.3b (18/07)"],
    ["P2 · 26c", "Yield anomaly là signal, không phải gate", "`declaredQuantity` vượt Σ(plotArea×maxYield) → `yieldRisk` + yêu cầu bổ sung/review; listing KHÔNG bị auto-reject", "product §2.3b (18/07 r2)"],
    ["P2 · 26d", "Deployment policy enforce được", "CI check: image pin theo digest, secret không nằm trong image, protected branch bắt buộc approval — fail build nếu vi phạm (policy test, không phải runtime test)", "governance §2 threat model (18/07 r2)"]
  ],
  { size: 13, colAlign: [AlignmentType.CENTER, null, null, null] }
));

// ============================================================
// 8. OPEN ITEMS
// ============================================================
push(H1("Tổng hợp trạng thái đóng & giới hạn đã biết"));
push(bullet([runs("KI-3 email IDOR — đã đóng ở design Phase 2. ", { bold: true }), runs("API tách /users/me (contact chính chủ), /users/{id} (PublicUserResponse không email/phone/address) và /internal/v1/users/{id} (DTO nội bộ có email/KYC/lock, Gateway không route). Không còn dùng một UserInfoResponse cho cả external và Feign.", {})]));
push(bullet([runs("Payload event mang commodity — đã đóng hoàn toàn (08/07/2026). ", { bold: true }), runs("contract.signed mang {contractId, commodity, buyerId, sellerId, totalAmount, buyerDepositAmount, sellerDepositAmount, signedContentHash, signedAt} — commodity là enum cứng COFFEE/RICE/RUBBER/CASHEW, luôn non-null (analytics-service chỉ nhận và lưu, không tự map). Nguồn: contract-service đọc Category.commodity của category gắn với sản phẩm — không có case NULL vì category chỉ dùng được khi APPROVED và approve() bắt buộc gán commodity. Cơ chế Category/commodity (2 tầng, vì sao bỏ bảng mapping) chốt ở product-service §2.6 (owner) — không mô tả lại ở đây. Điểm treo mapping trước đây đã đóng hoàn toàn.", {})]));
push(bullet([runs("Checklist KYC theo loại hình doanh nghiệp — giới hạn đã biết. ", { bold: true }), runs("(buyer TNHH/cổ phần/hộ kinh doanh; giấy tờ INSPECTOR) — nguyên tắc đã chốt, danh mục cụ thể cần xác nhận nghiệp vụ với đối tác thật, ngoài phạm vi xác nhận được của đồ án; không block thiết kế.", {})]));

if (IS_MAIN) push(endMark());

module.exports = { body };

if (IS_MAIN) {
  const doc = buildDoc(body, {
    title: "AgriContract — SDS — Phần 5: Ngoại vi & Tổng hợp (User · Notification · Analytics)",
    headerText: "AgriContract · SDS — Phần 5",
    footerText: "SDS v1.0 · Phần 5 · Tháng 7/2026",
  });
  Packer.toBuffer(doc).then(buf => { writeDocx("/tmp/AgriContract_08_SDS_Part5_v1.docx", buf); });
}
