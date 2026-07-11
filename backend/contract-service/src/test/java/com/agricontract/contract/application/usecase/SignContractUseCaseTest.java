package com.agricontract.contract.application.usecase;

import com.agricontract.contract.application.dto.SignContractCommand;
import com.agricontract.contract.application.exception.ContractNotFoundException;
import com.agricontract.contract.domain.exception.UnauthorizedContractAccessException;
import com.agricontract.contract.application.port.ListingPort;
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
class SignContractUseCaseTest {

    @Mock private ContractRepository contractRepository;
    @Mock private ListingPort listingPort;

    private SignContractUseCase useCase;

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
        useCase = new SignContractUseCase(contractRepository, listingPort);
        when(contractRepository.findById(new ContractId("contract-1"))).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.execute(new SignContractCommand("contract-1", "buyer-1")))
                .isInstanceOf(ContractNotFoundException.class);

        verify(contractRepository, never()).save(any());
    }

    @Test
    void execute_unauthorizedUser_throwsUnauthorized() {
        useCase = new SignContractUseCase(contractRepository, listingPort);
        Contract contract = offeredContract();
        when(contractRepository.findById(new ContractId("contract-1"))).thenReturn(Optional.of(contract));

        assertThatThrownBy(() -> useCase.execute(new SignContractCommand("contract-1", "stranger-99")))
                .isInstanceOf(UnauthorizedContractAccessException.class);

        verify(contractRepository, never()).save(any());
    }

    @Test
    void execute_wrongStatus_throwsIllegalArgument() {
        useCase = new SignContractUseCase(contractRepository, listingPort);
        Contract contract = offeredContract();
        // move to ACTIVE so status is wrong for signing
        contract.sign("buyer-1");
        contract.sign("seller-1");
        // now SIGNED → activate()
        contract.activate();
        when(contractRepository.findById(new ContractId("contract-1"))).thenReturn(Optional.of(contract));

        assertThatThrownBy(() -> useCase.execute(new SignContractCommand("contract-1", "buyer-1")))
                .isInstanceOf(IllegalArgumentException.class);

        verify(contractRepository, never()).save(any());
    }

    @Test
    void execute_buyerSignsFirst_partialSignAndSaves() {
        useCase = new SignContractUseCase(contractRepository, listingPort);
        Contract contract = offeredContract();
        when(contractRepository.findById(new ContractId("contract-1"))).thenReturn(Optional.of(contract));

        useCase.execute(new SignContractCommand("contract-1", "buyer-1"));

        assertThat(contract.getStatus()).isEqualTo(ContractStatus.OFFERED);
        assertThat(contract.getSignatories()).containsExactly("buyer-1");
        verify(contractRepository).save(contract);
        verify(listingPort, never()).closeListing(any());
    }

    @Test
    void execute_bothSign_statusSignedAndListingClosed() {
        useCase = new SignContractUseCase(contractRepository, listingPort);
        Contract contract = offeredContract();
        when(contractRepository.findById(new ContractId("contract-1"))).thenReturn(Optional.of(contract));

        useCase.execute(new SignContractCommand("contract-1", "buyer-1"));

        // reset mock for second call
        when(contractRepository.findById(new ContractId("contract-1"))).thenReturn(Optional.of(contract));
        useCase.execute(new SignContractCommand("contract-1", "seller-1"));

        assertThat(contract.getStatus()).isEqualTo(ContractStatus.SIGNED);
        verify(listingPort).closeListing("listing-1");
        verify(contractRepository, times(2)).save(contract);
    }

    @Test
    void execute_duplicateSign_throwsIllegalArgumentAndDoesNotSave() {
        useCase = new SignContractUseCase(contractRepository, listingPort);
        Contract contract = offeredContract();
        when(contractRepository.findById(new ContractId("contract-1"))).thenReturn(Optional.of(contract));

        // first sign ok
        useCase.execute(new SignContractCommand("contract-1", "buyer-1"));

        // second sign same user → should throw
        when(contractRepository.findById(new ContractId("contract-1"))).thenReturn(Optional.of(contract));
        assertThatThrownBy(() -> useCase.execute(new SignContractCommand("contract-1", "buyer-1")))
                .isInstanceOf(IllegalArgumentException.class);

        verify(contractRepository, times(1)).save(contract);
    }

    @Test
    void execute_signFromNegotiating_statusSignedWhenBothSign() {
        // NEGOTIATING is also a valid status for signing (not only OFFERED)
        useCase = new SignContractUseCase(contractRepository, listingPort);
        Contract contract = offeredContract();
        contract.counterOffer("buyer-1", TERMS); // move to NEGOTIATING
        when(contractRepository.findById(new ContractId("contract-1"))).thenReturn(Optional.of(contract));
        useCase.execute(new SignContractCommand("contract-1", "buyer-1"));

        when(contractRepository.findById(new ContractId("contract-1"))).thenReturn(Optional.of(contract));
        useCase.execute(new SignContractCommand("contract-1", "seller-1"));

        assertThat(contract.getStatus()).isEqualTo(ContractStatus.SIGNED);
        verify(listingPort).closeListing("listing-1");
    }

    @Test
    void execute_sellerSignsFirst_partialSignAndDoesNotCloseListing() {
        useCase = new SignContractUseCase(contractRepository, listingPort);
        Contract contract = offeredContract();
        when(contractRepository.findById(new ContractId("contract-1"))).thenReturn(Optional.of(contract));

        useCase.execute(new SignContractCommand("contract-1", "seller-1"));

        assertThat(contract.getSignatories()).containsExactly("seller-1");
        assertThat(contract.getStatus()).isEqualTo(ContractStatus.OFFERED);
        verify(listingPort, never()).closeListing(any());
    }
}
