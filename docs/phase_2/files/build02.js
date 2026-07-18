const fs = require("fs");
const { writeDocx } = require("./docx_output.js");
const { D, T, runs, P, H1, H2, H3, bullet, numbered, quote, callout, legal, risk, src, table, spacer, cover, toc, endMark, buildDoc } = require("./acdocx.js");
const { Packer, AlignmentType } = D;

const body = [];
const push = (...x) => x.forEach(e => body.push(e));

push(...cover("AGRICONTRACT", "Giải Pháp, Người Dùng & Mô Hình Kinh Doanh",
  "Cơ chế ký quỹ theo đợt, giám định độc lập phân tầng và audit trail bất biến cho hợp đồng nông sản B2B",
  ["Tài liệu nội bộ — Đồ án Tốt nghiệp", "Phiên bản 5.0 · Tháng 7/2026"]));
push(...toc());

// ============================================================
// 0. EXEC SUMMARY
// ============================================================
push(H1("Tóm tắt điều hành"));
push(P("AgriContract số hoá vòng đời hợp đồng mua bán nông sản B2B — từ đàm phán điều khoản, ký kết điện tử, giữ tiền ký quỹ theo từng đợt giao hàng, đến giải ngân sau khi xác nhận giao nhận. Các quyết định và số liệu có khả năng tranh chấp được ghi vào audit trail bất biến, tối giản PII, có thể xuất thành gói bằng chứng hỗ trợ DDS phục vụ chứng cứ pháp lý và kiểm toán EUDR."));
push(P("Điểm khác biệt cốt lõi của mô hình là cơ chế tự thực thi được thiết kế bám sát ba đặc thù của ngành. Thứ nhất, tiền ký quỹ được khoá theo từng đợt giao hàng (milestone) thay vì khoá toàn bộ giá trị hợp đồng ngay từ đầu — giải quyết áp lực vốn lưu động ở quy mô thương mại. Thứ hai, bên bán (HTX) mặc định không phải nộp tiền cọc — uy tín tích luỹ và cơ chế khoá tài khoản là lớp ràng buộc bổ sung, hiệu lực phụ thuộc mật độ mạng lưới, giao dịch lặp lại và khả năng liên thông chế tài; hai bên vẫn có thể tự đàm phán một khoản cọc nhỏ của bên bán nếu bên mua thấy cần, tuỳ mức độ tin tưởng với đối tác cụ thể. Thứ ba, tranh chấp được giải quyết phân tầng theo giá trị và độ phức tạp hàng hoá, với giám định viên độc lập ở hai cấp bên ngoài — mục tiêu rút ngắn thời gian thu thập bằng chứng và xử lý nội bộ so với tố tụng kéo dài; SLA thực tế còn phụ thuộc tổ chức giám định và mức độ tranh chấp."));
push(P("Nền tảng không tự giữ tiền: tiền do ngân hàng giữ với vai trò định chế được cấp phép, nền tảng chỉ ra lệnh khoá/giải ngân/phạt. Cấu trúc này loại bỏ rủi ro pháp lý nghiêm trọng nhất — cung ứng dịch vụ trung gian thanh toán không phép. Về dài hạn, audit trail tích luỹ trở thành lịch sử tín dụng có thể xác minh, là hạ tầng dữ liệu cho tín dụng nông nghiệp dựa trên dữ liệu giao dịch thay vì tài sản thế chấp."));

// ============================================================
// 1. PRODUCT DEFINITION
// ============================================================
push(H1("1. Định nghĩa sản phẩm"));
push(P("AgriContract là nền tảng số hoá hợp đồng mua bán nông sản B2B với cơ chế tiền cọc và tiền phong toả do ngân hàng giữ. Hợp đồng không bị phủ nhận giá trị pháp lý chỉ vì được giao kết và lưu trữ bằng phương tiện điện tử; hiệu lực vẫn phụ thuộc các điều kiện chung của giao dịch, pháp luật về hợp đồng và quy định có liên quan."));
push(P([runs("Phạm vi giới hạn có chủ đích ở tầng hợp đồng. ", { bold: true }), runs("AgriContract không xử lý logistics, không phải sàn thương mại điện tử, không thay thế hệ thống kế toán doanh nghiệp. Phần mềm giải quyết một vấn đề cụ thể — thiếu cơ chế tự thực thi trong giao dịch forward contract nông sản B2B — và không có tham vọng giải quyết toàn bộ chuỗi cung ứng.", {})]));
push(legal("Luật GDĐT 2023, Điều 8, Điều 14 và Điều 34–36", "Thông tin không bị phủ nhận giá trị pháp lý chỉ vì ở dạng thông điệp dữ liệu; việc giao kết và thực hiện hợp đồng điện tử vẫn phải tuân thủ pháp luật về hợp đồng và quy định liên quan. Audit trail có thể được sử dụng làm chứng cứ, với giá trị phụ thuộc độ tin cậy của phương thức tạo lập, gửi, nhận, lưu trữ và bảo toàn tính toàn vẹn."));

// ============================================================
// 2. FIVE USER TIERS
// ============================================================
push(H1("2. Năm tầng người dùng"));
push(P("Các tầng stakeholder không đồng nhất với role runtime. Keycloak có năm role BUYER, SELLER, INSPECTOR, OPERATOR và ADMIN. OPERATOR xử lý công việc vận hành hằng ngày có thể đảo ngược như KYC, review, moderation và nhập giá thủ công; ADMIN giữ các quyết định rủi ro cao, security/audit và bước approve maker-checker. External Verifier và System là actor kỹ thuật, không phải role người dùng thông thường."));
push(P("Nền tảng có năm nhóm người dùng với quan hệ pháp lý và quyền hạn hoàn toàn khác nhau. Nhầm lẫn giữa các tầng này dẫn đến hiểu sai về mô hình doanh thu, cơ chế trust và phân tích rủi ro pháp lý."));
push(table(
  [1500, 2900, 5238],
  ["Tầng", "Chủ thể", "Vai trò"],
  [
    [["Tầng 1", "Software Buyer"], "Hiệp hội ngành hàng (VICOFA, VRA, VINACAS) hoặc doanh nghiệp thu mua lớn (Intimex, Phúc Sinh Group, XNK 2/9 Đắk Lắk)", "Trả phí license/subscription — nguồn doanh thu của nền tảng. Triển khai cho cộng đồng thành viên; chỉ định OPERATOR vận hành hằng ngày và ADMIN kiểm soát hành động rủi ro cao"],
    [["Tầng 2", "Platform Buyer"], "Doanh nghiệp thu mua, tập đoàn xuất khẩu nông sản (nhiều trường hợp trùng Tầng 1)", "Khởi tạo offer và đàm phán điều khoản; khoá tiền ký quỹ trước khi bên bán giao hàng; xác nhận nhận hàng để kích hoạt giải ngân"],
    [["Tầng 3", "Platform Seller"], "Hợp tác xã nông sản, nông hộ liên kết, doanh nghiệp cung ứng nguyên liệu", "Đăng listing sau khi được xác minh; đàm phán và ký hợp đồng điện tử; giao hàng và nhận thanh toán. Nhóm được cơ chế ký quỹ bảo vệ trực tiếp nhất"],
    [["Tầng 4", "INSPECTOR"], "Tổ chức giám định được Nhà nước công nhận: Vinacontrol, Quatest, SGS, Bureau Veritas, Intertek", "Nhân chứng chuyên môn độc lập, không phải người phán xử. Xác định số lượng/chất lượng tại điểm giao nhận; nộp inspection report có hash xác thực, không sửa được sau khi submit"],
    [["Tầng 5", "Escrow Holder"], "Ngân hàng thương mại được NHNN cấp phép (Agribank/BIDV)", "Không phải người dùng nền tảng. Giữ tiền thật; nền tảng chỉ gửi lệnh khoá/giải ngân/phạt. Cấu trúc này loại bỏ rủi ro vi phạm quy định trung gian thanh toán"],
  ],
  { size: 18 }
));
push(P([runs("Vì sao bán cho hiệp hội, không bán trực tiếp cho HTX. ", { bold: true }), runs("HTX nhỏ không mua phần mềm. Hiệp hội hoặc doanh nghiệp thu mua lớn triển khai nền tảng, thành viên của họ sử dụng. Cách này cũng giải quyết bài toán niềm tin: HTX không cần tin một startup mới — họ tin VICOFA/VRA đã triển khai nền tảng. Khi ngân hàng giữ tiền, niềm tin hoàn toàn độc lập với uy tín thương hiệu của nền tảng.", {})]));

