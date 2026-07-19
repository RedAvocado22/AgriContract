const { renderMarkdown, extractTopLevelSections, readContent } = require("./md_to_acdocx.js");
const source = readContent("sds_final.md");
// SDS Part 5 internal module: user/notification/analytics, frozen contracts,
// Verification Matrix, migration, limitations and superseded terminology.
const body = renderMarkdown(extractTopLevelSections(source, 9, 13));
module.exports = { body };
