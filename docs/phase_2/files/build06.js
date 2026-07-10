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
  "Phần 3 — Cụm giám định & tin cậy: inspection-service · reputation-service · audit-service",
  ["Tài liệu nội bộ — Đồ án Tốt nghiệp", "Phiên bản 1.0 · Phần 3/5 · Tháng 7/2026"]));
push(...toc());
}

// ============================================================
// 1. SCOPE
// ============================================================
push(H1("1. Phạm vi phần 3"));
push(P("Phần 3 đặc tả cụm giám định và tin cậy — ba dịch vụ tạo ra và bảo vệ bằng chứng phục vụ giải quyết tranh chấp, cưỡng chế hành vi, và tuân thủ pháp lý: inspection-service (giám định độc lập 3 cấp), reputation-service (khoá tài khoản + uy tín + tham chiếu tín dụng), audit-service (chuỗi hash bất biến, neo Bitcoin, xuất báo cáo EUDR)."));
push(P("Cụm này gắn vào cụm lõi (Phần 2) qua sự kiện: nhận milestone/contract event, và trả lại quyết định (khoá, phán quyết giám định) mà cụm lõi thực thi. Tuân theo chuẩn dùng chung ở Phần 1."));
push(table(
  [2300, 7338],
  ["Dịch vụ", "Sở hữu"],
  [
    ["inspection-service", "Giám định Level 1.5 & Level 2; evidence record bất biến với reportHash; commission tổ chức Level 2. KHÔNG ra phán quyết cuối (Admin thực thi theo report)"],
    ["reputation-service", "Sổ khoá bất biến (nguồn quyết định lockout); điểm uy tín (view sống); tham chiếu tín dụng; giám sát AML. KHÔNG enforce khoá (user-service enforce)"],
    ["audit-service", "Chuỗi hash append-only nhận hash từ các dịch vụ khác; verify định kỳ; neo Bitcoin; xuất DDS cho EUDR. KHÔNG tạo dữ liệu nghiệp vụ gốc"],
  ],
  { size: 18 }
));

// ============================================================
// 2. INSPECTION-SERVICE
// ============================================================
push(H1("2. inspection-service"));
push(P("Nguyên tắc gốc: Level 1.5 và Level 2 KHÔNG phải hai mức độ nghiêm trọng của cùng một thứ — chúng là hai mô hình định danh hoàn toàn khác nhau, không dùng chung. Level 1 (Admin nội bộ) dùng account Admin sẵn có, không cần thiết kế riêng."));
push(table(
  [1600, 4000, 4038],
  ["Cấp", "Mô hình định danh", "Cơ chế xác thực bằng chứng"],
  [
    ["Level 1.5", "Actor thật trên nền tảng (Vinacontrol/Quatest quy mô tỉnh) — có account, login, JWT; dùng chung schema Signature", "Signature + RBAC; reportHash = SHA256(content + timestamp + inspectorId), inspectorId = signerUserId đã xác thực"],
    ["Level 2", "KHÔNG login, KHÔNG account (SGS/Bureau Veritas — uy tín accreditation tự thân đủ)", "Auto-intake qua hòm thư nền tảng + Admin CONFIRMED; nền tảng chỉ bảo vệ tính toàn vẹn file sau khi nhận"],
  ],
  { size: 18 }
));

push(H2("2.1 Level 1.5 — Giám định tích hợp nền tảng"));
push(bullet([runs("Định danh: ", { bold: true }), runs("mở rộng signerRole enum trong Signature thành BUYER | SELLER | INSPECTOR. Ràng buộc UNIQUE(contractId, signerRole) không áp cho INSPECTOR (một hợp đồng có thể phát sinh nhiều report qua nhiều lần dispute) — thay bằng field reportId với UNIQUE(reportId). Không tạo Dispute aggregate mới chỉ để có unique key.", {})]));
push(bullet([runs("KYC-gate: ", { bold: true }), runs("cùng cơ chế gate lúc đăng ký fail-closed như buyer/seller, nhưng nội dung khác — xác minh chứng chỉ/giấy phép hoạt động kiểm định thương mại (không phải thẩm quyền đại diện). authorizationExpiresAt tái dùng nguyên (giấy phép kiểm định cũng có hạn).", {})]));
push(bullet([runs("Session freshness riêng: ", { bold: true }), runs("inspectionAuthMaxAgeSeconds = 1800s (30 phút), KHÔNG dùng chung 300s của buyer/seller — INSPECTOR kiểm tra thực địa xong mới nộp report, khoảng cách step-up→submit dài hơn tự nhiên.", {})]));

push(H2("2.2 Level 2 — Giám định bên ngoài"));
push(P("Tổ chức Level 2 được đàm phán vào ContractTerms (field level2InspectorOrg, free text, snapshot bất biến lúc sign) — không hardcode danh sách. Đàm phán lúc ký (khi hai bên còn thiện chí), không phải lúc dispute xảy ra (đúng lúc khó đồng thuận nhất — tạo deadlock ngay tại bước cần giải quyết deadlock). Bắt buộc điền chỉ khi hợp đồng nằm trong ngưỡng có thể escalate Level 2; hợp đồng cap cứng Level 1.5 thì để NULL."));
push(P([runs("Allowlist 3 nhóm ", { bold: true }), runs("(chống hai bên thông đồng tự chọn tổ chức dễ dãi):", {})]));
push(table(
  [2400, 4200, 3038],
  ["Nhóm (orgVerificationType)", "Cơ chế xác nhận", "Lưu trữ"],
  [
    ["HARDCODED_MAJOR", "Tổ chức quốc tế lớn đã biết (SGS, Bureau Veritas) — verify một lần khi thêm vào bảng", "Bảng level2_inspector_allowlist (Admin quản lý, không cần deploy)"],
    ["BOA_VERIFIED", "Trong nước — verify qua số chứng nhận công nhận BoA-VIAS (boa.gov.vn)", "Cùng bảng, sau verify lần đầu"],
    ["ADMIN_AD_HOC", "Lạ — Admin tra accreditation certificate number theo chuỗi ILAC → national body → registry", "PRIVATE — không ghi vào danh sách dùng chung (né bài toán dọn danh sách khi tổ chức mất accreditation)"],
  ],
  { size: 17 }
));
push(callout("Confident-wrong vs honest-fail.", "Các quyết định best-effort (parse case ID từ email, match commission) KHÔNG tự gán — chỉ gợi ý cho Admin xác nhận. Auto-match sai tạo ra confident-wrong (hệ thống tự tin gắn nhầm hợp đồng), nằm im tới đúng lúc dispute cần dùng report mới lộ — nguy hiểm hơn honest-fail (không match được, để PENDING_REVIEW chờ người xử lý).", "note"));
push(P([runs("Nguồn tra accreditation (08/07/2026): ", { bold: true }), runs("Admin verify tổ chức Level 2 qua ba nguồn tra online xác định — BoA-VIAS (boa.gov.vn, scheme Inspection Bodies theo ISO/IEC 17020), IAF CertSearch (iafcertsearch.org, database validate chứng nhận toàn cầu), và ILAC Signatory Search (ilac.org/signatory-search). Không còn là hộp đen “chưa biết tra ở đâu”; tự động hoá full qua REST API là enhancement (chưa xác nhận BoA có API JSON export danh sách VIAS).", {})]));

