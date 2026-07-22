const D = require("docx");
const {
  Document, Paragraph, TextRun, HeadingLevel, AlignmentType,
  Table, TableRow, TableCell, WidthType, BorderStyle, ShadingType,
  TableOfContents, PageNumber, Header, Footer, PageBreak, LevelFormat,
} = D;

const T = {
  INK: "111827", HEAD: "1F2A37", SUB: "374151", MUTE: "6B7280",
  HFILL: "374151", BFILL: "F3F4F6", RISKFILL: "FDF2F2", ZEBRA: "F9FAFB",
  BORDER: "D1D5DB", RULE: "9CA3AF", FONT: "Calibri", CONTENT_W: 9638,
};

const thin = { style: BorderStyle.SINGLE, size: 4, color: T.BORDER };
const cellBorders = { top: thin, bottom: thin, left: thin, right: thin };
const runs = (text, o = {}) => new TextRun({ text, font: T.FONT, size: o.size || 21, color: o.color || T.INK, bold: o.bold || false, italics: o.italics || false });

// Heading demotion for merged document. Default 0 = standalone behaviour unchanged.
let HOFF = 0;
function setHeadingOffset(n) { HOFF = n; }
const _HLV = [HeadingLevel.HEADING_1, HeadingLevel.HEADING_2, HeadingLevel.HEADING_3, HeadingLevel.HEADING_4, HeadingLevel.HEADING_5];
const _HSZ = [30, 25, 22, 21, 20];
const _lv = (base) => _HLV[Math.min(base + HOFF, 4)];
const _sz = (base) => _HSZ[Math.min(base + HOFF, 4)];

