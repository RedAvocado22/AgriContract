"""UML sequence diagrams in Visual Paradigm notation, written as native draw.io + SVG + PNG.

Lifelines are ':ClassName' heads with a dashed line; synchronous messages are solid with a filled head, replies are
dashed with an open head; messages are numbered (1, 1.1, 1.1.1); combined fragments (alt, opt, critical, break) are
frames with the operator in a pentagon tag and guards in [brackets]; notes have a folded corner and a dashed anchor.

Layout is top-down: every message takes the next free row, so nothing is placed by hand except the order. check()
refuses a label that runs over another label or a note.
"""
from __future__ import annotations

import html
import subprocess
from dataclasses import dataclass, field
from pathlib import Path

from vpdiagram import FONT, FONT_SIZE, LINE_H, boxes_overlap, q, svg_text, text_w

HEAD_H_LINE = 15
MSG_FONT = 11


@dataclass
class Lifeline:
    id: str
    title: str
    x: float
    actor: bool = False


@dataclass
class Message:
    src: str
    dst: str
    text: str
    y: float
    kind: str  # sync | reply | self


@dataclass
class Fragment:
    op: str
    guard: str
    x0: float
    y0: float
    x1: float
    y1: float
    operands: list = field(default_factory=list)  # (y, guard) separators


@dataclass
class SeqNote:
    x: float
    y: float
    w: float
    h: float
    text: str
    anchor: tuple


