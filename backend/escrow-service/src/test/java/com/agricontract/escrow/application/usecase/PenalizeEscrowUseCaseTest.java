package com.agricontract.escrow.application.usecase;

import com.agricontract.escrow.application.dto.PenalizeEscrowCommand;
import com.agricontract.escrow.application.exception.EscrowAccountNotFoundException;
import com.agricontract.escrow.domain.model.EscrowAccount;
import com.agricontract.escrow.domain.model.vo.EscrowStatus;
import com.agricontract.escrow.domain.model.vo.Money;
import com.agricontract.escrow.domain.model.vo.Party;
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
class PenalizeEscrowUseCaseTest {

    @Mock
    private EscrowAccountRepository escrowAccountRepository;

    private PenalizeEscrowUseCase useCase;

    private EscrowAccount fullyLockedAccount() {
        EscrowAccount account = EscrowAccount.lockBuyerPayment(
                "contract-1", "buyer-1", "seller-1", "b@x.com", "s@x.com",
                new BigDecimal("0.1"), new Money(new BigDecimal("10000000"), "VND"));
        account.lockSellerDeposit();
        return account;
    }

    @Test
    void execute_accountNotFound_throws() {
        useCase = new PenalizeEscrowUseCase(escrowAccountRepository);
        when(escrowAccountRepository.findByContractId("contract-1")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.execute(new PenalizeEscrowCommand("contract-1", Party.BUYER, new BigDecimal("0.3"))))
                .isInstanceOf(EscrowAccountNotFoundException.class);

        verify(escrowAccountRepository, never()).save(any());
    }

    @Test
    void execute_whenNotFullyLocked_propagatesIllegalStateAndDoesNotSave() {
        useCase = new PenalizeEscrowUseCase(escrowAccountRepository);
        EscrowAccount account = EscrowAccount.lockBuyerPayment(
                "contract-1", "buyer-1", "seller-1", "b@x.com", "s@x.com",
                new BigDecimal("0.1"), new Money(new BigDecimal("10000000"), "VND"));
        when(escrowAccountRepository.findByContractId("contract-1")).thenReturn(Optional.of(account));

        assertThatThrownBy(() -> useCase.execute(new PenalizeEscrowCommand("contract-1", Party.BUYER, new BigDecimal("0.3"))))
                .isInstanceOf(IllegalStateException.class);

        verify(escrowAccountRepository, never()).save(any());
    }

    @Test
    void execute_alreadyPenalizedBuyer_skipsSilently() {
        useCase = new PenalizeEscrowUseCase(escrowAccountRepository);
        EscrowAccount account = fullyLockedAccount();
        account.penalizeBuyer(new BigDecimal("0.3"));
        when(escrowAccountRepository.findByContractId("contract-1")).thenReturn(Optional.of(account));

        useCase.execute(new PenalizeEscrowCommand("contract-1", Party.BUYER, new BigDecimal("0.3")));

        verify(escrowAccountRepository, never()).save(any());
    }

    @Test
    void execute_alreadyPenalizedSeller_skipsSilently() {
        useCase = new PenalizeEscrowUseCase(escrowAccountRepository);
        EscrowAccount account = fullyLockedAccount();
        account.penalizeSeller();
        when(escrowAccountRepository.findByContractId("contract-1")).thenReturn(Optional.of(account));

        useCase.execute(new PenalizeEscrowCommand("contract-1", Party.SELLER, null));

        verify(escrowAccountRepository, never()).save(any());
    }

    @Test
    void execute_cancelledByBuyer_penalizesBuyerAndSaves() {
        useCase = new PenalizeEscrowUseCase(escrowAccountRepository);
        EscrowAccount account = fullyLockedAccount();
        when(escrowAccountRepository.findByContractId("contract-1")).thenReturn(Optional.of(account));

        useCase.execute(new PenalizeEscrowCommand("contract-1", Party.BUYER, new BigDecimal("0.3")));

        assertThat(account.getStatus()).isEqualTo(EscrowStatus.PENALIZED_BUYER);
        verify(escrowAccountRepository).save(account);
    }

    @Test
    void execute_cancelledBySeller_penalizesSellerAndSaves() {
        useCase = new PenalizeEscrowUseCase(escrowAccountRepository);
        EscrowAccount account = fullyLockedAccount();
        when(escrowAccountRepository.findByContractId("contract-1")).thenReturn(Optional.of(account));

        useCase.execute(new PenalizeEscrowCommand("contract-1", Party.SELLER, null));

        assertThat(account.getStatus()).isEqualTo(EscrowStatus.PENALIZED_SELLER);
        verify(escrowAccountRepository).save(account);
    }
}
