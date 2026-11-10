"""Figure 11. Sequence — escrow → bank ledger and completion reconciler (UML / Visual Paradigm notation).

bank-service is drawn from the code on main (10/11/2026): LedgerCommandConsumer.onReleaseRequested →
ProcessLedgerCommandUseCase.execute → LedgerEntryRepository.findBySourceEventId / append → LedgerResultPublisher
.completed. The escrow release and the contract-side reconciler are the design (milestone settlement is not built
yet); the emergency-lock check of the bank is planned (UC52).
"""
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "tools"))
from seqdiagram import Seq  # noqa: E402

s = Seq("fig11_seq_money_reconcile", 800, [
    ("Q", ":Message\nBroker"),
    ("E", ":Milestone\nSettled\nConsumer"),
    ("F", ":Release\nMilestone\nFunds\nUseCase"),
    ("A", ":Ledger\nCommand\nConsumer"),
    ("P", ":Process\nLedger\nCommand\nUseCase"),
    ("D", ":Ledger\nEntry\nRepository"),
    ("U", ":Ledger\nResult\nPublisher"),
    ("R", ":Completion\nReconciler"),
    ("Y", ":Milestone\nSync\nOutbox"),
    ("L", ":BankLedger\nQueryPort"),
    ("C", ":Contract"),
])

s.call("Q", "E", "1: on(milestone.settled)")
s.note("E", "markProcessed(consumer,\nenvelope) first", side=1, dx=12)
s.call("E", "F", "1.1: execute(cmd)")
s.call("F", "Q", "1.1.1: append(bank.release_requested,\nbank.refund_requested)")
s.done("Q")
s.reply("F", "E")
s.reply("E", "Q", "ack")

s.call("Q", "A", "2: onReleaseRequested(message)")
s.call("A", "P", "2.1: execute(command)")
s.call("P", "D", "2.1.1: findBySourceEventId(sourceEventId)")
s.reply("D", "P", "empty (a repeat replays the first result)")
s.note("P", "LedgerEntry.create(...)\nplanned: refuse while the\nemergency lock is on", side=1, dx=12)
s.call("P", "D", "2.1.2: append(entry)")
s.done("D")
s.call("P", "U", "2.1.3: completed(command, entry)")
s.note("U", "outbox:\nbank.release_completed", side=1, dx=12)
s.done("U")
s.reply("P", "A")
s.reply("A", "Q", "ack")

loop = s.begin("loop", "scheduled", "R", "C")
s.call("R", "Y", "3: nextUnprocessed()")
s.reply("Y", "R", "milestoneIds")
s.call("R", "L", "3.1: entriesOf(contractId)")
s.reply("L", "R", "ledger entries")
opt = s.begin("opt", "all legs done, lock = 0", "R", "C")
s.call("R", "C", "3.2: transitionTo(SETTLED)")
s.done("C")
s.end(opt)
s.end(loop)

if __name__ == "__main__":
    s.write(Path(__file__).resolve().parent)
    print("ok")
