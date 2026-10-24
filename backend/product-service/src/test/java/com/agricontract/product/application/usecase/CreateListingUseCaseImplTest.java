package com.agricontract.product.application.usecase;

import com.agricontract.product.application.dto.CoffeeQualitySpecDto;
import com.agricontract.product.application.dto.CreateListingRequest;
import com.agricontract.product.application.dto.ListingResponse;
import com.agricontract.product.common.exception.PlotNotEligibleException;
import com.agricontract.product.common.exception.PlotNotOwnedException;
import com.agricontract.product.domain.model.Category;
import com.agricontract.product.domain.model.Listing;
import com.agricontract.product.domain.model.PlotRegistryEntry;
import com.agricontract.product.domain.model.Product;
import com.agricontract.product.domain.model.vo.CategoryId;
import com.agricontract.product.domain.model.vo.CategoryStatus;
import com.agricontract.product.domain.model.vo.CoffeeType;
import com.agricontract.product.domain.model.vo.Commodity;
import com.agricontract.product.domain.model.vo.GeoVerificationStatus;
import com.agricontract.product.domain.model.vo.ListingGoods;
import com.agricontract.product.domain.model.vo.ListingId;
import com.agricontract.product.domain.model.vo.Money;
import com.agricontract.product.domain.model.vo.PlotSource;
import com.agricontract.product.domain.model.vo.ProductId;
import com.agricontract.product.domain.model.vo.Province;
import com.agricontract.product.domain.model.vo.Quantity;
import com.agricontract.product.domain.model.vo.RegistryEntryId;
import com.agricontract.product.domain.model.vo.VerificationLevel;
import com.agricontract.product.domain.repository.CategoryRepository;
import com.agricontract.product.domain.repository.ListingRepository;
import com.agricontract.product.domain.repository.PlotRegistryRepository;
import com.agricontract.product.domain.repository.ProductRepository;
import com.agricontract.product.testsupport.PlotFixtures;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CreateListingUseCaseImplTest {

    private static final String SELLER = "seller-1";

    @Mock private ListingRepository listingRepository;
    @Mock private ProductRepository productRepository;
    @Mock private PlotRegistryRepository plotRegistryRepository;
    @Mock private CategoryRepository categoryRepository;

    private CreateListingUseCase useCase;

    private static final CoffeeQualitySpecDto SPEC = new CoffeeQualitySpecDto(Commodity.COFFEE, CoffeeType.ROBUSTA,
            new BigDecimal("12.5"), new BigDecimal("0.5"), new BigDecimal("5"));

    @BeforeEach
    void setUp() {
        useCase = new CreateListingUseCaseImpl(listingRepository, productRepository, plotRegistryRepository,
                categoryRepository);
    }

    private void productExists() {
        productExistsIn(Commodity.COFFEE);
    }

    private void productExistsIn(Commodity commodity) {
        Product product = Product.create(new ProductId("product-1"), "Ca phe Robusta", "kg", "category-1",
                List.of("https://cdn.test/cover.jpg", "https://cdn.test/2.jpg"), 2026, "Nien vu 2026/27",
                List.of("4C (cam ket)"));
        when(listingRepository.findById(any())).thenReturn(Optional.empty());
        when(productRepository.findById(new ProductId("product-1"))).thenReturn(Optional.of(product));
        when(categoryRepository.findById(new CategoryId("category-1"))).thenReturn(Optional.of(
                Category.reconstruct(new CategoryId("category-1"), "Ca phe Robusta", "ca phe robusta",
                        CategoryStatus.APPROVED, null, "seller-1", "seller@test", commodity)));
    }

    private static CreateListingRequest request(List<String> registryEntryIds) {
        return request(registryEntryIds, SPEC);
    }

    private static CreateListingRequest request(List<String> registryEntryIds, CoffeeQualitySpecDto spec) {
        return new CreateListingRequest("listing-1", "product-1", new BigDecimal("100"), "kg", new BigDecimal("10"),
                new BigDecimal("50000"), "VND", LocalDate.now().plusDays(10), registryEntryIds,
                " Buon Ma Thuot, Dak Lak ", spec, "Bao day 60 kg", " ", LocalDate.of(2026, 12, 1), null);
    }

    private static PlotRegistryEntry plot(String id, String owner, GeoVerificationStatus status) {
        return PlotRegistryEntry.reconstruct(new RegistryEntryId(id), owner, "Ho " + id, PlotFixtures.point(),
                PlotSource.KML_IMPORT, null, VerificationLevel.SELF_DECLARED, Province.DAK_LAK, status, null,
                Instant.parse("2026-10-24T00:00:00Z"));
    }

    @Test
    void execute_happyPath_snapshotsProductCoverImageAndPlots() {
        productExists();
        when(plotRegistryRepository.findAllById(any())).thenReturn(List.of(
                plot("plot-1", SELLER, GeoVerificationStatus.UNVERIFIED),
                plot("plot-2", SELLER, GeoVerificationStatus.UNVERIFIED)));
        when(listingRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        ListingResponse response = useCase.execute(SELLER, request(List.of("plot-1", "plot-2")));

        assertThat(response.coverImageUrl()).isEqualTo("https://cdn.test/cover.jpg");
        assertThat(response.quantityAvailable()).isEqualByComparingTo("100");
        assertThat(response.minOrderQuantity()).isEqualByComparingTo("10");
        assertThat(response.registryEntryIds()).containsExactly("plot-1", "plot-2");

        ArgumentCaptor<Listing> saved = ArgumentCaptor.forClass(Listing.class);
        verify(listingRepository).save(saved.capture());
        assertThat(saved.getValue().getPlots()).extracting(p -> p.householdLabel())
                .containsExactly("Ho plot-1", "Ho plot-2");
        assertThat(saved.getValue().getPlots().get(0).geometry()).isEqualTo(PlotFixtures.point());
    }

    @Test
    void execute_copiesCategoryAndSeasonFromTheProduct_andTakesDeclaredGoodsFromTheRequest() {
        productExists();
        when(plotRegistryRepository.findAllById(any()))
                .thenReturn(List.of(plot("plot-1", SELLER, GeoVerificationStatus.UNVERIFIED)));
        when(listingRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        ListingResponse response = useCase.execute(SELLER, request(List.of("plot-1")));

        assertThat(response.categoryId()).isEqualTo("category-1");
        assertThat(response.commodity()).isEqualTo(Commodity.COFFEE);
        assertThat(response.cropYear()).isEqualTo(2026);
        assertThat(response.harvestSeason()).isEqualTo("Nien vu 2026/27");
        assertThat(response.certifications()).containsExactly("4C (cam ket)");
        assertThat(response.regionText()).isEqualTo("Buon Ma Thuot, Dak Lak");
        assertThat(response.declaredQualitySpec().type()).isEqualTo(CoffeeType.ROBUSTA);
        assertThat(response.declaredQualitySpec().commodity()).isEqualTo(Commodity.COFFEE);
        assertThat(response.declaredQualitySpec().moisturePercent()).isEqualByComparingTo("12.5");
        assertThat(response.legacyQualitySpecMissing()).isFalse();
        assertThat(response.packagingDeclaration()).isEqualTo("Bao day 60 kg");
        assertThat(response.warehouseLocation()).isNull(); // blank → not declared
        assertThat(response.estimatedAvailabilityDate()).isEqualTo(LocalDate.of(2026, 12, 1));
    }

    @Test
    void execute_categoryApprovedBeforeCommodityExisted_isRejectedAsNotCoffee() {
        productExistsIn(null);

        assertThatThrownBy(() -> useCase.execute(SELLER, request(List.of("plot-1"))))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("COFFEE");
        verify(listingRepository, never()).save(any());
        verifyNoInteractions(plotRegistryRepository);
    }

    @Test
    void execute_qualitySpecWithMoreThanTwoDecimals_isRejected() {
        productExists();
        when(plotRegistryRepository.findAllById(any()))
                .thenReturn(List.of(plot("plot-1", SELLER, GeoVerificationStatus.UNVERIFIED)));
        CoffeeQualitySpecDto precise = new CoffeeQualitySpecDto(Commodity.COFFEE, CoffeeType.ROBUSTA,
                new BigDecimal("12.555"), new BigDecimal("0.5"), new BigDecimal("5"));

        assertThatThrownBy(() -> useCase.execute(SELLER, request(List.of("plot-1"), precise)))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("2 decimals");
        verify(listingRepository, never()).save(any());
    }

    @Test
    void execute_anotherSellersPlot_isForbiddenAndNothingSaved() {
        productExists();
        when(plotRegistryRepository.findAllById(any()))
                .thenReturn(List.of(plot("plot-1", "seller-2", GeoVerificationStatus.CHECKED)));

        assertThatThrownBy(() -> useCase.execute(SELLER, request(List.of("plot-1"))))
                .isInstanceOf(PlotNotOwnedException.class);
        verify(listingRepository, never()).save(any());
    }

    @Test
    void execute_unknownPlot_getsTheSameAnswerAsAnotherSellersPlot() {
        productExists();
        when(plotRegistryRepository.findAllById(any())).thenReturn(List.of());

        assertThatThrownBy(() -> useCase.execute(SELLER, request(List.of("plot-404"))))
                .isInstanceOf(PlotNotOwnedException.class);
        verify(listingRepository, never()).save(any());
    }

    @Test
    void execute_plotStillPendingGeoCheck_isRejected() {
        productExists();
        when(plotRegistryRepository.findAllById(any())).thenReturn(List.of(
                plot("plot-1", SELLER, GeoVerificationStatus.CHECKED),
                plot("plot-2", SELLER, GeoVerificationStatus.PENDING)));

        assertThatThrownBy(() -> useCase.execute(SELLER, request(List.of("plot-1", "plot-2"))))
                .isInstanceOf(PlotNotEligibleException.class)
                .hasMessageContaining("plot-2");
        verify(listingRepository, never()).save(any());
    }

    @Test
    void execute_samePlotTwice_isRejected() {
        productExists();

        assertThatThrownBy(() -> useCase.execute(SELLER, request(List.of("plot-1", "plot-1"))))
                .isInstanceOf(IllegalArgumentException.class);
        verify(listingRepository, never()).save(any());
    }

    @Test
    void execute_replayWithDifferentPlotsOrQuality_isConflict() {
        // what request(...) declares, as it was stored on the first call
        ListingGoods stored = new ListingGoods(new CategoryId("category-1"), Commodity.COFFEE, 2026, "Nien vu 2026/27",
                List.of("4C (cam ket)"), "Buon Ma Thuot, Dak Lak", SPEC.toDomain(), "Bao day 60 kg", null,
                LocalDate.of(2026, 12, 1), null);
        Listing existing = Listing.create(new ListingId("listing-1"), SELLER,
                new ProductId("product-1"), "Ca phe Robusta", "https://cdn.test/cover.jpg",
                new Quantity(new BigDecimal("100"), "kg"),
                new BigDecimal("10"), new Money(new BigDecimal("50000"), "VND"),
                LocalDate.now().plusDays(10), PlotFixtures.onePlot(), stored);
        when(listingRepository.findById(any())).thenReturn(Optional.of(existing));

        // 12.5 replayed as 12.50 is the same declared value
        CoffeeQualitySpecDto sameSpec = new CoffeeQualitySpecDto(Commodity.COFFEE, CoffeeType.ROBUSTA,
                new BigDecimal("12.50"), new BigDecimal("0.50"), new BigDecimal("5.00"));
        assertThat(useCase.execute(SELLER, request(List.of(PlotFixtures.PLOT_ID), sameSpec)).listingId())
                .isEqualTo("listing-1");
        assertThatThrownBy(() -> useCase.execute(SELLER, request(List.of("plot-2"))))
                .isInstanceOf(IllegalStateException.class);
        CoffeeQualitySpecDto arabica = new CoffeeQualitySpecDto(Commodity.COFFEE, CoffeeType.ARABICA,
                new BigDecimal("12.5"), new BigDecimal("0.5"), new BigDecimal("5"));
        assertThatThrownBy(() -> useCase.execute(SELLER, request(List.of(PlotFixtures.PLOT_ID), arabica)))
                .isInstanceOf(IllegalStateException.class);
        verify(listingRepository, never()).save(any());
    }
}
