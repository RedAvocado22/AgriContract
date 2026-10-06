package com.agricontract.escrow.application.usecase;

import com.agricontract.escrow.application.dto.LockBuyerPaymentCommand;
import com.agricontract.escrow.application.exception.InvalidEventPayloadException;
import com.agricontract.escrow.domain.model.EscrowAccount;
import com.agricontract.escrow.domain.model.vo.EscrowStatus;
import com.agricontract.escrow.domain.model.vo.Money;
import com.agricontract.escrow.domain.repository.EscrowAccountRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LockBuyerPaymentUseCaseTest {

    @Mock
    private EscrowAccountRepository escrowAccountRepository;

    private LockBuyerPaymentUseCase useCase;

    private static final LockBuyerPaymentCommand VALID_COMMAND = new LockBuyerPaymentCommand(
            "contract-1", "buyer-1", "seller-1", "buyer@agricontract.test", "seller@agricontract.test",
            new BigDecimal("0.1"), new Money(new BigDecimal("10000000"), "VND"));

    @Test
    void execute_missingContractId_throwsInvalidEventPayload() {
        useCase = new LockBuyerPaymentUseCase(escrowAccountRepository);
        LockBuyerPaymentCommand command = new LockBuyerPaymentCommand(
                null, "buyer-1", "seller-1", "b@x.com", "s@x.com", BigDecimal.TEN, new Money(BigDecimal.TEN, "VND"));

        assertThatThrownBy(() -> useCase.execute(command))
                .isInstanceOf(InvalidEventPayloadException.class);

        verify(escrowAccountRepository, never()).save(any());
    }

    @Test
    void execute_missingBuyerId_throwsInvalidEventPayload() {
        useCase = new LockBuyerPaymentUseCase(escrowAccountRepository);
        LockBuyerPaymentCommand command = new LockBuyerPaymentCommand(
                "contract-1", null, "seller-1", "b@x.com", "s@x.com", BigDecimal.TEN, new Money(BigDecimal.TEN, "VND"));

        assertThatThrownBy(() -> useCase.execute(command))
                .isInstanceOf(InvalidEventPayloadException.class);

        verify(escrowAccountRepository, never()).save(any());
    }

    @Test
    void execute_missingSellerId_throwsInvalidEventPayload() {
        useCase = new LockBuyerPaymentUseCase(escrowAccountRepository);
        LockBuyerPaymentCommand command = new LockBuyerPaymentCommand(
                "contract-1", "buyer-1", null, "b@x.com", "s@x.com", BigDecimal.TEN, new Money(BigDecimal.TEN, "VND"));

        assertThatThrownBy(() -> useCase.execute(command))
                .isInstanceOf(InvalidEventPayloadException.class);

        verify(escrowAccountRepository, never()).save(any());
    }

    @Test
    void execute_alreadyExists_skipsSilently() {
        useCase = new LockBuyerPaymentUseCase(escrowAccountRepository);
        when(escrowAccountRepository.existsByContractId("contract-1")).thenReturn(true);

        useCase.execute(VALID_COMMAND);

        verify(escrowAccountRepository, never()).save(any());
    }

    @Test
    void execute_happyPath_createsAndSavesAccount() {
        useCase = new LockBuyerPaymentUseCase(escrowAccountRepository);
        when(escrowAccountRepository.existsByContractId("contract-1")).thenReturn(false);

        useCase.execute(VALID_COMMAND);

        ArgumentCaptor<EscrowAccount> captor = ArgumentCaptor.forClass(EscrowAccount.class);
        verify(escrowAccountRepository).save(captor.capture());

        EscrowAccount saved = captor.getValue();
        assertThat(saved.getContractId()).isEqualTo("contract-1");
        assertThat(saved.getStatus()).isEqualTo(EscrowStatus.BUYER_LOCKED);
    }
}
