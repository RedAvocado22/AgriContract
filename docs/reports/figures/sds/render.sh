#!/usr/bin/env bash
# Render every SDS diagram to PNG (for the docx) and SVG (for the .drawio wrappers).
# Needs Java and a PlantUML jar: PLANTUML_JAR=/path/to/plantuml.jar ./render.sh
# No Graphviz needed: vp-style.iuml selects the ELK layout bundled with PlantUML.
set -euo pipefail
cd "$(dirname "$0")"
: "${PLANTUML_JAR:?set PLANTUML_JAR to the plantuml jar path}"
java -Djava.awt.headless=true -DPLANTUML_LIMIT_SIZE=16384 -jar "$PLANTUML_JAR" -tpng fig*.puml
java -Djava.awt.headless=true -DPLANTUML_LIMIT_SIZE=16384 -jar "$PLANTUML_JAR" -tsvg fig*.puml
# Trim the empty margin some layouts leave around the PNGs.
python3 - <<'PY'
import glob
from PIL import Image, ImageChops
for f in glob.glob("fig*.png"):
    im = Image.open(f).convert("RGB")
    box = ImageChops.difference(im, Image.new("RGB", im.size, "white")).getbbox()
    if box:
        pad = 20
        box = (max(box[0] - pad, 0), max(box[1] - pad, 0), min(box[2] + pad, im.width), min(box[3] + pad, im.height))
        im.crop(box).save(f)
PY
python3 build_drawio.py
