#!/usr/bin/env python3
"""Validate the design reading edition, using only the Python standard library.

Default: require all Markdown documents and HTML section-link destinations.
--preview: explicitly defer missing pages; still check existing pages fully and
check deferred section IDs against the target Markdown. Never hides a mismatch.
"""

import argparse
from collections import Counter
from html.parser import HTMLParser
from pathlib import Path
import re
import sys
from urllib.parse import unquote, urlsplit


ROOT = Path(__file__).resolve().parents[1]
DESIGN = ROOT / "docs/phase_2/design"
VOID = {"area", "base", "br", "col", "embed", "hr", "img", "input", "link", "meta", "param", "source", "track", "wbr"}
SECTION = re.compile(r"^#{1,6}\s+(?:\*\*)?(?:\u00a7\s*)?(\d+[a-z]?(?:\.\d+[a-z]?)*)(?=[.\s:)])")


def markdown_parts(text):
    sections, blocks, prose = [], [], []
    fence = None
    block = []
    frontmatter = text.startswith("---\n")
    for number, line in enumerate(text.splitlines(keepends=True)):
        if frontmatter:
            prose.append(line)
            if number and line.strip() == "---":
                frontmatter = False
            continue
        opening = re.match(r"^[ \t]*(`{3,}|~{3,})(.*)$", line.rstrip("\r\n"))
        if fence:
            closing = re.fullmatch(r"(.*?)" + re.escape(fence[0]) + "{" + str(len(fence)) + r",}[ \t]*\r?\n?", line)
            if closing:
                # Some source fences close on the SQL line itself; retain every
                # character before that fence rather than silently dropping SQL.
                if closing.group(1).strip():
                    block.append(closing.group(1))
                blocks.append("".join(block))
                fence, block = None, []
            else:
                block.append(line)
            continue
        if opening:
            fence = opening.group(1)
            continue
        prose.append(line)
        heading = SECTION.match(line)
        if heading:
            sections.append("sec-" + heading.group(1).replace(".", "-"))
    if fence:
        raise ValueError("Unclosed Markdown code fence")
    identifiers = {match.group(2) for match in re.finditer(r"(`+)([^`\n]+?)\1", "".join(prose))}
    return sections, blocks, identifiers


