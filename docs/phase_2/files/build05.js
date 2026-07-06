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
// use case block
function uc(id, name, fields) {
  push(new Paragraph({ spacing: { before: 140, after: 40 }, children: [new TextRun({ text: id + " — " + name, font: T.FONT, size: 21, bold: true, color: T.HEAD })] }));
  const rows = fields.map(([k, v]) => [k, v]);
  push(table([1800, 7838], ["", ""], rows, { size: 18, boldCol: [true, false] }));
}

if (require.main === module) {
push(...cover("AGRICONTRACT", "Software Design Specification (SDS)",
  "Phần 2 — Cụm lõi: contract-service · escrow-service · bank-service",
  ["Tài liệu nội bộ — Đồ án Tốt nghiệp", "Phiên bản 1.0 · Phần 2/5 · Tháng 7/2026"]));
push(...toc());
}

// ============================================================
// 1. INTRO
// ============================================================
push(H1("1. Phạm vi phần 2"));
push(P("Phần 2 đặc tả thiết kế chi tiết cụm lõi (Core domain) — ba dịch vụ gánh vòng đời hợp đồng và toàn bộ luồng tiền: contract-service (vòng đời hợp đồng, milestone, chữ ký), escrow-service (logic ký quỹ theo đợt), bank-service (giữ tiền, ledger). Đây là cụm defend nặng nhất và là trục mà các cụm khác gắn vào."));
push(P("Tài liệu này tuân theo các chuẩn dùng chung ở Phần 1 (API, sự kiện, dữ liệu, lỗi, bảo mật) và không lặp lại chúng. Ranh giới trách nhiệm giữa ba dịch vụ:"));
push(table(
  [2300, 7338],
  ["Dịch vụ", "Sở hữu (không lẫn sang dịch vụ khác)"],
  [
    ["contract-service", "Trạng thái giao hàng (delivery state): vòng đời hợp đồng, state machine milestone, chữ ký, tính penalty và publish event. KHÔNG giữ tiền, KHÔNG gọi bank"],
    ["escrow-service", "Logic ký quỹ: quyết định khoản nào cần lock/release/seize/refund. Là actor DUY NHẤT gọi bank-service. Chỉ giữ state, KHÔNG giữ con số tiền"],
    ["bank-service", "Single source of truth cho tiền: ledger append-only. Thực thi lệnh từ escrow, xác nhận lại. KHÔNG chứa business logic hợp đồng"],
  ],
  { size: 18 }
));
push(callout("Nguyên tắc xuyên suốt cụm này:", "chống dual-write. Số tiền thật chỉ tồn tại một nơi (ledger của bank-service); contract-service và escrow-service không lưu lại con số tiền để đồng bộ tay. Mọi thay đổi trạng thái tiền đi qua cặp lệnh–xác nhận (request → confirmation), không fire-and-forget.", "note"));

// ============================================================
// 2. CONTRACT-SERVICE
// ============================================================
push(H1("2. contract-service"));
push(H2("2.1 Mô hình miền (domain model)"));
push(P("Hai aggregate root độc lập trong cùng dịch vụ/DB: Contract và Milestone. Milestone KHÔNG phải entity con của Contract — milestone thứ 3 quyết toán không cần atomic cùng lúc với milestone khác, nên tách aggregate để mỗi milestone có transaction riêng (Effective Aggregate Design — Vernon). Signature và ContractTerms là value object trong Contract."));
push(table(
  [2400, 2200, 5038],
  ["Thành phần", "Loại", "Vai trò"],
  [
    ["Contract", "Aggregate root", "State machine cấp hợp đồng; giữ snapshot bất biến (buyerId, sellerId, productName, quantity, agreedPrice, totalAmount, signedContentHash)"],
    ["ContractTerms", "Value object (trong Contract)", "Điều khoản đàm phán: các rate + milestoneSchedule; snapshot bất biến lúc sign()"],
    ["MilestoneTerm", "Nested VO (trong ContractTerms)", "Một dòng lịch giao hàng đã thoả thuận: milestoneIndex, committedQuantity, batchAmount"],
    ["Milestone", "Aggregate root riêng", "Instance runtime của một đợt giao hàng; state machine riêng, transaction riêng"],
    ["Signature", "Value object (trong Contract)", "Bản ghi ký của một bên; UNIQUE(contractId, signerRole)"],
  ],
  { size: 18 }
));
push(P([runs("ContractTerms — các field then chốt: ", { bold: true }), runs("milestoneSchedule (List<MilestoneTerm>), toleranceRate (ngưỡng lệch cân Delta 2, mặc định chia 50/50), shortfallPenaltyThreshold (mặc định 5%), buyerPenaltyRate / sellerPenaltyRate, forceMajeureReportWindowDays (mặc định 3 ngày), buyerDepositRate (mặc định 5% totalAmount). sellerDepositRate (mới, 06/07/2026) — optional, mặc định 0, đàm phán per-contract giữa buyer/seller lúc NEGOTIATING, thay quyết định \"bỏ hẳn\" trước đây.", {})]));

