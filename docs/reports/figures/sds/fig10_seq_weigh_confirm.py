"""Figure 10. Sequence — weigh, receive and confirm clean (UML / Visual Paradigm notation).

Milestone delivery is not built yet (10/11/2026): this is the design. The outbox call uses the shared eventing
library as it exists (OutboxWriter.append).
"""
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "tools"))
from seqdiagram import Seq  # noqa: E402

s = Seq("fig10_seq_weigh_confirm", 800, [
    ("S", "Seller", "actor"),
    ("B", "Buyer", "actor"),
    ("C", ":Milestone\nController"),
    ("W", ":Record\nSeller\nWeighed\nUseCase"),
    ("K", ":Confirm\nMilestone\nClean\nUseCase"),
    ("X", ":Settlement\nCalculator"),
    ("M", ":Milestone"),
    ("O", ":Outbox\nWriter"),
    ("Y", ":MilestoneSync\nOutbox"),
])

s.call("S", "C", "1: POST /milestones/{id}/weigh\n(weight, evidenceFileId)")
s.call("C", "W", "1.1: execute(cmd)")
s.call("W", "M", "1.1.1: recordSellerWeighed(weight, evidenceId)")
s.done("M")
s.call("W", "O", "1.1.2: append(milestone.seller_weighed)")
s.done("O")
s.reply("W", "C")
s.reply("C", "S", "204 (MSG67)")

s.call("B", "C", "2: POST /milestones/{id}/confirm\n(weight, evidenceFileId)")
s.call("C", "K", "2.1: execute(cmd)")
s.call("K", "M", "2.1.1: recordBuyerReceived(weight, evidenceId)")
s.done("M")
s.call("K", "X", "2.1.2: compute(terms, sellerWeight, buyerWeight)")
s.reply("X", "K", "settlement (release + refund = batchAmount)")
s.call("K", "M", "2.1.3: settle(settlement)")
s.done("M")
s.call("K", "O", "2.1.4: append(milestone.settled)")
s.done("O")
s.call("K", "Y", "2.1.5: add(milestoneId)")
s.done("Y")
s.reply("K", "C")
s.reply("C", "B", "204 (MSG69: payment released)")

if __name__ == "__main__":
    s.write(Path(__file__).resolve().parent)
    print("ok")
