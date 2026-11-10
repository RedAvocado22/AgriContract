"""Content-only edits to the report .docx files: text, table rows and images, never styles.

* Text: the first run keeps its run properties (font, size, bold...), the other runs are removed, so a rewritten
  paragraph or cell looks exactly like before.
* Table rows: extra rows are deep copies of an existing data row (same borders, shading, widths), surplus rows are
  removed.
* Images: the new picture is added as a new image part and the drawing points at it; the frame keeps its width
  (capped to the text width) and takes the new picture's aspect ratio, so the picture is never stretched.
"""
from __future__ import annotations

import copy
import struct
from pathlib import Path

from docx.oxml.ns import qn

EMU_PER_CM = 360000


def body_elements(doc):
    return list(doc.element.body.iterchildren())


def text_of(el) -> str:
    return "".join(t.text or "" for t in el.iter(qn("w:t")))


def find(doc, starts_with: str, after=None, tag="w:p"):
    """First body element (paragraph or table) whose text starts with the given text."""
    els = body_elements(doc)
    start = els.index(after) + 1 if after is not None else 0
    for el in els[start:]:
        if el.tag == qn(tag) and text_of(el).strip().startswith(starts_with):
            return el
    raise KeyError(starts_with)


def next_table(el):
    n = el.getnext()
    while n is not None and n.tag != qn("w:tbl"):
        n = n.getnext()
    return n


def set_par_text(p, text: str):
    """Replaces the text of a w:p, keeping the first run's formatting. '\\n' becomes a line break."""
    runs = p.findall(qn("w:r"))
    if not runs:
        raise ValueError("paragraph has no run to copy the formatting from")
    first = runs[0]
    for r in runs[1:]:
        p.remove(r)
    for h in p.findall(qn("w:hyperlink")):
        p.remove(h)
    rpr = first.find(qn("w:rPr"))
    for child in list(first):
        if child is not rpr:
            first.remove(child)
    for i, part in enumerate(text.split("\n")):
        if i:
            first.append(first.makeelement(qn("w:br"), {}))
        t = first.makeelement(qn("w:t"), {})
        t.text = part
        t.set("{http://www.w3.org/XML/1998/namespace}space", "preserve")
        first.append(t)


def set_cell_text(tc, text: str):
    """One paragraph per cell, formatted like the cell's first paragraph."""
    paras = tc.findall(qn("w:p"))
    for p in paras[1:]:
        tc.remove(p)
    p = paras[0]
    if not p.findall(qn("w:r")):
        r = p.makeelement(qn("w:r"), {})
        p.append(r)
    set_par_text(p, text)


def set_table_rows(tbl, rows: list[list[str]], header_rows: int = 1, template_row: int = 1):
    """Rewrites the data rows of a table; header rows stay as they are."""
    trs = tbl.findall(qn("w:tr"))
    template = copy.deepcopy(trs[template_row])
    for tr in trs[header_rows:]:
        tbl.remove(tr)
    for values in rows:
        tr = copy.deepcopy(template)
        tcs = tr.findall(qn("w:tc"))
        if len(tcs) != len(values):
            raise ValueError(f"row has {len(tcs)} cells, got {len(values)} values: {values}")
        for tc, v in zip(tcs, values):
            set_cell_text(tc, v)
        tbl.append(tr)


def png_size(path: Path) -> tuple[int, int]:
    with open(path, "rb") as f:
        head = f.read(24)
    return struct.unpack(">II", head[16:24])


def replace_image(doc, p, png: Path, max_w_cm: float = 15.9, max_h_cm: float = 23.0, full_width: bool = False):
    """Points the first picture of paragraph p at png; keeps the frame width, follows the png aspect ratio."""
    blip = next(p.iter(qn("a:blip")))
    rid, _ = doc.part.get_or_add_image(str(png))
    blip.set(qn("r:embed"), rid)
    extent = next(p.iter(qn("wp:extent")))
    cx = int(extent.get("cx"))
    w, h = png_size(png)
    cx = int(max_w_cm * EMU_PER_CM) if full_width else min(cx, int(max_w_cm * EMU_PER_CM))
    cy = int(cx * h / w)
    if cy > max_h_cm * EMU_PER_CM:
        cy = int(max_h_cm * EMU_PER_CM)
        cx = int(cy * w / h)
    extent.set("cx", str(cx))
    extent.set("cy", str(cy))
    for ext in p.iter(qn("a:ext")):
        if ext.get("cx") is not None:
            ext.set("cx", str(cx))
            ext.set("cy", str(cy))
    return cx / EMU_PER_CM, cy / EMU_PER_CM


def insert_copy_after(anchor, source):
    """Inserts a deep copy of source right after anchor and returns it (a picture paragraph keeps its frame)."""
    new = copy.deepcopy(source)
    anchor.addnext(new)
    # a copied drawing must not share its id with the original
    for docpr in new.iter(qn("wp:docPr")):
        docpr.set("id", str(int(docpr.get("id")) + 1000))
    return new


def fill_change_log(tbl, values: list[str]):
    """Writes into the first empty row of the change log table."""
    trs = tbl.findall(qn("w:tr"))
    for k, tr in enumerate(trs[1:], start=1):
        if not text_of(tr).strip():
            above = trs[k - 1].findall(qn("w:tc"))
            for i, (tc, v) in enumerate(zip(tr.findall(qn("w:tc")), values)):
                p = tc.find(qn("w:p"))
                if not p.findall(qn("w:r")):
                    # empty cell: take the run formatting of the same column in the row above
                    p.append(copy.deepcopy(above[i].find(qn("w:p")).find(qn("w:r"))))
                set_par_text(p, v)
            return
    raise ValueError("no empty change log row left")


def append_row(tbl, values: list[str]):
    """Adds a row after the last one, as a copy of it (same borders, shading and run formatting)."""
    last = tbl.findall(qn("w:tr"))[-1]
    tr = copy.deepcopy(last)
    for tc, v in zip(tr.findall(qn("w:tc")), values):
        set_cell_text(tc, v)
    last.addnext(tr)
