package com.agricontract.contract.application.service;

import com.agricontract.contract.application.port.ContractEventPublisher;
import com.agricontract.contract.domain.model.Contract;
import com.agricontract.contract.domain.model.activation.ActivationCancelRequest;
import com.agricontract.contract.domain.model.activation.ActivationRecovery;
import com.agricontract.contract.domain.model.consent.SignerRole;
import com.agricontract.contract.domain.model.vo.ContractStatus;
import com.agricontract.contract.domain.repository.ContractRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/**
 * The D42 cancel rule, in one place (verify of a request, and the one-sided window job). With the contract row locked:
 * both parties' standing requests start the refund at once; one party's request does from oneSidedCancelAllowedFrom.
 * Starting = SIGNED → ACTIVATION_REFUND_PENDING + contract.activation_refund_requested in the same transaction.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ActivationRefundStarter {
    public static final String BOTH_PARTIES = "BOTH_PARTIES";
    public static final String ONE_PARTY_AFTER_WINDOW = "ONE_PARTY_AFTER_WINDOW";

    private final ContractRepository contracts;
    private final ContractEventPublisher events;

    /** True when the refund started now. */
    @Transactional(propagation = Propagation.MANDATORY)
    public boolean startIfDue(Contract contract, ActivationRecovery recovery, List<ActivationCancelRequest> standing,
                              Instant now) {
        if (contract.getStatus() != ContractStatus.SIGNED || standing.isEmpty()) {
            return false;
        }
        List<SignerRole> requestedBy = standing.stream().map(ActivationCancelRequest::partyRole).sorted().toList();
        String trigger;
        if (requestedBy.containsAll(List.of(SignerRole.BUYER, SignerRole.SELLER))) {
            trigger = BOTH_PARTIES;
        } else if (recovery.oneSidedCancelAllowed(now)) {
            trigger = ONE_PARTY_AFTER_WINDOW;
        } else {
            return false;
        }
        contract.startActivationRefund();
        contracts.save(contract);
        events.activationRefundRequested(contract, requestedBy, trigger, now);
        log.info("event=activation_refund_started contractId={} requestedBy={} trigger={}",
                contract.getContractId().value(), requestedBy, trigger);
        return true;
    }
}
