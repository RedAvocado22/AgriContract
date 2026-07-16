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
push(P([runs("ContractTerms — các field then chốt: ", { bold: true }), runs("milestoneSchedule (List<MilestoneTerm>), toleranceRate (ngưỡng lệch cân Delta 2, mặc định chia 50/50 phần vượt ngưỡng — xem §2.4), shortfallPenaltyThreshold (mặc định 5%), buyerPenaltyRate / sellerPenaltyRate, forceMajeureReportWindowDays (mặc định 3 ngày), buyerDepositRate (mặc định 5% totalAmount). sellerDepositRate (mới, 06/07/2026) — optional, mặc định 0, đàm phán per-contract giữa buyer/seller lúc NEGOTIATING, thay quyết định \"bỏ hẳn\" trước đây.", {})]));
push(P([runs("MilestoneTerm — 2 field mới (08/07/2026): ", { bold: true }), runs("expectedDeliveryDate (Date, snapshot bất biến lúc sign() giống mọi field khác trong MilestoneTerm) và graceDays (Integer, số ngày ân hạn sau expectedDeliveryDate). Trước bản này, MilestoneTerm không có bất kỳ mốc ngày nào — hệ quả: không neo được timeout nào ở giai đoạn giao hàng, và hệ thống không định nghĩa được \"seller giao trễ\" (Delta 1 chỉ đo thiếu lượng, không đo trễ hạn — trong khi trễ hạn là vi phạm phổ biến nhất của forward contract nông sản). Cửa sổ hợp lệ là [expectedDeliveryDate, expectedDeliveryDate + graceDays], không phải mốc cứng. graceDays để trong ContractTerms/MilestoneTerm (per-contract), không phải application.yml, vì độ nhạy thời gian khác nhau theo mặt hàng (cà phê khô để lâu, giao trễ vài ngày không sao; rau quả tươi trễ là hỏng) — cùng lý do forceMajeureReportWindowDays đã để per-contract.", {})]));
push(risk("Guardrail cho field negotiate tự do (mới, 08/07/2026).", "toleranceRate, shortfallPenaltyThreshold, buyerPenaltyRate/sellerPenaltyRate negotiate tự do per-contract nhưng trước bản này không validate range ở đâu. Buyer (bên mạnh hơn — luận điểm gốc của cả dự án là chống bất đối xứng quyền lực) hoàn toàn có thể ép shortfallPenaltyThreshold=0% (seller thiếu 1 gram cũng dính penalty) hoặc toleranceRate=0% (seller gánh 100% hao mòn ngay từ gram đầu) — thiết kế sinh ra để chống bất đối xứng quyền lực lại để hở đúng cơ chế đó. Chốt: validate range lúc sign() — shortfallPenaltyThreshold ∈ [3%,15%], toleranceRate ∈ [0%,10%], buyerPenaltyRate/sellerPenaltyRate ∈ [0%,30%]."));

