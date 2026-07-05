const fs = require("fs");
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
push(P("AgriContract số hoá vòng đời hợp đồng mua bán nông sản B2B — từ đàm phán điều khoản, ký kết điện tử, giữ tiền ký quỹ theo từng đợt giao hàng, đến giải ngân sau khi xác nhận giao nhận. Toàn bộ thao tác ghi vào audit trail bất biến, có giá trị làm bằng chứng pháp lý và xuất được cho kiểm toán EUDR."));
push(P("Điểm khác biệt cốt lõi của mô hình là cơ chế tự thực thi được thiết kế bám sát ba đặc thù của ngành. Thứ nhất, tiền ký quỹ được khoá theo từng đợt giao hàng (milestone) thay vì khoá toàn bộ giá trị hợp đồng ngay từ đầu — giải quyết áp lực vốn lưu động ở quy mô thương mại. Thứ hai, bên bán (HTX) không phải nộp tiền cọc; ràng buộc với bên bán đến từ uy tín tích luỹ và cơ chế khoá tài khoản, phù hợp thực tế HTX không có tiền mặt ứng trước. Thứ ba, tranh chấp được giải quyết phân tầng theo giá trị và độ phức tạp hàng hoá, với giám định viên độc lập ở hai cấp bên ngoài — giải quyết trong ngày thay vì trong năm."));
push(P("Nền tảng không tự giữ tiền: tiền do ngân hàng giữ với vai trò định chế được cấp phép, nền tảng chỉ ra lệnh khoá/giải ngân/phạt. Cấu trúc này loại bỏ rủi ro pháp lý nghiêm trọng nhất — cung ứng dịch vụ trung gian thanh toán không phép. Về dài hạn, audit trail tích luỹ trở thành lịch sử tín dụng có thể xác minh, là hạ tầng dữ liệu cho tín dụng nông nghiệp dựa trên dữ liệu giao dịch thay vì tài sản thế chấp."));

// ============================================================
// 1. PRODUCT DEFINITION
// ============================================================
push(H1("1. Định nghĩa sản phẩm"));
push(P("AgriContract là nền tảng số hoá hợp đồng mua bán nông sản B2B với cơ chế ký quỹ tự thực thi. Hợp đồng điện tử trên nền tảng có giá trị pháp lý tương đương hợp đồng giấy theo Luật Giao dịch Điện tử 2023."));
push(P([runs("Phạm vi giới hạn có chủ đích ở tầng hợp đồng. ", { bold: true }), runs("AgriContract không xử lý logistics, không phải sàn thương mại điện tử, không thay thế hệ thống kế toán doanh nghiệp. Phần mềm giải quyết một vấn đề cụ thể — thiếu cơ chế tự thực thi trong giao dịch forward contract nông sản B2B — và không có tham vọng giải quyết toàn bộ chuỗi cung ứng.", {})]));
push(legal("Luật GDĐT 2023, Điều 34 và Điều 14.2", "Giá trị pháp lý của hợp đồng điện tử không thể bị phủ nhận chỉ vì được thể hiện dưới dạng thông điệp dữ liệu (Điều 34). Giá trị chứng cứ của thông điệp dữ liệu được xác định dựa trên độ tin cậy của phương thức khởi tạo, lưu trữ và bảo toàn tính nguyên vẹn (Điều 14.2)."));

