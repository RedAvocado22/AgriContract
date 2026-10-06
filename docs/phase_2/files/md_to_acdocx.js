const fs = require("fs");
const path = require("path");
const { D, T, P, H1, H2, H3, bullet, numbered, quote, callout, src, table } = require("./acdocx.js");
const { TextRun } = D;

function stripFrontMatter(markdown) {
  const lines = markdown.replace(/\r\n/g, "\n").split("\n");
  if (lines[0] !== "---") return lines;
  const end = lines.indexOf("---", 1);
  return end >= 0 ? lines.slice(end + 1) : lines;
}

function plain(text) {
  return text
    .replace(/\\([`*_{}\[\]$|])/g, "$1")
    .replace(/\[([^\]]+)\]\(([^)]+)\)/g, "$1 ($2)")
    .replace(/\*\*([^*]+)\*\*/g, "$1")
    .replace(/\*([^*]+)\*/g, "$1")
    .replace(/`([^`]+)`/g, "$1")
    .replace(/<br\s*\/?\s*>/gi, " ")
    .trim();
}

function inlineRuns(text, opts = {}) {
  text = text
    .replace(/<br\s*\/?\s*>/gi, " · ")
    .replace(/_Nguồn:/g, "Nguồn:")
    .replace(/_\s*$/g, "")
    .replace(/\\([`*_{}\[\]$|])/g, "$1")
    .replace(/\[([^\]]+)\]\(([^)]+)\)/g, "$1 ($2)");

  const out = [];
  let i = 0;
  let buf = "";
  let bold = false;
  let italics = false;
  let code = false;

  const flush = () => {
    if (!buf) return;
    out.push(new TextRun({
      text: buf,
      font: code ? "Consolas" : T.FONT,
      size: code ? Math.max(16, (opts.size || 21) - 2) : (opts.size || 21),
      color: opts.color || T.INK,
      bold: opts.bold || bold,
      italics: opts.italics || italics,
    }));
    buf = "";
  };

  while (i < text.length) {
    if (!code && text.startsWith("**", i)) {
      flush(); bold = !bold; i += 2; continue;
    }
    if (text[i] === "`") {
      flush(); code = !code; i += 1; continue;
    }
    if (!code && text[i] === "*") {
      flush(); italics = !italics; i += 1; continue;
    }
    buf += text[i++];
  }
  flush();
  return out.length ? out : [new TextRun({ text: "", font: T.FONT, size: opts.size || 21 })];
}

function splitTableRow(line) {
  const s = line.trim().replace(/^\|/, "").replace(/\|$/, "");
  const cells = [];
  let buf = "";
  let escaped = false;
  for (const ch of s) {
    if (escaped) { buf += ch; escaped = false; continue; }
    if (ch === "\\") { escaped = true; continue; }
    if (ch === "|") { cells.push(buf.trim()); buf = ""; continue; }
    buf += ch;
  }
  cells.push(buf.trim());
  return cells;
}

function isSeparatorRow(line) {
  if (!/^\s*\|/.test(line)) return false;
  return splitTableRow(line).every(c => /^:?-{3,}:?$/.test(c.replace(/\s/g, "")));
}

function computeWidths(headers, rows) {
  const colCount = headers.length;
  const weights = Array.from({ length: colCount }, (_, i) => {
    const lengths = [headers, ...rows].map(r => plain(r[i] || "").length);
    const max = Math.max(...lengths, 4);
    return Math.max(5, Math.min(42, Math.sqrt(max) * 5));
  });
  const min = colCount >= 6 ? 850 : colCount === 5 ? 1050 : colCount === 4 ? 1350 : 1650;
  const totalMin = min * colCount;
  const remaining = Math.max(0, T.CONTENT_W - totalMin);
  const sum = weights.reduce((a, b) => a + b, 0);
  const widths = weights.map(w => min + Math.floor(remaining * w / sum));
  widths[widths.length - 1] += T.CONTENT_W - widths.reduce((a, b) => a + b, 0);
  return widths;
}

function renderTable(lines) {
  const headers = splitTableRow(lines[0]);
  const rows = lines.slice(2).map(splitTableRow).filter(r => r.some(Boolean));
  const widths = computeWidths(headers, rows);
  const size = headers.length >= 6 ? 16 : headers.length === 5 ? 17 : headers.length === 4 ? 18 : 19;
  const hdr = headers.map(c => [inlineRuns(c, { size, color: "FFFFFF", bold: true })]);
  const body = rows.map(r => headers.map((_, i) => [inlineRuns(r[i] || "", { size })]));
  return table(widths, hdr, body, { size });
}

function renderQuote(lines) {
  const cleaned = lines.map(l => l.replace(/^\s*>\s?/, "").trim());
  const joined = cleaned.filter(Boolean).join(" ").trim();
  if (/^\*\*Căn cứ pháp lý\s*[—-]/i.test(joined)) {
    const m = joined.match(/^\*\*Căn cứ pháp lý\s*[—-]\s*([^*]+)\*\*\s*(.*)$/i);
    if (m) return [callout(m[1].replace(/[.\s]+$/, ""), plain(m[2]), "legal")];
  }

  const out = [];
  let current = [];
  const chunks = [];
  for (const line of cleaned) {
    if (!line) {
      if (current.length) { chunks.push(current.join(" ")); current = []; }
    } else current.push(line);
  }
  if (current.length) chunks.push(current.join(" "));

  for (let i = 0; i < chunks.length; i++) {
    const q = chunks[i];
    if (/^[—-]\s*/.test(q)) continue;
    const who = i + 1 < chunks.length && /^[—-]\s*/.test(chunks[i + 1])
      ? chunks[++i].replace(/^[—-]\s*/, "")
      : undefined;
    const text = plain(q).replace(/^[“\"]/, "").replace(/[”\"]$/, "");
    out.push(...quote(text, who ? plain(who) : undefined));
  }
  return out.length ? out : [P(inlineRuns(joined))];
}

function isStructural(lines, i) {
  const line = lines[i] || "";
  if (!line.trim()) return true;
  if (/^#{1,6}\s+/.test(line)) return true;
  if (/^\s*>/.test(line)) return true;
  if (/^\s*[-*]\s+/.test(line)) return true;
  if (/^\s*\d+\.\s+/.test(line)) return true;
  if (/^\s*\|/.test(line) && isSeparatorRow(lines[i + 1] || "")) return true;
  return false;
}

function renderMarkdown(markdown) {
  const lines = stripFrontMatter(markdown);
  const body = [];
  let i = 0;

  while (i < lines.length) {
    const line = lines[i];
    if (!line.trim()) { i++; continue; }
    if (/^\s*<!--.*-->\s*$/.test(line)) { i++; continue; }

    const hm = line.match(/^(#{1,6})\s+(.*)$/);
    if (hm) {
      const level = hm[1].length;
      const text = plain(hm[2]);
      body.push(level === 1 ? H1(text) : level === 2 ? H2(text) : H3(text));
      i++; continue;
    }

    if (/^\s*\|/.test(line) && isSeparatorRow(lines[i + 1] || "")) {
      const block = [line, lines[i + 1]];
      i += 2;
      while (i < lines.length && /^\s*\|/.test(lines[i])) block.push(lines[i++]);
      body.push(renderTable(block));
      continue;
    }

    if (/^\s*>/.test(line)) {
      const block = [];
      while (i < lines.length && (/^\s*>/.test(lines[i]) || !lines[i].trim())) {
        if (!lines[i].trim() && i + 1 < lines.length && !/^\s*>/.test(lines[i + 1])) break;
        block.push(lines[i++]);
      }
      body.push(...renderQuote(block));
      continue;
    }

    if (/^\s*[-*]\s+/.test(line)) {
      const text = line.replace(/^\s*[-*]\s+/, "");
      if (/^Nguồn\s*:/i.test(plain(text))) body.push(src(plain(text).replace(/^Nguồn\s*:\s*/i, "")));
      else body.push(bullet(inlineRuns(text)));
      i++; continue;
    }

    if (/^\s*\d+\.\s+/.test(line)) {
      const m = line.match(/^\s*(\d+)\.\s+(.*)$/);
      body.push(P(inlineRuns(`${m[1]}. ${m[2]}`), { indent: { left: 360, hanging: 220 }, after: 60 }));
      i++; continue;
    }

    const para = [line.trim()];
    i++;
    while (i < lines.length && !isStructural(lines, i)) {
      para.push(lines[i].trim());
      i++;
    }
    const text = para.join(" ").trim();
    if (/^\*Nguồn\s*:/i.test(text) && /\*$/.test(text)) {
      body.push(src(plain(text).replace(/^Nguồn\s*:\s*/i, "")));
    } else {
      body.push(P(inlineRuns(text)));
    }
  }
  return body;
}

function extractTopLevelSections(markdown, fromIndex, toIndex) {
  const lines = stripFrontMatter(markdown);
  const starts = [];
  lines.forEach((line, idx) => { if (/^#\s+/.test(line)) starts.push(idx); });
  if (!starts.length) return markdown;
  const start = starts[fromIndex];
  const end = toIndex + 1 < starts.length ? starts[toIndex + 1] : lines.length;
  return lines.slice(start, end).join("\n");
}

function readContent(name) {
  return fs.readFileSync(path.join(__dirname, "content", name), "utf8");
}

module.exports = { renderMarkdown, extractTopLevelSections, readContent, inlineRuns, plain };
