const fs = require("fs");
const D = require("docx");
const {
  Document, Packer, Paragraph, TextRun, HeadingLevel, AlignmentType,
  Table, TableRow, TableCell, WidthType, BorderStyle, ShadingType,
  TableOfContents, PageNumber, Header, Footer, PageBreak, LevelFormat,
  PositionalTab, PositionalTabAlignment, PositionalTabLeader
} = D;

// ---------- design tokens ----------
const INK      = "111827";  // body
const HEAD     = "1F2A37";  // headings
const SUB      = "374151";  // sub headings / muted
const MUTE     = "6B7280";  // captions / sources
const HFILL    = "374151";  // table header fill
const BFILL    = "F3F4F6";  // callout / light fill
const ZEBRA    = "F9FAFB";  // zebra
const BORDER   = "D1D5DB";  // table borders
const RULE     = "9CA3AF";  // quote left border
const FONT     = "Calibri";
const CONTENT_W = 9638;      // page content width in DXA (A4, 2cm margins)

const thin = { style: BorderStyle.SINGLE, size: 4, color: BORDER };
const cellBorders = { top: thin, bottom: thin, left: thin, right: thin };

// ---------- helpers ----------
const runs = (text, o = {}) => new TextRun({ text, font: FONT, size: o.size || 21, color: o.color || INK, bold: o.bold || false, italics: o.italics || false });

function P(text, o = {}) {
  return new Paragraph({
    spacing: { after: o.after ?? 120, before: o.before ?? 0, line: o.line ?? 276 },
    alignment: o.align || (o.justify === false ? AlignmentType.LEFT : AlignmentType.JUSTIFIED),
    indent: o.indent,
    children: Array.isArray(text) ? text : [runs(text, o)],
  });
}

function H1(text) {
  return new Paragraph({
    heading: HeadingLevel.HEADING_1,
    spacing: { before: 320, after: 120 },
    border: { bottom: { style: BorderStyle.SINGLE, size: 6, color: BORDER, space: 6 } },
    children: [new TextRun({ text, font: FONT, size: 30, bold: true, color: HEAD })],
  });
}
function H2(text) {
  return new Paragraph({
    heading: HeadingLevel.HEADING_2,
    spacing: { before: 240, after: 80 },
    children: [new TextRun({ text, font: FONT, size: 25, bold: true, color: HEAD })],
  });
}
function H3(text) {
  return new Paragraph({
    heading: HeadingLevel.HEADING_3,
    spacing: { before: 180, after: 60 },
    children: [new TextRun({ text, font: FONT, size: 22, bold: true, color: SUB })],
  });
}

function bullet(text, o = {}) {
  return new Paragraph({
    numbering: { reference: "bullets", level: 0 },
    spacing: { after: o.after ?? 60, line: 276 },
    children: Array.isArray(text) ? text : [runs(text, o)],
  });
}

// Expert quote block: italic, left border, indented, with attribution
function quote(text, who) {
  const bar = { left: { style: BorderStyle.SINGLE, size: 18, color: RULE, space: 12 } };
  const out = [new Paragraph({
    border: bar, indent: { left: 360 }, spacing: { before: 120, after: 40, line: 276 },
    children: [new TextRun({ text: "“" + text + "”", font: FONT, size: 21, italics: true, color: SUB })],
  })];
  if (who) out.push(new Paragraph({
    border: bar, indent: { left: 360 }, spacing: { after: 140, line: 240 },
    children: [new TextRun({ text: "— " + who, font: FONT, size: 19, color: MUTE })],
  }));
  return out;
}

// Legal callout: shaded box, bold label, body text — no icon
function legal(label, text) {
  return new Paragraph({
    shading: { type: ShadingType.CLEAR, color: "auto", fill: BFILL },
    border: {
      top: { style: BorderStyle.SINGLE, size: 2, color: BORDER, space: 6 },
      bottom: { style: BorderStyle.SINGLE, size: 2, color: BORDER, space: 6 },
      left: { style: BorderStyle.SINGLE, size: 12, color: SUB, space: 10 },
      right: { style: BorderStyle.SINGLE, size: 2, color: BORDER, space: 6 },
    },
    spacing: { before: 120, after: 140, line: 264 }, indent: { left: 60, right: 60 },
    children: [
      new TextRun({ text: "Căn cứ pháp lý — " + label + ". ", font: FONT, size: 20, bold: true, color: HEAD }),
      new TextRun({ text, font: FONT, size: 20, color: SUB }),
    ],
  });
}

// Source caption line (small, muted)
function src(text) {
  return new Paragraph({
    spacing: { after: 140, line: 240 },
    children: [new TextRun({ text: "Nguồn: " + text, font: FONT, size: 17, color: MUTE, italics: true })],
  });
}

// ---------- table builder ----------
function cell(content, o = {}) {
  const kids = (Array.isArray(content) ? content : [content]).map((line, i) =>
    new Paragraph({
      alignment: o.align || AlignmentType.LEFT,
      spacing: { after: i === (Array.isArray(content) ? content.length - 1 : 0) ? 0 : 40, line: 252 },
      children: (Array.isArray(line) ? line : [new TextRun({ text: line, font: FONT, size: o.size || 19, bold: o.bold || false, color: o.color || INK })]),
    })
  );
  return new TableCell({
    width: { size: o.w, type: WidthType.DXA },
    shading: o.fill ? { type: ShadingType.CLEAR, color: "auto", fill: o.fill } : undefined,
    margins: { top: 60, bottom: 60, left: 90, right: 90 },
    verticalAlign: D.VerticalAlign.CENTER,
    borders: cellBorders,
    children: kids,
  });
}