// ============================================================
// 2. FIVE USER TIERS
// ============================================================
push(H1("2. Năm tầng người dùng"));
push(P("Nền tảng có năm nhóm người dùng với quan hệ pháp lý và quyền hạn hoàn toàn khác nhau. Nhầm lẫn giữa các tầng này dẫn đến hiểu sai về mô hình doanh thu, cơ chế trust và phân tích rủi ro pháp lý."));
push(table(
  [1500, 2900, 5238],
  ["Tầng", "Chủ thể", "Vai trò"],
  [
    [["Tầng 1", "Software Buyer"], "Hiệp hội ngành hàng (VICOFA, VRA, VINACAS) hoặc doanh nghiệp thu mua lớn (Intimex, Phúc Sinh Group, XNK 2/9 Đắk Lắk)", "Trả phí license/subscription — nguồn doanh thu của nền tảng. Triển khai nền tảng cho cộng đồng thành viên; chỉ định Admin vận hành"],
    [["Tầng 2", "Platform Buyer"], "Doanh nghiệp thu mua, tập đoàn xuất khẩu nông sản (nhiều trường hợp trùng Tầng 1)", "Khởi tạo offer và đàm phán điều khoản; khoá tiền ký quỹ trước khi bên bán giao hàng; xác nhận nhận hàng để kích hoạt giải ngân"],
    [["Tầng 3", "Platform Seller"], "Hợp tác xã nông sản, nông hộ liên kết, doanh nghiệp cung ứng nguyên liệu", "Đăng listing sau khi được xác minh; đàm phán và ký hợp đồng điện tử; giao hàng và nhận thanh toán. Nhóm được cơ chế ký quỹ bảo vệ trực tiếp nhất"],
    [["Tầng 4", "INSPECTOR"], "Tổ chức giám định được Nhà nước công nhận: Vinacontrol, Quatest, SGS, Bureau Veritas, Intertek", "Nhân chứng chuyên môn độc lập, không phải người phán xử. Xác định số lượng/chất lượng tại điểm giao nhận; nộp inspection report có hash xác thực, không sửa được sau khi submit"],
    [["Tầng 5", "Escrow Holder"], "Ngân hàng thương mại được NHNN cấp phép (Agribank/BIDV)", "Không phải người dùng nền tảng. Giữ tiền thật; nền tảng chỉ gửi lệnh khoá/giải ngân/phạt. Cấu trúc này loại bỏ rủi ro vi phạm quy định trung gian thanh toán"],
  ],
  { size: 18 }
));
push(P([runs("Vì sao bán cho hiệp hội, không bán trực tiếp cho HTX. ", { bold: true }), runs("HTX nhỏ không mua phần mềm. Hiệp hội hoặc doanh nghiệp thu mua lớn triển khai nền tảng, thành viên của họ sử dụng. Cách này cũng giải quyết bài toán niềm tin: HTX không cần tin một startup mới — họ tin VICOFA/VRA đã triển khai nền tảng. Khi ngân hàng giữ tiền, niềm tin hoàn toàn độc lập với uy tín thương hiệu của nền tảng.", {})]));

push(H3("Xử lý xung đột lợi ích khi Tầng 1 và Tầng 2 trùng nhau"));
push(P("Khi doanh nghiệp thu mua vừa mua license vừa là bên mua hàng trên nền tảng, Admin của họ có xung đột lợi ích trong vai trò xử lý tranh chấp. Hai cơ chế kiểm soát:"));
push(numbered("Tranh chấp giá trị lớn hoặc hàng hoá phức tạp bắt buộc kích hoạt INSPECTOR độc lập — Admin chỉ thực thi kết quả giám định, không được ra phán quyết độc lập."));
push(numbered("Mọi quyết định của Admin được ghi vào audit trail không thể xoá/sửa sau khi submit; hành động override bị đánh dấu vĩnh viễn."));
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
    ["1", "Bên bán đăng listing sau khi Admin xác minh tư cách pháp nhân và gắn dữ liệu geolocation cho lô hàng", "LISTED", "—"],
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
push(bullet([runs("Không có tiền cọc bên bán. ", { bold: true }), runs("HTX không có tiền mặt ứng trước khi còn đang nợ vật tư — đây là ràng buộc khả thi thực tế, không phải lựa chọn thiết kế. Hàng đã giao là rủi ro thực tế của bên bán, không thể thu hồi.", {})]));
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
push(P([runs("Đòn bẩy vốn bổ sung: ", { bold: true }), runs("khi ngân hàng tích hợp trực tiếp, HTX có thể dùng hợp đồng forward đang active — với ký quỹ đã khoá từ phía bên mua — làm bằng chứng dòng tiền tương lai để vay vốn lưu động trong vụ. Hợp đồng có ký quỹ = dòng tiền xác định = tài sản thế chấp thay thế. Agribank đang thử nghiệm cơ chế tín dụng chuỗi giá trị theo hướng này.", {})]));

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
push(P("Quyết toán pro-rata tự động khi sai lệch nằm trong ngưỡng đã thoả thuận — không cần Admin can thiệp. Chỉ khi bên mua chủ động flag vấn đề (thiếu cân hoặc sai chất lượng) thì đợt giao hàng mới đi vào luồng phản hồi/tranh chấp."));

