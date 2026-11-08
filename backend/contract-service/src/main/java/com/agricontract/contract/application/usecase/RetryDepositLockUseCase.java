package com.agricontract.contract.application.usecase;

import com.agricontract.contract.application.dto.RetryDepositLockCommand;
import com.agricontract.contract.application.exception.ContractNotFoundException;
import com.agricontract.contract.application.exception.IdempotencyKeyReusedException;
import com.agricontract.contract.application.port.ContractEventPublisher;
import com.agricontract.contract.application.port.IdempotencyStore;
import com.agricontract.contract.domain.exception.ContractConflictException;
import com.agricontract.contract.domain.model.Contract;
import com.agricontract.contract.domain.model.terms.CanonicalTerms;
import com.agricontract.contract.domain.model.vo.ContractId;
import com.agricontract.contract.domain.model.vo.ContractStatus;
import com.agricontract.contract.domain.repository.ActivationRecoveryRepository;
import com.agricontract.contract.domain.repository.ContractRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

/**
 * OPERATOR RetryDepositLock (golden-flow-api retryDepositLock, D42): once the cause is fixed, ask escrow to lock the
 * failed legs again (contract.deposit_lock_retry_requested). Only for a SIGNED contract whose deposit lock failed.
 * The one-sided cancel window is not moved. A replay of the same Idempotency-Key does nothing.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class RetryDepositLockUseCase {
    static final String OPERATION = "RETRY_DEPOSIT_LOCK";

    private final IdempotencyStore idempotencyStore;
    private final ContractRepository contracts;
    private final ActivationRecoveryRepository recoveries;
    private final ContractEventPublisher events;
    private final Clock clock;

    public void execute(RetryDepositLockCommand command) {
        IdempotencyStore.Outcome outcome = idempotencyStore.claim(command.operatorUserId(), OPERATION,
                command.idempotencyKey(), CanonicalTerms.sha256(command.contractId()));
        if (outcome == IdempotencyStore.Outcome.CONFLICT) {
            throw new IdempotencyKeyReusedException();
        }
        Contract contract = contracts.findByIdForUpdate(new ContractId(command.contractId()))
                .orElseThrow(() -> new ContractNotFoundException(command.contractId()));
        if (outcome == IdempotencyStore.Outcome.REPLAY) {
            log.info("event=deposit_lock_retry_replayed contractId={} key={}", command.contractId(),
                    command.idempotencyKey());
            return;
        }
        if (contract.getStatus() != ContractStatus.SIGNED) {
            throw new ContractConflictException(ContractConflictException.ACTIVATION_NOT_FAILED,
                    "Only a SIGNED contract waiting for its deposits can be retried, not " + contract.getStatus() + ".");
        }
        if (recoveries.findByContractId(command.contractId()).isEmpty()) {
            throw new ContractConflictException(ContractConflictException.ACTIVATION_NOT_FAILED,
                    "The deposit lock of this contract has not failed; escrow is still trying.");
        }
        Instant now = Instant.now(clock).truncatedTo(ChronoUnit.MICROS);
        events.depositLockRetryRequested(contract, command.operatorUserId(), now);
        log.info("event=deposit_lock_retry_requested contractId={} operator={}", command.contractId(),
                command.operatorUserId());
    }
}
