package com.agricontract.product.domain.model;

import com.agricontract.product.domain.model.vo.*;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.*;

class ListingTest {

    @Test
    void create_initializesQuantityAvailableFromOfferedQuantity() {
        Listing listing = Listing.create(new ListingId("listing-1"), "seller-1", new ProductId("product-1"),
                "Ca phe Robusta", "https://cdn.test/cover.jpg", new Quantity(new BigDecimal("100"), "kg"),
                new Money(new BigDecimal("50000"), "VND"), LocalDate.of(2026, 12, 31));

        assertThat(listing.getQuantityAvailable()).isEqualByComparingTo("100");
    }

    @Test
    void reconstruct_legacyRow_keepsQuantityAvailableNull() {
        Listing listing = Listing.reconstruct(new ListingId("listing-1"), "seller-1", new ProductId("product-1"),
                "Ca phe Robusta", "", new Quantity(new BigDecimal("100"), "kg"), null,
                new Money(new BigDecimal("50000"), "VND"), LocalDate.of(2026, 12, 31), ListingStatus.ACTIVE);

        assertThat(listing.getQuantityAvailable()).isNull();
    }
}
