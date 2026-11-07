package com.agricontract.product.application.usecase;

import com.agricontract.product.application.dto.ListingContractSnapshotResponse;
import com.agricontract.product.common.exception.ReservationConflictException;
import com.agricontract.product.domain.model.Category;
import com.agricontract.product.domain.model.Listing;
import com.agricontract.product.domain.model.vo.*;
import com.agricontract.product.domain.repository.CategoryRepository;
import com.agricontract.product.domain.repository.ListingRepository;
import com.agricontract.product.infrastructure.persistence.mapper.ListingMapperImpl;
import com.agricontract.product.infrastructure.persistence.mapper.PlotRegistryMapperImpl;
import com.agricontract.product.infrastructure.persistence.repository.ListingRepositoryImpl;
import com.agricontract.product.testsupport.GoodsFixtures;
import com.agricontract.product.testsupport.PlotFixtures;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

import static com.agricontract.product.common.exception.ReservationConflictException.LISTING_VERSION_MISMATCH;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * The snapshot is read from the listing's own copies on real MySQL (matrix 22, 27k): a later edit of the registry
 * entry does not reach it, and a listing edit changes the token so the buyer's old token is refused.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({ListingRepositoryImpl.class, ListingMapperImpl.class, PlotRegistryMapperImpl.class,
        GetListingContractSnapshotUseCaseImpl.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@Testcontainers(disabledWithoutDocker = true)
class ListingContractSnapshotIntegrationTest {

    @Container
    @ServiceConnection
    static MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.0");

    private static final ListingId LISTING_ID = new ListingId("listing-1");

    @Autowired private GetListingContractSnapshotUseCase useCase;
    @Autowired private ListingRepository listingRepository;
    @Autowired private JdbcTemplate jdbcTemplate;
    @MockBean private CategoryRepository categoryRepository;

    @BeforeEach
    void setUp() {
        PlotFixtures.deletePlotsAndListings(jdbcTemplate);
        PlotFixtures.insertRegistryEntry(jdbcTemplate, PlotFixtures.PLOT_ID, "seller-1");
        listingRepository.save(Listing.create(LISTING_ID, "seller-1", new ProductId("product-1"),
                "Ca phe Robusta", "https://cdn.test/cover.jpg", new Quantity(new BigDecimal("100"), "kg"), null,
                new Money(new BigDecimal("50000"), "VND"), LocalDate.of(2026, 12, 31),
                PlotFixtures.onePlot(), GoodsFixtures.coffeeGoods()));
        when(categoryRepository.findById(any())).thenReturn(Optional.of(Category.reconstruct(
                new CategoryId("category-1"), "Ca phe nhan", "ca phe nhan", CategoryStatus.APPROVED, null,
                "admin", "admin@test.com", Commodity.COFFEE)));
    }

    @Test
    void registryEntryEditedAfterListingCreate_doesNotChangeTheSnapshot() {
        String token = token();
        ListingContractSnapshotResponse before = useCase.execute(LISTING_ID.value(), token);

        jdbcTemplate.update("UPDATE plot_registry_entry SET household_label = 'Ho khac', geometry_hash = ? "
                + "WHERE registry_entry_id = ?", "f".repeat(64), PlotFixtures.PLOT_ID);

        ListingContractSnapshotResponse after = useCase.execute(LISTING_ID.value(), token);
        assertThat(after).isEqualTo(before);
        assertThat(after.originSnapshot().plotRefs()).singleElement().satisfies(plot -> {
            assertThat(plot.householdLabel()).isEqualTo("Ho Nguyen Van A");
            assertThat(plot.geometryHash()).isEqualTo(PlotFixtures.point().geometryHash());
        });
        assertThat(after.productName()).isEqualTo("Ca phe Robusta");
        assertThat(after.cropYear()).isEqualTo(2026);
    }

    @Test
    void listingEditedAfterTheBuyerRead_refusesTheOldToken() {
        String seenByBuyer = token();

        jdbcTemplate.update("UPDATE listings SET region_text = 'Cu M''gar, Dak Lak', "
                + "updated_at = TIMESTAMPADD(SECOND, 1, updated_at) WHERE listing_id = ?", LISTING_ID.value());

        assertThatThrownBy(() -> useCase.execute(LISTING_ID.value(), seenByBuyer))
                .isInstanceOfSatisfying(ReservationConflictException.class,
                        e -> assertThat(e.getCode()).isEqualTo(LISTING_VERSION_MISMATCH));
        assertThat(useCase.execute(LISTING_ID.value(), token()).originSnapshot().regionText())
                .isEqualTo("Cu M'gar, Dak Lak");
    }

    private String token() {
        return listingRepository.findById(LISTING_ID).orElseThrow().getVersionToken();
    }
}
