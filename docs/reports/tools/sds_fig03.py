"""SDS (Report-4) I.2: Figure 3 redrawn from the frontend folders (figures/sds/fig03_frontend_packages.py).
Runs once, after sds_fig02.py. Content only."""
from pathlib import Path

import docx
from docx.oxml.ns import qn

from docxedit import EMU_PER_CM, append_row, find, next_table, png_size

REPO = Path(__file__).resolve().parents[3]
DOC = REPO / "docs/reports/Report-4_SDS.docx"
png = REPO / "docs/reports/figures/sds/fig03_frontend_packages.png"
d = docx.Document(DOC)
if any("Figure 3 redrawn" in "".join(t.text or "" for t in tr.iter(qn("w:t")))
       for tr in next_table(find(d, "Change Log")).findall(qn("w:tr"))):
    raise SystemExit("already applied")
pic = find(d, "Figure 3. Frontend package diagram").getprevious()
rid, _ = d.part.get_or_add_image(str(png))
next(pic.iter(qn("a:blip"))).set(qn("r:embed"), rid)
w, h = png_size(png)
cx = int(16.0 * EMU_PER_CM)
cy = int(cx * h / w)
for ext in [*pic.iter(qn("wp:extent")), *pic.iter(qn("a:ext"))]:
    if ext.get("cx") is not None:
        ext.set("cx", str(cx))
        ext.set("cy", str(cy))
append_row(next_table(find(d, "Change Log")), [
    "10/11/2026", "M", "Group 6",
    "Figure 3 redrawn from the folders of frontend/src (editable .drawio); auth shown as the reference feature"])
d.save(DOC)
print("saved")
