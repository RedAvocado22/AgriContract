const { renderMarkdown, readContent } = require("./md_to_acdocx.js");

// Architecture content module. Kept separate to preserve the repository's
// two-stage build03 structure while using acdocx for every rendered element.
const body = renderMarkdown(readContent("architecture_final.md"));

module.exports = { body };