push(H2("2.2 State machine cấp hợp đồng"));
push(table(
  [2400, 2600, 2200, 2438],
  ["Từ trạng thái", "Trigger", "Sang trạng thái", "Ghi chú"],
  [
    ["OFFERED", "Bên mua gửi/đàm phán điều khoản", "NEGOTIATING", "Mọi thay đổi ghi audit trail"],
    ["NEGOTIATING", "Đủ 2 chữ ký (VerifyOtpAndSign lần 2)", "SIGNED", "ContractTerms đóng băng; sinh signedContentHash"],
    ["SIGNED", "Nhận escrow.deposit_locked (buyerDepositRate + sellerDepositRate nếu có, cả 2 khoản đã khoá xong ở bank-service)", "ACTIVE", "N milestone chuyển IN_PROGRESS"],
    ["ACTIVE", "completeAllMilestones() — mọi milestone SETTLED", "SETTLED", "Qua Local Outbox (§2.6); guard cho phép từ ACTIVE"],
    ["SIGNED / ACTIVE", "cancel() (buyer hoặc seller)", "CANCELLED", "Pro-rata phần chưa SETTLED (§2.4)"],
  ],
  { size: 18 }
));
push(P([runs("Điểm sửa quan trọng: ", { bold: true }), runs("guard của completeAllMilestones() phải cho phép chuyển SETTLED từ ACTIVE (không còn trạng thái DELIVERED của mô hình một-điểm-giao-hàng cũ). Các đường dead-path liên quan (confirmDelivery, sự kiện contract.delivered và consumer của nó) được loại bỏ khi hiện thực hoá.", {})]));
push(P([runs("SIGNED ≠ mốc tiền, ACTIVE mới là mốc tiền (làm rõ 08/07/2026): ", { bold: true }), runs("SIGNED là mốc chữ ký — transitionTo(SIGNED) khi đủ 2 chữ ký hợp lệ, độc lập hoàn toàn với tiền. contract.signed publish ngay tại đây là đúng — \"hợp đồng đã được ký kết\" là sự thật kể cả nếu bước lock cọc ngay sau đó thất bại. ACTIVE = đã ký VÀ cọc đã khoá thành công — đúng lúc escrow.deposit_locked (§3.2 UC-E1, escrow-service) về tới contract-service. Nếu lock cọc fail (bank-service trả bank.lock_failed) → Contract kẹt ở SIGNED, chưa ACTIVE; nhánh xử lý fail (retry lock, hoặc rollback về trạng thái trước ký) cần định nghĩa khi implement, hiện chưa có. Hệ quả cho analytics-service: contract.signed đo \"đã ký\", contract.settled/milestone events đo \"đã vận hành thật\" — hai câu hỏi khác nhau, không được lẫn khi tính conversion rate.", {})]));