push(H2("2.3 Use case chính"));
uc("UC-I1", "Level 1.5 nộp inspection report", [
  ["Actor", "INSPECTOR (Level 1.5, đã KYC + login)"],
  ["Tiền điều kiện", "Milestone CONTESTED, DisputeRoutingService route = LEVEL_1_5; session freshness ≤ 1800s"],
  ["Luồng chính", "Nộp report + file (file-service) → tính reportHash → INSERT inspection_report (tier=LEVEL_1_5) + Signature(signerRole=INSPECTOR, reportId) → publish INSPECTION_REPORT vào audit-service → contract-service verify hash, advance milestone SETTLED theo phán quyết"],
]);
uc("UC-I2", "Commission tổ chức Level 2", [
  ["Actor", "ADMIN"],
  ["Trigger", "DisputeRoutingService route = LEVEL_2"],
  ["Luồng chính", "Validate state cho phép escalate Level 2; đọc level2InspectorOrg (NULL → REJECT); nếu nhóm BOA_VERIFIED/ADMIN_AD_HOC bắt Admin nhập verificationReference; INSERT level2_inspection_commission(status=REQUESTED); INSERT audit_record(LEVEL2_INSPECTION_COMMISSIONED); gửi mail (SendGrid) tới org kèm 3 địa chỉ nhận report"],
  ["Ranh giới", "Chỉ tạo YÊU CẦU, không tạo sự đồng ý — org có nhận job hay không là quan hệ thương mại thật ngoài hệ thống"],
]);
uc("UC-I3", "Xác nhận case ID (join key)", [
  ["Actor", "Hệ thống (parse best-effort) → ADMIN (xác nhận)"],
  ["Luồng chính", "Webhook ParseCommissionAck extract case ID từ subject/body, match domain ↔ commission REQUESTED → gợi ý, KHÔNG tự gán. Admin ConfirmCommissionCaseId → status=CASE_ID_CONFIRMED"],
]);
uc("UC-I4", "Tiếp nhận report Level 2 cuối + review", [
  ["Actor", "Hệ thống (ingest) → ADMIN (review)"],
  ["Luồng chính", "IngestExternalInspectionReportEmail (webhook): tính reportHash NGAY trước khi ai chạm; upload file; lookup case ID → commission; INSERT inspection_report(status=PENDING_REVIEW, ingestion_source=AUTO_EMAIL, spf_dkim_result); KHÔNG publish audit. ReviewPendingExternalReport (Admin, KHÔNG nhận tham số file): APPROVE → CONFIRMED + publish EXTERNAL_INSPECTION_REPORT; REJECT → REJECTED"],
  ["Fallback", "SubmitExternalInspectionReport (Admin tải & upload thủ công, ingestion_source=ADMIN_MANUAL) khi org gửi ngoài intake@ hoặc webhook lỗi"],
]);
push(P([runs("Hash đóng băng trước khi có người chạm: ", { bold: true }), runs("reportHash tính ngay lúc ingest (trước cả Admin), publish vào audit dời sang lúc CONFIRMED — tránh mail rác/gắn nhầm lọt vào audit trail bất biến trước khi có người xác nhận. Endpoint review không nhận tham số file → Admin về mặt kỹ thuật không thể thay file qua đường này.", {})]));

