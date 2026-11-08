package com.agricontract.contract.application.usecase;

import com.agricontract.contract.application.dto.DepositLockedCommand;
import com.agricontract.contract.application.exception.ActivationRefusedException;
import com.agricontract.contract.application.port.ContractEventPublisher;
import com.agricontract.contract.domain.model.Contract;
import com.agricontract.contract.domain.model.vo.ContractId;
import com.agricontract.contract.domain.model.vo.ContractStatus;
import com.agricontract.contract.domain.repository.ContractRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

/**
 * escrow.deposit_locked → SIGNED becomes ACTIVE and contract.activated is written, once (#103, D40). Runs in the
 * consumer's transaction, under the contract row lock.
 * <ul>
 *   <li>Already ACTIVE: a duplicate signal, nothing to do.</li>
 *   <li>Any other state, an unknown contract, or deposit states that do not match the signed terms: refused to the
 *       DLQ. Money is locked for a contract that cannot become ACTIVE; an operator must see it.</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ActivateContractUseCase {
    static final String LOCKED = "DEPOSIT_LOCKED";

    private final ContractRepository contracts;
    private final ContractEventPublisher events;
    private final Clock clock;

    public void execute(DepositLockedCommand command) {
        Contract contract = contracts.findByIdForUpdate(new ContractId(command.contractId()))
                .orElseThrow(() -> new ActivationRefusedException("escrow.deposit_locked " + command.eventId()
                        + " for unknown contract " + command.contractId()));
        if (contract.getStatus() == ContractStatus.ACTIVE) {
            log.info("event=contract_activation_duplicate contractId={} eventId={}", command.contractId(),
                    command.eventId());
            return;
        }
        if (contract.getStatus() != ContractStatus.SIGNED) {
            throw new ActivationRefusedException("Deposits locked for contract " + command.contractId()
                    + " in status " + contract.getStatus() + "; it cannot become ACTIVE");
        }
        requireDepositsOfTheTerms(contract, command);

        contract.activate();
        contracts.save(contract);
        Instant activatedAt = Instant.now(clock).truncatedTo(ChronoUnit.MICROS);
        events.contractActivated(contract, activatedAt, command.correlationId(), command.eventId());
        log.info("event=contract_activated contractId={} causationId={}", command.contractId(), command.eventId());
    }

    /** Buyer deposit always locked; seller deposit locked exactly when the signed terms ask for one. */
    private static void requireDepositsOfTheTerms(Contract contract, DepositLockedCommand command) {
        boolean sellerDepositAgreed = contract.getCoffeeTerms() != null
                && contract.getCoffeeTerms().snapshot().sellerDepositAmount().value().signum() > 0;
        boolean sellerOk = sellerDepositAgreed ? LOCKED.equals(command.sellerDepositState())
                : command.sellerDepositState() == null;
        if (!LOCKED.equals(command.buyerDepositState()) || !sellerOk) {
            throw new ActivationRefusedException("Deposit states buyer=" + command.buyerDepositState() + " seller="
                    + command.sellerDepositState() + " do not match contract " + command.contractId()
                    + " (seller deposit agreed: " + sellerDepositAgreed + ")");
        }
    }
}
