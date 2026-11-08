package com.agricontract.contract.application.usecase;

import com.agricontract.contract.application.dto.ActivationCancelCommands;
import com.agricontract.contract.application.dto.IssuedOtp;
import com.agricontract.contract.application.dto.OtpEmail;
import com.agricontract.contract.application.dto.SignChallengeView;
import com.agricontract.contract.application.dto.UserInfo;
import com.agricontract.contract.application.exception.ContractNotFoundException;
import com.agricontract.contract.application.exception.IdempotencyKeyReusedException;
import com.agricontract.contract.application.exception.OtpEmailNotSentException;
import com.agricontract.contract.application.port.ContractEventPublisher;
import com.agricontract.contract.application.port.IdempotencyStore;
import com.agricontract.contract.application.port.OtpCodes;
import com.agricontract.contract.application.port.OtpEmailPort;
import com.agricontract.contract.application.port.UserPort;
import com.agricontract.contract.application.service.ActivationRefundStarter;
import com.agricontract.contract.application.service.OtpChallengeIssuer;
import com.agricontract.contract.application.service.SignerEligibility;
import com.agricontract.contract.application.service.SigningConsentRecorder;
import com.agricontract.contract.domain.exception.ContractConflictException;
import com.agricontract.contract.domain.exception.UnauthorizedContractAccessException;
import com.agricontract.contract.domain.model.Contract;
import com.agricontract.contract.domain.model.activation.ActivationCancelRequest;
import com.agricontract.contract.domain.model.activation.ActivationRecovery;
import com.agricontract.contract.domain.model.consent.ConsentActionType;
import com.agricontract.contract.domain.model.consent.ConsentEvidence;
import com.agricontract.contract.domain.model.consent.SignerRole;
import com.agricontract.contract.domain.model.signature.OtpChallenge;
import com.agricontract.contract.domain.model.signature.OtpPolicy;
import com.agricontract.contract.domain.model.terms.CanonicalTerms;
import com.agricontract.contract.domain.model.vo.ContractId;
import com.agricontract.contract.domain.model.vo.ContractStatus;
import com.agricontract.contract.domain.repository.ActivationCancelRequestRepository;
import com.agricontract.contract.domain.repository.ActivationRecoveryRepository;
import com.agricontract.contract.domain.repository.ContractRepository;
import com.agricontract.contract.domain.repository.OtpChallengeRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