push(H2("2.4 API & lược đồ dữ liệu"));
push(table(
  [4900, 1300, 3438],
  ["Endpoint", "Vai trò", "Mô tả"],
  [
    ["POST /api/v1/inspections/level1-5/reports", "INSPECTOR", "Nộp report Level 1.5 (UC-I1)"],
    ["POST /api/v1/inspections/level2/commissions", "ADMIN", "Commission org Level 2 (UC-I2)"],
    ["POST /api/v1/inspections/level2/commissions/{id}/case-id", "ADMIN", "Xác nhận case ID (UC-I3)"],
    ["POST /api/v1/inspections/level2/reports/{id}/review", "ADMIN", "Review report auto-intake (UC-I4)"],
    ["(webhook) SendGrid Inbound Parse → intake@", "hệ thống", "Nhận ack + report Level 2"],
  ],
  { size: 17, colAlign: [null, AlignmentType.CENTER, null] }
));
push(codeblock([
  "-- signature (contract_db): mở rộng cho nhánh INSPECTOR",
  "ALTER TABLE signature ADD COLUMN report_id UUID NULL;  -- FK LOGIC (không cross-DB)",
  "-- signer_role: BUYER | SELLER | INSPECTOR",
  "-- INSPECTOR: report_id NOT NULL, UNIQUE(report_id)",
  "-- BUYER/SELLER: report_id NULL, giữ UNIQUE(contract_id, signer_role)",
  "",
  "ALTER TABLE contract_terms ADD COLUMN level2_inspector_org VARCHAR(255) NULL;",
  "",
  "-- inspection_db",
  "CREATE TABLE inspection_report (",
  "  report_id            UUID PRIMARY KEY,",
  "  contract_id          UUID NULL,            -- denormalized; NULL nếu chưa match commission",
  "  milestone_id         UUID NULL,",
  "  tier                 VARCHAR(10) NOT NULL, -- LEVEL_1_5 | LEVEL_2",
  "  commission_id        UUID NULL,            -- Level 2 only",
  "  inspector_user_id    UUID NULL,            -- Level 1.5 only (= signer_user_id)",
  "  report_file_id       UUID NOT NULL,        -- ref file-service",
  "  report_hash          VARCHAR(64) NOT NULL,",
  "  status               VARCHAR(15) NOT NULL, -- SUBMITTED|PENDING_REVIEW|CONFIRMED|REJECTED",
  "  ingestion_source     VARCHAR(15) NOT NULL, -- PLATFORM_ACTOR|AUTO_EMAIL|ADMIN_MANUAL",
  "  spf_dkim_result      VARCHAR(20) NULL,",
  "  external_verification_status VARCHAR(20) NULL,",
  "  confirmed_by_admin_id UUID NULL,",
  "  created_at           TIMESTAMP NOT NULL, confirmed_at TIMESTAMP NULL",
  ");",
  "",
  "CREATE TABLE level2_inspection_commission (",
  "  commission_id        UUID PRIMARY KEY,",
  "  contract_id          UUID NOT NULL,        -- FK thường, KHÔNG unique",
  "  org                  VARCHAR(255) NOT NULL,",
  "  intake_case_id       VARCHAR(255) NULL,",
  "  status               VARCHAR(20) NOT NULL, -- REQUESTED | CASE_ID_CONFIRMED",
  "  org_verification_type VARCHAR(15) NOT NULL,-- HARDCODED_MAJOR|BOA_VERIFIED|ADMIN_AD_HOC",
  "  verification_reference VARCHAR(100) NULL,",
  "  verified_by_admin_id UUID NULL, verified_at TIMESTAMP NULL,",
  "  requested_at         TIMESTAMP NOT NULL, case_id_confirmed_at TIMESTAMP NULL,",
  "  UNIQUE (org, intake_case_id)",
  ");",
  "",
  "CREATE TABLE level2_inspector_allowlist (",
  "  org_name VARCHAR(255) PRIMARY KEY, org_verification_type VARCHAR(15) NOT NULL,",
  "  accreditation_ref VARCHAR(100) NULL, added_by_admin_id UUID NOT NULL, added_at TIMESTAMP NOT NULL",
  ");",
]));

// ============================================================
// 3. REPUTATION-SERVICE
// ============================================================
push(H1("3. reputation-service"));
push(P("Dịch vụ gánh ba vai trò khác bản chất — KHÔNG gộp chung logic dù cùng một dịch vụ:"));
push(table(
  [2600, 7038],
  ["Vai trò", "Bản chất dữ liệu"],
  [
    ["Sổ khoá (lock ledger)", "Bất biến, insert-only — bằng chứng pháp lý, nguồn quyết định lockout. KHÔNG thể là pure read model"],
    ["Điểm uy tín (reputation score)", "View sống, tính lại được từ lock_entry + contract.settled — phục vụ search ranking"],
    ["Tham chiếu tín dụng (credit reference)", "Export cho bên thứ ba (VARI) — bổ trợ, consent-based"],
  ],
  { size: 18 }
));

push(H2("3.1 Sổ khoá và công thức lockDurationDays"));
push(P("lockDurationDays snapshot cứng lúc tính, KHÔNG recompute sau đó — kể cả khi input (trackRecordMultiplier) đổi giá trị. Lý do: là bằng chứng pháp lý (LTM 2005 Điều 302), một con số tự đổi sau khi ghi thì mất giá trị bằng chứng."));
push(P([runs("lockDurationDays = baseDays × repeatOffenseMultiplier × trackRecordMultiplier × zeroProgressMultiplier", { bold: true, color: T.SUB })], { align: AlignmentType.CENTER }));
push(table(
  [3000, 2200, 4438],
  ["Multiplier", "Giá trị", "Điều kiện"],
  [
    ["baseDays", "30", "Cố định"],
    ["repeatOffenseMultiplier", "1x / 2x / 3x", "Lần vi phạm thứ mấy trong 24 tháng gần nhất (CÓ time window — khoảng cách dài giữa 2 vi phạm mang ý nghĩa: đã sửa hành vi)"],
    ["trackRecordMultiplier", "0.7x / 1.0x / 1.3x", "Dưới 5 hợp đồng hoàn thành: 1.0x. Từ 5 trở lên: 0.7x nếu ≥90% sạch, 1.0x nếu 70–90%, 1.3x nếu <70%. KHÔNG time window (mùa vụ tạo khoảng trống tự nhiên, không phải dấu hiệu bất thường)"],
    ["zeroProgressMultiplier (mới, 08/07/2026)", "1.5x / 1.0x", "1.5x khi cancel lúc 0 milestone nào từng SETTLED (ký xong bỏ ngay — tín hiệu xấu nhất có thể có, khác hẳn cancel giữa chừng do mâu thuẫn phát sinh); 1.0x mọi trường hợp còn lại. Cũng là lớp ma sát kỹ thuật cho rủi ro disintermediation (2 bên quen nhau qua platform rồi rủ nhau cancel-ở-milestone-đầu để tự giao dịch tay ngoài né phí) — không chặn tuyệt đối được, nhưng tăng chi phí lockout cho đúng pattern đáng nghi nhất"],
  ],
  { size: 18 }
));
push(P("Tách bạch: penalty debt phản ánh thiệt hại tài chính (tính riêng ở contract-service theo rate × giá trị), lockDurationDays phản ánh mức độ hành vi tái phạm — hai câu hỏi khác nhau, không dùng chung một biến. Mở khoá qua ba đường (đường nào tới trước): bên kia tự báo đã giải quyết; bên vi phạm nộp kết quả ràng buộc để Admin verify; hết thời hạn cố định. Penalty debt + lịch sử vi phạm không bao giờ xoá."));
push(P([runs("Insert-only enforce ở tầng DB permission: ", { bold: true }), runs("DB user chỉ có INSERT + SELECT trên lock_entry, không UPDATE/DELETE. \"Không bao giờ xoá\" là lời hứa yếu nếu chỉ dựa vào code không gọi hàm xoá — chặn ở DB permission thì bug code cũng không phá được. Cùng nguyên tắc audit-service.", {})]));
push(legal("Luật Thương mại 2005, Điều 302", "penalty debt ghi vào lock ledger bất biến là căn cứ bồi thường thiệt hại nếu bên bị vi phạm truy đòi qua VIAC/toà. Nền tảng không tự thu hộ được — đây là bằng chứng, không phải cơ chế cưỡng chế thu tiền."));

