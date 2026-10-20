package com.agricontract.product.domain.model;

import com.agricontract.product.domain.model.vo.ContractId;
import com.agricontract.product.domain.model.vo.ListingId;
import com.agricontract.product.domain.model.vo.ReservationStatus;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static com.agricontract.product.domain.model.vo.ReservationStatus.*;
import static org.assertj.core.api.Assertions.*;

class ReservationTest {

    private static final ContractId CONTRACT_ID = new ContractId("contract-1");
    private static final ListingId LISTING_ID = new ListingId("listing-1");

    @Test
    void create_startsReserved() {
        Reservation reservation = Reservation.create(CONTRACT_ID, LISTING_ID, new BigDecimal("40"));

        assertThat(reservation.getContractId()).isEqualTo(CONTRACT_ID);
        assertThat(reservation.getListingId()).isEqualTo(LISTING_ID);
        assertThat(reservation.getReservedQuantity()).isEqualByComparingTo("40");
        assertThat(reservation.getStatus()).isEqualTo(RESERVED);
    }

    @Test
    void create_nonPositiveQuantity_throws() {
        assertThatThrownBy(() -> Reservation.create(CONTRACT_ID, LISTING_ID, BigDecimal.ZERO))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Reservation.create(CONTRACT_ID, LISTING_ID, new BigDecimal("-1")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Reservation.create(CONTRACT_ID, LISTING_ID, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void create_missingIdentity_throws() {
        assertThatThrownBy(() -> Reservation.create(null, LISTING_ID, BigDecimal.ONE))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Reservation.create(CONTRACT_ID, null, BigDecimal.ONE))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ContractId(" ")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void isSameRequest_comparesListingAndQuantityByValue() {
        Reservation reservation = Reservation.create(CONTRACT_ID, LISTING_ID, new BigDecimal("40"));

        assertThat(reservation.isSameRequest(LISTING_ID, new BigDecimal("40.000"))).isTrue();
        assertThat(reservation.isSameRequest(LISTING_ID, new BigDecimal("41"))).isFalse();
        assertThat(reservation.isSameRequest(new ListingId("listing-2"), new BigDecimal("40"))).isFalse();
    }

    @Test
    void status_allowsOnlyCanonicalTransitions() {
        assertThat(RESERVED.canTransitionTo(SIGNED_RELEASABLE)).isTrue();
        assertThat(RESERVED.canTransitionTo(RELEASED)).isTrue();
        assertThat(SIGNED_RELEASABLE.canTransitionTo(COMMITTED)).isTrue();
        assertThat(SIGNED_RELEASABLE.canTransitionTo(RELEASED)).isTrue();

        // signed is not a hard commit, but reserved can never skip straight to committed
        assertThat(RESERVED.canTransitionTo(COMMITTED)).isFalse();
        for (ReservationStatus target : ReservationStatus.values()) {
            assertThat(COMMITTED.canTransitionTo(target)).isFalse();
            assertThat(RELEASED.canTransitionTo(target)).isFalse();
        }
    }
}