push(H2("2.3 State machine cấp milestone"));
push(table(
  [2600, 2900, 2400, 1738],
  ["Từ trạng thái", "Trigger", "Sang trạng thái", "Kết quả"],
  [
    ["CREATED", "Contract chuyển ACTIVE", "IN_PROGRESS", "Chờ seller gom hàng"],
    ["IN_PROGRESS", "Quá expectedDeliveryDate + graceDays mà chưa SELLER_WEIGHED (mới, 08/07/2026)", "Xử như Delta 1 penalty nhánh 2, hoặc cancel pro-rata milestone", "Buyer trigger được nhánh seller-quá-hạn; buyer KHÔNG bị seize cọc (buyer không phải bên phá kèo). Seller còn cửa claim bất khả kháng trong forceMajeureReportWindowDays"],
    ["IN_PROGRESS", "Seller cân + upload ảnh", "SELLER_WEIGHED", "Ghi sellerDeclaredWeight"],
    ["SELLER_WEIGHED", "Hết buyerReceiveWindowDays mà buyer chưa cân nhận (mới, 08/07/2026)", "— (không tự chuyển state)", "Notify buyer; im lặng tiếp → Admin/Level 1 quyết theo bằng chứng hiện có, KHÔNG auto-settle theo số seller tự khai"],
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
push(risk("2 lỗ hổng timeout đã vá (08/07/2026).", "SELLER_WEIGHED không có timeout trước bản này — nếu hàng đã cân/gửi mà buyer không bao giờ cân lại/ghi nhận, milestone kẹt SELLER_WEIGHED vĩnh viễn, batchAmount khoá vĩnh viễn. Buyer chỉ cần không bấm gì ở bước nhận hàng là né sạch mọi timeout đã dựng — và vì batchAmount lock sớm (§UC-E2), lối thoát duy nhất trước đây là cancel(), khiến buyer bị seize cọc dù mình mới là bên gây kẹt (bất công ngược đúng chỗ luận điểm gốc \"bảo vệ bên yếu\" muốn tránh). Vá bằng buyerReceiveWindowDays (mặc định 2 ngày làm việc, application.yml — invariant kỹ thuật). Song song, IN_PROGRESS trước bản này cũng không có timeout nào cho seller giao trễ, vì MilestoneTerm chưa có mốc ngày — vá bằng expectedDeliveryDate/graceDays (§2.1) mới thêm."));
push(P([runs("Quyền claim bất khả kháng ", { bold: true }), runs("không gắn cứng một bước — có thể chen ngang bất kỳ lúc nào trước khi milestone SETTLED, miễn còn trong forceMajeureReportWindowDays kể từ lúc seller biết sự kiện (không neo theo ngày giao).", {})]));

push(P([runs("Provisional settlement khi CONTESTED escalate Level 2 (mới, 06/07/2026). ", { bold: true }), runs("Khi DisputeRoutingService route milestone CONTESTED sang LEVEL_2, platform commission tổ chức Level 2 (InitiateLevel2Inspection) và chờ report thật trong tối đa level2BufferWindowDays. Hết window mà report chưa CONFIRMED: platform commission thêm 1 giám định Level 1.5 làm phán quyết tạm thời, settle ngay (1 − level2SafetyBufferRate) của batchAmount cho seller theo số 1.5, giữ khoá phần còn lại trong escrow (không ghi debt — seller không có tài sản đối ứng để đòi). Mechanics đầy đủ 3 bước (Provisional/Reconcile/Terminal, chi tiết milestone-escrow-phase2-design.md §3.2): Bước 1 provisional release X15×(1−rate) cho seller; Bước 2 khi report Level 2 thật về, bù/refund phần chênh; Bước 3 hết terminal cutoff mà report không về thì phán quyết Level 1.5 thành chung thẩm. Hết thêm level2BufferTerminalDays (30 ngày — chốt, tính từ lúc level2BufferWindowDays hết) mà vẫn chưa có report CONFIRMED: phán quyết Level 1.5 tự động thành chung thẩm, release nốt buffer cho seller, đóng milestone.", {})]));
push(bullet([runs("Đã chốt (13/07/2026): ", { bold: true }), runs("cả 3 số cấu hình chốt theo benchmark ngành — level2BufferWindowDays 10 ngày làm việc (neo benchmark lab test chất lượng chuẩn SCA: 5 ngày làm việc/mẫu + buffer lịch hẹn/vận chuyển mẫu); level2SafetyBufferRate 15% (mức trên dải cân nhắc, nghiêng bảo vệ buyer — bên gánh rủi ro nếu Level 2 lật); level2BufferTerminalDays 30 ngày (điểm dừng cứng chống kẹt buffer vô thời hạn). Cấu hình application.yml, tinh chỉnh khi có dữ liệu vận hành thật. Chi tiết đầy đủ ở milestone-escrow-phase2-design.md §3.2.", {})]));

push(H2("2.4 Quy tắc nghiệp vụ — Delta 1 và Delta 2"));
push(table(
  [1500, 3400, 2400, 2338],
  ["", "Delta 1 (thiếu so cam kết)", "Delta 2 (hao mòn vận chuyển)", "—"],
  [
    ["So sánh", "committedQuantity vs sellerDeclaredWeight", "sellerDeclaredWeight vs buyerReceivedWeight", ""],
    ["Bản chất", "Seller kiểm soát được (trước khi xe chạy)", "Ngoài kiểm soát 2 bên (trong vận chuyển)", ""],
    ["Ai chịu", "Seller (trừ khi bất khả kháng)", "Trong ngưỡng: buyer tự chịu. Vượt ngưỡng: chỉ phần vượt chia toleranceRate (mặc định 50/50) — sửa 08/07/2026", ""],
    ["Xử lý", "3 nhánh (dưới)", "Luôn pro-rata tự động", ""],
  ],
  { size: 17 }
));
push(P([runs("Công thức Delta 2 (chốt 08/07/2026): ", { bold: true }), runs("gọi within = ngưỡng khối lượng theo toleranceRate. Nếu delta2 ≤ within (chưa vượt ngưỡng) → actualAmount = buyerReceivedWeight × agreedPrice như cơ chế cũ, không đổi gì — trong ngưỡng, buyer chấp nhận hao mòn là bình thường (đã ngầm chấp nhận lúc ký). Nếu delta2 > within → chỉ phần VƯỢT ngưỡng (không phải toàn bộ delta2) mới chia theo toleranceRate, chia trên khối lượng (không phải tiền, vì agreedPrice cố định suốt hợp đồng nên 2 cách cho kết quả y hệt, nhưng khớp cách Delta 1 đã tính) — seller không gánh 100% phần bất thường, buyer chia sẻ trách nhiệm vì rủi ro vận chuyển ngoài kiểm soát cả hai. Sửa vì bản trước để 1 đường duy nhất — milestone.settled release theo buyerReceivedWeight, seller gánh 100% hao mòn — mâu thuẫn với chính nguyên tắc \"chia theo toleranceRate\" đã công bố. contract-service (có đủ ContractTerms) là nơi tính actualAmount cuối cùng, truyền số đã tính xuống escrow-service qua payload milestone.settled — escrow-service không tự tính lại tolerance split (xem UC-E3, §3.2).", {})]));
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
  ["Luồng chính", "1) InitiateSign: kiểm authorizationExpiresAt/session freshness, sinh OTP hash rồi gọi sync POST /internal/v1/notifications/otp-email (requestId=otpId); chỉ báo đã gửi khi provider accepted, lỗi không background-send muộn. 2) VerifyOtpAndSign: khớp OTP → tạo Signature → nếu đủ 2 dòng thì Contract → SIGNED → publish contract.signed, đẩy hash vào audit và publish notification.contract_anchor_requested"],
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
  ["Trigger", "Nhận contract.signed — payload mang sẵn buyerDepositAmount/sellerDepositAmount (đã tính = rate × totalAmount ở contract-service). escrow-service KHÔNG tự nhân rate×totalAmount (sửa 08/07/2026): là pure event consumer, không Feign ngược lấy ContractTerms, nên không tính nổi số tiền cần khoá nếu chỉ nhận rate thô"],
  ["Luồng", "Gửi bank.lock_requested(entryType=LOCK_BUYER_DEPOSIT, milestoneId=NULL, amount=buyerDepositAmount, sourceEventId) → đợi bank.lock_completed → set buyerDepositState=DEPOSIT_LOCKED. Nếu sellerDepositAmount>0: gửi thêm 1 bank.lock_requested riêng (entryType=LOCK_SELLER_DEPOSIT, userId=sellerId, sourceEventId khác) → set sellerDepositState=DEPOSIT_LOCKED (sellerDepositAmount=0 → bỏ qua, không bắn thêm LedgerEntry). Cả 2 khoản cần khoá xong (chỉ buyer nếu sellerDepositAmount=0) → publish escrow.deposit_locked(contractId, buyerDepositState, sellerDepositState) — contract-service consume để chuyển ACTIVE; escrow-service tự dùng làm tín hiệu nối tiếp lock batchAmount milestone đầu tiên (UC-E2), không cần round-trip qua contract-service"],
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
    ["bank.lock_requested", "bank.lock_completed / _failed", "LOCK_BUYER_DEPOSIT / LOCK_SELLER_DEPOSIT / LOCK_MILESTONE"],
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
  "  entry_type      VARCHAR(24) NOT NULL,   -- LOCK_BUYER_DEPOSIT|LOCK_SELLER_DEPOSIT|LOCK_MILESTONE",
  "                                          -- |RELEASE_TO_SELLER|SEIZE_PENALTY|REFUND_TO_BUYER",
  "  amount          DECIMAL(15,2) NOT NULL,",
  "  created_at      TIMESTAMP NOT NULL DEFAULT now()",
  ");",
  "CREATE INDEX idx_ledger_contract ON ledger_entry(contract_id, milestone_id);",
  "CREATE INDEX idx_ledger_user ON ledger_entry(user_id);",
  "-- Không có bảng 'account': số dư = SUM(amount) lọc theo tiêu chí, tính lúc cần.",
]));
push(P([runs("Ghi chú tách 2 loại cọc (08/07/2026): ", { bold: true }), runs("buyerDepositRate dùng LOCK_BUYER_DEPOSIT, sellerDepositRate (optional) dùng LOCK_SELLER_DEPOSIT — 2 entryType riêng để ledger phân biệt được nguồn cọc lúc release/seize/refund, thay vì gộp chung LOCK_DEPOSIT.", {})]));

