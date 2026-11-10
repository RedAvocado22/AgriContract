"""BF-03 Contract Signing & Activation (activity diagram, UML / Visual Paradigm notation).

Content: D37-D43. Either party revises or withdraws before the second signature; both sign the same terms with an
e-mail OTP in any order; activation needs both deposits locked; a failed lock is retried by Operator after fixing
the cause, or the parties ask to cancel (both, or one after the window) and the deposits come back.
"""
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "tools"))
from vpdiagram import Diagram  # noqa: E402

d = Diagram("bf03", width=1100, height=1440)
for name, w in (("Buyer", 260), ("Seller", 260), ("System", 360), ("Operator", 220)):
    d.lane(name, w)
B, S, Y, O = d.center("Buyer"), d.center("Seller"), d.center("System"), d.center("Operator")

d.initial("start", B, 70)
d.action("a1", B, 140, "1. Choose a listing and\ncreate an offer", w=190)
d.action("a2", Y, 140, "2. Validate the terms and\nreserve the listing quantity", w=220)
d.action("a3", S, 230, "3. Review and revise\nthe terms (either party)", w=190)
d.decision("d1", S, 310)
d.action("a4", S, 400, "4. Withdraw the offer\n(either party, before\nthe second signature)", w=190)
d.action("a5", Y, 400, "5. Mark WITHDRAWN, release\nthe quantity, notify\nthe other party", w=220)
d.final("f1", Y, 480)
d.bar("fork", 255, 520, 420)
d.action("a6", B, 600, "6. Sign the terms\nwith an e-mail OTP", w=190)
d.action("a7", S, 600, "7. Sign the same terms\nwith an e-mail OTP", w=190)
d.note("n1", Y, 600, "Either party may sign\nfirst; both signatures\nmust be on the same\nterms fingerprint", w=200)
d.bar("join", 255, 680, 420)
d.action("a8", Y, 760, "8. Check both signatures\nmatch the terms (SIGNED)", w=220)
d.action("a9", Y, 850, "9. Ask the bank to lock both\ndeposits (automatic retries)", w=220)
d.decision("d2", Y, 930)
d.action("a10", Y, 1010, "10. Activate the contract\n(ACTIVE)", w=200)
d.final("f2", Y, 1080)
d.action("a11", Y, 1170, "11. Notify both parties\nand Operator", w=200)
d.decision("d3", Y, 1250)
d.action("a12", O, 1250, "12. Fix the cause and\nretry the deposit lock", w=180)
d.action("a13", S, 1250, "13. Request the cancel with\nan e-mail OTP (both parties,\nor one after 5 business days)",
         w=220)
d.action("a14", Y, 1340, "14. Return the locked deposits,\nrelease the quantity\n(ACTIVATION_FAILED)", w=220)
d.final("f3", Y, 1410)

d.flow("start", "b", "a1", "t")
d.flow("a1", "r", "a2", "l")
d.flow("a2", "b", "a3", "r", (Y, 230))
d.flow("a3", "b", "d1", "t")
d.flow("d1", "b", "a4", "t", label="[withdraw]", label_at=(S + 6, 333))
d.flow("a4", "r", "a5", "l")
d.flow("a5", "b", "f1", "t")
d.flow("d1", "l", "fork", "t", (B, 310), d_off=B - 255, label="[terms agreed]", label_at=(B + 8, 292))
d.flow("fork", "b", "a6", "t", s_off=B - 255)
d.flow("fork", "b", "a7", "t", s_off=S - 255)
d.edge("n1", "a7", d.nodes["n1"].port("l"), d.nodes["a7"].port("r"), dashed=True, arrow=False)
d.flow("a6", "b", "join", "t", d_off=B - 255)
d.flow("a7", "b", "join", "t", d_off=S - 255)
d.flow("join", "b", "a8", "l", (300, 760), s_off=300 - 255)
d.flow("a8", "b", "a9", "t")
d.flow("a9", "b", "d2", "t")
d.flow("d2", "b", "a10", "t", label="[both locked]", label_at=(Y + 6, 952))
d.flow("a10", "b", "f2", "t")
d.flow("d2", "r", "a11", "t", (Y + 150, 930), (Y + 150, 1120), (Y, 1120), label="[a lock failed]",
       label_at=(Y + 22, 912))
d.flow("a11", "b", "d3", "t")
d.flow("d3", "r", "a12", "l", label="[Operator retries]", label_at=(Y + 22, 1232))
d.flow("a12", "t", "a9", "r", (O, 850))
d.flow("d3", "l", "a13", "r", label="[parties cancel]", label_at=(Y - 160, 1232))
d.flow("a13", "b", "a14", "l", (S, 1340))
d.flow("a14", "b", "f3", "t")

if __name__ == "__main__":
    d.write(Path(__file__).resolve().parent)
