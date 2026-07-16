package com.agricontract.contract.application.usecase;

import com.agricontract.contract.application.exception.ContractNotFoundException;
import com.agricontract.contract.application.exception.UnauthorizedContractActionException;
import com.agricontract.contract.domain.model.Contract;
import com.agricontract.contract.domain.model.NegotiationRevision;
import com.agricontract.contract.domain.model.vo.*;
import com.agricontract.contract.domain.repository.ContractRepository;
import com.agricontract.contract.domain.repository.NegotiationHistoryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class GetNegotiationHistoryUseCaseTest {

    @Mock private ContractRepository contractRepository;
    @Mock private NegotiationHistoryRepository negotiationHistoryRepository;

    private GetNegotiationHistoryUseCase useCase;
    private Contract contract;
    private ContractTerms terms;

    @BeforeEach
    void setUp() {
        useCase = new GetNegotiationHistoryUseCase(contractRepository, negotiationHistoryRepository);
        terms = new ContractTerms(
                new Quantity(new BigDecimal("100"), "kg"),
                new Money(new BigDecimal("500000"), "VND"),
                LocalDate.now().plusDays(30),
                new BigDecimal("0.30"),
                new BigDecimal("0.10"),
                "Grade A");
        contract = Contract.offer(
                new ContractId("contract-1"), "listing-1", "buyer-1", "seller-1",
                "Rice", "Buyer Corp", "Seller Corp", "buyer@test.com", "seller@test.com", terms);
    }

    @Test
    void execute_participant_returnsOrderedRevisionHistory() {
        when(contractRepository.findById(new ContractId("contract-1"))).thenReturn(Optional.of(contract));
        when(negotiationHistoryRepository.findByContractId("contract-1")).thenReturn(List.of(
                new NegotiationRevision(1, "buyer-1", Instant.parse("2026-07-01T00:00:00Z"), terms)));

        var response = useCase.execute("contract-1", "seller-1", false);

        assertThat(response).hasSize(1);
        assertThat(response.getFirst().termsRevision()).isEqualTo(1);
        assertThat(response.getFirst().proposedBy()).isEqualTo("buyer-1");
    }

    @Test
    void execute_admin_returnsHistory() {
        when(contractRepository.findById(new ContractId("contract-1"))).thenReturn(Optional.of(contract));
        when(negotiationHistoryRepository.findByContractId("contract-1")).thenReturn(List.of());

        assertThat(useCase.execute("contract-1", "admin-1", true)).isEmpty();
    }

    @Test
    void execute_stranger_throwsAndDoesNotReadHistory() {
        when(contractRepository.findById(new ContractId("contract-1"))).thenReturn(Optional.of(contract));

        assertThatThrownBy(() -> useCase.execute("contract-1", "stranger", false))
                .isInstanceOf(UnauthorizedContractActionException.class);
        verifyNoInteractions(negotiationHistoryRepository);
    }

    @Test
    void execute_missingContract_throws() {
        when(contractRepository.findById(new ContractId("missing"))).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.execute("missing", "buyer-1", false))
                .isInstanceOf(ContractNotFoundException.class);
        verifyNoInteractions(negotiationHistoryRepository);
    }
}