push(H2("4.4 bank.large_transaction_flagged — báo cáo giao dịch giá trị lớn, không hold"));
push(P("LedgerEntry nào ≥ 500.000.000 VNĐ (ngưỡng giao dịch chuyển tiền điện tử trong nước, Điều 9 Thông tư 27/2025/TT-NHNN) → publish bank.large_transaction_flagged ngay khi ghi entry, cùng transaction, KHÔNG hold. Nghĩa vụ báo cáo giao dịch giá trị lớn thuộc tổ chức tài chính giữ tiền, không phải platform số hoá hợp đồng — nên bank-service chỉ ghi nhận + tạo audit trail, không đóng băng giao dịch. Consumer: reputation-service (composite fraud score), audit-service."));
push(legal("Thông tư 27/2025/TT-NHNN, Điều 9 (hiệu lực 01/11/2025)", "Ngưỡng 500 triệu áp cho giao dịch chuyển tiền điện tử trong nước (đúng loại operation lock/release/refund của ledger), khác ngưỡng 400 triệu cho giao dịch giá trị lớn bằng tiền mặt (Quyết định 11/2023/QĐ-TTg). Ngân hàng thật cho giao dịch chạy rồi báo cáo Cục Phòng, chống rửa tiền trước 16 giờ ngày làm việc tiếp theo — không đóng băng theo ngưỡng."));

