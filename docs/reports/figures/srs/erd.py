"""SRS 3.1 conceptual ERD in crow's foot (Information Engineering) notation, three parts by domain:
(a) identity, product and listing; (b) contract, milestone and money; (c) dispute, inspection, reputation and audit.

Entities, keys, relationships and cardinalities are those of the earlier SRS figures; only the split follows the
three parts the SRS text names. An entity drawn in another part is a dashed grey box (ghost).
Output: erd_a/erd_b/erd_c .drawio/.svg/.png.
"""
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "tools"))
from vpdiagram import Diagram  # noqa: E402

OUT = Path(__file__).resolve().parent

KEYS = {
    "USER": (["userId"], []),
    "PLOT_REGISTRY_ENTRY": (["plotId"], ["sellerId", "cadastralFileId"]),
    "PRODUCT_PLOT": (["productId (FK)", "plotId (FK)"], []),
    "PRODUCT": (["productId"], ["sellerId", "categoryId"]),
    "LISTING": (["listingId"], ["productId"]),
    "RESERVATION": (["reservationId"], ["listingId", "contractId"]),
    "CATEGORY": (["categoryId"], ["parentCategoryId"]),
    "REFERENCE_PRICE": (["priceId"], ["categoryId"]),
    "FILE": (["fileId"], ["uploadedBy"]),
    "CONTRACT": (["contractId"], ["buyerId", "sellerId", "listingId", "replacesContractId"]),
    "CONTRACT_TERMS": (["termsId"], ["contractId"]),
    "SIGNATURE": (["signatureId"], ["termsId", "signerId"]),
    "MILESTONE": (["milestoneId"], ["contractId"]),
    "PRICE_ADJUSTMENT": (["adjustmentId"], ["milestoneId", "proposedBy"]),
    "ESCROW_ACCOUNT": (["escrowId"], ["contractId"]),
    "LEDGER_ENTRY": (["entryId"], ["escrowId", "milestoneId", "remedyLegId"]),
    "FORCE_MAJEURE_CLAIM": (["claimId"], ["milestoneId", "claimantId", "decidedBy"]),
    "BREACH_CASE": (["caseId"], ["contractId", "milestoneId", "reportedBy"]),
    "ATTRIBUTION_DECISION": (["decisionId"], ["caseId", "decidedBy"]),
    "REMEDY_DECISION": (["remedyId"], ["decisionId"]),
    "REMEDY_LEG": (["legId"], ["remedyId"]),
    "INSPECTION_REQUEST": (["requestId"], ["caseId", "milestoneId"]),
    "INSPECTION_REPORT": (["reportId"], ["requestId", "inspectorId", "fileId"]),
    "REPUTATION_LOCK": (["lockId"], ["userId", "remedyId"]),
    "REPUTATION_ACTION": (["actionId"], ["lockId", "proposedBy", "approvedBy"]),
    "AUDIT_RECORD": (["seq"], ["prevSeq", "contractId"]),
    "SECURITY_LOCK": (["securityLockId"], []),
}


def ent(d, name, cx, cy, ghost=False):
    pk, fk = KEYS[name]
    n = d.entity(name, cx, 0, name, pk, fk, ghost=ghost)
    n.cy = cy
    return n


def part_a():
    d = Diagram("erd_a", 800, 450)
    ent(d, "CONTRACT", 710, 30, ghost=True)
    ent(d, "RESERVATION", 710, 140)
    ent(d, "LISTING", 710, 275)
    ent(d, "PRODUCT", 470, 275)
    ent(d, "USER", 230, 275)
    ent(d, "PLOT_REGISTRY_ENTRY", 230, 90)
    ent(d, "PRODUCT_PLOT", 470, 90)
    ent(d, "FILE", 45, 180, ghost=True)
    ent(d, "CATEGORY", 470, 400)
    ent(d, "REFERENCE_PRICE", 700, 400)
    d.er("RESERVATION", "t", "CONTRACT", "b", s="one", d="one", label="holds", side="r")
    d.er("LISTING", "t", "RESERVATION", "b", label="is reserved by", side="r")
    d.er("PRODUCT", "r", "LISTING", "l", label="is offered via")
    d.er("USER", "r", "PRODUCT", "l", label="owns")
    d.er("USER", "t", "PLOT_REGISTRY_ENTRY", "b", label="registers")
    d.er("PLOT_REGISTRY_ENTRY", "r", "PRODUCT_PLOT", "l", label="is used in", side="t")
    d.er("PRODUCT", "t", "PRODUCT_PLOT", "b", d="many", label="is grown on")
    d.er("FILE", "t", "PLOT_REGISTRY_ENTRY", "l", (45, 90), s="zero_one", label="maps")
    d.er("USER", "l", "FILE", "b", (45, 275), label="uploads")
    d.er("CATEGORY", "t", "PRODUCT", "b", label="classifies", side="r")
    d.er("CATEGORY", "l", "CATEGORY", "l", (372, 390), (372, 420), s="zero_one", s_off=-10, d_off=20,
         label="groups", seg=1, side="l")
    d.er("CATEGORY", "r", "REFERENCE_PRICE", "l", label="is quoted in", side="t")
    return d


