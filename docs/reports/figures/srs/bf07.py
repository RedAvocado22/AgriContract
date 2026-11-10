"""BF-07 Emergency Security Lock (activity diagram, UML / Visual Paradigm notation). 700 px wide for the report."""
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "tools"))
from vpdiagram import Diagram  # noqa: E402

d = Diagram("bf07", width=700, height=950)
for name, w in (("External Verifier", 240), ("Admin", 220), ("System", 240)):
    d.lane(name, w)
V, Ad, Y = d.center("External Verifier"), d.center("Admin"), d.center("System")
YR = 690

d.initial("start", V, 55)
d.action("a1", V, 115, "1. Detect a suspected\nincident", w=190)
d.action("a2", V, 185, "2. Send a signed\nLOCK request", w=190)
d.action("a3", Y, 185, "3. Verify the key,\nsignature, time\nand nonce", w=190)
d.decision("d1", Y, 255)
d.action("a4", Y, 320, "4. Reject the request\nwith an error code", w=190)
d.final_named("f1", Y, 380, "Lock not applied", side="l")
d.action("a5", Y, 460, "5. Stop all money\ncommands", w=190)
d.action("a6", Y, 530, "6. Record the lock,\nnotify Admin and\nthe verifier", w=190)
d.action("a7", Ad, 610, "7. Investigate and\nreport to the\nverifier", w=180)
d.note("n1", Ad, 690, "Admin cannot unlock", w=150)
d.action("a8", V, 690, "8. Send a signed\nUNLOCK request", w=190)
d.action("a9", Y, 770, "9. Verify the request\nand resume money\ncommands", w=190)
d.action("a10", Y, 840, "10. Record the unlock\nand notify", w=190)
d.final_named("f2", Y, 902, "Money flow resumed", side="b")

d.flow("start", "b", "a1", "t")
d.flow("a1", "b", "a2", "t")
d.flow("a2", "r", "a3", "l")
d.flow("a3", "b", "d1", "t")
d.flow("d1", "b", "a4", "t", label="[invalid]", label_at=(Y + 6, 271))
d.flow("a4", "b", "f1", "t")
d.flow("d1", "r", "a5", "t", (YR, 255), (YR, 415), (Y, 415), label="[valid]", label_at=(Y + 20, 237))
d.flow("a5", "b", "a6", "t")
d.flow("a6", "b", "a7", "r", (Y, 610))
d.edge("n1", "a7", d.nodes["n1"].port("t"), d.nodes["a7"].port("b"), dashed=True, arrow=False)
d.flow("a7", "l", "a8", "t", (V, 610))
d.flow("a8", "b", "a9", "l", (V, 770))
d.flow("a9", "b", "a10", "t")
d.flow("a10", "b", "f2", "t")

if __name__ == "__main__":
    d.write(Path(__file__).resolve().parent)