/**
 * The parties' activation cancel requests (D42, golden-flow-api activation/cancel/*). Same high-risk tier as signing:
 * checkbox CANCEL_ACTIVATION bound to the signed terms, a fresh login (auth_time within 300 s) and an emailed OTP
 * bound to the CANCEL_ACTIVATION action. No user eligibility check beyond being a party: a locked or expired account
 * must still be able to get its money back. OPERATOR has no part in this path.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ActivationCancelUseCases {
    static final String INITIATE = "INITIATE_ACTIVATION_CANCEL";
    static final String VERIFY = "VERIFY_ACTIVATION_CANCEL";
    static final String WITHDRAW = "WITHDRAW_ACTIVATION_CANCEL";

    private final SignerEligibility eligibility;
    private final UserPort users;
    private final IdempotencyStore idempotencyStore;
    private final ContractRepository contracts;
    private final ActivationRecoveryRepository recoveries;
    private final ActivationCancelRequestRepository requests;
    private final SigningConsentRecorder consentRecorder;
    private final OtpChallengeIssuer issuer;
    private final OtpChallengeRepository challenges;
    private final OtpCodes otpCodes;
    private final OtpPolicy policy;
    private final OtpEmailPort otpEmailPort;
    private final ActivationRefundStarter refundStarter;
    private final ContractEventPublisher events;
    private final TransactionTemplate tx;
    private final Clock clock;

    /** Records the consent and the OTP challenge, then emails the code (purpose CANCEL_ACTIVATION). */
    public SignChallengeView initiate(ActivationCancelCommands.Initiate command) {
        eligibility.requireFreshLogin(command.authTime());
        UserInfo party = users.findUser(command.actorId()).orElseThrow(() ->
                new UnauthorizedContractAccessException("No profile was found for this account."));

        IssuedOtp issued = tx.execute(status -> {
            String requestHash = CanonicalTerms.sha256(String.join("\n", command.contractId(),
                    command.requesterRole().name(), command.consentStatementVersion(), command.presentedContentHash()));
            IdempotencyStore.Outcome outcome = idempotencyStore.claim(command.actorId(), INITIATE,
                    command.idempotencyKey(), requestHash);
            if (outcome == IdempotencyStore.Outcome.CONFLICT) {
                throw new IdempotencyKeyReusedException();
            }
            if (outcome == IdempotencyStore.Outcome.REPLAY) {
                return null;
            }
            Contract contract = contracts.findByIdForUpdate(new ContractId(command.contractId()))
                    .orElseThrow(() -> new ContractNotFoundException(command.contractId()));
            requireFailedLock(command.contractId());
            if (requests.findStanding(command.contractId()).stream()
                    .anyMatch(r -> r.partyRole() == command.requesterRole())) {
                throw new ContractConflictException(ContractConflictException.CANCEL_ALREADY_REQUESTED,
                        "You already asked to cancel this contract; withdraw it first to ask again.");
            }
            ConsentEvidence consent = consentRecorder.recordCancelActivation(contract, command.actorId(),
                    command.requesterRole(), command.consentStatementVersion(), command.presentedContentHash(),
                    command.idempotencyKey().toString());
            IssuedOtp otp = issuer.issue(contract, consent, command.authTime());
            idempotencyStore.recordResult(command.actorId(), INITIATE, command.idempotencyKey(),
                    otp.challenge().otpId().toString());
            return otp;
        });
        if (issued == null) {
            return replayedChallenge(command);
        }

        OtpChallenge challenge = issued.challenge();
        String email = party.email();
        if (email == null || email.isBlank()) {
            issuer.expire(challenge.otpId());
            throw new UnauthorizedContractAccessException("Your profile has no email to send the code to.");
        }
        try {
            otpEmailPort.send(new OtpEmail(challenge.otpId().toString(), command.actorId(), email,
                    challenge.contractId(), issued.code(), challenge.signedContentHash(), challenge.expiresAt(),
                    OtpEmail.CANCEL_ACTIVATION));
        } catch (OtpEmailNotSentException notSent) {
            issuer.expire(challenge.otpId());
            throw notSent;
        }
        log.info("event=activation_cancel_code_sent contractId={} party={} otpId={}", challenge.contractId(),
                challenge.signerRole(), challenge.otpId());
        return new SignChallengeView(challenge.otpId(), challenge.expiresAt());
    }

    /**
     * Checks the code and records the party's request; in the same transaction the refund starts when both parties
     * stand, or one does after the window. A wrong code is counted and committed before the refusal, as in signing.
     */
    public void verify(ActivationCancelCommands.Verify command) {
        eligibility.requireFreshLogin(command.authTime());
        RuntimeException refusal = tx.execute(status -> verifyInTx(command));
        if (refusal != null) {
            throw refusal;
        }
    }

    private RuntimeException verifyInTx(ActivationCancelCommands.Verify command) {
        Contract contract = contracts.findByIdForUpdate(new ContractId(command.contractId()))
                .orElseThrow(() -> new ContractNotFoundException(command.contractId()));
        SignerRole role = contract.roleOf(command.actorId()).orElseThrow(() ->
                new UnauthorizedContractAccessException("This user is not a party of this contract."));
        String requestHash = CanonicalTerms.sha256(command.contractId() + "\n" + command.otpId());
        var previous = idempotencyStore.lookup(command.actorId(), VERIFY, command.idempotencyKey(), requestHash);
        if (previous.isPresent()) {
            if (previous.get() == IdempotencyStore.Outcome.CONFLICT) {
                throw new IdempotencyKeyReusedException();
            }
            return null;
        }
        OtpChallenge challenge = challenges.findByIdForUpdate(command.otpId())
                .filter(c -> c.actionType() == ConsentActionType.CANCEL_ACTIVATION)
                .orElseThrow(() -> new ContractConflictException(VerifyOtpAndSignUseCase.OTP_NOT_FOUND,
                        "No such cancel code."));
        if (!challenge.userId().equals(command.actorId()) || challenge.signerRole() != role) {
            throw new UnauthorizedContractAccessException("This code belongs to another user.");
        }
        if (!challenge.contractId().equals(command.contractId())) {
            throw new ContractConflictException(VerifyOtpAndSignUseCase.OTP_FOR_OTHER_CONTRACT,
                    "This code is for another contract.");
        }
        Instant now = now();
        challenge.ensureUsable(now, policy.maxAttempts());
        if (contract.getStatus() != ContractStatus.SIGNED) {
            throw new ContractConflictException(ContractConflictException.NOT_CANCELLABLE,
                    "This contract is " + contract.getStatus() + "; it can no longer be cancelled.");
        }
        ActivationRecovery recovery = requireFailedLock(command.contractId());
        if (!otpCodes.matches(command.otpCode(), challenge.otpHash())) {
            int left = policy.maxAttempts() - challenges.recordFailedAttempt(challenge.otpId());
            return left > 0
                    ? new ContractConflictException(VerifyOtpAndSignUseCase.OTP_INVALID,
                            "Wrong code; " + left + " attempt" + (left == 1 ? "" : "s") + " left.")
                    : new ContractConflictException(OtpChallenge.OTP_LOCKED,
                            "Wrong code and no attempts left; request a new code.");
        }
        List<ActivationCancelRequest> standing = requests.findStanding(command.contractId());
        if (standing.stream().anyMatch(r -> r.partyRole() == role)) {
            throw new ContractConflictException(ContractConflictException.CANCEL_ALREADY_REQUESTED,
                    "You already asked to cancel this contract.");
        }
        challenges.markVerified(challenge.otpId(), now);
        ActivationCancelRequest request = ActivationCancelRequest.stand(command.contractId(), role,
                command.actorId(), challenge.otpId(), now);
        requests.insert(request);
        if (idempotencyStore.claim(command.actorId(), VERIFY, command.idempotencyKey(), requestHash)
                != IdempotencyStore.Outcome.FIRST) {
            throw new IllegalStateException("Idempotency-Key " + command.idempotencyKey() + " claimed concurrently");
        }
        idempotencyStore.recordResult(command.actorId(), VERIFY, command.idempotencyKey(),
                request.requestId().toString());

        boolean started = refundStarter.startIfDue(contract, recovery, requests.findStanding(command.contractId()),
                now);
        if (!started) {
            events.activationCancelRequested(contract, role, recovery.oneSidedCancelAllowedFrom(), now);
        }
        log.info("event=activation_cancel_requested contractId={} party={} refundStarted={}", command.contractId(),
                role, started);
        return null;
    }

    /** Withdraws the caller's standing request while the contract is still SIGNED; nothing to withdraw → 409. */
    public void withdraw(ActivationCancelCommands.Withdraw command) {
        tx.executeWithoutResult(status -> {
            Contract contract = contracts.findByIdForUpdate(new ContractId(command.contractId()))
                    .orElseThrow(() -> new ContractNotFoundException(command.contractId()));
            SignerRole role = contract.roleOf(command.actorId()).orElseThrow(() ->
                    new UnauthorizedContractAccessException("This user is not a party of this contract."));
            IdempotencyStore.Outcome outcome = idempotencyStore.claim(command.actorId(), WITHDRAW,
                    command.idempotencyKey(), CanonicalTerms.sha256(command.contractId()));
            if (outcome == IdempotencyStore.Outcome.CONFLICT) {
                throw new IdempotencyKeyReusedException();
            }
            if (outcome == IdempotencyStore.Outcome.REPLAY) {
                return;
            }
            if (contract.getStatus() != ContractStatus.SIGNED) {
                throw new ContractConflictException(ContractConflictException.NOT_CANCELLABLE,
                        "The refund already started (" + contract.getStatus() + "); nothing can be withdrawn.");
            }
            ActivationCancelRequest mine = requests.findStanding(command.contractId()).stream()
                    .filter(r -> r.partyRole() == role)
                    .findFirst()
                    .orElseThrow(() -> new ContractConflictException(ContractConflictException.NO_CANCEL_REQUEST,
                            "You have no standing cancel request on this contract."));
            requests.withdraw(mine.requestId(), now());
            log.info("event=activation_cancel_withdrawn contractId={} party={}", command.contractId(), role);
        });
    }

    private ActivationRecovery requireFailedLock(String contractId) {
        return recoveries.findByContractId(contractId).orElseThrow(() ->
                new ContractConflictException(ContractConflictException.NOT_CANCELLABLE,
                        "The deposit lock of this contract has not failed; it cannot be cancelled this way."));
    }

    private SignChallengeView replayedChallenge(ActivationCancelCommands.Initiate command) {
        OtpChallenge challenge = idempotencyStore.resultOf(command.actorId(), INITIATE, command.idempotencyKey())
                .map(UUID::fromString)
                .flatMap(challenges::findById)
                .orElseThrow(() -> new IllegalStateException("Idempotency record without challenge"));
        if (!challenge.isUsable(clock.instant(), policy.maxAttempts())) {
            throw new ContractConflictException(InitiateSignUseCase.CHALLENGE_NOT_ACTIVE,
                    "The code for this request is no longer valid; request a new one with a new Idempotency-Key.");
        }
        return new SignChallengeView(challenge.otpId(), challenge.expiresAt());
    }

    private Instant now() {
        return Instant.now(clock).truncatedTo(ChronoUnit.MICROS);
    }
}