push(H2("2.2 State machine cấp hợp đồng"));
push(table(
  [2400, 2600, 2200, 2438],
  ["Từ trạng thái", "Trigger", "Sang trạng thái", "Ghi chú"],
  [
    ["OFFERED", "Bên mua gửi/đàm phán điều khoản", "NEGOTIATING", "Mọi thay đổi ghi audit trail"],
    ["NEGOTIATING", "Đủ 2 chữ ký (VerifyOtpAndSign lần 2)", "SIGNED", "ContractTerms đóng băng; sinh signedContentHash"],
    ["SIGNED", "bank xác nhận đã khoá buyerDepositRate", "ACTIVE", "N milestone chuyển IN_PROGRESS"],
    ["ACTIVE", "completeAllMilestones() — mọi milestone SETTLED", "SETTLED", "Qua Local Outbox (§2.6); guard cho phép từ ACTIVE"],
    ["SIGNED / ACTIVE", "cancel() (buyer hoặc seller)", "CANCELLED", "Pro-rata phần chưa SETTLED (§2.4)"],
  ],
  { size: 18 }
));
push(P([runs("Điểm sửa quan trọng: ", { bold: true }), runs("guard của completeAllMilestones() phải cho phép chuyển SETTLED từ ACTIVE (không còn trạng thái DELIVERED của mô hình một-điểm-giao-hàng cũ). Các đường dead-path liên quan (confirmDelivery, sự kiện contract.delivered và consumer của nó) được loại bỏ khi hiện thực hoá.", {})]));

push(H2("2.3 State machine cấp milestone"));
push(table(
  [2600, 2900, 2400, 1738],
  ["Từ trạng thái", "Trigger", "Sang trạng thái", "Kết quả"],
  [
    ["CREATED", "Contract chuyển ACTIVE", "IN_PROGRESS", "Chờ seller gom hàng"],
    ["IN_PROGRESS", "Seller cân + upload ảnh", "SELLER_WEIGHED", "Ghi sellerDeclaredWeight"],
    ["SELLER_WEIGHED", "Vận chuyển, buyer cân lại + ảnh", "BUYER_RECEIVED", "Ghi buyerReceivedWeight"],
    ["BUYER_RECEIVED", "CONFIRM_CLEAN hoặc timeout buyerConfirmWindowDays", "SETTLED", "Pro-rata Delta 2, release"],
    ["BUYER_RECEIVED", "FLAG_ISSUE", "AWAITING_SELLER_RESPONSE", "Seller có sellerResponseWindowDays để phản hồi"],
    ["AWAITING_SELLER_RESPONSE", "Seller timeout (im lặng)", "SETTLED", "Theo số buyer báo"],
    ["AWAITING_SELLER_RESPONSE", "CONTESTED", "SETTLED (sau phán quyết)", "Qua DisputeRoutingService 3 cấp"],
    ["IN_PROGRESS / SELLER_WEIGHED", "Seller claim bất khả kháng (trong window)", "FORCE_MAJEURE_PENDING_REVIEW", "Kèm bằng chứng qua file-service"],
    ["FORCE_MAJEURE_PENDING_REVIEW", "Admin APPROVE", "BUYER_RECEIVED (miễn penalty)", "Buyer có thể escalate Level 1.5"],
    ["FORCE_MAJEURE_PENDING_REVIEW", "Admin REJECT", "Xử lý như Delta 1 penalty", "Seller có thể escalate Level 1.5"],
  ],
  { size: 17 }
));
push(P([runs("Quyền claim bất khả kháng ", { bold: true }), runs("không gắn cứng một bước — có thể chen ngang bất kỳ lúc nào trước khi milestone SETTLED, miễn còn trong forceMajeureReportWindowDays kể từ lúc seller biết sự kiện (không neo theo ngày giao).", {})]));

push(P([runs("Provisional settlement khi CONTESTED escalate Level 2 (mới, 06/07/2026). ", { bold: true }), runs("Khi DisputeRoutingService route milestone CONTESTED sang LEVEL_2, platform commission tổ chức Level 2 (InitiateLevel2Inspection) và chờ report thật trong tối đa level2BufferWindowDays. Hết window mà report chưa CONFIRMED: platform commission thêm 1 giám định Level 1.5 làm phán quyết tạm thời, settle ngay (1 − level2SafetyBufferRate) của batchAmount cho seller theo số 1.5, giữ khoá phần còn lại trong escrow (không ghi debt — seller không có tài sản đối ứng để đòi). Khi report Level 2 thật về sau đó, chênh lệch (nếu có) trừ/bù thẳng từ buffer đang khoá. Hết thêm level2BufferTerminalDays (placeholder 30 ngày, tính từ lúc level2BufferWindowDays hết) mà vẫn chưa có report CONFIRMED: phán quyết Level 1.5 tự động thành chung thẩm, release nốt buffer cho seller, đóng milestone.", {})]));
push(bullet([runs("Chưa đóng — còn treo (06/07/2026): ", { bold: true }), runs("cả 3 số (level2BufferWindowDays 7-14 ngày, level2SafetyBufferRate 10-15%, level2BufferTerminalDays 30 ngày) là placeholder, chưa validate với đơn vị Level 2 thật. Chi tiết đầy đủ ở milestone-escrow-phase2-design.md §3.2.", {})]));

