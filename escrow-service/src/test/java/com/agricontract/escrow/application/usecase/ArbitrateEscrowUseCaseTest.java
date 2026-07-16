package com.agricontract.escrow.application.usecase;

import com.agricontract.escrow.application.dto.ArbitrateEscrowCommand;
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
class ArbitrateEscrowUseCaseTest {

    @Mock
    private EscrowAccountRepository escrowAccountRepository;

    private ArbitrateEscrowUseCase useCase;

    private EscrowAccount fullyLockedAccount() {
        EscrowAccount account = EscrowAccount.lockBuyerPayment(
                "contract-1", "buyer-1", "seller-1", "b@x.com", "s@x.com",
                new BigDecimal("0.1"), new Money(new BigDecimal("10000000"), "VND"));
        account.lockSellerDeposit();
        account.holdForDispute();
        return account;
    }

    @Test
    void execute_accountNotFound_throws() {
        useCase = new ArbitrateEscrowUseCase(escrowAccountRepository);
        when(escrowAccountRepository.findByContractId("contract-1")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.execute(new ArbitrateEscrowCommand(
                "contract-1", new BigDecimal("3000000"), new BigDecimal("8000000"), "reason")))
                .isInstanceOf(EscrowAccountNotFoundException.class);

        verify(escrowAccountRepository, never()).save(any());
    }

    @Test
    void execute_happyPath_arbitratesWithAccountCurrencyAndSaves() {
        useCase = new ArbitrateEscrowUseCase(escrowAccountRepository);
        EscrowAccount account = fullyLockedAccount();
        when(escrowAccountRepository.findByContractId("contract-1")).thenReturn(Optional.of(account));

        useCase.execute(new ArbitrateEscrowCommand(
                "contract-1", new BigDecimal("3000000"), new BigDecimal("8000000"), "Seller delivered 70%."));

        assertThat(account.getStatus()).isEqualTo(EscrowStatus.ARBITRATED);
        verify(escrowAccountRepository).save(account);
    }

    @Test
    void execute_sumMismatch_propagatesIllegalArgumentAndDoesNotSave() {
        useCase = new ArbitrateEscrowUseCase(escrowAccountRepository);
        EscrowAccount account = fullyLockedAccount();
        when(escrowAccountRepository.findByContractId("contract-1")).thenReturn(Optional.of(account));

        assertThatThrownBy(() -> useCase.execute(new ArbitrateEscrowCommand(
                "contract-1", new BigDecimal("3000000"), new BigDecimal("7000000"), "reason")))
                .isInstanceOf(IllegalArgumentException.class);

        verify(escrowAccountRepository, never()).save(any());
    }

    @Test
    void execute_whenNotFullyLocked_propagatesIllegalState() {
        useCase = new ArbitrateEscrowUseCase(escrowAccountRepository);
        EscrowAccount account = EscrowAccount.lockBuyerPayment(
                "contract-1", "buyer-1", "seller-1", "b@x.com", "s@x.com",
                new BigDecimal("0.1"), new Money(new BigDecimal("10000000"), "VND"));
        when(escrowAccountRepository.findByContractId("contract-1")).thenReturn(Optional.of(account));

        assertThatThrownBy(() -> useCase.execute(new ArbitrateEscrowCommand(
                "contract-1", new BigDecimal("3000000"), new BigDecimal("8000000"), "reason")))
                .isInstanceOf(IllegalStateException.class);

        verify(escrowAccountRepository, never()).save(any());
    }
}
