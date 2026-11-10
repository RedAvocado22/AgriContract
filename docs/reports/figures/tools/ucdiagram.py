"""UML use case diagrams in Visual Paradigm notation, written as native draw.io + SVG + PNG.

Actors are stick figures with their name below; use cases are ellipses inside the system boundary; secondary
systems are «system» boxes outside it; associations are plain straight lines; «include» and «extend» are dashed with
an open arrow. check() refuses a line that cuts through an element it does not join, two lines that cross, and a
label that touches an element or a line.
"""
from __future__ import annotations

import html
import math
import subprocess
from dataclasses import dataclass
from pathlib import Path

from vpdiagram import FONT, FONT_SIZE, LINE_H, boxes_overlap, q, svg_text, text_w


@dataclass
class El:
    id: str
    kind: str  # actor | usecase | system
    cx: float
    cy: float
    w: float
    h: float
    text: str

    def box(self, pad=0.0):
        return self.cx - self.w / 2 - pad, self.cy - self.h / 2 - pad, self.cx + self.w / 2 + pad, \
            self.cy + self.h / 2 + pad

    def border_toward(self, x, y):
        """Point on this element's outline on the way to (x, y)."""
        dx, dy = x - self.cx, y - self.cy
        if dx == 0 and dy == 0:
            return self.cx, self.cy
        if self.kind == "usecase":
            a, b = self.w / 2, self.h / 2
            t = 1 / math.sqrt((dx / a) ** 2 + (dy / b) ** 2)
            return self.cx + dx * t, self.cy + dy * t
        if self.kind == "actor":  # leave from the figure's side, at shoulder height
            return self.cx + (14 if dx > 0 else -14), self.cy - 12
        hx, hy = self.w / 2, self.h / 2
        t = min(hx / abs(dx) if dx else 1e9, hy / abs(dy) if dy else 1e9)
        return self.cx + dx * t, self.cy + dy * t


@dataclass
class Link:
    a: str
    b: str
    kind: str  # assoc | include | extend
    p: tuple = None
    q: tuple = None