push(H2("2.4 Quy tắc nghiệp vụ — Delta 1 và Delta 2"));
push(table(
  [1500, 3400, 2400, 2338],
  ["", "Delta 1 (thiếu so cam kết)", "Delta 2 (hao mòn vận chuyển)", "—"],
  [
    ["So sánh", "committedQuantity vs sellerDeclaredWeight", "sellerDeclaredWeight vs buyerReceivedWeight", ""],
    ["Bản chất", "Seller kiểm soát được (trước khi xe chạy)", "Ngoài kiểm soát 2 bên (trong vận chuyển)", ""],
    ["Ai chịu", "Seller (trừ khi bất khả kháng)", "Chia theo toleranceRate (mặc định 50/50)", ""],
    ["Xử lý", "3 nhánh (dưới)", "Luôn pro-rata tự động", ""],
  ],
  { size: 17 }
));
push(table(
  [1100, 4700, 3838],
  ["Nhánh", "Điều kiện (Delta 1)", "Kết quả"],
  [
    ["1", "Thiếu trong shortfallPenaltyThreshold", "Pro-rata bình thường, không penalty"],
    ["2", "Thiếu vượt threshold, KHÔNG chứng minh được bất khả kháng", "Áp sellerPenaltyRate — đúng bản chất phá vỡ hợp đồng"],
    ["3", "Thiếu (bất kỳ mức), chứng minh được bất khả kháng (Admin/Level 1.5 approve)", "Pro-rata theo số thực giao, không penalty"],
  ],
  { size: 18, colAlign: [AlignmentType.CENTER, null, null] }
));
push(legal("BLDS 2015, Điều 156 & 351", "Bất khả kháng phải hội đủ 3 điều kiện: khách quan, không lường trước, không khắc phục được dù nỗ lực. Hệ quả: các bên tự chịu thiệt hại của mình, hợp đồng giảm xuống đúng số lượng thực giao — không ai bồi thường ai. Mất mùa/sâu bệnh thông thường không đạt ngưỡng."));

push(H2("2.5 Use case chính"));
uc("UC-C1", "Ký hợp đồng (2 bước: InitiateSign + VerifyOtpAndSign)", [
  ["Actor", "BUYER và SELLER (mỗi bên thực hiện độc lập)"],
  ["Tiền điều kiện", "Contract ở NEGOTIATING; tài khoản đã qua duyệt KYC; authorizationExpiresAt còn hiệu lực"],
  ["Luồng chính", "1) InitiateSign: kiểm authorizationExpiresAt > now (BLDS 142), kiểm session freshness (now − auth_time ≤ signatureAuthMaxAgeSeconds sau step-up), sinh OTP hash + gửi email kèm signedContentHash. 2) VerifyOtpAndSign: khớp OTP (chưa hết hạn, chưa quá otpMaxAttempts) → tạo Signature → nếu đủ 2 dòng thì Contract → SIGNED → publish contract.signed + đẩy hash vào audit"],
  ["Ngoại lệ", "Thẩm quyền hết hạn / session cũ / OTP sai hoặc hết hạn → REJECT (không tạo Signature, không đổi state)"],
  ["Hậu điều kiện", "Đủ 2 chữ ký → SIGNED; nếu mới 1 → chờ bên còn lại"],
]);
uc("UC-C2", "Seller cân hàng đợt", [
  ["Actor", "SELLER"],
  ["Tiền điều kiện", "Milestone ở IN_PROGRESS"],
  ["Luồng chính", "Nhập sellerDeclaredWeight + upload ảnh (file-service) → milestone SELLER_WEIGHED → publish milestone.seller_weighed"],
  ["Hậu điều kiện", "Ghi sellerDeclaredWeight, sellerEvidenceFileId"],
]);
uc("UC-C3", "Buyer xác nhận / gắn cờ đợt", [
  ["Actor", "BUYER"],
  ["Tiền điều kiện", "Milestone ở BUYER_RECEIVED"],
  ["Luồng chính", "CONFIRM_CLEAN → tính pro-rata Delta 2 → milestone.settled (kèm lockedAmount, actualAmount). HOẶC FLAG_ISSUE → AWAITING_SELLER_RESPONSE → publish milestone.flagged"],
  ["Ngoại lệ (timeout)", "Buyer im lặng quá buyerConfirmWindowDays → tự xử lý như CONFIRM_CLEAN (bảo vệ seller đã giao hàng)"],
]);
uc("UC-C4", "Claim và xét bất khả kháng", [
  ["Actor", "SELLER (claim), ADMIN (xét)"],
  ["Tiền điều kiện", "Milestone chưa SETTLED; trong forceMajeureReportWindowDays kể từ lúc biết sự kiện"],
  ["Luồng chính", "Seller nộp bằng chứng (file-service) → FORCE_MAJEURE_PENDING_REVIEW → publish milestone.force_majeure_claimed. Admin APPROVE (miễn penalty, về BUYER_RECEIVED) hoặc REJECT (xử như Delta 1 penalty) → publish milestone.force_majeure_resolved"],
  ["Quyền phản đối", "Buyer contest APPROVE / seller contest REJECT → cả hai escalate Level 1.5 (không lên Level 2)"],
]);
uc("UC-C5", "Huỷ hợp đồng (pro-rata)", [
  ["Actor", "BUYER hoặc SELLER"],
  ["Tiền điều kiện", "Contract ở SIGNED/ACTIVE; áp cho milestone chưa SETTLED"],
  ["Luồng chính", "cancel() → Contract CANCELLED → publish contract.cancelled(initiatedBy). Mỗi milestone còn lại publish milestone.cancelled_with_penalty riêng. Seller huỷ: nếu có sellerDepositRate đã khoá → seize ngay, offset vào penalty debt (sellerPenaltyRate × giá trị còn lại − deposit đã seize); refund buyerDepositRate về buyer, khoá tài khoản seller. Buyer huỷ: seize buyerDepositRate (→ seller) + seize batchAmount đang khoá theo buyerPenaltyRate, refund sellerDepositRate về seller nếu có (seller không phải bên phá kèo), khoá tài khoản buyer"],
  ["Hậu điều kiện", "Milestone đã SETTLED giữ nguyên; penalty debt bất biến trong audit"],
]);

