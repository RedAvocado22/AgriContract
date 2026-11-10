"""BF-01 User Onboarding & KYC (activity diagram, UML / Visual Paradigm notation)."""
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "tools"))
from vpdiagram import Diagram  # noqa: E402

d = Diagram("bf01", width=900, height=900)
for name, w in (("Guest / User", 300), ("Operator", 280), ("System", 320)):
    d.lane(name, w)
G, O, Y = d.center("Guest / User"), d.center("Operator"), d.center("System")

d.initial("start", G, 70)
d.action("a1", G, 140, "1. Register an account as\nBuyer, Seller or Inspector", w=220)
d.action("a2", Y, 140, "2. Create the account\nawaiting KYC", w=200)
d.action("a3", G, 240, "3. Submit organization and\nrepresentative documents", w=220)
d.action("a4", Y, 330, "4. Add the profile to the\nKYC review queue", w=200)
d.action("a5", O, 420, "5. Check the documents and\nthe authority to represent", w=220)
d.decision("d1", O, 500)
d.action("a6", O, 580, "6. Verify KYC and enter\nthe authorization expiry", w=210)
d.action("a7", Y, 580, "7. Enable trading and\ne-mail the result", w=200)
d.final_named("f1", Y, 660, "Account verified")
d.action("a8", O, 760, "8. Reject with a reason", w=180)
d.action("a9", Y, 760, "9. E-mail the\nrejection reason", w=200)
d.final_named("f2", Y, 840, "Account not verified", side="l")

d.flow("start", "b", "a1", "t")
d.flow("a1", "r", "a2", "l")
d.flow("a2", "b", "a3", "r", (Y, 240))
d.flow("a3", "b", "a4", "l", (G, 330))
d.flow("a4", "b", "a5", "r", (Y, 420))
d.flow("a5", "b", "d1", "t")
d.flow("d1", "b", "a6", "t", label="[valid]", label_at=(O + 6, 518))
d.flow("a6", "r", "a7", "l")
d.flow("a7", "b", "f1", "t")
d.flow("d1", "l", "a8", "l", (320, 500), (320, 760), label="[invalid]", label_at=(326, 482))
d.flow("a8", "r", "a9", "l")
d.flow("a9", "b", "f2", "t")

if __name__ == "__main__":
    d.write(Path(__file__).resolve().parent)
