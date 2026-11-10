"""SDS (Report-4) I.2: Figure 2 redrawn from the code (figures/sds/fig02_backend_packages.py) and the backend package
table completed with the packages contract-service really has. Runs once, after sds_arch.py. Content only."""
import copy
from pathlib import Path

import docx
from docx.oxml.ns import qn

from docxedit import EMU_PER_CM, append_row, find, next_table, png_size, set_cell_text, text_of

REPO = Path(__file__).resolve().parents[3]
DOC = REPO / "docs/reports/Report-4_SDS.docx"
FIG = REPO / "docs/reports/figures/sds"
d = docx.Document(DOC)

tbl = next_table(find(d, "Package descriptions — backend"))
rows = tbl.findall(qn("w:tr"))
if any(text_of(r.findall(qn("w:tc"))[1]) == "application.service" for r in rows):
    raise SystemExit("already applied")

cap = find(d, "Figure 2. Backend package diagram")
pic = cap.getprevious()
png = FIG / "fig02_backend_packages.png"
rid, _ = d.part.get_or_add_image(str(png))
next(pic.iter(qn("a:blip"))).set(qn("r:embed"), rid)
w, h = png_size(png)
cx = int(16.0 * EMU_PER_CM)
cy = int(cx * h / w)
for ext in [*pic.iter(qn("wp:extent")), *pic.iter(qn("a:ext"))]:
    if ext.get("cx") is not None:
        ext.set("cx", str(cx))
        ext.set("cy", str(cy))


def row_of(package):
    return next(r for r in tbl.findall(qn("w:tr")) if text_of(r.findall(qn("w:tc"))[1]) == package)


def insert_after(anchor, package, text):
    row = copy.deepcopy(anchor)
    tcs = row.findall(qn("w:tc"))
    set_cell_text(tcs[1], package)
    set_cell_text(tcs[2], text)
    anchor.addnext(row)
    return row


insert_after(row_of("application.dto / exception"), "application.service",
             "Application services shared by several use cases, no transaction of their own: SignerEligibility, "
             "SigningConsentRecorder, OtpChallengeIssuer, ActivationRefundStarter. Naming: <Noun><Role>.")
feign = row_of("infrastructure.feign / client")
set_cell_text(feign.findall(qn("w:tc"))[1], "infrastructure.feign")
a = insert_after(feign, "infrastructure.notification",
                 "NotificationServiceClient (OpenFeign) and OtpEmailPortAdapter, which implements OtpEmailPort: the "
                 "OTP e-mail is the only synchronous call to notification-service.")
a = insert_after(a, "infrastructure.otp",
                 "HmacOtpCodes implements the OtpCodes port (HMAC of the code, never the code itself); OtpProperties "
                 "holds length, lifetime and attempt limit.")
a = insert_after(a, "infrastructure.terms",
                 "JcsTermsCanonicalizer turns the contract terms into their RFC 8785 canonical text, the input of the "
                 "SHA-256 both parties sign; ContractTemplateProperties names the template.")
insert_after(a, "infrastructure.consent",
             "ConsentStatementProperties: the code, version and text of each consent statement a user confirms.")
for i, tr in enumerate(tbl.findall(qn("w:tr"))[1:], start=1):
    set_cell_text(tr.findall(qn("w:tc"))[0], f"{i:02d}")

append_row(next_table(find(d, "Change Log")), [
    "10/11/2026", "M", "Group 6",
    "Figure 2 redrawn from the packages and imports of contract-service (editable .drawio); the backend package "
    "table adds application.service and infrastructure.notification / otp / terms / consent, and drops the "
    "infrastructure.client package that does not exist"])
d.save(DOC)
print("saved")
