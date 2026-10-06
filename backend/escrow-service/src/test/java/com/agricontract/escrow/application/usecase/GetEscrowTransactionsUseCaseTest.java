package com.agricontract.escrow.application.usecase;

import com.agricontract.escrow.application.dto.EscrowTransactionResponse;
import com.agricontract.escrow.application.exception.EscrowAccountNotFoundException;
import com.agricontract.escrow.application.exception.UnauthorizedEscrowActionException;
import com.agricontract.escrow.domain.model.EscrowAccount;
import com.agricontract.escrow.domain.model.vo.Money;
import com.agricontract.escrow.domain.model.vo.TransactionType;
import com.agricontract.escrow.domain.repository.EscrowAccountRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class GetEscrowTransactionsUseCaseTest {

    @Mock
    private EscrowAccountRepository escrowAccountRepository;

    private GetEscrowTransactionsUseCase useCase;

    @Test
    void execute_accountNotFound_throws() {
        useCase = new GetEscrowTransactionsUseCase(escrowAccountRepository);
        when(escrowAccountRepository.findById(any())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.execute("escrow-1", "buyer-1", false))
                .isInstanceOf(EscrowAccountNotFoundException.class);
    }

    @Test
    void execute_happyPath_mapsTransactionsWithoutEscrowId() {
        useCase = new GetEscrowTransactionsUseCase(escrowAccountRepository);
        EscrowAccount account = EscrowAccount.lockBuyerPayment(
                "contract-1", "buyer-1", "seller-1", "b@x.com", "s@x.com",
                new BigDecimal("0.1"), new Money(new BigDecimal("10000000"), "VND"));
        account.lockSellerDeposit();
        when(escrowAccountRepository.findById(any())).thenReturn(Optional.of(account));

        List<EscrowTransactionResponse> responses = useCase.execute(account.getEscrowId().value(), "buyer-1", false);

        assertThat(responses).hasSize(2);
        assertThat(responses.get(0).type()).isEqualTo(TransactionType.LOCK);
        assertThat(responses.get(0).amount()).isEqualTo(new BigDecimal("10000000"));
        assertThat(responses.get(0).currency()).isEqualTo("VND");
        assertThat(responses.get(0).transactionId()).isNotBlank();
        assertThat(responses.get(1).amount()).isEqualTo(new BigDecimal("1000000"));
    }

    @Test
    void execute_requesterNotPartyAndNotAdmin_throwsUnauthorized() {
        useCase = new GetEscrowTransactionsUseCase(escrowAccountRepository);
        EscrowAccount account = EscrowAccount.lockBuyerPayment(
                "contract-1", "buyer-1", "seller-1", "b@x.com", "s@x.com",
                new BigDecimal("0.1"), new Money(new BigDecimal("10000000"), "VND"));
        when(escrowAccountRepository.findById(any())).thenReturn(Optional.of(account));

        assertThatThrownBy(() -> useCase.execute(account.getEscrowId().value(), "stranger-1", false))
                .isInstanceOf(UnauthorizedEscrowActionException.class);
    }

    @Test
    void execute_requesterIsAdmin_bypassesOwnershipCheck() {
        useCase = new GetEscrowTransactionsUseCase(escrowAccountRepository);
        EscrowAccount account = EscrowAccount.lockBuyerPayment(
                "contract-1", "buyer-1", "seller-1", "b@x.com", "s@x.com",
                new BigDecimal("0.1"), new Money(new BigDecimal("10000000"), "VND"));
        when(escrowAccountRepository.findById(any())).thenReturn(Optional.of(account));

        List<EscrowTransactionResponse> responses = useCase.execute(account.getEscrowId().value(), "admin-1", true);

        assertThat(responses).hasSize(1);
    }
}
