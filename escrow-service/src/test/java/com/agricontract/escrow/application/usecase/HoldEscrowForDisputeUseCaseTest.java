package com.agricontract.escrow.application.usecase;

import com.agricontract.escrow.application.dto.HoldEscrowForDisputeCommand;
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
import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class HoldEscrowForDisputeUseCaseTest {

    @Mock private EscrowAccountRepository escrowAccountRepository;

    private EscrowAccount deliveryPendingAccount() {
        EscrowAccount account = EscrowAccount.lockBuyerPayment(
                "contract-1", "buyer-1", "seller-1", "b@x.com", "s@x.com",
                new BigDecimal("0.1"), new Money(new BigDecimal("10000000"), "VND"));
        account.lockSellerDeposit();
        account.scheduleRelease(Instant.now().plusSeconds(30));
        return account;
    }

    @Test
    void execute_deliveryPending_holdsFundsAndSaves() {
        EscrowAccount account = deliveryPendingAccount();
        when(escrowAccountRepository.findByContractId("contract-1")).thenReturn(Optional.of(account));

        new HoldEscrowForDisputeUseCase(escrowAccountRepository)
                .execute(new HoldEscrowForDisputeCommand("contract-1"));

        assertThat(account.getStatus()).isEqualTo(EscrowStatus.DISPUTED);
        assertThat(account.getReleaseEligibleAt()).isNull();
        verify(escrowAccountRepository).save(account);
    }

    @Test
    void execute_alreadyDisputed_isIdempotent() {
        EscrowAccount account = deliveryPendingAccount();
        account.holdForDispute();
        when(escrowAccountRepository.findByContractId("contract-1")).thenReturn(Optional.of(account));

        new HoldEscrowForDisputeUseCase(escrowAccountRepository)
                .execute(new HoldEscrowForDisputeCommand("contract-1"));

        verify(escrowAccountRepository, never()).save(any());
    }

    @Test
    void execute_missingAccount_throws() {
        when(escrowAccountRepository.findByContractId("missing")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> new HoldEscrowForDisputeUseCase(escrowAccountRepository)
                .execute(new HoldEscrowForDisputeCommand("missing")))
                .isInstanceOf(EscrowAccountNotFoundException.class);
    }
}
