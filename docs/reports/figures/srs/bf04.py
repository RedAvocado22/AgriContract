"""BF-04 Milestone Delivery & Settlement (activity diagram, UML / Visual Paradigm notation).

Narrow and tall (800 px wide) so the text stays readable at the 16 cm text width of the report.
"""
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "tools"))
from vpdiagram import Diagram  # noqa: E402

d = Diagram("bf04", width=800, height=1070)
for name, w in (("Buyer", 160), ("Seller", 150), ("Arbitrator", 140), ("Inspector", 140), ("System", 210)):
    d.lane(name, w)
B, S, A, I, Y = (d.center(n) for n in ("Buyer", "Seller", "Arbitrator", "Inspector", "System"))

d.initial("start", Y, 50)
d.decision("m0", Y, 88)
d.action("a1", Y, 147, "1. Ask the bank to lock\nthe Buyer's milestone\npayment", w=150)
d.decision("d1", Y, 207)
d.action("a2", Y, 268, "2. Notify the parties\nof the funding failure", w=150)
d.final_named("f1", Y, 318, "Continues in BF-05", side="b")
d.action("a3", S, 207, "3. Deliver the goods\nand record the\nweighed quantity", w=136)
d.action("a4", B, 280, "4. Record the\nreceived quantity\nwith evidence", w=130)
d.decision("d2", B, 345)
d.action("a6", B, 408, "6. Flag a quantity\nor quality issue", w=130)
d.action("a5", B, 500, "5. Confirm delivery\n(silence in the\nwindow = confirmed)", w=130)
d.decision("d3", S, 560)
d.action("a7", S, 615, "7. Contest the flag", w=130)
d.action("a8", A, 615, "8. Request an\ninspection of\nthe goods", w=124)
d.action("a9", I, 615, "9. Inspect the\ngoods and submit\na report", w=124)
d.action("a10", A, 700, "10. Decide the\namounts from the\ninspection result", w=124)
d.decision("m1", Y, 780)
d.action("a11", Y, 840, "11. Release the payment\nand refund the\ndifference", w=150)
d.decision("d4", Y, 905)
d.action("a12", Y, 962, "12. Return both\ndeposits", w=150)
d.final_named("f2", Y, 1017, "Contract settled", side="b")

d.flow("start", "b", "m0", "t")
d.flow("m0", "b", "a1", "t")
d.flow("a1", "b", "d1", "t")
d.flow("d1", "b", "a2", "t", label="[not locked]", label_at=(Y + 6, 226))
d.flow("a2", "b", "f1", "t")
d.flow("d1", "l", "a3", "r", label="[locked]", label_at=(Y - 62, 189))
d.flow("a3", "b", "a4", "r", (S, 280))
d.flow("a4", "b", "d2", "t")
d.flow("d2", "b", "a6", "t", label="[disputes]", label_at=(B + 6, 361))
d.flow("d2", "l", "a5", "l", (8, 345), (8, 500), label="[accepts]", label_at=(12, 327))
d.flow("a6", "r", "d3", "t", (S, 408))
d.flow("d3", "b", "a7", "t", label="[contests]", label_at=(S + 6, 576))
d.flow("a7", "r", "a8", "l")
d.flow("a8", "r", "a9", "l")
d.flow("a9", "b", "a10", "r", (I, 700))
d.flow("a10", "b", "m1", "t", (A, 752), (Y, 752))
d.flow("d3", "r", "m1", "r", (780, 560), (780, 780), label="[accepts Buyer's figures]", label_at=(I - 65, 542))
d.flow("a5", "b", "m1", "l", (B, 780))
d.flow("m1", "b", "a11", "t")
d.flow("a11", "b", "d4", "t")
d.flow("d4", "r", "m0", "r", (792, 905), (792, 88), label="[more]", label_at=(Y + 20, 887))
d.flow("d4", "b", "a12", "t", label="[last milestone]", label_at=(Y + 6, 921))
d.flow("a12", "b", "f2", "t")

if __name__ == "__main__":
    d.write(Path(__file__).resolve().parent)
