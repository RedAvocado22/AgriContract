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
import subprocess
from dataclasses import dataclass, field
from pathlib import Path

FONT = "Arial"
FONT_SIZE = 12
LINE_H = 15
CHAR_W = 6.6  # average Arial 12 width, for label boxes


def text_size(text: str, size: int = FONT_SIZE) -> tuple[float, float]:
    lines = text.split("\n")
    w = max(len(line) for line in lines) * CHAR_W * size / FONT_SIZE
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

    def note(self, id, cx, cy, text, w=None):
        tw, th = text_size(text)
        self.nodes[id] = Node(id, "note", cx, cy, w or tw + 24, th + 16, text)
        return self.nodes[id]

    def edge(self, src, dst, *points, label="", label_at=None, dashed=False, arrow=True):
        self.edges.append(Edge(src, dst, list(points), label, label_at, dashed, arrow))

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
                if seg_hits_box(p, q, n.box(2)):
                    problems.append(f"edge {e.src}->{e.dst} crosses node {n.id}")
        for a in range(len(segs)):
            for b in range(a + 1, len(segs)):
                i, p, q = segs[a]
                j, r, s = segs[b]
                if i == j:
                    continue
                if segs_cross(p, q, r, s, self.edges[i], self.edges[j]):
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
                    if own == 1:  # the label belongs to the edge's first segment
                        continue
                if seg_hits_box(p, q, lb):
                    problems.append(f"label {e.label!r} touches edge {self.edges[i].src}->{self.edges[i].dst}")
        for e in self.edges:
            if e.label:
                x0, _, x1, _ = self.label_box(e)
                for lane in self.lanes[1:]:
                    if x0 < lane.x < x1:
                        problems.append(f"label {e.label!r} straddles the border of lane {lane.name}")
        ids = list(self.nodes.values())
        # A final's own name sits right next to it on purpose.
        def paired(a, b):
            return a.id + "_name" == b.id or b.id + "_name" == a.id
        for a in range(len(ids)):
            for b in range(a + 1, len(ids)):
                if not paired(ids[a], ids[b]) and boxes_overlap(ids[a].box(4), ids[b].box(4)):
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
             'stroke-width="1.2"/></marker></defs>',
             f'<rect x="0" y="0" width="{self.width}" height="{self.height}" fill="white"/>']
        for lane in self.lanes:
            o.append(f'<rect x="{lane.x + .5}" y=".5" width="{lane.w}" height="{self.height - 1}" fill="white" '
                     f'stroke="black"/>')
            o.append(f'<line x1="{lane.x}" y1="{self.header_h}" x2="{lane.x + lane.w}" y2="{self.header_h}" '
                     f'stroke="black"/>')
            o.append(svg_text(lane.x + lane.w / 2, self.header_h / 2, lane.name, bold=True))
        for e in self.edges:
            pts = " ".join(f"{x},{y}" for x, y in e.points)
            dash = ' stroke-dasharray="5,4"' if e.dashed else ""
            mark = ' marker-end="url(#open)"' if e.arrow else ""
            o.append(f'<polyline points="{pts}" fill="none" stroke="black" stroke-width="1.1"{dash}{mark}/>')
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
        }
        for n in self.nodes.values():
            x0, y0, _, _ = n.box()
            # html=1 cells hold HTML; the attribute escapes it once more.
            value = q(html.escape(n.text, quote=False).replace("\n", "<br>"))
            cells.append(f'<mxCell id="{n.id}" value={value} style="{styles[n.kind]}" vertex="1" parent="1">'
                         f'<mxGeometry x="{x0}" y="{y0}" width="{n.w}" height="{n.h}" as="geometry"/></mxCell>')
        for i, e in enumerate(self.edges):
            s, t = self.nodes[e.src], self.nodes[e.dst]
            ex, ey = rel(s, e.points[0])
            nx, ny = rel(t, e.points[-1])
            style = (f"edgeStyle=none;rounded=0;html=1;endArrow={'open' if e.arrow else 'none'};endSize=10;"
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


def q(s: str) -> str:
    return '"' + html.escape(s, quote=True) + '"'


def rel(n: Node, p) -> tuple[float, float]:
    x0, y0, _, _ = n.box()
    return round((p[0] - x0) / n.w, 4) if n.w else 0.5, round((p[1] - y0) / n.h, 4) if n.h else 0.5


def svg_text(x, y, text, bold=False, size=FONT_SIZE, anchor="middle") -> str:
    lines = text.split("\n")
    lh = LINE_H * size / FONT_SIZE
    top = y - lh * (len(lines) - 1) / 2
    weight = ' font-weight="bold"' if bold else ""
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
