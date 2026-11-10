"""BF-02 Plot & Listing Publication (activity diagram, UML / Visual Paradigm notation)."""
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "tools"))
from vpdiagram import Diagram  # noqa: E402

d = Diagram("bf02", width=880, height=1180)
for name, w in (("Seller", 300), ("System", 320), ("Operator", 260)):
    d.lane(name, w)
S, Y, O = d.center("Seller"), d.center("System"), d.center("Operator")

d.initial("start", S, 70)
d.action("a1", S, 140, "1. Import plots from KML\nor a GPS pin", w=200)
d.action("a2", Y, 140, "2. Check the file and\nthe plot geometry", w=200)
d.decision("d1", Y, 220)
d.action("a3", Y, 300, "3. Show the errors\nper plot", w=180)
d.action("a4", S, 300, "4. Fix the plots and\nimport them again", w=200)
d.action("a5", Y, 400, "5. Save the plots and run\nthe forest-risk check", w=220)
d.decision("d2", Y, 480)
d.action("a6", O, 480, "6. Review the flagged\nplot risk", w=180)
d.decision("m1", Y, 560)
d.action("a7", S, 640, "7. Create a product with\ncategory and plots", w=210)
d.action("a8", S, 730, "8. Create a listing with\nquantity and quality", w=210)
d.action("a9", Y, 730, "9. Check seller eligibility\nand the plot checks", w=220)
d.decision("d3", Y, 810)
d.action("a10", Y, 890, "10. Publish the listing\non the marketplace", w=210)
d.final_named("f1", Y, 960, "Listing available", side="l")
d.action("a11", Y, 1060, "11. Reject the listing\nwith a reason", w=200)
d.final_named("f2", Y, 1130, "Listing not published", side="l")

d.flow("start", "b", "a1", "t")
d.flow("a1", "r", "a2", "l")
d.flow("a2", "b", "d1", "t")
d.flow("d1", "b", "a3", "t", label="[invalid]", label_at=(Y + 6, 238))
d.flow("a3", "l", "a4", "r")
d.flow("a4", "t", "a1", "b")
d.flow("d1", "r", "a5", "t", (590, 220), (590, 350), (Y, 350), label="[valid]", label_at=(Y + 22, 202))
d.flow("a5", "b", "d2", "t")
d.flow("d2", "r", "a6", "l", label="[risk flagged]", label_at=(Y + 22, 462))
d.flow("d2", "b", "m1", "t", label="[no flag]", label_at=(Y + 6, 500))
d.flow("a6", "b", "m1", "r", (O, 560))
d.flow("m1", "l", "a7", "t", (S, 560))
d.flow("a7", "b", "a8", "t")
d.flow("a8", "r", "a9", "l")
d.flow("a9", "b", "d3", "t")
d.flow("d3", "b", "a10", "t", label="[eligible]", label_at=(Y + 6, 828))
d.flow("a10", "b", "f1", "t")
d.flow("d3", "r", "a11", "t", (600, 810), (600, 1010), (Y, 1010), label="[not eligible]", label_at=(Y + 22, 792))
d.flow("a11", "b", "f2", "t")

if __name__ == "__main__":
    d.write(Path(__file__).resolve().parent)
