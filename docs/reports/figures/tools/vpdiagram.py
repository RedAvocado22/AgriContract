"""Small diagram model that writes the same drawing three ways.

* ``.drawio``: native draw.io shapes (every lane, node, edge and note is its own cell), so the figure can be
  opened and edited in draw.io, not a picture wrapped in a file.
* ``.svg``: identical geometry, the file the PNG is made from.
* ``.png``: for the Word reports (``magick`` with librsvg).

Notation follows the UML 2.5 diagrams of Visual Paradigm: white shapes, black lines, open arrow heads on control
flows, diamonds for decision/merge, black bars for fork/join, guards in [brackets], partitions with a header,
notes with a folded corner and a dashed anchor.

Every edge is drawn exactly through the points given (orthogonal segments), never routed by a tool, and
``check()`` refuses a figure where an edge crosses a node, two edges cross, or a label touches a node or an edge.
"""
from __future__ import annotations

import html
import math
import subprocess
from dataclasses import dataclass, field
from pathlib import Path

FONT = "Arial"
FONT_SIZE = 12
LINE_H = 15
CHAR_W = 6.6  # average Arial 12 width, for label boxes


_FONT_DIR = Path("/usr/share/fonts/liberation-sans-fonts")
_FONTS: dict = {}


def text_w(text: str, size: int = FONT_SIZE, bold=False, italic=False) -> float:
    """Rendered width with the metric-compatible Arial (Liberation Sans) the SVG is drawn with."""
    style = "BoldItalic" if bold and italic else "Bold" if bold else "Italic" if italic else "Regular"
    try:
        from PIL import ImageFont
        key = (style, size)
        if key not in _FONTS:
            _FONTS[key] = ImageFont.truetype(str(_FONT_DIR / f"LiberationSans-{style}.ttf"), size * 4)
        return _FONTS[key].getlength(text) / 4
    except (ImportError, OSError):
        return len(text) * CHAR_W * size / FONT_SIZE * (1.1 if bold else 1)


def compartment_h(lines) -> float:
    return max(len(lines), 1) * LINE_H + 8


def text_size(text: str, size: int = FONT_SIZE) -> tuple[float, float]:
    lines = text.split("\n")
    w = max(text_w(line, size) for line in lines)
    return w, len(lines) * LINE_H * size / FONT_SIZE


@dataclass
class Node:
    id: str
    kind: str  # action | initial | final | decision | bar | note | object | text
    cx: float
    cy: float
    w: float = 0
    h: float = 0
    text: str = ""

    def box(self, pad: float = 0) -> tuple[float, float, float, float]:
        return (self.cx - self.w / 2 - pad, self.cy - self.h / 2 - pad,
                self.cx + self.w / 2 + pad, self.cy + self.h / 2 + pad)

    def port(self, side: str, offset: float = 0) -> tuple[float, float]:
        x0, y0, x1, y1 = self.box()
        if side == "t":
            return self.cx + offset, y0
        if side == "b":
            return self.cx + offset, y1
        if side == "l":
            return x0, self.cy + offset
        if side == "r":
            return x1, self.cy + offset
        raise ValueError(side)


@dataclass
class Edge:
    src: str
    dst: str
    points: list[tuple[float, float]]  # full polyline, first/last on the node borders
    label: str = ""
    label_at: tuple[float, float] | None = None  # top-left of the label box
    dashed: bool = False
    arrow: bool = True
    kind: str = "flow"  # flow | dependency | association | composition | realization | anchor


@dataclass
class Lane:
    name: str
    x: float
    w: float