push(H3("Xử lý xung đột lợi ích khi Tầng 1 và Tầng 2 trùng nhau"));
push(P("Khi doanh nghiệp thu mua vừa mua license vừa là bên mua hàng trên nền tảng, đội vận hành do họ chỉ định có xung đột lợi ích trong vai trò xử lý tranh chấp. Hai cơ chế kiểm soát:"));
push(numbered("Tranh chấp giá trị lớn hoặc hàng hoá phức tạp bắt buộc kích hoạt INSPECTOR độc lập — OPERATOR/ADMIN được ủy quyền chỉ thực thi kết quả giám định, không được ra phán quyết độc lập."));
push(numbered("Mọi quyết định vận hành của OPERATOR/ADMIN được ghi vào audit trail không thể xoá/sửa sau khi submit; hành động override bị đánh dấu vĩnh viễn."));
push(P("Khuyến nghị vận hành: ưu tiên triển khai qua hiệp hội ngành hàng — bên trung lập, không có lợi ích đối lập với Buyer hay Seller trong từng giao dịch — để loại bỏ xung đột lợi ích ngay từ mô hình phân phối."));

// ============================================================
// 3. TRANSACTION FLOW
// ============================================================
push(H1("3. Luồng giao dịch"));
push(P("Vòng đời một hợp đồng gồm hai cấp lồng nhau: cấp hợp đồng (từ đăng listing đến quyết toán toàn bộ) và cấp đợt giao hàng — milestone (mỗi đợt tự đi qua vòng đời cân hàng, giao nhận, quyết toán riêng). Thiết kế hai cấp này là nền tảng của cơ chế ký quỹ theo đợt ở Mục 3.2."));

push(H2("3.1 Vòng đời cấp hợp đồng"));
push(table(
  [1000, 4700, 2100, 1838],
  ["Bước", "Hành động", "Trạng thái hợp đồng", "Ký quỹ"],
  [
    ["1", "Bên bán đăng listing sau khi OPERATOR/ADMIN được ủy quyền xác minh tư cách pháp nhân và gắn dữ liệu geolocation cho lô hàng", "LISTED", "—"],
    ["2", "Bên mua gửi offer đề xuất điều khoản", "OFFERED", "—"],
    ["3", "Hai bên đàm phán điều khoản, gồm lịch giao hàng theo đợt (milestone schedule) và các tỷ lệ penalty/tolerance; mọi thay đổi ghi vào audit trail", "NEGOTIATING", "—"],
    ["4", "Hai bên ký điện tử (xác thực lại phiên + OTP). Cọc skin-in-the-game của bên mua được khoá một lần", "SIGNED", "buyerDepositRate khoá"],
    ["5", "Hợp đồng kích hoạt; N milestone chạy bên trong (tuần tự hoặc song song)", "ACTIVE", "batchAmount khoá theo đợt"],
    ["6", "Tất cả milestone lần lượt quyết toán xong", "SETTLED", "Giải ngân + hoàn cọc"],
  ],
  { size: 18, colAlign: [AlignmentType.CENTER, null, null, null] }
));
push(P("Khác với mô hình một điểm giao hàng duy nhất, ACTIVE ở đây là một trạng thái kéo dài: hợp đồng chỉ chuyển sang SETTLED khi milestone cuối cùng trong lịch giao hàng đã quyết toán. “Giao hàng xong” không còn là một sự kiện tức thời mà là kết quả cộng dồn của nhiều sự kiện nhỏ."));

push(H2("3.2 Ký quỹ theo đợt — Milestone Escrow"));
push(P("Khoá 100% giá trị hợp đồng ngay từ đầu gây áp lực vốn lưu động nghiêm trọng ở quy mô thương mại. Tại mức 1.000 tấn cà phê giá 135 triệu đồng/tấn, một hợp đồng đơn lẻ đòi hỏi 135 tỷ đồng nằm bất động trong ký quỹ suốt nhiều tháng — không doanh nghiệp nào có thể vận hành nhiều hợp đồng song song với cấu trúc vốn như vậy."));
push(...quote("Trước kia 100 triệu mua được 2 tấn thì nay 100 triệu chỉ còn 1 tấn. Chúng tôi phải giảm kế hoạch từ 125.000 tấn xuống 105.000 tấn để kiểm soát rủi ro vốn.", "Ông Lê Đức Huy, Tổng Giám đốc Công ty XNK 2/9 Đắk Lắk — VTV.vn, 17/4/2024"));
push(P("Mô hình Delivery-vs-Payment: bên mua khoá từng đợt thanh toán theo tiến độ giao hàng được xác nhận. Ba nguyên tắc thiết kế:"));
push(bullet([runs("Không bắt buộc tiền cọc bên bán, nhưng optional. ", { bold: true }), runs("Mặc định HTX không phải nộp tiền mặt ứng trước khi còn đang nợ vật tư — ràng buộc khả thi thực tế cho phần lớn trường hợp. Hai bên có thể tự đàm phán một khoản cọc nhỏ của bên bán vào điều khoản nếu bên mua thấy cần, tuỳ mức độ tin tưởng với đối tác cụ thể — nền tảng cấp công cụ, không áp đặt. Khi không có cọc, hàng đã giao vẫn là rủi ro thực tế của bên bán, không thể thu hồi.", {})]));
push(bullet([runs("Lịch giao hàng linh hoạt (Dynamic Milestone Schedule). ", { bold: true }), runs("Tỷ lệ từng đợt không cố định mà đàm phán vào điều khoản dựa trên lịch tài chính thực tế của HTX. Ví dụ HTX có khoản vay đáo hạn tháng 2 có thể thoả thuận đợt đầu 50% thay vì 30%.", {})]));
push(bullet([runs("Cọc skin-in-the-game của bên mua (buyerDepositRate). ", { bold: true }), runs("Một khoản cọc nhỏ mặc định 5% giá trị hợp đồng, khoá một lần lúc ký và giữ tới khi quyết toán cuối. Vai trò là bảo đảm bên mua luôn “có cái để mất”, không phải để phủ toàn bộ rủi ro tài chính — việc đó thuộc về khoản khoá từng đợt.", {})]));
push(P("Chi phí ký quỹ là rẻ so với rủi ro nó bảo hiểm. Với hợp đồng 100 tấn cà phê giá 135 triệu/tấn (tổng 13,5 tỷ đồng):"));
push(table(
  [4800, 2600, 2238],
  ["Kịch bản", "Chi phí thực tế", "Kết quả"],
  [
    ["Bên mua khoá 30% trong 3 tháng chờ giao hàng (chi phí cơ hội theo lãi vốn lưu động 8%/năm)", "≈ 81 triệu đồng", "Đảm bảo nhận đủ 100 tấn theo giá đã chốt. Nếu bên bán phá vỡ hợp đồng: nhận lại phần khoá + penalty, vẫn có hàng đợt 1"],
    ["Không dùng nền tảng (kịch bản 2024: bên bán phá vỡ hợp đồng khi giá tăng 125%)", "Thiệt hại hàng chục tỷ mỗi hợp đồng", "Phải mua bổ sung giá thị trường cao hơn hoặc cắt kế hoạch 15–20%; chưa tính phạt hợp đồng xuất khẩu với đối tác EU. Không bảo hiểm, không đền bù, không bằng chứng"],
  ],
  { size: 18 }
));
push(src("Lãi suất vay vốn lưu động doanh nghiệp 2025–2026: 6,8–9%/năm (ACB); lãi suất cho vay bình quân 10/2025 ~6,55%/năm (NHNN)."));
push(H3("3.2.1 Mức cọc 5% là tham số khởi điểm, không phải kết quả tối ưu đã được chứng minh"));
push(P("Mức buyerDepositRate mặc định 5% chỉ là giả thuyết cấu hình cho MVP. Nó không thể tự bảo đảm thực hiện hợp đồng khi giá spot tăng 30%, 50% hoặc 100%; tác động hành vi phụ thuộc đồng thời vào penalty có thể thu, chi phí uy tín, thời gian khoá và giá trị quan hệ tương lai."));
push(P([runs("U_phá_hợp_đồng = Q × (P_thị_trường − P_hợp_đồng) − Penalty − C_uy_tín − C_khoá", { bold: true, color: T.SUB })], { align: AlignmentType.CENTER }));
push(P("Một bên có động lực phá hợp đồng khi lợi ích chênh lệch giá còn dương sau khi trừ toàn bộ chi phí. Vì vậy pilot phải chạy sensitivity analysis theo độ tăng giá, tỷ lệ cọc, xác suất penalty thực sự thu được, số hợp đồng tương lai bị mất và mức độ chia sẻ uy tín giữa các buyer. Kết quả dùng để hiệu chỉnh cấu hình; không hardcode 5% thành quy tắc tối ưu cho mọi ngành và mọi cặp đối tác."));
push(P([runs("Đòn bẩy vốn bổ sung: ", { bold: true }), runs("khi ngân hàng tích hợp trực tiếp, HTX có thể dùng hợp đồng forward đang active — với ký quỹ đã khoá từ phía bên mua — làm bằng chứng dòng tiền tương lai để đề nghị vay vốn lưu động. Hợp đồng và ledger giúp tăng chất lượng hồ sơ dòng tiền, nhưng không tự động trở thành tài sản bảo đảm hay cam kết cấp tín dụng; quyết định tín dụng vẫn thuộc ngân hàng.", {})]));

