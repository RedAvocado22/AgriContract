package com.agricontract.product.application.usecase;

import com.agricontract.product.application.dto.ReservationResult;
import com.agricontract.product.application.dto.ReserveListingQuantityRequest;
import com.agricontract.product.common.exception.WriteConflictException;
import com.agricontract.product.domain.model.Listing;
import com.agricontract.product.domain.model.Reservation;
import com.agricontract.product.domain.model.vo.ContractId;
import com.agricontract.product.domain.model.vo.ListingId;
import com.agricontract.product.domain.model.vo.Money;
import com.agricontract.product.domain.model.vo.ProductId;
import com.agricontract.product.domain.model.vo.Quantity;
import com.agricontract.product.domain.repository.ListingRepository;
import com.agricontract.product.domain.repository.ReservationRepository;
import com.agricontract.product.testsupport.DirectTransactionRunner;
import com.agricontract.product.testsupport.GoodsFixtures;
import com.agricontract.product.testsupport.PlotFixtures;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReserveListingQuantityUseCaseImplTest {

    private static final ListingId LISTING_ID = new ListingId("listing-1");
    private static final ContractId CONTRACT_ID = new ContractId("contract-1");
    private static final BigDecimal QUANTITY = new BigDecimal("40");

    @Mock private ListingRepository listingRepository;
    @Mock private ReservationRepository reservationRepository;

    private ReserveListingQuantityUseCase useCase;
    private Listing listing;

    @BeforeEach
    void setUp() {
        useCase = new ReserveListingQuantityUseCaseImpl(listingRepository, reservationRepository,
                new DirectTransactionRunner());
        listing = Listing.create(LISTING_ID, "seller-1", new ProductId("product-1"), "Ca phe Robusta",
                "https://cdn.test/cover.jpg", new Quantity(new BigDecimal("100"), "kg"), new BigDecimal("10"),
                new Money(new BigDecimal("50000"), "VND"), LocalDate.of(2026, 12, 31), PlotFixtures.onePlot(),
                GoodsFixtures.coffeeGoods());
        when(listingRepository.findById(LISTING_ID)).thenReturn(Optional.of(listing));
        when(listingRepository.reserveAvailable(LISTING_ID, QUANTITY, "token")).thenReturn(true);
    }

    @Test
    void concurrentReserveWithSameContractId_isReplayedNotCreated() {
        Reservation winner = Reservation.create(CONTRACT_ID, LISTING_ID, QUANTITY);
        when(reservationRepository.findByContractId(CONTRACT_ID))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(winner));
        when(reservationRepository.create(any())).thenThrow(new WriteConflictException("duplicate", null));

        ReservationResult result = useCase.execute(request());

        assertThat(result.created()).isFalse();
    }

    @Test
    void writeConflictWithoutAWinningReservation_propagates() {
        when(reservationRepository.findByContractId(CONTRACT_ID)).thenReturn(Optional.empty());
        WriteConflictException conflict = new WriteConflictException("fk", null);
        when(reservationRepository.create(any())).thenThrow(conflict);

        assertThatThrownBy(() -> useCase.execute(request())).isSameAs(conflict);
    }

    private static ReserveListingQuantityRequest request() {
        return new ReserveListingQuantityRequest(CONTRACT_ID.value(), LISTING_ID.value(), QUANTITY, "token");
    }
}
