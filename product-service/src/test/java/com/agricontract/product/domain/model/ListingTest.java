package com.agricontract.product.domain.model;

import com.agricontract.product.domain.model.vo.ListingStatus;
import com.agricontract.product.domain.model.vo.Money;
import com.agricontract.product.domain.model.vo.Quantity;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

class ListingTest {

    private Listing buildListing() {
        return Listing.create(
                "listing-uuid-001",
                "seller-sub-123",
                "product-uuid-001",
                "Gao ST25",
                new Quantity(new BigDecimal("100.000"), "kg"),
                new Money(new BigDecimal("15000.00"), "VND"),
                LocalDate.now().plusDays(30)
        );
    }

    @Test
    void create_shouldSetStatusToActive() {
        Listing listing = buildListing();
        assertEquals(ListingStatus.ACTIVE, listing.getStatus());
    }

    @Test
    void close_whenActive_shouldTransitionToClosed() {
        Listing listing = buildListing();
        listing.close();
        assertEquals(ListingStatus.CLOSED, listing.getStatus());
    }

    @Test
    void close_whenAlreadyClosed_shouldThrowIllegalStateException() {
        Listing listing = buildListing();
        listing.close();
        assertThrows(IllegalStateException.class, listing::close);
    }

    @Test
    void close_whenExpired_shouldThrowIllegalStateException() {
        Listing listing = buildListing();
        listing.expire();
        assertThrows(IllegalStateException.class, listing::close);
    }

    @Test
    void expire_whenActive_shouldTransitionToExpired() {
        Listing listing = buildListing();
        listing.expire();
        assertEquals(ListingStatus.EXPIRED, listing.getStatus());
    }
}
