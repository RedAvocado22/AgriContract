"""SDS (Report-4) I.1: Figure 1 redrawn as a UML component diagram (figures/sds/fig01_architecture.py); object
storage is SeaweedFS (S3 API), which replaced MinIO. Runs once, after sds_erd.py. Content only."""
from pathlib import Path

import docx
from docx.oxml.ns import qn

from docxedit import EMU_PER_CM, append_row, find, next_table, png_size, set_cell_text, set_par_text, text_of

REPO = Path(__file__).resolve().parents[3]
DOC = REPO / "docs/reports/Report-4_SDS.docx"
FIG = REPO / "docs/reports/figures/sds"
d = docx.Document(DOC)

hits = [p for p in d.element.body.iter(qn("w:p")) if "MinIO" in text_of(p)]
if not hits:
    raise SystemExit("already applied")
for p in hits:
    set_par_text(p, text_of(p).replace("MinIO (S3 API)", "SeaweedFS (S3 API)").replace("MinIO", "SeaweedFS"))

cap = find(d, "Figure 1. AgriContract overall architecture")
pic = cap.getprevious()
png = FIG / "fig01_architecture.png"
rid, _ = d.part.get_or_add_image(str(png))
next(pic.iter(qn("a:blip"))).set(qn("r:embed"), rid)
w, h = png_size(png)
cx = int(16.0 * EMU_PER_CM)
cy = int(cx * h / w)
for ext in [*pic.iter(qn("wp:extent")), *pic.iter(qn("a:ext"))]:
    if ext.get("cx") is not None:
        ext.set("cx", str(cx))
        ext.set("cy", str(cy))
print(f"fig01 {cx / EMU_PER_CM:.1f} x {cy / EMU_PER_CM:.1f} cm; MinIO fixed in {len(hits)} paragraphs")

append_row(next_table(find(d, "Change Log")), [
    "10/11/2026", "M", "Group 6",
    "Figure 1 redrawn as a UML component diagram (editable .drawio); object storage is SeaweedFS (S3 API), which "
    "replaced MinIO"])
d.save(DOC)
print("saved")
