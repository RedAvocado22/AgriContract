package com.agricontract.contract.application.usecase;

import com.agricontract.contract.application.dto.ResolveDisputeCommand;
import com.agricontract.contract.application.exception.ContractNotFoundException;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ResolveDisputeUseCaseTest {

    @Mock private ContractRepository contractRepository;

    private Contract disputedContract() {
        ContractTerms terms = new ContractTerms(
                new Quantity(new BigDecimal("100"), "kg"),
                new Money(new BigDecimal("500000"), "VND"),
                LocalDate.now().plusDays(30),
                new BigDecimal("0.30"), new BigDecimal("0.10"), "Grade A");
        Contract contract = Contract.offer(
                new ContractId("contract-1"), "listing-1", "buyer-1", "seller-1",
                "Rice", "Buyer Corp", "Seller Corp", "buyer@test.com", "seller@test.com", terms);
        contract.sign("buyer-1");
        contract.sign("seller-1");
        contract.activate();
        contract.confirmDelivery("buyer-1");
        contract.dispute("buyer-1", "Damaged goods");
        return contract;
    }

    @Test
    void execute_disputedContract_settlesAndSaves() {
        Contract contract = disputedContract();
        when(contractRepository.findById(new ContractId("contract-1"))).thenReturn(Optional.of(contract));

        new ResolveDisputeUseCase(contractRepository).execute(new ResolveDisputeCommand("contract-1"));

        assertThat(contract.getStatus()).isEqualTo(ContractStatus.SETTLED);
        verify(contractRepository).save(contract);
    }

    @Test
    void execute_alreadySettled_isIdempotent() {
        Contract contract = disputedContract();
        contract.resolveDispute();
        when(contractRepository.findById(new ContractId("contract-1"))).thenReturn(Optional.of(contract));

        new ResolveDisputeUseCase(contractRepository).execute(new ResolveDisputeCommand("contract-1"));

        verify(contractRepository, never()).save(any());
    }

    @Test
    void execute_missingContract_throws() {
        when(contractRepository.findById(new ContractId("missing"))).thenReturn(Optional.empty());

        assertThatThrownBy(() -> new ResolveDisputeUseCase(contractRepository)
                .execute(new ResolveDisputeCommand("missing")))
                .isInstanceOf(ContractNotFoundException.class);
    }
}