push(H2("3.2 Cưỡng chế khoá (2 tầng)"));
push(P("Không thể chặn ở Gateway/Keycloak — Keycloak chỉ cấp JWT (identity + role), không biết business lock state. Check nằm ở đúng use case tạo nghĩa vụ mới:"));
push(bullet([runs("CreateListing / tạo offer (product-service): ", { bold: true }), runs("fail-open. Chưa có Feign client tới user-service (thêm mới).", {})]));
push(bullet([runs("sign() (contract-service): ", { bold: true }), runs("fail-closed, bắt buộc. Đã có UserServiceClient (Feign) — thêm lockedUntil vào response + @CircuitBreaker với fallback throw. ƯU TIÊN đóng gap circuit breaker này trước mọi Feign call khác: sign() nằm trên đường ký hợp đồng, user-service down không có breaker sẽ chặn toàn bộ nền tảng ký, không phải lỗi cục bộ.", {})]));
push(P("Hai tầng không thừa nhau: khoá chỉ chặn tạo mới, không hồi tố hợp đồng đã ACTIVE/SIGNED. Seller sạch lúc tạo offer, dính khoá giữa đàm phán — chỉ sign() check lại mới bắt được. reputation-service publish reputation.locked/unlocked; user-service cache một field lockedUntil trên UserProfile (không gọi sync mỗi lần check — tránh dual-write, cache state flag thì ổn)."));

push(H2("3.3 Tham chiếu tín dụng & AML"));
push(P("Credit export định vị là reputation attestation, không phải nguồn chấm điểm tín dụng chính (reputation ≠ cash-flow data). Hướng đối tác: VARI (xếp hạng tín nhiệm DN nông nghiệp) — đóng vai đọc dữ liệu nền tảng rồi đưa cho ngân hàng. Chỉ chạy khi seller chủ động yêu cầu (consent rõ ràng), gate theo counterparty diversity (chỉ giao dịch 1-2 đối tác dù đủ 5+ hợp đồng sạch cũng không đủ điều kiện — giảm động cơ tạo hợp đồng giả)."));
push(P([runs("Chống rửa tiền (AML): ", { bold: true }), runs("giao dịch thông đồng (hai bên hợp tác, không ai dispute) không bao giờ chạm INSPECTOR — lỗ hổng cấu trúc mà credit export làm nặng thêm. Biện pháp: composite fraud score (không dựa một signal đơn) kết hợp tín hiệu luật định (structuring, đột biến doanh số) + tín hiệu nông sản (counterparty concentration, zero-variance pattern — cân seller khớp tuyệt đối cân buyer lặp lại nhiều lần là bất thường vì nông sản luôn hao hụt), tính theo cặp buyer-seller. Vượt ngưỡng → CONFIRM_CLEAN hold giao dịch KẾ TIẾP của cặp đó (không hồi tố). Admin trigger inspection đột xuất nhưng KHÔNG tự chọn tổ chức (random từ danh sách đã vet); chi phí do nền tảng chịu.", {})]));
push(P([runs("Cập nhật (06/07/2026) — tách 2 nhóm tín hiệu: ", { bold: true }), runs("\"đột biến doanh số\" (tín hiệu tương đối) cần baseline lịch sử của chính account — account mới không có gì để so, nên không sửa được bằng đổi threshold. Thêm nhóm tín hiệu TUYỆT ĐỐI song song: ngưỡng chuyển khoản 500 triệu đồng (Thông tư 27/2025/TT-NHNN) trigger CONFIRM_CLEAN hold NGAY trên chính giao dịch, kể cả giao dịch đầu tiên của account mới — đóng đúng gap \"one-shot fraud\" (tài khoản giả, 1 hợp đồng khủng, rút rồi bỏ) mà nhóm tương đối chỉ bắt được từ giao dịch thứ 2. Không đóng toàn bộ — one-shot fraud dưới 500 triệu vẫn còn nguyên gap cũ.", {})]));
push(risk("Sửa (08/07/2026) — ngưỡng tuyệt đối một mình không còn đủ để hold, phải đi kèm ≥1 tín hiệu hành vi khác.", "Bản 06/07/2026 để giá trị tuyệt đối tự nó trigger CONFIRM_CLEAN hold, không điều kiện gì thêm. Vấn đề phát hiện khi đối chiếu ví dụ hợp đồng thật (100-1000 tấn cà phê, tương đương 13,5-135 tỷ VNĐ theo giá Robusta thị trường ~92.000-97.000đ/kg) — gần như 100% hợp đồng thật của platform vượt xa 500 triệu chỉ vì khối lượng thương mại lớn, không liên quan gì tới hành vi khả nghi. Hold cứng theo giá trị đơn thuần sẽ khiến phần lớn giao dịch điển hình bị treo chờ Admin duyệt — giết chết đúng selling point lõi \"escrow tự thực thi, release tự động, không ai can thiệp\", biến Admin thành nút cổ chai cho mọi giao dịch lớn, ngược triết lý neutral-party. Chốt: hold chỉ kích hoạt khi ngưỡng tuyệt đối ĐI KÈM ≥1 tín hiệu hành vi khác (track record mỏng / zero-variance / counterparty mới) — vẫn đóng đúng phần gap \"one-shot fraud\", nhưng không còn đánh đồng \"giao dịch lớn\" với \"giao dịch khả nghi\". Với nhóm tín hiệu tương đối: không đổi, hold chỉ áp giao dịch KẾ TIẾP."));
push(P([runs("Nguồn phát hiện dời sang bank-service (sửa 08/07/2026): ", { bold: true }), runs("reputation-service không tự query ledger_entry để so giá trị — bank-service (bên giữ tiền thật, đúng chủ thể pháp lý theo Luật PCRT 2022 Điều 4) publish bank.large_transaction_flagged (Phần 2 §4.4) cho mọi LedgerEntry ≥ 500 triệu. reputation-service consume event này làm 1 trong các input cho composite score, không phải tự trigger hold độc lập.", {})]));
push(legal("Điều 29 Nghị định 52/2024 & Luật Phòng chống rửa tiền 2022", "Mục tiêu thiết kế là đạt bar \"có biện pháp quản trị rủi ro\", không phải bắt 100% fraud (bất khả thi với collusion đủ nguồn lực). Audit trail bất biến là bằng chứng due diligence nếu bị điều tra."));