push(H2("3.3 Giao hàng từng phần và quyết toán pro-rata"));
push(P("Hao mòn trong vận chuyển nông sản là không thể tránh khỏi — giao 95 tấn thay vì 100 tấn không đương nhiên là vi phạm hợp đồng. Mỗi đợt giao hàng được xác định qua hai phép cân, tạo ra hai loại sai lệch cần xử lý khác nhau:"));
push(table(
  [1900, 4000, 3738],
  ["Loại sai lệch", "Định nghĩa", "Cách xử lý"],
  [
    ["Delta 2 — hao mòn vận chuyển", "Chênh lệch giữa số bên bán cân trước khi lên xe và số bên mua cân khi hạ hàng", "Luôn quyết toán pro-rata tự động theo số thực nhận. Trách nhiệm hao mòn chia theo tỷ lệ đã đàm phán (mặc định 50/50) nếu vượt ngưỡng tolerance"],
    ["Delta 1 — thiếu so với cam kết", "Chênh lệch giữa số cam kết giao của đợt và số bên bán thực cân", "Ba nhánh theo ngưỡng shortfallPenaltyThreshold (mặc định 5%) — xem bảng dưới"],
  ],
  { size: 18 }
));
push(table(
  [1200, 4600, 3838],
  ["Nhánh", "Điều kiện", "Kết quả"],
  [
    ["1", "Thiếu trong ngưỡng shortfallPenaltyThreshold", "Pro-rata bình thường, không penalty"],
    ["2", "Thiếu vượt ngưỡng, không chứng minh được bất khả kháng", "Áp sellerPenaltyRate — đúng bản chất phá vỡ hợp đồng"],
    ["3", "Thiếu (bất kỳ mức nào), chứng minh được bất khả kháng và được duyệt", "Pro-rata theo số thực giao, không penalty"],
  ],
  { size: 18, colAlign: [AlignmentType.CENTER, null, null] }
));
push(P("Quyết toán pro-rata tự động khi sai lệch nằm trong ngưỡng đã thoả thuận — không cần người vận hành can thiệp. Chỉ khi bên mua chủ động flag vấn đề (thiếu cân hoặc sai chất lượng) thì đợt giao hàng mới đi vào luồng phản hồi/tranh chấp."));

push(H2("3.4 Bất khả kháng"));
push(P("Bất khả kháng phải hội đủ ba điều kiện: khách quan, không thể lường trước, và không thể khắc phục dù đã nỗ lực hết mức. Mất mùa hay sâu bệnh thông thường không đạt ngưỡng này — chỉ thiên tai lớn (lũ, bão), dịch bệnh, hoặc lệnh cấm của nhà nước mới đủ điều kiện. Hệ quả pháp lý: các bên tự chịu thiệt hại của mình, không bên nào được đòi bồi thường — hợp đồng giảm xuống đúng số lượng thực giao."));
push(P("Thời điểm báo cáo neo theo lúc bên bán biết về sự kiện, không neo theo ngày giao hàng. Bên bán phải khai trong một cửa sổ thời gian ngắn (mặc định 3 ngày) kể từ lúc biết. Bằng chứng bắt buộc gồm xác nhận thiên tai của chính quyền địa phương, ảnh, tin tức — OPERATOR/ADMIN được ủy quyền review trước khi công nhận, không tự động miễn chỉ vì bên bán khai."));
push(P([runs("Quyền phản đối đối xứng hai chiều. ", { bold: true }), runs("Bên mua có quyền phản đối quyết định công nhận bất khả kháng; bên bán có quyền phản đối quyết định bác nếu cho rằng người vận hành đánh giá sai mức độ. Cả hai cùng được đẩy lên giám định cấp địa phương (Level 1.5) làm cấp cuối. Đối xứng này là cần thiết vì đội vận hành của deployment có thể gần phía bên mua hơn về cấu trúc — nếu chỉ bên mua được phản đối, một quyết định thiên vị có thể lặp lại mà không có lớp kiểm tra độc lập.", {})]));
push(legal("Bộ luật Dân sự 2015, Điều 156 và Điều 351", "Sự kiện bất khả kháng là sự kiện xảy ra khách quan, không thể lường trước và không thể khắc phục dù đã áp dụng mọi biện pháp cần thiết. Bên có nghĩa vụ không phải chịu trách nhiệm dân sự trong phạm vi sự kiện bất khả kháng gây ra."));

push(H2("3.5 Huỷ hợp đồng và penalty"));
push(P("Huỷ hợp đồng chỉ tác động các đợt chưa quyết toán; đợt đã xong giữ nguyên, không truy thu. Penalty tính trên tổng giá trị các đợt còn lại, không phải toàn bộ hợp đồng. Cơ chế enforce khác nhau giữa hai bên là có chủ đích — bên mua (thường có vốn) bị ràng buộc bằng cả tiền thật lẫn uy tín; bên bán (HTX) mặc định chỉ ràng buộc được bằng uy tín vì không bắt buộc có tiền cọc, nhưng có thể ràng buộc thêm bằng tiền thật nếu hai bên đàm phán một khoản cọc nhỏ của bên bán lúc ký."));
push(table(
  [2600, 4300, 2738],
  ["Tình huống", "Cơ chế xử lý", "Căn cứ pháp lý"],
  [
    ["Huỷ trước khi ký", "Tự do rút, không phát sinh nghĩa vụ tài chính", "BLDS 2015, Điều 403 — tự do giao kết"],
    ["Bên bán huỷ (phần chưa quyết toán)", "Nếu có cọc bên bán đã khoá: seize ngay, offset trực tiếp vào penalty debt. Ghi nhận phần penalty debt còn lại (sellerPenaltyRate × giá trị đợt còn lại, trừ đi cọc đã seize nếu có) vào audit trail làm bằng chứng bồi thường; khoá tài khoản ngay theo lockDurationDays; hoàn cọc buyerDepositRate về bên mua (độc lập với penalty debt/cọc bên bán)", "Luật TM 2005, Điều 302 — bồi thường thiệt hại"],
    ["Bên mua huỷ", "Mất toàn bộ buyerDepositRate (chuyển cho bên bán, ký quỹ tự động seize); nếu đợt hiện tại đang khoá thì seize thêm theo buyerPenaltyRate; nếu bên bán có cọc đã khoá, hoàn lại cho bên bán (bên bán không phải bên phá kèo)", "Luật TM 2005, Điều 300 — phạt vi phạm theo thoả thuận"],
    ["Tranh chấp số lượng/chất lượng của một đợt", "Đẩy qua giải quyết tranh chấp phân tầng (Mục 4); quyết toán theo phán quyết", "Nghị định 98/2018, Điều 15"],
  ],
  { size: 18 }
));
push(P([runs("Vì sao bên bán vẫn bị ràng buộc bằng uy tín ngay cả khi không có cọc. ", { bold: true }), runs("Khi bên bán huỷ phần còn lại mà không có cọc đã đàm phán, không có tiền khoá sẵn để ký quỹ tự động trừ — đây là cái giá thật của việc không bắt buộc cọc bên bán (đánh đổi lấy khả năng tham gia thực tế của HTX). Cơ chế thay thế là khoá tài khoản dựa trên uy tín: chi tiết ở Mục 5. Cọc bên bán không bắt buộc vì cùng lý do — platform để hai bên tự quyết định mức ràng buộc phù hợp với quan hệ cụ thể của họ, không áp một công thức chung cho mọi cặp buyer-seller.", {})]));

// ============================================================
// 4. INSPECTOR
// ============================================================
push(H2("3.6 Vì sao nông dân không bị khoá vốn lưu động"));
push(P([runs("Đây là câu hỏi sống còn với phía HTX/nông dân — và thiết kế đã trả lời sẵn bằng ba lựa chọn có chủ đích, nêu rõ ở đây thay vì để hội đồng phải tự suy ra: ", { bold: true })]));
push(bullet([runs("Bên khoá tiền là buyer, không phải nông dân. ", { bold: true }), runs("Nghĩa vụ ký quỹ giá trị hợp đồng (batchAmount) thuộc về bên mua — bên có vốn và có nghĩa vụ thanh toán. Với HTX, tiền của buyer nằm sẵn trong escrow chính là bảo đảm thanh toán mà phương thức truyền thống không có: giao hàng đạt chuẩn là tiền tự release, không còn cảnh giao xong đi đòi nợ.", {})]));
push(bullet([runs("Cọc phía seller là optional, mặc định 0. ", { bold: true }), runs("sellerDepositRate đàm phán per-contract, mặc định không thu — HTX thiếu vốn lưu động gần như không bị giam đồng nào để tham gia. Cặp đối tác muốn ràng buộc chặt hơn (giá trị lớn, lịch sử mỏng) tự thoả thuận mức cọc; nền tảng không áp đặt.", {})]));
push(bullet([runs("Khoá theo đợt, không khoá cả cục. ", { bold: true }), runs("Milestone escrow chia dòng tiền theo tiến độ giao hàng: mỗi đợt chỉ khoá phần giá trị của đợt đó và release ngay khi nghiệm thu — vòng quay vốn của cả hai bên ngắn hơn nhiều so với khoá toàn bộ giá trị hợp đồng từ ngày ký.", {})]));
push(P([runs("Lợi ích phái sinh — hồ sơ tín dụng: ", { bold: true }), runs("chuỗi hợp đồng đã thực hiện trên nền tảng (ký số, ledger đối soát được, lịch sử nghiệm thu) chính là loại bằng chứng mà phía ngân hàng cần để tự tin giải ngân cho liên kết chuỗi — đúng khuyến nghị lập hợp đồng chuỗi ràng buộc trách nhiệm đã dẫn ở tài liệu Phân tích thị trường. Nền tảng không xây sản phẩm tín dụng trong phạm vi đồ án; đây là giá trị dữ liệu mở ra cho HTX khi làm việc với tổ chức tín dụng.", {})]));

