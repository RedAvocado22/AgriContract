package com.agricontract.notification.application.usecase;

import com.agricontract.notification.application.dto.*;

public interface ProcessNotificationUseCase {
    void handleContractSigned(ContractSignedCommand command);

    void handleContractCancelled(ContractCancelledCommand command);

    void handleContractDelivered(ContractDeliveredCommand command);

    void handleContractDisputed(ContractDisputedCommand command);

    void handleEscrowLocked(EscrowLockedCommand command);

    void handleEscrowReleased(EscrowReleasedCommand command);

    void handleEscrowPenalized(EscrowPenalizedCommand command);
}
