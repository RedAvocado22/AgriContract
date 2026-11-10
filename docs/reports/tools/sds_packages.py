"""SDS I.2 backend package table: example class names as they are in contract-service (code first)."""
from pathlib import Path

import docx
from docx.oxml.ns import qn

from docxedit import find, next_table, set_cell_text, text_of

DOC = Path(__file__).resolve().parents[3] / "docs/reports/Report-4_SDS.docx"
d = docx.Document(DOC)
tbl = next_table(find(d, "2. Package Diagram"))
FIX = {
    "01": [("Signature, SignatureOtp, ConsentEvidence", "Signature, OtpChallenge, ConsentEvidence, CanonicalTerms"),
           ("(transitionTo, settleClean, resolve)", "(consentToSign, sign, withdraw, activate; settleClean, resolve planned)")],
    "02": [("Stateless domain services for rules that span aggregates or are pure calculations: SignedContentHasher "
            "(RFC 8785 + SHA-256), SettlementCalculator,",
            "Stateless domain services for rules that span aggregates or are pure calculations (planned): "
            "SettlementCalculator,")],
    "07": [("UserPort, ListingReservationPort, OtpDeliveryPort, FileStoragePort, BankLedgerQueryPort.",
            "UserPort, ListingPort, OtpEmailPort, OtpCodes, IdempotencyStore, ContractEventPublisher (implemented); "
            "ListingReservationPort, FileStoragePort, BankLedgerQueryPort (planned).")],
    "12": [("(UserServiceClient, ProductServiceClient, NotificationOtpClient)",
            "(UserServiceClient, ProductServiceClient; NotificationServiceClient in infrastructure.notification)")],
}
for tr in tbl.findall(qn("w:tr")):
    tcs = tr.findall(qn("w:tc"))
    no = text_of(tcs[0]).strip()
    if no in FIX:
        cell = tcs[-1]
        text = text_of(cell)
        for old, new in FIX[no]:
            if old not in text:
                raise SystemExit(f"row {no}: {old!r} not found in {text!r}")
            text = text.replace(old, new)
        set_cell_text(cell, text)
        print("fixed row", no)
d.save(DOC)