push(H2("2.6 Cơ chế đồng bộ Milestone → Contract (Local Outbox)"));
push(P("Khi milestone cuối quyết toán, Contract phải chuyển SETTLED. Không dùng Spring ApplicationEvent (nếu app crash sau khi milestone commit nhưng trước khi listener chạy, event bay mất — không retry — Contract kẹt vĩnh viễn ở ACTIVE, không exception nào báo). Thay bằng Local Outbox (local, không qua RabbitMQ, không network hop):"));
push(numbered("Milestone.settle() insert một row milestone_sync_outbox trong CÙNG transaction với chính nó — atomic, không thể commit milestone mà thiếu outbox row."));
push(numbered("@Scheduled poller đọc row processed=false, chạy countByContractIdAndStatusNot(SETTLED)==0 cho contractId của row."));
push(numbered("Nếu true → gọi Contract.completeAllMilestones() trong transaction riêng của Contract."));
push(numbered("Đánh dấu processed=true dù kết quả check là gì — milestone thật sự cuối cùng sẽ có row riêng bắt được điều kiện ==0."));
push(numbered("Fail giữa chừng → retry_count += 1, row vẫn processed=false, lần chạy sau xử lý lại (at-least-once)."));

push(H2("2.7 API endpoints"));
push(table(
  [4700, 1400, 3538],
  ["Endpoint", "Vai trò", "Mô tả"],
  [
    ["POST /api/v1/contracts", "BUYER", "Tạo offer từ listing"],
    ["PATCH /api/v1/contracts/{id}/terms", "BUYER/SELLER", "Đàm phán điều khoản (NEGOTIATING)"],
    ["POST /api/v1/contracts/{id}/sign/initiate", "BUYER/SELLER", "InitiateSign — sinh & gửi OTP"],
    ["POST /api/v1/contracts/{id}/sign/verify", "BUYER/SELLER", "VerifyOtpAndSign — tạo Signature"],
    ["POST /api/v1/contracts/{id}/milestones/{mid}/weigh", "SELLER", "Cân hàng đợt (UC-C2)"],
    ["POST /api/v1/contracts/{id}/milestones/{mid}/confirm", "BUYER", "CONFIRM_CLEAN (UC-C3)"],
    ["POST /api/v1/contracts/{id}/milestones/{mid}/flag", "BUYER", "FLAG_ISSUE"],
    ["POST /api/v1/contracts/{id}/milestones/{mid}/respond", "SELLER", "Phản hồi flag / CONTESTED"],
    ["POST /api/v1/contracts/{id}/milestones/{mid}/force-majeure", "SELLER", "Claim bất khả kháng (UC-C4)"],
    ["POST /api/v1/contracts/{id}/milestones/{mid}/force-majeure/resolve", "ADMIN", "APPROVE/REJECT claim"],
    ["POST /api/v1/contracts/{id}/cancel", "BUYER/SELLER", "Huỷ pro-rata (UC-C5)"],
    ["GET /api/v1/contracts/{id}", "các bên", "Chi tiết hợp đồng + milestone"],
  ],
  { size: 17, colAlign: [null, AlignmentType.CENTER, null] }
));

