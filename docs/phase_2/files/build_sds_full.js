const { writeDocx } = require("./docx_output.js");
const { D, cover, tocRange, partDivider, endMark, buildDoc, setHeadingOffset } = require("./acdocx.js");
const { Packer } = D;

// Demote internal H1/H2/H3 by one level under each true Part divider.
setHeadingOffset(1);
const p1 = require("./build04.js").body;
const p2 = require("./build05.js").body;
const p3 = require("./build06.js").body;
const p4 = require("./build07.js").body;
const p5 = require("./build08.js").body;
setHeadingOffset(0);

const body = [
  ...cover(
    "AGRICONTRACT",
    "Software Design Specification — Phase 2",
    "State, API, event, schema, invariant, migration và implementation contract hợp nhất",
    ["Tài liệu thiết kế chi tiết hợp nhất", "Phiên bản final · Tháng 7/2026"]
  ),
  ...tocRange("1-3"),
  ...partDivider("AGRICONTRACT · SDS", "Phần 1 — Nền tảng, scope, ownership và common contracts").slice(1),
  ...p1,
  ...partDivider("AGRICONTRACT · SDS", "Phần 2 — Contract, milestone escrow và monetary ledger"),
  ...p2,
  ...partDivider("AGRICONTRACT · SDS", "Phần 3 — Inspection, reputation và audit evidence"),
  ...p3,
  ...partDivider("AGRICONTRACT · SDS", "Phần 4 — Product, file và pricing"),
  ...p4,
  ...partDivider("AGRICONTRACT · SDS", "Phần 5 — Peripheral services, frozen contracts và release verification"),
  ...p5,
  endMark(),
];

const doc = buildDoc(body, {
  title: "AgriContract — Software Design Specification Phase 2 — Final",
  headerText: "AgriContract · SDS Phase 2",
  footerText: "Final · Tháng 7/2026",
});

Packer.toBuffer(doc).then(buf => writeDocx("/tmp/AgriContract_SDS_final.docx", buf));