function table(widths, headers, rows, opts = {}) {
  const headRow = new TableRow({
    tableHeader: true,
    children: headers.map((h, i) => cell(h, { w: widths[i], fill: HFILL, color: "FFFFFF", bold: true, size: 19, align: opts.headAlign?.[i] })),
  });
  const bodyRows = rows.map((r, ri) => new TableRow({
    children: r.map((c, i) => cell(c, { w: widths[i], fill: ri % 2 === 1 ? ZEBRA : undefined, size: opts.size || 19, align: opts.colAlign?.[i], bold: opts.boldCol?.[i] })),
  }));
  return new Table({
    columnWidths: widths,
    width: { size: widths.reduce((a, b) => a + b, 0), type: WidthType.DXA },
    borders: {
      top: thin, bottom: thin, left: thin, right: thin,
      insideHorizontal: thin, insideVertical: thin,
    },
    rows: [headRow, ...bodyRows],
  });
}
const spacer = (h = 60) => new Paragraph({ spacing: { after: h }, children: [] });

// ============================================================
// CONTENT
// ============================================================
const body = [];
const push = (...x) => x.forEach((e) => body.push(e));

// ---- Cover ----
push(
  new Paragraph({ spacing: { before: 600, after: 0 }, children: [new TextRun({ text: "AGRICONTRACT", font: FONT, size: 26, bold: true, color: SUB, characterSpacing: 60 })] }),
  new Paragraph({ spacing: { before: 120, after: 60 }, border: { bottom: { style: BorderStyle.SINGLE, size: 8, color: HEAD, space: 8 } }, children: [new TextRun({ text: "Phân Tích Thị Trường & Luận Cứ Kinh Doanh", font: FONT, size: 46, bold: true, color: HEAD })] }),
  new Paragraph({ spacing: { before: 120, after: 40 }, children: [new TextRun({ text: "Nền tảng số hoá vòng đời hợp đồng mua bán nông sản B2B với cơ chế ký quỹ tự thực thi", font: FONT, size: 23, color: SUB, italics: true })] }),
  new Paragraph({ spacing: { before: 240 }, children: [new TextRun({ text: "Tài liệu nội bộ — Đồ án Tốt nghiệp", font: FONT, size: 20, color: MUTE })] }),
  new Paragraph({ spacing: { after: 0 }, children: [new TextRun({ text: "Phiên bản 5.0 · Tháng 7/2026", font: FONT, size: 20, color: MUTE })] }),
  new Paragraph({ spacing: { before: 360, after: 0 }, children: [new PageBreak()] }),
);

// ---- TOC ----
push(
  new Paragraph({ spacing: { after: 120 }, children: [new TextRun({ text: "Mục lục", font: FONT, size: 28, bold: true, color: HEAD })] }),
  new TableOfContents("Mục lục", { hyperlink: true, headingStyleRange: "1-2" }),
  new Paragraph({ spacing: { before: 240 }, children: [new PageBreak()] }),
);

// ============================================================
// 0. EXECUTIVE SUMMARY
// ============================================================
push(H1("Tóm tắt điều hành"));
push(P("Xuất khẩu nông lâm thuỷ sản Việt Nam đạt 70,09 tỷ USD năm 2025, nhưng phần lớn giao dịch thu mua B2B — đặc biệt ở tầng thu mua từ hợp tác xã (HTX) — vẫn vận hành trên thoả thuận miệng, không có cơ chế bảo đảm thực thi. Ba đặc thù cấu trúc của ngành khiến chi phí phá vỡ hợp đồng thường thấp hơn lợi ích thu được: tính mùa vụ (hợp đồng ký trước thu hoạch 3–6 tháng, giá biến động mạnh), hàng dễ hỏng (không chờ được tố tụng 1–3 năm), và bất đối xứng quyền lực (HTX nhỏ không có công cụ pháp lý tự bảo vệ)."));
push(P("Ba yếu tố bên ngoài đang hội tụ, biến nhu cầu số hoá tầng hợp đồng từ “nên có” thành “bắt buộc phải có”: quy định chống phá rừng của EU (EUDR) với deadline pháp lý cứng 30/12/2026, áp lực chuẩn hoá quy trình ở quy mô xuất khẩu 70 tỷ USD, và chính sách chuyển đổi số nông nghiệp quốc gia còn cách mục tiêu 20 điểm phần trăm."));
push(P("Thị trường hiện chưa có giải pháp nào giải quyết đồng thời tầng hợp đồng cùng với ký quỹ trung lập, giải quyết tranh chấp phân tầng, và audit trail sẵn sàng cho EUDR ở tầng HTX/SME. AgriContract lấp đúng khoảng trống này, với phạm vi giới hạn có chủ đích ở tầng hợp đồng — không phải logistics, không phải sàn thương mại điện tử, không thay thế hệ thống kế toán. Về dài hạn, audit trail tích luỹ trên nền tảng trở thành lịch sử tín dụng có thể xác minh — hạ tầng dữ liệu mà ngân hàng cần để chuyển từ cho vay theo tài sản thế chấp sang cho vay theo dữ liệu giao dịch."));

// ============================================================
// 1. MARKET SIZE
// ============================================================
push(H1("1. Quy mô thị trường"));
push(P("Năm 2025, kim ngạch xuất khẩu nông lâm thuỷ sản Việt Nam đạt 70,09 tỷ USD, tăng 12% so với năm 2024 và vượt mục tiêu 65 tỷ USD đã đề ra. Thặng dư thương mại đạt kỷ lục 21 tỷ USD. Bộ Nông nghiệp và Môi trường đặt mục tiêu 73–74 tỷ USD cho năm 2026."));
push(table(
  [2600, 1900, 1900, 3238],
  ["Mặt hàng", "Kim ngạch 2025", "Tăng trưởng YoY", "Vị thế toàn cầu"],
  [
    ["Cà phê", "8,57 tỷ USD", "+54,4%", "Xuất khẩu Robusta số 1 thế giới"],
    ["Rau quả", "8,56 tỷ USD", "Kỷ lục mới", "Top 10 thế giới"],
    ["Gỗ & sản phẩm gỗ", "17,32 tỷ USD", "+6,6%", "Xuất khẩu đồ gỗ số 2 thế giới"],
    ["Cao su (toàn ngành)", "~11 tỷ USD", "+~10%", "Cao su tự nhiên top 3 thế giới"],
    ["Điều nhân", "5,23 tỷ USD", "+20,4%", "Số 1 thế giới — 18 năm liên tiếp"],
  ],
  { colAlign: [null, AlignmentType.CENTER, AlignmentType.CENTER, null] }
));
push(src("Bộ Nông nghiệp và Môi trường — Họp báo tổng kết 2025 (6/1/2026); VietnamPlus — Agriculture sector on path to $74B (23/2/2026)."));

