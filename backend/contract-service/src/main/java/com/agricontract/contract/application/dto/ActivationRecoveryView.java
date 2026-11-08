package com.agricontract.contract.application.dto;

import com.agricontract.contract.domain.model.activation.ActivationRecovery;

import java.time.Instant;
import java.util.List;

/**
 * ContractResponse.activationRecovery (D42): present only after escrow.deposit_lock_failed. cancelRequestedBy lists
 * the parties with a standing cancel request.
 */
public record ActivationRecoveryView(Instant depositLockFailedAt, Instant oneSidedCancelAllowedFrom,
                                     List<String> cancelRequestedBy) {

    public static ActivationRecoveryView from(ActivationRecovery recovery, List<String> cancelRequestedBy) {
        return new ActivationRecoveryView(recovery.depositLockFailedAt(), recovery.oneSidedCancelAllowedFrom(),
                List.copyOf(cancelRequestedBy));
    }
}
