"""SDS (Report-4): bring the contract-signing part in line with the code on main (10/11/2026, code first).

Touches only content: II.1 text, Figures 6-8 (6 becomes 6a + 6b), the II.1 class table, I.3 tables 3.1 and 3.3-3.5,
the contract_db row of the schema overview, one change-log row. Run from the repo root; it edits the file in place.
"""
from pathlib import Path

import docx

from docxedit import (fill_change_log, find, insert_copy_after, next_table, replace_image, set_par_text,
                      set_table_rows)

REPO = Path(__file__).resolve().parents[3]
DOC = REPO / "docs/reports/Report-4_SDS.docx"
FIG = REPO / "docs/reports/figures/sds"

d = docx.Document(DOC)

# ---------- II.1 text ----------
sec = find(d, "1. Contract Signing")
set_par_text(sec.getnext(),
    "Signing is split into two HTTP calls on ContractSigningController. InitiateSignUseCase refuses a session older "
    "than 300 s (auth_time) and an ineligible signer (KYC VERIFIED, authorization not expired, not locked; "
    "fail-closed), then in one transaction claims the Idempotency-Key, records the checkbox ConsentEvidence "
    "(SigningConsentRecorder) and issues an OtpChallenge bound to (signer, contract, signedContentHash) "
    "(OtpChallengeIssuer); the code is e-mailed after commit, and the challenge is expired if the e-mail fails. "
    "VerifyOtpAndSignUseCase checks the eligibility of both parties, then in one transaction under the contract row "
    "lock matches the code, calls Contract.sign and inserts the Signature; the second signature on the same hash "
    "makes the contract SIGNED and writes contract.signed and notification.contract_anchor_requested to the outbox "
    "in that transaction. The final signed PDF (ContractDocument, issue #235) is not implemented yet.")

# ---------- Figure 6 -> 6a + 6b ----------
cap6 = find(d, "Figure 6.", after=sec)
pic6 = cap6.getprevious()
replace_image(d, pic6, FIG / "fig06a_class_signing_application.png")
set_par_text(cap6, "Figure 6a. Class diagram — contract signing, application layer")
pic6b = insert_copy_after(cap6, pic6)
replace_image(d, pic6b, FIG / "fig06b_class_signing_domain.png")
cap6b = insert_copy_after(pic6b, cap6)
set_par_text(cap6b, "Figure 6b. Class diagram — contract signing, domain model")
note = cap6b.getnext()
set_par_text(note, "The diagrams show the classes of the signing path with the names used in the code; "
                   "supporting classes (adapters, configuration) are listed in the table.")

set_table_rows(next_table(note), [
    ["ContractSigningController", "infrastructure.web «RestController»",
     "Maps POST /api/v1/contracts/{id}/sign/initiate and /sign/verify for BUYER or SELLER; reads the body "
     "strictly; the caller and auth_time come from the authenticated principal set from the gateway headers."],
    ["InitiateSignUseCase", "application.usecase",
     "requireFreshLogin and requireEligible; in one transaction claims the Idempotency-Key, records ConsentEvidence "
     "and issues the OtpChallenge; e-mails the code after commit (challenge expired and 502 if the e-mail fails); a "
     "replayed key returns the same challenge while it is usable."],
    ["VerifyOtpAndSignUseCase", "application.usecase",
     "Checks both parties' eligibility; in one transaction under the contract row lock: idempotency lookup, "
     "challenge lock and checks (owner, contract, role, SIGN_CONTRACT, usable), terms unchanged, HMAC match (a "
     "wrong code is counted and committed), Contract.sign, Signature insert; on the second signature writes "
     "contract.signed and notification.contract_anchor_requested to the outbox."],
    ["Contract", "domain.model «AggregateRoot»",
     "consentToSign and sign guard the party, the state (OFFERED or NEGOTIATING), one signature per party and the "
     "same signedContentHash for both; two signatures make it SIGNED. reviseCoffeeTerms is refused after the first "
     "signature; withdraw is allowed until the second."],
    ["OtpChallenge / ConsentEvidence / Signature", "domain.model (records)",
     "Challenge with HMAC hash, expiry and attempt counter (isUsable, ensureUsable); append-only consent evidence; "
     "immutable signature."],
    ["CanonicalTerms ← JcsTermsCanonicalizer", "domain.model / infrastructure.terms",
     "RFC 8785 canonical JSON of ContractTermsSnapshot (template code, version and hash, coffee quality profile "
     "version included) and its SHA-256, the signedContentHash; restore recomputes the hash and refuses a mismatch."],
    ["SignerEligibility, UserPort ← UserPortAdapter", "application.service / port / Feign adapter",
     "Fresh login (auth_time within contract.signature.auth-max-age-seconds) and eligibility read from "
     "user-service: KYC VERIFIED, authorization not expired, not locked; user-service down → 503."],
    ["SigningConsentRecorder, OtpChallengeIssuer, OtpCodes ← HmacOtpCodes, OtpEmailPort ← OtpEmailPortAdapter",
     "application.service / port / adapter",
     "Consent from the active statement version. The issuer kills earlier codes of the same signer and terms, "
     "enforces a 60 s cooldown and 5 codes per hour, and stores only the HMAC of the 6-digit code (expiry 300 s, "
     "5 attempts); the e-mail goes through notification-service synchronously."],
    ["ContractEventPublisher ← OutboxContractEventPublisher, IdempotencyStore ← JdbcIdempotencyStore",
     "port / adapter",
     "Events are written to event_outbox (java-eventing) in the caller's transaction and checked against the "
     "frozen schema; Idempotency-Key records are kept per (actor, operation, key) with the request hash."],
])