push(H2("3.4 Use case & lược đồ dữ liệu"));
push(table(
  [3400, 6238],
  ["Use case", "Mô tả"],
  [
    ["ProcessLockoutUseCase", "Consume milestone.cancelled_with_penalty → tính multiplier → INSERT lock_entry → publish reputation.locked"],
    ["UnlockEarlyUseCase", "Admin trigger → status=UNLOCKED_EARLY + unlockReason bắt buộc → publish reputation.unlocked"],
    ["CheckLockStatusUseCase", "Expose cho user-service gọi Feign → trả lockedUntil hiện tại"],
    ["GetCreditExportUseCase", "Seller tự trigger (consent) → check counterparty diversity gate → JSON export"],
    ["FlagSuspiciousPatternUseCase", "Tính composite fraud score theo cặp/account (2 nhóm tín hiệu) → publish event hold khi vượt ngưỡng. Nhóm tương đối: hold giao dịch KẾ TIẾP. Nhóm tuyệt đối (consume bank.large_transaction_flagged — sửa 08/07/2026): hold ngay giao dịch hiện tại CHỈ KHI đi kèm ≥1 tín hiệu hành vi khác — không tự hold một mình"],
  ],
  { size: 18 }
));
push(codeblock([
  "-- reputation_db · DB user chỉ INSERT + SELECT (insert-only)",
  "CREATE TABLE lock_entry (",
  "  entry_id                  UUID PRIMARY KEY,",
  "  source_event_id           UUID NOT NULL UNIQUE,   -- idempotency key",
  "  contract_id               UUID NOT NULL,",
  "  user_id                   UUID NOT NULL,",
  "  penalized_role            VARCHAR(10) NOT NULL,   -- BUYER | SELLER",
  "  base_days                 INT NOT NULL DEFAULT 30,",
  "  repeat_offense_multiplier DECIMAL(3,2) NOT NULL,",
  "  track_record_multiplier   DECIMAL(3,2) NOT NULL,",
  "  lock_duration_days        INT NOT NULL,           -- snapshot lúc tính",
  "  locked_until              TIMESTAMP NOT NULL,",
  "  status                    VARCHAR(20) NOT NULL,   -- LOCKED|UNLOCKED_EARLY|EXPIRED",
  "  unlock_reason             TEXT NULL,",
  "  created_at                TIMESTAMP NOT NULL DEFAULT now()",
  ");",
  "CREATE INDEX idx_lock_entry_user ON lock_entry(user_id);",
  "-- reputation score: KHÔNG có bảng riêng — tính từ lock_entry + contract.settled lúc cần",
]));

push(H2("3.5 Đối xứng hoá — buyer reputation hiển thị cho seller + chống flag-abuse (mới, 08/07/2026)"));
push(P("Mọi tín hiệu minh bạch đã xây (verificationLevel, geoRiskLevel bên product-service, reputation score ở đây) đều một chiều buyer-xem-seller. Seller không có tín hiệu nào để đánh giá buyer trước khi ký (buyer có hay bùng không, có hay lạm dụng FLAG_ISSUE ép seller vào dispute không) — với luận điểm \"bảo vệ bên yếu\" xuyên suốt dự án, đây là chỗ hội đồng dễ hỏi: \"seller được gì để tự bảo vệ, ngoài việc bị chấm điểm?\""));
push(P([runs("Dữ liệu đã có sẵn, chỉ thiếu chiều hiển thị: ", { bold: true }), runs("lock_entry (§3.4) đã insert-only cho cả penalizedRole=BUYER lẫn SELLER từ đầu — dữ liệu \"buyer này từng bùng mấy lần, lock bao lâu\" đã tồn tại, không cần cơ chế mới. Thêm: (1) endpoint GET /reputation/{userId}/public-summary — query theo userId (không phân biệt buyer/seller gọi), trả reputation score + lock history công khai, không cần consent như credit-export (§3.3) vì đây là thông tin đối tác cần biết TRƯỚC KHI quyết định ký, không phải hồ sơ tín dụng riêng tư; (2) framing lại buyerDepositRate/sellerDepositRate là công cụ 2 chiều theo mức tin tưởng (cơ chế đã đúng ở milestone-escrow §2.1/§6.1, chỉ cần nói rõ) — seller mới gặp buyer lạ, xem public-summary thấy buyer track record xấu → seller có quyền đàm phán buyerDepositRate cao hơn 5% mặc định lúc NEGOTIATING, không phải chỉ buyer mới có quyền đòi cọc seller.", {})]));
push(P([runs("Tín hiệu chống buyer lạm dụng FLAG_ISSUE vô cớ: ", { bold: true }), runs("lỗ thật seller chưa được bảo vệ — buyer có thể flag bừa để ép seller vào dispute/kéo dài mà không mất gì (chi phí giám định do bên thua chịu, nhưng buyer có thể chấp nhận rủi ro đó để gây áp lực). Thêm 1 input event mới: milestone.dispute_resolved (contract-service — bắn khi DisputeRoutingService 3-tier ra phán quyết cho milestone từng CONTESTED; payload {milestoneId, flaggedBy: BUYER, resolutionFavors: BUYER|SELLER}, chỉ buyer flag được ở state machine hiện tại). reputation-service đếm tỷ lệ resolutionFavors=SELLER trên tổng số lần buyer đó từng FLAG_ISSUE — tỷ lệ cao (flag rồi thua nhiều lần) là tín hiệu buyer lạm dụng, hiển thị trong public-summary để seller thấy trước khi ký. Dùng đúng bộ máy reputation đã có (view sống, tính từ event, không bảng riêng) — chỉ thêm 1 loại tín hiệu đầu vào, không phải cơ chế mới.", {})]));
push(P("Không phải known-limitation cần chấp nhận — là hoàn chỉnh hoá luận điểm gốc \"bảo vệ bên yếu thế\" cho đúng cả 2 chiều, không chỉ chiều buyer nhìn seller."));