function P(text, o = {}) {
  return new Paragraph({
    spacing: { after: o.after ?? 120, before: o.before ?? 0, line: o.line ?? 276 },
    alignment: o.align || (o.justify === false ? AlignmentType.LEFT : AlignmentType.JUSTIFIED),
    indent: o.indent,
    children: Array.isArray(text) ? text : [runs(text, o)],
  });
}
function H1(text) {
  return new Paragraph({
    heading: _lv(0), spacing: { before: 320, after: 120 },
    border: { bottom: { style: BorderStyle.SINGLE, size: 6, color: T.BORDER, space: 6 } },
    children: [new TextRun({ text, font: T.FONT, size: _sz(0), bold: true, color: T.HEAD })],
  });
}
function H2(text) {
  return new Paragraph({
    heading: _lv(1), spacing: { before: 240, after: 80 },
    children: [new TextRun({ text, font: T.FONT, size: _sz(1), bold: true, color: T.HEAD })],
  });
}
function H3(text) {
  return new Paragraph({
    heading: _lv(2), spacing: { before: 180, after: 60 },
    children: [new TextRun({ text, font: T.FONT, size: _sz(2), bold: true, color: T.SUB })],
  });
}
function bullet(text, o = {}) {
  return new Paragraph({
    numbering: { reference: "bullets", level: 0 }, spacing: { after: o.after ?? 60, line: 276 },
    children: Array.isArray(text) ? text : [runs(text, o)],
  });
}
function numbered(text, o = {}) {
  return new Paragraph({
    numbering: { reference: o.ref || "nums", level: 0 }, spacing: { after: o.after ?? 60, line: 276 },
    children: Array.isArray(text) ? text : [runs(text, o)],
  });
}
function quote(text, who) {
  const bar = { left: { style: BorderStyle.SINGLE, size: 18, color: T.RULE, space: 12 } };
  const out = [new Paragraph({
    border: bar, indent: { left: 360 }, spacing: { before: 120, after: 40, line: 276 },
    children: [new TextRun({ text: "“" + text + "”", font: T.FONT, size: 21, italics: true, color: T.SUB })],
  })];
  if (who) out.push(new Paragraph({
    border: bar, indent: { left: 360 }, spacing: { after: 140, line: 240 },
    children: [new TextRun({ text: "— " + who, font: T.FONT, size: 19, color: T.MUTE })],
  }));
  return out;
}
// callout box: kind 'legal' (label "Căn cứ pháp lý") or 'risk' (custom label) or 'note'
function callout(label, text, kind = "legal") {
  const fill = kind === "risk" ? T.RISKFILL : T.BFILL;
  const barColor = kind === "risk" ? "B45454" : T.SUB;
  const prefix = kind === "legal" ? "Căn cứ pháp lý — " + label + ". " : label + " ";
  const children = [new TextRun({ text: prefix, font: T.FONT, size: 20, bold: true, color: T.HEAD })];
  if (Array.isArray(text)) text.forEach(t => children.push(t)); else children.push(new TextRun({ text, font: T.FONT, size: 20, color: T.SUB }));
  return new Paragraph({
    shading: { type: ShadingType.CLEAR, color: "auto", fill },
    border: {
      top: { style: BorderStyle.SINGLE, size: 2, color: T.BORDER, space: 6 },
      bottom: { style: BorderStyle.SINGLE, size: 2, color: T.BORDER, space: 6 },
      left: { style: BorderStyle.SINGLE, size: 12, color: barColor, space: 10 },
      right: { style: BorderStyle.SINGLE, size: 2, color: T.BORDER, space: 6 },
    },
    spacing: { before: 120, after: 140, line: 264 }, indent: { left: 60, right: 60 },
    children,
  });
}
const legal = (label, text) => callout(label, text, "legal");
const risk = (label, text) => callout(label, text, "risk");
function src(text) {
  return new Paragraph({ spacing: { after: 140, line: 240 }, children: [new TextRun({ text: "Nguồn: " + text, font: T.FONT, size: 17, color: T.MUTE, italics: true })] });
}
function cell(content, o = {}) {
  const arr = Array.isArray(content) ? content : [content];
  const kids = arr.map((line, i) => new Paragraph({
    alignment: o.align || AlignmentType.LEFT,
    spacing: { after: i === arr.length - 1 ? 0 : 40, line: 252 },
    children: (Array.isArray(line) ? line : [new TextRun({ text: line, font: T.FONT, size: o.size || 19, bold: o.bold || false, color: o.color || T.INK })]),
  }));
  return new TableCell({
    width: { size: o.w, type: WidthType.DXA },
    shading: o.fill ? { type: ShadingType.CLEAR, color: "auto", fill: o.fill } : undefined,
    margins: { top: 60, bottom: 60, left: 90, right: 90 },
    verticalAlign: D.VerticalAlign.CENTER, borders: cellBorders, children: kids,
  });
}
function table(widths, headers, rows, opts = {}) {
  const headRow = new TableRow({
    tableHeader: true,
    cantSplit: true,
    children: headers.map((h, i) => cell(h, { w: widths[i], fill: T.HFILL, color: "FFFFFF", bold: true, size: 19, align: opts.headAlign?.[i] })),
  });
  const bodyRows = rows.map((r, ri) => new TableRow({
    cantSplit: true,
    children: r.map((c, i) => cell(c, { w: widths[i], fill: ri % 2 === 1 ? T.ZEBRA : undefined, size: opts.size || 19, align: opts.colAlign?.[i], bold: opts.boldCol?.[i] })),
  }));
  return new Table({
    columnWidths: widths, width: { size: widths.reduce((a, b) => a + b, 0), type: WidthType.DXA },
    borders: { top: thin, bottom: thin, left: thin, right: thin, insideHorizontal: thin, insideVertical: thin },
    rows: [headRow, ...bodyRows],
  });
}
const spacer = (h = 60) => new Paragraph({ spacing: { after: h }, children: [] });

function cover(kicker, title, subtitle, meta) {
  return [
    new Paragraph({ spacing: { before: 600, after: 0 }, children: [new TextRun({ text: kicker, font: T.FONT, size: 26, bold: true, color: T.SUB, characterSpacing: 60 })] }),
    new Paragraph({ spacing: { before: 120, after: 60 }, border: { bottom: { style: BorderStyle.SINGLE, size: 8, color: T.HEAD, space: 8 } }, children: [new TextRun({ text: title, font: T.FONT, size: 44, bold: true, color: T.HEAD })] }),
    new Paragraph({ spacing: { before: 120, after: 40 }, children: [new TextRun({ text: subtitle, font: T.FONT, size: 23, color: T.SUB, italics: true })] }),
    new Paragraph({ spacing: { before: 240 }, children: [new TextRun({ text: meta[0], font: T.FONT, size: 20, color: T.MUTE })] }),
    new Paragraph({ spacing: { after: 0 }, children: [new TextRun({ text: meta[1], font: T.FONT, size: 20, color: T.MUTE })] }),
    new Paragraph({ spacing: { before: 360, after: 0 }, children: [new PageBreak()] }),
  ];
}
function toc() {
  return [
    new Paragraph({ spacing: { after: 120 }, children: [new TextRun({ text: "Mục lục", font: T.FONT, size: 28, bold: true, color: T.HEAD })] }),
    new TableOfContents("Mục lục", { hyperlink: true, headingStyleRange: "1-2" }),
    new Paragraph({ spacing: { before: 240 }, children: [new PageBreak()] }),
  ];
}
const endMark = () => new Paragraph({ spacing: { before: 300 }, border: { top: { style: BorderStyle.SINGLE, size: 6, color: T.BORDER, space: 6 } }, children: [new TextRun({ text: "— Hết tài liệu —", font: T.FONT, size: 19, color: T.MUTE, italics: true })] });