class UseCaseDiagram:
    def __init__(self, name, width, height, boundary, title="AgriContract"):
        self.name, self.width, self.height = name, width, height
        self.boundary = boundary  # (x0, y0, x1, y1)
        self.title = title
        self.els: dict[str, El] = {}
        self.links: list[Link] = []

    def actor(self, id, cx, cy, name):
        w = max(text_w(l) for l in name.split("\n")) + 6
        self.els[id] = El(id, "actor", cx, cy, max(w, 30), 40 + name.count("\n") * LINE_H + LINE_H + 4, name)

    def stack(self, left, top, items, gap=14):
        """Use cases in a left-aligned column from top down; returns the y after the last one."""
        y = top
        for id, text in items:
            lines = text.split("\n")
            w = max(text_w(l) for l in lines) + 44
            h = len(lines) * LINE_H + 22
            self.usecase(id, left + w / 2, y + h / 2, text)
            y += h + gap
        return y

    def usecase(self, id, cx, cy, text, w=None):
        lines = text.split("\n")
        tw = max(text_w(l) for l in lines)
        self.els[id] = El(id, "usecase", cx, cy, w or tw + 44, len(lines) * LINE_H + 22, text)
        if w and w < tw + 36:
            raise SystemExit(f"use case {id}: {w} px is too narrow ({tw + 36:.0f} needed)")

    def system(self, id, cx, cy, text):
        lines = ["«system»", *text.split("\n")]
        self.els[id] = El(id, "system", cx, cy, max(text_w(l) for l in lines) + 18, len(lines) * LINE_H + 12, text)

    def assoc(self, a, b):
        self.links.append(Link(a, b, "assoc"))

    def include(self, base, inc):
        self.links.append(Link(base, inc, "include"))

    def extend(self, ext, base):
        self.links.append(Link(ext, base, "extend"))

    def generalize(self, child, parent):
        self.links.append(Link(child, parent, "generalize"))

    # ---------- geometry ----------
    def _anchor(self, el: El, other: El):
        """Where a line leaves el toward other: the facing side's middle point when other lies clearly to the left
        or right (lines then stay out of the column of ellipses), else the outline toward other's centre."""
        if el.kind == "actor":
            x0, _, x1, _ = other.box()
            return el.border_toward(x0 if x0 > el.cx else x1, other.cy)
        x0, y0, x1, y1 = el.box()
        ox0, _, ox1, _ = other.box()
        if ox1 <= x0:
            return x0, el.cy
        if ox0 >= x1:
            return x1, el.cy
        return el.border_toward(other.cx, other.cy)

    def _layout(self):
        for l in self.links:
            a, b = self.els[l.a], self.els[l.b]
            l.p, l.q = self._anchor(a, b), self._anchor(b, a)

    def label_box(self, l: Link):
        text = f"«{l.kind}»"
        w, h = text_w(text, 11, italic=True) + 4, 14
        mx, my = (l.p[0] + l.q[0]) / 2, (l.p[1] + l.q[1]) / 2
        return mx - w / 2, my - h / 2, mx + w / 2, my + h / 2

    def check(self):
        self._layout()
        problems = []
        for l in self.links:
            for e in self.els.values():
                if e.id in (l.a, l.b):
                    continue
                if seg_hits_shape(l.p, l.q, e):
                    problems.append(f"line {l.a}-{l.b} cuts through {e.id}")
        for i in range(len(self.links)):
            for j in range(i + 1, len(self.links)):
                a, b = self.links[i], self.links[j]
                if {a.a, a.b} & {b.a, b.b}:
                    continue
                if segs_intersect(a.p, a.q, b.p, b.q):
                    problems.append(f"lines {a.a}-{a.b} and {b.a}-{b.b} cross")
        for l in self.links:
            if l.kind not in ("include", "extend"):
                continue
            lb = self.label_box(l)
            for e in self.els.values():
                if boxes_overlap(lb, e.box(1)):
                    problems.append(f"label of {l.a}-{l.b} touches {e.id}")
            for m in self.links:
                if m is not l and seg_hits_box(m.p, m.q, lb):
                    problems.append(f"label of {l.a}-{l.b} touches line {m.a}-{m.b}")
        els = list(self.els.values())
        for i in range(len(els)):
            for j in range(i + 1, len(els)):
                if boxes_overlap(els[i].box(6), els[j].box(6)):
                    problems.append(f"{els[i].id} and {els[j].id} overlap")
        bx0, by0, bx1, by1 = self.boundary
        for e in els:
            x0, y0, x1, y1 = e.box()
            inside = bx0 < x0 and x1 < bx1 and by0 + 26 < y0 and y1 < by1
            outside = x1 < bx0 or x0 > bx1 or y1 < by0 or y0 > by1
            if e.kind == "usecase" and not inside:
                problems.append(f"use case {e.id} is not inside the system boundary")
            if e.kind != "usecase" and not outside:
                problems.append(f"{e.kind} {e.id} touches the system boundary")
            if x1 > self.width - 2 or y1 > self.height - 2 or x0 < 2:
                problems.append(f"{e.id} runs past the edge of the drawing")
        return problems

    # ---------- output ----------
    def write(self, out_dir: Path, png_density=200):
        problems = self.check()
        if problems:
            raise SystemExit(f"{self.name}: refusing to write\n  " + "\n  ".join(problems))
        (out_dir / f"{self.name}.drawio").write_text(self.drawio(), encoding="utf-8")
        svg = out_dir / f"{self.name}.svg"
        svg.write_text(self.svg(), encoding="utf-8")
        subprocess.run(["magick", "-density", str(png_density), "-background", "white", str(svg), "-flatten",
                        str(out_dir / f"{self.name}.png")], check=True)

    def svg(self):
        W, H = self.width, self.height
        bx0, by0, bx1, by1 = self.boundary
        o = [f'<svg xmlns="http://www.w3.org/2000/svg" width="{W}" height="{H}" viewBox="0 0 {W} {H}" '
             f'font-family="{FONT}" font-size="{FONT_SIZE}">',
             '<defs><marker id="open" viewBox="0 0 12 12" refX="11" refY="6" markerWidth="12" markerHeight="12" '
             'markerUnits="userSpaceOnUse" orient="auto"><path d="M1,1 L11,6 L1,11" fill="none" stroke="black" '
             'stroke-width="1.2"/></marker><marker id="tri" viewBox="0 0 14 14" refX="13" refY="7" markerWidth="14" '
             'markerHeight="14" markerUnits="userSpaceOnUse" orient="auto"><path d="M1,1 L13,7 L1,13 Z" fill="white" '
             'stroke="black"/></marker></defs>', f'<rect width="{W}" height="{H}" fill="white"/>',
             f'<rect x="{bx0}" y="{by0}" width="{bx1 - bx0}" height="{by1 - by0}" fill="white" stroke="black" '
             f'stroke-width="1.3"/>', svg_text((bx0 + bx1) / 2, by0 + 16, self.title, bold=True)]
        for l in self.links:
            dash = {"assoc": "", "generalize": ' marker-end="url(#tri)"'}.get(
                l.kind, ' stroke-dasharray="5,4" marker-end="url(#open)"')
            o.append(f'<line x1="{l.p[0]:.1f}" y1="{l.p[1]:.1f}" x2="{l.q[0]:.1f}" y2="{l.q[1]:.1f}" stroke="black" '
                     f'stroke-width="1.1"{dash}/>')
            if l.kind in ("include", "extend"):
                x0, y0, x1, y1 = self.label_box(l)
                o.append(f'<rect x="{x0}" y="{y0}" width="{x1 - x0}" height="{y1 - y0}" fill="white"/>')
                o.append(f'<text x="{(x0 + x1) / 2}" y="{(y0 + y1) / 2}" text-anchor="middle" '
                         f'dominant-baseline="central" font-size="11" font-style="italic">'
                         f'«{l.kind}»</text>')
        for e in self.els.values():
            x0, y0, x1, y1 = e.box()
            if e.kind == "usecase":
                o.append(f'<ellipse cx="{e.cx}" cy="{e.cy}" rx="{e.w / 2}" ry="{e.h / 2}" fill="white" '
                         f'stroke="black" stroke-width="1.2"/>')
                o.append(svg_text(e.cx, e.cy, e.text))
            elif e.kind == "system":
                o.append(f'<rect x="{x0}" y="{y0}" width="{e.w}" height="{e.h}" fill="white" stroke="black" '
                         f'stroke-width="1.2"/>')
                o.append(f'<text x="{e.cx}" y="{y0 + 6 + LINE_H / 2}" text-anchor="middle" dominant-baseline="central"'
                         f' font-size="11" font-style="italic">«system»</text>')
                o.append(svg_text(e.cx, y0 + 6 + LINE_H + LINE_H * (e.text.count("\n") + 1) / 2, e.text))
            else:
                cx, top = e.cx, y0
                o.append(f'<circle cx="{cx}" cy="{top + 7}" r="7" fill="white" stroke="black" stroke-width="1.2"/>'
                         f'<line x1="{cx}" y1="{top + 14}" x2="{cx}" y2="{top + 28}" stroke="black" stroke-width="1.2"/>'
                         f'<line x1="{cx - 11}" y1="{top + 19}" x2="{cx + 11}" y2="{top + 19}" stroke="black" '
                         f'stroke-width="1.2"/><line x1="{cx}" y1="{top + 28}" x2="{cx - 9}" y2="{top + 39}" '
                         f'stroke="black" stroke-width="1.2"/><line x1="{cx}" y1="{top + 28}" x2="{cx + 9}" '
                         f'y2="{top + 39}" stroke="black" stroke-width="1.2"/>')
                o.append(svg_text(cx, top + 44 + LINE_H * (e.text.count("\n") + 1) / 2, e.text))
        o.append("</svg>")
        return "\n".join(o)

    def drawio(self):
        bx0, by0, bx1, by1 = self.boundary
        base = f"fontFamily={FONT};fontSize={FONT_SIZE};strokeColor=#000000;fillColor=#FFFFFF;html=1;whiteSpace=wrap;"
        cells = ['<mxCell id="0"/>', '<mxCell id="1" parent="0"/>',
                 f'<mxCell id="boundary" value={q("<b>" + self.title + "</b>")} style="rounded=0;verticalAlign=top;'
                 f'{base}" vertex="1" parent="1"><mxGeometry x="{bx0}" y="{by0}" width="{bx1 - bx0}" '
                 f'height="{by1 - by0}" as="geometry"/></mxCell>']
        for e in self.els.values():
            x0, y0, _, _ = e.box()
            text = html.escape(e.text).replace("\n", "<br>")
            if e.kind == "usecase":
                cells.append(f'<mxCell id="{e.id}" value={q(text)} style="ellipse;{base}" vertex="1" parent="1">'
                             f'<mxGeometry x="{x0}" y="{y0}" width="{e.w}" height="{e.h}" as="geometry"/></mxCell>')
            elif e.kind == "system":
                cells.append(f'<mxCell id="{e.id}" value={q("<i>&laquo;system&raquo;</i><br>" + text)} '
                             f'style="rounded=0;{base}" vertex="1" parent="1"><mxGeometry x="{x0}" y="{y0}" '
                             f'width="{e.w}" height="{e.h}" as="geometry"/></mxCell>')
            else:
                cells.append(f'<mxCell id="{e.id}" value={q(text)} style="shape=umlActor;verticalLabelPosition=bottom;'
                             f'verticalAlign=top;{base}" vertex="1" parent="1"><mxGeometry x="{e.cx - 11}" y="{y0}" '
                             f'width="22" height="40" as="geometry"/></mxCell>')
        for k, l in enumerate(self.links):
            style = {"assoc": "endArrow=none;", "generalize": "endArrow=block;endFill=0;endSize=12;"}.get(
                l.kind, "dashed=1;endArrow=open;endSize=10;fontStyle=2;fontSize=11;labelBackgroundColor=#FFFFFF;")
            value = f"&laquo;{l.kind}&raquo;" if l.kind in ("include", "extend") else ""
            cells.append(f'<mxCell id="l{k}" value="{value}" style="edgeStyle=none;html=1;strokeColor=#000000;'
                         f'fontFamily={FONT};{style}" edge="1" parent="1" source="{l.a}" target="{l.b}">'
                         f'<mxGeometry relative="1" as="geometry"/></mxCell>')
        return (f'<mxfile host="ucdiagram.py"><diagram name={q(self.name)} id={q(self.name)}><mxGraphModel grid="1" '
                f'gridSize="10" page="1" pageWidth="{self.width}" pageHeight="{self.height}"><root>{"".join(cells)}'
                f'</root></mxGraphModel></diagram></mxfile>\n')


