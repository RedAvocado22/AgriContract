"""Figure 7. Sequence — initiate sign (UML / Visual Paradigm notation).

Follows ContractSigningController.initiate → InitiateSignUseCase.execute / issueOrReplay → SignerEligibility,
IdempotencyStore, ContractRepository, SigningConsentRecorder.record, OtpChallengeIssuer.issue, OtpEmailPort.send in
backend/contract-service on main (10/11/2026), in that order. Message and parameter names are the code's.
"""
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "tools"))
from seqdiagram import Seq  # noqa: E402

s = Seq("fig07_seq_initiate_sign", 700, [
    ("P", "Buyer /\nSeller", "actor"),
    ("C", ":Contract\nSigning\nController"),
    ("U", ":InitiateSign\nUseCase"),
    ("E", ":Signer\nEligibility"),
    ("I", ":Idempotency\nStore"),
    ("R", ":Contract\nRepository"),
    ("K", ":Signing\nConsent\nRecorder"),
    ("O", ":Otp\nChallenge\nIssuer"),
    ("M", ":OtpEmail\nPort"),
])

s.call("P", "C", "1: POST /contracts/{id}/sign/initiate\n(Idempotency-Key, signerRole,\nconsentStatementVersion, presentedContentHash)")
s.call("C", "U", "1.1: execute(command)")
s.call("U", "E", "1.1.1: requireFreshLogin(authTime)")
s.done("E")
s.call("U", "E", "1.1.2: requireEligible(actorId)")
s.reply("E", "U", "signer : UserInfo")

tx = s.begin("critical", "one transaction: issueOrReplay(command)", "U", "M")
s.call("U", "I", "1.1.3: claim(actorId, OPERATION,\nidempotencyKey, requestHash)")
s.reply("I", "U", "outcome")
alt = s.begin("alt", "outcome = REPLAY", "U", "O")
s.call("U", "I", "1.1.4: resultOf(actorId, OPERATION, idempotencyKey)")
s.reply("I", "U", "otpId (same challenge, if still usable)")
s.separator(alt, "outcome = FIRST")
s.call("U", "R", "1.1.5: findById(contractId)")
s.reply("R", "U", "contract")
s.call("U", "K", "1.1.6: record(contract, actorId, signerRole,\nstatementVersion, presentedContentHash, correlationId)")
s.note("K", "contract.consentToSign(...)\nconsentEvidenceRepository\n.append(evidence)", side=-1, dx=12)
s.reply("K", "U", "consent : ConsentEvidence")
s.call("U", "O", "1.1.7: issue(contract, consent, authTime)")
s.note("O", "kill earlier codes,\ncooldown 60 s / 5 per h,\nnew code, insert\nOtpChallenge", side=-1, dx=12)
s.reply("O", "U", "otp : IssuedOtp")
s.call("U", "I", "1.1.8: recordResult(actorId, OPERATION,\nidempotencyKey, otpId)")
s.done("I")
s.end(alt)
s.end(tx)

s.call("U", "M", "1.1.9: send(OtpEmail)   [after commit]")
s.done("M")
fail = s.begin("opt", "OtpEmailNotSentException", "U", "O")
s.call("U", "O", "1.1.10: expire(otpId)")
s.done("O")
s.end(fail)
s.reply("U", "C", "SignChallengeView(otpId, expiresAt)")
s.reply("C", "P", "200 InitiateSignResponse  (502 if the e-mail failed)")

if __name__ == "__main__":
    s.write(Path(__file__).resolve().parent)