push(H2("3.4 Bất khả kháng"));
push(P("Bất khả kháng phải hội đủ ba điều kiện: khách quan, không thể lường trước, và không thể khắc phục dù đã nỗ lực hết mức. Mất mùa hay sâu bệnh thông thường không đạt ngưỡng này — chỉ thiên tai lớn (lũ, bão), dịch bệnh, hoặc lệnh cấm của nhà nước mới đủ điều kiện. Hệ quả pháp lý: các bên tự chịu thiệt hại của mình, không bên nào được đòi bồi thường — hợp đồng giảm xuống đúng số lượng thực giao."));
push(P("Thời điểm báo cáo neo theo lúc bên bán biết về sự kiện, không neo theo ngày giao hàng. Bên bán phải khai trong một cửa sổ thời gian ngắn (mặc định 3 ngày) kể từ lúc biết. Bằng chứng bắt buộc gồm xác nhận thiên tai của chính quyền địa phương, ảnh, tin tức — Admin xác minh trước khi công nhận, không tự động miễn chỉ vì bên bán khai."));
push(P([runs("Quyền phản đối đối xứng hai chiều. ", { bold: true }), runs("Bên mua có quyền phản đối quyết định công nhận bất khả kháng (nghi ngờ bên bán không thực sự gặp thiên tai); bên bán có quyền phản đối quyết định bác (cho rằng Admin đánh giá sai mức độ). Cả hai cùng được đẩy lên giám định cấp địa phương (Level 1.5) làm cấp cuối. Đối xứng này là cần thiết vì Admin có thể gần phía bên mua hơn về cấu trúc trong mô hình triển khai của doanh nghiệp — nếu chỉ bên mua được phản đối, một Admin thiên vị có thể liên tục bác claim thật của bên bán mà không ai kiểm tra lại.", {})]));
push(legal("Bộ luật Dân sự 2015, Điều 156 và Điều 351", "Sự kiện bất khả kháng là sự kiện xảy ra khách quan, không thể lường trước và không thể khắc phục dù đã áp dụng mọi biện pháp cần thiết. Bên có nghĩa vụ không phải chịu trách nhiệm dân sự trong phạm vi sự kiện bất khả kháng gây ra."));

push(H2("3.5 Huỷ hợp đồng và penalty"));
push(P("Huỷ hợp đồng chỉ tác động các đợt chưa quyết toán; đợt đã xong giữ nguyên, không truy thu. Penalty tính trên tổng giá trị các đợt còn lại, không phải toàn bộ hợp đồng. Cơ chế enforce khác nhau giữa hai bên là có chủ đích — bên mua (thường có vốn) bị ràng buộc bằng cả tiền thật lẫn uy tín; bên bán (HTX) chỉ ràng buộc được bằng uy tín vì không có tiền cọc để giữ."));
push(table(
  [2600, 4300, 2738],
  ["Tình huống", "Cơ chế xử lý", "Căn cứ pháp lý"],
  [
    ["Huỷ trước khi ký", "Tự do rút, không phát sinh nghĩa vụ tài chính", "BLDS 2015, Điều 403 — tự do giao kết"],
    ["Bên bán huỷ (phần chưa quyết toán)", "Ghi nhận penalty debt (sellerPenaltyRate × giá trị đợt còn lại) vào audit trail làm bằng chứng bồi thường; khoá tài khoản ngay theo lockDurationDays; hoàn cọc buyerDepositRate về bên mua (độc lập với penalty debt)", "Luật TM 2005, Điều 302 — bồi thường thiệt hại"],
    ["Bên mua huỷ", "Mất toàn bộ buyerDepositRate (chuyển cho bên bán, ký quỹ tự động seize); nếu đợt hiện tại đang khoá thì seize thêm theo buyerPenaltyRate", "Luật TM 2005, Điều 300 — phạt vi phạm theo thoả thuận"],
    ["Tranh chấp số lượng/chất lượng của một đợt", "Đẩy qua giải quyết tranh chấp phân tầng (Mục 4); quyết toán theo phán quyết", "Nghị định 98/2018, Điều 15"],
  ],
  { size: 18 }
));
push(P([runs("Vì sao bên bán vẫn bị ràng buộc dù không có cọc để seize. ", { bold: true }), runs("Khi bên bán huỷ phần còn lại, không có tiền khoá sẵn để ký quỹ tự động trừ — đây là cái giá thật của quyết định bỏ cọc bên bán. Cơ chế thay thế là khoá tài khoản dựa trên uy tín: chi tiết ở Mục 5.", {})]));