push(H2("4.5 Emergency Lock — Zero-Trust Kill Switch cho External Verifier"));
push(P("Thu hẹp lỗ hổng trusted-operator (audit-service §4, phần dưới): cho tổ chức vận hành platform (External Verifier / Software Buyer — VICOFA/VRA/doanh nghiệp bất kỳ, không cột cứng 1 tên) một đường độc lập để đóng băng toàn hệ thống khi phát hiện tampering, không phụ thuộc job chạy trong platform."));
push(bullet([runs("Gate 1 chốt chặn: ", { bold: true }), runs("vì escrow-service là actor duy nhất gọi bank-service, chỉ cần check system_lock (status=ACTIVE) trước mọi bank.*_requested — có lock thì reject, publish bank.*_failed; không service nào khác cần biết về freeze.", {})]));
push(bullet([runs("Chữ ký bất đối xứng: ", { bold: true }), runs("POST /security/emergency-lock và /emergency-unlock KHÔNG dùng API key/JWT (Admin chui DB/env lấy được secret đối xứng) — dùng RSA/ECDSA: External Verifier giữ private key, platform chỉ giữ public key. Payload gồm timestamp + nonce nằm trong chuỗi ký, chống replay.", {})]));
push(bullet([runs("Freeze tách khỏi notify: ", { bold: true }), runs("freeze tự động toàn cục; buyer/seller KHÔNG được tự động báo cho tới khi Admin khoanh vùng xong (giữ audit-service §5.3). Unlock mirror lock — cần chữ ký External Verifier, không ngoại lệ Admin.", {})]));
push(bullet([runs("Root-of-trust: ", { bold: true }), runs("public key baked deploy-time (ngoài tầm Admin runtime); mỗi lần rotation là 1 event EXTERNAL_VERIFIER_KEY_REGISTERED anchor vào hash chain + email fingerprint cho External Verifier; rotation phải ký bởi key cũ.", {})]));
push(P([runs("wallet_snapshot — scaling path, không build ở scope hiện tại: ", { bold: true }), runs("ở quy mô đồ án (vài nghìn entry) SUM có index chạy dưới 50ms; snapshot chốt sổ định kỳ (cutoff theo entry_id, single-writer, derived) chỉ ghi nhận là hướng scale khi lên hàng triệu giao dịch, không thực thi.", {})]));
push(codeblock([
  "CREATE TABLE system_lock (",
  "  lock_id         UUID PRIMARY KEY,",
  "  status          VARCHAR(20) NOT NULL,   -- ACTIVE | RELEASED",
  "  verifier_org_id UUID NOT NULL,          -- External Verifier (generic, không hardcode tên)",
  "  reason          TEXT, triggered_at TIMESTAMP NOT NULL, released_at TIMESTAMP NULL",
  ");",
  "CREATE TABLE used_nonce (nonce VARCHAR(64) PRIMARY KEY, seen_at TIMESTAMP NOT NULL);",
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
    ["3", "contract → notification (internal sync)", "POST /internal/v1/notifications/otp-email; OTP + signedContentHash; requestId=otpId", "Provider accepted → mới trả 'đã gửi'; lỗi không retry nền"],
    ["4", "contract-service", "VerifyOtpAndSign: khớp OTP → INSERT signature", "Tạo Signature của bên đó"],
    ["5", "contract-service", "Đủ 2 chữ ký → Contract SIGNED → publish contract.signed + đẩy hash", "audit-service nối chain; email anchor"],
    ["6", "escrow → bank", "bank.lock_requested (LOCK_BUYER_DEPOSIT, + LOCK_SELLER_DEPOSIT nếu sellerDepositAmount>0) → bank.lock_completed", "Ledger LOCK_BUYER_DEPOSIT/LOCK_SELLER_DEPOSIT; DEPOSIT_LOCKED"],
    ["7", "escrow-service", "Cả 2 khoản cọc cần khoá đã confirm xong → publish escrow.deposit_locked", "contract-service consume để chuyển ACTIVE"],
    ["8", "contract-service", "Nhận escrow.deposit_locked → Contract ACTIVE", "N milestone → IN_PROGRESS"],
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
    ["shortfallPenaltyThreshold / toleranceRate", "ContractTerms", "Mặc định 5% / 50-50 (phần vượt ngưỡng); negotiate được. Guardrail (mới, 08/07/2026): validate range lúc sign() — shortfallPenaltyThreshold ∈ [3%,15%], toleranceRate ∈ [0%,10%]"],
    ["buyerPenaltyRate / sellerPenaltyRate", "ContractTerms", "Đàm phán theo hợp đồng. Guardrail (mới, 08/07/2026): validate range [0%,30%] lúc sign()"],
    ["buyerDepositRate", "ContractTerms", "Mặc định 5% totalAmount"],
    ["sellerDepositRate (mới, 06/07/2026)", "ContractTerms", "Optional, mặc định 0 — đàm phán per-contract, thay quyết định \"bỏ hẳn\" trước đây"],
    ["forceMajeureReportWindowDays", "ContractTerms", "Mặc định 3 ngày; khác theo mặt hàng"],
    ["expectedDeliveryDate (mới, 08/07/2026)", "ContractTerms/MilestoneTerm", "Snapshot bất biến lúc sign() — mốc neo timeout giao hàng, per-milestone"],
    ["graceDays (mới, 08/07/2026)", "ContractTerms/MilestoneTerm", "Số ngày ân hạn sau expectedDeliveryDate — per-contract vì độ nhạy khác theo mặt hàng, cùng lý do forceMajeureReportWindowDays"],
    ["buyerReceiveWindowDays (mới, 08/07/2026)", "application.yml", "Mặc định 2 ngày làm việc — vá lỗ hổng SELLER_WEIGHED không timeout (§2.3). Invariant kỹ thuật, không khác theo hợp đồng"],
    ["zeroProgressMultiplier (mới, 08/07/2026)", "application.yml", "1.5x — nhân vào công thức lockDurationDays (reputation-service, Phần 3 §3.1) khi cancel lúc 0 milestone nào từng SETTLED; 1.0x mọi trường hợp khác"],
    ["level2BufferWindowDays (chốt 13/07/2026)", "application.yml", "10 ngày làm việc — chốt, neo theo benchmark lab test chất lượng chuẩn SCA (5 ngày làm việc/mẫu) + buffer lịch hẹn/vận chuyển mẫu; tinh chỉnh khi có dữ liệu vận hành thật"],
    ["level2SafetyBufferRate (chốt 13/07/2026)", "application.yml", "15% batchAmount — chốt, mức trên dải cân nhắc, nghiêng bảo vệ buyer (bên gánh rủi ro nếu Level 2 lật); tinh chỉnh khi có variance vận hành thật"],
    ["level2BufferTerminalDays (chốt 13/07/2026)", "application.yml", "30 ngày — chốt, điểm dừng cứng: hết hạn thì phán quyết Level 1.5 tự động thành chung thẩm"],
  ],
  { size: 17, colAlign: [null, AlignmentType.CENTER, null] }
));

