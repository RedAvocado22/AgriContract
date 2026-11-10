"""BF-04 Milestone Delivery & Settlement (activity diagram, UML / Visual Paradigm notation)."""
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "tools"))
from vpdiagram import Diagram  # noqa: E402

d = Diagram("bf04", width=1380, height=1280)
for name, w in (("Buyer", 400), ("Seller", 240), ("Arbitrator", 220), ("Inspector", 220), ("System", 300)):
    d.lane(name, w)
B, S, A, I, Y = (d.center(n) for n in ("Buyer", "Seller", "Arbitrator", "Inspector", "System"))
BL, BR = B - 100, B + 100

d.initial("start", Y, 65)
d.decision("m0", Y, 120)
d.action("a1", Y, 195, "1. Ask the bank to lock the\nBuyer's milestone payment", w=220)
d.decision("d1", Y, 275)
d.action("a2", Y, 355, "2. Notify the parties of\nthe funding failure", w=200)
d.final_named("f1", Y, 425, "Continues in BF-05", side="b")
d.action("a3", S, 275, "3. Deliver the goods and\nrecord the weighed quantity", w=220)
d.action("a4", B, 365, "4. Record the received\nquantity with evidence", w=210)
d.decision("d2", B, 445)
d.action("a5", BL, 540, "5. Confirm the delivery\n(no answer within the\nwindow = confirmed)", w=180)
d.action("a6", BR, 540, "6. Flag a quantity\nor quality issue", w=170)
d.decision("d3", S, 620)
d.action("a7", S, 700, "7. Contest the flag", w=170)
d.action("a8", A, 700, "8. Request an inspection\nof the goods", w=190)
d.action("a9", I, 700, "9. Inspect the goods\nand submit a report", w=190)
d.action("a10", A, 820, "10. Decide the amounts from\nthe inspection result", w=200)
d.decision("m1", Y, 900)
d.action("a11", Y, 980, "11. Release the payment and\nrefund the difference", w=220)
d.decision("d4", Y, 1060)
d.action("a12", Y, 1140, "12. Return both deposits", w=200)
d.final_named("f2", Y, 1210, "Contract settled", side="b")

d.flow("start", "b", "m0", "t")
d.flow("m0", "b", "a1", "t")
d.flow("a1", "b", "d1", "t")
d.flow("d1", "b", "a2", "t", label="[not locked]", label_at=(Y + 6, 293))
d.flow("a2", "b", "f1", "t")
d.flow("d1", "l", "a3", "r", label="[locked]", label_at=(Y - 75, 257))
d.flow("a3", "b", "a4", "r", (S, 365))
d.flow("a4", "b", "d2", "t")
d.flow("d2", "l", "a5", "t", (BL, 445), label="[accepts]", label_at=(BL - 60, 427))
d.flow("d2", "r", "a6", "t", (BR, 445), label="[disputes]", label_at=(BR + 6, 427))
d.flow("a6", "r", "d3", "t", (S, 540))
d.flow("d3", "b", "a7", "t", label="[contests]", label_at=(S + 6, 638))
d.flow("a7", "r", "a8", "l")
d.flow("a8", "r", "a9", "l")
d.flow("a9", "b", "a10", "r", (I, 820))
d.flow("a10", "b", "m1", "t", (A, 860), (Y, 860))
d.flow("d3", "r", "m1", "r", (1320, 620), (1320, 900), label="[accepts Buyer's figures]", label_at=(A - 95, 602))
d.flow("a5", "b", "m1", "l", (BL, 900))
d.flow("m1", "b", "a11", "t")
d.flow("a11", "b", "d4", "t")
d.flow("d4", "r", "m0", "r", (1370, 1060), (1370, 120), label="[more milestones]", label_at=(Y + 22, 1066))
d.flow("d4", "b", "a12", "t", label="[last milestone]", label_at=(Y + 6, 1078))
d.flow("a12", "b", "f2", "t")

if __name__ == "__main__":
    d.write(Path(__file__).resolve().parent)
