package com.agricontract.escrow.application.usecase;

import com.agricontract.escrow.application.dto.ReleaseEscrowCommand;
import com.agricontract.escrow.application.exception.EscrowAccountNotFoundException;
import com.agricontract.escrow.domain.model.EscrowAccount;
import com.agricontract.escrow.domain.model.vo.EscrowStatus;
import com.agricontract.escrow.domain.model.vo.Money;
import com.agricontract.escrow.domain.repository.EscrowAccountRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ReleaseEscrowUseCaseTest {

    @Mock
    private EscrowAccountRepository escrowAccountRepository;

    private ReleaseEscrowUseCase useCase;

    private EscrowAccount fullyLockedAccount() {
        EscrowAccount account = EscrowAccount.lockBuyerPayment(
                "contract-1", "buyer-1", "seller-1", "b@x.com", "s@x.com",
                new BigDecimal("0.1"), new Money(new BigDecimal("10000000"), "VND"));
        account.lockSellerDeposit();
        return account;
    }

    @Test
    void execute_accountNotFound_throws() {
        useCase = new ReleaseEscrowUseCase(escrowAccountRepository);
        when(escrowAccountRepository.findByContractId("contract-1")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.execute(new ReleaseEscrowCommand("contract-1")))
                .isInstanceOf(EscrowAccountNotFoundException.class);

        verify(escrowAccountRepository, never()).save(any());
    }

    @Test
    void execute_alreadyReleased_skipsSilently() {
        useCase = new ReleaseEscrowUseCase(escrowAccountRepository);
        EscrowAccount account = fullyLockedAccount();
        account.release();
        when(escrowAccountRepository.findByContractId("contract-1")).thenReturn(Optional.of(account));

        useCase.execute(new ReleaseEscrowCommand("contract-1"));

        verify(escrowAccountRepository, never()).save(any());
    }

    @Test
    void execute_happyPath_releasesAndSaves() {
        useCase = new ReleaseEscrowUseCase(escrowAccountRepository);
        EscrowAccount account = fullyLockedAccount();
        when(escrowAccountRepository.findByContractId("contract-1")).thenReturn(Optional.of(account));

        useCase.execute(new ReleaseEscrowCommand("contract-1"));

        assertThat(account.getStatus()).isEqualTo(EscrowStatus.RELEASED);
        verify(escrowAccountRepository).save(account);
    }

    @Test
    void execute_whenNotFullyLocked_propagatesIllegalState() {
        useCase = new ReleaseEscrowUseCase(escrowAccountRepository);
        EscrowAccount account = EscrowAccount.lockBuyerPayment(
                "contract-1", "buyer-1", "seller-1", "b@x.com", "s@x.com",
                new BigDecimal("0.1"), new Money(new BigDecimal("10000000"), "VND"));
        when(escrowAccountRepository.findByContractId("contract-1")).thenReturn(Optional.of(account));

        assertThatThrownBy(() -> useCase.execute(new ReleaseEscrowCommand("contract-1")))
                .isInstanceOf(IllegalStateException.class);

        verify(escrowAccountRepository, never()).save(any());
    }
}
