package com.agricontract.product.infrastructure.persistence.repository;

import com.agricontract.product.application.usecase.CheckPlotForestBaselineUseCaseImpl;
import com.agricontract.product.domain.model.PlotRegistryEntry;
import com.agricontract.product.domain.model.vo.*;
import com.agricontract.product.domain.repository.PlotRegistryRepository;
import com.agricontract.product.domain.service.PlotGeometries;
import com.agricontract.product.infrastructure.persistence.mapper.PlotRegistryMapperImpl;
import com.agricontract.product.testsupport.PlotFixtures;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Forest-baseline check on real MySQL (V9): spatial overlap against SRID 4326 baseline areas in long-lat order,
 * explicit coverage, the PENDING/geometry guard and the append-only audit record (issue #264).
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({PlotRegistryRepositoryImpl.class, ForestBaselineRepositoryImpl.class, PlotRegistryMapperImpl.class,
        CheckPlotForestBaselineUseCaseImpl.class, ForestBaselineCheckPersistenceTest.FixedClock.class})
@TestPropertySource(properties = "plot.forest-baseline.dataset-version=" + ForestBaselineCheckPersistenceTest.DATASET)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@Testcontainers(disabledWithoutDocker = true)
class ForestBaselineCheckPersistenceTest {

    static final String DATASET = "natural-forests-2020-v1";
    private static final Instant NOW = Instant.now().truncatedTo(ChronoUnit.MICROS);

    @Container
    @ServiceConnection
    static MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.0");

    @TestConfiguration
    static class FixedClock {
        @Bean
        Clock clock() {
            return Clock.fixed(NOW, ZoneOffset.UTC);
        }
    }

    @Autowired private PlotRegistryRepository plotRegistryRepository;
    @Autowired private CheckPlotForestBaselineUseCaseImpl useCase;
    @Autowired private JdbcTemplate jdbcTemplate;

    // closed long-lat square ring with its lower-left corner at (lng, lat)
    private static String square(double lng, double lat, double side) {
        return String.format(Locale.ROOT, "POLYGON((%f %f, %f %f, %f %f, %f %f, %f %f))", lng, lat, lng + side, lat,
                lng + side, lat + side, lng, lat + side, lng, lat);
    }

    // ~2.7 ha plot polygon
    private static PlotGeometry plot(double lng, double lat) {
        double side = 0.0015;
        return PlotGeometries.polygon(List.of(new double[]{lng, lat}, new double[]{lng + side, lat},
                new double[]{lng + side, lat + side}, new double[]{lng, lat + side}, new double[]{lng, lat}), List.of());
    }

    private void register(String id, PlotGeometry geometry) {
        plotRegistryRepository.create(PlotRegistryEntry.register(new RegistryEntryId(id), "seller-1", "Ho " + id,
                geometry, PlotSource.KML_IMPORT, null, Province.DAK_LAK, NOW.minusSeconds(60)));
    }

    private PlotRegistryEntry load(String id) {
        return plotRegistryRepository.findAllById(List.of(new RegistryEntryId(id))).get(0);
    }

    private void baselineArea(String dataset, String id, ForestClass forestClass, String wkt) {
        jdbcTemplate.update("INSERT INTO forest_baseline_area (dataset_version, baseline_area_id, forest_class, geom) "
                + "VALUES (?, ?, ?, ST_GeomFromText(?, 4326, 'axis-order=long-lat'))", dataset, id, forestClass.name(), wkt);
    }

    private Map<String, Object> plotRow(String id) {
        return jdbcTemplate.queryForMap("SELECT geo_verification_status, geo_risk_level FROM plot_registry_entry "
                + "WHERE registry_entry_id = ?", id);
    }

    private List<Map<String, Object>> audit(String id) {
        return jdbcTemplate.queryForList("SELECT geometry_hash, dataset_version, geo_risk_level, "
                + "CAST(matched_baseline_area_ids AS CHAR) AS matched FROM plot_geo_risk_check "
                + "WHERE registry_entry_id = ?", id);
    }

    @BeforeEach
    void setUp() {
        PlotFixtures.deletePlotsAndListings(jdbcTemplate);
        jdbcTemplate.update("DELETE FROM forest_baseline_area");
        jdbcTemplate.update("DELETE FROM forest_baseline_coverage");
        // coverage: a block of the Central Highlands around Buon Ma Thuot
        jdbcTemplate.update("INSERT INTO forest_baseline_coverage (dataset_version, coverage_id, extent) "
                        + "VALUES (?, 'dak-lak-west', ST_GeomFromText(?, 4326, 'axis-order=long-lat'))",
                DATASET, square(107.9, 12.5, 0.4));
        baselineArea(DATASET, "nf-1", ForestClass.NATURAL_FOREST, square(108.00, 12.60, 0.01));
        baselineArea(DATASET, "amb-1", ForestClass.AMBIGUOUS, square(108.10, 12.60, 0.01));
        // the same ground in another dataset version must not count
        baselineArea("forty-2020-v1", "other-nf", ForestClass.NATURAL_FOREST, square(108.20, 12.60, 0.01));
    }

    @Test
    void overlapSetsTheRiskSignal_andEachResultIsAudited() {
        register("in-forest", plot(108.009, 12.609));      // straddles the corner of nf-1
        register("intercropped", plot(108.105, 12.605));   // inside amb-1
        register("clear", plot(108.05, 12.66));            // covered, no baseline area
        register("other-dataset", plot(108.205, 12.605));  // only overlaps another version's area

        assertThat(useCase.checkPending(50)).isEqualTo(4);

        assertThat(plotRow("in-forest")).containsEntry("geo_verification_status", "CHECKED")
                .containsEntry("geo_risk_level", "HIGH_RISK");
        assertThat(plotRow("intercropped")).containsEntry("geo_risk_level", "INCONCLUSIVE");
        assertThat(plotRow("clear")).containsEntry("geo_risk_level", "LOW_RISK");
        assertThat(plotRow("other-dataset")).containsEntry("geo_risk_level", "LOW_RISK");

        assertThat(audit("in-forest")).singleElement().satisfies(row -> {
            assertThat(row).containsEntry("geometry_hash", plot(108.009, 12.609).geometryHash())
                    .containsEntry("dataset_version", DATASET).containsEntry("geo_risk_level", "HIGH_RISK");
            assertThat((String) row.get("matched")).isEqualTo("[\"nf-1\"]");
        });
        assertThat(audit("clear")).singleElement()
                .satisfies(row -> assertThat((String) row.get("matched")).isEqualTo("[]"));
    }

    @Test
    void highRiskPlot_isNotRejected_itCanStillBackAListing() {
        register("in-forest", plot(108.003, 12.603));

        useCase.checkPending(50);

        PlotRegistryEntry entry = load("in-forest");
        assertThat(entry.getGeoRiskLevel()).isEqualTo(GeoRiskLevel.HIGH_RISK);
        assertThat(entry.canBackListing()).isTrue();
    }

    @Test
    void manualPinPoint_insideForest_isHighRisk() {
        plotRegistryRepository.create(PlotRegistryEntry.register(new RegistryEntryId("pin"), "seller-1", "Ho pin",
                PlotGeometries.point(108.005, 12.605, new BigDecimal("1.5")), PlotSource.MANUAL_PIN, null,
                Province.DAK_LAK, NOW));

        useCase.checkPending(50);

        assertThat(plotRow("pin")).containsEntry("geo_risk_level", "HIGH_RISK");
    }

    @Test
    void outsideCoverage_staysPending_withNoAuditRecord() {
        register("binh-phuoc", plot(106.9, 11.6));
        register("crossing-the-edge", plot(108.2995, 12.7));

        assertThat(useCase.checkPending(50)).isZero();

        assertThat(plotRow("binh-phuoc")).containsEntry("geo_verification_status", "PENDING");
        assertThat(plotRow("crossing-the-edge")).containsEntry("geo_verification_status", "PENDING");
        assertThat(audit("binh-phuoc")).isEmpty();
    }

    @Test
    void staleResult_isDropped_afterAReimportOrTheTimeout() {
        register("reimported", plot(108.003, 12.603));
        register("timed-out", plot(108.003, 12.613));
        GeoRiskCheck staleCheck = load("reimported")
                .recordGeoCheck(new ForestBaselineAssessment(DATASET, List.of()), NOW);
        GeoRiskCheck lateCheck = load("timed-out")
                .recordGeoCheck(new ForestBaselineAssessment(DATASET, List.of()), NOW);

        // the seller re-imports a new geometry while the old one is being checked
        PlotRegistryEntry current = load("reimported");
        current.reimport(plot(108.05, 12.66), PlotSource.KML_IMPORT, null, Province.DAK_LAK, NOW);
        plotRegistryRepository.update(current);
        // the timeout job wins the race
        plotRegistryRepository.markUnverifiedImportedBefore(NOW.minusSeconds(1));

        assertThat(plotRegistryRepository.recordGeoCheck(staleCheck)).isFalse();
        assertThat(plotRegistryRepository.recordGeoCheck(lateCheck)).isFalse();

        assertThat(plotRow("reimported")).containsEntry("geo_verification_status", "PENDING");
        assertThat(plotRow("timed-out")).containsEntry("geo_verification_status", "UNVERIFIED");
        assertThat(audit("reimported")).isEmpty();
        assertThat(audit("timed-out")).isEmpty();
    }

    @Test
    void aCheckedPlot_isNotPickedUpAgain() {
        register("clear", plot(108.05, 12.66));

        assertThat(useCase.checkPending(50)).isEqualTo(1);
        assertThat(useCase.checkPending(50)).isZero();
        assertThat(audit("clear")).hasSize(1);
    }
}
