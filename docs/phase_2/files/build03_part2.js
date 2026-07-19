const { writeDocx } = require("./docx_output.js");
const { D, cover, tocRange, endMark, buildDoc } = require("./acdocx.js");
const { body: architectureBody } = require("./build03_part1.js");
const { Packer } = D;

const body = [
  ...cover(
    "AGRICONTRACT",
    "Kiến trúc hệ thống Phase 2",
    "Boundary, ownership, interaction, trust model, state choreography và quality attributes",
    ["Tài liệu kiến trúc đồ án", "Phiên bản final · Tháng 7/2026"]
  ),
  ...tocRange("1-3"),
  ...architectureBody,
  endMark(),
];

const doc = buildDoc(body, {
  title: "AgriContract — Kiến trúc hệ thống Phase 2 — Final",
  headerText: "AgriContract · Architecture",
  footerText: "Final · Tháng 7/2026",
});

Packer.toBuffer(doc).then(buf => writeDocx("/tmp/AgriContract_Architecture_final.docx", buf));