# ---------- Figures 7 and 8 ----------
cap7 = find(d, "Figure 7.", after=sec)
replace_image(d, cap7.getprevious(), FIG / "fig07_seq_initiate_sign.png")
cap8 = find(d, "Figure 8.", after=sec)
replace_image(d, cap8.getprevious(), FIG / "fig08_seq_verify_sign.png")
set_par_text(cap8, "Figure 8. Sequence — verify OTP and sign")

# ---------- I.3 tables ----------
ov = next_table(find(d, "Schema overview"))
rows = ov.findall(docx.oxml.ns.qn("w:tr"))
cells = rows[1].findall(docx.oxml.ns.qn("w:tc"))
from docxedit import set_cell_text  # noqa: E402
set_cell_text(cells[2],
    "contracts, signature, signature_otp, consent_evidence, idempotency_record, activation_recovery, "
    "activation_cancel_request, event_outbox, processed_event (implemented); milestone, contract_document, "
    "breach_case, attribution_decision, remedy_decision, remedy_leg, price_adjustment, milestone_sync_outbox "
    "(planned)")

h31 = find(d, "3.1 contract")
set_par_text(h31, "3.1 contracts")
set_par_text(h31.getnext(),
    "contract_db · Aggregate root of a forward contract (table contracts). The terms are stored as their exact "
    "canonical JSON text with its SHA-256; the Phase 1 columns are kept as a read projection until the Phase 1 "
    "cleanup.")
set_table_rows(next_table(h31), [
    ["id", "BIGINT", "PK, AUTO_INCREMENT", "Technical key"],
    ["contract_id", "VARCHAR(255)", "NOT NULL, UNIQUE",
     "UUID, cross-service identity; referenced by signature, signature_otp and the activation tables"],
    ["listing_id", "VARCHAR(255)", "NOT NULL", "product-service listing (no FK)"],
    ["buyer_id / seller_id", "VARCHAR(255)", "NOT NULL, indexed",
     "Keycloak sub of the parties; ownership checks use these"],
    ["product_name, buyer_org_name, seller_org_name, buyer_email, seller_email", "VARCHAR(255)", "NOT NULL",
     "Snapshot taken at offer time; the e-mails address the notifications"],
    ["status", "VARCHAR(30)", "NOT NULL, indexed",
     "OFFERED, NEGOTIATING, SIGNED, ACTIVE, SETTLED, ACTIVATION_REFUND_PENDING, ACTIVATION_FAILED, WITHDRAWN "
     "(ContractStatus; checked in the domain, no CHECK constraint)"],
    ["terms_snapshot", "LONGTEXT utf8mb4_bin", "NULL",
     "ContractTermsSnapshot as its exact RFC 8785 text (not the MySQL JSON type, which rewrites the text and would "
     "change the hash); NULL only for Phase 1 rows"],
    ["signed_content_hash", "CHAR(64) ascii_bin", "NULL, CHECK chk_contracts_terms_hash_pair",
     "SHA-256 of terms_snapshot; both NULL or both set, 64 lowercase hex"],
    ["quantity, quantity_unit, agreed_price, currency, delivery_deadline, buyer_penalty_rate, seller_deposit_rate, "
     "quality_spec, signatories", "Phase 1 types", "Phase 1",
     "Read projection of the coffee terms; removed in the Phase 1 cleanup"],
    ["version", "BIGINT", "NOT NULL DEFAULT 0", "Optimistic locking"],
    ["created_at / updated_at", "DATETIME", "NOT NULL", "Set by MySQL"],
    ["reserved_quantity", "DECIMAL(15,3)", "planned (#232)",
     "kg reserved on the listing; must equal Σ committedQuantity before SIGNED (F18)"],
])

