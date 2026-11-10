"""SDS I.3 Figures 4a, 4b, 5a, 5b: physical ERDs (crow's foot), keys and key columns only; 3.1-3.34 hold the full
column specifications.

Code first: a table created by Flyway is drawn from the schema the migrations produce (checked 10/11/2026 by
applying every service's migrations to MySQL 8.0 and reading information_schema). A table not created yet is
drawn «planned» (dashed) with its design columns. Lines are foreign keys (solid FK constraints, or the planned
ones of the design); cross-service references are ids without a line.
Output: fig04a/04b/05a/05b_*.drawio/.svg/.png.
"""
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "tools"))
from vpdiagram import Diagram  # noqa: E402

OUT = Path(__file__).resolve().parent
LEGEND = ("Bold underlined: primary key · italic: foreign key · (UQ) unique · dashed «planned»: in the design, "
          "not created by Flyway yet")

T = {
    # ---- contract_db, implemented (V1-V14) ----
    "contracts": dict(pk=["id : BIGINT"], cols=[
        "contract_id : VARCHAR(255) (UQ)", "listing_id : VARCHAR(255)", "buyer_id : VARCHAR(255)",
        "seller_id : VARCHAR(255)", "status : VARCHAR(30)", "terms_snapshot : LONGTEXT",
        "signed_content_hash : CHAR(64)", "version : BIGINT"]),
    "consent_evidence": dict(pk=["consent_id : CHAR(36)"], cols=[
        "action_type : VARCHAR(32)", "subject_type : VARCHAR(32)", "subject_id : VARCHAR(255)",
        "actor_user_id : VARCHAR(255)", "statement_hash : CHAR(64)", "presented_content_hash : CHAR(64)",
        "consented_at : TIMESTAMP(6)"]),
    "signature_otp": dict(pk=["otp_id : CHAR(36)"],
        fk=["consent_id : CHAR(36) (FK)", "contract_id : VARCHAR(255) (FK)"], cols=[
        "action_type : VARCHAR(32)", "signer_role : VARCHAR(16)", "user_id : VARCHAR(255)",
        "signed_content_hash : CHAR(64)", "otp_hash : CHAR(64)", "expires_at : TIMESTAMP(6)",
        "verified_at : TIMESTAMP(6)", "attempt_count : INT"]),
    "signature": dict(pk=["signature_id : CHAR(36)"],
        fk=["contract_id : VARCHAR(255) (FK)", "otp_id : CHAR(36) (FK, UQ)", "consent_id : CHAR(36) (FK)"], cols=[
        "signer_role : VARCHAR(16)", "signer_user_id : VARCHAR(255)", "signed_at : TIMESTAMP(6)",
        "signed_content_hash : CHAR(64)", "UQ (contract_id, signer_role)"]),
    "activation_recovery": dict(pk=["contract_id : VARCHAR(255) (FK)"], cols=[
        "deposit_lock_failed_at : TIMESTAMP(6)", "one_sided_cancel_allowed_from : TIMESTAMP(6)",
        "last_failed_leg : VARCHAR(8)", "failure_reports : INT"]),
    "activation_cancel_request": dict(pk=["request_id : CHAR(36)"],
        fk=["contract_id : VARCHAR(255) (FK)", "otp_id : CHAR(36) (FK, UQ)"], cols=[
        "party_role : VARCHAR(8)", "requested_at : TIMESTAMP(6)", "withdrawn_at : TIMESTAMP(6)",
        "standing_role : VARCHAR(8) (generated)", "UQ (contract_id, standing_role)"]),
    # ---- contract_db, planned ----
    "contract_document": dict(planned=True, pk=["document_id : CHAR(36)"], fk=["contract_id : CHAR(36) (FK)"], cols=[
        "document_type : VARCHAR(30)", "template_code : VARCHAR(50)", "source_content_hash : CHAR(64)",
        "file_id : CHAR(36)", "status : VARCHAR(10)"]),
    "milestone": dict(planned=True, pk=["milestone_id : CHAR(36)"], fk=["contract_id : CHAR(36) (FK)"], cols=[
        "milestone_index : INT", "status : VARCHAR(30)", "funding_status : VARCHAR(20)",
        "committed_quantity : DECIMAL(15,3)", "batch_amount : DECIMAL(18,2)",
        "effective_unit_price : DECIMAL(18,2)", "effective_delivery_deadline : DATE",
        "seller_declared_weight : DECIMAL(15,3)", "buyer_received_weight : DECIMAL(15,3)",
        "force_majeure_claim_id : CHAR(36)", "version : BIGINT"]),
    "price_adjustment": dict(planned=True, pk=["proposal_id : CHAR(36)"], fk=["milestone_id : CHAR(36) (FK)"], cols=[
        "proposed_unit_price : DECIMAL(18,2)", "proposer_id : CHAR(36)", "accepter_id : CHAR(36)",
        "status : VARCHAR(10)"]),
    "milestone_sync_outbox": dict(planned=True, pk=["outbox_id : CHAR(36)"], fk=["milestone_id : CHAR(36) (FK)"],
        cols=["processed : BOOLEAN", "retry_count : INT", "processed_at : TIMESTAMP"]),
    "breach_case": dict(planned=True, pk=["breach_case_id : CHAR(36)"],
        fk=["contract_id : CHAR(36) (FK)", "milestone_id : CHAR(36) (FK)"], cols=[
        "requested_by : VARCHAR(10)", "alleged_breaching_role : VARCHAR(10)", "breach_reason_code : VARCHAR(40)",
        "severity : VARCHAR(10)", "evidence_file_ids : JSON", "status : VARCHAR(15)",
        "contest_status : VARCHAR(15)", "contest_deadline : DATETIME(6)"]),
    "attribution_decision": dict(planned=True, pk=["attribution_decision_id : CHAR(36)"],
        fk=["contract_id : CHAR(36) (FK)", "breach_case_id : CHAR(36) (FK)"], cols=[
        "decision_source : VARCHAR(20)", "final_breaching_role : VARCHAR(10)", "quality_disposition : VARCHAR(25)",
        "evidence_refs : JSON", "created_at : DATETIME(6)"]),
    "remedy_decision": dict(planned=True, pk=["remedy_decision_id : CHAR(36)"],
        fk=["attribution_decision_id : CHAR(36) (FK, UQ)"], cols=[
        "affected_milestone_ids : JSON", "penalty_eligible : BOOLEAN", "reputation_eligible : BOOLEAN"]),
    "remedy_leg": dict(planned=True, pk=["remedy_leg_id : CHAR(36)"], fk=["remedy_decision_id : CHAR(36) (FK)"],
        cols=["remedy_type : VARCHAR(25)", "fund_type : VARCHAR(20)", "role : VARCHAR(10)",
              "amount : DECIMAL(18,2)"]),
    # ---- escrow_db (V9-V11) ----
    "deposit_leg": dict(pk=["leg_id : CHAR(36)"], cols=[
        "contract_id : CHAR(36)", "leg : VARCHAR(10)", "user_id : CHAR(36)", "amount : DECIMAL(18,2)",
        "status : VARCHAR(30)", "request_event_id : CHAR(36) (UQ)", "ledger_entry_id : CHAR(36)",
        "failed_attempts : INT", "next_attempt_at : DATETIME(6)", "return_request_event_id : CHAR(36) (UQ)",
        "return_ledger_entry_id : CHAR(36)"]),
    "escrow_milestone": dict(planned=True, pk=["milestone_id : CHAR(36)"], cols=[
        "contract_id : CHAR(36)", "status : VARCHAR(25)", "request_event_id : CHAR(36) (UQ)"]),
    "remedy_leg_command": dict(planned=True, pk=["remedy_leg_id : CHAR(36)"], cols=[
        "remedy_decision_id : CHAR(36)", "source_event_id : CHAR(36)", "bank_request_event_id : CHAR(36) (UQ)",
        "status : VARCHAR(15)"]),
    # ---- bank_db (V1) ----
    "ledger_entry": dict(pk=["entry_id : CHAR(36)"], cols=[
        "source_event_id : CHAR(36) (UQ)", "contract_id : CHAR(36)", "milestone_id : CHAR(36)",
        "user_id : CHAR(36)", "entry_type : VARCHAR(30)", "fund_type : VARCHAR(30)",
        "remedy_decision_id : CHAR(36)", "remedy_leg_id : CHAR(36) (UQ)", "amount : DECIMAL(18,2)",
        "created_at : DATETIME(6)"]),
    "system_lock": dict(planned=True, pk=["lock_id : CHAR(36)"], cols=[
        "status : VARCHAR(20)", "verifier_org_id : CHAR(36)", "triggered_at : DATETIME(6)",
        "released_at : DATETIME(6)"]),
    "used_nonce": dict(planned=True, pk=["nonce : VARCHAR(64)"], cols=["seen_at : DATETIME(6)"]),
    # ---- reputation_db (V1-V2) ----
    "completed_contract_fact": dict(pk=["contract_id : CHAR(36)"], cols=[
        "buyer_id : CHAR(36)", "seller_id : CHAR(36)", "settled_at : DATETIME(6)",
        "source_event_id : CHAR(36) (UQ)"]),
    "dispute_outcome_fact": dict(pk=["milestone_id : CHAR(36)"], cols=[
        "contract_id : CHAR(36)", "flagged_by_user_id : CHAR(36)", "resolution_favors : VARCHAR(10)",
        "resolved_at : DATETIME(6)", "source_event_id : CHAR(36) (UQ)"]),
    "lock_entry": dict(pk=["entry_id : CHAR(36)"], cols=[
        "source_event_id : CHAR(36) (UQ)", "remedy_decision_id : CHAR(36) (UQ)", "contract_id : CHAR(36)",
        "user_id : CHAR(36)", "penalized_role : VARCHAR(10)", "base_days : INT",
        "repeat_offense_multiplier : DECIMAL(3,2)", "track_record_multiplier : DECIMAL(3,2)",
        "zero_progress_multiplier : DECIMAL(3,2)", "lock_duration_days : INT", "locked_until : TIMESTAMP"]),
    "lock_override_event": dict(pk=["event_id : CHAR(36)"], fk=["lock_entry_id : CHAR(36) (FK)"], cols=[
        "override_type : VARCHAR(20)", "reason : TEXT", "proposed_by : CHAR(36)", "approved_by : CHAR(36)"]),
    "governance_action_request": dict(planned=True, pk=["request_id : CHAR(36)"], cols=[
        "action_type : VARCHAR(30)", "target_id : VARCHAR(100)", "status : VARCHAR(15)", "reason : TEXT"]),
    # ---- inspection_db (V1-V2) ----
    "level1_5_inspection_assignment": dict(pk=["commission_id : CHAR(36)"], cols=[
        "contract_id : CHAR(36)", "milestone_id : CHAR(36)", "inspector_id : CHAR(36)",
        "assigned_at : TIMESTAMP(6)", "ended_at : TIMESTAMP(6)", "assigned_by_service : VARCHAR(64)",
        "initiated_by_actor_id : CHAR(36)", "active_slot : TINYINT (generated)",
        "report_due_business_days : INT", "report_due_at : TIMESTAMP(6)"]),
    "level1_5_assignment_lock": dict(pk=["contract_id : CHAR(36)", "milestone_id : CHAR(36)"], cols=[]),
    "inspection_report": dict(planned=True, pk=["report_id : CHAR(36)"],
        fk=["l15_commission_id : CHAR(36) (FK, UQ)"], cols=[
        "tier : VARCHAR(10)", "contract_id : CHAR(36)", "milestone_id : CHAR(36)", "content : JSON",
        "normalized_result : JSON", "result_hash : CHAR(64)", "report_hash : CHAR(64)",
        "inspector_id : CHAR(36)", "external_org : VARCHAR(255)", "status : VARCHAR(20)"]),
    "inspector_signature": dict(planned=True, pk=["signature_id : CHAR(36)"], fk=["report_id : CHAR(36) (FK, UQ)"],
        cols=["signer_user_id : CHAR(36)", "auth_time : TIMESTAMP", "signed_at : TIMESTAMP",
              "report_hash : CHAR(64)"]),
    "level2_inspection_commission": dict(planned=True, pk=["commission_id : CHAR(36)"], cols=[
        "contract_id : CHAR(36)", "org : VARCHAR(255)", "intake_case_id : VARCHAR(255)", "status : VARCHAR(20)",
        "requested_at : TIMESTAMP"]),
    # ---- audit_db (planned service) ----
    "audit_record": dict(planned=True, pk=["record_id : CHAR(36)"], cols=[
        "subject_type : VARCHAR(20)", "subject_id : VARCHAR(100)", "source_event_type : VARCHAR(100)",
        "source_event_id : CHAR(36) (UQ)", "source_hash : CHAR(64)", "content : JSON",
        "record_hash : CHAR(64)", "prev_hash_global : CHAR(64)", "prev_hash_subject : CHAR(64)"]),
    "audit_anchor": dict(planned=True, pk=["anchor_id : CHAR(36)"], fk=["record_id : CHAR(36) (FK)"], cols=[
        "anchored_hash : CHAR(64)", "anchor_type : VARCHAR(20)", "proof : TEXT"]),
}


