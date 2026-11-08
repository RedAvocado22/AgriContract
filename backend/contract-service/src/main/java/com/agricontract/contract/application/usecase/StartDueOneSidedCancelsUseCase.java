package com.agricontract.contract.application.usecase;

import com.agricontract.contract.application.service.ActivationRefundStarter;
import com.agricontract.contract.domain.model.Contract;
import com.agricontract.contract.domain.model.vo.ContractId;
import com.agricontract.contract.domain.repository.ActivationCancelRequestRepository;
import com.agricontract.contract.domain.repository.ActivationRecoveryRepository;
import com.agricontract.contract.domain.repository.ContractRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * D42: a one-sided cancel request made before oneSidedCancelAllowedFrom is executed when that instant passes, without
 * a further call (golden-flow-api verifyActivationCancel). Each contract is re-checked under its own row lock, so a
 * withdrawal or a late activation in between wins.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StartDueOneSidedCancelsUseCase {
    private static final int BATCH = 50;

    private final ActivationCancelRequestRepository requests;
    private final ActivationRecoveryRepository recoveries;
    private final ContractRepository contracts;
    private final ActivationRefundStarter refundStarter;
    private final TransactionTemplate tx;
    private final Clock clock;

    /** Returns how many refunds started. */
    public int execute() {
        Instant now = Instant.now(clock).truncatedTo(ChronoUnit.MICROS);
        List<String> due = requests.findContractsWithOneSidedCancelDue(now, BATCH);
        int started = 0;
        for (String contractId : due) {
            Boolean result = tx.execute(status -> {
                Contract contract = contracts.findByIdForUpdate(new ContractId(contractId)).orElse(null);
                if (contract == null) {
                    return false;
                }
                return recoveries.findByContractId(contractId)
                        .map(recovery -> refundStarter.startIfDue(contract, recovery,
                                requests.findStanding(contractId), now))
                        .orElse(false);
            });
            if (Boolean.TRUE.equals(result)) {
                started++;
            }
        }
        return started;
    }
}
