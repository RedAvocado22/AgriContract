"""SDS (Report-4) I.3: Figures 4a/4b/5a/5b redrawn as crow's foot ERDs (figures/sds/erd_sds.py), and the database
text brought in line with the migrations (checked 10/11/2026 against the schema the migrations produce).

Runs once on the docx that already has sds_signing.py / sds_packages.py applied. Content only.
"""
import copy
from pathlib import Path

import docx
from docx.oxml.ns import qn

from docxedit import (EMU_PER_CM, append_row, find, next_table, png_size, set_cell_text, set_par_text, text_of)

REPO = Path(__file__).resolve().parents[3]
DOC = REPO / "docs/reports/Report-4_SDS.docx"
FIG = REPO / "docs/reports/figures/sds"
d = docx.Document(DOC)
body = d.element.body

intro = find(d, "Each service owns one MySQL 8 schema")
if "«planned»" in text_of(intro):
    raise SystemExit("already applied")

# ---------- figures ----------
pics, n = [], intro.getnext()
while len(pics) < 4:
    if any(True for _ in n.iter(qn("w:drawing"))):
        pics.append(n)
    n = n.getnext()
for p, name in zip(pics, ("fig04a_contract_signing_erd", "fig04b_milestone_dispute_erd", "fig05a_money_stores_erd",
                          "fig05b_evidence_stores_erd")):
    png = FIG / f"{name}.png"
    rid, _ = d.part.get_or_add_image(str(png))
    next(p.iter(qn("a:blip"))).set(qn("r:embed"), rid)
    w, h = png_size(png)
    cx = int(16.0 * EMU_PER_CM)  # 800 px = text width, the scale of every redrawn figure
    cy = int(cx * h / w)
    for ext in [*p.iter(qn("wp:extent")), *p.iter(qn("a:ext"))]:
        if ext.get("cx") is not None:
            ext.set("cx", str(cx))
            ext.set("cy", str(cy))
    print(f"{name} {cx / EMU_PER_CM:.1f} x {cy / EMU_PER_CM:.1f} cm")

text = text_of(intro)
cut = text.index("Tables already created by Flyway")
set_par_text(intro, text[:cut] +
    "Tables already created by Flyway in the repository are documented from the schema their migrations produce "
    "(contract_db: contracts, signing, consent and activation recovery; bank, escrow, reputation, inspection, user, "
    "product, pricing, notification); the other tables are the planned Phase 2 design. Figures 4 and 5 draw a "
    "planned table with a dashed border, marked «planned»; a line is a foreign key, and an id that points into "
    "another service has no line.")

# ---------- schema overview ----------
overview = {
    "escrow_db": "deposit_leg (implemented, V9–V11); escrow_milestone, remedy_leg_command (planned; leg → bank "
                 "request mapping)",
    "bank_db": "ledger_entry (implemented); system_lock, used_nonce (planned)",
    "inspection_db": "level1_5_inspection_assignment, level1_5_assignment_lock (implemented); inspection_report, "
                     "inspector_signature, level2_inspection_commission (planned)",
    "reputation_db": "completed_contract_fact, dispute_outcome_fact, lock_entry, lock_override_event (implemented); "
                     "governance_action_request, pair_risk_state (planned)",
    "audit_db": "audit_record, audit_anchor (planned; audit-service not built yet)",
}
for tr in next_table(find(d, "Schema overview")).findall(qn("w:tr")):
    tcs = tr.findall(qn("w:tc"))
    if text_of(tcs[0]) in overview:
        set_cell_text(tcs[-1], overview[text_of(tcs[0])])

# ---------- 3.12 deposit_leg (V10, V11) ----------
set_par_text(find(d, "escrow_db · (implemented, V9)"),
             "escrow_db · (implemented, V9–V11) One activation deposit leg per party per contract, with the bank "
             "result of its lock and, when the parties cancel after a failed lock (D42), of its return.")
tbl = next_table(find(d, "3.12 deposit_leg"))
rows = {text_of(tr.findall(qn("w:tc"))[0]): tr for tr in tbl.findall(qn("w:tr"))}
st = rows["status"].findall(qn("w:tc"))
set_cell_text(st[2], "NOT NULL, CHECK IN (…)")
set_cell_text(st[3], "REQUESTED → LOCKED / FAILED; cancel after a failed lock: CANCEL_PENDING, CANCELLED, "
                     "RETURN_REQUESTED, RETURN_FAILED, RETURNED")
anchor = rows["request_event_id"]
for values in [
    ["ledger_entry_id", "CHAR(36)", "NULL; CHECK set exactly while the money is locked",
     "bank ledger row of the lock (LOCKED and the return states)"],
    ["failed_attempts", "INT", "NOT NULL DEFAULT 0, CHECK ≥ 0", "bank.lock_failed results; drives the retry backoff"],
    ["next_attempt_at / last_failure_reason", "DATETIME(6) / VARCHAR(500)", "NULL",
     "when the same request_event_id is resent; NULL once the retries are used up"],
    ["return_request_event_id", "CHAR(36)", "UNIQUE, NULL; CHECK set in the return states",
     "eventId of bank.refund_requested / bank.release_requested; a retry resends the same id"],
    ["return_ledger_entry_id", "CHAR(36)", "NULL; CHECK set exactly when RETURNED", "bank ledger row of the return"],
]:
    row = copy.deepcopy(anchor)
    for tc, v in zip(row.findall(qn("w:tc")), values):
        set_cell_text(tc, v)
    anchor.addnext(row)
    anchor = row

# ---------- 3.16: the assignment lock table (V2) ----------
set_par_text(find(d, "inspection_db · (implemented, V1)"),
             "inspection_db · (implemented, V1–V2) Authorization record: which inspector may report on which "
             "milestone. A second table, level1_5_assignment_lock, serializes the assignments of one milestone.")
append_row(next_table(find(d, "3.16 level1_5_inspection_assignment")), [
    "level1_5_assignment_lock.contract_id + milestone_id", "CHAR(36)", "PK (contract_id, milestone_id)",
    "Upserted then read FOR UPDATE before an inspector is chosen, so two assignments of one milestone run one after "
    "the other; the row exists even when no assignment does"])

append_row(next_table(find(d, "Change Log")), [
    "10/11/2026", "M", "Group 6",
    "Figures 4a, 4b, 5a, 5b redrawn as crow's foot ERDs (editable .drawio in docs/reports/figures/sds) from the "
    "schema the migrations produce; planned tables marked «planned». Figure 4a now shows activation_recovery and "
    "activation_cancel_request (D42). Schema overview marks implemented / planned tables; 3.12 deposit_leg adds the "
    "V10/V11 columns; 3.16 adds level1_5_assignment_lock"])
d.save(DOC)
print("saved")
