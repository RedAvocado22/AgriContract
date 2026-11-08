package com.agricontract.contract.application.usecase;

import com.agricontract.contract.application.dto.VerifySignCommand;
import com.agricontract.contract.application.exception.ContractNotFoundException;
import com.agricontract.contract.application.exception.IdempotencyKeyReusedException;
import com.agricontract.contract.application.port.ContractEventPublisher;
import com.agricontract.contract.application.port.IdempotencyStore;
import com.agricontract.contract.application.port.OtpCodes;
import com.agricontract.contract.application.service.SignerEligibility;
import com.agricontract.contract.domain.exception.ContractConflictException;
import com.agricontract.contract.domain.exception.UnauthorizedContractAccessException;
import com.agricontract.contract.domain.model.Contract;
import com.agricontract.contract.domain.model.consent.SignerRole;
import com.agricontract.contract.domain.model.signature.OtpChallenge;
import com.agricontract.contract.domain.model.signature.OtpPolicy;
import com.agricontract.contract.domain.model.signature.Signature;
import com.agricontract.contract.domain.model.terms.CanonicalTerms;
import com.agricontract.contract.domain.model.vo.ContractId;
import com.agricontract.contract.domain.model.vo.ContractStatus;
import com.agricontract.contract.domain.repository.ContractRepository;
import com.agricontract.contract.domain.repository.OtpChallengeRepository;
import com.agricontract.contract.domain.repository.SignatureRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