// ============================================================
// 7. OPEN ITEMS
// ============================================================
push(H1("7. Trạng thái đóng & giới hạn đã biết"));
push(P("Đánh dấu rõ trạng thái từng mục — phần lớn đã đóng; phần cần xác nhận nghiệp vụ với đối tác thật ghi là giới hạn đã biết:"));
push(bullet([runs("Event contract.cancelled ", { bold: true }), runs("là phát hiện mới khi rà lại luồng tiền cấp hợp đồng (buyerDepositRate không thuộc milestone nào nên cần event cấp Contract riêng). Logic khớp với UC-C5, nhưng cần xác nhận nghiệp vụ trước khi coi là chốt cứng.", {})]));
push(bullet([runs("Checklist KYC theo loại hình doanh nghiệp buyer ", { bold: true }), runs("(TNHH, cổ phần, hộ kinh doanh…) — Signature design mới chốt nguyên tắc đối xứng buyer/seller (BLDS 142), chưa chốt danh mục giấy tờ cụ thể.", {})]));
push(bullet([runs("Payload event mang commodity — đã đóng hoàn toàn (08/07/2026). ", { bold: true }), runs("contract.signed mang {contractId, commodity, buyerId, sellerId, totalAmount, buyerDepositAmount, sellerDepositAmount, signedAt} — commodity là enum cứng COFFEE/RICE/RUBBER/CASHEW, luôn non-null: contract-service đọc Category.commodity của category gắn với sản phẩm, không có bảng mapping riêng và không có case NULL (category chỉ dùng được khi APPROVED, approve() bắt buộc gán commodity). Cơ chế 2 tầng Category/commodity chốt ở product-service (owner); analytics-service dùng event này populate dim_contract mà không cần Feign ngược (chi tiết Phần 5 §4).", {})]));
push(callout("Ghi chú.", "WebAuthn/chữ ký số CA là hướng nâng cấp sole-control mạnh hơn nhưng KHÔNG đổi tier pháp lý (cần chứng thư từ CA được cấp phép); ghi nhận out-of-scope, không thiết kế trong phần này.", "note"));
push(bullet([runs("Provisional settlement Level 2 (06/07/2026, chốt 13/07/2026). ", { bold: true }), runs("Cả 3 số cấu hình đã chốt: level2BufferWindowDays 10 ngày làm việc (benchmark lab test SCA), level2SafetyBufferRate 15%, level2BufferTerminalDays 30 ngày — application.yml, tinh chỉnh khi có dữ liệu vận hành thật. Cơ chế terminal cutoff đã đóng câu hỏi \"buffer xử lý sao nếu report không bao giờ về\".", {})]));
push(bullet([runs("sellerDepositRate optional — đã đóng (chốt 13/07/2026). ", { bold: true }), runs("Thay quyết định bỏ hẳn trước đây — cọc seller thành optional, đàm phán per-contract (mặc định 0, giữ nguyên hiện trạng cho ai không cần). Case cancel-ở-0-milestone khi sellerDepositRate=0: chốt ÁP DỤNG lockDurationDays nặng hơn qua zeroProgressMultiplier 1.5x (ký xong bỏ ngay là tín hiệu hành vi xấu nhất) — công thức và baseline do reputation-service sở hữu (Phần 3 §4), chi tiết milestone-escrow-phase2-design.md §6.1.", {})]));

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
