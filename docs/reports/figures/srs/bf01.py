"""BF-01 User Onboarding & KYC (activity diagram, UML / Visual Paradigm notation). 700 px wide for the report."""
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "tools"))
from vpdiagram import Diagram  # noqa: E402

d = Diagram("bf01", width=700, height=820)
for name, w in (("Guest / User", 240), ("Operator", 230), ("System", 230)):
    d.lane(name, w)
G, O, Y = d.center("Guest / User"), d.center("Operator"), d.center("System")

d.initial("start", G, 60)
d.action("a1", G, 125, "1. Register an account\nas Buyer, Seller\nor Inspector", w=200)
d.action("a2", Y, 125, "2. Create the account\nawaiting KYC", w=190)
d.action("a3", G, 220, "3. Submit organization\nand representative\ndocuments", w=200)
d.action("a4", Y, 300, "4. Add the profile to\nthe KYC review queue", w=190)
d.action("a5", O, 385, "5. Check the documents\nand the authority\nto represent", w=200)
d.decision("d1", O, 460)
d.action("a6", O, 535, "6. Verify KYC and enter\nthe authorization\nexpiry", w=190)
d.action("a7", Y, 535, "7. Enable trading and\ne-mail the result", w=190)
d.final_named("f1", Y, 605, "Account verified", side="b")
d.action("a8", O, 690, "8. Reject with\na reason", w=160)
d.action("a9", Y, 690, "9. E-mail the\nrejection reason", w=190)
d.final_named("f2", Y, 760, "Account not verified", side="b")

d.flow("start", "b", "a1", "t")
d.flow("a1", "r", "a2", "l")
d.flow("a2", "b", "a3", "r", (Y, 220))
d.flow("a3", "b", "a4", "l", (G, 300))
d.flow("a4", "b", "a5", "r", (Y, 385))
d.flow("a5", "b", "d1", "t")
d.flow("d1", "b", "a6", "t", label="[valid]", label_at=(O + 6, 477))
d.flow("a6", "r", "a7", "l")
d.flow("a7", "b", "f1", "t")
d.flow("d1", "l", "a8", "l", (252, 460), (252, 690), label="[invalid]", label_at=(258, 442))
d.flow("a8", "r", "a9", "l")
d.flow("a9", "b", "f2", "t")

if __name__ == "__main__":
    d.write(Path(__file__).resolve().parent)