// ============================================================
// 4. INSPECTOR
// ============================================================
push(H1("4. Giám định độc lập — INSPECTOR ba cấp"));
push(P("Phán quyết dựa trên nhận định chủ quan của Admin không đủ tin cậy cho hợp đồng giá trị cao hoặc hàng hoá phức tạp; nhưng áp phí giám định quốc tế cho mọi hợp đồng lại không thực tế về kinh tế. Hệ thống giải quyết cả hai vế bằng phân tầng theo giá trị kết hợp định giá linh hoạt. Cấp giám định được xác định tự động theo giá trị hợp đồng, loại hàng hoá và yếu tố xuất khẩu EU — cấu hình theo từng deployment, không hardcode trong logic nghiệp vụ."));
push(table(
  [1900, 3400, 2400, 1938],
  ["Cấp", "Tổ chức", "Điều kiện kích hoạt (OR)", "Phí giám định"],
  [
    [["Level 1", "Admin nội bộ"], "Admin của deployment", "Giá trị nhỏ VÀ hàng hoá thông thường", "Không có"],
    [["Level 1.5", "Giám định địa phương"], "Vinacontrol, Quatest, trung tâm kiểm định tỉnh được Nhà nước công nhận — là actor thật trên nền tảng, có tài khoản và ký báo cáo", "Giá trị trung bình HOẶC cần xác nhận khối lượng/chất lượng cơ bản", "inspectionFeeRate × contractValue (vd 0,1–0,3%)"],
    [["Level 2", "Giám định quốc tế"], "SGS, Bureau Veritas, Intertek — tổ chức quốc tế, report tự thân đủ uy tín; nền tảng chỉ tiếp nhận và bảo vệ tính toàn vẹn file", "Giá trị lớn HOẶC hàng hoá phức tạp (cà phê specialty, cao su kỹ thuật, điều xuất khẩu EU)", "inspectionFeeRate × contractValue (vd 0,2–0,5%)"],
  ],
  { size: 18 }
));
push(P([runs("Hai mô hình định danh khác nhau, không phải hai mức độ nghiêm trọng của cùng một thứ. ", { bold: true }), runs("Level 1.5 là tổ chức quy mô tỉnh, quan hệ hợp đồng dịch vụ trực tiếp với nền tảng khả thi — trở thành actor thật, có tài khoản đăng nhập, KYC xác minh chứng chỉ hành nghề kiểm định. Level 2 là tập đoàn quốc tế không tích hợp đăng nhập vào một nền tảng agritech Việt Nam — và không cần, vì uy tín report của họ tự thân đã đủ; nền tảng chỉ cần bảo vệ tính toàn vẹn của file sau khi nhận, qua hòm thư tiếp nhận tự động rồi Admin xác nhận.", {})]));
push(P("Cơ chế vận hành chung: INSPECTOR nộp report có hash xác thực, không sửa được sau khi submit. Admin thực thi giải ngân theo report, không được override; override bị đánh dấu vĩnh viễn trong audit trail. Cả hai bên deposit phí giám định vào ký quỹ trước khi INSPECTOR được assign; bên thua tranh chấp chịu toàn bộ, phán quyết 50/50 thì chia đôi."));
push(P([runs("Kiểm soát chọn tổ chức Level 2. ", { bold: true }), runs("Tổ chức Level 2 được đàm phán vào điều khoản lúc ký, nhưng không thả tự do: chỉ chấp nhận ba nhóm — tổ chức quốc tế lớn có danh sách cứng, tổ chức trong nước xác minh qua số chứng nhận công nhận (BoA-VIAS), và tổ chức lạ thì Admin duyệt từng trường hợp và không lưu vào danh sách dùng chung. Điều này chặn kịch bản hai bên thông đồng tự chọn một tổ chức dễ dãi.", {})]));
push(legal("Nghị định 98/2018/NĐ-CP, Điều 15", "Cho phép các bên lựa chọn phương thức phù hợp để giải quyết tranh chấp — thương lượng, hoà giải hoặc trọng tài. Hoà giải nội bộ do Admin thực hiện là hợp lệ khi hai bên đã đồng ý điều khoản này trong Điều khoản dịch vụ trước khi kích hoạt tài khoản."));

