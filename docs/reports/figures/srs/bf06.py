"""BF-06 Mutual Termination & Replacement (activity diagram, UML / Visual Paradigm notation). 740 px wide."""
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "tools"))
from vpdiagram import Diagram  # noqa: E402

d = Diagram("bf06", width=740, height=1025)
for name, w in (("Proposing party", 230), ("Other party", 230), ("System", 280)):
    d.lane(name, w)
P, O, Y = d.center("Proposing party"), d.center("Other party"), d.center("System")
YR = 728

d.initial("start", P, 50)
d.action("a1", P, 100, "1. Choose termination\nor replacement", w=190)
d.decision("d1", P, 155)
d.action("a2", P, 210, "2. Propose a mutual\ntermination", w=190)
d.decision("d2", O, 265)
d.final_named("f1", O, 318, "Contract stays active", side="b")
d.action("a3", Y, 265, "3. Settle the entitlements\nwithout a penalty", w=220)
d.action("a4", Y, 325, "4. Refund the remaining\nlocked funds", w=220)
d.final_named("f2", Y, 376, "Contract terminated", side="l")
d.action("a5", Y, 435, "5. Check there is no open\ndelivery, dispute or claim", w=220)
d.decision("d3", Y, 495)
d.action("a6", Y, 552, "6. Reject the proposal\nwith a reason", w=200)
d.final_named("f3", Y, 604, "Contract stays active", side="l")
d.action("a7", P, 495, "7. Propose the\nreplacement terms", w=190)
d.action("a8", O, 555, "8. Review and agree\non the terms", w=190)
d.action("a9", P, 620, "9. Sign the replacement\nwith an e-mail OTP", w=200)
d.action("a10", O, 685, "10. Sign the replacement\nwith an e-mail OTP", w=200)
d.action("a11", Y, 685, "11. Re-check eligibility", w=200)
d.decision("d4", Y, 742)
d.final_named("f4", 700, 742, "Replacement\ncancelled", side="b")
d.action("a12", Y, 825, "12. Freeze the old contract\nand refund its funds", w=220)
d.action("a13", Y, 885, "13. Mark the old contract\nSUPERSEDED", w=220)
d.action("a14", Y, 945, "14. Activate the replacement\nas in BF-03", w=220)
d.final_named("f5", Y, 998, "Replacement\nactivated", side="l")

d.flow("start", "b", "a1", "t")
d.flow("a1", "b", "d1", "t")
d.flow("d1", "b", "a2", "t", label="[terminate]", label_at=(P + 6, 171))
d.flow("a2", "b", "d2", "l", (P, 265))
d.flow("d2", "b", "f1", "t", label="[refuses]", label_at=(O + 6, 281))
d.flow("d2", "r", "a3", "l", label="[agrees]", label_at=(O + 20, 247))
d.flow("a3", "b", "a4", "t")
d.flow("a4", "b", "f2", "t")
d.flow("d1", "r", "a5", "t", (YR, 155), (YR, 400), (Y, 400), label="[replace]", label_at=(P + 20, 137))
d.flow("a5", "b", "d3", "t")
d.flow("d3", "b", "a6", "t", label="[not eligible]", label_at=(Y + 6, 511))
d.flow("a6", "b", "f3", "t")
d.flow("d3", "l", "a7", "r", label="[eligible]", label_at=(Y - 75, 477))
d.flow("a7", "b", "a8", "l", (P, 555))
d.flow("a8", "b", "a9", "r", (O, 620))
d.flow("a9", "b", "a10", "l", (P, 685))
d.flow("a10", "r", "a11", "l")
d.flow("a11", "b", "d4", "t")
d.flow("d4", "r", "f4", "l", label="[not eligible]", label_at=(Y + 20, 724))
d.flow("d4", "b", "a12", "t", label="[still eligible]", label_at=(Y - 92, 758))
d.flow("a12", "b", "a13", "t")
d.flow("a13", "b", "a14", "t")
d.flow("a14", "b", "f5", "t")

if __name__ == "__main__":
    d.write(Path(__file__).resolve().parent)
