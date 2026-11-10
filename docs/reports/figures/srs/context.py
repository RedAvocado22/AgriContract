"""Context diagram (SRS I.1): AgriContract as one process, users on the left, external systems on the right.

Visual Paradigm context-diagram (DFD level 0) notation: one process, external entities as rectangles, every data
flow named. As the SRS text says, a solid flow is a request or action sent by the party that starts it, a dashed
flow is data returned or provided to that party. Roles follow data-governance §5.4 and D42/D43.
"""
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "tools"))
from vpdiagram import Diagram, Node, text_size  # noqa: E402

USERS = [
    ("guest", "Guest", "registration, listing search", "listings, reference prices"),
    ("buyer", "Buyer", "offer, terms, OTP signature,\nreceipt, flag, cancel request",
     "contract status,\ne-mails and OTP codes"),
    ("seller", "Seller", "plots, listings, terms,\nOTP signature, weighing, contest",
     "contract status,\ne-mails and OTP codes"),
    ("inspector", "Inspector\n(Level 1.5)", "inspection report", "assignment, evidence"),
    ("operator", "Operator", "KYC decision, lock retry,\nprice entry, moderation", "review queues, alerts"),
    ("arbitrator", "Arbitrator", "breach and force majeure\ndecisions", "case evidence"),
    ("admin", "Admin", "system administration", "audit and security\nreports"),
    ("verifier", "External\nVerifier", "signed LOCK / UNLOCK", "lock status,\naudit hashes"),
]
SYSTEMS = [
    ("bank", "Custodian Bank\n(mock\nbank-service)", "lock, release and\nrefund commands", "ledger results"),
    ("l2", "Level 2\nInspection Org", "inspection commission", "external report\n(by e-mail)"),
    ("email", "Email Service\n(SMTP / intake\nmailbox)", "outgoing e-mails", "intake e-mails"),
    ("ots", "OpenTimestamps", "record hashes", "timestamp proofs"),
    ("vnsat", "VNSAT Price\nSource", None, "reference prices"),
    ("geo", "Geo Datasets", None, "province polygons,\nforest 2020 map"),
]

ROW = 104
TOP = 70
H = TOP + len(USERS) * ROW + 10
d = Diagram("context", width=700, height=H)
PX0, PX1 = 300, 400
d.nodes["sys"] = Node("sys", "action", (PX0 + PX1) / 2, (TOP + H - 20) / 2, PX1 - PX0, H - 20 - TOP + 20,
                      "0\nAgriContract\nB2B\nAgricultural\nContract\nPlatform")
d.nodes["h_users"] = Node("h_users", "text", 65, 30, *text_size("Users"), "Users")
d.nodes["h_ext"] = Node("h_ext", "text", 635, 30, *text_size("External Systems"), "External Systems")


def entity(id, cx, cy, text):
    w, h = text_size(text)
    if w + 12 > 110:
        raise SystemExit(f"{id}: text is {w:.0f} px wide, the box is 110")
    d.nodes[id] = Node(id, "object", cx, cy, 110, max(h + 16, 44), text)


for i, (id, name, request, data) in enumerate(USERS):
    cy = TOP + i * ROW + ROW / 2
    entity(id, 65, cy, name)
    a, b = cy - 9, cy + 9
    d.edge(id, "sys", (120, a), (PX0, a), label=request,
           label_at=(126, a - 4 - text_size(request, 11)[1]))
    d.edge("sys", id, (PX0, b), (120, b), label=data, label_at=(126, b + 4), dashed=True)

step = (H - TOP - 10) / len(SYSTEMS)
for i, (id, name, request, data) in enumerate(SYSTEMS):
    cy = TOP + i * step + step / 2
    entity(id, 635, cy, name)
    a, b = cy - 9, cy + 9
    if request:
        d.edge("sys", id, (PX1, a), (580, a), label=request,
               label_at=(406, a - 4 - text_size(request, 11)[1]))
    d.edge(id, "sys", (580, b), (PX1, b), label=data, label_at=(406, b + 4), dashed=True)

if __name__ == "__main__":
    d.write(Path(__file__).resolve().parent)