@dataclass
class Diagram:
    name: str
    width: float
    height: float
    lanes: list[Lane] = field(default_factory=list)
    header_h: float = 32
    allow_crossings: bool = False  # class diagrams: perpendicular crossings drawn with a line jump
    nodes: dict[str, Node] = field(default_factory=dict)
    edges: list[Edge] = field(default_factory=list)

    # ---------- building ----------
    def lane(self, name: str, w: float) -> Lane:
        x = self.lanes[-1].x + self.lanes[-1].w if self.lanes else 0
        lane = Lane(name, x, w)
        self.lanes.append(lane)
        return lane

    def center(self, lane_name: str, dx: float = 0) -> float:
        lane = next(l for l in self.lanes if l.name == lane_name)
        return lane.x + lane.w / 2 + dx

    def action(self, id, cx, cy, text, w=None, h=None):
        tw, th = text_size(text)
        self.nodes[id] = Node(id, "action", cx, cy, w or tw + 28, h or max(th + 18, 40), text)
        return self.nodes[id]

    def initial(self, id, cx, cy):
        self.nodes[id] = Node(id, "initial", cx, cy, 24, 24)
        return self.nodes[id]

    def final(self, id, cx, cy):
        self.nodes[id] = Node(id, "final", cx, cy, 26, 26)
        return self.nodes[id]

    def final_named(self, id, cx, cy, label, side="r"):
        """Activity final with its name next to it (VP shows a final node's name as a label)."""
        f = self.final(id, cx, cy)
        tw, th = text_size(label)
        if side == "r":
            pos = (cx + 13 + 8 + tw / 2, cy)
        elif side == "l":
            pos = (cx - 13 - 8 - tw / 2, cy)
        else:
            pos = (cx, cy + 13 + 6 + th / 2)
        self.nodes[id + "_name"] = Node(id + "_name", "text", pos[0], pos[1], tw, th, label)
        return f

    def decision(self, id, cx, cy, text=""):
        self.nodes[id] = Node(id, "decision", cx, cy, 30, 30, text)
        return self.nodes[id]

    def bar(self, id, cx, cy, w):
        self.nodes[id] = Node(id, "bar", cx, cy, w, 6)
        return self.nodes[id]

    def klass(self, id, cx, top, name, stereotype="", attrs=(), ops=(), w=None, collapsed=False):
        """UML class / interface box. Long lines are wrapped by the caller (continuation lines start with spaces)."""
        tw = max([text_w(l.lstrip("_")) for l in (*attrs, *(ops or ())) if l]
                 + [text_w("\u00ab" + stereotype + "\u00bb", 11) if stereotype else 0,
                    text_w(name, bold=True, italic=stereotype == "interface")])
        head = (LINE_H if stereotype else 0) + LINE_H + 10
        # ops=None: no operations compartment (an enumeration shows its literals only)
        h = head if collapsed else head + compartment_h(attrs) + (compartment_h(ops) if ops is not None else 0)
        if w and w < tw + 16:
            raise SystemExit(f"class {id}: width {w} is too narrow for its text ({tw + 16:.0f} needed)")
        width = w or tw + 20
        n = Node(id, "class", cx, top + h / 2, width, h, name)
        n.stereotype, n.attrs, n.head, n.collapsed = stereotype, list(attrs), head, collapsed
        n.ops = None if ops is None else list(ops)
        self.nodes[id] = n
        return n

    def entity(self, id, cx, top, name, pk=(), fk=(), ghost=False):
        """Crow's foot entity: name, primary key compartment (bold, underlined), foreign key compartment (italic).
        A primary key line may end in " (FK)" (associative entity); only the name part is bold and underlined.
        ghost=True: an entity drawn in another part, a dashed grey box with its name only."""
        if ghost:
            n = Node(id, "ghost", cx, top + 13, text_w(name) + 24, 26, name)
        else:
            tw = max([text_w(name)] + [text_w(l, bold=True) for l in pk] + [text_w(l, italic=True) for l in fk])
            h = LINE_H + 10 + compartment_h(pk) + (compartment_h(fk) if fk else 0)
            n = Node(id, "entity", cx, top + h / 2, tw + 22, h, name)
            n.pk, n.fk = list(pk), list(fk)
        self.nodes[id] = n
        return n

    def er(self, src, s_side, dst, d_side, *via, s="one", d="zero_many", label="", s_off=0, d_off=0,
           seg=0, side="on"):
        """Relationship line, cardinality s at src and d at dst: one | zero_one | many | zero_many.
        The label (verb read from src to dst) sits on segment seg: on the line, or beside it (t | b | l | r)."""
        a = self.nodes[src].port(s_side, s_off)
        b = self.nodes[dst].port(d_side, d_off)
        pts = [a, *via, b]
        e = Edge(src, dst, pts, label, None, False, False, "er")
        e.cards, e.label_seg = (s, d), seg
        if label:
            w, h = text_size(label, 11)
            w += 4
            mx, my = (pts[seg][0] + pts[seg + 1][0]) / 2, (pts[seg][1] + pts[seg + 1][1]) / 2
            e.label_at = {"on": (mx - w / 2, my - h / 2), "t": (mx - w / 2, my - 10 - h), "b": (mx - w / 2, my + 10),
                          "l": (mx - 10 - w, my - h / 2), "r": (mx + 10, my - h / 2)}[side]
        self.edges.append(e)

    def package(self, id, x0, y0, w, h, name):
        """UML package frame (tab with the name); class boxes may sit inside it."""
        n = Node(id, "package", x0 + w / 2, y0 + h / 2, w, h, name)
        self.nodes[id] = n
        return n

    def note(self, id, cx, cy, text, w=None):
        tw, th = text_size(text)
        self.nodes[id] = Node(id, "note", cx, cy, w or tw + 24, th + 16, text)
        return self.nodes[id]

    def edge(self, src, dst, *points, label="", label_at=None, dashed=False, arrow=True):
        self.edges.append(Edge(src, dst, list(points), label, label_at, dashed, arrow))

    def rel(self, kind, src, s_side, dst, d_side, *via, label="", label_at=None, s_off=0, d_off=0):
        """A class relationship: dependency (dashed, open arrow), association (solid, open arrow),
        composition (filled diamond at src), realization (dashed, hollow triangle)."""
        a = self.nodes[src].port(s_side, s_off)
        b = self.nodes[dst].port(d_side, d_off)
        self.edges.append(Edge(src, dst, [a, *via, b], label, label_at, kind in ("dependency", "realization"),
                               True, kind))

    def flow(self, src, s_side, dst, d_side, *via, label="", label_at=None, s_off=0, d_off=0):
        """Control flow from a side of src to a side of dst through the given corner points."""
        a = self.nodes[src].port(s_side, s_off)
        b = self.nodes[dst].port(d_side, d_off)
        self.edge(src, dst, a, *via, b, label=label, label_at=label_at)

    # ---------- checking ----------
    def check(self) -> list[str]:
        problems = []
        segs = []
        for i, e in enumerate(self.edges):
            for p, q in zip(e.points, e.points[1:]):
                if p[0] != q[0] and p[1] != q[1]:
                    problems.append(f"edge {e.src}->{e.dst} has a diagonal segment {p}->{q}")
                segs.append((i, p, q))
        for i, p, q in segs:
            e = self.edges[i]
            for n in self.nodes.values():
                if n.id in (e.src, e.dst):
                    continue
                if n.kind == "package":
                    if any(m.id in (e.src, e.dst) for m in self.nodes.values() if inside(m, n)):
                        continue
                if seg_hits_box(p, q, n.box(2)):
                    problems.append(f"edge {e.src}->{e.dst} crosses node {n.id}")
        for a in range(len(segs)):
            for b in range(a + 1, len(segs)):
                i, p, q = segs[a]
                j, r, s = segs[b]
                if i == j:
                    continue
                if segs_cross(p, q, r, s, self.edges[i], self.edges[j]) and not (
                        self.allow_crossings and (p[1] == q[1]) != (r[1] == s[1])):
                    problems.append(f"edges {self.edges[i].src}->{self.edges[i].dst} and "
                                    f"{self.edges[j].src}->{self.edges[j].dst} cross at {p}-{q} / {r}-{s}")
        for e in self.edges:
            if not e.label:
                continue
            lb = self.label_box(e)
            for n in self.nodes.values():
                if boxes_overlap(lb, n.box(1)):
                    problems.append(f"label {e.label!r} touches node {n.id}")
            own = 0
            for i, p, q in segs:
                if self.edges[i] is e:
                    own += 1
                    if own == getattr(e, "label_seg", 0) + 1:  # the segment the label belongs to
                        continue
                if seg_hits_box(p, q, lb):
                    problems.append(f"label {e.label!r} touches edge {self.edges[i].src}->{self.edges[i].dst}")
        for e in self.edges:
            if e.kind == "er" and e.label:
                lb = self.label_box(e)
                for p, q in ((e.points[0], e.points[1]), (e.points[-1], e.points[-2])):
                    ln = math.hypot(q[0] - p[0], q[1] - p[1])
                    ex, ey = p[0] + (q[0] - p[0]) / ln * 25, p[1] + (q[1] - p[1]) / ln * 25
                    px, py = (8, 2) if p[0] == ex else (2, 8)  # 8 across the line, 2 along it
                    zone = (min(p[0], ex) - px, min(p[1], ey) - py, max(p[0], ex) + px, max(p[1], ey) + py)
                    if boxes_overlap(lb, zone):
                        problems.append(f"label {e.label!r} covers a cardinality symbol")
            if e.kind == "er":
                for p, q in ((e.points[0], e.points[1]), (e.points[-1], e.points[-2])):
                    if abs(p[0] - q[0]) + abs(p[1] - q[1]) < 28:
                        problems.append(f"edge {e.src}->{e.dst}: end segment too short for its symbol")
        for e in self.edges:
            if e.label:
                x0, _, x1, _ = self.label_box(e)
                for lane in self.lanes[1:]:
                    if x0 < lane.x < x1:
                        problems.append(f"label {e.label!r} straddles the border of lane {lane.name}")
        for e in self.edges:
            if e.label and self.label_box(e)[2] > self.width - 2:
                problems.append(f"label {e.label!r} runs past the right edge")
        for n in self.nodes.values():
            if n.box()[2] > self.width - 1 or n.box()[3] > self.height - 1:
                problems.append(f"node {n.id} runs past the edge of the drawing")
        ids = list(self.nodes.values())
        # A final's own name sits right next to it on purpose.
        def paired(a, b):
            return a.id + "_name" == b.id or b.id + "_name" == a.id
        for a in range(len(ids)):
            for b in range(a + 1, len(ids)):
                if paired(ids[a], ids[b]) or inside(ids[a], ids[b]) or inside(ids[b], ids[a]):
                    continue
                if boxes_overlap(ids[a].box(4), ids[b].box(4)):
                    problems.append(f"nodes {ids[a].id} and {ids[b].id} overlap")
        for n in self.nodes.values():
            x0, y0, x1, y1 = n.box()
            if self.lanes and y0 < self.header_h + 4:
                problems.append(f"node {n.id} under the lane header")
            for lane in self.lanes:
                if x0 < lane.x < x1 and n.kind not in ("bar", "note"):
                    problems.append(f"node {n.id} straddles the border of lane {lane.name}")
        return problems

    def label_box(self, e: Edge):
        w, h = text_size(e.label, 11)
        x, y = e.label_at
        return (x, y, x + w + 4, y + h)

    # ---------- output ----------
    def write(self, out_dir: Path, png_density: int = 200) -> None:
        problems = self.check()
        if problems:
            raise SystemExit(f"{self.name}: refusing to write\n  " + "\n  ".join(problems))
        out_dir.mkdir(parents=True, exist_ok=True)
        (out_dir / f"{self.name}.drawio").write_text(self.drawio(), encoding="utf-8")
        svg = out_dir / f"{self.name}.svg"
        svg.write_text(self.svg(), encoding="utf-8")
        subprocess.run(["magick", "-density", str(png_density), "-background", "white", str(svg),
                        "-flatten", str(out_dir / f"{self.name}.png")], check=True)

    # SVG ------------------------------------------------------------
    def svg(self) -> str:
        o = [f'<svg xmlns="http://www.w3.org/2000/svg" width="{self.width}" height="{self.height}" '
             f'viewBox="0 0 {self.width} {self.height}" font-family="{FONT}" font-size="{FONT_SIZE}">',
             '<defs><marker id="open" viewBox="0 0 12 12" refX="11" refY="6" markerWidth="12" markerHeight="12" '
             'markerUnits="userSpaceOnUse" orient="auto"><path d="M1,1 L11,6 L1,11" fill="none" stroke="black" '
             'stroke-width="1.2"/></marker>'
             '<marker id="tri" viewBox="0 0 14 14" refX="13" refY="7" markerWidth="14" markerHeight="14" '
             'markerUnits="userSpaceOnUse" orient="auto"><path d="M1,1 L13,7 L1,13 Z" fill="white" stroke="black"/>'
             '</marker><marker id="dia" viewBox="0 0 18 10" refX="1" refY="5" markerWidth="18" markerHeight="10" '
             'markerUnits="userSpaceOnUse" orient="auto"><path d="M1,5 L9,1 L17,5 L9,9 Z" fill="black"/></marker></defs>',
             f'<rect x="0" y="0" width="{self.width}" height="{self.height}" fill="white"/>']
        for lane in self.lanes:
            o.append(f'<rect x="{lane.x + .5}" y=".5" width="{lane.w}" height="{self.height - 1}" fill="white" '
                     f'stroke="black"/>')
            o.append(f'<line x1="{lane.x}" y1="{self.header_h}" x2="{lane.x + lane.w}" y2="{self.header_h}" '
                     f'stroke="black"/>')
            o.append(svg_text(lane.x + lane.w / 2, self.header_h / 2, lane.name, bold=True))
        verticals = [(p, q) for e in self.edges for p, q in zip(e.points, e.points[1:]) if p[0] == q[0]]
        for e in self.edges:
            d = path_with_jumps(e.points, verticals if self.allow_crossings else [])
            dash = ' stroke-dasharray="5,4"' if e.dashed else ""
            end = {"realization": "tri"}.get(e.kind, "open")
            mark = f' marker-end="url(#{end})"' if e.arrow else ""
            start = ' marker-start="url(#dia)"' if e.kind == "composition" else ""
            o.append(f'<path d="{d}" fill="none" stroke="black" stroke-width="1.1"{dash}{mark}{start}/>')
            if e.kind == "er":
                o.append(er_mark(e.points[0], e.points[1], e.cards[0]))
                o.append(er_mark(e.points[-1], e.points[-2], e.cards[1]))
            if e.label:
                x0, y0, x1, y1 = self.label_box(e)
                o.append(f'<rect x="{x0}" y="{y0}" width="{x1 - x0}" height="{y1 - y0}" fill="white"/>')
                o.append(svg_text(x0 + 2, (y0 + y1) / 2, e.label, size=11, anchor="start"))
        for n in self.nodes.values():
            x0, y0, x1, y1 = n.box()
            if n.kind in ("action", "object"):
                r = 10 if n.kind == "action" else 0
                o.append(f'<rect x="{x0}" y="{y0}" width="{n.w}" height="{n.h}" rx="{r}" fill="white" '
                         f'stroke="black" stroke-width="1.2"/>')
                o.append(svg_text(n.cx, n.cy, n.text))
            elif n.kind == "initial":
                o.append(f'<circle cx="{n.cx}" cy="{n.cy}" r="{n.w / 2}" fill="black"/>')
            elif n.kind == "final":
                o.append(f'<circle cx="{n.cx}" cy="{n.cy}" r="{n.w / 2}" fill="white" stroke="black" '
                         f'stroke-width="1.2"/><circle cx="{n.cx}" cy="{n.cy}" r="{n.w / 2 - 5}" fill="black"/>')
            elif n.kind == "decision":
                o.append(f'<polygon points="{n.cx},{y0} {x1},{n.cy} {n.cx},{y1} {x0},{n.cy}" fill="white" '
                         f'stroke="black" stroke-width="1.2"/>')
            elif n.kind == "bar":
                o.append(f'<rect x="{x0}" y="{y0}" width="{n.w}" height="{n.h}" fill="black"/>')
            elif n.kind == "note":
                f = 10
                o.append(f'<path d="M{x0},{y0} L{x1 - f},{y0} L{x1},{y0 + f} L{x1},{y1} L{x0},{y1} Z" '
                         f'fill="white" stroke="black"/><path d="M{x1 - f},{y0} L{x1 - f},{y0 + f} L{x1},{y0 + f}" '
                         f'fill="none" stroke="black"/>')
                o.append(svg_text(x0 + 10, n.cy, n.text, anchor="start"))
            elif n.kind == "text":
                o.append(svg_text(n.cx, n.cy, n.text))
            elif n.kind == "package":
                tw = len(n.text) * CHAR_W + 16
                o.append(f'<rect x="{x0}" y="{y0 - 20}" width="{tw}" height="20" fill="white" stroke="black"/>')
                o.append(f'<rect x="{x0}" y="{y0}" width="{n.w}" height="{n.h}" fill="none" stroke="black"/>')
                o.append(svg_text(x0 + 8, y0 - 10, n.text, bold=True, anchor="start"))
            elif n.kind == "ghost":
                o.append(f'<rect x="{x0}" y="{y0}" width="{n.w}" height="{n.h}" fill="white" stroke="#808080" '
                         f'stroke-width="1.2" stroke-dasharray="5,3"/>')
                o.append(svg_text(n.cx, n.cy, n.text).replace("<text ", '<text fill="#606060" ', 1))
            elif n.kind == "entity":
                o.append(f'<rect x="{x0}" y="{y0}" width="{n.w}" height="{n.h}" fill="white" stroke="black" '
                         f'stroke-width="1.2"/>')
                o.append(svg_text(n.cx, y0 + 5 + LINE_H / 2, n.text))
                y = y0 + LINE_H + 10
                for block, italic in ((n.pk, False), (n.fk, True)):
                    if not block:
                        continue
                    o.append(f'<line x1="{x0}" y1="{y}" x2="{x1}" y2="{y}" stroke="black"/>')
                    for i, line in enumerate(block):
                        ty = y + 4 + LINE_H / 2 + i * LINE_H
                        if italic:
                            o.append(svg_text(x0 + 8, ty, line, anchor="start", italic=True))
                            continue
                        key, _, rest = line.partition(" (")
                        o.append(svg_text(x0 + 8, ty, key, anchor="start", bold=True))
                        o.append(f'<line x1="{x0 + 8}" y1="{ty + 6.5}" x2="{x0 + 8 + text_w(key, bold=True)}" '
                                 f'y2="{ty + 6.5}" stroke="black" stroke-width="0.9"/>')
                        if rest:
                            o.append(svg_text(x0 + 8 + text_w(key + " ", bold=True), ty, "(" + rest,
                                              anchor="start"))
                    y += compartment_h(block)
            elif n.kind == "class":
                o.append(f'<rect x="{x0}" y="{y0}" width="{n.w}" height="{n.h}" fill="white" stroke="black" '
                         f'stroke-width="1.2"/>')
                y = y0 + 5 + LINE_H / 2
                if n.stereotype:
                    o.append(svg_text(n.cx, y, "\u00ab" + n.stereotype + "\u00bb", size=11))
                    y += LINE_H
                o.append(svg_text(n.cx, y, n.text, bold=True, italic=n.stereotype == "interface"))
                if not n.collapsed:
                    y = y0 + n.head
                    for block in [b for b in (n.attrs, n.ops) if b is not None]:
                        o.append(f'<line x1="{x0}" y1="{y}" x2="{x1}" y2="{y}" stroke="black"/>')
                        for i, line in enumerate(block):
                            static = line.startswith("__")  # UML: static members are underlined
                            ty = y + 4 + LINE_H / 2 + i * LINE_H
                            o.append(svg_text(x0 + 8, ty, line.lstrip("_"), anchor="start", keep_space=True))
                            if static:  # drawn as a line: librsvg ignores text-decoration
                                text = line.lstrip("_")
                                lead = text_w(text[:len(text) - len(text.lstrip())])
                                o.append(f'<line x1="{x0 + 8 + lead}" y1="{ty + 6.5}" '
                                         f'x2="{x0 + 8 + text_w(text)}" y2="{ty + 6.5}" stroke="black" '
                                         f'stroke-width="0.8"/>')
                        y += compartment_h(block)
        o.append("</svg>")
        return "\n".join(o)

    # draw.io ----------------------------------------------------------
    def drawio(self) -> str:
        cells = ['<mxCell id="0"/>', '<mxCell id="1" parent="0"/>']
        for i, lane in enumerate(self.lanes):
            cells.append(
                f'<mxCell id="lane{i}" value={q(lane.name)} style="swimlane;horizontal=1;startSize={self.header_h};'
                f'fontStyle=1;fontFamily={FONT};fontSize={FONT_SIZE};fillColor=none;strokeColor=#000000;'
                f'swimlaneFillColor=#FFFFFF;html=1;" vertex="1" parent="1"><mxGeometry x="{lane.x}" y="0" '
                f'width="{lane.w}" height="{self.height}" as="geometry"/></mxCell>')
        base = f"fontFamily={FONT};fontSize={FONT_SIZE};strokeColor=#000000;fillColor=#FFFFFF;html=1;whiteSpace=wrap;"
        styles = {
            "action": "rounded=1;arcSize=20;" + base,
            "object": base,
            "initial": "ellipse;fillColor=#000000;strokeColor=#000000;html=1;",
            "final": "ellipse;shape=endState;fillColor=#000000;strokeColor=#000000;html=1;",
            "decision": "rhombus;" + base,
            "bar": "shape=line;strokeWidth=6;strokeColor=#000000;html=1;",
            "note": "shape=note;size=10;align=left;spacingLeft=8;" + base,
            "text": "text;strokeColor=none;fillColor=none;align=center;" + base,
            "package": "shape=folder;tabWidth={tw};tabHeight=20;tabPosition=left;fontStyle=1;align=left;"
                       "verticalAlign=top;spacingLeft=8;spacingTop=-20;fillColor=none;strokeColor=#000000;html=1;"
                       f"fontFamily={FONT};fontSize={FONT_SIZE};",
        }
        for n in [n for n in self.nodes.values() if n.kind == "package"]:
            pass
        for n in [n for n in self.nodes.values() if n.kind == "class"]:
            x0, y0, _, _ = n.box()
            head = ("&laquo;" + html.escape(n.stereotype) + "&raquo;<br>" if n.stereotype else "") + \
                ("<i><b>" if n.stereotype == "interface" else "<b>") + html.escape(n.text) + \
                ("</b></i>" if n.stereotype == "interface" else "</b>")
            cells.append(f'<mxCell id="{n.id}" value={q(head)} style="swimlane;fontStyle=0;html=1;whiteSpace=wrap;'
                         f'startSize={n.head};fillColor=#FFFFFF;strokeColor=#000000;fontFamily={FONT};'
                         f'fontSize={FONT_SIZE};childLayout=stackLayout;horizontal=1;horizontalStack=0;'
                         f'resizeParent=1;collapsible=0;marginBottom=0;" vertex="1" parent="1">'
                         f'<mxGeometry x="{x0}" y="{y0}" width="{n.w}" height="{n.h}" as="geometry"/></mxCell>')
            if not n.collapsed:
                y = n.head
                for k, block in enumerate([b for b in (n.attrs, n.ops) if b is not None]):
                    if k == 1:
                        cells.append(f'<mxCell id="{n.id}_sep" style="line;strokeWidth=1;fillColor=none;align=left;'
                                     f'verticalAlign=middle;spacingTop=-1;spacingLeft=3;spacingRight=3;rotatable=0;'
                                     f'labelPosition=right;points=[];portConstraint=eastwest;strokeColor=#000000;" '
                                     f'vertex="1" parent="{n.id}"><mxGeometry y="{y}" width="{n.w}" height="1" '
                                     f'as="geometry"/></mxCell>')
                    hgt = compartment_h(block)
                    val = "<br>".join(("<u>" + html.escape(l[2:]) + "</u>") if l.startswith("__")
                                      else html.escape(l).replace("  ", "&nbsp;&nbsp;") for l in block)
                    cells.append(f'<mxCell id="{n.id}_c{k}" value={q(val)} style="text;html=1;align=left;'
                                 f'verticalAlign=top;spacingLeft=6;spacingTop=2;fontFamily={FONT};fontSize={FONT_SIZE};'
                                 f'strokeColor=none;fillColor=none;whiteSpace=nowrap;" vertex="1" parent="{n.id}">'
                                 f'<mxGeometry y="{y}" width="{n.w}" height="{hgt}" as="geometry"/></mxCell>')
                    y += hgt
        for n in [n for n in self.nodes.values() if n.kind in ("entity", "ghost")]:
            x0, y0, _, _ = n.box()
            if n.kind == "ghost":
                cells.append(f'<mxCell id="{n.id}" value={q(html.escape(n.text))} style="rounded=0;dashed=1;'
                             f'strokeColor=#808080;fontColor=#606060;fillColor=#FFFFFF;fontFamily={FONT};'
                             f'fontSize={FONT_SIZE};html=1;" vertex="1" parent="1"><mxGeometry x="{x0}" y="{y0}" '
                             f'width="{n.w}" height="{n.h}" as="geometry"/></mxCell>')
                continue
            cells.append(f'<mxCell id="{n.id}" value={q(html.escape(n.text))} style="swimlane;fontStyle=0;html=1;'
                         f'whiteSpace=wrap;startSize={LINE_H + 10};fillColor=#FFFFFF;strokeColor=#000000;'
                         f'fontFamily={FONT};fontSize={FONT_SIZE};childLayout=stackLayout;horizontal=1;'
                         f'horizontalStack=0;resizeParent=1;collapsible=0;marginBottom=0;" vertex="1" parent="1">'
                         f'<mxGeometry x="{x0}" y="{y0}" width="{n.w}" height="{n.h}" as="geometry"/></mxCell>')
            y = LINE_H + 10
            for k, (block, italic) in enumerate(((n.pk, False), (n.fk, True))):
                if not block:
                    continue
                if italic:
                    val = "<br>".join("<i>" + html.escape(l) + "</i>" for l in block)
                else:
                    val = "<br>".join("<b><u>" + html.escape(l.partition(" (")[0]) + "</u></b>"
                                      + (" (" + html.escape(l.partition(" (")[2]) if " (" in l else "") for l in block)
                hgt = compartment_h(block)
                border = "top=1;bottom=0;left=0;right=0;strokeColor=#000000;shape=partialRectangle;"
                cells.append(f'<mxCell id="{n.id}_c{k}" value={q(val)} style="text;html=1;align=left;'
                             f'verticalAlign=top;spacingLeft=6;spacingTop=2;fontFamily={FONT};fontSize={FONT_SIZE};'
                             f'{border}fillColor=none;whiteSpace=nowrap;" vertex="1" parent="{n.id}">'
                             f'<mxGeometry y="{y}" width="{n.w}" height="{hgt}" as="geometry"/></mxCell>')
                y += hgt
        for n in sorted([n for n in self.nodes.values() if n.kind not in ("class", "entity", "ghost")],
                        key=lambda n: n.kind != "package"):
            x0, y0, _, _ = n.box()
            # html=1 cells hold HTML; the attribute escapes it once more.
            value = q(html.escape(n.text, quote=False).replace("\n", "<br>"))
            style = styles[n.kind].replace("{tw}", str(int(len(n.text) * CHAR_W + 16)))
            gy, gh = (y0 - 20, n.h + 20) if n.kind == "package" else (y0, n.h)
            cells.append(f'<mxCell id="{n.id}" value={value} style="{style}" vertex="1" parent="1">'
                         f'<mxGeometry x="{x0}" y="{gy}" width="{n.w}" height="{gh}" as="geometry"/></mxCell>')
        for i, e in enumerate(self.edges):
            s, t = self.nodes[e.src], self.nodes[e.dst]
            ex, ey = rel(s, e.points[0])
            nx, ny = rel(t, e.points[-1])
            end = "block;endFill=0" if e.kind == "realization" else ("open" if e.arrow else "none")
            start = "startArrow=diamondThin;startFill=1;startSize=14;" if e.kind == "composition" else ""
            if e.kind == "er":
                end = ER_DRAWIO[e.cards[1]] + ";endFill=0"
                start = f"startArrow={ER_DRAWIO[e.cards[0]]};startFill=0;startSize=12;"
            jump = "jumpStyle=arc;jumpSize=8;" if self.allow_crossings else ""
            style = (f"edgeStyle=none;rounded=0;html=1;endArrow={end};endSize=10;{start}{jump}"
                     f"strokeColor=#000000;fontFamily={FONT};fontSize=11;exitX={ex};exitY={ey};exitDx=0;exitDy=0;"
                     f"entryX={nx};entryY={ny};entryDx=0;entryDy=0;" + ("dashed=1;" if e.dashed else ""))
            pts = "".join(f'<mxPoint x="{x}" y="{y}"/>' for x, y in e.points[1:-1])
            cells.append(f'<mxCell id="e{i}" style="{style}" edge="1" parent="1" source="{e.src}" target="{e.dst}">'
                         f'<mxGeometry relative="1" as="geometry"><Array as="points">{pts}</Array></mxGeometry>'
                         f'</mxCell>')
            if e.label:
                x0, y0, x1, y1 = self.label_box(e)
                cells.append(f'<mxCell id="e{i}l" value={q(e.label)} style="text;html=1;fontFamily={FONT};'
                             f'fontSize=11;align=left;verticalAlign=middle;labelBackgroundColor=#FFFFFF;" '
                             f'vertex="1" parent="1"><mxGeometry x="{x0}" y="{y0}" width="{x1 - x0}" '
                             f'height="{y1 - y0}" as="geometry"/></mxCell>')
        return (f'<mxfile host="vpdiagram.py"><diagram name={q(self.name)} id={q(self.name)}><mxGraphModel '
                f'grid="1" gridSize="10" page="1" pageWidth="{int(self.width)}" pageHeight="{int(self.height)}">'
                f'<root>{"".join(cells)}</root></mxGraphModel></diagram></mxfile>\n')