// ============================================================
// 5. REPUTATION
// ============================================================
push(H1("5. Uy tín và cơ chế khoá tài khoản"));
push(P("Trong mô hình bỏ tiền cọc bên bán, uy tín là cơ chế dài hạn duy nhất ràng buộc bên bán không phá vỡ hợp đồng. Sau mỗi hợp đồng hoàn thành, hai bên đánh giá lẫn nhau; lịch sử tích luỹ thành điểm uy tín công khai. Phá vỡ một hợp đồng đồng nghĩa mất toàn bộ lịch sử tích luỹ — chi phí dài hạn này thường lớn hơn chênh lệch giá ngắn hạn của một vụ."));
push(P("Uy tín không chỉ là điểm số hiển thị; nó gồm ba loại dữ liệu khác bản chất: một sổ khoá (lock ledger) bất biến phục vụ enforce khoá tài khoản, một điểm uy tín sống phục vụ xếp hạng tìm kiếm, và dữ liệu tham chiếu tín dụng xuất được cho bên thứ ba."));
push(H3("Khoá tài khoản khi phá vỡ hợp đồng"));
push(P("Khi một bên phá vỡ hợp đồng có penalty, tài khoản bị khoá ngay (chặn tạo listing/hợp đồng mới) — không đợi kết quả toà, vì tố tụng 1–3 năm mâu thuẫn với chính lý do sản phẩm tồn tại. Thời gian khoá tách bạch với thiệt hại tài chính (đã phản ánh riêng ở penalty debt) và chỉ đo mức độ hành vi:"));
push(P([runs("lockDurationDays = baseDays × repeatOffenseMultiplier × trackRecordMultiplier", { bold: true, color: T.SUB })], { align: AlignmentType.CENTER }));
push(bullet([runs("baseDays ", { bold: true }), runs("— mặc định 30 ngày.", {})]));
push(bullet([runs("repeatOffenseMultiplier ", { bold: true }), runs("— theo số lần từng phá vỡ hợp đồng trước đó (1x/2x/3x), tuyến tính theo số lần tuyệt đối.", {})]));
push(bullet([runs("trackRecordMultiplier ", { bold: true }), runs("— gated theo ngưỡng mẫu tối thiểu 5 hợp đồng: dưới ngưỡng dùng hệ số trung tính (không đẩy người mới xuống đáy chỉ vì ít dữ liệu); từ ngưỡng trở lên dùng tỷ lệ hợp đồng sạch thật để giảm nhẹ hoặc tăng nặng (0,7x nếu ≥90% sạch, 1,0x nếu 70–90%, 1,3x nếu dưới 70%).", {})]));
push(P("Toàn bộ tham số nằm trong cấu hình, chỉnh được sau khi có dữ liệu thật. Tài khoản mở khoá qua một trong ba đường (đường nào tới trước): bên kia tự báo đã giải quyết, bên vi phạm nộp kết quả ràng buộc (bản án/phán quyết VIAC/thoả thuận hoà giải) để Admin xác minh, hoặc hết thời hạn khoá cố định. Penalty debt và lịch sử vi phạm không bao giờ bị xoá — mở khoá chỉ là cho giao dịch tiếp, không phải xoá tiền án."));
push(legal("Luật Thương mại 2005, Điều 302", "Penalty debt ghi vào audit trail bất biến có giá trị làm căn cứ bồi thường thiệt hại nếu bên bị vi phạm truy đòi qua VIAC hoặc toà án. Nền tảng không tự thu hộ được — đây là bằng chứng, không phải cơ chế cưỡng chế thu tiền."));

