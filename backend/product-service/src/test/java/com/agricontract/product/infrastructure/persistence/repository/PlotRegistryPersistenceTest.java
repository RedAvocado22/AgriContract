package com.agricontract.product.infrastructure.persistence.repository;

import com.agricontract.product.common.exception.DuplicatePlotHouseholdException;
import com.agricontract.product.domain.model.Listing;
import com.agricontract.product.domain.model.PlotRegistryEntry;
import com.agricontract.product.domain.model.vo.*;
import com.agricontract.product.domain.repository.ListingRepository;
import com.agricontract.product.domain.repository.PlotRegistryRepository;
import com.agricontract.product.domain.service.PlotGeometries;
import com.agricontract.product.infrastructure.persistence.mapper.ListingMapperImpl;
import com.agricontract.product.infrastructure.persistence.mapper.PlotRegistryMapperImpl;
import com.agricontract.product.testsupport.GoodsFixtures;
import com.agricontract.product.testsupport.PlotFixtures;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

/**
 * Plot registry on real MySQL: Flyway V6, GEOMETRY SRID 4326 in long-lat order, accent-sensitive household
 * match key, the geo check timeout, and listing copies that a registry re-import does not change (matrix 27k).
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({PlotRegistryRepositoryImpl.class, ListingRepositoryImpl.class,
        PlotRegistryMapperImpl.class, ListingMapperImpl.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@Testcontainers(disabledWithoutDocker = true)
class PlotRegistryPersistenceTest {

    @Container
    @ServiceConnection
    static MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.0");

    private static final Instant NOW = Instant.now().truncatedTo(ChronoUnit.MICROS);

    @Autowired private PlotRegistryRepository plotRegistryRepository;
    @Autowired private ListingRepository listingRepository;
    @Autowired private JdbcTemplate jdbcTemplate;

    // ~3 ha square near Buon Ma Thuot, listed clockwise so storage must not depend on ring direction
    private static PlotGeometry polygon(double side) {
        return PlotGeometries.polygon(List.of(new double[]{108.05, 12.66}, new double[]{108.05, 12.66 + side},
                new double[]{108.05 + side, 12.66 + side}, new double[]{108.05 + side, 12.66},
                new double[]{108.05, 12.66}), List.of());
    }

    private static PlotRegistryEntry entry(String id, String seller, String label, PlotGeometry geometry,
                                           Instant importedAt) {
        return PlotRegistryEntry.register(new RegistryEntryId(id), seller, label, geometry, PlotSource.KML_IMPORT,
                null, Province.DAK_LAK, importedAt);
    }

    private String status(String id) {
        return jdbcTemplate.queryForObject(
                "SELECT geo_verification_status FROM plot_registry_entry WHERE registry_entry_id = ?", String.class, id);
    }

    @BeforeEach
    void setUp() {
        PlotFixtures.deletePlotsAndListings(jdbcTemplate);
    }

    @Test
    void polygon_storedAsSrid4326LongLat_withTheAreaMySqlComputesToo() throws Exception {
        PlotGeometry geometry = polygon(0.0015);
        plotRegistryRepository.create(entry("plot-1", "seller-1", "Ho A", geometry, NOW));

        // MySQL's own ellipsoidal area agrees with ours: axis order and ring orientation survived storage
        Double squareMeters = jdbcTemplate.queryForObject(
                "SELECT ST_Area(geo_json) FROM plot_registry_entry WHERE registry_entry_id = 'plot-1'", Double.class);
        assertThat(squareMeters / 10_000).isCloseTo(geometry.areaHectares().doubleValue(), withinPercentage(1));
        assertThat(jdbcTemplate.queryForObject(
                "SELECT ST_SRID(geo_json) FROM plot_registry_entry WHERE registry_entry_id = 'plot-1'", Integer.class))
                .isEqualTo(4326);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT ST_Longitude(ST_PointN(ST_ExteriorRing(geo_json), 1)) FROM plot_registry_entry",
                Double.class)).isBetween(108.0, 108.1);

        PlotRegistryEntry read = plotRegistryRepository
                .findBySellerIdAndHouseholdLabel("seller-1", "Ho A").orElseThrow();
        assertThat(read.getGeometry()).isEqualTo(geometry);
        assertThat(sha256(read.getGeometry().wkt())).isEqualTo(read.getGeometry().geometryHash());
        assertThat(read.getGeoVerificationStatus()).isEqualTo(GeoVerificationStatus.PENDING);
        assertThat(read.getImportedAt()).isEqualTo(NOW);
    }

    @Test
    void sameHouseholdOfTheSameSeller_isOneRow() {
        plotRegistryRepository.create(entry("plot-1", "seller-1", "Ho A", PlotFixtures.point(), NOW));

        assertThatThrownBy(() -> plotRegistryRepository.create(
                entry("plot-2", "seller-1", "Ho A", PlotFixtures.point(), NOW)))
                .isInstanceOf(DuplicatePlotHouseholdException.class);
        // another seller may use the same label for their own member household
        plotRegistryRepository.create(entry("plot-3", "seller-2", "Ho A", PlotFixtures.point(), NOW));
        assertThat(plotRegistryRepository.findBySellerIdAndHouseholdLabel("seller-2", "Ho A")).isPresent();
    }

    @Test
    void otherIntegrityError_isNotReportedAsADuplicateHousehold() {
        // reconstruct skips the domain length check, so the column limit itself is hit
        PlotRegistryEntry tooLong = PlotRegistryEntry.reconstruct(new RegistryEntryId("plot-1"), "seller-1",
                "a".repeat(256), PlotFixtures.point(), PlotSource.KML_IMPORT, null, VerificationLevel.SELF_DECLARED,
                Province.DAK_LAK, GeoVerificationStatus.PENDING, null, NOW);

        assertThatThrownBy(() -> plotRegistryRepository.create(tooLong))
                .isInstanceOf(DataIntegrityViolationException.class)
                .isNotInstanceOf(DuplicatePlotHouseholdException.class);
    }

    @Test
    void householdMatch_isAccentSensitive() {
        plotRegistryRepository.create(entry("plot-1", "seller-1", "Hồ Văn A", PlotFixtures.point(), NOW));
        plotRegistryRepository.create(entry("plot-2", "seller-1", "Hổ Văn A", PlotFixtures.point(), NOW));

        assertThat(plotRegistryRepository.findBySellerIdAndHouseholdLabel("seller-1", "Hồ Văn A").orElseThrow()
                .getRegistryEntryId().value()).isEqualTo("plot-1");
        assertThat(plotRegistryRepository.findBySellerIdAndHouseholdLabel("seller-1", "Ho Van A")).isEmpty();
    }

    @Test
    void geoTimeout_movesOnlyPendingEntriesImportedBeforeTheCutoff() {
        plotRegistryRepository.create(entry("old", "seller-1", "Ho A", PlotFixtures.point(), NOW.minus(7, ChronoUnit.HOURS)));
        plotRegistryRepository.create(entry("fresh", "seller-1", "Ho B", PlotFixtures.point(), NOW));
        plotRegistryRepository.create(entry("checked", "seller-1", "Ho C", PlotFixtures.point(), NOW.minus(7, ChronoUnit.HOURS)));
        jdbcTemplate.update("UPDATE plot_registry_entry SET geo_verification_status = 'CHECKED', "
                + "geo_risk_level = 'LOW_RISK' WHERE registry_entry_id = 'checked'");

        Instant cutoff = NOW.minus(6, ChronoUnit.HOURS);
        assertThat(plotRegistryRepository.markUnverifiedImportedBefore(cutoff)).isEqualTo(1);
        assertThat(plotRegistryRepository.markUnverifiedImportedBefore(cutoff)).isZero();

        assertThat(status("old")).isEqualTo("UNVERIFIED");
        assertThat(status("fresh")).isEqualTo("PENDING");
        assertThat(status("checked")).isEqualTo("CHECKED");
    }

    @Test
    void listingCopy_isNotChangedByALaterReimportOfTheRegistryEntry() {
        PlotGeometry original = polygon(0.0015);
        PlotRegistryEntry plot = plotRegistryRepository.create(entry("plot-1", "seller-1", "Ho A", original, NOW));
        listingRepository.save(Listing.create(new ListingId("listing-1"), "seller-1", new ProductId("product-1"),
                "Ca phe Robusta", "https://cdn.test/cover.jpg", new Quantity(new BigDecimal("100"), "kg"), null,
                new Money(new BigDecimal("50000"), "VND"), LocalDate.of(2026, 12, 31),
                List.of(new ListingPlot(plot.getRegistryEntryId(), plot.getHouseholdLabel(), plot.getGeometry(),
                        plot.getVerificationLevel())), GoodsFixtures.coffeeGoods()));

        PlotGeometry remeasured = polygon(0.002);
        plot.reimport(remeasured, PlotSource.KML_IMPORT, null, Province.DAK_LAK, NOW.plusSeconds(60));
        plotRegistryRepository.update(plot);

        assertThat(plotRegistryRepository.findBySellerIdAndHouseholdLabel("seller-1", "Ho A").orElseThrow()
                .getGeometry()).isEqualTo(remeasured);
        Listing listing = listingRepository.findById(new ListingId("listing-1")).orElseThrow();
        assertThat(listing.getPlots()).hasSize(1);
        assertThat(listing.getPlots().get(0).geometry()).isEqualTo(original);
        assertThat(listing.getPlots().get(0).sourceRegistryEntryId().value()).isEqualTo("plot-1");
    }

    @Test
    void pagedListingReads_carryEachListingsOwnPlots() {
        PlotFixtures.insertRegistryEntry(jdbcTemplate, "plot-a", "seller-1");
        PlotFixtures.insertRegistryEntry(jdbcTemplate, "plot-b", "seller-1");
        for (String id : List.of("listing-a", "listing-b")) {
            listingRepository.save(Listing.create(new ListingId(id), "seller-1", new ProductId("product-1"),
                    "Ca phe Robusta", "https://cdn.test/cover.jpg", new Quantity(new BigDecimal("100"), "kg"), null,
                    new Money(new BigDecimal("50000"), "VND"), LocalDate.of(2026, 12, 31),
                    List.of(PlotFixtures.listingPlot(id.equals("listing-a") ? "plot-a" : "plot-b")), GoodsFixtures.coffeeGoods()));
        }

        List<Listing> page = listingRepository.findBySellerId("seller-1",
                org.springframework.data.domain.PageRequest.of(0, 10));

        assertThat(page).hasSize(2).allSatisfy(listing -> assertThat(listing.getPlots()).hasSize(1));
        assertThat(page).extracting(l -> l.getListingId().value() + "=" + l.getPlots().get(0).sourceRegistryEntryId().value())
                .containsExactlyInAnyOrder("listing-a=plot-a", "listing-b=plot-b");
    }

    private static String sha256(String value) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
    }
}
