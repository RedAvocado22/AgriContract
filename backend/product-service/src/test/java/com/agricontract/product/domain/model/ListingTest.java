package com.agricontract.product.domain.model;

import com.agricontract.product.domain.model.vo.*;
import com.agricontract.product.testsupport.GoodsFixtures;
import com.agricontract.product.testsupport.PlotFixtures;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static com.agricontract.product.domain.model.vo.ReservationRejection.*;
import static org.assertj.core.api.Assertions.*;

class ListingTest {

    private static final Instant UPDATED_AT = Instant.parse("2026-10-20T08:00:00.123456Z");
    private static final String TOKEN = UPDATED_AT.toString();

    private static Listing create(BigDecimal minOrderQuantity) {
        return Listing.create(new ListingId("listing-1"), "seller-1", new ProductId("product-1"),
                "Ca phe Robusta", "https://cdn.test/cover.jpg", new BigDecimal("100"),
                minOrderQuantity, new BigDecimal("50000.00"), PlotFixtures.onePlot(), GoodsFixtures.coffeeGoods());
    }

    private static Listing reconstruct(BigDecimal available, BigDecimal min, ListingStatus status) {
        return Listing.reconstruct(new ListingId("listing-1"), "seller-1", new ProductId("product-1"),
                "Ca phe Robusta", "", new BigDecimal("100"), available, min,
                new BigDecimal("50000.00"), status, null, UPDATED_AT,
                List.of(), ListingGoods.legacy());
    }

    // a Phase 2 listing offering 50 t (kg below), in the given inventory state
    private static Listing coffee(BigDecimal available, ListingStatus status, CloseReason closeReason) {
        return Listing.reconstruct(new ListingId("listing-1"), "seller-1", new ProductId("product-1"),
                "Ca phe Robusta", "", new BigDecimal("50000"), available, new BigDecimal("1000"),
                new BigDecimal("50000.00"), status, closeReason, UPDATED_AT,
                PlotFixtures.onePlot(), GoodsFixtures.coffeeGoods());
    }

    private static void edit(Listing listing, String offered, String reserved) {
        listing.edit(new BigDecimal(offered), new BigDecimal("1000"), new BigDecimal("52000.00"),
                PlotFixtures.onePlot(), GoodsFixtures.coffeeGoods(), new BigDecimal(reserved));
    }

    @Test
    void create_initializesQuantityAvailableFromOfferedQuantity() {
        Listing listing = create(new BigDecimal("10"));

        assertThat(listing.getQuantityAvailable()).isEqualByComparingTo("100");
        assertThat(listing.getMinOrderQuantity()).isEqualByComparingTo("10");
        assertThat(listing.getStatus()).isEqualTo(ListingStatus.AVAILABLE);
        assertThat(listing.getVersionToken()).isNull(); // issued by persistence
    }

