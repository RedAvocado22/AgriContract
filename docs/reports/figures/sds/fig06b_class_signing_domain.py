"""Figure 6b. Contract signing — domain model (class diagram, UML / Visual Paradigm notation).

Names are the ones in backend/contract-service/src/main/java/com/agricontract/contract/domain/model on main
(10/11/2026): Contract, signature/{OtpChallenge,Signature}, consent/{ConsentEvidence,SignerRole,ConsentActionType},
terms/CanonicalTerms, vo/ContractStatus. Contract shows the fields and methods of the signing path; records show
all their components. Static members are underlined.
"""
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "tools"))
from vpdiagram import Diagram  # noqa: E402

d = Diagram("fig06b_class_signing_domain", width=722, height=1030, allow_crossings=True)
L, R, WL, WR = 170, 535, 300, 310  # columns 20-320 and 380-690, channel 320-380

d.klass("contract", L, 20, "Contract", "entity", attrs=[
    "- contractId : ContractId",
    "- listingId : String",
    "- buyerId : String",
    "- sellerId : String",
    "- coffeeTerms : CanonicalTerms",
    "- status : ContractStatus",
    "- version : Long",
    "- signedHashes : Map<SignerRole, String>",
], ops=[
    "+ reviseCoffeeTerms(userId : String,",
    "      revised : CanonicalTerms) : void",
    "+ consentToSign(userId : String,",
    "      claimedRole : SignerRole,",
    "      presentedStatementVersion : String,",
    "      presentedContentHash : String,",
    "      statements : ConsentStatements,",
    "      consentId : UUID, consentedAt : Instant,",
    "      correlationId : String) : ConsentEvidence",
    "+ sign(userId : String, verified : OtpChallenge,",
    "      authTime : Instant, signedAt : Instant,",
    "      ipAddress : String, signatureId : UUID)",
    "      : Signature",
    "+ roleOf(userId : String) : Optional<SignerRole>",
    "+ getSignedRoles() : Set<SignerRole>",
    "+ withdraw(userId : String) : SignerRole",
    "+ activate() : void",
], w=WL)
d.klass("terms", L, 520, "CanonicalTerms", "value object", attrs=[
    "+ snapshot : ContractTermsSnapshot",
    "+ canonicalJson : String",
    "+ signedContentHash : String",
], ops=[
    "__+ of(snapshot : ContractTermsSnapshot,",
    "__      canonicalizer : TermsCanonicalizer)",
    "__      : CanonicalTerms",
    "__+ restore(storedJson : String, storedHash : String,",
    "__      canonicalizer : TermsCanonicalizer)",
    "__      : CanonicalTerms",
    "__+ sha256(canonicalJson : String) : String",
], w=WL)
d.klass("status", L, 760, "ContractStatus", "enumeration", attrs=[
    "OFFERED", "NEGOTIATING", "SIGNED", "ACTIVE", "SETTLED", "ACTIVATION_REFUND_PENDING", "ACTIVATION_FAILED",
    "WITHDRAWN"], ops=None, w=WL)
d.klass("role", 85, 940, "SignerRole", "enumeration", attrs=["BUYER", "SELLER"], ops=None, w=130)
d.klass("action", 240, 940, "ConsentActionType", "enumeration", attrs=["SIGN_CONTRACT", "CANCEL_ACTIVATION"], ops=None,
        w=160)

d.klass("signature", R, 20, "Signature", "entity", attrs=[
    "+ signatureId : UUID",
    "+ contractId : String",
    "+ signerRole : SignerRole",
    "+ signerUserId : String",
    "+ otpId : UUID",
    "+ consentId : UUID",
    "+ authTime : Instant",
    "+ signedAt : Instant",
    "+ signedContentHash : String",
    "+ ipAddress : String",
], w=WR)
d.klass("consent", R, 290, "ConsentEvidence", "entity", attrs=[
    "+ consentId : UUID",
    "+ actionType : ConsentActionType",
    "+ subjectType : ConsentSubjectType",
    "+ subjectId : String",
    "+ actorUserId : String",
    "+ actorRole : SignerRole",
    "+ statementCode : String",
    "+ statementVersion : String",
    "+ statementHash : String",
    "+ presentedContentHash : String",
    "+ consentedAt : Instant",
    "+ correlationId : String",
], w=WR)
d.klass("otp", R, 600, "OtpChallenge", "entity", attrs=[
    "+ otpId : UUID",
    "+ consentId : UUID",
    "+ contractId : String",
    "+ signerRole : SignerRole",
    "+ userId : String",
    "+ authTime : Instant",
    "+ signedContentHash : String",
    "+ otpHash : String",
    "+ sentAt : Instant",
    "+ expiresAt : Instant",
    "+ verifiedAt : Instant",
    "+ attemptCount : int",
    "+ actionType : ConsentActionType",
], ops=[
    "__+ issue(otpId : UUID, consentId : UUID,",
    "__      contractId : String, signerRole : SignerRole,",
    "__      userId : String, authTime : Instant,",
    "__      signedContentHash : String, otpHash : String,",
    "__      now : Instant, expirySeconds : long,",
    "__      actionType : ConsentActionType) : OtpChallenge",
    "+ isUsable(now : Instant, maxAttempts : int) : boolean",
    "+ ensureUsable(now : Instant, maxAttempts : int) : void",
], w=WR)

n = d.nodes
def y(node, frac):
    x0, y0, x1, y1 = n[node].box()
    return y0 + (y1 - y0) * frac

d.rel("composition", "contract", "b", "terms", "t", label="coffeeTerms 0..1", label_at=(L + 8, y("contract", 1) + 14))
d.rel("dependency", "contract", "r", "signature", "l", s_off=y("signature", .4) - n["contract"].cy,
      d_off=y("signature", .4) - n["signature"].cy, label="«create»", label_at=(326, y("signature", .4) - 18))
d.rel("dependency", "contract", "r", "consent", "l", s_off=y("consent", .35) - n["contract"].cy,
      d_off=y("consent", .35) - n["consent"].cy, label="«create»", label_at=(326, y("consent", .35) - 18))
d.rel("association", "signature", "b", "consent", "t", label="consentId", label_at=(R + 6, y("signature", 1) + 8))
d.rel("association", "otp", "t", "consent", "b", label="consentId", label_at=(R + 6, y("otp", 0) - 26))
d.rel("association", "signature", "r", "otp", "r", (706, y("signature", .5)), (706, y("otp", .08)),
      d_off=y("otp", .08) - n["otp"].cy, label="otpId", label_at=(691, y("signature", .5) - 17))

if __name__ == "__main__":
    d.write(Path(__file__).resolve().parent)