// ============================================================
// 6. AUDIT TRAIL / EVIDENCE
// ============================================================
push(H1("6. Bằng chứng và audit trail"));
push(P("Vì chữ ký trên nền tảng ở dạng chữ ký điện tử cơ bản (không có chứng thư từ tổ chức chứng thực được cấp phép), luật không tự động suy đoán “đúng người, đúng thời điểm”. Nền tảng phải tự chứng minh điều đó khi có tranh chấp — nên audit trail không phải lớp phụ, mà là lý do tồn tại của toàn bộ thiết kế bằng chứng. Bốn lớp bảo vệ:"));
push(bullet([runs("Hash nội dung hợp đồng. ", { bold: true }), runs("Toàn bộ điều khoản được băm (SHA-256) lúc ký; mọi thao tác sau đó verify hash trước khi thực hiện — sửa dữ liệu trong DB làm hash lệch, thao tác bị từ chối.", {})]));
push(bullet([runs("Chuỗi hash audit trail. ", { bold: true }), runs("Mỗi bản ghi chứa hash của chính nó và hash của bản ghi trước, tạo thành chuỗi append-only; tài khoản DB của dịch vụ audit chỉ có quyền INSERT + SELECT, không UPDATE/DELETE. Chuỗi được verify định kỳ và trước mỗi lần xuất báo cáo EUDR.", {})]));
push(bullet([runs("Lưu hash nhiều nơi + neo timestamp qua email. ", { bold: true }), runs("Hash được lưu độc lập ở nhiều nơi (DB hợp đồng, DB audit) và gửi email cho cả hai bên sau mỗi lần ký/nộp report — email là điểm neo bên ngoài nền tảng; dù toàn bộ DB bị xâm phạm, hai bên vẫn giữ bằng chứng hash trong hộp thư.", {})]));
push(bullet([runs("Neo bằng chứng lên Bitcoin (OpenTimestamps). ", { bold: true }), runs("Hash cam kết toàn cục được neo lên Bitcoin qua OpenTimestamps (miễn phí, không cần ví crypto). Bằng chứng tồn tại độc lập ngay cả khi nền tảng sập hoàn toàn, và phát hiện được kiểu tấn công xoá-rồi-viết-lại-toàn-chuỗi.", {})]));
push(P("Toàn bộ lịch sử xuất được dưới dạng báo cáo PDF/CSV theo yêu cầu — đây là Due Diligence Statement cho kiểm toán EUDR, không phải tính năng phụ thêm."));
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
    ["Luật Giao dịch Điện tử 2023 (20/2023/QH15)", "Điều 34 — hợp đồng điện tử không bị phủ nhận giá trị; Điều 14.2 — giá trị chứng cứ của thông điệp dữ liệu", "Hợp đồng ký trên nền tảng tương đương hợp đồng giấy; audit trail là bằng chứng hợp lệ trước toà và cho kiểm toán EUDR"],
    ["Nghị định 98/2018/NĐ-CP", "Điều 4 — hợp đồng liên kết lập thành văn bản; Điều 15 — lựa chọn phương thức giải quyết tranh chấp", "Văn bản điện tử đáp ứng Điều 4; hoà giải nội bộ hợp lệ theo Điều 15, không thay thế toà án"],
    ["Luật Trọng tài Thương mại 2010 (54/2010/QH12)", "Điều 5 — thoả thuận trọng tài trước/sau tranh chấp; phán quyết chung thẩm", "Hoà giải nội bộ hợp lệ; nếu leo thang, VIAC xử lý dựa trên audit trail nền tảng làm bằng chứng"],
    ["Bộ luật Dân sự 2015 & Luật Thương mại 2005", "BLDS Điều 328 (ký quỹ), 142 (đại diện), 156 & 351 (bất khả kháng); LTM Điều 300 (phạt), 302 (bồi thường)", "Ký quỹ hợp pháp; penalty thực thi theo thoả thuận đã ký — nền tảng thực thi thoả thuận của hai bên, không ra phán quyết"],
  ],
  { size: 18 }
));

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
push(P("Giá trị của nền tảng được xây dựng thành ba lớp chồng lên nhau — mỗi lớp giải quyết một nhóm nhu cầu và phục vụ một nhóm hưởng lợi khác nhau. Doanh nghiệp tiếp cận nền tảng vì yêu cầu EUDR, ở lại vì giảm được rủi ro vốn và tranh chấp, và không thể rời đi vì lịch sử giao dịch trên nền tảng là tài sản thực chất của họ."));
push(table(
  [1900, 4900, 2838],
  ["Lớp", "Giá trị cụ thể", "Ai hưởng lợi"],
  [
    [["Lớp 1", "Tuân thủ EUDR"], "Hợp đồng thu mua từ HTX có audit trail bất biến = bằng chứng deforestation-free cho kiểm toán EU; xuất báo cáo on-demand thay vì tổng hợp thủ công", "Doanh nghiệp xuất khẩu cà phê, cao su, gỗ vào EU — deadline pháp lý 30/12/2026"],
    [["Lớp 2", "Kiểm soát rủi ro"], "Ký quỹ + Milestone: chi phí phá vỡ hợp đồng vượt lợi ích ngắn hạn. Giám định phân tầng: giải quyết tranh chấp trong ngày. Uy tín: vốn xã hội tích luỹ định giá được", "Cả bên mua lẫn bên bán — đặc biệt HTX không có nguồn lực pháp lý tự bảo vệ"],
    [["Lớp 3", "Hạ tầng tín dụng"], "Audit trail tích luỹ = lịch sử tín dụng thực chất; ngân hàng đọc dữ liệu thay vì chỉ đếm tài sản thế chấp. 57% SME hiện không tiếp cận được vốn", "HTX nhỏ + ngân hàng (vai trò data oracle, không chỉ cấp tín dụng truyền thống)"],
  ],
  { size: 18 }
));

