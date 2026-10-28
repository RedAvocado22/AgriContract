package com.agricontract.escrow.application.usecase;

import com.agricontract.escrow.application.dto.ContractSignedCommand;
import com.agricontract.escrow.application.port.LockCommandPublisher;
import com.agricontract.escrow.domain.repository.DepositLegRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Clock;

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
        // TODO(#101): create the BUYER leg, and the SELLER leg when sellerDepositAmount > 0;
        //  insert each leg, then lockCommands.lockRequested(leg, command.correlationId(), command.eventId()).
        throw new UnsupportedOperationException("TODO #101");
    }
}
