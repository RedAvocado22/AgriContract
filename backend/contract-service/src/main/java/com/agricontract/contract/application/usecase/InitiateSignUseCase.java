package com.agricontract.contract.application.usecase;

import com.agricontract.contract.application.dto.InitiateSignCommand;
import com.agricontract.contract.application.dto.IssuedOtp;
import com.agricontract.contract.application.dto.OtpEmail;
import com.agricontract.contract.application.dto.SignChallengeView;
import com.agricontract.contract.application.dto.UserInfo;
import com.agricontract.contract.application.exception.ContractNotFoundException;
import com.agricontract.contract.application.exception.IdempotencyKeyReusedException;
import com.agricontract.contract.application.exception.OtpEmailNotSentException;
import com.agricontract.contract.application.port.IdempotencyStore;
import com.agricontract.contract.application.port.OtpEmailPort;
import com.agricontract.contract.application.service.OtpChallengeIssuer;
import com.agricontract.contract.application.service.SignerEligibility;
import com.agricontract.contract.application.service.SigningConsentRecorder;
import com.agricontract.contract.domain.exception.ContractConflictException;
import com.agricontract.contract.domain.model.Contract;
import com.agricontract.contract.domain.model.consent.ConsentEvidence;
import com.agricontract.contract.domain.model.signature.OtpChallenge;
import com.agricontract.contract.domain.model.signature.OtpPolicy;
import com.agricontract.contract.domain.model.terms.CanonicalTerms;
import com.agricontract.contract.domain.model.vo.ContractId;
import com.agricontract.contract.domain.repository.ContractRepository;
import com.agricontract.contract.domain.repository.OtpChallengeRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.util.UUID;

/**
 * InitiateSign (signature design §6, #96). Order matters:
 * <ol>
 *   <li>Fresh login and signer profile are checked first, outside any transaction (user-service call).</li>
 *   <li>One transaction: claim the Idempotency-Key, append the consent, kill older codes, enforce the send limits,
 *       insert the new challenge. An OTP never exists without its consent.</li>
 *   <li>After commit, ask notification-service to send the email. If it does not confirm, the new code is killed
 *       before the error is returned, so an email that still arrives late carries a dead code. No retry.</li>
 * </ol>
 * A replay of the same key returns the same challenge while it is usable and never sends a second email.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class InitiateSignUseCase {
    static final String OPERATION = "INITIATE_SIGN";
    public static final String CHALLENGE_NOT_ACTIVE = "OTP_CHALLENGE_NOT_ACTIVE";

    private final SignerEligibility eligibility;
    private final IdempotencyStore idempotencyStore;
    private final ContractRepository contractRepository;
    private final SigningConsentRecorder consentRecorder;
    private final OtpChallengeIssuer issuer;
    private final OtpChallengeRepository challenges;
    private final OtpPolicy policy;
    private final OtpEmailPort otpEmailPort;
    private final TransactionTemplate tx;
    private final Clock clock;

    public SignChallengeView execute(InitiateSignCommand command) {
        eligibility.requireFreshLogin(command.authTime());
        UserInfo signer = eligibility.requireEligible(command.actorId());

        Issued issued = tx.execute(status -> issueOrReplay(command));
        if (issued.replayed()) {
            return issued.view();
        }

        OtpChallenge challenge = issued.otp().challenge();
        try {
            otpEmailPort.send(new OtpEmail(challenge.otpId().toString(), command.actorId(), signer.email(),
                    challenge.contractId(), issued.otp().code(), challenge.signedContentHash(),
                    challenge.expiresAt()));
        } catch (OtpEmailNotSentException notSent) {
            issuer.expire(challenge.otpId());
            throw notSent;
        }
        log.info("Signing code {} sent for contract {} to {} as {}", challenge.otpId(), challenge.contractId(),
                command.actorId(), challenge.signerRole());
        return issued.view();
    }

    private Issued issueOrReplay(InitiateSignCommand command) {
        String requestHash = CanonicalTerms.sha256(String.join("\n", command.contractId(),
                command.signerRole().name(), command.consentStatementVersion(), command.presentedContentHash()));
        IdempotencyStore.Outcome outcome = idempotencyStore.claim(command.actorId(), OPERATION,
                command.idempotencyKey(), requestHash);
        if (outcome == IdempotencyStore.Outcome.CONFLICT) {
            throw new IdempotencyKeyReusedException();
        }
        if (outcome == IdempotencyStore.Outcome.REPLAY) {
            return Issued.replay(replayed(command));
        }

        Contract contract = contractRepository.findById(new ContractId(command.contractId()))
                .orElseThrow(() -> new ContractNotFoundException(command.contractId()));
        ConsentEvidence consent = consentRecorder.record(contract, command.actorId(), command.signerRole(),
                command.consentStatementVersion(), command.presentedContentHash(),
                command.idempotencyKey().toString());
        IssuedOtp otp = issuer.issue(contract, consent, command.authTime());
        idempotencyStore.recordResult(command.actorId(), OPERATION, command.idempotencyKey(),
                otp.challenge().otpId().toString());
        return Issued.fresh(otp);
    }

    /** Same key again (double click, network retry): the same challenge while it can still be answered. */
    private SignChallengeView replayed(InitiateSignCommand command) {
        OtpChallenge challenge = idempotencyStore.resultOf(command.actorId(), OPERATION, command.idempotencyKey())
                .map(UUID::fromString)
                .flatMap(challenges::findById)
                .orElseThrow(() -> new IllegalStateException("Idempotency record without challenge for "
                        + command.idempotencyKey()));
        if (!challenge.isUsable(clock.instant(), policy.maxAttempts())) {
            throw new ContractConflictException(CHALLENGE_NOT_ACTIVE,
                    "The code for this request is no longer valid; request a new one with a new Idempotency-Key.");
        }
        log.info("Replayed sign initiate {} for contract {}", command.idempotencyKey(), command.contractId());
        return new SignChallengeView(challenge.otpId(), challenge.expiresAt());
    }

    private record Issued(IssuedOtp otp, SignChallengeView view, boolean replayed) {
        static Issued fresh(IssuedOtp otp) {
            return new Issued(otp, new SignChallengeView(otp.challenge().otpId(), otp.challenge().expiresAt()), false);
        }

        static Issued replay(SignChallengeView view) {
            return new Issued(null, view, true);
        }

        @Override
        public String toString() {
            return "Issued[" + view + "]";
        }
    }
}
