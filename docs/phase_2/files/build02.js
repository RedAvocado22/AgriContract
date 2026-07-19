const { writeDocx } = require("./docx_output.js");
const { D, cover, tocRange, endMark, buildDoc } = require("./acdocx.js");
const { renderMarkdown, readContent } = require("./md_to_acdocx.js");
const { Packer } = D;

const body = [
  ...cover(
    "AGRICONTRACT",
    "Giải pháp và mô hình nghiệp vụ Phase 2",
    "Contract layer, golden flow, attribution/remedy, escrow, reputation và evidence",
    ["Tài liệu đồ án tốt nghiệp", "Phiên bản final · Tháng 7/2026"]
  ),
  ...tocRange("1-3"),
  ...renderMarkdown(readContent("solution_final.md")),
  endMark(),
];

const doc = buildDoc(body, {
  title: "AgriContract — Giải pháp và mô hình nghiệp vụ Phase 2 — Final",
  headerText: "AgriContract · Giải pháp và mô hình",
  footerText: "Final · Tháng 7/2026",
});

Packer.toBuffer(doc).then(buf => writeDocx("/tmp/AgriContract_02_GiaiPhap_MoHinh_final.docx", buf));