push(P([runs("Bốn ngành hàng nằm trong phạm vi phục vụ trực tiếp của nền tảng — ", {}), runs("cà phê, lúa gạo, cao su, điều", { bold: true }), runs(" — được chọn vì chúng hội tụ đủ hai điều kiện: giá trị giao dịch cao và quy trình thu mua từ HTX còn phụ thuộc nặng vào niềm tin cá nhân. Cà phê và cao su thuộc phạm vi điều chỉnh của EUDR; lúa gạo và điều không thuộc EUDR nhưng chịu đúng ba đặc thù cấu trúc mô tả ở Mục 2.", {})]));

push(H2("1.1 Cà phê — Siêu chu kỳ giá và hệ quả chuỗi cung ứng"));
push(P("Trong niên vụ 2024, giá cà phê Robusta tại Tây Nguyên leo từ 60 triệu đồng/tấn lên 135 triệu đồng/tấn. Biên độ tăng 125% trong chưa đầy một năm tạo ra áp lực phá vỡ hợp đồng có hệ thống trên toàn chuỗi thu mua — không phải ngoại lệ, mà là phản ứng hợp lý về mặt kinh tế khi không có cơ chế ràng buộc."));
push(...quote("Một tỷ lệ lớn các nhà cung cấp không giao hàng, nên các nhà xuất khẩu đang vật lộn.", "Ông Phan Minh Thông, Chủ tịch Phúc Sinh Group"));
push(...quote("Chúng tôi phải giảm kế hoạch từ 125.000 tấn xuống 105.000 tấn để kiểm soát rủi ro vốn. Với giá tăng như vậy, 100 triệu đồng trước kia mua được 2 tấn thì nay chỉ còn 1 tấn.", "Ông Lê Đức Huy, Tổng Giám đốc Công ty XNK 2/9 Đắk Lắk"));
push(P("Kim ngạch xuất khẩu cà phê năm 2025 đạt 8,57 tỷ USD (+54,4%), với sản lượng 1,5 triệu tấn. Thị trường EU chiếm khoảng 40% tổng kim ngạch — đây là thị trường xuất khẩu chịu ảnh hưởng trực tiếp từ EUDR."));
push(src("VTV.vn — Giá thu mua tăng cao, cơ hội và thách thức của ngành cà phê Việt Nam (17/4/2024); VICOFA — Báo cáo tổng kết niên vụ cà phê 2024–2025 (10/2025)."));

push(H2("1.2 Lúa gạo — Quy mô nội địa lớn và bất đối xứng thu mua"));
push(P("Lúa gạo không thuộc phạm vi điều chỉnh của EUDR, nhưng là ngành hàng chịu bất đối xứng quyền lực rõ nét nhất trong chuỗi thu mua. Tại Đồng bằng sông Cửu Long, khoảng 90% sản lượng lúa vẫn tiêu thụ qua kênh thương lái; doanh nghiệp thu mua trực tiếp từ nông dân chỉ chiếm khoảng 10%. Đặc thù thứ hai của lúa gạo là sự đa dạng giống trong cùng một thời điểm và địa bàn: nhiều giống (OM 18, ST25, IR 50404…) cùng tồn tại tại một tỉnh với mặt bằng giá khác hẳn nhau — khiến việc tham chiếu giá và xác định đúng mặt hàng trở thành yêu cầu bắt buộc chứ không phải chi tiết phụ."));
push(P("Với lúa gạo, giá trị cốt lõi của nền tảng nằm ở cơ chế ký quỹ, giải quyết tranh chấp và tích luỹ uy tín — không phải ở compliance EUDR."));
push(src("Tạp chí Kinh tế Sài Gòn (20/6/2025)."));

push(H2("1.3 Cao su — Tăng trưởng giá trị"));
push(P("Năm 2024, Việt Nam xuất khẩu xấp xỉ 2 triệu tấn cao su tự nhiên, đạt 3,42 tỷ USD (+18% về giá trị dù giảm 6% về sản lượng). Tính toàn ngành — bao gồm sản phẩm chế biến và gỗ cao su — đạt 10,2 tỷ USD. Hiệp hội Cao su Việt Nam (VRA) dự báo năm 2025 đạt 11–11,6 tỷ USD nhờ giá hồi phục và nhu cầu tăng từ Trung Quốc và Ấn Độ. Cao su thuộc phạm vi điều chỉnh của EUDR."));
push(src("VRA — Vietnam Rubber Industry International Conference 2025 (12/2025)."));

push(H2("1.4 Điều — Dẫn đầu xuất khẩu, phụ thuộc nguyên liệu nhập khẩu"));
push(P("Năm 2025, Việt Nam xuất khẩu 766.600 tấn điều nhân, đạt 5,23 tỷ USD (+20,4%), giữ vị trí số 1 thế giới 18 năm liên tiếp với thị phần hơn 80% tổng xuất khẩu điều nhân toàn cầu. Điểm yếu cấu trúc: 90% nguyên liệu thô phụ thuộc nhập khẩu từ châu Phi và Campuchia — rủi ro đứt gãy chuỗi cung ứng là thường trực, làm tăng giá trị của các cơ chế ràng buộc giao hàng đúng cam kết."));
push(src("VINACAS & Tổng cục Hải quan Việt Nam (1/2026)."));

