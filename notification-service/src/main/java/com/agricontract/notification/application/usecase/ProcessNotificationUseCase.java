package com.agricontract.notification.application.usecase;

import com.agricontract.notification.infrastructure.messaging.dto.*;

public interface ProcessNotificationUseCase {
    void handleContractSigned(ContractSignedEvent event);

    void handleContractCancelled(ContractCancelledEvent event);

    void handleContractDelivered(ContractDeliveredEvent event);

    void handleContractDisputed(ContractDisputedEvent event);

    void handleEscrowLocked(EscrowLockedEvent event);

    void handleEscrowReleased(EscrowReleasedEvent event);

    void handleEscrowPenalized(EscrowPenalizedEvent event);
}