def part_b():
    d = Diagram("erd_b", 800, 535)
    ent(d, "LISTING", 360, 40, ghost=True)
    ent(d, "CONTRACT", 360, 200)
    ent(d, "USER", 60, 200, ghost=True)
    ent(d, "SIGNATURE", 70, 340)
    ent(d, "CONTRACT_TERMS", 360, 340)
    ent(d, "MILESTONE", 600, 200)
    ent(d, "PRICE_ADJUSTMENT", 600, 60)
    ent(d, "LEDGER_ENTRY", 600, 360)
    ent(d, "ESCROW_ACCOUNT", 600, 490)
    ent(d, "REMEDY_LEG", 740, 490, ghost=True)
    d.er("LISTING", "b", "CONTRACT", "t", label="supplies")
    d.er("USER", "r", "CONTRACT", "l", s_off=-6, d_off=-6, label="buys under")
    d.er("USER", "b", "CONTRACT", "l", (72, 245), s_off=12, d_off=45, label="sells under", seg=1)
    d.er("CONTRACT", "t", "CONTRACT", "r", (400, 110), (455, 110), (455, 165), s="zero_one", d="zero_one",
         s_off=40, d_off=-35, label="replaces", seg=1, side="t")
    d.er("USER", "b", "SIGNATURE", "t", s_off=-10, d_off=-20, label="provides")
    d.er("CONTRACT", "b", "CONTRACT_TERMS", "t", d="many", label="is governed by", side="r")
    d.er("CONTRACT_TERMS", "l", "SIGNATURE", "r", label="is signed with")
    d.er("CONTRACT", "r", "MILESTONE", "l", d="many", label="is split into")
    d.er("MILESTONE", "t", "PRICE_ADJUSTMENT", "b", label="is repriced by", side="r")
    d.er("MILESTONE", "b", "LEDGER_ENTRY", "t", s="zero_one", label="is funded by")
    d.er("CONTRACT", "r", "ESCROW_ACCOUNT", "l", (480, 245), (480, 490), s_off=45, d="one",
         label="is secured by", seg=1)
    d.er("ESCROW_ACCOUNT", "t", "LEDGER_ENTRY", "b", label="records", side="r")
    d.er("REMEDY_LEG", "t", "LEDGER_ENTRY", "r", (740, 360), d="zero_one", label="is executed as")
    return d


def part_c():
    d = Diagram("erd_c", 800, 680)
    ent(d, "FORCE_MAJEURE_CLAIM", 100, 60)
    ent(d, "MILESTONE", 330, 60, ghost=True)
    ent(d, "INSPECTION_REQUEST", 640, 60)
    ent(d, "BREACH_CASE", 330, 200)
    ent(d, "INSPECTION_REPORT", 640, 200)
    ent(d, "CONTRACT", 100, 300, ghost=True)
    ent(d, "FILE", 620, 340)
    ent(d, "ATTRIBUTION_DECISION", 330, 340)
    ent(d, "AUDIT_RECORD", 100, 430)
    ent(d, "REMEDY_DECISION", 330, 480)
    ent(d, "REPUTATION_LOCK", 540, 480)
    ent(d, "USER", 750, 470, ghost=True)
    ent(d, "SECURITY_LOCK", 100, 560)
    ent(d, "REMEDY_LEG", 330, 620)
    ent(d, "REPUTATION_ACTION", 540, 620)
    d.er("MILESTONE", "l", "FORCE_MAJEURE_CLAIM", "r", label="is excused by", side="t")
    d.er("MILESTONE", "b", "BREACH_CASE", "t", s="zero_one", label="concerns")
    d.er("MILESTONE", "r", "INSPECTION_REQUEST", "l", label="is inspected in")
    d.er("BREACH_CASE", "r", "INSPECTION_REQUEST", "l", (480, 170), (480, 80), s_off=-30, d_off=20,
         label="requests", side="t")
    d.er("INSPECTION_REQUEST", "b", "INSPECTION_REPORT", "t", d="zero_one", label="is answered by", side="r")
    d.er("FILE", "t", "INSPECTION_REPORT", "b", d="zero_one", d_off=-20, label="evidences", side="r")
    d.er("USER", "t", "INSPECTION_REPORT", "b", (750, 300), (700, 300), s="zero_one", d_off=60, label="submits")
    d.er("CONTRACT", "t", "BREACH_CASE", "l", (100, 220), d_off=20, label="is disputed in", seg=1)
    d.er("CONTRACT", "b", "AUDIT_RECORD", "t", s="zero_one", label="is recorded in")
    d.er("AUDIT_RECORD", "r", "AUDIT_RECORD", "r", (190, 420), (190, 455), s="zero_one", d="zero_one",
         s_off=-10, d_off=25, label="chains to", seg=1, side="r")
    d.er("BREACH_CASE", "b", "ATTRIBUTION_DECISION", "t", d="zero_one", label="is resolved by", side="r")
    d.er("ATTRIBUTION_DECISION", "b", "REMEDY_DECISION", "t", d="zero_one", label="leads to", side="r")
    d.er("REMEDY_DECISION", "b", "REMEDY_LEG", "t", d="many", label="consists of", side="r")
    d.er("REMEDY_DECISION", "r", "REPUTATION_LOCK", "l", s="zero_one", label="imposes", side="t")
    d.er("USER", "l", "REPUTATION_LOCK", "r", d_off=-10, label="is restricted by", side="t")
    d.er("REPUTATION_LOCK", "b", "REPUTATION_ACTION", "t", label="is reviewed in", side="r")
    return d


if __name__ == "__main__":
    failed = []
    for build in (part_a, part_b, part_c):
        try:
            build().write(OUT)
            print("ok", build.__name__)
        except SystemExit as e:
            failed.append(str(e))
    if failed:
        print("\n".join(failed))
        sys.exit(1)