push(H1("4. Giám định độc lập — INSPECTOR ba cấp"));
push(P("Nhận định chủ quan của vận hành nội bộ không đủ tin cậy cho hợp đồng giá trị cao hoặc hàng hoá phức tạp; nhưng áp phí giám định quốc tế cho mọi hợp đồng lại không thực tế về kinh tế. Hệ thống giải quyết cả hai vế bằng phân tầng theo giá trị kết hợp định giá linh hoạt. Cấp giám định được xác định tự động theo giá trị hợp đồng, loại hàng hoá và yếu tố xuất khẩu EU — cấu hình theo từng deployment, không hardcode trong logic nghiệp vụ."));
push(table(
  [1900, 3400, 2400, 1938],
  ["Cấp", "Tổ chức", "Điều kiện kích hoạt (OR)", "Phí giám định"],
  [
    [["Level 1", "Vận hành nội bộ"], "OPERATOR hoặc ADMIN được ủy quyền", "Giá trị nhỏ VÀ hàng hoá thông thường", "Không có"],
    [["Level 1.5", "Giám định địa phương"], "Vinacontrol, Quatest, trung tâm kiểm định tỉnh được Nhà nước công nhận — là actor thật trên nền tảng, có tài khoản và ký báo cáo", "Giá trị trung bình HOẶC cần xác nhận khối lượng/chất lượng cơ bản", "inspectionFeeRate × contractValue (vd 0,1–0,3%)"],
    [["Level 2", "Giám định quốc tế"], "SGS, Bureau Veritas, Intertek — tổ chức quốc tế, report tự thân đủ uy tín; nền tảng chỉ tiếp nhận và bảo vệ tính toàn vẹn file", "Giá trị lớn HOẶC hàng hoá phức tạp (cà phê specialty, cao su kỹ thuật, điều xuất khẩu EU)", "inspectionFeeRate × contractValue (vd 0,2–0,5%)"],
  ],
  { size: 18 }
));
push(P([runs("Hai mô hình định danh khác nhau, không phải hai mức độ nghiêm trọng của cùng một thứ. ", { bold: true }), runs("Level 1.5 là tổ chức quy mô tỉnh, quan hệ hợp đồng dịch vụ trực tiếp với nền tảng khả thi — trở thành actor thật, có tài khoản đăng nhập, KYC xác minh chứng chỉ hành nghề kiểm định. Level 2 là tập đoàn quốc tế không tích hợp đăng nhập vào một nền tảng agritech Việt Nam — và không cần, vì uy tín report của họ tự thân đã đủ; nền tảng chỉ cần bảo vệ tính toàn vẹn của file sau khi nhận, qua hòm thư tiếp nhận tự động rồi OPERATOR/ADMIN được ủy quyền xác nhận.", {})]));
push(P("Cơ chế vận hành chung: INSPECTOR nộp report có hash xác thực, không sửa được sau khi submit. OPERATOR/ADMIN được ủy quyền thực thi giải ngân theo report, không được tự đổi kết luận; mọi override trái workflow bị đánh dấu vĩnh viễn trong audit trail. Cả hai bên deposit phí giám định vào ký quỹ trước khi INSPECTOR được assign; bên thua tranh chấp chịu toàn bộ, phán quyết 50/50 thì chia đôi."));
push(P([runs("Kiểm soát chọn tổ chức Level 2. ", { bold: true }), runs("Tổ chức Level 2 được đàm phán vào điều khoản lúc ký, nhưng không thả tự do: chỉ chấp nhận ba nhóm — tổ chức quốc tế lớn có danh sách cứng, tổ chức trong nước xác minh qua số chứng nhận công nhận (BoA-VIAS), và tổ chức lạ thì OPERATOR đề xuất, ADMIN duyệt từng trường hợp và không lưu vào danh sách dùng chung. Điều này chặn kịch bản hai bên thông đồng tự chọn một tổ chức dễ dãi.", {})]));
push(legal("Nghị định 98/2018/NĐ-CP, Điều 15", "Cho phép các bên lựa chọn phương thức phù hợp để giải quyết tranh chấp — thương lượng, hoà giải hoặc trọng tài. Hoà giải nội bộ do OPERATOR/ADMIN được ủy quyền hỗ trợ chỉ áp dụng khi hai bên đã chấp nhận quy trình này; nền tảng không thay thế cơ quan tài phán."));

// ============================================================
// 5. REPUTATION
// ============================================================
push(H1("5. Uy tín và cơ chế khoá tài khoản"));
push(P("Với hợp đồng không có cọc bên bán, uy tín và khoá tài khoản là cơ chế bổ sung để làm tăng chi phí phá vỡ hợp đồng. Sau mỗi hợp đồng hoàn thành, hai bên đánh giá lẫn nhau; lịch sử tích luỹ thành điểm uy tín công khai. Cơ chế này chỉ mạnh khi người dùng giao dịch lặp lại, mạng lưới có đủ buyer, khó tạo danh tính/pháp nhân thay thế và các tổ chức tham gia thực sự coi lịch sử nền tảng là tín hiệu có giá trị. Với hợp đồng có đàm phán cọc bên bán (Mục 3.5), uy tín áp dụng song song — cọc bù một phần rủi ro tài chính, còn uy tín phản ánh hành vi dài hạn."));
push(P("Uy tín không chỉ là điểm số hiển thị; nó gồm ba loại dữ liệu khác bản chất: một sổ khoá (lock ledger) bất biến phục vụ enforce khoá tài khoản, một điểm uy tín sống phục vụ xếp hạng tìm kiếm, và dữ liệu tham chiếu tín dụng xuất được cho bên thứ ba."));
push(H3("Khoá tài khoản khi phá vỡ hợp đồng"));
push(P("Khi một bên phá vỡ hợp đồng có penalty, tài khoản bị khoá ngay (chặn tạo listing/hợp đồng mới) — không đợi kết quả toà, vì tố tụng 1–3 năm mâu thuẫn với chính lý do sản phẩm tồn tại. Thời gian khoá tách bạch với thiệt hại tài chính (đã phản ánh riêng ở penalty debt) và chỉ đo mức độ hành vi:"));
push(P([runs("lockDurationDays = baseDays × repeatOffenseMultiplier × trackRecordMultiplier", { bold: true, color: T.SUB })], { align: AlignmentType.CENTER }));
push(bullet([runs("baseDays ", { bold: true }), runs("— mặc định 30 ngày.", {})]));
push(bullet([runs("repeatOffenseMultiplier ", { bold: true }), runs("— theo số lần từng phá vỡ hợp đồng trước đó (1x/2x/3x), tuyến tính theo số lần tuyệt đối.", {})]));
push(bullet([runs("trackRecordMultiplier ", { bold: true }), runs("— gated theo ngưỡng mẫu tối thiểu 5 hợp đồng: dưới ngưỡng dùng hệ số trung tính (không đẩy người mới xuống đáy chỉ vì ít dữ liệu); từ ngưỡng trở lên dùng tỷ lệ hợp đồng sạch thật để giảm nhẹ hoặc tăng nặng (0,7x nếu ≥90% sạch, 1,0x nếu 70–90%, 1,3x nếu dưới 70%).", {})]));
push(P("Toàn bộ tham số nằm trong cấu hình, chỉnh được sau khi có dữ liệu thật. Tài khoản mở khoá qua một trong ba đường (đường nào tới trước): bên kia tự báo đã giải quyết, bên vi phạm nộp kết quả ràng buộc (bản án/phán quyết VIAC/thoả thuận hoà giải) để OPERATOR đề xuất và ADMIN phê duyệt mở khoá sớm theo maker-checker, hoặc hết thời hạn khoá cố định. Penalty debt và lịch sử vi phạm không bao giờ bị xoá — mở khoá chỉ là cho giao dịch tiếp, không phải xoá tiền án."));
push(callout("Giới hạn hiệu lực của uy tín", "Khoá tài khoản chỉ enforce được trong phạm vi AgriContract. Nếu mạng lưới còn nhỏ, người vi phạm có thể quay lại giao dịch ngoài nền tảng hoặc chuyển sang pháp nhân khác; nếu dữ liệu không được nhiều buyer dùng trong quyết định đối tác, C_uy_tín gần bằng 0. Vì vậy tài liệu không coi reputation lock là bảo đảm thay thế cho cọc, pháp luật hoặc chế tài hiệp hội.", "warn"));
push(legal("Luật Thương mại 2005, Điều 302", "Penalty debt ghi vào audit trail bất biến có giá trị làm căn cứ bồi thường thiệt hại nếu bên bị vi phạm truy đòi qua VIAC hoặc toà án. Nền tảng không tự thu hộ được — đây là bằng chứng, không phải cơ chế cưỡng chế thu tiền."));

