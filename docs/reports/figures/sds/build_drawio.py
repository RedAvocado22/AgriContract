"""Wrap each rendered PlantUML diagram in a .drawio file.

draw.io keeps PlantUML diagrams as an image shape that carries its source in the
`plantUmlData` style key, so the file opens as a picture and can be edited again
from draw.io (Arrange > Insert > Advanced > PlantUML, or double-click the shape).

Usage: python3 build_drawio.py   (run after render.sh produced the .svg files)
"""
import base64
import json
import pathlib
import re
import urllib.parse
from xml.sax.saxutils import quoteattr

HERE = pathlib.Path(__file__).resolve().parent


def svg_size(svg: str) -> tuple[int, int]:
    m = re.search(r'viewBox="[\d.]+ [\d.]+ ([\d.]+) ([\d.]+)"', svg)
    if m:
        return int(float(m.group(1))), int(float(m.group(2)))
    w = re.search(r'width="([\d.]+)px"', svg)
    h = re.search(r'height="([\d.]+)px"', svg)
    return int(float(w.group(1))), int(float(h.group(1)))


def inline_includes(src: str) -> str:
    # draw.io renders with its own PlantUML server, which cannot see local includes.
    def repl(m):
        return (HERE / m.group(1)).read_text(encoding="utf-8")
    return re.sub(r"^!include (\S+)$", repl, src, flags=re.M)


for puml in sorted(HERE.glob("fig*.puml")):
    svg_path = puml.with_suffix(".svg")
    if not svg_path.exists():
        raise SystemExit(f"missing {svg_path.name}; run render.sh first")
    svg = svg_path.read_text(encoding="utf-8")
    width, height = svg_size(svg)
    source = inline_includes(puml.read_text(encoding="utf-8"))
    image = "data:image/svg+xml," + base64.b64encode(svg.encode("utf-8")).decode("ascii")
    plantuml_data = urllib.parse.quote(json.dumps({"data": source, "format": "svg"}), safe="")
    style = (
        "shape=image;noLabel=1;verticalAlign=top;aspect=fixed;imageAspect=0;"
        f"image={image};plantUmlData={plantuml_data};"
    )
    xml = f"""<mxfile host="app.diagrams.net">
  <diagram name={quoteattr(puml.stem)} id={quoteattr(puml.stem)}>
    <mxGraphModel dx="1000" dy="800" grid="0" gridSize="10" guides="1" tooltips="1" connect="1" arrows="1" fold="1" page="0" pageScale="1" pageWidth="{width}" pageHeight="{height}" math="0" shadow="0">
      <root>
        <mxCell id="0" />
        <mxCell id="1" parent="0" />
        <mxCell id="plantuml" value="" style={quoteattr(style)} vertex="1" parent="1">
          <mxGeometry x="0" y="0" width="{width}" height="{height}" as="geometry" />
        </mxCell>
      </root>
    </mxGraphModel>
  </diagram>
</mxfile>
"""
    puml.with_suffix(".drawio").write_text(xml, encoding="utf-8")
    print(f"{puml.stem}.drawio  {width}x{height}")
