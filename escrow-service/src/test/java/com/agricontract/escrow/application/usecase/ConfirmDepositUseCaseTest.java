package com.agricontract.escrow.application.usecase;

import com.agricontract.escrow.application.dto.ConfirmDepositCommand;
import com.agricontract.escrow.application.exception.EscrowAccountNotFoundException;
import com.agricontract.escrow.application.exception.UnauthorizedEscrowActionException;
import com.agricontract.escrow.domain.model.EscrowAccount;
import com.agricontract.escrow.domain.model.vo.EscrowStatus;
import com.agricontract.escrow.domain.model.vo.Money;
import com.agricontract.escrow.domain.repository.EscrowAccountRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ConfirmDepositUseCaseTest {

    @Mock
    private EscrowAccountRepository escrowAccountRepository;

    private ConfirmDepositUseCase useCase;

    private EscrowAccount buyerLockedAccount() {
        return EscrowAccount.lockBuyerPayment(
                "contract-1", "buyer-1", "seller-1", "b@x.com", "s@x.com",
                new BigDecimal("0.1"), new Money(new BigDecimal("10000000"), "VND"));
    }

    @Test
    void execute_accountNotFound_throws() {
        useCase = new ConfirmDepositUseCase(escrowAccountRepository);
        when(escrowAccountRepository.findByContractId("contract-1")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.execute(new ConfirmDepositCommand("contract-1", "seller-1")))
                .isInstanceOf(EscrowAccountNotFoundException.class);

        verify(escrowAccountRepository, never()).save(any());
    }

    @Test
    void execute_wrongSeller_throwsUnauthorized() {
        useCase = new ConfirmDepositUseCase(escrowAccountRepository);
        EscrowAccount account = buyerLockedAccount();
        when(escrowAccountRepository.findByContractId("contract-1")).thenReturn(Optional.of(account));

        assertThatThrownBy(() -> useCase.execute(new ConfirmDepositCommand("contract-1", "someone-else")))
                .isInstanceOf(UnauthorizedEscrowActionException.class);

        verify(escrowAccountRepository, never()).save(any());
    }

    @Test
    void execute_alreadyFullyLocked_skipsSilently() {
        useCase = new ConfirmDepositUseCase(escrowAccountRepository);
        EscrowAccount account = buyerLockedAccount();
        account.lockSellerDeposit();
        when(escrowAccountRepository.findByContractId("contract-1")).thenReturn(Optional.of(account));

        useCase.execute(new ConfirmDepositCommand("contract-1", "seller-1"));

        verify(escrowAccountRepository, never()).save(any());
    }

    @Test
    void execute_whenAlreadyReleased_skipsDuplicateDepositConfirmation() {
        useCase = new ConfirmDepositUseCase(escrowAccountRepository);
        EscrowAccount account = buyerLockedAccount();
        account.lockSellerDeposit();
        account.scheduleRelease(Instant.now());
        account.release();
        when(escrowAccountRepository.findByContractId("contract-1")).thenReturn(Optional.of(account));

        useCase.execute(new ConfirmDepositCommand("contract-1", "seller-1"));

        verify(escrowAccountRepository, never()).save(any());
    }

    @Test
    void execute_happyPath_locksDepositAndSaves() {
        useCase = new ConfirmDepositUseCase(escrowAccountRepository);
        EscrowAccount account = buyerLockedAccount();
        when(escrowAccountRepository.findByContractId("contract-1")).thenReturn(Optional.of(account));

        useCase.execute(new ConfirmDepositCommand("contract-1", "seller-1"));

        assertThat(account.getStatus()).isEqualTo(EscrowStatus.FULLY_LOCKED);
        verify(escrowAccountRepository).save(account);
    }
}
