const { renderMarkdown, extractTopLevelSections, readContent } = require("./md_to_acdocx.js");
const source = readContent("sds_final.md");
// SDS Part 3 internal module: inspection, reputation and audit/evidence.
const body = renderMarkdown(extractTopLevelSections(source, 7, 7));
module.exports = { body };
