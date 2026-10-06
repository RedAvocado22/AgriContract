package com.agricontract.contract.application.usecase;

import com.agricontract.contract.application.dto.ContractResponse;
import com.agricontract.contract.application.dto.DisputeContractCommand;
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
class DisputeContractUseCaseTest {

    @Mock private ContractRepository contractRepository;

    private DisputeContractUseCase useCase;

    private static final ContractTerms TERMS = new ContractTerms(
            new Quantity(new BigDecimal("100"), "kg"),
            new Money(new BigDecimal("500000"), "VND"),
            LocalDate.now().plusDays(30),
            new BigDecimal("0.30"),
            new BigDecimal("0.10"),
            "Grade A"
    );

    private Contract deliveredContract() {
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
        contract.confirmDelivery("buyer-1");
        return contract;
    }

    @Test
    void execute_contractNotFound_throws() {
        useCase = new DisputeContractUseCase(contractRepository);
        when(contractRepository.findById(new ContractId("contract-1"))).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.execute(
                new DisputeContractCommand("contract-1", "buyer-1", "quality issue")))
                .isInstanceOf(ContractNotFoundException.class);

        verify(contractRepository, never()).save(any());
    }

    @Test
    void execute_sellerTriesToDispute_throwsUnauthorized() {
        useCase = new DisputeContractUseCase(contractRepository);
        Contract contract = deliveredContract();
        when(contractRepository.findById(new ContractId("contract-1"))).thenReturn(Optional.of(contract));

        assertThatThrownBy(() -> useCase.execute(
                new DisputeContractCommand("contract-1", "seller-1", "reason")))
                .isInstanceOf(UnauthorizedContractAccessException.class);

        verify(contractRepository, never()).save(any());
    }

    @Test
    void execute_wrongStatus_throwsIllegalArgument() {
        useCase = new DisputeContractUseCase(contractRepository);
        // ACTIVE — dispute only allowed from DELIVERED
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
        when(contractRepository.findById(new ContractId("contract-1"))).thenReturn(Optional.of(contract));

        assertThatThrownBy(() -> useCase.execute(
                new DisputeContractCommand("contract-1", "buyer-1", "reason")))
                .isInstanceOf(IllegalArgumentException.class);

        verify(contractRepository, never()).save(any());
    }

    @Test
    void execute_happyPath_statusDisputedAndSaves() {
        useCase = new DisputeContractUseCase(contractRepository);
        Contract contract = deliveredContract();
        when(contractRepository.findById(new ContractId("contract-1"))).thenReturn(Optional.of(contract));

        ContractResponse response = useCase.execute(
                new DisputeContractCommand("contract-1", "buyer-1", "goods were rotten"));

        assertThat(contract.getStatus()).isEqualTo(ContractStatus.DISPUTED);
        assertThat(response.status()).isEqualTo("DISPUTED");
        verify(contractRepository).save(contract);
    }
}