set_table_rows(next_table(find(d, "3.3 signature")), [
    ["signature_id", "CHAR(36)", "PK", ""],
    ["contract_id", "VARCHAR(255)", "NOT NULL, FK → contracts(contract_id)", ""],
    ["signer_role", "VARCHAR(16)", "NOT NULL, CHECK BUYER | SELLER, UQ(contract_id, signer_role)",
     "Each party signs exactly once"],
    ["signer_user_id", "VARCHAR(255)", "NOT NULL", "JWT sub"],
    ["otp_id", "CHAR(36)", "NOT NULL, UNIQUE, FK → signature_otp", "The challenge that was answered"],
    ["consent_id", "CHAR(36)", "NOT NULL, FK → consent_evidence", "The checkbox consent behind it"],
    ["auth_time", "TIMESTAMP(6)", "NOT NULL", "Login time of the session that signed (step-up, ≤ 300 s)"],
    ["signed_at", "TIMESTAMP(6)", "NOT NULL", "Server time of the OTP verification"],
    ["signed_content_hash", "CHAR(64) ascii_bin", "NOT NULL, CHECK 64 hex",
     "Must equal the other party's hash; append-only (triggers refuse UPDATE and DELETE)"],
    ["ip_address", "VARCHAR(45)", "NULL", "Supporting evidence only"],
])
set_par_text(find(d, "3.3 signature").getnext(),
    "contract_db · One row per signing party; two rows with the same hash make the contract SIGNED. The table is the "
    "only source of who signed.")

set_table_rows(next_table(find(d, "3.4 signature_otp")), [
    ["otp_id", "CHAR(36)", "PK", "Returned to the client; verification is by otp_id, never \"latest OTP\""],
    ["consent_id", "CHAR(36)", "NOT NULL, FK → consent_evidence", "Consent recorded with the challenge"],
    ["action_type", "VARCHAR(32)", "NOT NULL DEFAULT SIGN_CONTRACT, CHECK",
     "SIGN_CONTRACT or CANCEL_ACTIVATION (D42): a code only confirms the action it was issued for"],
    ["contract_id / signer_role / user_id", "VARCHAR(255) / VARCHAR(16) / VARCHAR(255)",
     "NOT NULL, FK → contracts, CHECK BUYER | SELLER", "Binding tuple; indexed with signed_content_hash"],
    ["auth_time", "TIMESTAMP(6)", "NOT NULL", "Carried from initiate to verify"],
    ["signed_content_hash", "CHAR(64) ascii_bin", "NOT NULL", "Terms hash at issue time; a mismatch kills the code"],
    ["otp_hash", "CHAR(64) ascii_bin", "NOT NULL", "HMAC-SHA256(code, OTP_PEPPER), never the plain code"],
    ["sent_at / expires_at", "TIMESTAMP(6)", "NOT NULL",
     "expires_at = sent_at + 300 s; a resend or a withdrawn offer sets earlier challenges' expires_at = now"],
    ["verified_at", "TIMESTAMP(6)", "NULL", "Set once by the right code"],
    ["attempt_count", "INT", "NOT NULL DEFAULT 0, CHECK ≥ 0", "Refused from 5 wrong codes"],
])

set_table_rows(next_table(find(d, "3.5 consent_evidence")), [
    ["consent_id", "CHAR(36)", "PK", ""],
    ["action_type", "VARCHAR(32)", "NOT NULL", "SIGN_CONTRACT, CANCEL_ACTIVATION (other actions planned)"],
    ["subject_type / subject_id", "VARCHAR(32) / VARCHAR(255)", "NOT NULL", "CONTRACT and its contract_id"],
    ["actor_user_id / actor_role", "VARCHAR(255) / VARCHAR(16)", "NOT NULL", ""],
    ["statement_code / statement_version / statement_hash", "VARCHAR(64) / VARCHAR(16) / CHAR(64)",
     "NOT NULL, CHECK hash 64 hex", "Server-resolved; SHA-256 of the exact checkbox text"],
    ["presented_content_hash", "CHAR(64)", "NOT NULL, CHECK 64 hex", "Must equal the current terms hash"],
    ["consented_at", "TIMESTAMP(6)", "NOT NULL", "Append-only: triggers refuse UPDATE and DELETE"],
    ["correlation_id", "VARCHAR(64)", "NULL", "The request's Idempotency-Key"],
])

fill_change_log(next_table(find(d, "Change Log")), [
    "10/11/26", "M", "Minh Cuong",
    "Contract signing brought in line with the code (code first): II.1 text and class table; Figure 6 split into 6a "
    "(application layer) and 6b (domain model); Figures 6-8 redrawn from the code in Visual Paradigm UML notation "
    "(editable .drawio in docs/reports/figures/sds); tables contracts, signature, signature_otp, consent_evidence "
    "and the contract_db schema row as implemented"])

d.save(DOC)
print("saved", DOC)