// ============================================================
// 2. THREE STRUCTURAL DRIVERS
// ============================================================
push(H1("2. Ba đặc thù cấu trúc tạo ra nhu cầu"));
push(P("Các ngành hàng trên có quy mô xuất khẩu lớn nhưng cơ sở hạ tầng giao dịch B2B vẫn hoạt động theo cơ chế thủ công, phụ thuộc vào niềm tin cá nhân và chịu rủi ro tập trung cao. Ba đặc thù cấu trúc sau đây tạo ra môi trường mà chi phí phá vỡ hợp đồng thường thấp hơn lợi ích thu được — đây là gốc rễ của vấn đề, không phải triệu chứng."));

push(H2("2.1 Tính mùa vụ — phá vỡ hợp đồng là quyết định kinh tế hợp lý"));
push(P("Hợp đồng nông sản thường ký trước thu hoạch 3–6 tháng. Trong khoảng thời gian đó, nếu giá thị trường tăng mạnh, bên bán có thể thu được khoản chênh lệch lớn hơn bất kỳ khoản phạt nào — đặc biệt khi hợp đồng không có cơ chế ràng buộc tài chính nào ngoài lời hứa. Đây không phải vấn đề đạo đức mà là một incentive có thật, cần được giải quyết ở tầng thiết kế cơ chế."));
push(...quote("Khi doanh nghiệp ký hợp đồng thu mua với nông dân ở mức 7.000 đồng/kg, nhưng đến thời điểm thu mua giá tăng lên 10.000 đồng/kg, doanh nghiệp chỉ trả đúng giá hợp đồng. Nông dân bẻ kèo là phản ứng tất yếu.", "GS.TS Trần Đức Viên, nguyên Giám đốc Học viện Nông nghiệp Việt Nam"));
push(P("Theo đánh giá tại Diễn đàn Thúc đẩy hợp tác liên kết chuỗi giá trị nông nghiệp (tháng 8/2024), chỉ khoảng 30% liên kết đạt mức bền chắc. Phần còn lại là liên kết lỏng lẻo, không có cơ chế thực thi."));
push(...quote("Mô hình liên kết “4 nhà” đang đứt gãy vì nạn bẻ kèo. Cần lập các hợp đồng chuỗi ràng buộc trách nhiệm để ngân hàng tự tin giải ngân, thay vì chỉ chăm chăm đếm tài sản thế chấp.", "Bà Cao Xuân Thu Vân, Chủ tịch Liên minh Hợp tác xã Việt Nam"));
push(src("Vietnamnet — 3 chiêu để hạn chế doanh nghiệp bẻ kèo, nông dân chạy làng (30/8/2024); Báo Pháp Luật TP.HCM — Nghịch lý xuất khẩu cà phê Việt (4/4/2026)."));
push(legal("Nghị định 98/2018/NĐ-CP, Điều 4 và Điều 15", "Hợp đồng liên kết sản xuất và tiêu thụ nông sản phải được lập thành văn bản (Điều 4). Các bên được lựa chọn phương thức phù hợp — thương lượng, hoà giải, hoặc trọng tài — mà không bắt buộc qua toà án (Điều 15)."));

push(H2("2.2 Hàng dễ hỏng — mỗi ngày tranh chấp là thiệt hại không thu hồi được"));
push(P("Khác với hàng công nghiệp, nông sản không thể chờ. Thủ tục tố tụng tại toà án thương mại Việt Nam trung bình kéo dài 1–3 năm — đủ thời gian để toàn bộ hàng hoá mất giá trị hoàn toàn. Năm 2024, Trung tâm Trọng tài Quốc tế Việt Nam (VIAC) ghi nhận 475 vụ tranh chấp thương mại, cao nhất kể từ khi thành lập, trong đó tranh chấp mua bán hàng hoá chiếm tỷ lệ lớn nhất (25%)."));
push(src("VIAC — Thống kê hoạt động giải quyết tranh chấp năm 2024."));
push(legal("Luật Trọng tài Thương mại 2010, Điều 5", "Các bên có quyền thoả thuận chọn trọng tài làm phương thức giải quyết tranh chấp trước hoặc sau khi tranh chấp phát sinh. Phán quyết trọng tài là chung thẩm, không bị kháng cáo — nhanh hơn toà án và phù hợp với đặc thù hàng hoá dễ hỏng."));

push(H2("2.3 Bất đối xứng quyền lực — HTX không có công cụ tự bảo vệ"));
push(P("Tại Đồng bằng sông Cửu Long, 90% sản lượng lúa vẫn tiêu thụ qua kênh thương lái, doanh nghiệp thu mua trực tiếp từ nông dân chỉ chiếm khoảng 10%. Bất đối xứng thông tin và quyền lực khiến HTX nhỏ không có khả năng đàm phán thực chất, càng không có nguồn lực pháp lý để tự bảo vệ khi bị vi phạm hợp đồng. Hệ quả kéo dài sang tận khả năng tiếp cận vốn:"));
push(bullet("57% doanh nghiệp vừa và nhỏ chính thức tại Việt Nam không tiếp cận được tài chính (We-Fi / OCB, 2022)."));
push(bullet("Khoảng cách tài chính SME so với nhu cầu tiềm năng ước tính 68%."));
push(bullet("Nguyên nhân chính: thiếu tài sản thế chấp và không có lịch sử tín dụng chính thức có thể xác minh."));
push(src("We-Fi & OCB — How cooperation from a bank gives wings to women entrepreneurs in Vietnam (2022)."));

