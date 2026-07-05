const fs = require("fs");
const { D, cover, tocRange, partDivider, endMark, buildDoc, setHeadingOffset } = require("./acdocx.js");
const { Packer } = D;

// Demote heading levels by 1 for every part's internal content, so each part's
// own H1 ("1. Phạm vi phần X", "2. contract-service"...) nests as H2 under the
// part's true H1 divider, and each part's H2 ("2.1 Mô hình miền"...) nests as H3.
// Must be set BEFORE requiring each part module, since H1()/H2() bake in the
// offset at push()-time (module load time), not lazily.
setHeadingOffset(1);

const p1 = require("./build04.js").body;
const p2 = require("./build05.js").body;
const p3 = require("./build06.js").body;
const p4 = require("./build07.js").body;
const p5 = require("./build08.js").body;

// Reset offset — cover/toc/partDivider are unaffected by HOFF (they don't call
// H1()/H2()), but reset anyway for hygiene in case anything else runs after.
setHeadingOffset(0);

const body = [];
const push = (...x) => x.forEach(e => body.push(e));

push(...cover(
  "AGRICONTRACT",
  "Software Design Specification (SDS)",
  "Tài liệu thiết kế chi tiết đầy đủ hệ thống — 5 phần hợp nhất, không tách file",
  ["Tài liệu nội bộ — Đồ án Tốt nghiệp", "Phiên bản 1.0 · Bản đầy đủ · Tháng 7/2026"]
));
// Range 1-3 vì nội dung mỗi phần đã bị demote 1 cấp (H1->H2, H2->H3) — giữ TOC
// hiển thị đủ Phần / Chương / Mục con, thay vì chỉ Phần + Chương.
push(...tocRange("1-3"));

push(...partDivider("AGRICONTRACT · SDS", "Phần 1 — Nền tảng thiết kế và chuẩn dùng chung"));
push(...p1);
push(...partDivider("AGRICONTRACT · SDS", "Phần 2 — Cụm lõi: contract-service · escrow-service · bank-service"));
push(...p2);
push(...partDivider("AGRICONTRACT · SDS", "Phần 3 — Cụm giám định & tin cậy: inspection-service · reputation-service · audit-service"));
push(...p3);
push(...partDivider("AGRICONTRACT · SDS", "Phần 4 — Cụm dữ liệu & hỗ trợ: product-service · file-service · pricing-service"));
push(...p4);
push(...partDivider("AGRICONTRACT · SDS", "Phần 5 — Cụm ngoại vi & tổng hợp: user-service · notification-service · analytics-service + Phụ lục"));
push(...p5);

push(endMark());

const doc = buildDoc(body, {
  title: "AgriContract — Software Design Specification (SDS) — Bản đầy đủ v1.0",
  headerText: "AgriContract · SDS — Bản đầy đủ",
  footerText: "SDS v1.0 · Bản đầy đủ · Tháng 7/2026",
});
Packer.toBuffer(doc).then(buf => {
  fs.writeFileSync("/tmp/AgriContract_SDS_Full_v1.docx", buf);
  console.log("written", buf.length);
});