ER_DRAWIO = {"one": "ERmandOne", "zero_one": "ERzeroToOne", "many": "ERoneToMany", "zero_many": "ERzeroToMany"}


def er_mark(p, nxt, card) -> str:
    """Crow's foot symbol at end point p (on the entity border) of a line going on to nxt."""
    dx, dy = nxt[0] - p[0], nxt[1] - p[1]
    ln = math.hypot(dx, dy)
    ux, uy = dx / ln, dy / ln
    vx, vy = -uy, ux
    at = lambda d, s=0.0: (p[0] + ux * d + vx * s, p[1] + uy * d + vy * s)
    def tick(d):
        (x1, y1), (x2, y2) = at(d, -6), at(d, 6)
        return f'<line x1="{x1:.1f}" y1="{y1:.1f}" x2="{x2:.1f}" y2="{y2:.1f}" stroke="black" stroke-width="1.2"/>'
    def circle(d):
        x, y = at(d)
        return f'<circle cx="{x:.1f}" cy="{y:.1f}" r="4" fill="white" stroke="black" stroke-width="1.2"/>'
    def foot():
        (hx, hy) = at(11)
        return "".join(f'<line x1="{hx:.1f}" y1="{hy:.1f}" x2="{x:.1f}" y2="{y:.1f}" stroke="black" '
                       f'stroke-width="1.2"/>' for x, y in (at(0, -7), at(0, 7)))
    return {"one": tick(7) + tick(12), "zero_one": tick(8) + circle(17),
            "many": foot() + tick(15), "zero_many": foot() + circle(19)}[card]


