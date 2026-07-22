const { renderMarkdown, extractTopLevelSections, readContent } = require("./md_to_acdocx.js");
const source = readContent("sds_final.md");
// SDS Part 5 internal module: user/notification/analytics, frozen contracts,
// Verification Matrix, current scope, limitations, future work and bibliography.
const body = renderMarkdown(extractTopLevelSections(source, 9, 15));
module.exports = { body };