function buildDoc(children, o) {
  return new Document({
    creator: "AgriContract", title: o.title, features: { updateFields: true },
    styles: {
      default: { document: { run: { font: T.FONT, size: 21, color: T.INK } } },
      paragraphStyles: [
        { id: "Heading1", name: "Heading 1", basedOn: "Normal", next: "Normal", quickFormat: true, run: { font: T.FONT, size: 30, bold: true, color: T.HEAD }, paragraph: { outlineLevel: 0 } },
        { id: "Heading2", name: "Heading 2", basedOn: "Normal", next: "Normal", quickFormat: true, run: { font: T.FONT, size: 25, bold: true, color: T.HEAD }, paragraph: { outlineLevel: 1 } },
        { id: "Heading3", name: "Heading 3", basedOn: "Normal", next: "Normal", quickFormat: true, run: { font: T.FONT, size: 22, bold: true, color: T.SUB }, paragraph: { outlineLevel: 2 } },
        { id: "Heading4", name: "Heading 4", basedOn: "Normal", next: "Normal", quickFormat: true, run: { font: T.FONT, size: 21, bold: true, color: T.SUB }, paragraph: { outlineLevel: 3 } },
      ],
    },
    numbering: {
      config: [
        { reference: "bullets", levels: [{ level: 0, format: LevelFormat.BULLET, text: "•", alignment: AlignmentType.LEFT, style: { run: { color: T.SUB }, paragraph: { indent: { left: 360, hanging: 220 } } } }] },
        { reference: "nums", levels: [{ level: 0, format: LevelFormat.DECIMAL, text: "%1.", alignment: AlignmentType.LEFT, style: { paragraph: { indent: { left: 360, hanging: 220 } } } }] },
      ],
    },
    sections: [{
      properties: { page: { size: { width: 11906, height: 16838 }, margin: { top: 1134, bottom: 1134, left: 1134, right: 1134 } } },
      headers: { default: new Header({ children: [new Paragraph({ alignment: AlignmentType.RIGHT, spacing: { after: 0 }, children: [new TextRun({ text: o.headerText, font: T.FONT, size: 16, color: T.MUTE })] })] }) },
      footers: { default: new Footer({ children: [new Paragraph({ alignment: AlignmentType.CENTER, spacing: { before: 0 }, children: [new TextRun({ text: o.footerText + " · Trang ", font: T.FONT, size: 16, color: T.MUTE }), new TextRun({ children: [PageNumber.CURRENT], font: T.FONT, size: 16, color: T.MUTE }), new TextRun({ text: "/", font: T.FONT, size: 16, color: T.MUTE }), new TextRun({ children: [PageNumber.TOTAL_PAGES], font: T.FONT, size: 16, color: T.MUTE })] })] }) },
      children,
    }],
  });
}

function tocRange(range) {
  return [
    new Paragraph({ spacing: { after: 120 }, children: [new TextRun({ text: "Mục lục", font: T.FONT, size: 28, bold: true, color: T.HEAD })] }),
    new TableOfContents("Mục lục", { hyperlink: true, headingStyleRange: range || "1-2" }),
    new Paragraph({ spacing: { before: 240 }, children: [new PageBreak()] }),
  ];
}
// Part-divider banner (real HEADING_1, unaffected by HOFF) for merged doc chapters
function partDivider(kicker, title) {
  return [
    new Paragraph({ children: [new PageBreak()], spacing: { after: 0 } }),
    new Paragraph({ spacing: { before: 1600, after: 0 }, children: [new TextRun({ text: kicker, font: T.FONT, size: 24, bold: true, color: T.MUTE, characterSpacing: 40 })] }),
    new Paragraph({ heading: HeadingLevel.HEADING_1, spacing: { before: 120, after: 0 }, border: { bottom: { style: BorderStyle.SINGLE, size: 8, color: T.HEAD, space: 8 } }, children: [new TextRun({ text: title, font: T.FONT, size: 40, bold: true, color: T.HEAD })] }),
    new Paragraph({ children: [new PageBreak()], spacing: { before: 0 } }),
  ];
}
module.exports = { D, T, runs, P, H1, H2, H3, bullet, numbered, quote, callout, legal, risk, src, cell, table, spacer, cover, toc, tocRange, partDivider, endMark, buildDoc, setHeadingOffset };