push(H2("2.8 Lược đồ dữ liệu (contract_db)"));
push(codeblock([
  "CREATE TABLE contract (",
  "  contract_id         UUID PRIMARY KEY,",
  "  listing_id          UUID NOT NULL,          -- ref product-service (plain UUID)",
  "  buyer_id            UUID NOT NULL,          -- ref user-service",
  "  seller_id           UUID NOT NULL,          -- ref user-service",
  "  status              VARCHAR(15) NOT NULL,   -- OFFERED..SETTLED/CANCELLED",
  "  product_name        VARCHAR(255) NOT NULL,  -- snapshot bất biến",
  "  quantity            DECIMAL(15,2) NOT NULL, -- snapshot",
  "  agreed_price        DECIMAL(15,2) NOT NULL, -- snapshot (VND/kg)",
  "  total_amount        DECIMAL(15,2) NOT NULL,",
  "  signed_content_hash VARCHAR(64) NULL,       -- set lúc SIGNED",
  "  version             BIGINT NOT NULL DEFAULT 0,  -- optimistic lock",
  "  created_at          TIMESTAMP NOT NULL,",
  "  updated_at          TIMESTAMP NOT NULL",
  ");",
  "",
  "CREATE TABLE contract_terms (",
  "  contract_id                  UUID PRIMARY KEY REFERENCES contract(contract_id),",
  "  tolerance_rate               DECIMAL(5,4) NOT NULL,",
  "  shortfall_penalty_threshold  DECIMAL(5,4) NOT NULL DEFAULT 0.05,",
  "  buyer_penalty_rate           DECIMAL(5,4) NOT NULL,",
  "  seller_penalty_rate          DECIMAL(5,4) NOT NULL,",
  "  buyer_deposit_rate           DECIMAL(5,4) NOT NULL DEFAULT 0.05,",
  "  force_majeure_report_window_days INT NOT NULL DEFAULT 3",
  ");",
  "",
  "CREATE TABLE milestone_term (           -- lịch giao hàng bất biến (snapshot)",
  "  term_id             UUID PRIMARY KEY,",
  "  contract_id         UUID NOT NULL REFERENCES contract(contract_id),",
  "  milestone_index     INT NOT NULL,",
  "  committed_quantity  DECIMAL(15,2) NOT NULL,",
  "  batch_amount        DECIMAL(15,2) NOT NULL, -- committed_quantity × agreed_price",
  "  UNIQUE (contract_id, milestone_index)",
  ");",
  "",
  "CREATE TABLE milestone (                -- instance runtime",
  "  milestone_id           UUID PRIMARY KEY,",
  "  contract_id            UUID NOT NULL,",
  "  milestone_index        INT NOT NULL,",
  "  status                 VARCHAR(28) NOT NULL,  -- CREATED..SETTLED",
  "  seller_declared_weight DECIMAL(15,2) NULL,",
  "  seller_evidence_file_id UUID NULL,            -- ref file-service",
  "  buyer_received_weight  DECIMAL(15,2) NULL,",
  "  buyer_evidence_file_id UUID NULL,",
  "  force_majeure_claim_id UUID NULL,",
  "  version                BIGINT NOT NULL DEFAULT 0,",
  "  created_at             TIMESTAMP NOT NULL,",
  "  updated_at             TIMESTAMP NOT NULL",
  ");",
  "",
  "CREATE TABLE force_majeure_claim (",
  "  claim_id          UUID PRIMARY KEY,",
  "  milestone_id      UUID NOT NULL,",
  "  contract_id       UUID NOT NULL,",
  "  status            VARCHAR(20) NOT NULL,  -- PENDING_REVIEW/APPROVED/REJECTED/ESCALATED",
  "  evidence_file_id  UUID NOT NULL,",
  "  claimed_at        TIMESTAMP NOT NULL,",
  "  reviewed_by       UUID NULL,",
  "  reviewed_at       TIMESTAMP NULL",
  ");",
]));
push(P("Chữ ký + OTP + outbox (cùng contract_db):"));
push(codeblock([
  "CREATE TABLE signature (",
  "  signature_id        UUID PRIMARY KEY,",
  "  contract_id         UUID NOT NULL REFERENCES contract(contract_id),",
  "  signer_role         VARCHAR(10) NOT NULL,  -- BUYER | SELLER",
  "  signer_user_id      UUID NOT NULL,         -- = JWT sub",
  "  auth_time           TIMESTAMP NOT NULL,    -- = JWT auth_time (sau step-up)",
  "  signed_at           TIMESTAMP NOT NULL,",
  "  signed_content_hash VARCHAR(64) NOT NULL,",
  "  ip_address          VARCHAR(45),",
  "  UNIQUE (contract_id, signer_role)",
  ");",
  "",
  "CREATE TABLE signature_otp (",
  "  otp_id        UUID PRIMARY KEY,",
  "  contract_id   UUID NOT NULL, signer_role VARCHAR(10) NOT NULL,",
  "  user_id       UUID NOT NULL,",
  "  otp_hash      VARCHAR(64) NOT NULL,   -- hash, không lưu plaintext",
  "  sent_at       TIMESTAMP NOT NULL, expires_at TIMESTAMP NOT NULL,",
  "  verified_at   TIMESTAMP NULL, attempt_count INT NOT NULL DEFAULT 0",
  ");",
  "",
  "CREATE TABLE milestone_sync_outbox (    -- Local Outbox (§2.6)",
  "  outbox_id UUID PRIMARY KEY, milestone_id UUID NOT NULL, contract_id UUID NOT NULL,",
  "  processed BOOLEAN NOT NULL DEFAULT false, retry_count INT NOT NULL DEFAULT 0,",
  "  last_attempt_at TIMESTAMP NULL, created_at TIMESTAMP NOT NULL, processed_at TIMESTAMP NULL",
  ");",
  "",
  "CREATE TABLE contract_domain_events (   -- Transactional Outbox → RabbitMQ",
  "  event_id UUID PRIMARY KEY, aggregate_id UUID NOT NULL, routing_key VARCHAR(60) NOT NULL,",
  "  payload JSON NOT NULL, status VARCHAR(10) NOT NULL DEFAULT 'PENDING',",
  "  created_at TIMESTAMP NOT NULL, published_at TIMESTAMP NULL",
  ");",
]));

// ============================================================
// 3. ESCROW-SERVICE
// ============================================================
push(H1("3. escrow-service"));
push(H2("3.1 Trách nhiệm và mô hình miền"));
push(P("escrow-service là actor duy nhất gọi bank-service. EscrowAccount (một per contract) và EscrowMilestone (một per milestone) CHỈ giữ state, không lưu con số tiền — số tiền thật là single source of truth ở ledger bank-service. Giữ thêm con số ở đây là dual-write."));
push(table(
  [2400, 2200, 5038],
  ["Aggregate", "Loại", "Trạng thái quản lý"],
  [
    ["EscrowAccount", "Aggregate root (per contract)", "Trạng thái cọc cấp hợp đồng — 2 field độc lập: buyerDepositState (DEPOSIT_LOCKED/RELEASED/SEIZED) và sellerDepositState (mới, 06/07/2026, cùng 3 giá trị — chỉ có ý nghĩa khi sellerDepositRate > 0)"],
    ["EscrowMilestone", "Entity (per milestone)", "Trạng thái batch: LOCKED / RELEASED / REFUNDED_PARTIAL / PENALIZED"],
  ],
  { size: 18 }
));