def q(s: str) -> str:
    return '"' + html.escape(s, quote=True) + '"'


def rel(n: Node, p) -> tuple[float, float]:
    x0, y0, _, _ = n.box()
    return round((p[0] - x0) / n.w, 4) if n.w else 0.5, round((p[1] - y0) / n.h, 4) if n.h else 0.5


def svg_text(x, y, text, bold=False, size=FONT_SIZE, anchor="middle", italic=False, keep_space=False,
             underline=False) -> str:
    lines = text.split("\n")
    lh = LINE_H * size / FONT_SIZE
    top = y - lh * (len(lines) - 1) / 2
    weight = (' font-weight="bold"' if bold else "") + (' font-style="italic"' if italic else "") + \
        (' xml:space="preserve"' if keep_space else "") + (' text-decoration="underline"' if underline else "")
    spans = "".join(f'<tspan x="{x}" y="{top + i * lh}">{html.escape(line)}</tspan>' for i, line in enumerate(lines))
    return (f'<text text-anchor="{anchor}" dominant-baseline="central" font-size="{size}"{weight}>{spans}</text>')


def boxes_overlap(a, b) -> bool:
    return a[0] < b[2] and b[0] < a[2] and a[1] < b[3] and b[1] < a[3]


def seg_hits_box(p, q, box) -> bool:
    x0, y0, x1, y1 = box
    sx0, sx1 = sorted((p[0], q[0]))
    sy0, sy1 = sorted((p[1], q[1]))
    return sx0 < x1 and x0 < sx1 + (1 if sx0 == sx1 else 0) and sy0 < y1 and y0 < sy1 + (1 if sy0 == sy1 else 0)