    @Test
    void create_minOrderQuantityMissingOrOutOfRange_throws() {
        assertThatThrownBy(() -> create(new BigDecimal("100.001"))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> create(BigDecimal.ZERO)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> create(null)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void reconstruct_legacyRow_keepsQuantityAvailableNull() {
        assertThat(reconstruct(null, null, ListingStatus.AVAILABLE).getQuantityAvailable()).isNull();
    }

    @Test
    void versionToken_isCanonicalUtcUpdatedAt() {
        assertThat(reconstruct(BigDecimal.TEN, null, ListingStatus.AVAILABLE).getVersionToken())
                .isEqualTo("2026-10-20T08:00:00.123456Z");
    }

    @Test
    void classifyRejectedReserve_quantityProblemsAreInsufficient() {
        BigDecimal thirty = new BigDecimal("30");

        assertThat(reconstruct(null, null, ListingStatus.AVAILABLE).classifyRejectedReserve(thirty, TOKEN))
                .isEqualTo(INSUFFICIENT_QUANTITY);
        assertThat(reconstruct(new BigDecimal("20"), null, ListingStatus.PARTIALLY_COMMITTED)
                .classifyRejectedReserve(thirty, TOKEN)).isEqualTo(INSUFFICIENT_QUANTITY);
        assertThat(reconstruct(new BigDecimal("60"), new BigDecimal("40"), ListingStatus.AVAILABLE)
                .classifyRejectedReserve(thirty, TOKEN)).isEqualTo(INSUFFICIENT_QUANTITY);
        // drained to zero and closed: the losing buyer still sees "insufficient", not a state conflict
        assertThat(reconstruct(BigDecimal.ZERO, null, ListingStatus.CLOSED).classifyRejectedReserve(thirty, TOKEN))
                .isEqualTo(INSUFFICIENT_QUANTITY);
    }

    @Test
    void classifyRejectedReserve_enoughStockButNotOpen_isStateConflict() {
        assertThat(reconstruct(new BigDecimal("60"), null, ListingStatus.CLOSED)
                .classifyRejectedReserve(new BigDecimal("30"), TOKEN)).isEqualTo(LISTING_STATE_CONFLICT);
        assertThat(reconstruct(new BigDecimal("60"), null, ListingStatus.PAUSED)
                .classifyRejectedReserve(new BigDecimal("30"), "stale")).isEqualTo(LISTING_STATE_CONFLICT);
    }

    @Test
    void classifyRejectedReserve_enoughStockAndOpen_isVersionMismatch() {
        assertThat(reconstruct(new BigDecimal("60"), null, ListingStatus.AVAILABLE)
                .classifyRejectedReserve(new BigDecimal("30"), "stale-token")).isEqualTo(VERSION_MISMATCH);
        assertThat(reconstruct(new BigDecimal("60"), null, ListingStatus.PARTIALLY_COMMITTED)
                .classifyRejectedReserve(new BigDecimal("30"), "stale-token")).isEqualTo(VERSION_MISMATCH);
    }

    @Test
    void edit_raisingTheOffer_keepsTheReservedPartOut() {
        // D48 example: offered 50 t, reserved 20 t, edit to 60 t -> 40 t available
        Listing listing = coffee(new BigDecimal("30000"), ListingStatus.PARTIALLY_COMMITTED, null);

        edit(listing, "60000", "20000");

        assertThat(listing.getOfferedQuantity()).isEqualByComparingTo("60000");
        assertThat(listing.getQuantityAvailable()).isEqualByComparingTo("40000");
        assertThat(listing.getStatus()).isEqualTo(ListingStatus.PARTIALLY_COMMITTED);
        assertThat(listing.getAskingUnitPrice()).isEqualByComparingTo("52000.00");
    }

    @Test
    void edit_loweringTheOfferToTheReservedTotal_closesAsSellerClose() {
        Listing listing = coffee(new BigDecimal("30000"), ListingStatus.PARTIALLY_COMMITTED, null);

        edit(listing, "20000", "20000");

        assertThat(listing.getQuantityAvailable()).isEqualByComparingTo("0");
        assertThat(listing.getStatus()).isEqualTo(ListingStatus.CLOSED);
        assertThat(listing.getCloseReason()).isEqualTo(CloseReason.SELLER_CLOSED);
    }

    @Test
    void edit_belowTheReservedTotal_isRefusedAndChangesNothing() {
        Listing listing = coffee(new BigDecimal("30000"), ListingStatus.PARTIALLY_COMMITTED, null);

        assertThatThrownBy(() -> edit(listing, "19999", "20000")).isInstanceOf(IllegalArgumentException.class);

        assertThat(listing.getOfferedQuantity()).isEqualByComparingTo("50000");
        assertThat(listing.getQuantityAvailable()).isEqualByComparingTo("30000");
        assertThat(listing.getStatus()).isEqualTo(ListingStatus.PARTIALLY_COMMITTED);
    }

    @Test
    void edit_closedListingGettingStockAgain_isRepublished() {
        Listing exhausted = coffee(BigDecimal.ZERO, ListingStatus.CLOSED, CloseReason.INVENTORY_EXHAUSTED);
        edit(exhausted, "70000", "50000");
        assertThat(exhausted.getStatus()).isEqualTo(ListingStatus.PARTIALLY_COMMITTED);
        assertThat(exhausted.getCloseReason()).isNull();

        Listing expired = coffee(new BigDecimal("50000"), ListingStatus.CLOSED, CloseReason.LEGACY_EXPIRED);
        edit(expired, "50000", "0");
        assertThat(expired.getStatus()).isEqualTo(ListingStatus.AVAILABLE);
        assertThat(expired.getQuantityAvailable()).isEqualByComparingTo("50000");
        assertThat(expired.getCloseReason()).isNull();
    }

    @Test
    void edit_legacyNullCounterWithoutReservation_isInitializedFromTheOffer() {
        Listing listing = coffee(null, ListingStatus.AVAILABLE, null);

        edit(listing, "45000", "0");

        assertThat(listing.getQuantityAvailable()).isEqualByComparingTo("45000");
        assertThat(listing.getStatus()).isEqualTo(ListingStatus.AVAILABLE);
    }

    @Test
    void edit_legacyListingWithoutQualitySpec_isRefused() {
        Listing legacy = reconstruct(new BigDecimal("100"), null, ListingStatus.AVAILABLE);

        assertThat(legacy.isEditable()).isFalse();
        assertThatThrownBy(() -> edit(legacy, "100", "0")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void edit_minOrderAboveTheNewOffer_isRefused() {
        Listing listing = coffee(new BigDecimal("50000"), ListingStatus.AVAILABLE, null);

        assertThatThrownBy(() -> edit(listing, "999", "0")).isInstanceOf(IllegalArgumentException.class);
    }
}