/**
 * VerifyOtpAndSign (signature design §6, #97, decisions D37). Order matters:
 * <ol>
 *   <li>Outside any transaction: fresh login, then both parties checked with user-service (fail closed). A party
 *       that is locked, unverified or past its authorization stops the signature, also the one who signed first.</li>
 *   <li>One transaction under the contract row lock, so the two parties' verifies run one after the other and the
 *       second always sees the first signature: check the challenge, then either count a wrong code or record the
 *       signature. The second one makes the contract SIGNED and writes contract.signed to the outbox in the same
 *       transaction (#98), so the event exists exactly when SIGNED commits.</li>
 * </ol>
 * Some refusals must be committed (a wrong code is counted, a challenge for changed terms is killed). The
 * transaction returns them instead of throwing, and they are thrown after commit. The Idempotency-Key is claimed
 * only by a successful verify, so a retry after a wrong code is a new attempt, and a retry after success is 204.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class VerifyOtpAndSignUseCase {
    static final String OPERATION = "VERIFY_SIGN";
    public static final String OTP_NOT_FOUND = "OTP_CHALLENGE_NOT_FOUND";
    public static final String OTP_INVALID = "OTP_INVALID";
    public static final String OTP_FOR_OTHER_CONTRACT = "OTP_CONTRACT_MISMATCH";

    private final SignerEligibility eligibility;
    private final ContractRepository contractRepository;
    private final OtpChallengeRepository challenges;
    private final SignatureRepository signatures;
    private final IdempotencyStore idempotencyStore;
    private final ContractEventPublisher events;
    private final OtpCodes otpCodes;
    private final OtpPolicy policy;
    private final TransactionTemplate tx;
    private final Clock clock;

    public void execute(VerifySignCommand command) {
        eligibility.requireFreshLogin(command.authTime());
        Contract contract = contractRepository.findById(new ContractId(command.contractId()))
                .orElseThrow(() -> new ContractNotFoundException(command.contractId()));
        SignerRole role = contract.roleOf(command.actorId()).orElseThrow(() ->
                new UnauthorizedContractAccessException("This user is not a party of this contract."));
        eligibility.requireEligible(command.actorId());
        eligibility.requireCounterpartyEligible(role == SignerRole.BUYER ? contract.getSellerId()
                : contract.getBuyerId());

        RuntimeException refusal = tx.execute(status -> verifyAndSign(command, role));
        if (refusal != null) {
            throw refusal;
        }
    }

    /** Returns null when signed (or replayed), or the refusal to throw once its side effects are committed. */
    private RuntimeException verifyAndSign(VerifySignCommand command, SignerRole role) {
        Contract contract = contractRepository.findByIdForUpdate(new ContractId(command.contractId()))
                .orElseThrow(() -> new ContractNotFoundException(command.contractId()));
        String requestHash = CanonicalTerms.sha256(command.contractId() + "\n" + command.otpId());
        var previous = idempotencyStore.lookup(command.actorId(), OPERATION, command.idempotencyKey(), requestHash);
        if (previous.isPresent()) {
            if (previous.get() == IdempotencyStore.Outcome.CONFLICT) {
                throw new IdempotencyKeyReusedException();
            }
            log.info("Replayed sign verify {} for contract {}", command.idempotencyKey(), command.contractId());
            return null;
        }

        OtpChallenge challenge = challenges.findByIdForUpdate(command.otpId())
                .orElseThrow(() -> new ContractConflictException(OTP_NOT_FOUND, "No such signing code."));
        if (!challenge.userId().equals(command.actorId())) {
            throw new UnauthorizedContractAccessException("This signing code belongs to another user.");
        }
        if (!challenge.contractId().equals(command.contractId())) {
            throw new ContractConflictException(OTP_FOR_OTHER_CONTRACT, "This signing code is for another contract.");
        }
        if (challenge.signerRole() != role) {
            throw new UnauthorizedContractAccessException("This signing code was issued for the other party.");
        }
        Instant now = Instant.now(clock).truncatedTo(ChronoUnit.MICROS);
        challenge.ensureUsable(now, policy.maxAttempts());

        // A Phase 1 contract (no coffee terms) has no hash to compare; Contract.sign refuses it below.
        if (contract.getCoffeeTerms() != null
                && !challenge.signedContentHash().equals(contract.getCoffeeTerms().signedContentHash())) {
            challenges.expire(challenge.otpId(), now);
            log.warn("Challenge {} killed: terms of contract {} changed after it was sent", challenge.otpId(),
                    command.contractId());
            return new ContractConflictException(ContractConflictException.TERMS_CHANGED_SINCE_CODE,
                    "The terms changed after this code was sent; review them and start signing again.");
        }
        if (!otpCodes.matches(command.otpCode(), challenge.otpHash())) {
            int left = policy.maxAttempts() - challenges.recordFailedAttempt(challenge.otpId());
            log.warn("Wrong code for challenge {} on contract {}, {} attempts left", challenge.otpId(),
                    command.contractId(), left);
            return left > 0
                    ? new ContractConflictException(OTP_INVALID, "Wrong code; " + left + " attempt"
                            + (left == 1 ? "" : "s") + " left.")
                    : new ContractConflictException(OtpChallenge.OTP_LOCKED,
                            "Wrong code and no attempts left; request a new code.");
        }

        challenges.markVerified(challenge.otpId(), now);
        Signature signature = contract.sign(command.actorId(), challenge, command.authTime(), now,
                command.ipAddress(), UUID.randomUUID());
        signatures.insert(signature);
        contractRepository.save(contract);
        if (contract.getStatus() == ContractStatus.SIGNED) {
            // Only the verify that adds the second signature gets here, once per contract (row lock + status
            // check in Contract.sign); a replay of the same key returned above without reaching this line.
            events.contractSigned(contract, now);
        }
        if (idempotencyStore.claim(command.actorId(), OPERATION, command.idempotencyKey(), requestHash)
                != IdempotencyStore.Outcome.FIRST) {
            throw new IllegalStateException("Idempotency-Key " + command.idempotencyKey() + " claimed concurrently");
        }
        idempotencyStore.recordResult(command.actorId(), OPERATION, command.idempotencyKey(),
                signature.signatureId().toString());
        log.info("Contract {} signed by {} as {} (signature {}), status {}", command.contractId(),
                command.actorId(), role, signature.signatureId(), contract.getStatus());
        return null;
    }
}
