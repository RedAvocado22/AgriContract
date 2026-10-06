const fs = require("fs");
const path = require("path");
const { execFileSync } = require("child_process");

/**
 * Write a generated DOCX, update its TOC/index fields through LibreOffice,
 * then atomically replace the raw file with the materialized result.
 */
function writeDocx(outputPath, buffer) {
  const outputDir = process.env.DOCX_OUTPUT_DIR;
  const targetPath = outputDir
    ? path.join(outputDir, path.basename(outputPath))
    : outputPath;
  fs.mkdirSync(path.dirname(targetPath), { recursive: true });
  fs.writeFileSync(targetPath, buffer);

  const materializer = path.join(__dirname, "materialize_toc.py");
  const tempOutput = targetPath.replace(/\.docx$/i, ".materialized.docx");
  const python = process.env.LIBREOFFICE_PYTHON || "/usr/bin/python3";

  try {
    execFileSync(python, [materializer, targetPath, tempOutput], {
      stdio: "inherit",
      env: process.env,
    });
    fs.renameSync(tempOutput, targetPath);
  } catch (error) {
    if (fs.existsSync(tempOutput)) fs.unlinkSync(tempOutput);
    throw new Error(
      `Generated ${outputPath}, but could not materialize its TOC. ` +
      `Install LibreOffice + Python UNO or set LIBREOFFICE_PYTHON. Cause: ${error.message}`
    );
  }

  console.log("written", targetPath, fs.statSync(targetPath).size, "bytes (TOC materialized)");
}

module.exports = { writeDocx };