// ============================================================
// 4. AUDIT-SERVICE
// ============================================================
push(H1("4. audit-service"));
push(P("Nhận và nối các hash từ nhiều nguồn (signedContentHash từ contract-service, reportHash từ inspection-service, các milestone event mang số liệu tranh chấp được) vào một chuỗi append-only, dùng chung một schema AuditRecord."));
push(H2("4.1 Event nào vào chain"));
push(P("Không phải mọi event vào chain — tiêu chí: event mang số liệu/quyết định có thể bị tranh chấp làm bằng chứng, hoặc là input để tính con số sẽ bị tranh chấp. 6/8 milestone event vào chain:"));
push(table(
  [4200, 1300, 4138],
  ["Milestone event", "Vào chain", "Lý do"],
  [
    ["milestone.seller_weighed", "Có", "Mang sellerDeclaredWeight — input tính Delta 1/2"],
    ["milestone.buyer_confirmed", "Có", "Mang buyerReceivedWeight — input tính Delta 2"],
    ["milestone.flagged", "Không", "Chỉ tín hiệu bấm nút, không mang số liệu riêng"],
    ["milestone.force_majeure_claimed", "Có", "Bằng chứng bất khả kháng (BLDS 156/351)"],
    ["milestone.force_majeure_resolved", "Có", "Quyết định APPROVE/REJECT — kết quả tranh chấp"],
    ["milestone.settled", "Có", "Kết quả cuối, số tiền release"],
    ["milestone.cancelled_with_penalty", "Có", "Căn cứ tính lockDurationDays, penalty debt"],
    ["milestone_sync (Local Outbox)", "Không", "Cơ chế sync nội bộ, không có ý nghĩa pháp lý"],
  ],
  { size: 17, colAlign: [null, AlignmentType.CENTER, null] }
));
push(P("Ngoài ra: Contract.signedContentHash (lúc sign) và reportHash (INSPECTOR submit) — dùng chung schema AuditRecord."));

push(H2("4.2 Dual chain trên một bảng"));
push(P("Không tách hai bảng cho global chain và per-contract chain (tốn kém, trùng lặp). Một bảng, hai cột prevHash khác mục đích:"));
push(codeblock([
  "CREATE TABLE audit_record (",
  "  record_id          UUID PRIMARY KEY,",
  "  contract_id        UUID NOT NULL,",
  "  source_type        VARCHAR(50) NOT NULL,",
  "  source_event_type  VARCHAR(100) NOT NULL,   -- vd 'milestone.settled'",
  "  content            JSON NOT NULL,           -- payload gốc",
  "  record_hash        VARCHAR(64) NOT NULL,    -- SHA256(content + prev_hash_global)",
  "  prev_hash_global   VARCHAR(64),             -- NULL nếu record đầu toàn hệ thống",
  "  prev_hash_contract VARCHAR(64),             -- NULL nếu record đầu của contract này",
  "  ots_proof          TEXT,                    -- NULL trừ event vào chain (§4.3)",
  "  created_at         TIMESTAMP NOT NULL DEFAULT now()",
  ");",
  "CREATE INDEX idx_audit_contract ON audit_record(contract_id, created_at);",
  "-- DB user chỉ INSERT + SELECT — không UPDATE/DELETE",
]));
push(P([runs("Hai kiểu verify: ", { bold: true }), runs("per-contract (WHERE contract_id, theo prev_hash_contract) — export bằng chứng gọn cho một vụ (VIAC/toà), tự đủ. Global (theo prev_hash_global, đọc toàn bảng) — phát hiện xoá nguyên cụm record của một contract, thứ mà per-contract chain đứng một mình không phát hiện được.", {})]));
push(callout("Lưu ý concurrency (Spring):", "tính prev_hash_global cần đọc record cuối toàn bảng; nhiều contract insert đồng thời cần SELECT ... FOR UPDATE trên row cuối (pessimistic lock) hoặc một sequence riêng, tránh race làm hai insert tính ra cùng prev_hash_global và gãy chain. Với quy mô B2B forward contract (tần suất thấp), serialize mức này không phải bottleneck.", "note"));

push(H2("4.3 Đa vị trí + neo email + OpenTimestamps"));
push(P("Ba nơi lưu hash phải khớp tuyệt đối: contract-service DB, audit-service DB, và email gửi buyer/seller lúc sign — nơi thứ ba nằm NGOÀI nền tảng (hộp thư cá nhân), ngoài tầm với của cả Admin có quyền root. Email cũng cung cấp timestamp độc lập (do hệ thống email bên thứ ba ghi, nền tảng không tự lùi ngày được)."));
push(P([runs("OpenTimestamps (OTS) — neo Bitcoin, 2 tầng trigger: ", { bold: true }), runs("(1) event-triggered: mỗi event vào chain gọi OTS API lấy .ots cho record_hash (đã cuốn theo toàn bộ lịch sử qua prev_hash_global → là commitment cho toàn chain). (2) weekly luôn chạy: VerifyChainJob mỗi Chủ nhật tự tạo OTS cho head hiện tại kể cả tuần không có event vào chain — đảm bảo trần cứng cửa sổ tấn công ≤ 7 ngày. OTS bắt được cascade tampering (xoá + sửa lại toàn bộ prevHash phía sau để chain tự nhất quán) mà verify tự-so-với-chính-nó không phát hiện. Miễn phí, không cần ví crypto. Gửi .ots cho buyer/seller CHỈ tại milestone.settled (tránh noise email ở bước trung gian).", {})]));

