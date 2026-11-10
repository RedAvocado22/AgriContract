"""SRS (Report-3): redrawn context + BF-01..07 figures, and the activation-failure content after D42/D43.

D42 (08/11/2026): the parties, not the Operator, stop a SIGNED contract whose deposit lock failed — both ask, or one
asks after 5 business days; the Operator only retries the lock. D43 (10/11/2026): withdraw details (already in
UC24). Content only; every edit keeps the formatting of the paragraph, cell or row it changes.
"""
import copy
from pathlib import Path

import docx
from docx.oxml.ns import qn

from docxedit import (EMU_PER_CM, append_row, find, insert_copy_after, next_table, replace_image,
                      set_cell_text, set_par_text, text_of)

REPO = Path(__file__).resolve().parents[3]
DOC = REPO / "docs/reports/Report-3_Software Requirement Specification.docx"
FIG = REPO / "docs/reports/figures/srs"
d = docx.Document(DOC)
body = d.element.body


def text_area(el):
    """(width, height) in cm of the text area of the section that contains el."""
    n = el
    while n is not None:
        sect = n.find(f"{qn('w:pPr')}/{qn('w:sectPr')}") if n.tag == qn("w:p") else None
        if sect is not None:
            break
        n = n.getnext()
    if n is None:
        sect = body.find(qn("w:sectPr"))
    pg, mar = sect.find(qn("w:pgSz")), sect.find(qn("w:pgMar"))
    tw = lambda v: int(v) / 567.0  # twips -> cm
    w = tw(pg.get(qn("w:w"))) - tw(mar.get(qn("w:left"))) - tw(mar.get(qn("w:right")))
    h = tw(pg.get(qn("w:h"))) - tw(mar.get(qn("w:top"))) - tw(mar.get(qn("w:bottom")))
    return w, h


def picture_after(el):
    n = el
    while n is not None:
        if any(True for _ in n.iter(qn("w:drawing"))):
            return n
        n = n.getnext()
    raise KeyError("no picture after " + text_of(el)[:40])


def put(heading_start, png):
    h = find(d, heading_start)
    pic = picture_after(h)
    w, hh = text_area(pic)
    size = replace_image(d, pic, FIG / png, max_w_cm=w, max_h_cm=hh - 3.8, full_width=True)  # room for the caption
    print(f"{png:12s} {size[0]:.1f} x {size[1]:.1f} cm in a {w:.1f} x {hh:.1f} cm text area")


put("1. Context Diagram", "context.png")
for n, title in enumerate(["User Onboarding & KYC", "Plot & Listing Publication", "Contract Signing & Activation",
                           "Milestone Delivery & Settlement", "Breach & Force Majeure Resolution",
                           "Mutual Termination & Replacement", "Emergency Security Lock"], start=1):
    put(f"2.{n} {title}", f"bf0{n}.png")

# ---------- D42: activation failure ----------
uc = next(tr for tr in body.iter(qn("w:tr")) if text_of(tr).startswith("UC29"))
set_cell_text(uc.findall(qn("w:tc"))[-1],
    "After a deposit lock failed, the Operator retries the lock once the cause is fixed; the Buyer and Seller decide "
    "to stop: when both ask to cancel, or one asks after 5 business days, the system refunds the locked deposits and "
    "releases the reserved quantity. The Operator cannot cancel a contract.")

roles = next(tr for tr in body.iter(qn("w:tr")) if text_of(tr).startswith("ContractRetry deposit lock"))
set_cell_text(roles.findall(qn("w:tc"))[1], "Retry deposit lock")
withdraw = next(tr for tr in body.iter(qn("w:tr")) if text_of(tr).startswith("ContractWithdraw offer"))
cancel = copy.deepcopy(withdraw)
set_cell_text(cancel.findall(qn("w:tc"))[1], "Request / withdraw activation cancel")
roles.addnext(cancel)

act = next(tr for tr in body.iter(qn("w:tr")) if text_of(tr).startswith("Alternative Flows")
           and "Deposit Lock Fails After All Retries" in text_of(tr))
cell = act.findall(qn("w:tc"))[-1]
paras = cell.findall(qn("w:p"))
step4 = next(p for p in paras if text_of(p).startswith("4. The Operator handles the failure"))
set_par_text(step4,
    "4. The failure is handled under UC29: the Operator retries the lock for the missing deposit after fixing the "
    "cause, or the parties ask to cancel. When both ask, or one asks after 5 business days from the first failure, "
    "System refunds every locked deposit and the contract becomes ACTIVATION_FAILED only after all refunds are "
    "confirmed. A successful lock before that makes the contract ACTIVE and the cancel requests lapse.")
post = next(tr for tr in body.iter(qn("w:tr")) if text_of(tr).startswith("Postconditions")
            and "waits for the Operator" in text_of(tr))
set_cell_text(post.findall(qn("w:tc"))[-1],
    "The contract is ACTIVE with the deposits locked and the first milestone funding started; or the contract stays "
    "SIGNED until a retried lock succeeds or the parties' cancel request ends it as ACTIVATION_FAILED.")

screen = next(tr for tr in body.iter(qn("w:tr")) if text_of(tr).startswith("48") and "Activation Failures" in text_of(tr))
set_cell_text(screen.findall(qn("w:tc"))[-1],
    "Allows the Operator to retry the deposit lock of a contract whose activation failed, see the parties' cancel "
    "requests and track the refunds")

mark = find(d, "Mark activation failed:")
set_par_text(mark,
    "View cancel requests: see which party asked to cancel and from when one party's request is enough; the "
    "Operator cannot cancel the contract, the parties decide (D42). (UC29)")
field = next(tr for tr in body.iter(qn("w:tr")) if text_of(tr).startswith("Mark Failed Confirmation"))
set_cell_text(field.findall(qn("w:tc"))[0], "Cancel Requests")
set_cell_text(field.findall(qn("w:tc"))[1],
    "Data type: label; the parties that asked to cancel and oneSidedCancelAllowedFrom, the time from which one "
    "party's request is enough")

msg = next(tr for tr in body.iter(qn("w:tr")) if text_of(tr).startswith("94MSG94"))
c = msg.findall(qn("w:tc"))
set_cell_text(c[3], "A party is about to ask to cancel a contract whose deposit lock failed.")
set_cell_text(c[4], "Ask to cancel this contract? The deposits already locked will be returned and nobody is "
                    "penalised. The contract ends when both parties ask, or 5 business days after the failure if only "
                    "one does. You can withdraw your request until the refund starts.")

wd = find(d, "Withdraw the offer: withdraw before the second signature")
ask = insert_copy_after(wd, wd)
set_par_text(ask,
    "Ask to cancel a contract whose deposit lock failed: confirm the cancel statement with an e-mail OTP (MSG94); "
    "withdraw the request until the refund starts. The contract ends with no penalty when both parties ask, or one "
    "asks after 5 business days. (UC29, GB-22)")

append_row(next_table(find(d, "Change Log")), [
    "10/11/2026", "M", "Group 6",
    "Context diagram and BF-01..BF-07 redrawn in Visual Paradigm notation (editable .drawio in "
    "docs/reports/figures/srs); BF-03 follows D42/D43. Activation failure (D42): the parties ask to cancel, the "
    "Operator only retries the lock — UC29, role matrix, UC Activate Contract alternative flow and postconditions, "
    "screens 5.2.2 and 7.5.1, screen list and MSG94 updated"])

d.save(DOC)
print("saved")
