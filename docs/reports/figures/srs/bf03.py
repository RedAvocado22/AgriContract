"""BF-03 Contract Signing & Activation (activity diagram, UML / Visual Paradigm notation).

Content: D37-D43. Either party revises or withdraws before the second signature; both sign the same terms with an
e-mail OTP in any order; activation needs both deposits locked; a failed lock is retried by Operator after fixing
the cause, or the parties ask to cancel (both, or one after the window) and the deposits come back.
Narrow and tall (780 px) so the text stays readable at the 16 cm text width of the report.
"""
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "tools"))
from vpdiagram import Diagram  # noqa: E402

d = Diagram("bf03", width=780, height=1075)
for name, w in (("Buyer", 170), ("Seller", 170), ("System", 270), ("Operator", 170)):
    d.lane(name, w)
B, S, Y, O = d.center("Buyer"), d.center("Seller"), d.center("System"), d.center("Operator")
YR = 600  # right channel inside the System lane (340-610)

d.initial("start", B, 52)
d.action("a1", B, 100, "1. Choose a listing\nand create an offer", w=156)
d.action("a2", Y, 100, "2. Validate the terms and\nreserve the listing quantity", w=230)
d.action("a3", S, 175, "3. Review and revise\nthe terms (either\nparty)", w=156)
d.decision("d1", S, 235)
d.action("a4", S, 300, "4. Withdraw the offer\n(either party, before\nthe second signature)", w=156)
d.action("a5", Y, 300, "5. Mark WITHDRAWN, release\nthe quantity, notify the\nother party", w=230)
d.final("f1", Y, 360)
d.bar("fork", 170, 385, 300)
d.action("a6", B, 435, "6. Sign the terms\nwith an e-mail OTP", w=156)
d.action("a7", S, 435, "7. Sign the same\nterms with an\ne-mail OTP", w=156)
d.note("n1", Y, 435, "Either party may sign first;\nboth signatures must be on\nthe same terms fingerprint", w=225)
d.bar("join", 170, 495, 300)
d.action("a8", Y, 545, "8. Check both signatures\nmatch the terms (SIGNED)", w=230)
d.action("a9", Y, 610, "9. Ask the bank to lock both\ndeposits (automatic retries)", w=230)
d.decision("d2", Y, 670)
d.action("a10", Y, 730, "10. Activate the contract\n(ACTIVE)", w=200)
d.final("f2", Y, 780)
d.action("a11", Y, 845, "11. Notify both parties\nand Operator", w=200)
d.decision("d3", Y, 910)
d.action("a12", O, 910, "12. Fix the cause and\nretry the deposit lock", w=156)
d.action("a13", S, 910, "13. Request the cancel\nwith an e-mail OTP\n(both, or one after\n5 business days)", w=156)
d.action("a14", Y, 985, "14. Return the locked deposits,\nrelease the quantity\n(ACTIVATION_FAILED)", w=230)
d.final("f3", Y, 1045)

d.flow("start", "b", "a1", "t")
d.flow("a1", "r", "a2", "l")
d.flow("a2", "b", "a3", "r", (Y, 175))
d.flow("a3", "b", "d1", "t")
d.flow("d1", "b", "a4", "t", label="[withdraw]", label_at=(S + 6, 252))
d.flow("a4", "r", "a5", "l")
d.flow("a5", "b", "f1", "t")
d.flow("d1", "l", "fork", "t", (B, 235), d_off=B - 170, label="[terms agreed]", label_at=(B + 6, 219))
d.flow("fork", "b", "a6", "t", s_off=B - 170)
d.flow("fork", "b", "a7", "t", s_off=S - 170)
d.edge("n1", "a7", d.nodes["n1"].port("l"), d.nodes["a7"].port("r"), dashed=True, arrow=False)
d.flow("a6", "b", "join", "t", d_off=B - 170)
d.flow("a7", "b", "join", "t", d_off=S - 170)
d.flow("join", "b", "a8", "l", (300, 545), s_off=300 - 170)
d.flow("a8", "b", "a9", "t")
d.flow("a9", "b", "d2", "t")
d.flow("d2", "b", "a10", "t", label="[both locked]", label_at=(Y + 6, 686))
d.flow("a10", "b", "f2", "t")
d.flow("d2", "r", "a11", "t", (YR, 670), (YR, 802), (Y, 802), label="[a lock failed]", label_at=(Y + 22, 652))
d.flow("a11", "b", "d3", "t")
d.flow("d3", "r", "a12", "l", label="[Operator retries]", label_at=(Y + 22, 892))
d.flow("a12", "t", "a9", "r", (O, 610))
d.flow("d3", "l", "a13", "r", label="[parties cancel]", label_at=(Y - 120, 892))
d.flow("a13", "b", "a14", "l", (S, 985))
d.flow("a14", "b", "f3", "t")

if __name__ == "__main__":
    d.write(Path(__file__).resolve().parent)
