package com.agricontract.contract.application.usecase;

import com.agricontract.contract.application.dto.ContractResponse;
import com.agricontract.contract.application.dto.PagedResult;
import com.agricontract.contract.domain.model.Contract;
import com.agricontract.contract.domain.model.vo.*;
import com.agricontract.contract.domain.repository.ContractRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

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

    private static final Pageable PAGE = PageRequest.of(0, 20);

    @BeforeEach
    void setUp() {
        useCase = new ListContractsUseCase(contractRepository);
    }

    private Contract contractFor(String contractId, String buyerId, String sellerId) {
        return Contract.offer(
                new ContractId(contractId), "listing-1",
                buyerId, sellerId,
                "Rice", "Buyer Corp", "Seller Corp",
                "buyer@agricontract.test", "seller@agricontract.test",
                TERMS
        );
    }

    // --- executeByBuyer ---

    @Test
    void executeByBuyer_nullStatus_returnsAllContracts() {
        List<Contract> contracts = List.of(
                contractFor("c-1", "buyer-1", "seller-1"),
                contractFor("c-2", "buyer-1", "seller-2")
        );
        when(contractRepository.findByBuyerId("buyer-1", null, PAGE)).thenReturn(contracts);
        when(contractRepository.countByBuyerId("buyer-1", null)).thenReturn(2L);

        PagedResult<ContractResponse> result = useCase.executeByBuyer("buyer-1", null, PAGE);

        assertThat(result.content()).hasSize(2);
        assertThat(result.content()).extracting(ContractResponse::buyerId).containsOnly("buyer-1");
    }

    @Test
    void executeByBuyer_withStatusFilter_returnsFilteredContracts() {
        List<Contract> contracts = List.of(contractFor("c-1", "buyer-1", "seller-1"));
        when(contractRepository.findByBuyerId("buyer-1", ContractStatus.OFFERED, PAGE)).thenReturn(contracts);
        when(contractRepository.countByBuyerId("buyer-1", ContractStatus.OFFERED)).thenReturn(1L);

        PagedResult<ContractResponse> result = useCase.executeByBuyer("buyer-1", ContractStatus.OFFERED, PAGE);

        assertThat(result.content()).hasSize(1);
        assertThat(result.totalElements()).isEqualTo(1L);
    }

    @Test
    void executeByBuyer_noContracts_returnsEmptyPagedResult() {
        when(contractRepository.findByBuyerId("buyer-1", null, PAGE)).thenReturn(List.of());
        when(contractRepository.countByBuyerId("buyer-1", null)).thenReturn(0L);

        PagedResult<ContractResponse> result = useCase.executeByBuyer("buyer-1", null, PAGE);

        assertThat(result.content()).isEmpty();
        assertThat(result.totalElements()).isZero();
        assertThat(result.empty()).isTrue();
    }

    // --- executeBySeller ---

    @Test
    void executeBySeller_nullStatus_returnsAllContracts() {
        List<Contract> contracts = List.of(
                contractFor("c-1", "buyer-1", "seller-1"),
                contractFor("c-2", "buyer-2", "seller-1")
        );
        when(contractRepository.findBySellerId("seller-1", null, PAGE)).thenReturn(contracts);
        when(contractRepository.countBySellerId("seller-1", null)).thenReturn(2L);

        PagedResult<ContractResponse> result = useCase.executeBySeller("seller-1", null, PAGE);

        assertThat(result.content()).hasSize(2);
        assertThat(result.content()).extracting(ContractResponse::sellerId).containsOnly("seller-1");
    }

    @Test
    void executeBySeller_withStatusFilter_returnsFilteredContracts() {
        List<Contract> contracts = List.of(contractFor("c-1", "buyer-1", "seller-1"));
        when(contractRepository.findBySellerId("seller-1", ContractStatus.OFFERED, PAGE)).thenReturn(contracts);
        when(contractRepository.countBySellerId("seller-1", ContractStatus.OFFERED)).thenReturn(1L);

        PagedResult<ContractResponse> result = useCase.executeBySeller("seller-1", ContractStatus.OFFERED, PAGE);

        assertThat(result.content()).hasSize(1);
        assertThat(result.totalElements()).isEqualTo(1L);
    }

    @Test
    void executeBySeller_noContracts_returnsEmptyPagedResult() {
        when(contractRepository.findBySellerId("seller-1", null, PAGE)).thenReturn(List.of());
        when(contractRepository.countBySellerId("seller-1", null)).thenReturn(0L);

        PagedResult<ContractResponse> result = useCase.executeBySeller("seller-1", null, PAGE);

        assertThat(result.content()).isEmpty();
        assertThat(result.empty()).isTrue();
    }

    // --- pagination metadata ---

    @Test
    void pagingMetadata_isCorrect() {
        Pageable page2 = PageRequest.of(1, 10);
        List<Contract> contracts = List.of(contractFor("c-11", "buyer-1", "seller-1"));
        when(contractRepository.findByBuyerId("buyer-1", null, page2)).thenReturn(contracts);
        when(contractRepository.countByBuyerId("buyer-1", null)).thenReturn(11L);

        PagedResult<ContractResponse> result = useCase.executeByBuyer("buyer-1", null, page2);

        assertThat(result.page()).isEqualTo(1);
        assertThat(result.size()).isEqualTo(10);
        assertThat(result.totalElements()).isEqualTo(11L);
        assertThat(result.totalPages()).isEqualTo(2);
        assertThat(result.first()).isFalse();
        assertThat(result.last()).isTrue();
        assertThat(result.empty()).isFalse();
    }
}
