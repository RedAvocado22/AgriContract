package com.agricontract.product.application.usecase;

import com.agricontract.product.application.dto.ListingContractSnapshotResponse;
import com.agricontract.product.common.exception.ListingNotFoundException;
import com.agricontract.product.common.exception.ReservationConflictException;
import com.agricontract.product.domain.model.Category;
import com.agricontract.product.domain.model.Listing;
import com.agricontract.product.domain.model.vo.*;
import com.agricontract.product.domain.repository.CategoryRepository;
import com.agricontract.product.domain.repository.ListingRepository;
import com.agricontract.product.infrastructure.contract.FrozenApiSchemas;
import com.agricontract.product.testsupport.GoodsFixtures;
import com.agricontract.product.testsupport.PlotFixtures;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static com.agricontract.product.common.exception.ReservationConflictException.LISTING_STATE_CONFLICT;
import static com.agricontract.product.common.exception.ReservationConflictException.LISTING_VERSION_MISMATCH;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class GetListingContractSnapshotUseCaseImplTest {

    private static final String LISTING_ID = "5b0c7c0e-6f2a-4d39-9a51-2d7a1f3e8c11";
    private static final String PRODUCT_ID = "0e8f3c55-1b7d-4c2e-8a9f-4f6d2b1a7e22";
    private static final String CATEGORY_ID = "9a1d2e3f-4b5c-4d6e-8f70-1a2b3c4d5e33";
    private static final String PLOT_A = "1f2e3d4c-5b6a-4978-8695-a4b3c2d1e044";
    private static final String PLOT_B = "2a3b4c5d-6e7f-4a8b-9c0d-1e2f3a4b5c55";
    private static final Instant UPDATED_AT = Instant.parse("2026-11-07T08:15:30.123456Z");
    private static final String TOKEN = UPDATED_AT.toString();

    @Mock private ListingRepository listingRepository;
    @Mock private CategoryRepository categoryRepository;
    @InjectMocks private GetListingContractSnapshotUseCaseImpl useCase;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void category() {
        lenient().when(categoryRepository.findById(new CategoryId(CATEGORY_ID)))
                .thenReturn(Optional.of(Category.reconstruct(new CategoryId(CATEGORY_ID), "Ca phe nhan",
                        "ca phe nhan", CategoryStatus.APPROVED, null, "admin", "admin@test.com", Commodity.COFFEE)));
    }

    @Test
    void currentToken_returnsTheListingCopies_matchingBothFrozenSchemas() throws Exception {
        givenListing(coffeeGoods(2026, "Nien vu 2026/27"),
                List.of(plot(PLOT_B, "Ho Tran Thi B"), plot(PLOT_A, "Ho Nguyen Van A")));

        ListingContractSnapshotResponse snapshot = useCase.execute(LISTING_ID, TOKEN);

        assertThat(snapshot.commodity()).isEqualTo(Commodity.COFFEE);
        assertThat(snapshot.listingId()).isEqualTo(LISTING_ID);
        assertThat(snapshot.productId()).isEqualTo(PRODUCT_ID);
        assertThat(snapshot.categoryId()).isEqualTo(CATEGORY_ID);
        assertThat(snapshot.categoryName()).isEqualTo("Ca phe nhan");
        assertThat(snapshot.productName()).isEqualTo("Ca phe Robusta");
        assertThat(snapshot.unitOfMeasure()).isEqualTo("KG");
        assertThat(snapshot.cropYear()).isEqualTo(2026);
        assertThat(snapshot.harvestSeason()).isEqualTo("Nien vu 2026/27");
        assertThat(snapshot.originSnapshot().regionText()).isEqualTo("Buon Ma Thuot, Dak Lak");
        // ordered by householdLabel, whatever order the listing holds them in
        assertThat(snapshot.originSnapshot().plotRefs())
                .extracting(ListingContractSnapshotResponse.PlotRef::registryEntryId)
                .containsExactly(PLOT_A, PLOT_B);
        ListingContractSnapshotResponse.PlotRef first = snapshot.originSnapshot().plotRefs().get(0);
        assertThat(first.householdLabel()).isEqualTo("Ho Nguyen Van A");
        assertThat(first.geometryType()).isEqualTo(GeometryType.POINT);
        assertThat(first.geometryHash()).isEqualTo(PlotFixtures.point().geometryHash());
        assertThat(first.verificationLevel()).isEqualTo(VerificationLevel.SELF_DECLARED);

        JsonNode body = objectMapper.valueToTree(snapshot);
        FrozenApiSchemas.assertValid(FrozenApiSchemas.SUPPLEMENTARY_API, "ListingContractSnapshot", body);
        // contract-service stores the body unchanged as goodsTerms
        FrozenApiSchemas.assertValid(FrozenApiSchemas.GOLDEN_FLOW_API, "GoodsTermsSnapshot", body);
    }

    @Test
    void nullSeasonData_isSentAsExplicitNull() throws Exception {
        givenListing(coffeeGoods(null, null), List.of(plot(PLOT_A, "Ho Nguyen Van A")));

        JsonNode body = objectMapper.valueToTree(useCase.execute(LISTING_ID, TOKEN));

        assertThat(body.has("cropYear")).isTrue();
        assertThat(body.get("cropYear").isNull()).isTrue();
        assertThat(body.has("harvestSeason")).isTrue();
        FrozenApiSchemas.assertValid(FrozenApiSchemas.GOLDEN_FLOW_API, "GoodsTermsSnapshot", body);
    }

    @Test
    void staleToken_is409VersionMismatch_beforeAnythingElseIsRead() {
        givenListing(coffeeGoods(2026, "Nien vu 2026/27"), List.of(plot(PLOT_A, "Ho Nguyen Van A")));

        assertConflict(() -> useCase.execute(LISTING_ID, "2026-11-07T08:15:30.123455Z"), LISTING_VERSION_MISMATCH);
        verifyNoInteractions(categoryRepository);
    }

    @Test
    void legacyListing_withAnyToken_isStillAVersionCheckFirst() {
        givenListing(ListingGoods.legacy(), List.of());

        assertConflict(() -> useCase.execute(LISTING_ID, "other"), LISTING_VERSION_MISMATCH);
    }

    @Test
    void legacyListingWithoutPlotsOrGoods_is409StateConflict() {
        givenListing(ListingGoods.legacy(), List.of());

        assertConflict(() -> useCase.execute(LISTING_ID, TOKEN), LISTING_STATE_CONFLICT);
    }

    @Test
    void coffeeGoodsWithoutPlot_is409StateConflict() {
        givenListing(coffeeGoods(2026, "Nien vu 2026/27"), List.of());

        assertConflict(() -> useCase.execute(LISTING_ID, TOKEN), LISTING_STATE_CONFLICT);
    }

    @Test
    void missingCategory_is409StateConflict() {
        givenListing(coffeeGoods(2026, "Nien vu 2026/27"), List.of(plot(PLOT_A, "Ho Nguyen Van A")));
        when(categoryRepository.findById(any())).thenReturn(Optional.empty());

        assertConflict(() -> useCase.execute(LISTING_ID, TOKEN), LISTING_STATE_CONFLICT);
    }

    @Test
    void unknownListing_isNotFound() {
        when(listingRepository.findById(new ListingId(LISTING_ID))).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.execute(LISTING_ID, TOKEN)).isInstanceOf(ListingNotFoundException.class);
    }

    @Test
    void blankToken_isRejectedWithoutReading() {
        assertThatThrownBy(() -> useCase.execute(LISTING_ID, " ")).isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(listingRepository, categoryRepository);
    }

    private void givenListing(ListingGoods goods, List<ListingPlot> plots) {
        Listing listing = Listing.reconstruct(new ListingId(LISTING_ID), "seller-1", new ProductId(PRODUCT_ID),
                "Ca phe Robusta", null, new Quantity(new BigDecimal("100"), "kg"), new BigDecimal("100"), null,
                new Money(new BigDecimal("50000"), "VND"), LocalDate.of(2026, 12, 31), ListingStatus.ACTIVE,
                UPDATED_AT, plots, goods);
        when(listingRepository.findById(new ListingId(LISTING_ID))).thenReturn(Optional.of(listing));
    }

    private static ListingGoods coffeeGoods(Integer cropYear, String harvestSeason) {
        ListingGoods base = GoodsFixtures.coffeeGoods();
        return new ListingGoods(new CategoryId(CATEGORY_ID), Commodity.COFFEE, cropYear, harvestSeason,
                base.certifications(), base.regionText(), base.declaredQualitySpec(), base.packagingDeclaration(),
                base.warehouseLocation(), base.estimatedAvailabilityDate(), null);
    }

    private static ListingPlot plot(String registryEntryId, String householdLabel) {
        return new ListingPlot(new RegistryEntryId(registryEntryId), householdLabel, PlotFixtures.point(),
                VerificationLevel.SELF_DECLARED);
    }

    private static void assertConflict(org.assertj.core.api.ThrowableAssert.ThrowingCallable call, String code) {
        assertThatThrownBy(call).isInstanceOfSatisfying(ReservationConflictException.class,
                e -> assertThat(e.getCode()).isEqualTo(code));
    }
}
