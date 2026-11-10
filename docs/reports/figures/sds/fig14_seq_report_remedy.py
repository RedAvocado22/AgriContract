"""Figure 14. Sequence — report confirmation, disposition and remedy fan-out (UML / Visual Paradigm notation).

Report submission (#129) and the contract-side decision are the design. Two calls already exist in
inspection-service on main (10/11/2026) and use their real names: InspectionHashes.calculateResultHash /
calculateReportHash, and ManageLevel15Assignments.closeConfirmed, which the report confirmation must call in its
own transaction.
"""
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "tools"))
from seqdiagram import Seq  # noqa: E402

s = Seq("fig14_seq_report_remedy", 860, [
    ("I", "Inspector", "actor"),
    ("C", ":Inspection\nController"),
    ("U", ":Submit\nInspection\nReport\nUseCase"),
    ("H", ":Inspection\nHashes"),
    ("A", ":Manage\nLevel15\nAssignments"),
    ("Q", ":Message\nBroker"),
    ("K", ":Report\nConfirmed\nConsumer"),
    ("P", ":Apply\nInspection\nResult\nUseCase"),
    ("X", ":Quality\nDisposition\nCalculator"),
    ("R", ":Remedy\nCalculator"),
    ("B", ":BreachCase"),
])

s.call("I", "C", "1: POST /inspections/{commissionId}/report\n(metrics, reportFile)")
s.call("C", "U", "1.1: execute(cmd)")
s.call("U", "H", "1.1.1: calculateResultHash(result)")
s.reply("H", "U", "resultHash")
s.call("U", "H", "1.1.2: calculateReportHash(report)")
s.reply("H", "U", "reportHash")
s.note("U", "new InspectionReport(...)", side=1, dx=12)
s.call("U", "A", "1.1.3: closeConfirmed(contract, milestone,\ncommission, inspector, confirmedAt)")
s.done("A")
s.call("U", "Q", "1.1.4: append(inspection.report_confirmed)")
s.done("Q")
s.reply("U", "C")
s.reply("C", "I", "201 (MSG96)")

s.call("Q", "K", "2: on(inspection.report_confirmed)")
s.call("K", "P", "2.1: execute(cmd)")
s.note("P", "verifyResultHash(result)", side=1, dx=12)
s.call("P", "X", "2.1.1: evaluate(spec, metrics)")
s.reply("X", "P", "disposition")
s.note("P", "new AttributionDecision(\nINSPECTOR_L1_5, role, disposition)", side=1, dx=12)
s.call("P", "R", "2.1.2: calculate(attribution, legalProfile)")
s.reply("R", "P", "remedyDecision with legs")
s.call("P", "B", "2.1.3: resolve(attribution)")
s.done("B")
s.call("P", "Q", "2.1.4: append(remedy.finalized)")
s.done("Q")
s.note("Q", "consumed by escrow-service\nand reputation-service", side=-1, dx=12)
s.reply("P", "K")
s.reply("K", "Q", "ack")

if __name__ == "__main__":
    s.write(Path(__file__).resolve().parent)
    print("ok")
