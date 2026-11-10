"""BF-02 Plot & Listing Publication (activity diagram, UML / Visual Paradigm notation). 700 px wide for the report."""
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "tools"))
from vpdiagram import Diagram  # noqa: E402

d = Diagram("bf02", width=700, height=1010)
for name, w in (("Seller", 240), ("System", 240), ("Operator", 220)):
    d.lane(name, w)
S, Y, O = d.center("Seller"), d.center("System"), d.center("Operator")
YR = 472  # channel at the right of the System lane (240-480)

d.initial("start", S, 55)
d.action("a1", S, 115, "1. Import plots from\nKML or a GPS pin", w=190)
d.action("a2", Y, 115, "2. Check the file and\nthe plot geometry", w=190)
d.decision("d1", Y, 180)
d.action("a3", Y, 245, "3. Show the errors\nper plot", w=170)
d.action("a4", S, 245, "4. Fix the plots and\nimport them again", w=190)
d.action("a5", Y, 340, "5. Save the plots and\nrun the forest-risk\ncheck", w=190)
d.decision("d2", Y, 410)
d.action("a6", O, 410, "6. Review the flagged\nplot risk", w=180)
d.decision("m1", Y, 475)
d.action("a7", S, 545, "7. Create a product\nwith category and\nplots", w=190)
d.action("a8", S, 630, "8. Create a listing\nwith quantity and\nquality", w=190)
d.action("a9", Y, 630, "9. Check seller\neligibility and the\nplot checks", w=190)
d.decision("d3", Y, 700)
d.action("a10", Y, 765, "10. Publish the listing\non the marketplace", w=190)
d.final_named("f1", Y, 825, "Listing available", side="b")
d.action("a11", Y, 900, "11. Reject the listing\nwith a reason", w=190)
d.final_named("f2", Y, 955, "Listing not published", side="b")

d.flow("start", "b", "a1", "t")
d.flow("a1", "r", "a2", "l")
d.flow("a2", "b", "d1", "t")
d.flow("d1", "b", "a3", "t", label="[invalid]", label_at=(Y + 6, 196))
d.flow("a3", "l", "a4", "r")
d.flow("a4", "t", "a1", "b")
d.flow("d1", "r", "a5", "t", (YR, 180), (YR, 290), (Y, 290), label="[valid]", label_at=(Y + 20, 162))
d.flow("a5", "b", "d2", "t")
d.flow("d2", "r", "a6", "l", label="[risk flagged]", label_at=(Y + 20, 392))
d.flow("d2", "b", "m1", "t", label="[no flag]", label_at=(Y + 6, 427))
d.flow("a6", "b", "m1", "r", (O, 475))
d.flow("m1", "l", "a7", "t", (S, 475))
d.flow("a7", "b", "a8", "t")
d.flow("a8", "r", "a9", "l")
d.flow("a9", "b", "d3", "t")
d.flow("d3", "b", "a10", "t", label="[eligible]", label_at=(Y + 6, 716))
d.flow("a10", "b", "f1", "t")
d.flow("d3", "r", "a11", "t", (YR, 700), (YR, 862), (Y, 862), label="[not eligible]", label_at=(Y + 20, 682))
d.flow("a11", "b", "f2", "t")

if __name__ == "__main__":
    d.write(Path(__file__).resolve().parent)
