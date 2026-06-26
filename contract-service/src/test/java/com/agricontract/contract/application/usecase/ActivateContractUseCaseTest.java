package com.agricontract.contract.application.usecase;

import com.agricontract.contract.application.dto.ActivateContractCommand;
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

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ActivateContractUseCaseTest {

    @Mock private ContractRepository contractRepository;

    private ActivateContractUseCase useCase;

    private static final ContractTerms TERMS = new ContractTerms(
            new Quantity(new BigDecimal("100"), "kg"),
            new Money(new BigDecimal("500000"), "VND"),
            LocalDate.now().plusDays(30),
            new BigDecimal("0.30"),
            new BigDecimal("0.10"),
            "Grade A"
    );

    private Contract signedContract() {
        Contract contract = Contract.offer(
                new ContractId("contract-1"), "listing-1",
                "buyer-1", "seller-1",
                "Rice", "Buyer Corp", "Seller Corp",
                "buyer@agricontract.test", "seller@agricontract.test",
                TERMS
        );
        contract.sign("buyer-1");
        contract.sign("seller-1");
        return contract;
    }

    @Test
    void execute_contractNotFound_throws() {
        useCase = new ActivateContractUseCase(contractRepository);
        when(contractRepository.findById(new ContractId("contract-1"))).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.execute(new ActivateContractCommand("contract-1")))
                .isInstanceOf(ContractNotFoundException.class);

        verify(contractRepository, never()).save(any());
    }

    @Test
    void execute_happyPath_statusActiveAndSaves() {
        useCase = new ActivateContractUseCase(contractRepository);
        Contract contract = signedContract();
        when(contractRepository.findById(new ContractId("contract-1"))).thenReturn(Optional.of(contract));

        useCase.execute(new ActivateContractCommand("contract-1"));

        assertThat(contract.getStatus()).isEqualTo(ContractStatus.ACTIVE);
        verify(contractRepository).save(contract);
    }

    @Test
    void execute_alreadyActive_skipsSilently() {
        useCase = new ActivateContractUseCase(contractRepository);
        Contract contract = signedContract();
        contract.activate();
        when(contractRepository.findById(new ContractId("contract-1"))).thenReturn(Optional.of(contract));

        useCase.execute(new ActivateContractCommand("contract-1"));

        // idempotent — still ACTIVE, no duplicate save
        assertThat(contract.getStatus()).isEqualTo(ContractStatus.ACTIVE);
        verify(contractRepository, never()).save(any());
    }
}
