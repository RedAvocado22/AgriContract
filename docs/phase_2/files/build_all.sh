#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR=$(cd "$(dirname "$0")" && pwd)
REPO_ROOT=$(cd "$SCRIPT_DIR/../../.." && pwd)
BUILD_ROOT=${AGRICONTRACT_TMP_DIR:-/tmp/agricontract-doc-sync}
DOCX_DIR="$BUILD_ROOT/build"
mkdir -p "$DOCX_DIR"

python3 "$SCRIPT_DIR/validate_final.py"
cd "$SCRIPT_DIR"
npm ci
npm test
DOCX_OUTPUT_DIR="$DOCX_DIR" node "$SCRIPT_DIR/build01.js"
DOCX_OUTPUT_DIR="$DOCX_DIR" node "$SCRIPT_DIR/build02.js"
DOCX_OUTPUT_DIR="$DOCX_DIR" node "$SCRIPT_DIR/build03_part2.js"
DOCX_OUTPUT_DIR="$DOCX_DIR" node "$SCRIPT_DIR/build_sds_full.js"
python3 "$SCRIPT_DIR/verify_built_docx.py" "$DOCX_DIR"

cp "$DOCX_DIR/AgriContract_01_PhanTichThiTruong_final.docx" "$REPO_ROOT/docs/phase_2/"
cp "$DOCX_DIR/AgriContract_02_GiaiPhap_MoHinh_final.docx" "$REPO_ROOT/docs/phase_2/"
cp "$DOCX_DIR/AgriContract_Architecture_final.docx" "$REPO_ROOT/docs/phase_2/"
cp "$DOCX_DIR/AgriContract_SDS_final.docx" "$REPO_ROOT/docs/phase_2/"

echo "Generated and copied 4 final DOCX files from $DOCX_DIR to $REPO_ROOT/docs/phase_2"