class Seq:
    def __init__(self, name: str, width: float, lifelines: list, top: float = 10, row: float = 23):
        self.name, self.width = name, width
        self.lines: dict[str, Lifeline] = {}
        gap = width / len(lifelines)
        self.head_h = max(len(spec[1].split("\n")) for spec in lifelines if len(spec) < 3) * HEAD_H_LINE + 12
        actors = [spec for spec in lifelines if len(spec) > 2 and spec[2] == "actor"]
        if actors:
            self.head_h = max(self.head_h, 40 + max(len(a[1].split("\n")) for a in actors) * HEAD_H_LINE + 6)
        for i, spec in enumerate(lifelines):
            id, title = spec[0], spec[1]
            self.lines[id] = Lifeline(id, title, gap * i + gap / 2, actor=len(spec) > 2 and spec[2] == "actor")
        self.gap = gap
        self.top = top
        self.y = top + self.head_h + 20
        self.row = row
        self.messages: list[Message] = []
        self.fragments: list[Fragment] = []
        self.notes: list[SeqNote] = []
        self.activations: list[tuple] = []  # (lifeline, y0, y1, depth)
        self._open: dict[str, list] = {}

    # ---------- building ----------
    def _advance(self, text: str) -> float:
        lines = text.count("\n") + 1
        self.y += self.row + (lines - 1) * (LINE_H - 2)
        return self.y

    def call(self, src, dst, text):
        """Synchronous message; opens an activation on dst."""
        y = self._advance(text)
        if src == dst:
            self.messages.append(Message(src, dst, text, y, "self"))
            self.y += 16
            self.activate(dst, y + 8)
            self.deactivate(dst, y + 22)
            self.y += 8
            return
        self.messages.append(Message(src, dst, text, y, "sync"))
        self.activate(dst, y)

    def reply(self, src, dst, text=""):
        """Reply message; closes the activation on src."""
        y = self._advance(text or " ")
        self.messages.append(Message(src, dst, text, y, "reply"))
        self.deactivate(src, y)

    def done(self, id):
        """Closes an activation without a drawn reply (void call)."""
        self.y += 8
        self.deactivate(id, self.y)

    def activate(self, id, y):
        self._open.setdefault(id, []).append(y)

    def deactivate(self, id, y):
        y0 = self._open[id].pop()
        self.activations.append((id, y0, y, len(self._open[id])))

    def gap_rows(self, h):
        self.y += h

    def begin(self, op, guard, first, last):
        """Opens a combined fragment spanning lifelines first..last; returns it for end()/separator()."""
        self.y += 8
        depth = sum(1 for f in self.fragments if f.y1 == 0)  # fragments still open around this one
        x0 = self.lines[first].x - self.gap / 2 + 6 + depth * 8
        x1 = self.lines[last].x + self.gap / 2 - 6 - depth * 8
        f = Fragment(op, guard, x0, self.y, x1, 0)
        self.fragments.append(f)
        self.y += 18
        return f

    def separator(self, f, guard):
        self.y += 10
        f.operands.append((self.y, guard))
        self.y += 10

    def end(self, f):
        self.y += 10
        f.y1 = self.y
        self.y += 4

    def note(self, at, text, side=1, dx=10):
        """Note next to a lifeline at the current row, anchored to it with a dashed line."""
        w = max(text_w(l, MSG_FONT) for l in text.split("\n")) + 18
        h = (text.count("\n") + 1) * (LINE_H - 1) + 10
        lx = self.lines[at].x
        x = lx + dx if side > 0 else lx - dx - w
        y = self.y + 8
        self.notes.append(SeqNote(x, y, w, h, text, (lx + 5 * side, y + h / 2)))
        self.y = y + h

    # ---------- checks ----------
    def label_box(self, m: Message):
        lines = m.text.split("\n")
        w = max(text_w(l, MSG_FONT) for l in lines) + 4
        h = len(lines) * (LINE_H - 2)
        if m.kind == "self":
            x = self.lines[m.src].x + 14
        else:
            a, b = self.lines[m.src].x, self.lines[m.dst].x
            x = min(a, b) + 10
        return (x, m.y - h - 2, x + w, m.y - 2)

    def check(self):
        problems = []
        boxes = [(f"message {m.text!r}", self.label_box(m)) for m in self.messages if m.text]
        boxes += [(f"note {n.text!r}", (n.x, n.y, n.x + n.w, n.y + n.h)) for n in self.notes]
        for i in range(len(boxes)):
            for j in range(i + 1, len(boxes)):
                if boxes_overlap(boxes[i][1], boxes[j][1]):
                    problems.append(f"{boxes[i][0]} overlaps {boxes[j][0]}")
        for name, b in boxes:
            if b[2] > self.width - 2:
                problems.append(f"{name} runs past the right edge ({b[2]:.0f} > {self.width})")
        for f in self.fragments:
            for m in self.messages:
                lb = self.label_box(m)
                tag = (f.x0, f.y0, f.x0 + text_w(f.op, MSG_FONT, bold=True) + 22, f.y0 + 16)
                if m.text and boxes_overlap(lb, tag):
                    problems.append(f"message {m.text!r} hides the {f.op} tag")
        for f in self.fragments:
            need = text_w(f.op, MSG_FONT, bold=True) + 30 + text_w("[" + f.guard + "]", MSG_FONT)
            if need > f.x1 - f.x0:
                problems.append(f"guard [{f.guard}] does not fit its {f.op} frame")
        heads = [(l.id, (lambda x, y, w, h: (x, y, x + w, y + h))(*self._head_box(l)))
                 for l in self.lines.values() if not l.actor]
        for i in range(len(heads)):
            for j in range(i + 1, len(heads)):
                a, b = heads[i][1], heads[j][1]
                if boxes_overlap((a[0] - 4, a[1], a[2] + 4, a[3]), b):
                    problems.append(f"heads of {heads[i][0]} and {heads[j][0]} touch")
        for id, stack in self._open.items():
            if stack:
                problems.append(f"lifeline {id} has an activation never closed")
        return problems

    # ---------- output ----------
    @property
    def height(self):
        return self.y + 30

    def write(self, out_dir: Path, png_density: int = 200):
        problems = self.check()
        if problems:
            raise SystemExit(f"{self.name}: refusing to write\n  " + "\n  ".join(problems))
        (out_dir / f"{self.name}.drawio").write_text(self.drawio(), encoding="utf-8")
        svg = out_dir / f"{self.name}.svg"
        svg.write_text(self.svg(), encoding="utf-8")
        subprocess.run(["magick", "-density", str(png_density), "-background", "white", str(svg), "-flatten",
                        str(out_dir / f"{self.name}.png")], check=True)

    def _head_box(self, l: Lifeline):
        w = max(text_w(t, MSG_FONT) for t in l.title.split("\n")) + 10
        if l.actor:  # the figure is above, the name sits right under it
            return l.x - w / 2, self.top + 40, w, self.head_h - 40
        return l.x - w / 2, self.top, w, self.head_h

    def svg(self) -> str:
        H = self.height
        o = [f'<svg xmlns="http://www.w3.org/2000/svg" width="{self.width}" height="{H}" '
             f'viewBox="0 0 {self.width} {H}" font-family="{FONT}" font-size="{FONT_SIZE}">',
             '<defs><marker id="fill" viewBox="0 0 12 12" refX="11" refY="6" markerWidth="11" markerHeight="11" '
             'markerUnits="userSpaceOnUse" orient="auto"><path d="M1,1 L11,6 L1,11 Z" fill="black"/></marker>'
             '<marker id="open" viewBox="0 0 12 12" refX="11" refY="6" markerWidth="11" markerHeight="11" '
             'markerUnits="userSpaceOnUse" orient="auto"><path d="M1,1 L11,6 L1,11" fill="none" stroke="black"/>'
             '</marker></defs>', f'<rect width="{self.width}" height="{H}" fill="white"/>']
        for l in self.lines.values():
            x0, y0, w, h = self._head_box(l)
            if l.actor:
                cx, top = l.x, self.top
                o.append(f'<circle cx="{cx}" cy="{top + 7}" r="6" fill="white" stroke="black"/>'
                         f'<line x1="{cx}" y1="{top + 13}" x2="{cx}" y2="{top + 27}" stroke="black"/>'
                         f'<line x1="{cx - 9}" y1="{top + 18}" x2="{cx + 9}" y2="{top + 18}" stroke="black"/>'
                         f'<line x1="{cx}" y1="{top + 27}" x2="{cx - 8}" y2="{top + 37}" stroke="black"/>'
                         f'<line x1="{cx}" y1="{top + 27}" x2="{cx + 8}" y2="{top + 37}" stroke="black"/>')
            o.append(f'<line x1="{l.x}" y1="{y0 + h}" x2="{l.x}" y2="{H - 10}" stroke="black" '
                     f'stroke-dasharray="5,4"/>')
            if not l.actor:
                o.append(f'<rect x="{x0}" y="{y0}" width="{w}" height="{h}" fill="white" stroke="black"/>')
            o.append(svg_text(l.x, y0 + h / 2, l.title, size=MSG_FONT))
        for id, y0, y1, depth in sorted(self.activations, key=lambda a: a[3]):
            x = self.lines[id].x - 5 + depth * 5
            o.append(f'<rect x="{x}" y="{y0}" width="10" height="{y1 - y0}" fill="white" stroke="black"/>')
        for f in self.fragments:
            tw = text_w(f.op, MSG_FONT, bold=True) + 14
            o.append(f'<rect x="{f.x0}" y="{f.y0}" width="{f.x1 - f.x0}" height="{f.y1 - f.y0}" fill="none" '
                     f'stroke="black"/>')
            o.append(f'<path d="M{f.x0},{f.y0 + 16} L{f.x0 + tw},{f.y0 + 16} L{f.x0 + tw + 8},{f.y0 + 8} '
                     f'L{f.x0 + tw + 8},{f.y0}" fill="white" stroke="black"/>')
            o.append(svg_text(f.x0 + 6, f.y0 + 8, f.op, bold=True, size=MSG_FONT, anchor="start"))
            if f.guard:
                g = f"[{f.guard}]"
                o.append(f'<rect x="{f.x0 + tw + 12}" y="{f.y0 + 1}" width="{text_w(g, MSG_FONT) + 4}" height="14" '
                         f'fill="white"/>')
                o.append(svg_text(f.x0 + tw + 14, f.y0 + 8, g, size=MSG_FONT, anchor="start"))
            for y, guard in f.operands:
                o.append(f'<line x1="{f.x0}" y1="{y}" x2="{f.x1}" y2="{y}" stroke="black" stroke-dasharray="6,4"/>')
                g = f"[{guard}]"
                o.append(f'<rect x="{f.x0 + 4}" y="{y + 2}" width="{text_w(g, MSG_FONT) + 4}" height="14" fill="white"/>')
                o.append(svg_text(f.x0 + 6, y + 9, g, size=MSG_FONT, anchor="start"))
        for m in self.messages:
            o.extend(self._svg_message(m))
        for n in self.notes:
            f = 8
            o.append(f'<line x1="{n.anchor[0]}" y1="{n.anchor[1]}" x2="{n.x if n.x > n.anchor[0] else n.x + n.w}" '
                     f'y2="{n.anchor[1]}" stroke="black" stroke-dasharray="4,3"/>')
            o.append(f'<path d="M{n.x},{n.y} L{n.x + n.w - f},{n.y} L{n.x + n.w},{n.y + f} L{n.x + n.w},{n.y + n.h} '
                     f'L{n.x},{n.y + n.h} Z" fill="white" stroke="black"/><path d="M{n.x + n.w - f},{n.y} '
                     f'L{n.x + n.w - f},{n.y + f} L{n.x + n.w},{n.y + f}" fill="none" stroke="black"/>')
            o.append(svg_text(n.x + 8, n.y + n.h / 2, n.text, size=MSG_FONT, anchor="start"))
        o.append("</svg>")
        return "\n".join(o)

    def _edge_x(self, id, y, toward):
        """x where a message meets lifeline id at height y: the side of its innermost open activation."""
        depth = max([a[3] for a in self.activations if a[0] == id and a[1] <= y <= a[2]], default=-1)
        base = self.lines[id].x
        if depth < 0:
            return base
        left, right = base - 5 + depth * 5, base + 5 + depth * 5
        return right if toward > base else left

    def _svg_message(self, m: Message):
        out = []
        x0, y0, x1, y1 = self.label_box(m)
        out.append(f'<rect x="{x0}" y="{y0}" width="{x1 - x0}" height="{y1 - y0}" fill="white" opacity="0.85"/>')
        out.append(svg_text(x0 + 2, (y0 + y1) / 2, m.text, size=MSG_FONT, anchor="start"))
        if m.kind == "self":
            x = self._edge_x(m.src, m.y, 1e9)
            out.append(f'<path d="M{x},{m.y} L{x + 26},{m.y} L{x + 26},{m.y + 14} L{x + 5},{m.y + 14}" '
                       f'fill="none" stroke="black" marker-end="url(#fill)"/>')
            return out
        a, b = self.lines[m.src].x, self.lines[m.dst].x
        sx = self._edge_x(m.src, m.y, b)
        ex = self._edge_x(m.dst, m.y + (1 if m.kind == "sync" else -1), a)
        dash = ' stroke-dasharray="6,4"' if m.kind == "reply" else ""
        mark = "open" if m.kind == "reply" else "fill"
        out.append(f'<line x1="{sx}" y1="{m.y}" x2="{ex}" y2="{m.y}" stroke="black"{dash} marker-end="url(#{mark})"/>')
        return out

    def drawio(self) -> str:
        cells = ['<mxCell id="0"/>', '<mxCell id="1" parent="0"/>']
        H = self.height
        for k, f in enumerate(self.fragments):
            tw = text_w(f.op, MSG_FONT, bold=True) + 22
            cells.append(f'<mxCell id="frag{k}" value={q(f.op)} style="shape=umlFrame;whiteSpace=wrap;html=1;'
                         f'width={tw};height=16;fontStyle=1;fontSize={MSG_FONT};fontFamily={FONT};fillColor=none;'
                         f'strokeColor=#000000;" vertex="1" parent="1"><mxGeometry x="{f.x0}" y="{f.y0}" '
                         f'width="{f.x1 - f.x0}" height="{f.y1 - f.y0}" as="geometry"/></mxCell>')
            guards = ([(f.y0, f.guard, f.x0 + tw + 6)] if f.guard else []) + [(y, g, f.x0 + 6) for y, g in f.operands]
            for j, (y, g, gx) in enumerate(guards):
                cells.append(f'<mxCell id="frag{k}g{j}" value={q("[" + g + "]")} style="text;html=1;align=left;'
                             f'fontSize={MSG_FONT};fontFamily={FONT};" vertex="1" parent="1"><mxGeometry x="{gx}" '
                             f'y="{y}" width="{text_w(g, MSG_FONT) + 20}" height="16" as="geometry"/></mxCell>')
            for j, (y, g) in enumerate(f.operands):
                cells.append(f'<mxCell id="frag{k}s{j}" style="endArrow=none;dashed=1;html=1;strokeColor=#000000;" '
                             f'edge="1" parent="1"><mxGeometry relative="1" as="geometry"><mxPoint x="{f.x0}" '
                             f'y="{y}" as="sourcePoint"/><mxPoint x="{f.x1}" y="{y}" as="targetPoint"/>'
                             f'</mxGeometry></mxCell>')
        for l in self.lines.values():
            x0, y0, w, h = self._head_box(l)
            title = html.escape(l.title).replace("\n", "<br>")
            if l.actor:
                y0, h = self.top, self.head_h
            participant = "participant=umlActor;verticalLabelPosition=bottom;" if l.actor else ""
            cells.append(f'<mxCell id="{l.id}" value={q(title)} style="shape=umlLifeline;{participant}perimeter=lifelinePerimeter;'
                         f'whiteSpace=wrap;html=1;container=0;collapsible=0;size={h};fontSize={MSG_FONT};'
                         f'fontFamily={FONT};strokeColor=#000000;fillColor=#FFFFFF;" vertex="1" parent="1">'
                         f'<mxGeometry x="{x0}" y="{y0}" width="{w}" height="{H - 10 - y0}" as="geometry"/>'
                         f'</mxCell>')
        for k, (id, y0, y1, depth) in enumerate(self.activations):
            x = self.lines[id].x - 5 + depth * 5
            cells.append(f'<mxCell id="act{k}" style="html=1;points=[];fillColor=#FFFFFF;strokeColor=#000000;" '
                         f'vertex="1" parent="1"><mxGeometry x="{x}" y="{y0}" width="10" height="{y1 - y0}" '
                         f'as="geometry"/></mxCell>')
        for k, m in enumerate(self.messages):
            x0, y0, x1, y1 = self.label_box(m)
            if m.kind == "self":
                x = self._edge_x(m.src, m.y, 1e9)
                pts = (x, m.y), (x + 5, m.y + 14)
                via = f'<Array as="points"><mxPoint x="{x + 26}" y="{m.y}"/><mxPoint x="{x + 26}" y="{m.y + 14}"/></Array>'
            else:
                a, b = self.lines[m.src].x, self.lines[m.dst].x
                pts = (self._edge_x(m.src, m.y, b), m.y), (self._edge_x(m.dst, m.y, a), m.y)
                via = ""
            style = ("dashed=1;endArrow=open;" if m.kind == "reply" else "endArrow=block;endFill=1;")
            cells.append(f'<mxCell id="m{k}" style="html=1;{style}endSize=8;strokeColor=#000000;rounded=0;" '
                         f'edge="1" parent="1"><mxGeometry relative="1" as="geometry"><mxPoint x="{pts[0][0]}" '
                         f'y="{pts[0][1]}" as="sourcePoint"/><mxPoint x="{pts[1][0]}" y="{pts[1][1]}" '
                         f'as="targetPoint"/>{via}</mxGeometry></mxCell>')
            if m.text:
                cells.append(f'<mxCell id="m{k}l" value={q(html.escape(m.text).replace(chr(10), "<br>"))} '
                             f'style="text;html=1;align=left;verticalAlign=middle;fontSize={MSG_FONT};'
                             f'fontFamily={FONT};labelBackgroundColor=#FFFFFF;" vertex="1" parent="1"><mxGeometry '
                             f'x="{x0}" y="{y0}" width="{x1 - x0}" height="{y1 - y0}" as="geometry"/></mxCell>')
        for k, n in enumerate(self.notes):
            cells.append(f'<mxCell id="note{k}" value={q(html.escape(n.text).replace(chr(10), "<br>"))} '
                         f'style="shape=note;size=8;whiteSpace=wrap;html=1;align=left;spacingLeft=6;'
                         f'fontSize={MSG_FONT};fontFamily={FONT};strokeColor=#000000;fillColor=#FFFFFF;" vertex="1" '
                         f'parent="1"><mxGeometry x="{n.x}" y="{n.y}" width="{n.w}" height="{n.h}" as="geometry"/>'
                         f'</mxCell>')
            nx = n.x if n.x > n.anchor[0] else n.x + n.w
            cells.append(f'<mxCell id="note{k}a" style="endArrow=none;dashed=1;html=1;strokeColor=#000000;" '
                         f'edge="1" parent="1"><mxGeometry relative="1" as="geometry"><mxPoint x="{n.anchor[0]}" '
                         f'y="{n.anchor[1]}" as="sourcePoint"/><mxPoint x="{nx}" y="{n.anchor[1]}" '
                         f'as="targetPoint"/></mxGeometry></mxCell>')
        return (f'<mxfile host="seqdiagram.py"><diagram name={q(self.name)} id={q(self.name)}><mxGraphModel '
                f'grid="1" gridSize="10" page="1" pageWidth="{int(self.width)}" pageHeight="{int(H)}"><root>'
                f'{"".join(cells)}</root></mxGraphModel></diagram></mxfile>\n')
