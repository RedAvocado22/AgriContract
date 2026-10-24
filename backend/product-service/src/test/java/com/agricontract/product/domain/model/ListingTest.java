package com.agricontract.product.domain.model;

import com.agricontract.product.domain.model.vo.*;
import com.agricontract.product.testsupport.GoodsFixtures;
import com.agricontract.product.testsupport.PlotFixtures;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static com.agricontract.product.domain.model.vo.ReservationRejection.*;
import static org.assertj.core.api.Assertions.*;

class ListingTest {

    private static final Instant UPDATED_AT = Instant.parse("2026-10-20T08:00:00.123456Z");
    private static final String TOKEN = UPDATED_AT.toString();

    private static Listing create(BigDecimal minOrderQuantity) {
        return Listing.create(new ListingId("listing-1"), "seller-1", new ProductId("product-1"),
                "Ca phe Robusta", "https://cdn.test/cover.jpg", new Quantity(new BigDecimal("100"), "kg"),
                minOrderQuantity, new Money(new BigDecimal("50000"), "VND"), LocalDate.of(2026, 12, 31),
                PlotFixtures.onePlot(), GoodsFixtures.coffeeGoods());
    }

    private static Listing reconstruct(BigDecimal available, BigDecimal min, ListingStatus status) {
        return Listing.reconstruct(new ListingId("listing-1"), "seller-1", new ProductId("product-1"),
                "Ca phe Robusta", "", new Quantity(new BigDecimal("100"), "kg"), available, min,
                new Money(new BigDecimal("50000"), "VND"), LocalDate.of(2026, 12, 31), status, UPDATED_AT,
                List.of(), ListingGoods.legacy());
    }

    @Test
    void create_initializesQuantityAvailableFromOfferedQuantity() {
        Listing listing = create(new BigDecimal("10"));

        assertThat(listing.getQuantityAvailable()).isEqualByComparingTo("100");
        assertThat(listing.getMinOrderQuantity()).isEqualByComparingTo("10");
        assertThat(listing.getVersionToken()).isNull(); // issued by persistence
    }

    @Test
    void create_minOrderQuantityOutOfRange_throws() {
        assertThatThrownBy(() -> create(new BigDecimal("100.001"))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> create(BigDecimal.ZERO)).isInstanceOf(IllegalArgumentException.class);
        assertThat(create(null).getMinOrderQuantity()).isNull();
    }

    @Test
    void reconstruct_legacyRow_keepsQuantityAvailableNull() {
        assertThat(reconstruct(null, null, ListingStatus.ACTIVE).getQuantityAvailable()).isNull();
    }

    @Test
    void versionToken_isCanonicalUtcUpdatedAt() {
        assertThat(reconstruct(BigDecimal.TEN, null, ListingStatus.ACTIVE).getVersionToken())
                .isEqualTo("2026-10-20T08:00:00.123456Z");
    }

    @Test
    void classifyRejectedReserve_quantityProblemsAreInsufficient() {
        BigDecimal thirty = new BigDecimal("30");

        assertThat(reconstruct(null, null, ListingStatus.ACTIVE).classifyRejectedReserve(thirty, TOKEN))
                .isEqualTo(INSUFFICIENT_QUANTITY);
        assertThat(reconstruct(new BigDecimal("20"), null, ListingStatus.ACTIVE).classifyRejectedReserve(thirty, TOKEN))
                .isEqualTo(INSUFFICIENT_QUANTITY);
        assertThat(reconstruct(new BigDecimal("60"), new BigDecimal("40"), ListingStatus.ACTIVE)
                .classifyRejectedReserve(thirty, TOKEN)).isEqualTo(INSUFFICIENT_QUANTITY);
        // drained to zero and closed: the losing buyer still sees "insufficient", not a state conflict
        assertThat(reconstruct(BigDecimal.ZERO, null, ListingStatus.CLOSED).classifyRejectedReserve(thirty, TOKEN))
                .isEqualTo(INSUFFICIENT_QUANTITY);
    }

    @Test
    void classifyRejectedReserve_enoughStockButNotActive_isStateConflict() {
        assertThat(reconstruct(new BigDecimal("60"), null, ListingStatus.CLOSED)
                .classifyRejectedReserve(new BigDecimal("30"), TOKEN)).isEqualTo(LISTING_STATE_CONFLICT);
        assertThat(reconstruct(new BigDecimal("60"), null, ListingStatus.EXPIRED)
                .classifyRejectedReserve(new BigDecimal("30"), "stale")).isEqualTo(LISTING_STATE_CONFLICT);
    }

    @Test
    void classifyRejectedReserve_enoughStockAndActive_isVersionMismatch() {
        assertThat(reconstruct(new BigDecimal("60"), null, ListingStatus.ACTIVE)
                .classifyRejectedReserve(new BigDecimal("30"), "stale-token")).isEqualTo(VERSION_MISMATCH);
    }
}