// ============================================================
// 9. FAQ
// ============================================================
push(H1("9. Câu hỏi thường gặp từ hội đồng thẩm định"));
const faq = [
  ["Ký quỹ dùng mock balance — có giá trị kỹ thuật gì?", "Giá trị kỹ thuật nằm ở logic, không phải ở tiền: Choreography Saga qua message queue, idempotency cho compensating transaction, Outbox Pattern đảm bảo at-least-once delivery. Các thách thức này đều thật và không thay đổi khi thay lớp giữ tiền mock bằng API ngân hàng thật — ranh giới interface được thiết kế sạch để việc thay thế không đụng business logic."],
  ["Nền tảng có đang thay thế chức năng của toà án không?", "Không. Admin thực thi điều khoản penalty mà hai bên đã tự thoả thuận và ký trước đó. Luật TM 2005 Điều 300 cho phép phạt vi phạm theo thoả thuận; Nghị định 98/2018 Điều 15 cho phép hoà giải nội bộ. Nền tảng không ra phán quyết — chỉ thực thi thoả thuận đã có."],
  ["Vì sao HTX sẽ tin đặt tiền vào ký quỹ của một nền tảng mới?", "HTX không cần tin nền tảng — họ tin hiệp hội (VICOFA/VRA) đã triển khai nền tảng. Tiền do ngân hàng giữ, không phải nền tảng. Cấu trúc trust được thiết kế để không phụ thuộc vào uy tín của một startup."],
  ["Vì sao chưa có ai làm điều này trước đây?", "Koina (VinaCapital, >1 triệu USD) đã thử năm 2023 với mô hình farm-to-business, đóng cửa 2024 — sai lầm ở phân khúc (bán trực tiếp cho nông dân lẻ, unit economics không bền vững) và mô hình full-stack đốt vốn quá nhanh. AgriContract khác ở ba điểm: bán cho hiệp hội, chỉ giải quyết tầng hợp đồng, và EUDR 2026 tạo ra nhu cầu bắt buộc chưa từng tồn tại."],
  ["INSPECTOR có thể bị mua chuộc không?", "Report có hash xác thực, không sửa được sau khi submit. SGS/Bureau Veritas là tổ chức chứng nhận quốc tế chịu trách nhiệm pháp nhân theo giấy phép hành nghề; tổ chức trong nước được xác minh qua số chứng nhận công nhận quốc gia. Đây là cấp bảo đảm cao hơn nhiều so với Admin nội bộ không có chuyên môn độc lập."],
  ["Ký quỹ dùng mock balance — có vi phạm Nghị định 52/2024 không?", "Không. Nghị định 52/2024 điều chỉnh dịch vụ thanh toán liên quan đến tiền thật. Mock balance trong database không phát sinh giao dịch tài chính thật, không cần giấy phép trung gian thanh toán. Khi tích hợp ngân hàng có license giữ tiền thật, chính ngân hàng là bên chịu điều chỉnh — nền tảng vẫn chỉ ra lệnh."],
];
faq.forEach(([q, a]) => { push(P([runs(q, { bold: true, color: T.HEAD })], { after: 40 })); push(P(a, { after: 160 })); });