def tab(d, name, cx, top):
    t = T[name]
    return d.entity(name, cx, top, name, t["pk"], t.get("fk", ()), cols=t.get("cols", ()),
                    planned=t.get("planned", False))


def fig04a():
    d = Diagram("fig04a_contract_signing_erd", 800, 760)
    tab(d, "activation_recovery", 150, 20)
    tab(d, "contracts", 400, 230)
    tab(d, "contract_document", 660, 20)
    tab(d, "activation_cancel_request", 125, 250)
    tab(d, "signature", 680, 240)
    tab(d, "signature_otp", 400, 480)
    tab(d, "consent_evidence", 680, 560)
    d.er("contracts", "t", "activation_recovery", "r", (340, 78), s_off=-60, d="zero_one",
         label="has")
    d.er("contracts", "t", "contract_document", "l", (460, 100), s_off=60, label="renders")
    d.er("contracts", "r", "signature", "l", label="is signed by", side="t")
    d.er("contracts", "l", "activation_cancel_request", "r", label="receives", side="t")
    d.er("contracts", "b", "signature_otp", "t", label="is challenged by")
    d.er("signature_otp", "r", "signature", "b", (620, 500), d="zero_one", label="authorizes")
    d.er("consent_evidence", "l", "signature_otp", "r", label="precedes", side="t")
    d.er("consent_evidence", "t", "signature", "b", s_off=60, d_off=60, label="backs")
    d.er("signature_otp", "l", "activation_cancel_request", "b", (125, 560), d="zero_one", label="confirms")
    d.legend("legend", 5, 735, LEGEND)
    return d


