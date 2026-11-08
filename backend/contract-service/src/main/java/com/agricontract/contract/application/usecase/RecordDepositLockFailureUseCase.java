package com.agricontract.contract.application.usecase;

import com.agricontract.calendar.VietnamBusinessDays;
import com.agricontract.contract.application.dto.DepositLockFailedCommand;
import com.agricontract.contract.application.exception.ActivationRefusedException;
import com.agricontract.contract.application.port.ContractEventPublisher;
import com.agricontract.contract.domain.model.Contract;
import com.agricontract.contract.domain.model.activation.ActivationRecovery;
import com.agricontract.contract.domain.model.vo.ContractId;
import com.agricontract.contract.domain.model.vo.ContractStatus;
import com.agricontract.contract.domain.repository.ActivationRecoveryRepository;
import com.agricontract.contract.domain.repository.ContractRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

/**
 * escrow.deposit_lock_failed (#234, D42). The contract stays SIGNED: the signatures happened, the platform failed to
 * lock the money. Under the contract row lock:
 * <ul>
 *   <li>the first report records depositLockFailedAt and fixes oneSidedCancelAllowedFrom = end of the Nth business
 *       day after it (shared Vietnam calendar) + 1 ms; a later report (a retry cycle failed again) keeps both;</li>
 *   <li>notification.contract_activation_failed_requested goes to both parties in the same transaction.</li>
 * </ul>
 * ACTIVE: a late lock success already activated the contract, the report is stale, nothing to do. Any other status
 * is refused (DLQ): money may be locked for a contract that can no longer use it, which must not pass silently.
 */
@Slf4j
@Service
public class RecordDepositLockFailureUseCase {

    private final ContractRepository contracts;
    private final ActivationRecoveryRepository recoveries;
    private final ContractEventPublisher events;
    private final VietnamBusinessDays calendar;
    private final Clock clock;
    private final int windowBusinessDays;

    public RecordDepositLockFailureUseCase(ContractRepository contracts, ActivationRecoveryRepository recoveries,
                                           ContractEventPublisher events, VietnamBusinessDays calendar, Clock clock,
                                           @Value("${contract.activation.one-sided-cancel-window-business-days}")
                                           int windowBusinessDays) {
        this.contracts = contracts;
        this.recoveries = recoveries;
        this.events = events;
        this.calendar = calendar;
        this.clock = clock;
        this.windowBusinessDays = windowBusinessDays;
    }

    public void execute(DepositLockFailedCommand command) {
        Contract contract = contracts.findByIdForUpdate(new ContractId(command.contractId()))
                .orElseThrow(() -> new ActivationRefusedException("escrow.deposit_lock_failed " + command.eventId()
                        + " for unknown contract " + command.contractId()));
        if (contract.getStatus() == ContractStatus.ACTIVE) {
            log.info("event=deposit_lock_failure_stale contractId={} eventId={} (already ACTIVE)",
                    command.contractId(), command.eventId());
            return;
        }
        if (contract.getStatus() != ContractStatus.SIGNED || contract.getCoffeeTerms() == null) {
            throw new ActivationRefusedException("Deposit lock failure for contract " + command.contractId()
                    + " in status " + contract.getStatus() + "; only a SIGNED coffee contract waits for its deposits");
        }

        Instant now = Instant.now(clock).truncatedTo(ChronoUnit.MICROS);
        ActivationRecovery.FailedLeg failedLeg = ActivationRecovery.FailedLeg.valueOf(command.failedLeg());
        ActivationRecovery recovery = recoveries.findByContractId(command.contractId())
                .map(existing -> {
                    existing.anotherFailure(now, failedLeg, command.reason());
                    return existing;
                })
                .orElseGet(() -> ActivationRecovery.firstFailure(command.contractId(), now,
                        calendar.deadline(now, windowBusinessDays).plusMillis(1), failedLeg, command.reason()));
        recoveries.save(recovery);

        events.contractActivationFailedRequested(contract, recovery, command.buyerDepositState(),
                command.sellerDepositState(), command.correlationId(), command.eventId());
        log.warn("event=contract_activation_failed contractId={} failedLeg={} reports={} oneSidedCancelAllowedFrom={}",
                command.contractId(), failedLeg, recovery.failureReports(), recovery.oneSidedCancelAllowedFrom());
    }
}
