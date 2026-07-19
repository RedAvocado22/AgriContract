const { writeDocx } = require("./docx_output.js");
const { D, cover, tocRange, endMark, buildDoc } = require("./acdocx.js");
const { renderMarkdown, readContent } = require("./md_to_acdocx.js");
const { Packer } = D;

const body = [
  ...cover(
    "AGRICONTRACT",
    "Phân tích thị trường và bài toán",
    "Luận cứ thị trường, pain point và phạm vi giá trị của contract layer nông sản B2B",
    ["Tài liệu đồ án tốt nghiệp", "Phiên bản final · Tháng 7/2026"]
  ),
  ...tocRange("1-3"),
  ...renderMarkdown(readContent("market_final.md")),
  endMark(),
];

const doc = buildDoc(body, {
  title: "AgriContract — Phân tích thị trường và bài toán — Final",
  headerText: "AgriContract · Phân tích thị trường",
  footerText: "Final · Tháng 7/2026",
});

Packer.toBuffer(doc).then(buf => writeDocx("/tmp/AgriContract_01_PhanTichThiTruong_final.docx", buf));