def segs_cross(p, q, r, s, e1: Edge, e2: Edge) -> bool:
    # Two orthogonal segments cross when one is horizontal, the other vertical, and they meet strictly inside
    # both. Edges leaving the same node or arriving at the same node may share a point (a fan-out), not cross.
    h1, h2 = p[1] == q[1], r[1] == s[1]
    if h1 == h2:
        if h1 and p[1] == r[1] and overlap_1d(p[0], q[0], r[0], s[0]) and not shared(e1, e2):
            return True
        if not h1 and p[0] == r[0] and overlap_1d(p[1], q[1], r[1], s[1]) and not shared(e1, e2):
            return True
        return False
    hp, hq = (p, q) if h1 else (r, s)
    vp, vq = (r, s) if h1 else (p, q)
    x, y = vp[0], hp[1]
    inside_h = min(hp[0], hq[0]) < x < max(hp[0], hq[0])
    inside_v = min(vp[1], vq[1]) < y < max(vp[1], vq[1])
    return inside_h and inside_v


def overlap_1d(a, b, c, d) -> bool:
    return max(min(a, b), min(c, d)) < min(max(a, b), max(c, d))


def shared(e1: Edge, e2: Edge) -> bool:
    return e1.src == e2.src or e1.dst == e2.dst


def path_with_jumps(points, verticals, r=5.0) -> str:
    """SVG path through the points; a horizontal segment hops over every vertical segment it crosses."""
    d = f"M{points[0][0]},{points[0][1]}"
    for p, q in zip(points, points[1:]):
        if p[1] == q[1] and verticals:
            y = p[1]
            lo, hi = sorted((p[0], q[0]))
            xs = sorted({v[0][0] for v in verticals
                         if lo + r < v[0][0] < hi - r and min(v[0][1], v[1][1]) < y < max(v[0][1], v[1][1])},
                        reverse=q[0] < p[0])
            step = r if q[0] > p[0] else -r
            for x in xs:
                d += f" L{x - step},{y} A{r},{r} 0 0 {1 if step > 0 else 0} {x + step},{y}"
        d += f" L{q[0]},{q[1]}"
    return d


def inside(a: Node, b: Node) -> bool:
    """a lies inside package b."""
    if b.kind != "package":
        return False
    ax0, ay0, ax1, ay1 = a.box()
    bx0, by0, bx1, by1 = b.box()
    return bx0 <= ax0 and by0 <= ay0 and ax1 <= bx1 and ay1 <= by1
