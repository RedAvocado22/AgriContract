#!/usr/bin/env bash
set -euo pipefail

npm install
node build01.js
node build02.js
node build03_part2.js
node build04.js
node build05.js
node build06.js
node build07.js
node build08.js
node build_sds_full.js

mkdir -p generated
cp /tmp/AgriContract_01_PhanTichThiTruong_v5.docx generated/
cp /tmp/AgriContract_02_GiaiPhap_MoHinh_v5.docx generated/
cp /tmp/AgriContract_Architecture_v2.docx generated/
cp /tmp/AgriContract_04_SDS_Part1_v1.docx generated/
cp /tmp/AgriContract_05_SDS_Part2_v1.docx generated/
cp /tmp/AgriContract_06_SDS_Part3_v1.docx generated/
cp /tmp/AgriContract_07_SDS_Part4_v1.docx generated/
cp /tmp/AgriContract_08_SDS_Part5_v1.docx generated/
cp /tmp/AgriContract_SDS_Full_v1.docx generated/

echo "Generated 9 DOCX files in $(pwd)/generated"
