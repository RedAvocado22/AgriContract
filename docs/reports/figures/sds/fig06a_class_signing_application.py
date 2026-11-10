"""Figure 6a. Contract signing — application layer (class diagram, UML / Visual Paradigm notation).

Every class, method and parameter name is the one in backend/contract-service (main, 10/11/2026):
infrastructure/web/ContractSigningController, application/usecase/{InitiateSign,VerifyOtpAndSign}UseCase,
application/service/{SignerEligibility,SigningConsentRecorder,OtpChallengeIssuer}, application/port/*,
domain/repository/*.
"""
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "tools"))
from vpdiagram import Diagram  # noqa: E402

d = Diagram("fig06a_class_signing_application", width=700, height=1020, allow_crossings=True)
L, R = 175, 525  # column centres; columns 20-330 and 370-680, channels at x=10, 350, 690
W = 310

d.klass("ctl", 350, 20, "ContractSigningController", "control", ops=[
    "+ initiate(contractId : UUID, idempotencyKey : UUID,",
    "      body : String, auth : Authentication)",
    "      : ResponseEntity<InitiateSignResponse>",
    "+ verify(contractId : UUID, idempotencyKey : UUID,",
    "      body : String, httpRequest : HttpServletRequest,",
    "      auth : Authentication) : ResponseEntity<Void>",
], w=440)
d.klass("init", L, 210, "InitiateSignUseCase", "control", ops=[
    "+ execute(command : InitiateSignCommand)",
    "      : SignChallengeView",
    "- issueOrReplay(command : InitiateSignCommand)",
    "      : Issued",
    "- replayed(command : InitiateSignCommand)",
    "      : SignChallengeView",
], w=W)
d.klass("verify", R, 210, "VerifyOtpAndSignUseCase", "control", ops=[
    "+ execute(command : VerifySignCommand) : void",
    "- verifyAndSign(command : VerifySignCommand,",
    "      role : SignerRole) : RuntimeException",
], w=W)
d.klass("elig", R, 375, "SignerEligibility", "control", ops=[
    "+ requireFreshLogin(authTime : Instant) : void",
    "+ requireEligible(userId : String) : UserInfo",
    "+ requireCounterpartyEligible(",
    "      counterpartyId : String) : void",
], w=W)
d.klass("issuer", L, 430, "OtpChallengeIssuer", "control", ops=[
    "+ issue(contract : Contract,",
    "      consent : ConsentEvidence,",
    "      authTime : Instant) : IssuedOtp",
    "+ expire(otpId : UUID) : void",
], w=W)
d.klass("consent", L, 600, "SigningConsentRecorder", "control", ops=[
    "+ record(contract : Contract, actorId : String,",
    "      signerRole : SignerRole,",
    "      statementVersion : String,",
    "      presentedContentHash : String,",
    "      correlationId : String) : ConsentEvidence",
], w=W)

PT = 548
d.package("ports", 370, PT, W, 264, "application.port")
for i, name in enumerate(("UserPort", "OtpEmailPort", "OtpCodes", "ContractEventPublisher", "IdempotencyStore")):
    d.klass(f"p{i}", R, PT + 12 + i * 50, name, "interface", w=210, collapsed=True)
RT = 780
d.package("repos", 20, RT, W, 214, "domain.repository")
for i, name in enumerate(("ContractRepository", "OtpChallengeRepository", "SignatureRepository",
                          "ConsentEvidenceRepository")):
    d.klass(f"r{i}", L, RT + 12 + i * 50, name, "interface", w=230, collapsed=True)

n = d.nodes
dep = "dependency"
def y(node, frac):  # a y inside a node's height, 0 = top, 1 = bottom
    x0, y0, x1, y1 = n[node].box()
    return y0 + (y1 - y0) * frac

d.rel(dep, "ctl", "b", "init", "t", s_off=L - 350)
d.rel(dep, "ctl", "b", "verify", "t", s_off=R - 350)
d.rel(dep, "verify", "b", "elig", "t")
d.rel(dep, "elig", "b", "ports", "t")
d.rel(dep, "init", "b", "issuer", "t")
d.rel(dep, "consent", "b", "repos", "t")
# InitiateSignUseCase fans out through the channel at x = 350
by = y("init", .45)
for target, ty, side_x in (("elig", n["elig"].cy, 370), ("ports", y("ports", .25), 370),
                           ("consent", n["consent"].cy, 330), ("repos", y("repos", .2), 330)):
    d.edge("init", target, (330, by), (350, by), (350, ty), (side_x, ty), dashed=True)
# VerifyOtpAndSignUseCase fans out through the channel at x = 690
vy = n["verify"].cy
d.edge("verify", "ports", (680, vy), (690, vy), (690, y("ports", .75)), (680, y("ports", .75)), dashed=True)
d.edge("verify", "repos", (680, vy), (690, vy), (690, y("repos", .6)), (330, y("repos", .6)), dashed=True)
d.edge("issuer", "ports", (330, y("issuer", .9)), (370, y("issuer", .9)), dashed=True)
d.edge("issuer", "repos", (20, y("issuer", .5)), (10, y("issuer", .5)), (10, y("repos", .85)),
       (20, y("repos", .85)), dashed=True)
for e in d.edges:
    e.kind = dep

if __name__ == "__main__":
    d.write(Path(__file__).resolve().parent)