// ============================================================
// 6. AUDIT TRAIL / EVIDENCE
// ============================================================
push(H1("6. Bằng chứng và audit trail"));
push(P("Mỗi AuditRecord được định danh theo subjectType/subjectId (CONTRACT, USER_PAIR hoặc SYSTEM), nối đồng thời global chain và per-subject chain bằng prevHashGlobal/prevHashSubject. sourceHash là hash của artefact nguồn; recordHash là hash canonical của chính bản ghi. Bằng chứng OTS nằm ở bảng audit_anchor append-only riêng, không cập nhật ngược audit_record."));
push(P("Vì chữ ký trên nền tảng ở dạng chữ ký điện tử cơ bản (không có chứng thư từ tổ chức chứng thực được cấp phép), luật không tự động suy đoán “đúng người, đúng thời điểm”. Nền tảng phải tự chứng minh điều đó khi có tranh chấp — nên audit trail không phải lớp phụ, mà là lý do tồn tại của toàn bộ thiết kế bằng chứng. Bốn lớp bảo vệ:"));
push(bullet([runs("Hash nội dung hợp đồng. ", { bold: true }), runs("Toàn bộ điều khoản được băm (SHA-256) lúc ký; mọi thao tác sau đó verify hash trước khi thực hiện — sửa dữ liệu trong DB làm hash lệch, thao tác bị từ chối.", {})]));
push(bullet([runs("Chuỗi hash audit trail. ", { bold: true }), runs("Mỗi bản ghi chứa hash của chính nó và hash của bản ghi trước, tạo thành chuỗi append-only; tài khoản DB của dịch vụ audit chỉ có quyền INSERT + SELECT, không UPDATE/DELETE. Chuỗi được verify định kỳ và trước mỗi lần xuất báo cáo EUDR.", {})]));
push(bullet([runs("Lưu hash nhiều nơi + neo timestamp qua email. ", { bold: true }), runs("Hash được lưu độc lập ở nhiều nơi (DB hợp đồng, DB audit) và gửi email cho cả hai bên sau mỗi lần ký/nộp report — email là điểm neo bên ngoài nền tảng; dù toàn bộ DB bị xâm phạm, hai bên vẫn giữ bằng chứng hash trong hộp thư.", {})]));
push(bullet([runs("Neo bằng chứng lên Bitcoin (OpenTimestamps). ", { bold: true }), runs("Hash cam kết toàn cục được neo lên Bitcoin qua OpenTimestamps (miễn phí, không cần ví crypto). Bằng chứng tồn tại độc lập ngay cả khi nền tảng sập hoàn toàn, và phát hiện được kiểu tấn công xoá-rồi-viết-lại-toàn-chuỗi.", {})]));
push(P("Toàn bộ lịch sử liên quan xuất được dưới dạng PDF/CSV theo yêu cầu như một gói bằng chứng hỗ trợ DDS; nền tảng không tuyên bố tự mình tạo ra một DDS hoàn chỉnh thay cho nghĩa vụ due diligence của operator EU. Operator hoặc đại diện được uỷ quyền vẫn phải thực hiện due diligence và nộp Due Diligence Statement/Simplified Declaration vào EUDR Information System của EU."));
push(table(
  [1900, 3500, 4238],
  ["Thành phần", "Chứng minh được", "Không tự chứng minh được"],
  [
    ["Hash file", "File tại thời điểm kiểm tra không khác artefact đã băm", "Nội dung file phản ánh đúng sự thật ngoài đời"],
    ["OTP/email challenge", "Tài khoản/email nhận challenge đã hoàn tất thao tác tại thời điểm ghi nhận", "Người thao tác chắc chắn có thẩm quyền đại diện pháp lý"],
    ["Timestamp/audit chain", "Dữ liệu tồn tại và chuỗi record không bị thay đổi mà không để lại dấu vết", "Giao dịch không gian lận hoặc điều khoản đương nhiên hợp pháp"],
    ["Inspection report", "Ý kiến chuyên môn và artefact của tổ chức/giám định viên đã nộp", "Phán quyết pháp lý cuối cùng hoặc hàng hoá tuyệt đối đúng khai báo"],
  ],
  { size: 17 }
));
push(src("European Commission — EUDR Information System; Understand Due Diligence (truy cập 7/2026)."));
push(P([runs("Vì sao chọn hash thay vì blockchain đầy đủ. ", { bold: true }), runs("Nền tảng có trusted operator (hiệp hội/doanh nghiệp triển khai) — bài toán đồng thuận không tin cậy (trustless consensus) mà blockchain sinh ra để giải không tồn tại ở đây. Ba lớp hash + neo email + neo Bitcoin phủ được các attack vector tương đương với chi phí thực hiện thấp hơn nhiều, phù hợp ràng buộc thời gian và nhân lực thật của dự án.", {})]));

// ============================================================
// 7. LEGAL & RISK
// ============================================================
push(H1("7. Khung pháp lý và quản trị rủi ro"));
push(H2("7.1 Bốn văn bản pháp luật nền tảng"));
push(table(
  [3000, 3100, 3538],
  ["Văn bản", "Điều khoản liên quan", "Áp dụng với AgriContract"],
  [
    ["Luật Giao dịch Điện tử 2023 (20/2023/QH15)", "Điều 8 — không phủ nhận chỉ vì ở dạng thông điệp dữ liệu; Điều 14 — giá trị chứng cứ; Điều 34–36 — hợp đồng điện tử và nghĩa vụ tuân thủ pháp luật hợp đồng", "Hợp đồng không tự động có hiệu lực chỉ vì ở dạng điện tử; audit trail hỗ trợ chứng minh quá trình tạo lập, lưu trữ và bảo toàn, còn giá trị chứng cứ do cơ quan có thẩm quyền đánh giá"],
    ["Nghị định 98/2018/NĐ-CP", "Điều 4 — hợp đồng liên kết lập thành văn bản; Điều 15 — lựa chọn phương thức giải quyết tranh chấp", "Văn bản điện tử đáp ứng Điều 4; hoà giải nội bộ hợp lệ theo Điều 15, không thay thế toà án"],
    ["Luật Trọng tài Thương mại 2010 (54/2010/QH12)", "Điều 5 — thoả thuận trọng tài trước/sau tranh chấp; phán quyết chung thẩm", "Hoà giải nội bộ hợp lệ; nếu leo thang, VIAC xử lý dựa trên audit trail nền tảng làm bằng chứng"],
    ["Bộ luật Dân sự 2015 & Luật Thương mại 2005", "BLDS Điều 328 (đặt cọc giữa các bên), Điều 330 (ký quỹ tại tài khoản phong toả của tổ chức tín dụng), Điều 142 (đại diện), Điều 156 & 351 (bất khả kháng); LTM Điều 300 (phạt), Điều 302 (bồi thường)", "buyerDepositRate/sellerDepositRate là tiền cọc theo thoả thuận; khoản tiền do ngân hàng giữ trong tài khoản phong toả được đối chiếu với cơ chế ký quỹ; nền tảng chỉ thực thi workflow đã ký, không ra phán quyết"],
  ],
  { size: 18 }
));
push(P([runs("Chính sách đang dịch chuyển đúng hướng AgriContract — vừa xác nhận thị trường, vừa cần phân định ranh giới. ", { bold: true }), runs("Bộ Nông nghiệp & Môi trường đang xây dựng Nghị định thay thế Nghị định 98/2018 (dự kiến trình Chính phủ 2026). Theo đánh giá của chuyên gia PSAV, dự thảo lần đầu bổ sung tương đối đầy đủ các công cụ bảo đảm thực hiện hợp đồng — bảo lãnh, ký quỹ (escrow), bảo hiểm và cơ chế chia sẻ rủi ro — cùng một cơ sở dữ liệu về tình hình thực hiện hợp đồng để tổ chức tín dụng đánh giá khả năng cho vay theo dòng tiền. Đây chính xác là ba trụ mà AgriContract hiện thực hoá: ký quỹ tự thực thi, dữ liệu thực hiện hợp đồng, và tín dụng theo dòng tiền.", {})]));
push(legal("Dự thảo Nghị định thay thế Nghị định 98/2018/NĐ-CP (trình Chính phủ 2026)", "Lần đầu đưa ký quỹ, bảo lãnh, bảo hiểm và cơ sở dữ liệu thực hiện hợp đồng vào chính sách liên kết chuỗi giá trị nông nghiệp; bổ sung khái niệm “chủ thể trung tâm chuỗi giá trị” để khắc phục bất cập của Nghị định 98 khi trách nhiệm các bên khó xác định lúc đứt gãy hợp đồng."));
push(P([runs("Ranh giới cần nói rõ trước hội đồng: ", { bold: true }), runs("Nghị định quy định chính sách — khuyến khích, khung, hỗ trợ — chứ không xây phần mềm thực thi. Nghị định nói “cần có ký quỹ”; AgriContract là công cụ thực hiện ký quỹ đó, tự động theo milestone, tích hợp giám định, có tầng tranh chấp. Tương tự luật quy định “cần hoá đơn điện tử” nhưng vẫn cần công ty làm phần mềm hoá đơn — chính sách và công cụ thực thi là hai lớp khác nhau, và đó là vị trí AgriContract chiếm.", {})]));
push(src("Chuyên gia PSAV (Đối tác phát triển nông nghiệp bền vững Việt Nam) đánh giá dự thảo Nghị định thay thế NĐ 98/2018; Báo Công Thương, Nông nghiệp Môi trường — sửa đổi Nghị định 98 (2026)."));

