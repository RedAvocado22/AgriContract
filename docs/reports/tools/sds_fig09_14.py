"""SDS (Report-4) II.2-II.3: Figures 9-14 redrawn (figures/sds/fig09..fig14 *.py). The class tables name the classes
that exist in bank-service and inspection-service as the code does. Runs once, after sds_fig03.py. Content only."""
from pathlib import Path

import docx
from docx.oxml.ns import qn

from docxedit import EMU_PER_CM, append_row, find, next_table, png_size, set_cell_text, text_of

REPO = Path(__file__).resolve().parents[3]
DOC = REPO / "docs/reports/Report-4_SDS.docx"
FIG = REPO / "docs/reports/figures/sds"
d = docx.Document(DOC)
body = d.element.body

rows = {text_of(tr.findall(qn("w:tc"))[0]): tr for tr in body.iter(qn("w:tr"))}
if "ProcessBankCommandUseCase" not in rows:
    raise SystemExit("already applied")

for cap, name in [("Figure 9.", "fig09_class_milestone"), ("Figure 10.", "fig10_seq_weigh_confirm"),
                  ("Figure 11.", "fig11_seq_money_reconcile"), ("Figure 12.", "fig12_class_dispute"),
                  ("Figure 13.", "fig13_seq_flag_contest_assign"), ("Figure 14.", "fig14_seq_report_remedy")]:
    pic = find(d, cap).getprevious()
    png = FIG / f"{name}.png"
    rid, _ = d.part.get_or_add_image(str(png))
    next(pic.iter(qn("a:blip"))).set(qn("r:embed"), rid)
    w, h = png_size(png)
    px = w * 96 / 200
    cx = int(min(16.0, px * 16.0 / 800) * EMU_PER_CM)  # 800 px = text width; wider figures shrink to fit
    cy = int(cx * h / w)
    for ext in [*pic.iter(qn("wp:extent")), *pic.iter(qn("a:ext"))]:
        if ext.get("cx") is not None:
            ext.set("cx", str(cx))
            ext.set("cy", str(cy))
    print(f"{name} {cx / EMU_PER_CM:.1f} x {cy / EMU_PER_CM:.1f} cm")

bank = rows["ProcessBankCommandUseCase"].findall(qn("w:tc"))
set_cell_text(bank[0], "LedgerCommandConsumer / ProcessLedgerCommandUseCase")
set_cell_text(bank[2], "Insert ledger_entry with source_event_id = command eventId (UNIQUE); a repeated command "
                       "writes nothing and re-sends the result of the entry already recorded; publish "
                       "bank.*_completed / _failed through LedgerResultPublisher (outbox). Planned: reject while "
                       "system_lock is ACTIVE (UC52).")
insp = rows["Level15RequestedConsumer / AssignInspectorUseCase"].findall(qn("w:tc"))
set_cell_text(insp[0], "Level15RequestedConsumer / ManageLevel15Assignments")
set_cell_text(insp[2], "Dedup; locks the milestone (level1_5_assignment_lock), keeps one active assignment, picks "
                       "uniformly at random among the eligible inspectors from user-service, excluding previous "
                       "assignees on a timeout; deadline in business days; closeConfirmed ends it with the report.")

append_row(next_table(find(d, "Change Log")), [
    "10/11/2026", "M", "Group 6",
    "Figures 9-14 redrawn (editable .drawio); the classes that exist (bank-service ledger, inspection-service "
    "assignment, shared eventing) carry their names from the code, the rest stays the design; class tables "
    "follow (ProcessLedgerCommandUseCase, ManageLevel15Assignments)"])
d.save(DOC)
print("saved")
