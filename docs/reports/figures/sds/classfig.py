"""Helpers shared by the SDS class diagrams (Figures 9 and 12): links that line up by themselves."""
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "tools"))
from vpdiagram import Diagram, Edge  # noqa: E402,F401


def link(d, kind, src, ss, dst, ds, *via, s_off=0, d_off=0, label="", seg=0, side="t"):
    """association: solid line, open arrow (navigable, "uses"); dependency: dashed, open arrow (e.g. «create»)."""
    pts = d._route(src, ss, dst, ds, via, s_off, d_off)
    e = Edge(src, dst, pts, label, None, kind == "dependency", kind != "composition", kind)
    e.label_seg = seg
    if label:
        e.label_at = d._label_at(pts, label, seg, side)
    d.edges.append(e)
