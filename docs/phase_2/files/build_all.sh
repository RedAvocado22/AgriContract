#!/usr/bin/env bash
set -euo pipefail

npm install
npm test
node build01.js
node build02.js
node build03_part2.js
node build_sds_full.js

mkdir -p generated
cp /tmp/AgriContract_01_PhanTichThiTruong_final.docx generated/
cp /tmp/AgriContract_02_GiaiPhap_MoHinh_final.docx generated/
cp /tmp/AgriContract_Architecture_final.docx generated/
cp /tmp/AgriContract_SDS_final.docx generated/

echo "Generated 4 final DOCX files in $(pwd)/generated"