push(H2("3.2 Use case (event-driven)"));
uc("UC-E1", "Khoá cọc cấp hợp đồng khi SIGNED", [
  ["Trigger", "Nhận contract.signed"],
  ["Luồng", "Gửi bank.lock_requested(entryType=LOCK_DEPOSIT, milestoneId=NULL, amount=buyerDepositRate×totalAmount, sourceEventId) → đợi bank.lock_completed → set buyerDepositState=DEPOSIT_LOCKED. Nếu sellerDepositRate>0 (mới, 06/07/2026): gửi thêm 1 bank.lock_requested riêng (userId=sellerId, sourceEventId khác) → set sellerDepositState=DEPOSIT_LOCKED. Cả hai xong → báo contract-service để chuyển ACTIVE"],
]);
uc("UC-E2", "Khoá batchAmount đợt (lock sớm)", [
  ["Trigger", "Contract ACTIVE (milestone đầu) hoặc milestone trước SETTLED", ],
  ["Luồng", "bank.lock_requested(LOCK_MILESTONE, milestoneId, batchAmount, sourceEventId) → bank.lock_completed → EscrowMilestone LOCKED"],
]);
uc("UC-E3", "Quyết toán đợt (pro-rata Delta 1/2)", [
  ["Trigger", "Nhận milestone.settled (mang lockedAmount, actualAmount)"],
  ["Luồng", "Tính diff = lockedAmount − actualAmount. Nếu diff>0: bank.release_requested(RELEASE_TO_SELLER, actualAmount) + bank.refund_requested(REFUND_TO_BUYER, diff), 2 sourceEventId riêng. Nếu diff==0: chỉ release. → EscrowMilestone RELEASED"],
]);
uc("UC-E4", "Xử lý cọc lúc kết thúc/huỷ", [
  ["Trigger", "contract.settled hoặc contract.cancelled(initiatedBy)"],
  ["Luồng", "contract.settled → refund buyerDepositRate về buyer (REFUND_TO_BUYER, milestoneId=NULL); nếu có sellerDepositRate đã khoá → release về seller (RELEASE_TO_SELLER). contract.cancelled SELLER → refund buyerDepositRate về buyer; nếu có sellerDepositRate đã khoá → seize (SEIZE_PENALTY), offset vào penalty debt (mới, 06/07/2026). contract.cancelled BUYER → seize buyerDepositRate chuyển seller (SEIZE_PENALTY); nếu có sellerDepositRate đã khoá → release về seller (seller không phải bên phá kèo, mới 06/07/2026). milestone.cancelled_with_penalty → seize batchAmount đang khoá nếu có"],
]);
push(P("Toàn bộ đợi confirmation event mới đổi state (không fire-and-forget). bank.*_failed → giữ nguyên state, xử lý nhánh fail."));

push(H2("3.3 API & lược đồ dữ liệu (escrow_db)"));
push(P("Giao tiếp chính qua event; REST chỉ để đọc trạng thái: GET /api/v1/escrow/accounts/{contractId} và .../milestones."));
push(codeblock([
  "CREATE TABLE escrow_account (",
  "  escrow_account_id UUID PRIMARY KEY,",
  "  contract_id       UUID NOT NULL UNIQUE,",
  "  deposit_status    VARCHAR(20) NOT NULL,  -- buyerDepositRate: DEPOSIT_LOCKED/RELEASED/SEIZED",
  "  seller_deposit_status VARCHAR(20) NULL,  -- sellerDepositRate (mới, 06/07/2026), NULL nếu rate=0 — cùng 3 giá trị",
  "  version           BIGINT NOT NULL DEFAULT 0,",
  "  created_at        TIMESTAMP NOT NULL",
  ");",
  "CREATE TABLE escrow_milestone (",
  "  escrow_milestone_id UUID PRIMARY KEY,",
  "  escrow_account_id   UUID NOT NULL REFERENCES escrow_account(escrow_account_id),",
  "  milestone_id        UUID NOT NULL,        -- ref contract-service (plain UUID)",
  "  milestone_index     INT NOT NULL,",
  "  status              VARCHAR(20) NOT NULL, -- LOCKED/RELEASED/REFUNDED_PARTIAL/PENALIZED",
  "  version             BIGINT NOT NULL DEFAULT 0",
  ");",
]));

// ============================================================
// 4. BANK-SERVICE
// ============================================================
push(H1("4. bank-service"));
push(H2("4.1 Trách nhiệm và mô hình ledger"));
push(P("Giữ tiền hợp pháp (mock) — mô hình FBO/Omnibus: một chỗ giữ tiền chung, toàn bộ chi tiết ai-sở-hữu-bao-nhiêu nằm trong LedgerEntry append-only. Số dư KHÔNG lưu sẵn — luôn derive bằng SUM lọc theo contract_id/milestone_id/user_id/entry_type lúc cần. escrow-service là actor duy nhất gọi."));
push(legal("Nghị định 52/2024/NĐ-CP, Điều 8 Khoản 7", "Ngân hàng (trong triển khai thật) giữ tiền, nền tảng chỉ ra lệnh — không thuộc phạm vi phải xin giấy phép trung gian thanh toán. Trong đồ án, bank-service là mock với interface event sạch để thay bằng tích hợp thật mà không đổi business logic escrow."));

push(H2("4.2 Idempotency và cặp lệnh–xác nhận"));
push(P("RabbitMQ at-least-once → bank có thể nhận cùng một bank.lock_requested nhiều lần. Idempotency key = sourceEventId (ID của outbox message escrow gửi sang), UNIQUE trên ledger_entry — không dùng compound business key vì một (contractId, milestoneId) trải qua nhiều entryType theo thời gian, không unique per message."));
push(P([runs("Xử lý trùng: ", { bold: true }), runs("nhận sourceEventId đã tồn tại → KHÔNG insert lại, nhưng VẪN re-publish confirmation tương ứng (không silent-drop). Vì lý do gửi lại có thể là confirmation lần trước bị mất trên đường về; silent-drop khiến escrow treo chờ mãi.", {})]));
push(table(
  [3400, 3100, 3138],
  ["Lệnh (escrow → bank)", "Xác nhận (bank → escrow)", "Ledger entryType"],
  [
    ["bank.lock_requested", "bank.lock_completed / _failed", "LOCK_DEPOSIT / LOCK_MILESTONE"],
    ["bank.release_requested", "bank.release_completed / _failed", "RELEASE_TO_SELLER"],
    ["bank.seize_requested", "bank.seize_completed / _failed", "SEIZE_PENALTY"],
    ["bank.refund_requested", "bank.refund_completed / _failed", "REFUND_TO_BUYER"],
  ],
  { size: 18 }
));

