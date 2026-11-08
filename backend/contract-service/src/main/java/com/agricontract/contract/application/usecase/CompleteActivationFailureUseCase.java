package com.agricontract.contract.application.usecase;

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
 * escrow.activation_refund_completed (D41/D42): every deposit is back with its owner and the contract's lock is 0, so
 * ACTIVATION_REFUND_PENDING → ACTIVATION_FAILED (terminal) and contract.activation_failed, in one transaction.
 * Already ACTIVATION_FAILED: duplicate, nothing to do. Any other status: refused (DLQ), it must not pass silently.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CompleteActivationFailureUseCase {
    private final ContractRepository contracts;
    private final ContractEventPublisher events;
    private final Clock clock;

    public void execute(String contractId, String eventId, String correlationId) {
        Contract contract = contracts.findByIdForUpdate(new ContractId(contractId))
                .orElseThrow(() -> new ActivationRefusedException("escrow.activation_refund_completed " + eventId
                        + " for unknown contract " + contractId));
        if (contract.getStatus() == ContractStatus.ACTIVATION_FAILED) {
            log.info("event=activation_failure_duplicate contractId={} eventId={}", contractId, eventId);
            return;
        }
        if (contract.getStatus() != ContractStatus.ACTIVATION_REFUND_PENDING) {
            throw new ActivationRefusedException("Refund completed for contract " + contractId + " in status "
                    + contract.getStatus() + "; only ACTIVATION_REFUND_PENDING can end as ACTIVATION_FAILED");
        }
        contract.failActivation();
        contracts.save(contract);
        Instant now = Instant.now(clock).truncatedTo(ChronoUnit.MICROS);
        events.contractActivationFailed(contract, now, correlationId, eventId);
        log.info("event=contract_activation_failed_final contractId={}", contractId);
    }
}
