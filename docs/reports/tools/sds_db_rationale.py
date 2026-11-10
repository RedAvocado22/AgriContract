"""SDS 3 Database Design: why one database per service instead of one shared database (mentor question, 10/11/2026).

Two body paragraphs inserted right after the section's opening paragraph, formatted as copies of it; one change log row.
Run once on the version before this change.
"""
import copy
import sys
from pathlib import Path

import docx

sys.path.insert(0, str(Path(__file__).parent))
from docxedit import append_row, find, next_table, set_par_text  # noqa: E402

DOC = Path(__file__).resolve().parents[1] / "Report-4_SDS.docx"
d = docx.Document(DOC)

intro = find(d, "Each service owns one MySQL 8 schema")

WHY = (
    "Why one database per service and not one shared database. (1) Ownership of money and evidence is enforced "
    "by the database, not by discipline: only bank-service's DB user can reach ledger_entry and lock_entry, and it "
    "may only INSERT and SELECT them; in a shared database every service holding a connection could update a "
    "ledger row, and the rule that bank-service is the only writer of money facts would depend on every developer "
    "never writing that query. The same holds for audit_record and the hash chain. (2) No hidden coupling: a "
    "service never joins another service's tables; what it needs is copied as a snapshot at the moment it matters "
    "(for example the parties, goods and plots in terms_snapshot when the offer is made), so a schema change in "
    "user-service or product-service cannot break contract-service, and a signed contract does not change when a "
    "profile or listing is edited later. (3) Failure isolation: heavy analytics queries, a notification outage or "
    "a long migration in one service cannot lock or slow the tables used by signing and settlement. (4) "
    "Independent evolution: each service versions its own Flyway migrations, so team members change their "
    "schemas without colliding."
)
COST = (
    "The cost is accepted on purpose: there is no ACID transaction and no JOIN across services. Consistency "
    "between services is kept by the transactional outbox and idempotent consumers (processed_event), by saga "
    "steps with explicit compensations (for example the activation refund), and by ledger reconciliation before "
    "any terminal contract state; reports that need data from several services read the analytics-service read "
    "model. For a system that moves deposits and payments, a wrong balance caused by a shared, writable table is "
    "a worse risk than this extra complexity."
)

p1 = copy.deepcopy(intro)
set_par_text(p1, WHY)
intro.addnext(p1)
p2 = copy.deepcopy(intro)
set_par_text(p2, COST)
p1.addnext(p2)

append_row(next_table(find(d, "Change Log")),
           ["10/11/2026", "A", "Minh Cuong",
            "3 Database Design: rationale for one database per service instead of a shared database, and the "
            "accepted cost (no cross-service transaction or join)"])

d.save(DOC)
print("ok")
