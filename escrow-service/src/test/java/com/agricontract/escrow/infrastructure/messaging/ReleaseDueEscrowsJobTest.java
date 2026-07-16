package com.agricontract.escrow.infrastructure.messaging;

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
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ReleaseDueEscrowsJobTest {

    @Mock private EscrowAccountRepository escrowAccountRepository;

    @Test
    void releaseDueEscrows_releasesOnlyRepositoryEligibleAccounts() {
        EscrowAccount account = EscrowAccount.lockBuyerPayment(
                "contract-1", "buyer-1", "seller-1", "b@x.com", "s@x.com",
                new BigDecimal("0.1"), new Money(new BigDecimal("10000000"), "VND"));
        account.lockSellerDeposit();
        account.scheduleRelease(Instant.now().minusSeconds(1));
        when(escrowAccountRepository.findReleaseEligibleBefore(any())).thenReturn(List.of(account));

        new ReleaseDueEscrowsJob(escrowAccountRepository).releaseDueEscrows();

        assertThat(account.getStatus()).isEqualTo(EscrowStatus.RELEASED);
        verify(escrowAccountRepository).save(account);
    }

    @Test
    void releaseDueEscrows_noEligibleAccounts_doesNothing() {
        when(escrowAccountRepository.findReleaseEligibleBefore(any())).thenReturn(List.of());

        new ReleaseDueEscrowsJob(escrowAccountRepository).releaseDueEscrows();

        verify(escrowAccountRepository, never()).save(any());
    }
}