class Document(HTMLParser):
    def __init__(self, path):
        super().__init__(convert_charrefs=True)
        self.path = path
        self.ids = []
        self.sections = []
        self.heading_sections = []
        self.links = []
        self.blocks = []
        self.text = []
        self.stack = []
        self.errors = []
        self.code = None
        self.figure_count = 0
        self.lang = None
        self.title = []
        self.h1_count = 0
        self.doctype = False
        self.feed(path.read_text(encoding="utf-8"))
        self.close()
        if self.stack:
            self.errors.append("Unclosed HTML tags: " + str([tag for tag, _ in self.stack]))
        if self.lang != "vi":
            self.errors.append('Expected lang="vi"')
        if not self.doctype:
            self.errors.append("Missing HTML doctype")
        if self.h1_count != 1:
            self.errors.append("Expected exactly one h1")
        if not "".join(self.title).strip():
            self.errors.append("Missing title")
        duplicates = [key for key, count in Counter(self.ids).items() if count > 1]
        if duplicates:
            self.errors.append("Duplicate IDs: " + ", ".join(duplicates))

    def handle_decl(self, declaration):
        self.doctype |= declaration.lower() == "doctype html"

    def handle_startendtag(self, tag, attributes):
        self.handle_starttag(tag, attributes)
        if tag not in VOID:
            self.handle_endtag(tag)

    def handle_starttag(self, tag, attributes):
        attrs = dict(attributes)
        if tag == "html":
            self.lang = attrs.get("lang")
        if tag == "h1":
            self.h1_count += 1
        if tag == "style" or "style" in attrs:
            self.errors.append("Embedded/inline CSS is forbidden")
        if any(key.startswith("on") for key in attrs):
            self.errors.append("Inline event handler is forbidden")
        if tag == "script":
            if not attrs.get("src"):
                self.errors.append("Inline script is forbidden")
            elif attrs["src"] == "assets/design.js" and "defer" not in attrs:
                self.errors.append("design.js must use defer")
        if "id" in attrs:
            self.ids.append(attrs["id"])
            if attrs["id"].startswith("sec-"):
                if tag != "section":
                    self.errors.append("Section ID must be on a section element")
                self.sections.append(attrs["id"])
        if tag in {"h2", "h3", "h4", "h5", "h6"} and self.stack:
            parent_tag, parent_attrs = self.stack[-1]
            # Only the numbered heading counts; unnumbered sub-headings may follow inside a section.
            if (parent_tag == "section" and parent_attrs.get("id", "").startswith("sec-")
                    and attrs.get("id") == "heading-" + parent_attrs["id"]):
                self.heading_sections.append(parent_attrs["id"])
        if "href" in attrs:
            self.links.append((attrs["href"], attrs))
        # Original reference names remain available in link titles/accessibility text.
        for key in ("title", "aria-label"):
            if attrs.get(key):
                self.text.append(attrs[key])
        if tag == "figure" and "code" in attrs.get("class", "").split():
            self.figure_count += 1
        if tag == "code" and self.stack and self.stack[-1][0] == "pre" and any(
            name == "figure" and "code" in data.get("class", "").split() for name, data in self.stack
        ):
            self.code = []
        if tag not in VOID:
            self.stack.append((tag, attrs))

    def handle_endtag(self, tag):
        if tag in VOID:
            self.errors.append("Unexpected closing void tag: " + tag)
            return
        if not self.stack or self.stack[-1][0] != tag:
            self.errors.append("Mismatched closing tag: " + tag)
            return
        if tag == "code" and self.code is not None:
            self.blocks.append("".join(self.code))
            self.code = None
        self.stack.pop()

    def handle_data(self, data):
        if not any(tag in {"script", "style"} for tag, _ in self.stack):
            self.text.append(data)
        if self.code is not None:
            self.code.append(data)
        if self.stack and self.stack[-1][0] == "title":
            self.title.append(data)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--preview", action="store_true", help="Explicitly allow missing preview pages; report all deferred links")
    args = parser.parse_args()
    sources = {path.stem: markdown_parts(path.read_text(encoding="utf-8")) for path in sorted(DESIGN.glob("*.md"))}
    documents = {path.resolve(): Document(path) for path in sorted((DESIGN / "html").glob("*.html"))}
    errors, deferred = [], set()
    print(f"{'Document':47} {'Sections':10} {'Code':8} {'Identifiers':13} Result")
    print("-" * 96)
    checked = 0
    for name, (sections, blocks, identifiers) in sources.items():
        path = (DESIGN / "html" / (name + ".html")).resolve()
        doc = documents.get(path)
        if not doc:
            print(f"{name:47} {'-':10} {'-':8} {'-':13} {'PENDING' if args.preview else 'FAIL missing HTML'}")
            if not args.preview:
                errors.append(name + ": missing HTML")
            continue
        checked += 1
        local_errors = list(doc.errors)
        if sections != doc.sections:
            local_errors.append(f"Section IDs/order differ: MD={sections!r}; HTML={doc.sections!r}")
        if doc.heading_sections != sections:
            local_errors.append("Each source section needs its own heading, in source order")
        if len(blocks) != doc.figure_count or len(blocks) != len(doc.blocks):
            local_errors.append("Code block count differs")
        if blocks != doc.blocks:
            local_errors.append("Code block content/order differs (including whitespace)")
        html_text = "".join(doc.text)
        # A `\|` inside a Markdown table cell renders as a literal pipe.
        missing = sorted(i for i in identifiers if i.replace("\\|", "|") not in html_text)
        if missing:
            local_errors.append("Missing backtick identifiers: " + repr(missing))
        if "".join(doc.title).strip() != name:
            local_errors.append("Title must equal source document name")
        print(f"{name:47} {str(len(doc.sections))+'/'+str(len(sections)):10} {str(doc.figure_count)+'/'+str(len(blocks)):8} {str(len(identifiers)-len(missing))+'/'+str(len(identifiers)):13} {'FAIL' if local_errors else 'PASS'}")
        errors.extend(name + ": " + error for error in local_errors)
    if not checked:
        errors.append("No document HTML pairs were checked")
    index = (DESIGN / "html/index.html").resolve()
    if index not in documents:
        errors.append("index.html is missing")
    else:
        errors.extend("index.html: " + error for error in documents[index].errors)
    for path, doc in documents.items():
        for href, attrs in doc.links:
            url = urlsplit(href)
            if url.scheme or url.netloc:
                continue
            target = (path.parent / unquote(url.path)).resolve() if url.path else path
            if not target.is_relative_to(ROOT):
                errors.append(f"{path.name}: local link leaves repository: {href}")
                continue
            fragment = unquote(url.fragment)
            if target.suffix == ".html" and fragment.startswith("sec-"):
                dest = documents.get(target)
                if dest:
                    if fragment not in dest.ids:
                        errors.append(f"{path.name}: missing section target: {href}")
                elif args.preview and target.stem in sources and target.parent == (DESIGN / "html").resolve():
                    if fragment not in sources[target.stem][0]:
                        errors.append(f"{path.name}: deferred target absent even in Markdown: {href}")
                    else:
                        deferred.add(href)
                else:
                    errors.append(f"{path.name}: missing HTML link target: {href}")
            elif not target.exists():
                if args.preview and target.suffix == ".html" and target.stem in sources and "data-pending" in attrs:
                    deferred.add(href)
                else:
                    errors.append(f"{path.name}: missing local link target: {href}")
            elif fragment and target.suffix == ".html" and target in documents and fragment not in documents[target].ids:
                errors.append(f"{path.name}: missing local fragment: {href}")
            if "data-source" in attrs and not (path.parent / attrs["data-source"].split("#", 1)[0]).resolve().is_file():
                errors.append(f"{path.name}: missing preview Markdown fallback: {attrs['data-source']}")
    for error in errors:
        print("ERROR: " + error)
    if deferred:
        print(f"\nDEFERRED: {len(deferred)} HTML destinations have no preview page yet; Markdown section IDs checked.")
        for href in sorted(deferred):
            print("  " + href)
    print(f"\nChecked {checked}/{len(sources)} document pairs; {len(errors)} error(s).")
    if args.preview:
        print("PREVIEW ONLY: run without --preview to require all 17 pages and real HTML destinations.")
    return 1 if errors else 0


if __name__ == "__main__":
    sys.exit(main())
