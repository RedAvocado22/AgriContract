package com.agricontract.product.application.usecase;

import com.agricontract.product.domain.model.PlotRegistryEntry;
import com.agricontract.product.domain.model.vo.*;
import com.agricontract.product.domain.repository.ForestBaselineRepository;
import com.agricontract.product.domain.repository.PlotRegistryRepository;
import com.agricontract.product.testsupport.PlotFixtures;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class CheckPlotForestBaselineUseCaseImplTest {

    private static final Instant NOW = Instant.parse("2026-11-02T03:00:00Z");

    private final PlotRegistryRepository plotRegistryRepository = mock(PlotRegistryRepository.class);
    private final ForestBaselineRepository forestBaselineRepository = mock(ForestBaselineRepository.class);
    private CheckPlotForestBaselineUseCaseImpl useCase;

    @BeforeEach
    void setUp() {
        useCase = new CheckPlotForestBaselineUseCaseImpl(plotRegistryRepository, forestBaselineRepository,
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private static PlotRegistryEntry pending(String id) {
        return PlotRegistryEntry.register(new RegistryEntryId(id), "seller-1", "Ho " + id, PlotFixtures.point(),
                PlotSource.MANUAL_PIN, null, Province.DAK_LAK, NOW.minusSeconds(60));
    }

    @Test
    void overlapWithNaturalForest_recordsHighRiskWithItsAuditRecord() {
        when(plotRegistryRepository.findPendingGeoCheck(50)).thenReturn(List.of(pending("plot-1")));
        when(forestBaselineRepository.assess(any())).thenReturn(Optional.of(new ForestBaselineAssessment(
                "nf-2020-v1", List.of(new ForestOverlap("area-7", ForestClass.NATURAL_FOREST)))));
        when(plotRegistryRepository.recordGeoCheck(any())).thenReturn(true);

        assertThat(useCase.checkPending(50)).isEqualTo(1);

        ArgumentCaptor<GeoRiskCheck> check = ArgumentCaptor.forClass(GeoRiskCheck.class);
        verify(plotRegistryRepository).recordGeoCheck(check.capture());
        assertThat(check.getValue()).isEqualTo(new GeoRiskCheck(new RegistryEntryId("plot-1"),
                PlotFixtures.point().geometryHash(), "nf-2020-v1", GeoRiskLevel.HIGH_RISK, List.of("area-7"), NOW));
    }

    @Test
    void outsideBaselineCoverage_recordsNothing_soThePlotWaitsForTheTimeout() {
        when(plotRegistryRepository.findPendingGeoCheck(50)).thenReturn(List.of(pending("plot-1")));
        when(forestBaselineRepository.assess(any())).thenReturn(Optional.empty());

        assertThat(useCase.checkPending(50)).isZero();
        verify(plotRegistryRepository, never()).recordGeoCheck(any());
    }

    @Test
    void resultDroppedBecauseThePlotChanged_isNotCounted() {
        when(plotRegistryRepository.findPendingGeoCheck(50)).thenReturn(List.of(pending("plot-1")));
        when(forestBaselineRepository.assess(any()))
                .thenReturn(Optional.of(new ForestBaselineAssessment("nf-2020-v1", List.of())));
        when(plotRegistryRepository.recordGeoCheck(any())).thenReturn(false);

        assertThat(useCase.checkPending(50)).isZero();
    }

    @Test
    void oneFailingPlot_doesNotStopTheBatch() {
        when(plotRegistryRepository.findPendingGeoCheck(50)).thenReturn(List.of(pending("plot-1"), pending("plot-2")));
        when(forestBaselineRepository.assess(any()))
                .thenThrow(new IllegalStateException("db hiccup"))
                .thenReturn(Optional.of(new ForestBaselineAssessment("nf-2020-v1", List.of())));
        when(plotRegistryRepository.recordGeoCheck(any())).thenReturn(true);

        assertThat(useCase.checkPending(50)).isEqualTo(1);
        verify(plotRegistryRepository, times(1)).recordGeoCheck(any());
    }
}
