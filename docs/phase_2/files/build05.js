const { renderMarkdown, extractTopLevelSections, readContent } = require("./md_to_acdocx.js");
const source = readContent("sds_final.md");
// SDS Part 2 internal module: contract, milestone/escrow and bank monetary contracts.
const body = renderMarkdown(extractTopLevelSections(source, 4, 6));
module.exports = { body };
