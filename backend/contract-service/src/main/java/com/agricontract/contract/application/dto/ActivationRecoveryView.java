package com.agricontract.contract.application.dto;

import com.agricontract.contract.domain.model.activation.ActivationRecovery;

import java.time.Instant;
import java.util.List;

/**
 * ContractResponse.activationRecovery (D42): present only after escrow.deposit_lock_failed. cancelRequestedBy lists
 * the parties with a standing cancel request (empty until the parties' cancel requests ship, #234).
 */
public record ActivationRecoveryView(Instant depositLockFailedAt, Instant oneSidedCancelAllowedFrom,
                                     List<String> cancelRequestedBy) {

    public static ActivationRecoveryView from(ActivationRecovery recovery) {
        return new ActivationRecoveryView(recovery.depositLockFailedAt(), recovery.oneSidedCancelAllowedFrom(),
                List.of());
    }
}
