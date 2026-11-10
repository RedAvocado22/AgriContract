package com.agricontract.contract.application.usecase;

import com.agricontract.contract.application.dto.WithdrawOfferCommand;
import com.agricontract.contract.application.exception.ContractNotFoundException;
import com.agricontract.contract.application.exception.IdempotencyKeyReusedException;
import com.agricontract.contract.application.port.ContractEventPublisher;
import com.agricontract.contract.application.port.IdempotencyStore;
import com.agricontract.contract.domain.exception.UnauthorizedContractAccessException;
import com.agricontract.contract.domain.model.Contract;
import com.agricontract.contract.domain.model.consent.SignerRole;
import com.agricontract.contract.domain.model.terms.CanonicalTerms;
import com.agricontract.contract.domain.model.vo.ContractId;
import com.agricontract.contract.domain.repository.ContractRepository;
import com.agricontract.contract.domain.repository.OtpChallengeRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

/**
 * WITHDRAW_OFFER (#233, golden-flow-api withdrawOffer). Either party ends an offer before the second signature;
 * authenticated party only, no checkbox or OTP: it moves no money and binds no one.
 *
 * <p>One transaction under the contract row lock, the same lock a sign verify and a sign initiate take, so a
 * withdraw and a second signature cannot both succeed: whichever runs second sees WITHDRAWN or SIGNED and gets 409.
 * In that transaction: WITHDRAWN, every still-usable OTP of the contract killed, and contract.withdrawn,
 * contract.terminated(WITHDRAW_OFFER) and the other party's email written to the outbox. Replaying the same
 * Idempotency-Key returns 204 without writing anything again; a new key on a withdrawn contract is 409.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WithdrawOfferUseCase {
    static final String OPERATION = "WITHDRAW_OFFER";

    private final ContractRepository contracts;
    private final IdempotencyStore idempotencyStore;
    private final OtpChallengeRepository challenges;
    private final ContractEventPublisher events;
    private final TransactionTemplate tx;
    private final Clock clock;

    public void execute(WithdrawOfferCommand command) {
        tx.executeWithoutResult(status -> {
            Contract contract = contracts.findByIdForUpdate(new ContractId(command.contractId()))
                    .orElseThrow(() -> new ContractNotFoundException(command.contractId()));
            if (contract.roleOf(command.actorId()).isEmpty()) {
                throw new UnauthorizedContractAccessException("This user is not a party of this contract.");
            }
            IdempotencyStore.Outcome outcome = idempotencyStore.claim(command.actorId(), OPERATION,
                    command.idempotencyKey(), CanonicalTerms.sha256(command.contractId()));
            if (outcome == IdempotencyStore.Outcome.CONFLICT) {
                throw new IdempotencyKeyReusedException();
            }
            if (outcome == IdempotencyStore.Outcome.REPLAY) {
                return;
            }
            Instant now = Instant.now(clock).truncatedTo(ChronoUnit.MICROS);
            SignerRole requestedBy = contract.withdraw(command.actorId());
            contracts.save(contract);
            int killed = challenges.expireAllUsable(command.contractId(), now);
            events.contractWithdrawn(contract, now);
            events.offerWithdrawnTerminated(contract, requestedBy, now);
            events.offerWithdrawnNoticeRequested(contract, requestedBy, now);
            log.info("event=offer_withdrawn contractId={} party={} otpKilled={}", command.contractId(), requestedBy,
                    killed);
        });
    }
}
