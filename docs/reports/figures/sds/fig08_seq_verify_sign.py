"""Figure 8. Sequence — verify OTP and sign (UML / Visual Paradigm notation).

Follows ContractSigningController.verify → VerifyOtpAndSignUseCase.execute / verifyAndSign in backend/contract-service
on main (10/11/2026), in that order: eligibility before the transaction; inside it the contract row lock, the
idempotency lookup, the challenge lock and checks, the OTP match, Contract.sign, the signature insert and, for the
second signature only, contract.signed + notification.contract_anchor_requested to the outbox. A wrong code is
counted and committed before the refusal. The signed PDF (#235) is not built yet and is not shown.
"""
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "tools"))
from seqdiagram import Seq  # noqa: E402

s = Seq("fig08_seq_verify_sign", 700, [
    ("P", "Buyer /\nSeller", "actor"),
    ("C", ":Contract\nSigning\nController"),
    ("V", ":VerifyOtp\nAndSign\nUseCase"),
    ("R", ":Contract\nRepository"),
    ("I", ":Idempotency\nStore"),
    ("Q", ":Otp\nChallenge\nRepository"),
    ("T", ":Contract"),
    ("S", ":Signature\nRepository"),
    ("B", ":Contract\nEvent\nPublisher"),
], row=20)

s.call("P", "C", "1: POST /contracts/{id}/sign/verify\n(Idempotency-Key, otpId, otpCode)")
s.call("C", "V", "1.1: execute(command)")
s.note("V", "signerEligibility.requireFreshLogin(authTime)", side=1, dx=14)
s.call("V", "R", "1.1.1: findById(contractId)")
s.reply("R", "V", "contract")
s.call("V", "T", "1.1.2: roleOf(actorId)")
s.reply("T", "V", "role")
s.note("V", "signerEligibility.requireEligible(actorId),\n.requireCounterpartyEligible(counterpartyId)", side=1, dx=14)

tx = s.begin("critical", "one transaction: verifyAndSign(command, role)", "V", "B")
s.call("V", "R", "1.1.3: findByIdForUpdate(contractId)")
s.reply("R", "V", "contract (row locked)")
s.call("V", "I", "1.1.4: lookup(actorId, OPERATION,\nidempotencyKey, requestHash)")
s.reply("I", "V", "previous (present: replay, return)")
s.call("V", "Q", "1.1.5: findByIdForUpdate(otpId)")
s.reply("Q", "V", "challenge")
s.note("V", "owner / contract / role / SIGN_CONTRACT checks;\nchallenge.ensureUsable(now, maxAttempts)", side=1, dx=14)
alt = s.begin("alt", "terms changed since the code", "V", "B")
s.call("V", "Q", "1.1.6: expire(otpId, now)")
s.done("Q")
s.separator(alt, "!otpCodes.matches(otpCode, otpHash)")
s.call("V", "Q", "1.1.7: recordFailedAttempt(otpId)")
s.reply("Q", "V", "attempts")
s.separator(alt, "code matches")
s.call("V", "Q", "1.1.8: markVerified(otpId, now)")
s.done("Q")
s.call("V", "T", "1.1.9: sign(actorId, challenge, authTime,\nnow, ipAddress, signatureId)")
s.reply("T", "V", "signature : Signature")
s.call("V", "S", "1.1.10: insert(signature)")
s.done("S")
s.call("V", "R", "1.1.11: save(contract)")
s.done("R")
both = s.begin("opt", "contract.getStatus() = SIGNED", "V", "B")
s.call("V", "B", "1.1.12: contractSigned(contract, now)")
s.done("B")
s.call("V", "B", "1.1.13: contractAnchorRequested(contract, now)")
s.done("B")
s.end(both)
s.call("V", "I", "1.1.14: claim(...); recordResult(..., signatureId)")
s.done("I")
s.end(alt)
s.end(tx)

s.reply("V", "C", "void, or the refusal committed above")
s.reply("C", "P", "204  (409 OTP_INVALID / OTP_TERMS_CHANGED / ...)")

if __name__ == "__main__":
    s.write(Path(__file__).resolve().parent)
