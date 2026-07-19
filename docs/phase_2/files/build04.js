const { renderMarkdown, extractTopLevelSections, readContent } = require("./md_to_acdocx.js");
const source = readContent("sds_final.md");
// SDS Part 1 internal module: source-of-truth status, scope, ownership, common contracts.
const body = renderMarkdown(extractTopLevelSections(source, 0, 3));
module.exports = { body };
