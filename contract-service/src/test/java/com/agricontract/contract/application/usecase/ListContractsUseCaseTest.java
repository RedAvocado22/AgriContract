package com.agricontract.contract.application.usecase;

import com.agricontract.contract.application.dto.ContractResponse;
import com.agricontract.contract.domain.model.Contract;
import com.agricontract.contract.domain.model.vo.*;
import com.agricontract.contract.domain.repository.ContractRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ListContractsUseCaseTest {

    @Mock private ContractRepository contractRepository;

    private ListContractsUseCase useCase;

    private static final ContractTerms TERMS = new ContractTerms(
            new Quantity(new BigDecimal("100"), "kg"),
            new Money(new BigDecimal("500000"), "VND"),
            LocalDate.now().plusDays(30),
            new BigDecimal("0.30"),
            new BigDecimal("0.10"),
            "Grade A"
    );

    private Contract contractFor(String contractId, String buyerId, String sellerId) {
        return Contract.offer(
                new ContractId(contractId), "listing-1",
                buyerId, sellerId,
                "Rice", "Buyer Corp", "Seller Corp",
                "buyer@agricontract.test", "seller@agricontract.test",
                TERMS
        );
    }

    @Test
    void executeByBuyer_returnsContractsMappedToResponse() {
        useCase = new ListContractsUseCase(contractRepository);
        List<Contract> contracts = List.of(
                contractFor("c-1", "buyer-1", "seller-1"),
                contractFor("c-2", "buyer-1", "seller-2")
        );
        when(contractRepository.findByBuyerId("buyer-1")).thenReturn(contracts);

        List<ContractResponse> result = useCase.executeByBuyer("buyer-1");

        assertThat(result).hasSize(2);
        assertThat(result).extracting(ContractResponse::buyerId).containsOnly("buyer-1");
    }

    @Test
    void executeByBuyer_noContracts_returnsEmptyList() {
        useCase = new ListContractsUseCase(contractRepository);
        when(contractRepository.findByBuyerId("buyer-1")).thenReturn(List.of());

        List<ContractResponse> result = useCase.executeByBuyer("buyer-1");

        assertThat(result).isEmpty();
    }

    @Test
    void executeBySeller_returnsContractsMappedToResponse() {
        useCase = new ListContractsUseCase(contractRepository);
        List<Contract> contracts = List.of(
                contractFor("c-1", "buyer-1", "seller-1"),
                contractFor("c-2", "buyer-2", "seller-1")
        );
        when(contractRepository.findBySellerId("seller-1")).thenReturn(contracts);

        List<ContractResponse> result = useCase.executeBySeller("seller-1");

        assertThat(result).hasSize(2);
        assertThat(result).extracting(ContractResponse::sellerId).containsOnly("seller-1");
    }

    @Test
    void executeBySeller_noContracts_returnsEmptyList() {
        useCase = new ListContractsUseCase(contractRepository);
        when(contractRepository.findBySellerId("seller-1")).thenReturn(List.of());

        List<ContractResponse> result = useCase.executeBySeller("seller-1");

        assertThat(result).isEmpty();
    }
}