push(H2("4.4 VerifyChainJob + alert routing"));
push(P("@Scheduled hàng tuần 2–3h sáng Chủ nhật (traffic thấp), chạy TRƯỚC digest cùng một lần chạy:"));
push(numbered("Đọc toàn bộ audit_record theo created_at; tính lại record_hash từng row, so với giá trị đã lưu."));
push(numbered("Lấy anchoredHash (record_hash gần nhất đã OTS-anchor); query SELECT 1 WHERE record_hash = anchoredHash — cascade tampering bất kỳ điểm nào sẽ làm giá trị này biến mất khỏi bảng (chỉ một query đủ)."));
push(numbered("Tạo OTS mới cho head hiện tại (tầng 2) làm anchor cho lần verify tuần sau."));
push(numbered("Khớp 100% + anchoredHash còn tồn tại → WEEKLY_VERIFY_OK → DigestJob gửi digest cho Software Buyer. Ngược lại → WEEKLY_VERIFY_FAILED → flow alert."));
push(P([runs("Alert routing khi fail — không để một người làm gatekeeper: ", { bold: true }), runs("verify fail → hệ thống tự động bắn SONG SONG (không qua bước duyệt của ai) tới Admin (điều tra kỹ thuật) VÀ nhiều địa chỉ liên hệ phía Software Buyer (không chỉ một người). Nếu chính Admin là người sửa data, cơ chế \"báo Admin rồi đợi Admin điều tra\" sẽ bị chặn vĩnh viễn — nên bỏ bước gatekeeper đó. Buyer/seller từng hợp đồng chỉ được báo SAU khi Admin khoanh vùng đúng contract_id bị ảnh hưởng (không tự động — thông tin nhạy cảm cần chính xác).", {})]));

push(P([runs("External Verifier self-service watchdog (08/07/2026): ", { bold: true }), runs("ngoài VerifyChainJob nội bộ, expose GET /security/audit-hash (chỉ-đọc, auth nhẹ — hash không phải bí mật) để tổ chức vận hành platform (Software Buyer, không cột cứng VICOFA) tự query record_hash và đối soát với bản hash nhận qua email anchor, bằng lịch riêng ngoài tầm Admin. Phát hiện lệch → ký lệnh emergency-lock (bank-service §4.5) đóng băng toàn hệ thống. Đây là lần đầu phép đối chiếu chủ động không phụ thuộc DUY NHẤT vào job nội bộ — thu hẹp lỗ hổng “phải có người chủ động nhìn” ở §6, dù không đóng hoàn toàn (verifier vẫn phải chịu query; collusion Admin+verifier là giới hạn cố hữu trusted-operator).", {})]));
push(table(
  [3400, 6238],
  ["source_type", "Ý nghĩa / sức nặng bằng chứng"],
  [
    ["MILESTONE_EVENT", "Số liệu/quyết định milestone (6/8 event)"],
    ["CONTRACT_SIGNED", "signedContentHash lúc ký"],
    ["INSPECTION_REPORT", "Report Level 1.5 — đứng sau actor đã KYC + login (Signature + RBAC)"],
    ["EXTERNAL_INSPECTION_REPORT", "Report Level 2 đã CONFIRMED — không actor login (SPF/DKIM + Admin confirm)"],
    ["LEVEL2_INSPECTION_COMMISSIONED", "Chỉ ghi YÊU CẦU commission — không phải kết quả"],
    ["EXTERNAL_VERIFIER_KEY_REGISTERED", "Đăng ký/đổi public key External Verifier — root-of-trust kill switch, chống Admin lén swap key (08/07/2026)"],
    ["SECURITY_LOCK_TRIGGERED / _UNLOCK_", "Quyết định đóng/mở băng toàn hệ thống — kill switch tự nó cũng tamper-evident (08/07/2026)"],
  ],
  { size: 18 }
));
push(P("Không gộp chung các loại report dù cùng mục đích nghiệp vụ — sức nặng bằng chứng khác nhau thật (actor đã KYC vs kết quả xác nhận vs chỉ yêu cầu); gộp chung một nhãn là che giấu khác biệt evidentiary mà hội đồng sẽ hỏi. Xuất DDS (EUDR): verify chain trước, rồi xuất PDF/CSV on-demand từ audit trail của các hợp đồng liên quan."));
push(legal("Luật GDĐT 2023, Điều 14.2", "Chuỗi hash append-only + DB INSERT-only + neo độc lập (email, Bitcoin) là hiện thực hoá kỹ thuật của yêu cầu \"bảo toàn tính nguyên vẹn\" — cơ sở để audit trail có giá trị chứng cứ và hợp lệ cho kiểm toán EUDR."));

