"""SRS (Report-3) 3.1: the three ERD parts redrawn in crow's foot notation (figures/srs/erd_a|b|c.png).

The three pictures share one paragraph; they are replaced in the order the 3.1 text names the parts: (a) identity,
product and listing, (b) contract, milestone and money, (c) dispute, inspection, reputation and audit.
Runs once on the docx that already has srs_uc43.py applied. Content only.
"""
from pathlib import Path

import docx
from docx.oxml.ns import qn

from docxedit import EMU_PER_CM, append_row, find, next_table, png_size, text_of

REPO = Path(__file__).resolve().parents[3]
DOC = REPO / "docs/reports/Report-3_Software Requirement Specification.docx"
FIG = REPO / "docs/reports/figures/srs"
d = docx.Document(DOC)

intro = find(d, "The conceptual data model of AgriContract")
pic = intro.getnext()
drawings = list(pic.iter(qn("w:drawing")))
if len(drawings) != 3:
    raise SystemExit(f"expected 3 pictures after the 3.1 text, found {len(drawings)}")
if any("erd_a" in str(r.target_ref) for r in d.part.rels.values()):
    raise SystemExit("already applied")
TEXT_W = 16.0  # cm; 800 px fills the text width, as for the use case diagrams
for drawing, name in zip(drawings, ("erd_a", "erd_b", "erd_c")):
    png = FIG / f"{name}.png"
    rid, _ = d.part.get_or_add_image(str(png))
    next(drawing.iter(qn("a:blip"))).set(qn("r:embed"), rid)
    w, h = png_size(png)
    cx = int(TEXT_W * EMU_PER_CM)
    cy = int(cx * h / w)
    for ext in [*drawing.iter(qn("wp:extent")), *drawing.iter(qn("a:ext"))]:
        if ext.get("cx") is not None:
            ext.set("cx", str(cx))
            ext.set("cy", str(cy))
    print(f"{name} {cx / EMU_PER_CM:.1f} x {cy / EMU_PER_CM:.1f} cm")

append_row(next_table(find(d, "Change Log")), [
    "10/11/2026", "M", "Group 6",
    "ERD 3.1 redrawn in crow's foot notation (editable .drawio in docs/reports/figures/srs); the three parts now "
    "follow the split the 3.1 text names (a) identity, product and listing, (b) contract, milestone and money, "
    "(c) dispute, inspection, reputation and audit; entities, keys and cardinalities unchanged"])
d.save(DOC)
print("saved")
