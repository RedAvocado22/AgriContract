#!/usr/bin/env python3
"""Verify that exactly four generated DOCX files contain all final sections."""

from pathlib import Path
import sys
import zipfile


EXPECTED = {
    "AgriContract_01_PhanTichThiTruong_final.docx",
    "AgriContract_02_GiaiPhap_MoHinh_final.docx",
    "AgriContract_Architecture_final.docx",
    "AgriContract_SDS_final.docx",
}
REQUIRED = ("Current Scope", "Known Limitations", "Future Work", "Danh m\u1ee5c ngu\u1ed3n tham kh\u1ea3o")
FORBIDDEN = ("source of truth", "nguồn sự thật", "Architecture/SDS", "_Nguồn:")


def main() -> None:
    if len(sys.argv) != 2:
        raise SystemExit("usage: verify_built_docx.py OUTPUT_DIR")
    output_dir = Path(sys.argv[1])
    actual = {path.name for path in output_dir.glob("*.docx")}
    if actual != EXPECTED:
        raise SystemExit(f"expected exactly four final DOCX files, got: {sorted(actual)}")

    for filename in sorted(EXPECTED):
        path = output_dir / filename
        if path.stat().st_size < 10_000:
            raise SystemExit(f"DOCX is unexpectedly small: {path}")
        with zipfile.ZipFile(path) as archive:
            xml = archive.read("word/document.xml").decode("utf-8")
        for heading in REQUIRED:
            if heading not in xml:
                raise SystemExit(f"{filename} is missing rendered section: {heading}")
        for phrase in FORBIDDEN:
            if phrase.lower() in xml.lower():
                raise SystemExit(f"{filename} contains forbidden phrase: {phrase}")

    print("verified: exactly four DOCX files with Current Scope, Known Limitations and Future Work")


if __name__ == "__main__":
    main()