def fig04b():
    d = Diagram("fig04b_milestone_dispute_erd", 800, 800)
    d.entity("contracts", 125, 40, "contracts", ghost=True)
    tab(d, "milestone", 125, 140)
    tab(d, "price_adjustment", 125, 460)
    tab(d, "milestone_sync_outbox", 340, 420)
    tab(d, "breach_case", 560, 90)
    tab(d, "attribution_decision", 560, 380)
    tab(d, "remedy_decision", 560, 625)
    tab(d, "remedy_leg", 230, 625)
    d.er("contracts", "b", "milestone", "t", d="many", label="has")
    d.er("contracts", "r", "breach_case", "t", (560, 53), label="concerns")
    d.er("contracts", "t", "attribution_decision", "r", (125, 8), (790, 8), (790, 468), label="decides", seg=1)
    d.er("milestone", "b", "price_adjustment", "t", s_off=-50, d_off=-50, label="adjusts")
    d.er("milestone", "r", "milestone_sync_outbox", "t", (340, 380), label="settles into", side="t")
    d.er("milestone", "r", "breach_case", "l", s="zero_one", label="may concern")
    d.er("breach_case", "b", "attribution_decision", "t", s="zero_one", d="zero_one", label="is resolved by")
    d.er("attribution_decision", "b", "remedy_decision", "t", d="zero_one", label="produces")
    d.er("remedy_decision", "l", "remedy_leg", "r", d="many", label="contains", side="t")
    d.legend("legend", 5, 780, LEGEND)
    return d