push(H2("7.2 Năm rủi ro pháp lý và biện pháp kiểm soát"));
push(P("Phần này trình bày các rủi ro pháp lý thực chất, không chỉ những điểm thuận lợi. Nhận diện rủi ro đầy đủ là cơ sở để thiết kế hệ thống đúng ngay từ đầu."));
push(risk("Rủi ro 1 — Giữ tiền không có giấy phép NHNN (nghiêm trọng nhất):", [runs("Nghị định 52/2024 Điều 8 Khoản 7 nghiêm cấm cung ứng dịch vụ trung gian thanh toán khi chưa được cấp phép (phạt 100–200 triệu đồng theo Nghị định 88/2019, có thể truy cứu hình sự). ", {}), runs("Kiểm soát: ", { bold: true }), runs("ngân hàng có giấy phép giữ tiền thật; nền tảng chỉ gửi lệnh và nhận phản hồi, không giữ tiền — không thuộc phạm vi phải xin giấy phép trung gian thanh toán.", {})]));
push(risk("Rủi ro 2 — Phán xử tranh chấp đơn phương khi chưa có đồng thuận:", [runs("Nghị định 52/2013 về thương mại điện tử quy định thương nhân sở hữu sàn không được đơn phương can thiệp tranh chấp khi chưa có đồng ý của khách hàng. ", {}), runs("Kiểm soát: ", { bold: true }), runs("Điều khoản dịch vụ bắt buộc hai bên đồng ý cơ chế hoà giải nội bộ trước khi kích hoạt tài khoản — consent trước bằng văn bản điện tử hợp lệ theo Luật GDĐT 2023.", {})]));
push(risk("Rủi ro 3 — Hợp đồng vô hiệu do người ký không có thẩm quyền:", [runs("BLDS 2015 Điều 142: giao dịch do người không có thẩm quyền đại diện xác lập có thể bị tuyên vô hiệu. ", {}), runs("Kiểm soát: ", { bold: true }), runs("quy trình xác minh bắt buộc trước khi kích hoạt tài khoản — kiểm tra đăng ký kinh doanh và xác nhận thẩm quyền ký kết của người đại diện; không pass xác minh, không được đăng listing. Áp dụng đối xứng cho cả bên mua và bên bán.", {})]));
push(risk("Rủi ro 4 — Nền tảng bị lợi dụng tạo giao dịch khống:", [runs("Nghị định 52/2024 yêu cầu có biện pháp phòng chống giao dịch thanh toán khống. Nếu hai bên thông đồng tạo hợp đồng giả, nền tảng có thể bị coi là đồng lõa. ", {}), runs("Kiểm soát: ", { bold: true }), runs("KYC xác minh pháp nhân cả hai phía; INSPECTOR xác nhận hàng hoá tồn tại thực tế tại điểm giao nhận (report có hash không giả mạo được); audit trail đầy đủ là bằng chứng due diligence nếu bị điều tra.", {})]));
push(risk("Rủi ro 5 — Rò rỉ thông tin giao dịch và số dư:", [runs("Nghị định 52/2024 Điều 8 Khoản 4 nghiêm cấm tiết lộ thông tin số dư và giao dịch của khách hàng. ", {}), runs("Kiểm soát: ", { bold: true }), runs("kiểm soát truy cập theo vai trò (Keycloak RBAC) — mỗi người dùng chỉ truy cập dữ liệu trong phạm vi vai trò; mTLS cho giao tiếp nội bộ giữa các dịch vụ; audit trail bất biến cung cấp evidence nếu có điều tra.", {})]));

push(H2("7.3 Án lệ tham chiếu"));
push(P("Bản án 15/2021/KDTM-ST (25/5/2021, Toà án quận Nam Từ Liêm, Hà Nội): một công ty cung cấp cổng thanh toán bị Mastercard áp phạt 55.482,5 USD vì merchant sử dụng cổng có hành vi vi phạm sở hữu trí tuệ; toà tuyên merchant phải bồi thường toàn bộ khoản phạt cho bên cung cấp cổng."));
push(callout("Bài học:", "Nền tảng trung gian có thể chịu trách nhiệm tài chính do hành vi của merchant sử dụng nền tảng, dù không trực tiếp tham gia hành vi vi phạm. KYC nghiêm ngặt + INSPECTOR xác nhận hàng hoá thực tế + audit trail đầy đủ là ba lớp bảo vệ chứng minh nền tảng đã thực hiện due diligence hợp lý.", "note"));

// ============================================================
// 8. VALUE LAYERS
// ============================================================
push(H1("8. Ba lớp giá trị"));
push(P("Giá trị của nền tảng được xây dựng thành ba lớp chồng lên nhau — mỗi lớp giải quyết một nhóm nhu cầu và phục vụ một nhóm hưởng lợi khác nhau. Doanh nghiệp có thể tiếp cận nền tảng vì yêu cầu EUDR, tiếp tục sử dụng nếu giảm được rủi ro vốn và tranh chấp, và chịu chi phí chuyển đổi tăng dần khi lịch sử giao dịch trên nền tảng trở thành tài sản vận hành có giá trị."));
push(table(
  [1900, 4900, 2838],
  ["Lớp", "Giá trị cụ thể", "Ai hưởng lợi"],
  [
    [["Lớp 1", "Hỗ trợ EUDR"], "Audit trail, geolocation snapshot và hồ sơ nguồn cung tạo gói traceability hỗ trợ due diligence; xuất báo cáo on-demand thay vì tổng hợp thủ công. Hệ thống không tự chứng minh deforestation-free", "Doanh nghiệp xuất khẩu cà phê, cao su, gỗ vào EU — 30/12/2026 với doanh nghiệp lớn/vừa; 30/6/2027 với phần lớn doanh nghiệp nhỏ/siêu nhỏ"],
    [["Lớp 2", "Kiểm soát rủi ro"], "Ký quỹ + Milestone làm tăng chi phí phá vỡ hợp đồng; mức đủ mạnh phải kiểm chứng bằng sensitivity/pilot. Giám định phân tầng rút ngắn thu thập bằng chứng. Uy tín là tín hiệu bổ sung khi mạng lưới đủ dày", "Cả bên mua lẫn bên bán — đặc biệt HTX không có nguồn lực pháp lý tự bảo vệ"],
    [["Lớp 3", "Hạ tầng tín dụng"], "Audit trail tích luỹ tạo dữ liệu đầu vào cho đánh giá tín dụng: lịch sử hoàn thành, nghiệm thu và dòng tiền. Ngân hàng quyết định cách sử dụng dữ liệu; nền tảng không tự biến lịch sử này thành hạn mức vay", "HTX nhỏ + ngân hàng (vai trò data oracle, không chỉ cấp tín dụng truyền thống)"],
  ],
  { size: 18 }
));

push(H2("8.1 Khung ROI và willingness-to-pay cần kiểm chứng"));
push(P("Business case không nên dừng ở câu “chi phí ký quỹ nhỏ hơn rủi ro được bảo hiểm”. Mỗi khách hàng mục tiêu cần một baseline đo được trước pilot và một kết quả sau pilot."));
push(P([runs("ROI = (thiệt hại tránh được + chi phí tranh chấp tiết kiệm + lợi ích compliance − phí hệ thống − chi phí vốn ký quỹ) / (phí hệ thống + chi phí vốn ký quỹ)", { bold: true, color: T.SUB })], { align: AlignmentType.CENTER }));
push(table(
  [2100, 3500, 4038],
  ["Giả thuyết thương mại", "Cách kiểm chứng", "Tiêu chí không được khẳng định trước"],
  [
    ["Ai trả tiền", "Phỏng vấn tách hiệp hội, doanh nghiệp xuất khẩu, buyer lớn và ngân hàng", "Không mặc định hiệp hội là payer chỉ vì có trust"],
    ["Mô hình giá", "Test subscription năm, per-user và transaction fee với cùng một ICP", "Không chốt pricing từ benchmark ngành khác"],
    ["Khoảng giá", "Dùng price-sensitivity với các band 30/60/120/240 triệu đồng/năm; test thêm 0,02–0,1% GMV như câu hỏi khảo sát", "Các band là công cụ hỏi, không phải bảng giá AgriContract"],
    ["ROI", "Đo số vụ vi phạm, giá trị mua bù, ngày xử lý tranh chấp, giờ tổng hợp compliance và chi phí vốn", "Không dùng kim ngạch xuất khẩu làm proxy cho giá trị phần mềm"],
  ],
  { size: 17 }
));
push(callout("Gate thương mại", "Chỉ chuyển từ giả thuyết sang claim sau khi có ít nhất một anchor buyer cung cấp baseline thật, chấp nhận pilot có KPI và xác nhận mức phí hoặc cơ chế mua sắm. Letter of interest hữu ích, nhưng không tương đương hợp đồng trả tiền.", "note"));