def seg_hits_shape(p, q, e: El, steps=60):
    x0, y0, x1, y1 = e.box(2)
    for i in range(1, steps):
        t = i / steps
        x, y = p[0] + (q[0] - p[0]) * t, p[1] + (q[1] - p[1]) * t
        if e.kind == "usecase":
            if ((x - e.cx) / (e.w / 2 + 2)) ** 2 + ((y - e.cy) / (e.h / 2 + 2)) ** 2 < 1:
                return True
        elif x0 < x < x1 and y0 < y < y1:
            return True
    return False


def seg_hits_box(p, q, box, steps=60):
    for i in range(steps + 1):
        t = i / steps
        x, y = p[0] + (q[0] - p[0]) * t, p[1] + (q[1] - p[1]) * t
        if box[0] < x < box[2] and box[1] < y < box[3]:
            return True
    return False


def segs_intersect(p1, p2, p3, p4):
    def orient(a, b, c):
        return (b[0] - a[0]) * (c[1] - a[1]) - (b[1] - a[1]) * (c[0] - a[0])
    d1, d2 = orient(p3, p4, p1), orient(p3, p4, p2)
    d3, d4 = orient(p1, p2, p3), orient(p1, p2, p4)
    return d1 * d2 < 0 and d3 * d4 < 0
