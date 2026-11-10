"""Figure 13. Sequence — flag, contest and Level 1.5 assignment (UML / Visual Paradigm notation).

The flag and the contest in contract-service are the design (dispute is not built yet). The assignment in
inspection-service is drawn from the code on main (10/11/2026): Level15RequestedConsumer.accept →
ManageLevel15Assignments.requested → AssignmentLifecycleRepository.lock / active / insert and
InspectorEligibilityPort.loadEligibleInspectors.
"""
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "tools"))
from seqdiagram import Seq  # noqa: E402

s = Seq("fig13_seq_flag_contest_assign", 860, [
    ("B", "Buyer", "actor"),
    ("S", "Seller", "actor"),
    ("C", ":Milestone\nController"),
    ("F", ":Flag\nMilestone\nIssue\nUseCase"),
    ("R", ":RespondTo\nMilestone\nFlag\nUseCase"),
    ("M", ":Milestone"),
    ("Q", ":Message\nBroker"),
    ("K", ":Level15\nRequested\nConsumer"),
    ("A", ":Manage\nLevel15\nAssignments"),
    ("D", ":Assignment\nLifecycle\nRepository"),
    ("E", ":Inspector\nEligibility\nPort"),
])

s.call("B", "C", "1: POST /milestones/{id}/flag\n(QUALITY_BELOW_TERMS, evidence)")
s.call("C", "F", "1.1: execute(cmd)")
s.call("F", "M", "1.1.1: flag(issue)")
s.reply("M", "F", "AWAITING_SELLER_RESPONSE")
s.note("F", "new BreachCase(milestoneId,\nQUALITY_BELOW_COMMITTED)", side=1, dx=12)
s.reply("F", "C")
s.reply("C", "B", "204 (MSG70: payment held)")

s.call("S", "C", "2: POST /milestones/{id}/respond (CONTEST)")
s.call("C", "R", "2.1: execute(cmd)")
s.note("R", "DisputeRoutingService\n.route(...) = LEVEL_1_5", side=1, dx=12)
s.call("R", "M", "2.1.1: contest()")
s.reply("M", "R", "CONTESTED")
s.call("R", "Q", "2.1.2: append(inspection.level1_5_requested)")
s.done("Q")
s.reply("R", "C")
s.reply("C", "S", "204 (MSG74)")

s.call("Q", "K", "3: accept(message)")
s.call("K", "A", "3.1: requested(command)")
s.call("A", "D", "3.1.1: lock(contractId,\nmilestoneId)")
s.done("D")
s.call("A", "D", "3.1.2: active(contractId,\nmilestoneId)")
s.reply("D", "A", "empty")
s.call("A", "E", "3.1.3: loadEligibleInspectors()")
s.reply("E", "A", "candidates")
s.note("A", "random pick;\ndeadline in\nbusiness days", side=1, dx=12)
s.call("A", "D", "3.1.4: insert(TimedAssignment)")
s.done("D")
s.reply("A", "K")
s.reply("K", "Q", "ack")

if __name__ == "__main__":
    s.write(Path(__file__).resolve().parent)
    print("ok")