// ============================================================
// 3. WHY NOW
// ============================================================
push(H1("3. Ba yếu tố thúc đẩy đồng thời"));
push(P("Nhu cầu tự thân của ngành đã tồn tại từ lâu, nhưng ba yếu tố bên ngoài dưới đây mới là thứ biến nó thành nhu cầu cấp bách và có deadline cụ thể tại thời điểm này."));

push(H2("3.1 EUDR — Áp lực tuân thủ cứng từ thị trường EU"));
push(P("Quy định EU 2023/1115 (sửa đổi bởi 2025/2650) yêu cầu toàn bộ lô hàng cà phê, cao su, gỗ, ca cao nhập khẩu vào EU phải kèm bằng chứng deforestation-free và audit trail truy xuất đến toạ độ GPS từng mảnh đất trồng, chứng minh không có phá rừng sau 31/12/2020. Deadline ràng buộc pháp lý: 30/12/2026 đối với doanh nghiệp lớn và vừa; 30/6/2027 đối với doanh nghiệp nhỏ và siêu nhỏ."));
push(table(
  [2400, 3200, 4038],
  ["Deadline", "Đối tượng", "Yêu cầu tối thiểu"],
  [
    ["30/12/2026", "Doanh nghiệp lớn & vừa", "Due Diligence Statement đầy đủ, truy xuất đến toạ độ GPS"],
    ["30/6/2027", "Doanh nghiệp nhỏ & siêu nhỏ", "DDS đơn giản hoá, truy xuất theo postal code"],
  ],
  { colAlign: [AlignmentType.CENTER, null, null] }
));
push(P([runs("Một yêu cầu của EUDR quyết định trực tiếp độ phức tạp của bài toán truy xuất: ", {}), runs("cấm mass balance", { bold: true }), runs(" — không được gộp hàng từ nhiều nguồn rồi khai đại diện bằng một mảnh đất. Phải khai đủ toàn bộ mảnh đất đóng góp vào lô hàng, tách bạch rõ ràng. Điều này va trực tiếp với bản chất của HTX: một lô hàng thường được gom từ nhiều hộ thành viên, mỗi hộ có mảnh đất riêng — nên bằng chứng nguồn gốc không thể là một toạ độ đơn, mà phải là tập hợp toạ độ của tất cả các hộ đóng góp. Đây chính là tầng dữ liệu mà nền tảng phải xử lý ở khâu thu mua từ HTX.", {})]));
push(P("Việt Nam được phân loại “low-risk” trong EUDR benchmarking, tạo lợi thế cạnh tranh so với các nguồn cung “high-risk”. Để tận dụng lợi thế này, doanh nghiệp cần chứng minh audit trail đầy đủ tại tầng thu mua từ HTX — chính là layer AgriContract xử lý. EUDR không bắt buộc sử dụng AgriContract; nó tạo ra yêu cầu pháp lý về audit trail số hoá, và AgriContract giải quyết một mắt xích cụ thể trong bức tranh compliance đó, không phải toàn bộ giải pháp."));
push(src("EC Regulation (EU) 2025/2650 — Official Journal (23/12/2025); World Resources Institute — What Is the EUDR? (cập nhật 5/2026)."));

push(H2("3.2 Quy mô xuất khẩu và áp lực chuẩn hoá"));
push(P("Với 70 tỷ USD xuất khẩu năm 2025, chuẩn hoá quy trình giao dịch không còn là lựa chọn tối ưu mà là điều kiện duy trì khả năng tiếp cận thị trường quốc tế. BIDV và Techcombank đã tham gia nền tảng truy xuất blockchain Agrichain (Exabyte) với vai trò xác thực dữ liệu — tín hiệu rõ ràng rằng các định chế tài chính đang dịch chuyển từ vai trò tài trợ vốn sang vai trò xác thực thông tin trong chuỗi nông sản."));
push(src("Vietnam.vn — Banks partner with the digital agricultural supply chain (1/4/2026)."));

push(H2("3.3 Chính sách số hoá nông nghiệp"));
push(P("Quyết định 749/QĐ-TTg (3/6/2020) xác định nông nghiệp là một trong tám lĩnh vực ưu tiên chuyển đổi số quốc gia, với mục tiêu 50% doanh nghiệp nông nghiệp ứng dụng công nghệ số vào năm 2025. Thực tế đạt được chỉ khoảng 30% — khoảng cách 20 điểm phần trăm là dư địa thị trường cụ thể, không phải ước tính."));
push(src("PSAV — Viet Nam prioritizes digitalization of agriculture sector; Tạp chí Kinh tế và Dự báo (28/2/2025)."));

// ============================================================
// 4. MARKET GAP
// ============================================================
push(H1("4. Khoảng trống thị trường"));
push(H2("4.1 Các giải pháp hiện có và giới hạn của chúng"));
push(table(
  [2100, 2500, 3300, 1738],
  ["Giải pháp", "Năng lực", "Giới hạn", "Tình trạng"],
  [
    ["Hợp đồng giấy truyền thống", "Có giá trị pháp lý theo BLDS 2015", "Không có ký quỹ; không audit trail số; không tự thực thi penalty", "Chiếm ~70% thị trường"],
    ["Agrichain (Exabyte)", "Truy xuất nguồn gốc blockchain; BIDV và Techcombank tích hợp", "Không có đàm phán hợp đồng; không ký quỹ; không cơ chế penalty", "Đang hoạt động — tầng traceability"],
    ["Kamereo", "B2B procurement cho nhà hàng/retailer; Series B 7,8 triệu USD", "Không phải forward contract nông sản; không phục vụ HTX", "Đang hoạt động — segment khác"],
    ["Alibaba Trade Assurance", "Ký quỹ B2B quốc tế; bảo vệ hơn 160 triệu đơn; 37 triệu buyer", "Không bản địa hoá cho HTX Việt Nam; không workflow tiếng Việt; không tích hợp EUDR", "Bằng chứng khái niệm ở quy mô lớn"],
    ["Koina (đóng cửa 2024)", "Farm-to-business; VinaCapital hậu thuẫn; huy động >1 triệu USD", "Không có contract layer; không ký quỹ; mô hình full-stack đốt vốn quá nhanh", "Đã ngừng — sai phân khúc, thiếu revenue model"],
    ["AgriContract", "Vòng đời hợp đồng + Ký quỹ + Audit trail + Giải quyết tranh chấp", "Không giải quyết logistics — phạm vi giới hạn có chủ đích", "Đang phát triển"],
  ],
  { size: 18 }
));
push(src("Kamereo Series B (2024); Alibaba Trade Assurance."));