// ============================================================
// 10. LIMITATIONS
// ============================================================
push(H1("10. Giới hạn và phạm vi ngoài"));
push(P("Các điểm dưới đây là ranh giới có chủ đích của thiết kế, không phải điểm mù bị bỏ sót — nêu rõ để giữ độ tin cậy trước hội đồng."));
push(bullet([runs("Ngân hàng giữ tiền là mock trong phạm vi đồ án. ", { bold: true }), runs("Không tổ chức tín dụng nào ký hợp đồng API cho một đồ án tốt nghiệp. Lớp giữ tiền được mô phỏng theo đúng ngữ nghĩa nghiệp vụ (mô hình ledger gộp), interface thiết kế sạch để tích hợp thật chỉ cần thay implementation — nhưng tích hợp thật nằm ngoài phạm vi.", {})]));
push(bullet([runs("Chữ ký ở tầng cơ bản, không tương đương chữ ký tay. ", { bold: true }), runs("Chữ ký điện tử của nền tảng không có chứng thư từ CA được cấp phép nên không được luật tự động suy đoán; hợp đồng vẫn có hiệu lực đầy đủ, nhưng gánh nặng chứng minh khi tranh chấp thuộc về nền tảng (đã bù bằng audit trail). Chữ ký số CA và WebAuthn là hướng nâng cấp, chưa triển khai.", {})]));
push(bullet([runs("Chống thông đồng toàn diện là giới hạn cố hữu. ", { bold: true }), runs("Nếu Admin và toàn bộ (hoặc đa số) bên nhận cảnh báo phía Software Buyer cùng thông đồng, không cơ chế phần mềm nào trong kiến trúc hiện tại chặn được — đây chính là bài toán trustless consensus mà nhóm chủ đích không theo hướng blockchain vì ràng buộc thời gian/nhân lực. Neo email và neo Bitcoin vẫn đảm bảo bằng chứng tồn tại độc lập cho bất kỳ bên thứ ba nào sau này kiểm tra.", {})]));
push(bullet([runs("Xác minh nội dung file là best-effort. ", { bold: true }), runs("Nền tảng lưu và bảo vệ tính toàn vẹn file (trích lục địa chính, KML, report), nhưng không đối chiếu nội dung file với dữ liệu đã khai — khai đất hợp lệ hình học nhưng sai sự thật vẫn có thể lọt; cross-check tỉnh chỉ giảm nhẹ lỗi convert toạ độ vô ý, không chặn fraud có chủ đích.", {})]));
push(bullet([runs("Đòn bẩy uy tín phụ thuộc giả định thể chế chưa xác nhận. ", { bold: true }), runs("Cơ chế khoá tài khoản có hiệu lực trong phạm vi nền tảng; việc gắn dữ liệu vi phạm vào hệ quả tư cách hội viên hiệp hội (quota, đoàn xúc tiến…) cần hiệp hội ban hành quy chế mới — nằm ngoài khả năng đồ án tự quyết định.", {})]));

// ============================================================
// 11. SOURCES
// ============================================================
push(H1("11. Danh mục nguồn tham khảo"));
push(H3("Văn bản pháp luật"));
[
  "Luật Giao dịch Điện tử 2023 (Luật 20/2023/QH15, hiệu lực 1/7/2024).",
  "Nghị định 52/2024/NĐ-CP — Thanh toán không dùng tiền mặt; Nghị định 52/2013/NĐ-CP — Thương mại điện tử.",
  "Nghị định 98/2018/NĐ-CP — Liên kết sản xuất và tiêu thụ nông sản.",
  "Luật Trọng tài Thương mại 2010 (Luật 54/2010/QH12).",
  "Bộ luật Dân sự 2015 — Điều 328, 142, 403, 156, 351.",
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
  "We-Fi / OCB — SME financing gap 68% in Vietnam (2022).",
  "EC Regulation (EU) 2025/2650 — EUDR timeline (23/12/2025).",
].forEach(s => push(bullet(s)));

push(endMark());

const doc = buildDoc(body, {
  title: "AgriContract — Giải Pháp, Người Dùng & Mô Hình Kinh Doanh v5.0",
  headerText: "AgriContract · Giải pháp & Mô hình",
  footerText: "v5.0 · Tháng 7/2026",
});
Packer.toBuffer(doc).then(buf => { fs.writeFileSync("/tmp/AgriContract_02_GiaiPhap_MoHinh_v5.docx", buf); console.log("written", buf.length); });