def fig05a():
    d = Diagram("fig05a_money_stores_erd", 800, 935)
    d.package("escrow_db", 5, 30, 790, 251, "escrow_db")
    tab(d, "deposit_leg", 140, 45)
    tab(d, "escrow_milestone", 405, 45)
    tab(d, "remedy_leg_command", 660, 45)
    d.package("bank_db", 5, 310, 790, 236, "bank_db")
    tab(d, "ledger_entry", 140, 325)
    tab(d, "system_lock", 405, 325)
    tab(d, "used_nonce", 660, 325)
    d.package("reputation_db", 5, 575, 790, 330, "reputation_db")
    tab(d, "lock_entry", 140, 590)
    tab(d, "completed_contract_fact", 405, 590)
    tab(d, "governance_action_request", 660, 590)
    tab(d, "lock_override_event", 405, 760)
    tab(d, "dispute_outcome_fact", 660, 760)
    d.er("lock_entry", "b", "lock_override_event", "l", (140, 840), label="is overridden by", seg=1)
    d.legend("legend", 5, 915, LEGEND)
    return d


def fig05b():
    d = Diagram("fig05b_evidence_stores_erd", 800, 605)
    d.package("inspection_db", 5, 30, 520, 540, "inspection_db")
    tab(d, "level1_5_inspection_assignment", 125, 45)
    tab(d, "level2_inspection_commission", 400, 45)
    tab(d, "level1_5_assignment_lock", 400, 215)
    tab(d, "inspection_report", 130, 320)
    tab(d, "inspector_signature", 410, 320)
    d.package("audit_db", 545, 30, 250, 540, "audit_db")
    tab(d, "audit_record", 670, 45)
    tab(d, "audit_anchor", 670, 320)
    d.er("level1_5_inspection_assignment", "b", "inspection_report", "t", d="zero_one", label="authorizes")
    d.er("inspection_report", "r", "inspector_signature", "l", d="zero_one", label="signed by", side="t")
    d.er("audit_record", "b", "audit_anchor", "t", label="is anchored by")
    d.legend("legend", 5, 585, LEGEND)
    return d


if __name__ == "__main__" and sys.argv[1:] != ["measure"]:
    failed = []
    for build in (fig04a, fig04b, fig05a, fig05b):
        try:
            build().write(OUT)
            print("ok", build.__name__)
        except SystemExit as e:
            failed.append(str(e))
    if failed:
        print("\n".join(failed))
        sys.exit(1)

if __name__ == "__main__" and sys.argv[1:] == ["measure"]:
    d = Diagram("m", 800, 800)
    for k in T:
        n = tab(d, k, 0, 0)
        print(f"{k:32s} w={n.w:5.0f} h={n.h:4.0f}")