push(H2("4.3 Lược đồ dữ liệu (bank_db)"));
push(codeblock([
  "CREATE TABLE ledger_entry (",
  "  entry_id        UUID PRIMARY KEY,",
  "  source_event_id UUID NOT NULL UNIQUE,   -- idempotency key (§4.2)",
  "  contract_id     UUID NOT NULL,",
  "  milestone_id    UUID NULL,              -- NULL = buyerDepositRate (cấp contract)",
  "  user_id         UUID NOT NULL,",
  "  entry_type      VARCHAR(20) NOT NULL,   -- LOCK_DEPOSIT|LOCK_MILESTONE|RELEASE_TO_SELLER",
  "                                          -- |SEIZE_PENALTY|REFUND_TO_BUYER",
  "  amount          DECIMAL(15,2) NOT NULL,",
  "  created_at      TIMESTAMP NOT NULL DEFAULT now()",
  ");",
  "CREATE INDEX idx_ledger_contract ON ledger_entry(contract_id, milestone_id);",
  "CREATE INDEX idx_ledger_user ON ledger_entry(user_id);",
  "-- Không có bảng 'account': số dư = SUM(amount) lọc theo tiêu chí, tính lúc cần.",
]));

// ============================================================
// 5. SEQUENCE FLOWS
// ============================================================
push(H1("5. Luồng trình tự xuyên dịch vụ"));
push(H2("5.1 Ký hợp đồng và kích hoạt"));
push(table(
  [700, 2400, 3500, 3038],
  ["#", "Actor / Dịch vụ", "Hành động / Sự kiện", "Kết quả"],
  [
    ["1", "Bên ký (UI)", "Bấm \"Ký\" → step-up re-auth (prompt=login)", "auth_time cập nhật mới"],
    ["2", "contract-service", "InitiateSign: kiểm authorizationExpiresAt + session freshness; sinh OTP hash", "INSERT signature_otp"],
    ["3", "notification-service", "Gửi email OTP kèm signedContentHash", "Bên ký nhận mã"],
    ["4", "contract-service", "VerifyOtpAndSign: khớp OTP → INSERT signature", "Tạo Signature của bên đó"],
    ["5", "contract-service", "Đủ 2 chữ ký → Contract SIGNED → publish contract.signed + đẩy hash", "audit-service nối chain; email anchor"],
    ["6", "escrow → bank", "bank.lock_requested (LOCK_DEPOSIT) → bank.lock_completed", "Ledger LOCK_DEPOSIT; DEPOSIT_LOCKED"],
    ["7", "contract-service", "Nhận xác nhận cọc đã khoá → Contract ACTIVE", "N milestone → IN_PROGRESS"],
  ],
  { size: 17, colAlign: [AlignmentType.CENTER, null, null, null] }
));

push(H2("5.2 Quyết toán một milestone"));
push(table(
  [700, 2400, 3500, 3038],
  ["#", "Actor / Dịch vụ", "Hành động / Sự kiện", "Kết quả"],
  [
    ["1", "escrow → bank", "Lock batchAmount sớm (LOCK_MILESTONE)", "EscrowMilestone LOCKED"],
    ["2", "SELLER", "Cân + ảnh → milestone.seller_weighed", "SELLER_WEIGHED; file lưu evidence"],
    ["3", "BUYER", "Cân lại + ảnh → CONFIRM_CLEAN → milestone.settled(lockedAmount, actualAmount)", "Tính pro-rata Delta 2"],
    ["4", "escrow-service", "diff = locked − actual; nếu >0 bắn RELEASE + REFUND, nếu =0 chỉ RELEASE", "2 sourceEventId riêng"],
    ["5", "bank-service", "Ghi RELEASE_TO_SELLER (+ REFUND_TO_BUYER) → confirmation", "Ledger cập nhật; EscrowMilestone RELEASED"],
    ["6", "contract-service", "Milestone SETTLED → Local Outbox → completeAllMilestones khi mọi milestone xong", "Contract SETTLED → contract.settled"],
    ["7", "escrow → bank", "contract.settled → REFUND_TO_BUYER (buyerDepositRate)", "Hoàn cọc; reputation nhận input tích cực"],
  ],
  { size: 17, colAlign: [AlignmentType.CENTER, null, null, null] }
));

push(H2("5.3 Huỷ hợp đồng"));
push(table(
  [700, 2200, 3400, 3338],
  ["#", "Actor / Dịch vụ", "Hành động / Sự kiện", "Kết quả"],
  [
    ["1", "BUYER/SELLER", "cancel() → Contract CANCELLED → contract.cancelled(initiatedBy)", "Chỉ tác động milestone chưa SETTLED"],
    ["2a", "escrow → bank (SELLER huỷ)", "REFUND_TO_BUYER (cọc); contract-service ghi penalty debt vào audit", "Cọc về buyer; khoá tài khoản seller"],
    ["2b", "escrow → bank (BUYER huỷ)", "SEIZE_PENALTY (cọc → seller) + seize batchAmount đang khoá", "Khoá tài khoản buyer"],
    ["3", "contract-service", "Mỗi milestone còn lại → milestone.cancelled_with_penalty", "reputation tính lockDurationDays"],
  ],
  { size: 17, colAlign: [AlignmentType.CENTER, null, null, null] }
));

