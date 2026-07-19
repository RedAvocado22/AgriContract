const { renderMarkdown, extractTopLevelSections, readContent } = require("./md_to_acdocx.js");
const source = readContent("sds_final.md");
// SDS Part 4 internal module: product, file and pricing.
const body = renderMarkdown(extractTopLevelSections(source, 8, 8));
module.exports = { body };
