package com.agricontract.escrow.application.usecase;

import com.agricontract.escrow.application.dto.EscrowAccountResponse;
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
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class GetEscrowByContractIdUseCaseTest {

    @Mock
    private EscrowAccountRepository escrowAccountRepository;

    private GetEscrowByContractIdUseCase useCase;

    @Test
    void execute_accountNotFound_throws() {
        useCase = new GetEscrowByContractIdUseCase(escrowAccountRepository);
        when(escrowAccountRepository.findByContractId("contract-1")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.execute("contract-1", "buyer-1", false))
                .isInstanceOf(EscrowAccountNotFoundException.class);
    }

    @Test
    void execute_beforeSellerDepositLocked_returnsNullSellerDeposit() {
        useCase = new GetEscrowByContractIdUseCase(escrowAccountRepository);
        EscrowAccount account = EscrowAccount.lockBuyerPayment(
                "contract-1", "buyer-1", "seller-1", "b@x.com", "s@x.com",
                new BigDecimal("0.1"), new Money(new BigDecimal("10000000"), "VND"));
        when(escrowAccountRepository.findByContractId("contract-1")).thenReturn(Optional.of(account));

        EscrowAccountResponse response = useCase.execute("contract-1", "buyer-1", false);

        assertThat(response.contractId()).isEqualTo("contract-1");
        assertThat(response.buyerUserId()).isEqualTo("buyer-1");
        assertThat(response.sellerUserId()).isEqualTo("seller-1");
        assertThat(response.totalAmount()).isEqualTo(new BigDecimal("10000000"));
        assertThat(response.sellerDeposit()).isNull();
        assertThat(response.currency()).isEqualTo("VND");
        assertThat(response.status()).isEqualTo(EscrowStatus.BUYER_LOCKED);
    }

    @Test
    void execute_afterSellerDepositLocked_returnsComputedSellerDeposit() {
        useCase = new GetEscrowByContractIdUseCase(escrowAccountRepository);
        EscrowAccount account = EscrowAccount.lockBuyerPayment(
                "contract-1", "buyer-1", "seller-1", "b@x.com", "s@x.com",
                new BigDecimal("0.1"), new Money(new BigDecimal("10000000"), "VND"));
        account.lockSellerDeposit();
        when(escrowAccountRepository.findByContractId("contract-1")).thenReturn(Optional.of(account));

        EscrowAccountResponse response = useCase.execute("contract-1", "seller-1", false);

        assertThat(response.sellerDeposit()).isEqualTo(new BigDecimal("1000000"));
        assertThat(response.status()).isEqualTo(EscrowStatus.FULLY_LOCKED);
    }

    @Test
    void execute_requesterNotPartyAndNotAdmin_throwsUnauthorized() {
        useCase = new GetEscrowByContractIdUseCase(escrowAccountRepository);
        EscrowAccount account = EscrowAccount.lockBuyerPayment(
                "contract-1", "buyer-1", "seller-1", "b@x.com", "s@x.com",
                new BigDecimal("0.1"), new Money(new BigDecimal("10000000"), "VND"));
        when(escrowAccountRepository.findByContractId("contract-1")).thenReturn(Optional.of(account));

        assertThatThrownBy(() -> useCase.execute("contract-1", "stranger-1", false))
                .isInstanceOf(UnauthorizedEscrowActionException.class);
    }

    @Test
    void execute_requesterIsAdmin_bypassesOwnershipCheck() {
        useCase = new GetEscrowByContractIdUseCase(escrowAccountRepository);
        EscrowAccount account = EscrowAccount.lockBuyerPayment(
                "contract-1", "buyer-1", "seller-1", "b@x.com", "s@x.com",
                new BigDecimal("0.1"), new Money(new BigDecimal("10000000"), "VND"));
        when(escrowAccountRepository.findByContractId("contract-1")).thenReturn(Optional.of(account));

        EscrowAccountResponse response = useCase.execute("contract-1", "admin-1", true);

        assertThat(response.contractId()).isEqualTo("contract-1");
    }
}