// ============================================================
// 6. CONFIG
// ============================================================
push(H1("6. Cấu hình: application.yml vs ContractTerms"));
push(P("Nguyên tắc: invariant kỹ thuật (không có lý do hợp đồng khác nhau cần khác nhau) đặt ở application.yml; tham số đàm phán theo hợp đồng đặt ở ContractTerms."));
push(table(
  [4200, 2100, 3338],
  ["Tham số", "Nơi lưu", "Giá trị / lý do"],
  [
    ["signatureAuthMaxAgeSeconds", "application.yml", "300s — session freshness lúc ký"],
    ["otpLength / otpExpirySeconds / otpMaxAttempts", "application.yml", "6 / 300s / 5 — invariant OTP"],
    ["otpMaxResendPerHour / otpResendCooldownSeconds", "application.yml", "5 / 60s — chống spam"],
    ["buyerConfirmWindowDays / sellerResponseWindowDays", "application.yml", "2 ngày làm việc — đối xứng"],
    ["Escalation cap bất khả kháng = Level 1.5", "application.yml", "Invariant — sai chuyên môn nếu lên Level 2"],
    ["Ngưỡng giá trị/loại hàng kích hoạt cấp dispute", "application.yml", "Per-deployment config"],
    ["milestoneSchedule", "ContractTerms", "Đàm phán số đợt và tỷ lệ từng đợt"],
    ["shortfallPenaltyThreshold / toleranceRate", "ContractTerms", "Mặc định 5% / 50-50; negotiate được"],
    ["buyerPenaltyRate / sellerPenaltyRate", "ContractTerms", "Đàm phán theo hợp đồng"],
    ["buyerDepositRate", "ContractTerms", "Mặc định 5% totalAmount"],
    ["sellerDepositRate (mới, 06/07/2026)", "ContractTerms", "Optional, mặc định 0 — đàm phán per-contract, thay quyết định \"bỏ hẳn\" trước đây"],
    ["forceMajeureReportWindowDays", "ContractTerms", "Mặc định 3 ngày; khác theo mặt hàng"],
    ["level2BufferWindowDays (mới, 06/07/2026)", "application.yml", "Placeholder 7-14 ngày làm việc — chưa validate với đơn vị Level 2 thật"],
    ["level2SafetyBufferRate (mới, 06/07/2026)", "application.yml", "Placeholder 10-15% batchAmount — chưa có dữ liệu variance thật"],
    ["level2BufferTerminalDays (mới, 06/07/2026)", "application.yml", "Placeholder 30 ngày — hết hạn thì phán quyết Level 1.5 tự động thành chung thẩm"],
  ],
  { size: 17, colAlign: [null, AlignmentType.CENTER, null] }
));

// ============================================================
// 7. OPEN ITEMS
// ============================================================
push(H1("7. Điểm còn treo cần xác nhận nghiệp vụ"));
push(P("Đánh dấu rõ để không lẫn với phần đã chốt cứng:"));
push(bullet([runs("Event contract.cancelled ", { bold: true }), runs("là phát hiện mới khi rà lại luồng tiền cấp hợp đồng (buyerDepositRate không thuộc milestone nào nên cần event cấp Contract riêng). Logic khớp với UC-C5, nhưng cần xác nhận nghiệp vụ trước khi coi là chốt cứng.", {})]));
push(bullet([runs("Checklist KYC theo loại hình doanh nghiệp buyer ", { bold: true }), runs("(TNHH, cổ phần, hộ kinh doanh…) — Signature design mới chốt nguyên tắc đối xứng buyer/seller (BLDS 142), chưa chốt danh mục giấy tờ cụ thể.", {})]));
push(bullet([runs("Payload event mang commodity — đã giải quyết (06/07/2026). ", { bold: true }), runs("Thêm event contract.signed cấp Contract (publisher contract-service, bắn lúc Contract.transitionTo(SIGNED)), payload {contractId, commodity, buyerId, sellerId, totalAmount, signedAt} — analytics-service dùng để populate dim_contract mà không cần Feign ngược (chi tiết Phần 5 §4). Còn treo cấp thấp hơn: bảng mapping Product.category (enum tiếng Việt) → commodity enum dùng chung (COFFEE/RICE/RUBBER/CASHEW) cần xác nhận nghiệp vụ trước khi contract-service code phần publish.", {})]));
push(callout("Ghi chú.", "WebAuthn/chữ ký số CA là hướng nâng cấp sole-control mạnh hơn nhưng KHÔNG đổi tier pháp lý (cần chứng thư từ CA được cấp phép); ghi nhận out-of-scope, không thiết kế trong phần này.", "note"));
push(bullet([runs("Provisional settlement Level 2 (mới, 06/07/2026, chưa đóng). ", { bold: true }), runs("level2BufferWindowDays (7-14 ngày), level2SafetyBufferRate (10-15%), level2BufferTerminalDays (30 ngày) đều là placeholder, chưa validate với đơn vị Level 2 thật (đã liên hệ NHL, chưa có phản hồi). Cơ chế terminal cutoff đã đóng câu hỏi \"buffer xử lý sao nếu report không bao giờ về\".", {})]));
push(bullet([runs("sellerDepositRate optional (mới, 06/07/2026, chưa đóng hoàn toàn). ", { bold: true }), runs("Thay quyết định \"bỏ hẳn\" trước đây — đàm phán per-contract. Còn treo: lockDurationDays nặng hơn riêng cho case cancel-ở-0-milestone khi sellerDepositRate=0 — Cường chưa xác nhận áp dụng hay không.", {})]));

module.exports = { body };

if (require.main === module) {
push(endMark());

const doc = buildDoc(body, {
  title: "AgriContract — SDS — Phần 2: Cụm lõi (Contract · Escrow · Bank)",
  headerText: "AgriContract · SDS — Phần 2",
  footerText: "SDS v1.0 · Phần 2 · Tháng 7/2026",
});
Packer.toBuffer(doc).then(buf => { fs.writeFileSync("/tmp/AgriContract_05_SDS_Part2_v1.docx", buf); console.log("written", buf.length); });

}
