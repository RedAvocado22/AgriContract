package com.agricontract.contract.application.usecase;

import com.agricontract.contract.application.dto.ContractResponse;
import com.agricontract.contract.application.dto.CreateContractCommand;
import com.agricontract.contract.application.dto.ListingResponse;
import com.agricontract.contract.application.dto.UserInfo;
import com.agricontract.contract.application.port.ListingPort;
import com.agricontract.contract.application.port.UserPort;
import com.agricontract.contract.domain.model.Contract;
import com.agricontract.contract.domain.model.vo.*;
import com.agricontract.contract.domain.repository.ContractRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CreateContractUseCaseTest {

    @Mock private ContractRepository contractRepository;
    @Mock private ListingPort listingPort;
    @Mock private UserPort userPort;

    private CreateContractUseCase useCase;

    private static final ContractTerms TERMS = new ContractTerms(
            new Quantity(new BigDecimal("100"), "kg"),
            new Money(new BigDecimal("500000"), "VND"),
            LocalDate.now().plusDays(30),
            new BigDecimal("0.30"),
            new BigDecimal("0.10"),
            "Grade A"
    );

    private static final ListingResponse ACTIVE_LISTING = new ListingResponse(
            "listing-1", "seller-1", "product-1", "Rice",
            new BigDecimal("100"), "kg", new BigDecimal("400000"), "VND",
            LocalDate.now().plusDays(30), "ACTIVE"
    );

    private static final UserInfo BUYER_INFO = new UserInfo("buyer-1", "Buyer Corp", "buyer@agricontract.test", "BUYER");
    private static final UserInfo SELLER_INFO = new UserInfo("seller-1", "Seller Corp", "seller@agricontract.test", "SELLER");

    @Test
    void execute_listingNotActive_throwsIllegalArgument() {
        useCase = new CreateContractUseCase(contractRepository, listingPort, userPort);
        ListingResponse closedListing = new ListingResponse(
                "listing-1", "seller-1", "product-1", "Rice",
                new BigDecimal("100"), "kg", new BigDecimal("400000"), "VND",
                LocalDate.now().plusDays(30), "CLOSED"
        );
        when(listingPort.getListing("listing-1")).thenReturn(closedListing);

        assertThatThrownBy(() -> useCase.execute(
                new CreateContractCommand(null, "buyer-1", "listing-1", TERMS)))
                .isInstanceOf(IllegalArgumentException.class);

        verify(contractRepository, never()).save(any());
    }

    @Test
    void execute_happyPath_createsOfferedContractAndSaves() {
        useCase = new CreateContractUseCase(contractRepository, listingPort, userPort);
        when(listingPort.getListing("listing-1")).thenReturn(ACTIVE_LISTING);
        when(userPort.getUser("buyer-1")).thenReturn(BUYER_INFO);
        when(userPort.getUser("seller-1")).thenReturn(SELLER_INFO);

        ContractResponse response = useCase.execute(
                new CreateContractCommand(null, "buyer-1", "listing-1", TERMS));

        ArgumentCaptor<Contract> captor = ArgumentCaptor.forClass(Contract.class);
        verify(contractRepository).save(captor.capture());

        Contract saved = captor.getValue();
        assertThat(saved.getStatus()).isEqualTo(ContractStatus.OFFERED);
        assertThat(saved.getBuyerId()).isEqualTo("buyer-1");
        assertThat(saved.getSellerId()).isEqualTo("seller-1");
        assertThat(saved.getProductName()).isEqualTo("Rice");

        assertThat(response.status()).isEqualTo("OFFERED");
        assertThat(response.buyerId()).isEqualTo("buyer-1");
    }

    @Test
    void execute_withExplicitContractId_usesProvidedId() {
        useCase = new CreateContractUseCase(contractRepository, listingPort, userPort);
        when(listingPort.getListing("listing-1")).thenReturn(ACTIVE_LISTING);
        when(userPort.getUser("buyer-1")).thenReturn(BUYER_INFO);
        when(userPort.getUser("seller-1")).thenReturn(SELLER_INFO);

        useCase.execute(new CreateContractCommand("fixed-id", "buyer-1", "listing-1", TERMS));

        ArgumentCaptor<Contract> captor = ArgumentCaptor.forClass(Contract.class);
        verify(contractRepository).save(captor.capture());
        assertThat(captor.getValue().getContractId().value()).isEqualTo("fixed-id");
    }

    @Test
    void execute_withNullContractId_generatesUuid() {
        useCase = new CreateContractUseCase(contractRepository, listingPort, userPort);
        when(listingPort.getListing("listing-1")).thenReturn(ACTIVE_LISTING);
        when(userPort.getUser("buyer-1")).thenReturn(BUYER_INFO);
        when(userPort.getUser("seller-1")).thenReturn(SELLER_INFO);

        useCase.execute(new CreateContractCommand(null, "buyer-1", "listing-1", TERMS));

        ArgumentCaptor<Contract> captor = ArgumentCaptor.forClass(Contract.class);
        verify(contractRepository).save(captor.capture());
        assertThat(captor.getValue().getContractId().value()).isNotBlank();
    }

    @Test
    void execute_snapshotsOrgNamesAndEmailsFromFeign() {
        // sellerId comes from listing.sellerId(), NOT from command
        // orgName/email come from UserPort responses — must be snapshotted correctly
        useCase = new CreateContractUseCase(contractRepository, listingPort, userPort);
        when(listingPort.getListing("listing-1")).thenReturn(ACTIVE_LISTING);
        when(userPort.getUser("buyer-1")).thenReturn(BUYER_INFO);
        when(userPort.getUser("seller-1")).thenReturn(SELLER_INFO);

        useCase.execute(new CreateContractCommand(null, "buyer-1", "listing-1", TERMS));

        ArgumentCaptor<Contract> captor = ArgumentCaptor.forClass(Contract.class);
        verify(contractRepository).save(captor.capture());

        Contract saved = captor.getValue();
        assertThat(saved.getBuyerOrgName()).isEqualTo("Buyer Corp");
        assertThat(saved.getSellerOrgName()).isEqualTo("Seller Corp");
        assertThat(saved.getBuyerEmail()).isEqualTo("buyer@agricontract.test");
        assertThat(saved.getSellerEmail()).isEqualTo("seller@agricontract.test");
        assertThat(saved.getSellerId()).isEqualTo("seller-1");  // from listing, not command
    }
}
