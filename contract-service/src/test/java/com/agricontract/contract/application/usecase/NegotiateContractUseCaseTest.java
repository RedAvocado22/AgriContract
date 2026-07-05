package com.agricontract.contract.application.usecase;

import com.agricontract.contract.application.dto.ContractResponse;
import com.agricontract.contract.application.dto.NegotiateContractCommand;
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
class NegotiateContractUseCaseTest {

    @Mock private ContractRepository contractRepository;

    private NegotiateContractUseCase useCase;

    private static final ContractTerms TERMS = new ContractTerms(
            new Quantity(new BigDecimal("100"), "kg"),
            new Money(new BigDecimal("500000"), "VND"),
            LocalDate.now().plusDays(30),
            new BigDecimal("0.30"),
            new BigDecimal("0.10"),
            "Grade A"
    );

    private static final ContractTerms NEW_TERMS = new ContractTerms(
            new Quantity(new BigDecimal("120"), "kg"),
            new Money(new BigDecimal("480000"), "VND"),
            LocalDate.now().plusDays(25),
            new BigDecimal("0.20"),
            new BigDecimal("0.10"),
            "Grade B"
    );

    private Contract offeredContract() {
        return Contract.offer(
                new ContractId("contract-1"), "listing-1",
                "buyer-1", "seller-1",
                "Rice", "Buyer Corp", "Seller Corp",
                "buyer@agricontract.test", "seller@agricontract.test",
                TERMS
        );
    }

    @Test
    void execute_contractNotFound_throws() {
        useCase = new NegotiateContractUseCase(contractRepository);
        when(contractRepository.findById(new ContractId("contract-1"))).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.execute(
                new NegotiateContractCommand("contract-1", "buyer-1", NEW_TERMS)))
                .isInstanceOf(ContractNotFoundException.class);

        verify(contractRepository, never()).save(any());
    }

    @Test
    void execute_happyPath_statusNegotiatingAndSaves() {
        useCase = new NegotiateContractUseCase(contractRepository);
        Contract contract = offeredContract();
        when(contractRepository.findById(new ContractId("contract-1"))).thenReturn(Optional.of(contract));

        ContractResponse response = useCase.execute(
                new NegotiateContractCommand("contract-1", "buyer-1", NEW_TERMS));

        assertThat(contract.getStatus()).isEqualTo(ContractStatus.NEGOTIATING);
        assertThat(contract.getTerms()).isEqualTo(NEW_TERMS);
        assertThat(response.status()).isEqualTo("NEGOTIATING");
        verify(contractRepository).save(contract);
    }

    @Test
    void execute_sellerCounterOffers_statusNegotiatingAndSaves() {
        useCase = new NegotiateContractUseCase(contractRepository);
        Contract contract = offeredContract();
        when(contractRepository.findById(new ContractId("contract-1"))).thenReturn(Optional.of(contract));

        useCase.execute(new NegotiateContractCommand("contract-1", "seller-1", NEW_TERMS));

        assertThat(contract.getStatus()).isEqualTo(ContractStatus.NEGOTIATING);
        verify(contractRepository).save(contract);
    }

    @Test
    void execute_unauthorizedUser_propagatesUnauthorizedAndDoesNotSave() {
        useCase = new NegotiateContractUseCase(contractRepository);
        Contract contract = offeredContract();
        when(contractRepository.findById(new ContractId("contract-1"))).thenReturn(Optional.of(contract));

        assertThatThrownBy(() -> useCase.execute(
                new NegotiateContractCommand("contract-1", "stranger-99", NEW_TERMS)))
                .isInstanceOf(UnauthorizedContractAccessException.class);

        verify(contractRepository, never()).save(any());
    }
}