// ============================================================
// 9. RELATED WORK
// ============================================================
push(H1("9. Tổng quan giải pháp quốc tế và điểm khác biệt"));
push(P("Bài toán side-selling, hợp đồng quan hệ và điều phối qua hợp tác xã đã được nghiên cứu quốc tế; literature hỗ trợ cơ chế incentive nhưng cũng phản biện quan điểm rằng chỉ cần penalty hoặc công nghệ là đủ."));
push(bullet([runs("Alemu, Guinan & Hermanson (2021): ", { bold: true }), runs("side-selling trong chuỗi malt barley được ước tính khoảng 30%; khuyến nghị phải xử lý đồng thời vấn đề cấp nông hộ, thị trường và năng lực hợp tác xã, kết hợp incentive với disincentive.", {})]));
push(bullet([runs("Ewusi Koomson et al. (2022): ", { bold: true }), runs("trên mẫu 370 hộ cao su tại Ghana, 20% nông hộ contract farming có side-selling; chậm trễ từ contractor và điều kiện quan hệ là biến quan trọng — rất sát ngành cao su trong phạm vi AgriContract.", {})]));
push(bullet([runs("Macchiavello (2022): ", { bold: true }), runs("relational contracts được duy trì bởi giá trị tương tác tương lai; reputation chỉ có hiệu lực khi quan hệ lặp lại và lợi ích tương lai đủ lớn.", {})]));
push(bullet([runs("Abreham et al. (2025): ", { bold: true }), runs("systematic review/meta-regression nhấn mạnh kết quả contract farming phụ thuộc tiếp cận thị trường và bối cảnh thể chế — không nên suy luận rằng số hoá hợp đồng tự động tạo tác động tích cực.", {})]));
push(src("DOI 10.1080/09614524.2020.1860194; 10.1080/14728028.2022.2079007; 10.1146/annurev-economics-051420-110722; 10.1080/23311932.2025.2551263."));
push(P([runs("Điểm khác biệt của AgriContract so với cả literature quốc tế lẫn giải pháp Việt Nam hiện có. ", { bold: true }), runs("Escrow đã được nhà nước Việt Nam công nhận nhưng chỉ cho B2C bán lẻ (chống bom hàng, COD); escrow B2B hiện tại chỉ là tài khoản ký quỹ ngân hàng thủ công cho M&A/giải ngân — không có nền tảng số hoá tự thực thi theo milestone, không tích hợp giám định, không có state machine tranh chấp. Trong các nguồn đã rà soát, chưa thấy một mô hình duy nhất tích hợp đủ ba lớp AgriContract đang thiết kế: (i) tầng giám định ba cấp cho tranh chấp chất lượng nông sản thật, không chỉ nhị phân giao/không-giao; (ii) mô hình pháp lý Việt Nam thật — không tự giữ tiền, tách qua bank-service theo Nghị định 52/2024; (iii) neo vào hạ tầng dữ liệu đang hình thành trong nước. AgriContract do đó không phải “một giải pháp blockchain nông nghiệp thứ N”, mà là bản địa hoá cộng với tầng tranh chấp và khớp khung pháp lý Việt Nam cho forward contract B2B.", {})]));
push(src("Bộ Công Thương (Cục TMĐT) — hệ thống đảm bảo giao dịch escrow B2C; BIDV — dịch vụ tài khoản Escrow; CeCA — ứng dụng giao dịch an toàn kết hợp hợp đồng điện tử (2025-2026)."));

// ============================================================
// 10. FAQ
// ============================================================
push(H1("10. Chiến lược vào thị trường — đi qua anchor buyer"));
push(P([runs("Nền tảng hai đầu chợ không launch bằng cách kéo cả hai đầu cùng lúc. ", { bold: true }), runs("Chiến lược là đi qua một anchor buyer: doanh nghiệp xuất khẩu cà phê sang thị trường EU. Nhóm khách này chịu áp lực EUDR từ 30/12/2026 với doanh nghiệp lớn/vừa và từ 30/6/2027 với phần lớn doanh nghiệp nhỏ/siêu nhỏ (Quy định (EU) 2023/1115, sửa đổi 2025/2650), nên là ICP hợp lý để kiểm chứng nhu cầu chuẩn hoá dữ liệu vùng trồng và hợp đồng thu mua. Tuy nhiên “có áp lực pháp lý” không tự động đồng nghĩa “có ngân sách” hoặc “sẵn sàng mua AgriContract”; hai giả thuyết đó phải được xác nhận trong discovery. Một anchor buyer có thể kéo theo một phần mạng lưới HTX cung ứng sẵn có, giúp giảm bài toán khởi động hai đầu; tỷ lệ supplier thực sự onboard vẫn là KPI cần đo trong pilot.", {})]));
push(P([runs("Định vị theo đó cũng đổi: ", { bold: true }), runs("giai đoạn đầu, AgriContract không chào hàng như một chợ mở cho mọi người, mà như công cụ hợp đồng + compliance cho chuỗi cung ứng của anchor — nơi giá trị (EUDR traceability, escrow bảo đảm thanh toán, hồ sơ nghiệm thu) đến được cả hai phía của những cặp giao dịch vốn đã quen mặt nhau. Chợ mở là bước mở rộng khi mật độ người dùng và dữ liệu uy tín đã đủ dày.", {})]));
push(P([runs("Về adverse selection — tác nhân có ý định phá hợp đồng có thể né nền tảng ràng buộc mình. ", { bold: true }), runs("Chiến lược giai đoạn đầu không phụ thuộc vào việc chuyển đổi toàn bộ nhóm rủi ro; AgriContract dùng cơ chế tín hiệu để phục vụ trước các bên muốn chứng minh lịch sử giao dịch sạch. Chỉ khi mạng lưới đủ dày và nhiều buyer thực sự dùng lịch sử này trong lựa chọn đối tác, việc không có hồ sơ mới có thể trở thành một tín hiệu bất lợi — đây là giả thuyết cần đo, không phải hiệu ứng mạng đã tồn tại.", {})]));

push(H1("11. Câu hỏi thường gặp từ hội đồng thẩm định"));
const faq = [
  ["Ký quỹ dùng mock balance — có giá trị kỹ thuật gì?", "Giá trị kỹ thuật nằm ở logic, không phải ở tiền: Choreography Saga qua message queue, idempotency cho compensating transaction, Outbox Pattern đảm bảo at-least-once delivery. Các thách thức này đều thật và không thay đổi khi thay lớp giữ tiền mock bằng API ngân hàng thật — ranh giới interface được thiết kế sạch để việc thay thế không đụng business logic."],
  ["Nền tảng có đang thay thế chức năng của toà án không?", "Không. OPERATOR/ADMIN được ủy quyền chỉ thực thi workflow và điều khoản penalty mà hai bên đã tự thoả thuận, ký trước; Luật TM 2005 Điều 300 cho phép phạt vi phạm theo thoả thuận và Nghị định 98/2018 Điều 15 cho phép các bên lựa chọn phương thức giải quyết tranh chấp. Nền tảng không ra phán quyết cuối cùng."],
  ["Vì sao HTX sẽ tin đặt tiền vào ký quỹ của một nền tảng mới?", "HTX không phải tin nền tảng về việc giữ tiền vì tiền do ngân hàng giữ; trust triển khai có thể được mượn từ hiệp hội hoặc anchor buyer. Đây là giả thuyết go-to-market cần phỏng vấn xác nhận: tên hiệp hội không tự động tạo adoption nếu quy chế, hỗ trợ vận hành và lợi ích cho HTX chưa rõ."],
  ["Vì sao chưa có ai làm điều này trước đây?", "Koina là một case tham chiếu về rủi ro mô hình farm-to-business full-stack; các nguyên nhân đóng cửa công khai chưa đủ để kết luận duy nhất là sai phân khúc hoặc unit economics. AgriContract dùng case này như cảnh báo cần giữ phạm vi contract layer và kiểm chứng payer, không như bằng chứng chắc chắn cho chiến lược của mình. AgriContract khác ở việc giữ phạm vi contract layer, thử kênh hiệp hội/anchor buyer và đi vào thời điểm EUDR làm nhu cầu traceability cấp thiết hơn; cả ba vẫn phải được kiểm chứng bằng pilot và willingness-to-pay."],
  ["INSPECTOR có thể bị mua chuộc không?", "Report có hash xác thực, không sửa được sau khi submit. SGS/Bureau Veritas là tổ chức chứng nhận quốc tế chịu trách nhiệm pháp nhân theo giấy phép hành nghề; tổ chức trong nước được xác minh qua số chứng nhận công nhận quốc gia. Đây là cấp bảo đảm cao hơn nhiều so với vận hành nội bộ không có chuyên môn độc lập."],
  ["Chợ hai đầu — làm sao có người dùng đầu tiên khi chưa ai ở đầu kia?", "Không launch hai đầu cùng lúc — thử pilot qua một anchor buyer có mạng lưới HTX cung ứng sẵn có. Cách này giảm số phía phải bán đồng thời, nhưng không bảo đảm supplier tự động onboard; pilot phải đo tỷ lệ mời–kích hoạt–ký hợp đồng và nguyên nhân từ chối."],
  ["Bên định phá hợp đồng đâu có tự nguyện vào nền tảng ràng buộc mình?", "Đúng — và nền tảng không cần kéo họ vào. Giá trị nằm ở phía ngược lại: người làm ăn nghiêm túc dùng nền tảng để tách mình khỏi nhóm rủi ro bằng hồ sơ giao dịch đắt giả (ký số, ledger, nghiệm thu độc lập — không làm giả được bằng vài giao dịch ảo). Khi mật độ đủ dày, không có lịch sử trên nền tảng tự nó thành dấu hỏi trong đàm phán — cơ chế tự chọn lọc chuẩn của thị trường có tín hiệu (mục 10)."],
  ["Ký quỹ dùng mock balance — có vi phạm Nghị định 52/2024 không?", "Không. Nghị định 52/2024 điều chỉnh dịch vụ thanh toán liên quan đến tiền thật. Mock balance trong database không phát sinh giao dịch tài chính thật, không cần giấy phép trung gian thanh toán. Khi tích hợp ngân hàng có license giữ tiền thật, chính ngân hàng là bên chịu điều chỉnh — nền tảng vẫn chỉ ra lệnh."],
];
faq.forEach(([q, a]) => { push(P([runs(q, { bold: true, color: T.HEAD })], { after: 40 })); push(P(a, { after: 160 })); });

