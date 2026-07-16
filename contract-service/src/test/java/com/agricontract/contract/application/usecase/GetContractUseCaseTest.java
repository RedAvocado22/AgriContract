package com.agricontract.contract.application.usecase;

import com.agricontract.contract.application.dto.ContractResponse;
import com.agricontract.contract.application.exception.ContractNotFoundException;
import com.agricontract.contract.application.exception.UnauthorizedContractActionException;
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
class GetContractUseCaseTest {

    @Mock private ContractRepository contractRepository;

    private GetContractUseCase useCase;

    private static final ContractTerms TERMS = new ContractTerms(
            new Quantity(new BigDecimal("100"), "kg"),
            new Money(new BigDecimal("500000"), "VND"),
            LocalDate.now().plusDays(30),
            new BigDecimal("0.30"),
            new BigDecimal("0.10"),
            "Grade A"
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
        useCase = new GetContractUseCase(contractRepository);
        when(contractRepository.findById(new ContractId("contract-1"))).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.execute("contract-1", "buyer-1", false))
                .isInstanceOf(ContractNotFoundException.class);
    }

    @Test
    void execute_buyerAccesses_returnsResponse() {
        useCase = new GetContractUseCase(contractRepository);
        Contract contract = offeredContract();
        when(contractRepository.findById(new ContractId("contract-1"))).thenReturn(Optional.of(contract));

        ContractResponse response = useCase.execute("contract-1", "buyer-1", false);

        assertThat(response.contractId()).isEqualTo("contract-1");
        assertThat(response.buyerId()).isEqualTo("buyer-1");
        assertThat(response.termsRevision()).isEqualTo(1);
        assertThat(response.signatories()).isEmpty();
    }

    @Test
    void execute_sellerAccesses_returnsResponse() {
        useCase = new GetContractUseCase(contractRepository);
        Contract contract = offeredContract();
        when(contractRepository.findById(new ContractId("contract-1"))).thenReturn(Optional.of(contract));

        ContractResponse response = useCase.execute("contract-1", "seller-1", false);

        assertThat(response.sellerId()).isEqualTo("seller-1");
    }

    @Test
    void execute_adminAccesses_returnsResponse() {
        useCase = new GetContractUseCase(contractRepository);
        Contract contract = offeredContract();
        when(contractRepository.findById(new ContractId("contract-1"))).thenReturn(Optional.of(contract));

        ContractResponse response = useCase.execute("contract-1", "admin-99", true);

        assertThat(response.contractId()).isEqualTo("contract-1");
    }

    @Test
    void execute_strangerNotAdmin_throwsUnauthorized() {
        useCase = new GetContractUseCase(contractRepository);
        Contract contract = offeredContract();
        when(contractRepository.findById(new ContractId("contract-1"))).thenReturn(Optional.of(contract));

        assertThatThrownBy(() -> useCase.execute("contract-1", "stranger-99", false))
                .isInstanceOf(UnauthorizedContractActionException.class);
    }

    @Test
    void execute_buyerOfDifferentContract_throwsUnauthorized() {
        // buyer-2 is a legit user but NOT a party of contract-1 — must be denied
        useCase = new GetContractUseCase(contractRepository);
        Contract contract = offeredContract();  // buyer-1, seller-1
        when(contractRepository.findById(new ContractId("contract-1"))).thenReturn(Optional.of(contract));

        assertThatThrownBy(() -> useCase.execute("contract-1", "buyer-2", false))
                .isInstanceOf(UnauthorizedContractActionException.class);
    }
}
