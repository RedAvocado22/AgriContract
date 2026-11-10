"""BF-07 Emergency Security Lock (activity diagram, UML / Visual Paradigm notation)."""
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "tools"))
from vpdiagram import Diagram  # noqa: E402

d = Diagram("bf07", width=880, height=1080)
for name, w in (("External Verifier", 300), ("Admin", 260), ("System", 320)):
    d.lane(name, w)
V, Ad, Y = d.center("External Verifier"), d.center("Admin"), d.center("System")

d.initial("start", V, 65)
d.action("a1", V, 130, "1. Detect a suspected\nincident", w=190)
d.action("a2", V, 210, "2. Send a signed\nLOCK request", w=190)
d.action("a3", Y, 210, "3. Verify the key, signature,\ntime and nonce", w=220)
d.decision("d1", Y, 290)
d.action("a4", Y, 370, "4. Reject the request\nwith an error code", w=200)
d.final_named("f1", Y, 440, "Lock not applied", side="l")
d.action("a5", Y, 550, "5. Stop all money\ncommands", w=200)
d.action("a6", Y, 630, "6. Record the lock, notify\nAdmin and the verifier", w=220)
d.action("a7", Ad, 710, "7. Investigate and report\nto the verifier", w=200)
d.note("n1", Ad, 800, "Admin cannot unlock", w=150)
d.action("a8", V, 790, "8. Send a signed\nUNLOCK request", w=190)
d.action("a9", Y, 880, "9. Verify the request and\nresume money commands", w=220)
d.action("a10", Y, 960, "10. Record the unlock\nand notify", w=200)
d.final_named("f2", Y, 1030, "Money flow resumed", side="l")

d.flow("start", "b", "a1", "t")
d.flow("a1", "b", "a2", "t")
d.flow("a2", "r", "a3", "l")
d.flow("a3", "b", "d1", "t")
d.flow("d1", "b", "a4", "t", label="[invalid]", label_at=(Y + 6, 308))
d.flow("a4", "b", "f1", "t")
d.flow("d1", "r", "a5", "t", (850, 290), (850, 500), (Y, 500), label="[valid]", label_at=(Y + 20, 272))
d.flow("a5", "b", "a6", "t")
d.flow("a6", "b", "a7", "r", (Y, 710))
d.edge("n1", "a7", d.nodes["n1"].port("t"), d.nodes["a7"].port("b"), dashed=True, arrow=False)
d.flow("a7", "l", "a8", "t", (V, 710))
d.flow("a8", "b", "a9", "l", (V, 880))
d.flow("a9", "b", "a10", "t")
d.flow("a10", "b", "f2", "t")

if __name__ == "__main__":
    d.write(Path(__file__).resolve().parent)