push(H2("4.2 Vì sao khoảng trống contract layer vẫn còn tồn tại"));
push(P([runs("Phân khúc sai. ", { bold: true }), runs("Các startup đã thử gia nhập thị trường agtech Việt Nam đều tiếp cận sai phân khúc: bán trực tiếp cho nông dân lẻ, nơi quy mô giao dịch quá nhỏ để unit economics có thể hoạt động. Không ai trong số đó xây dựng mô hình triển khai qua hiệp hội ngành hàng — đây là cách duy nhất giải quyết được vấn đề niềm tin ở tầng HTX mà không cần startup tự xây dựng thương hiệu từ đầu.", {})]));
push(P([runs("Rào cản pháp lý. ", { bold: true }), runs("Giữ tiền của người khác mà không có giấy phép trung gian thanh toán từ Ngân hàng Nhà nước là hành vi bị nghiêm cấm theo Nghị định 52/2024 — mức phạt hành chính 100–200 triệu đồng và có thể truy cứu trách nhiệm hình sự theo Nghị định 88/2019. Yêu cầu tích hợp ngân hàng ngay từ đầu là rào cản đáng kể đối với startup ở giai đoạn sớm.", {})]));
push(legal("Nghị định 52/2024/NĐ-CP, Điều 8, Khoản 7 và Nghị định 88/2019/NĐ-CP", "Nghiêm cấm cung ứng dịch vụ trung gian thanh toán khi chưa được NHNN cấp Giấy phép hoạt động. Mức phạt 100–200 triệu đồng đối với hành vi vi phạm và buộc chấm dứt hoạt động."));
push(P([runs("Thời điểm. ", { bold: true }), runs("Trước khi EUDR có deadline pháp lý cứng, không có áp lực bên ngoài nào buộc doanh nghiệp xuất khẩu phải số hoá tầng thu mua — Excel và hợp đồng giấy vẫn đủ để vận hành. Deadline 30/12/2026 chuyển đổi nhu cầu này từ “nên có” thành “bắt buộc phải có”.", {})]));

// ============================================================
// 5. PAIN POINTS
// ============================================================
push(H1("5. Ba pain point hệ thống và ánh xạ giải pháp"));
push(P("Ba đặc thù cấu trúc ở Mục 2 biểu hiện thành ba pain point cụ thể trong vận hành. Bảng dưới ánh xạ từng pain point sang cơ chế giải quyết của nền tảng và căn cứ pháp lý tương ứng."));
push(table(
  [2100, 3100, 2700, 1738],
  ["Pain point", "Biểu hiện thực tế", "Giải pháp AgriContract", "Căn cứ pháp lý"],
  [
    ["Thiếu bằng chứng hợp đồng", "Chỉ 30% liên kết bền chắc; phần còn lại dựa vào thoả thuận miệng hoặc tin nhắn không có giá trị pháp lý", "Hợp đồng điện tử với timestamp, lịch sử đàm phán bất biến, audit trail xuất được làm bằng chứng pháp lý hoặc nộp kiểm toán EUDR", "Luật GDĐT 2023, Điều 34"],
    ["Không có ký quỹ trung lập", "Không có bên thứ ba giữ tiền; bên có lợi thế thường trì hoãn thanh toán hoặc từ chối nhận hàng", "Ký quỹ khoá tiền theo điều khoản đã ký trước khi giao hàng; penalty tự động thực thi theo rate đã thoả thuận", "BLDS 2015, Điều 328; Luật TM 2005, Điều 300"],
    ["Thiếu thông tin đối tác", "Không có lịch sử giao dịch số hoá; HTX lần đầu hợp tác không có cơ sở đánh giá độ tin cậy", "Audit trail tích luỹ thành credit history; reputation scoring sau mỗi hợp đồng; INSPECTOR xác nhận chất lượng độc lập", "Luật GDĐT 2023, Điều 14.2"],
  ],
  { size: 18 }
));

// ============================================================
// 6. LONG TERM VISION
// ============================================================
push(H1("6. Tầm nhìn dài hạn — hạ tầng dữ liệu cho tín dụng nông nghiệp"));
push(P("57% doanh nghiệp vừa và nhỏ chính thức tại Việt Nam không tiếp cận được tài chính, chủ yếu vì thiếu tài sản thế chấp và không có lịch sử tín dụng có thể xác minh (We-Fi / OCB, 2022). AgriContract tích luỹ chính xác dữ liệu mà ngân hàng cần để đánh giá tín dụng: lịch sử hoàn thành hợp đồng, tỷ lệ giao hàng đúng hạn, kết quả kiểm định chất lượng, và điểm đánh giá từ đối tác. Đây là credit profile thực chất — không phải tài sản thế chấp, không phải báo cáo tài chính."));
push(P("BIDV đã tham gia nền tảng Agrichain blockchain với vai trò xác thực dữ liệu — không chỉ là bên tài trợ vốn. Đây là dịch chuyển có chủ đích từ cho vay theo tài sản thế chấp sang cho vay theo dữ liệu giao dịch. AgriContract xây dựng đúng lớp hạ tầng mà xu hướng này cần: dữ liệu uy tín tích luỹ được thiết kế để có thể xuất ra làm tham chiếu tín dụng cho bên thứ ba khi có đối tác chính thức."));
push(src("Vietnam.vn — Xu hướng ứng dụng ngân hàng số vào chuỗi cung ứng nông sản (4/4/2026); We-Fi / OCB — SME financing gap 68% in Vietnam (2022)."));

