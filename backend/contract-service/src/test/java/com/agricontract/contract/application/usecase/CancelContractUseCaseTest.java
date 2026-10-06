package com.agricontract.contract.application.usecase;

import com.agricontract.contract.application.dto.CancelContractCommand;
import com.agricontract.contract.application.dto.ContractResponse;
import com.agricontract.contract.application.exception.ContractNotFoundException;
import com.agricontract.contract.domain.exception.UnauthorizedContractAccessException;
import com.agricontract.contract.domain.model.Contract;
import com.agricontract.contract.domain.model.vo.*;
import com.agricontract.contract.domain.repository.ContractRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CancelContractUseCaseTest {

    @Mock private ContractRepository contractRepository;

    private CancelContractUseCase useCase;

    private static final ContractTerms TERMS = new ContractTerms(
            new Quantity(new BigDecimal("100"), "kg"),
            new Money(new BigDecimal("500000"), "VND"),
            LocalDate.now().plusDays(30),
            new BigDecimal("0.30"),
            new BigDecimal("0.10"),
            "Grade A"
    );

    private Contract activeContract() {
        Contract contract = Contract.offer(
                new ContractId("contract-1"), "listing-1",
                "buyer-1", "seller-1",
                "Rice", "Buyer Corp", "Seller Corp",
                "buyer@agricontract.test", "seller@agricontract.test",
                TERMS
        );
        contract.sign("buyer-1");
        contract.sign("seller-1");
        contract.activate();
        return contract;
    }

    @Test
    void execute_contractNotFound_throws() {
        useCase = new CancelContractUseCase(contractRepository);
        when(contractRepository.findById(new ContractId("contract-1"))).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.execute(
                new CancelContractCommand("contract-1", "buyer-1", "changed mind")))
                .isInstanceOf(ContractNotFoundException.class);

        verify(contractRepository, never()).save(any());
    }

    @Test
    void execute_unauthorizedUser_throwsUnauthorized() {
        useCase = new CancelContractUseCase(contractRepository);
        Contract contract = activeContract();
        when(contractRepository.findById(new ContractId("contract-1"))).thenReturn(Optional.of(contract));

        assertThatThrownBy(() -> useCase.execute(
                new CancelContractCommand("contract-1", "stranger-99", "reason")))
                .isInstanceOf(UnauthorizedContractAccessException.class);

        verify(contractRepository, never()).save(any());
    }

    @Test
    void execute_wrongStatus_throwsIllegalArgument() {
        useCase = new CancelContractUseCase(contractRepository);
        // OFFERED status — cancel only allowed from ACTIVE (domain rule)
        Contract contract = Contract.offer(
                new ContractId("contract-1"), "listing-1",
                "buyer-1", "seller-1",
                "Rice", "Buyer Corp", "Seller Corp",
                "buyer@agricontract.test", "seller@agricontract.test",
                TERMS
        );
        when(contractRepository.findById(new ContractId("contract-1"))).thenReturn(Optional.of(contract));

        assertThatThrownBy(() -> useCase.execute(
                new CancelContractCommand("contract-1", "buyer-1", "reason")))
                .isInstanceOf(IllegalArgumentException.class);

        verify(contractRepository, never()).save(any());
    }

    @Test
    void execute_buyerCancels_statusCancelledByBuyerAndSaves() {
        useCase = new CancelContractUseCase(contractRepository);
        Contract contract = activeContract();
        when(contractRepository.findById(new ContractId("contract-1"))).thenReturn(Optional.of(contract));

        ContractResponse response = useCase.execute(
                new CancelContractCommand("contract-1", "buyer-1", "bad harvest"));

        assertThat(contract.getStatus()).isEqualTo(ContractStatus.CANCELLED);
        assertThat(contract.getCancelledBy()).isEqualTo(CancelledBy.BUYER);
        assertThat(response.status()).isEqualTo("CANCELLED");
        assertThat(response.cancelledBy()).isEqualTo("BUYER");
        verify(contractRepository).save(contract);
    }

    @Test
    void execute_sellerCancels_statusCancelledBySellerAndSaves() {
        useCase = new CancelContractUseCase(contractRepository);
        Contract contract = activeContract();
        when(contractRepository.findById(new ContractId("contract-1"))).thenReturn(Optional.of(contract));

        ContractResponse response = useCase.execute(
                new CancelContractCommand("contract-1", "seller-1", "cannot supply"));

        assertThat(contract.getStatus()).isEqualTo(ContractStatus.CANCELLED);
        assertThat(contract.getCancelledBy()).isEqualTo(CancelledBy.SELLER);
        assertThat(response.cancelledBy()).isEqualTo("SELLER");
        verify(contractRepository).save(contract);
    }

    @Test
    void execute_cancelReasonIsPersistedOnContract() {
        useCase = new CancelContractUseCase(contractRepository);
        Contract contract = activeContract();
        when(contractRepository.findById(new ContractId("contract-1"))).thenReturn(Optional.of(contract));

        useCase.execute(new CancelContractCommand("contract-1", "buyer-1", "bad weather"));

        assertThat(contract.getCancelReason()).isEqualTo("bad weather");
    }
}