// ============================================================
// 10. LIMITATIONS
// ============================================================
push(H1("12. Giới hạn và phạm vi ngoài"));
push(P("Các điểm dưới đây là ranh giới có chủ đích của thiết kế, không phải điểm mù bị bỏ sót — nêu rõ để giữ độ tin cậy trước hội đồng."));
push(bullet([runs("Ngân hàng giữ tiền là mock trong phạm vi đồ án. ", { bold: true }), runs("Không tổ chức tín dụng nào ký hợp đồng API cho một đồ án tốt nghiệp. Lớp giữ tiền được mô phỏng theo đúng ngữ nghĩa nghiệp vụ (mô hình ledger gộp), interface thiết kế sạch để tích hợp thật chỉ cần thay implementation — nhưng tích hợp thật nằm ngoài phạm vi.", {})]));
push(bullet([runs("Chữ ký ở tầng cơ bản, không tương đương chữ ký tay. ", { bold: true }), runs("Chữ ký điện tử của nền tảng không có chứng thư từ CA được cấp phép nên không được luật tự động suy đoán; hợp đồng vẫn có hiệu lực đầy đủ, nhưng gánh nặng chứng minh khi tranh chấp thuộc về nền tảng (đã bù bằng audit trail). Chữ ký số CA và WebAuthn là hướng nâng cấp, chưa triển khai.", {})]));
push(bullet([runs("Chống thông đồng toàn diện là giới hạn cố hữu. ", { bold: true }), runs("Nếu Admin và toàn bộ (hoặc đa số) bên nhận cảnh báo phía Software Buyer cùng thông đồng, không cơ chế phần mềm nào trong kiến trúc hiện tại chặn được — đây chính là bài toán trustless consensus mà nhóm chủ đích không theo hướng blockchain vì ràng buộc thời gian/nhân lực. Neo email và neo Bitcoin vẫn đảm bảo bằng chứng tồn tại độc lập cho bất kỳ bên thứ ba nào sau này kiểm tra.", {})]));
push(bullet([runs("Xác minh nội dung file là best-effort. ", { bold: true }), runs("Nền tảng lưu và bảo vệ tính toàn vẹn file (trích lục địa chính, KML, report), nhưng không đối chiếu nội dung file với dữ liệu đã khai — khai đất hợp lệ hình học nhưng sai sự thật vẫn có thể lọt; cross-check tỉnh chỉ giảm nhẹ lỗi convert toạ độ vô ý, không chặn fraud có chủ đích.", {})]));
push(bullet([runs("Đòn bẩy uy tín phụ thuộc giả định thể chế chưa xác nhận. ", { bold: true }), runs("Cơ chế khoá tài khoản có hiệu lực trong phạm vi nền tảng; việc gắn dữ liệu vi phạm vào hệ quả tư cách hội viên hiệp hội (quota, đoàn xúc tiến…) cần hiệp hội ban hành quy chế mới — nằm ngoài khả năng đồ án tự quyết định.", {})]));
push(bullet([runs("Mức cọc, pricing và payer chưa được chứng minh bằng dữ liệu sơ cấp. ", { bold: true }), runs("buyerDepositRate 5%, mô hình license/subscription và giả thuyết hiệp hội/anchor buyer trả phí là các tham số để pilot; chưa có cơ sở tuyên bố tối ưu hoặc willingness-to-pay thực tế.", {})]));
push(bullet([runs("Tác động giảm bẻ kèo chưa có causal evidence tại Việt Nam. ", { bold: true }), runs("Nghiên cứu quốc tế xác nhận side-selling và vai trò incentive, nhưng AgriContract chưa có thử nghiệm đối chứng hoặc dữ liệu trước–sau để khẳng định ký quỹ làm giảm bao nhiêu phần trăm vi phạm.", {})]));

// ============================================================
// 11. SOURCES
// ============================================================
push(H1("13. Danh mục nguồn tham khảo"));
push(H3("Văn bản pháp luật"));
[
  "Luật Giao dịch Điện tử 2023 (Luật 20/2023/QH15, hiệu lực 1/7/2024).",
  "Nghị định 52/2024/NĐ-CP — Thanh toán không dùng tiền mặt; Nghị định 52/2013/NĐ-CP — Thương mại điện tử.",
  "Nghị định 98/2018/NĐ-CP — Liên kết sản xuất và tiêu thụ nông sản.",
  "Luật Trọng tài Thương mại 2010 (Luật 54/2010/QH12).",
  "Bộ luật Dân sự 2015 — Điều 328, 330, 142, 403, 156, 351.",
  "Luật Thương mại 2005 — Điều 300, 302.",
  "Nghị định 88/2019/NĐ-CP — Xử phạt vi phạm hành chính lĩnh vực tiền tệ.",
].forEach(s => push(bullet(s)));
push(H3("Án lệ & tổ chức pháp lý"));
[
  "Bản án 15/2021/KDTM-ST — Toà án quận Nam Từ Liêm, Hà Nội.",
  "VIAC — Thống kê tranh chấp 2024 (475 vụ, kỷ lục).",
].forEach(s => push(bullet(s)));
push(H3("Thị trường & nghiên cứu"));
[
  "VTV.vn — Giá cà phê tăng, làn sóng phá vỡ hợp đồng (17/4/2024).",
  "Báo Pháp Luật TP.HCM — Nghịch lý xuất khẩu cà phê Việt (4/4/2026).",
  "Vietnam.vn — Banks partner with the digital agricultural supply chain (1/4/2026).",
  "Agribank — Chương trình tín dụng chuỗi giá trị nông nghiệp 2024.",
  "VCCI — Báo cáo Kinh tế tư nhân 2025 (75,5% doanh nghiệp cần tài sản thế chấp để tiếp cận tín dụng).",
  "EC Regulation (EU) 2025/2650 — EUDR timeline (23/12/2025).",
  "European Commission — EUDR Information System; Understand Due Diligence (truy cập 7/2026).",
  "Alemu, D., Guinan, A. & Hermanson, J. (2021), DOI 10.1080/09614524.2020.1860194.",
  "Macchiavello, R. (2022), DOI 10.1146/annurev-economics-051420-110722.",
  "Ewusi Koomson, J. et al. (2022), DOI 10.1080/14728028.2022.2079007.",
  "Tefera, D. A. & Bijman, J. (2021), DOI 10.1186/s40100-021-00198-0.",
  "Abreham, G. et al. (2025), DOI 10.1080/23311932.2025.2551263.",
].forEach(s => push(bullet(s)));

push(endMark());

const doc = buildDoc(body, {
  title: "AgriContract — Giải Pháp, Người Dùng & Mô Hình Kinh Doanh v5.0",
  headerText: "AgriContract · Giải pháp & Mô hình",
  footerText: "v5.0 · Tháng 7/2026",
});
Packer.toBuffer(doc).then(buf => { writeDocx("/tmp/AgriContract_02_GiaiPhap_MoHinh_v5.docx", buf); });
