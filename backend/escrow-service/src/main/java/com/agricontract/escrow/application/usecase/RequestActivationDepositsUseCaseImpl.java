package com.agricontract.escrow.application.usecase;

import com.agricontract.escrow.application.dto.ContractSignedCommand;
import com.agricontract.escrow.application.port.LockCommandPublisher;
import com.agricontract.escrow.domain.exception.InvalidDepositLegException;
import com.agricontract.escrow.domain.model.DepositLeg;
import com.agricontract.escrow.domain.model.DepositLegParty;
import com.agricontract.escrow.domain.model.Money;
import com.agricontract.escrow.domain.repository.DepositLegRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * contract.signed → one deposit leg per required deposit → one bank.lock_requested per leg
 * (milestone-escrow design §3.1). BUYER always; SELLER only when sellerDepositAmount > 0.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RequestActivationDepositsUseCaseImpl implements RequestActivationDepositsUseCase {

    private final DepositLegRepository legs;
    private final LockCommandPublisher lockCommands;
    private final Clock clock;

    @Override
    public void execute(ContractSignedCommand command) {
        // Build every leg first, so a bad amount fails before anything is stored or sent.
        List<DepositLeg> requested = requiredLegs(command, clock.instant());

        for (DepositLeg leg : requested) {
            legs.insert(leg);
            lockCommands.lockRequested(leg, command.correlationId(), command.eventId());
        }

        log.info("event=activation_deposits_requested contractId={} legs={} sourceEventId={} correlationId={}",
                command.contractId(), requested.size(), command.eventId(), command.correlationId());
    }

    private static List<DepositLeg> requiredLegs(ContractSignedCommand command, Instant now) {
        List<DepositLeg> required = new ArrayList<>();
        required.add(DepositLeg.request(command.contractId(), DepositLegParty.BUYER, command.buyerId(),
                new Money(command.buyerDepositAmount()), now));

        BigDecimal sellerDeposit = command.sellerDepositAmount();
        if (sellerDeposit == null || sellerDeposit.signum() < 0) {
            // 0.00 means "no seller deposit"; null or negative is a producer bug and must not pass as "no deposit".
            throw new InvalidDepositLegException("sellerDepositAmount must be 0.00 or positive: " + sellerDeposit);
        }
        if (sellerDeposit.signum() > 0) {
            required.add(DepositLeg.request(command.contractId(), DepositLegParty.SELLER, command.sellerId(),
                    new Money(sellerDeposit), now));
        }
        return required;
    }
}