// ============================================================
// 7. LEGAL FRAMEWORK
// ============================================================
push(H1("7. Khung pháp lý tham chiếu"));
push(P("Toàn bộ luận cứ thị trường và thiết kế cơ chế của nền tảng đứng trên khung pháp lý sau. Bảng tổng hợp các văn bản và điều khoản được viện dẫn xuyên suốt tài liệu."));
push(table(
  [3100, 2900, 3638],
  ["Văn bản", "Điều khoản liên quan", "Ý nghĩa với nền tảng"],
  [
    ["Luật Giao dịch Điện tử 2023 (20/2023/QH15)", "Điều 34 — hợp đồng điện tử không bị phủ nhận giá trị pháp lý; Điều 14.2 — giá trị chứng cứ của thông điệp dữ liệu", "Hợp đồng ký trên nền tảng có giá trị pháp lý tương đương hợp đồng giấy; audit trail là bằng chứng hợp lệ trước toà và cho kiểm toán EUDR"],
    ["Nghị định 98/2018/NĐ-CP", "Điều 4 — hợp đồng liên kết lập thành văn bản; Điều 15 — lựa chọn phương thức giải quyết tranh chấp", "Văn bản điện tử đáp ứng Điều 4; hoà giải nội bộ hợp lệ theo Điều 15"],
    ["Luật Trọng tài Thương mại 2010 (54/2010/QH12)", "Điều 5 — thoả thuận trọng tài trước/sau tranh chấp; phán quyết chung thẩm", "Nếu cần leo thang, VIAC xử lý dựa trên audit trail nền tảng làm bằng chứng"],
    ["Bộ luật Dân sự 2015", "Điều 328 — ký quỹ; Điều 142 — đại diện theo uỷ quyền; Điều 403 — tự do giao kết; Điều 156 & 351 — bất khả kháng", "Cơ sở cho ký quỹ, xác minh thẩm quyền ký kết, và xử lý bất khả kháng"],
    ["Luật Thương mại 2005", "Điều 300 — phạt vi phạm; Điều 302 — bồi thường thiệt hại", "Penalty và bồi thường thực thi theo thoả thuận đã ký của hai bên"],
    ["Nghị định 52/2024/NĐ-CP & 88/2019/NĐ-CP", "52/2024 Điều 8 — trung gian thanh toán; 88/2019 — xử phạt lĩnh vực tiền tệ", "Nền tảng không giữ tiền thật; ngân hàng giữ tiền, nền tảng chỉ ra lệnh — không cần giấy phép trung gian thanh toán"],
    ["EU 2023/1115, sửa đổi 2025/2650", "EUDR — deforestation-free, truy xuất toạ độ GPS, cấm mass balance", "Xác định yêu cầu dữ liệu geolocation ở tầng thu mua từ HTX"],
    ["Quyết định 749/QĐ-TTg (2020)", "Chương trình Chuyển đổi số quốc gia — nông nghiệp là lĩnh vực ưu tiên", "Cơ sở chính sách cho số hoá quy trình thu mua nông sản"],
  ],
  { size: 18 }
));

// ============================================================
// 8. LIMITATIONS
// ============================================================
push(H1("8. Giới hạn của phân tích và phạm vi"));
push(P("Nêu rõ giới hạn là điều kiện để luận cứ giữ được độ tin cậy trước hội đồng. Các điểm dưới đây là ranh giới có chủ đích của phân tích này, không phải điểm mù bị bỏ sót."));
push(bullet([runs("Phạm vi sản phẩm giới hạn ở tầng hợp đồng. ", { bold: true }), runs("AgriContract không xử lý logistics, không phải sàn thương mại điện tử, không thay thế hệ thống kế toán doanh nghiệp. Nó giải quyết một vấn đề cụ thể: thiếu cơ chế tự thực thi trong giao dịch forward contract nông sản B2B.", {})]));
push(bullet([runs("EUDR chỉ là một mắt xích. ", { bold: true }), runs("Nền tảng cung cấp audit trail ở tầng thu mua từ HTX — một phần trong bức tranh compliance tổng thể, không phải toàn bộ giải pháp EUDR của doanh nghiệp xuất khẩu.", {})]));
push(bullet([runs("Phân tích dựa trên nguồn thứ cấp. ", { bold: true }), runs("Số liệu thị trường, giá và tranh chấp lấy từ báo cáo hiệp hội, cơ quan nhà nước và báo chí tại thời điểm tổng hợp; chưa qua khảo sát sơ cấp với HTX/doanh nghiệp thu mua để định lượng nhu cầu chi trả thực tế.", {})]));
push(bullet([runs("Quy mô thị trường khả dụng chưa được định lượng chính xác. ", { bold: true }), runs("Khoảng cách số hoá 20 điểm phần trăm và tỷ lệ 30% liên kết bền chắc chỉ ra dư địa, không phải ước tính doanh thu tiềm năng (TAM/SAM/SOM) đã được mô hình hoá.", {})]));
push(bullet([runs("Giả định triển khai qua hiệp hội chưa được xác nhận cam kết. ", { bold: true }), runs("Mô hình phân phối qua VICOFA/VRA/VINACAS là luận điểm chiến lược dựa trên tính trung lập của hiệp hội; chưa có thoả thuận triển khai chính thức tại thời điểm tài liệu.", {})]));
push(bullet([runs("Lúa gạo và điều ngoài phạm vi EUDR. ", { bold: true }), runs("Với hai ngành này, giá trị nền tảng đến từ ký quỹ, giải quyết tranh chấp và uy tín — luận cứ compliance không áp dụng.", {})]));