// ============================================================
// 5. SEQUENCE FLOWS
// ============================================================
push(H1("5. Luồng trình tự xuyên dịch vụ"));
push(H2("5.1 Tranh chấp Level 1.5 → report → quyết toán"));
push(table(
  [700, 2300, 3400, 3238],
  ["#", "Actor / Dịch vụ", "Hành động / Sự kiện", "Kết quả"],
  [
    ["1", "BUYER → contract-service", "FLAG_ISSUE → CONTESTED → DisputeRoutingService route=LEVEL_1_5", "Milestone chờ giám định"],
    ["2", "INSPECTOR", "Kiểm tra thực địa, nộp report + file (≤1800s sau step-up)", "reportHash tính từ actor đã KYC"],
    ["3", "inspection-service", "INSERT inspection_report + Signature(reportId) → publish INSPECTION_REPORT", "audit-service nối chain"],
    ["4", "contract-service", "Verify reportHash → advance milestone SETTLED theo phán quyết", "escrow release theo kết quả; bên thua chịu phí"],
  ],
  { size: 17, colAlign: [AlignmentType.CENTER, null, null, null] }
));
push(H2("5.2 Escalate Level 2 → commission → intake → quyết toán"));
push(table(
  [700, 2300, 3400, 3238],
  ["#", "Actor / Dịch vụ", "Hành động / Sự kiện", "Kết quả"],
  [
    ["1", "ADMIN → inspection-service", "InitiateLevel2Inspection: đọc level2InspectorOrg, verify allowlist, INSERT commission(REQUESTED)", "audit: LEVEL2_INSPECTION_COMMISSIONED"],
    ["2", "Hệ thống", "Gửi mail tới org kèm 3 địa chỉ (buyer, seller, intake@)", "Yêu cầu đã gửi (không phải đồng ý)"],
    ["3", "org → intake@ (webhook)", "Ack → ParseCommissionAck gợi ý case ID → Admin ConfirmCaseId", "status=CASE_ID_CONFIRMED"],
    ["4", "org → intake@ (webhook)", "Report cuối → reportHash tính NGAY → PENDING_REVIEW", "Chưa vào audit"],
    ["5", "ADMIN", "ReviewPendingExternalReport APPROVE → CONFIRMED → publish EXTERNAL_INSPECTION_REPORT", "audit nối chain; milestone SETTLED theo report"],
  ],
  { size: 17, colAlign: [AlignmentType.CENTER, null, null, null] }
));
push(H2("5.3 Cưỡng chế khoá tài khoản"));
push(table(
  [700, 2300, 3400, 3238],
  ["#", "Actor / Dịch vụ", "Hành động / Sự kiện", "Kết quả"],
  [
    ["1", "contract-service", "Milestone bị cancel-có-penalty → milestone.cancelled_with_penalty", "Mang penalizedRole + input rate"],
    ["2", "reputation-service", "ProcessLockout: tính multiplier → INSERT lock_entry (insert-only) → publish reputation.locked", "lockDurationDays snapshot bất biến"],
    ["3", "user-service", "Consume reputation.locked → cache lockedUntil trên UserProfile", "Không gọi sync mỗi lần check"],
    ["4", "contract-service", "Lần sign() tiếp theo → Feign CheckLockStatus (fail-closed + circuit breaker)", "lockedUntil > now → chặn ký"],
  ],
  { size: 17, colAlign: [AlignmentType.CENTER, null, null, null] }
));

// ============================================================
// 6. LIMITATIONS
// ============================================================
push(H1("6. Giới hạn có chủ đích"));
push(bullet([runs("Chống thông đồng toàn diện. ", { bold: true }), runs("Nếu Admin VÀ đa số người nhận cảnh báo phía Software Buyer cùng thông đồng và không ai chủ động đối chiếu OTS — không cơ chế phần mềm nào trong kiến trúc hiện tại chặn được (đúng bài toán trustless consensus mà nhóm chủ đích không theo hướng blockchain). Email anchor lúc sign (gửi buyer/seller thật, khác người/khác thời điểm với người nhận digest) và bằng chứng toán học độc lập vẫn đứng vững.", {})]));
push(bullet([runs("OTS thu hẹp, không đóng hoàn toàn. ", { bold: true }), runs("OTS đảm bảo bằng chứng tồn tại độc lập ngoài nền tảng, nhưng không tạo ra động lực phát hiện — vế \"phải có người chủ động đối chiếu\" vẫn là giới hạn cố hữu của mô hình trusted-operator.", {})]));
push(bullet([runs("AML phát hiện giao dịch KẾ TIẾP cho tín hiệu tương đối; ngưỡng tuyệt đối bắt được ngay giao dịch đầu (mới, 06/07/2026). ", { bold: true }), runs("Detect dựa trên pattern lặp lại cần đủ lịch sử — chính giao dịch làm score vượt ngưỡng không bị hold, chỉ giao dịch tiếp theo của cặp đó, giới hạn cấu trúc không sửa bằng đổi threshold. Nhóm tín hiệu tuyệt đối mới (ngưỡng luật định 500 triệu) đóng được phần này cho giao dịch đủ lớn — nhưng one-shot fraud dưới ngưỡng đó vẫn còn nguyên gap cũ.", {})]));
push(bullet([runs("Tự động hoá tra cứu BoA-VIAS/ILAC bị deferred. ", { bold: true }), runs("Hiện Admin tra tay qua web (tần suất thấp, không phải hot path); tự động hoá sau nếu hai tổ chức có API công khai.", {})]));
push(bullet([runs("VerifyChainJob quét toàn bảng O(n). ", { bold: true }), runs("Ổn ở vài nghìn–vài chục nghìn record; nếu phình tới hàng triệu, hướng fix là incremental verify (con trỏ last_verified_record_id) — ngoài phạm vi đồ án.", {})]));

// ============================================================
// 7. OPEN ITEMS
// ============================================================
push(H1("7. Điểm còn treo"));
push(bullet([runs("search-service ", { bold: true }), runs("chưa thiết kế — kỳ vọng reputation-service publish event khi score đổi, search tự consume denormalized (xử lý ở phần SDS liên quan).", {})]));
push(bullet([runs("Checklist KYC INSPECTOR Level 1.5 ", { bold: true }), runs("— nguyên tắc xác minh giấy phép kiểm định đã chốt; danh mục giấy tờ cụ thể cần xác nhận nghiệp vụ.", {})]));

module.exports = { body };

if (require.main === module) {
push(endMark());

const doc = buildDoc(body, {
  title: "AgriContract — SDS — Phần 3: Giám định & Tin cậy (Inspection · Reputation · Audit)",
  headerText: "AgriContract · SDS — Phần 3",
  footerText: "SDS v1.0 · Phần 3 · Tháng 7/2026",
});
Packer.toBuffer(doc).then(buf => { fs.writeFileSync("/tmp/AgriContract_06_SDS_Part3_v1.docx", buf); console.log("written", buf.length); });

}
