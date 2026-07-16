package com.agricontract.escrow.infrastructure.messaging;

import com.agricontract.escrow.application.dto.HoldEscrowForDisputeCommand;
import com.agricontract.escrow.application.exception.InvalidEventPayloadException;
import com.agricontract.escrow.application.usecase.HoldEscrowForDisputeUseCase;
import com.agricontract.escrow.application.usecase.LockBuyerPaymentUseCase;
import com.agricontract.escrow.application.usecase.PenalizeEscrowUseCase;
import com.agricontract.escrow.application.usecase.ReleaseEscrowUseCase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ContractEventConsumerTest {

    @Mock private LockBuyerPaymentUseCase lockBuyerPaymentUseCase;
    @Mock private ReleaseEscrowUseCase releaseEscrowUseCase;
    @Mock private PenalizeEscrowUseCase penalizeEscrowUseCase;
    @Mock private HoldEscrowForDisputeUseCase holdEscrowForDisputeUseCase;

    private ContractEventConsumer consumer;

    @BeforeEach
    void setUp() {
        consumer = new ContractEventConsumer(
                lockBuyerPaymentUseCase,
                releaseEscrowUseCase,
                penalizeEscrowUseCase,
                holdEscrowForDisputeUseCase);
    }

    @Test
    void onContractDisputed_delegatesHoldCommand() {
        consumer.onContractDisputed(Map.of("contractId", "contract-1"));

        ArgumentCaptor<HoldEscrowForDisputeCommand> captor =
                ArgumentCaptor.forClass(HoldEscrowForDisputeCommand.class);
        verify(holdEscrowForDisputeUseCase).execute(captor.capture());
        assertThat(captor.getValue().contractId()).isEqualTo("contract-1");
    }

    @Test
    void onContractDisputed_missingContractId_rejectsPayload() {
        assertThatThrownBy(() -> consumer.onContractDisputed(Map.of("eventId", "event-1")))
                .isInstanceOf(InvalidEventPayloadException.class);

        verifyNoInteractions(holdEscrowForDisputeUseCase);
    }
}