// ============================================================
// 9. SOURCES
// ============================================================
push(H1("9. Danh mục nguồn tham khảo"));
push(H3("Chính phủ & tổ chức quốc tế"));
[
  "Bộ Nông nghiệp và Môi trường — Tổng kết xuất khẩu 2025 (6/1/2026).",
  "EC Regulation (EU) 2025/2650 — Official Journal (23/12/2025).",
  "VIAC — Thống kê hoạt động giải quyết tranh chấp năm 2024.",
  "World Resources Institute — What Is the EUDR? (5/2026).",
  "We-Fi / OCB — SME financing gap Vietnam (2022).",
  "PSAV — Vietnam prioritizes digitalization of agriculture (Quyết định 749/QĐ-TTg).",
].forEach(s => push(bullet(s)));
push(H3("Hiệp hội ngành hàng"));
[
  "VICOFA — Báo cáo tổng kết niên vụ cà phê 2024–2025 (10/2025).",
  "VRA — Vietnam Rubber Industry International Conference 2025 (12/2025).",
  "VINACAS & Tổng cục Hải quan — Xuất khẩu điều 2025 (1/2026).",
].forEach(s => push(bullet(s)));
push(H3("Báo chí & chuyên gia"));
[
  "VTV.vn — Giá thu mua tăng cao, cơ hội và thách thức ngành cà phê Việt Nam (17/4/2024).",
  "Vietnamnet — 3 chiêu để hạn chế doanh nghiệp bẻ kèo, nông dân chạy làng (30/8/2024).",
  "Báo Pháp Luật TP.HCM — Nghịch lý xuất khẩu cà phê Việt (4/4/2026).",
  "Vietnam.vn — Banks partner with the digital agricultural supply chain (1/4/2026).",
  "Tạp chí Kinh tế Sài Gòn (20/6/2025).",
  "Tạp chí Kinh tế và Dự báo — Chuyển đổi số nông nghiệp (28/2/2025).",
].forEach(s => push(bullet(s)));
push(H3("Văn bản pháp luật"));
[
  "Luật Giao dịch Điện tử 2023 (Luật 20/2023/QH15, hiệu lực 1/7/2024).",
  "Nghị định 52/2024/NĐ-CP — Thanh toán không dùng tiền mặt (hiệu lực 1/7/2024).",
  "Nghị định 98/2018/NĐ-CP — Liên kết sản xuất và tiêu thụ nông sản.",
  "Luật Trọng tài Thương mại 2010 (Luật 54/2010/QH12).",
  "Bộ luật Dân sự 2015 — Điều 328, 142, 403, 156, 351.",
  "Luật Thương mại 2005 — Điều 300, 302.",
  "Nghị định 88/2019/NĐ-CP — Xử phạt vi phạm hành chính lĩnh vực tiền tệ.",
].forEach(s => push(bullet(s)));

push(new Paragraph({ spacing: { before: 300 }, border: { top: { style: BorderStyle.SINGLE, size: 6, color: BORDER, space: 6 } }, children: [new TextRun({ text: "— Hết tài liệu —", font: FONT, size: 19, color: MUTE, italics: true })] }));

// ============================================================
// DOC
// ============================================================
const doc = new Document({
  creator: "AgriContract",
  title: "AgriContract — Phân Tích Thị Trường & Luận Cứ Kinh Doanh v5.0",
  features: { updateFields: true },
  styles: {
    default: { document: { run: { font: FONT, size: 21, color: INK } } },
    paragraphStyles: [
      { id: "Heading1", name: "Heading 1", basedOn: "Normal", next: "Normal", quickFormat: true, run: { font: FONT, size: 30, bold: true, color: HEAD }, paragraph: { outlineLevel: 0 } },
      { id: "Heading2", name: "Heading 2", basedOn: "Normal", next: "Normal", quickFormat: true, run: { font: FONT, size: 25, bold: true, color: HEAD }, paragraph: { outlineLevel: 1 } },
    ],
  },
  numbering: {
    config: [{
      reference: "bullets",
      levels: [{ level: 0, format: LevelFormat.BULLET, text: "•", alignment: AlignmentType.LEFT, style: { run: { color: SUB }, paragraph: { indent: { left: 360, hanging: 220 } } } }],
    }],
  },
  sections: [{
    properties: { page: { size: { width: 11906, height: 16838 }, margin: { top: 1134, bottom: 1134, left: 1134, right: 1134 } } },
    headers: { default: new Header({ children: [new Paragraph({ alignment: AlignmentType.RIGHT, spacing: { after: 0 }, children: [new TextRun({ text: "AgriContract · Phân tích thị trường", font: FONT, size: 16, color: MUTE })] })] }) },
    footers: { default: new Footer({ children: [new Paragraph({ alignment: AlignmentType.CENTER, spacing: { before: 0 }, children: [new TextRun({ text: "v5.0 · Tháng 7/2026 · Trang ", font: FONT, size: 16, color: MUTE }), new TextRun({ children: [PageNumber.CURRENT], font: FONT, size: 16, color: MUTE }), new TextRun({ text: "/", font: FONT, size: 16, color: MUTE }), new TextRun({ children: [PageNumber.TOTAL_PAGES], font: FONT, size: 16, color: MUTE })] })] }) },
    children: body,
  }],
});

Packer.toBuffer(doc).then(buf => {
  fs.writeFileSync("/tmp/AgriContract_01_PhanTichThiTruong_v5.docx", buf);
  console.log("written", buf.length, "bytes");
});
