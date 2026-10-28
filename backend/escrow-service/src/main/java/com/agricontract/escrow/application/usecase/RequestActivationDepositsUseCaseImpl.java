package com.agricontract.escrow.application.usecase;

import com.agricontract.escrow.application.dto.ContractSignedCommand;
import com.agricontract.escrow.application.port.LockCommandPublisher;
import com.agricontract.escrow.domain.model.DepositLeg;
import com.agricontract.escrow.domain.model.DepositLegParty;
import com.agricontract.escrow.domain.model.Money;
import com.agricontract.escrow.domain.repository.DepositLegRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;

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
        Instant now = clock.instant();
        DepositLeg buyerLeg = DepositLeg.request(
                command.contractId(),
                DepositLegParty.BUYER,
                command.buyerId(),
                new Money(command.buyerDepositAmount()),
                now
        );

        legs.insert(buyerLeg);
        lockCommands.lockRequested(buyerLeg, command.correlationId(), command.eventId());

        int requested = 1;
        if (command.sellerDepositAmount().signum() > 0) {
            DepositLeg sellerLeg = DepositLeg.request(
                    command.contractId(),
                    DepositLegParty.SELLER,
                    command.sellerId(),
                    new Money(command.sellerDepositAmount()),
                    now
            );

            legs.insert(sellerLeg);
            lockCommands.lockRequested(sellerLeg, command.correlationId(), command.eventId());
            requested++;
        }

        log.info("event=activation_deposits_requested contractId={} legs={} sourceEventId={} correlationId={}",
                command.contractId(), requested, command.eventId(), command.correlationId());
    }
}
