"""SRS (Report-3) 4.3: the nine use case diagrams redrawn in Visual Paradigm notation (figures/srs/uc_*.png).

Runs once on the docx that already has srs_bf_d42.py applied. Content only; formatting of the edited paragraph and
of the change log row is kept.
"""
from pathlib import Path

import docx
from docx.oxml.ns import qn

from docxedit import append_row, find, next_table, png_size, replace_image, set_par_text, text_of

REPO = Path(__file__).resolve().parents[3]
DOC = REPO / "docs/reports/Report-3_SRS_1_7.docx"
FIG = REPO / "docs/reports/figures/srs"
d = docx.Document(DOC)
body = d.element.body

notation = find(d, "Notation (UML 2.5)")
if "generalization" in text_of(notation):
    raise SystemExit("already applied")


def text_area(el):
    """(width, height) in cm of the text area of the section that contains el."""
    n = el
    while n is not None:
        sect = n.find(f"{qn('w:pPr')}/{qn('w:sectPr')}") if n.tag == qn("w:p") else None
        if sect is not None:
            break
        n = n.getnext()
    if n is None:
        sect = body.find(qn("w:sectPr"))
    pg, mar = sect.find(qn("w:pgSz")), sect.find(qn("w:pgMar"))
    tw = lambda v: int(v) / 567.0  # twips -> cm
    w = tw(pg.get(qn("w:w"))) - tw(mar.get(qn("w:left"))) - tw(mar.get(qn("w:right")))
    h = tw(pg.get(qn("w:h"))) - tw(mar.get(qn("w:top"))) - tw(mar.get(qn("w:bottom")))
    return w, h


def picture_after(el):
    n = el.getnext()
    while n is not None:
        if any(True for _ in n.iter(qn("w:drawing"))):
            return n
        n = n.getnext()
    raise KeyError("no picture after " + text_of(el)[:40])


for heading, png in [("4.3.1 UCs for Guest", "uc_guest"), ("4.3.2 UCs for Buyer", "uc_buyer"),
                     ("4.3.3 UCs for Seller", "uc_seller"), ("4.3.4 UCs for Contract Parties", "uc_parties"),
                     ("4.3.5 UCs for Inspector", "uc_inspector"), ("4.3.6 UCs for Operator", "uc_operator"),
                     ("4.3.7 UCs for Admin", "uc_admin"), ("4.3.8 UCs for Arbitrator", "uc_arbitrator"),
                     ("4.3.9 UCs for External Verifier", "uc_verifier")]:
    pic = picture_after(find(d, heading))
    w, h = text_area(pic)
    # one scale for every diagram (the widest, 800 px, fills the text width), so the text prints at one size
    px = png_size(FIG / f"{png}.png")[0] * 96 / 200
    size = replace_image(d, pic, FIG / f"{png}.png", max_w_cm=min(w, px * w / 800), max_h_cm=h - 3.0,
                         full_width=True)
    print(f"{png:14s} {size[0]:.1f} x {size[1]:.1f} cm")

set_par_text(notation,
    "Notation (UML 2.5): a solid line joins an actor to a use case the actor starts. A dashed «include» arrow goes "
    "from a base use case to a use case it always performs. A dashed «extend» arrow goes from an optional use case "
    "to the base use case it extends, and the extension runs only under its condition. In 4.3.4 a solid line with a "
    "hollow triangle (generalization) goes from Buyer and from Seller to the abstract Contract Party actor: both "
    "start every use case of Contract Party.")

append_row(next_table(find(d, "Change Log")), [
    "10/11/2026", "M", "Group 6",
    "Use case diagrams 4.3.1-4.3.9 redrawn in Visual Paradigm notation (editable .drawio in "
    "docs/reports/figures/srs); 4.3.4 uses an abstract Contract Party actor with generalization from Buyer and "
    "Seller; 4.3.6 UC29 is the Operator's lock retry (D42)"])

d.save(DOC)
print("saved")
