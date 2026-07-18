const fs = require("fs");
const path = require("path");
const { execFileSync } = require("child_process");

/**
 * Write a generated DOCX, update its TOC/index fields through LibreOffice,
 * then atomically replace the raw file with the materialized result.
 */
function writeDocx(outputPath, buffer) {
  fs.writeFileSync(outputPath, buffer);

  const materializer = path.join(__dirname, "materialize_toc.py");
  const tempOutput = outputPath.replace(/\.docx$/i, ".materialized.docx");
  const python = process.env.LIBREOFFICE_PYTHON || "/usr/bin/python3";

  try {
    execFileSync(python, [materializer, outputPath, tempOutput], {
      stdio: "inherit",
      env: process.env,
    });
    fs.renameSync(tempOutput, outputPath);
  } catch (error) {
    if (fs.existsSync(tempOutput)) fs.unlinkSync(tempOutput);
    throw new Error(
      `Generated ${outputPath}, but could not materialize its TOC. ` +
      `Install LibreOffice + Python UNO or set LIBREOFFICE_PYTHON. Cause: ${error.message}`
    );
  }

  console.log("written", outputPath, fs.statSync(outputPath).size, "bytes (TOC materialized)");
}

module.exports = { writeDocx };
