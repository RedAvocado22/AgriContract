package com.agricontract.product.application.usecase;

import com.agricontract.product.application.dto.RegisteredPlot;
import com.agricontract.product.domain.model.PlotRegistryEntry;
import com.agricontract.product.domain.model.vo.GeoVerificationStatus;
import com.agricontract.product.domain.model.vo.PlotSource;
import com.agricontract.product.domain.model.vo.Province;
import com.agricontract.product.domain.model.vo.RegistryEntryId;
import com.agricontract.product.domain.model.vo.VerificationLevel;
import com.agricontract.product.domain.repository.PlotRegistryRepository;
import com.agricontract.product.testsupport.PlotFixtures;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.text.Normalizer;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PlotRegistrarTest {

    private static final Instant NOW = Instant.parse("2026-10-24T03:00:00Z");

    @Mock private PlotRegistryRepository plotRegistryRepository;

    private PlotRegistrar registrar;

    @BeforeEach
    void setUp() {
        registrar = new PlotRegistrar(plotRegistryRepository, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private static PlotRegistryEntry existing() {
        return PlotRegistryEntry.reconstruct(new RegistryEntryId("plot-1"), "seller-1", "Hộ Lê Văn A",
                PlotFixtures.point(), PlotSource.KML_IMPORT, null, VerificationLevel.SELF_DECLARED, Province.DAK_LAK,
                GeoVerificationStatus.UNVERIFIED, null, Instant.parse("2026-10-01T00:00:00Z"));
    }

    @Test
    void newHousehold_isCreatedPending() {
        when(plotRegistryRepository.findBySellerIdAndHouseholdLabel("seller-1", "Ho A")).thenReturn(Optional.empty());
        when(plotRegistryRepository.create(any())).thenAnswer(inv -> inv.getArgument(0));

        RegisteredPlot result = registrar.register("seller-1", " Ho A ", PlotFixtures.point(), PlotSource.MANUAL_PIN,
                null, Province.DAK_LAK);

        assertThat(result.created()).isTrue();
        assertThat(result.plot().householdLabel()).isEqualTo("Ho A");
        assertThat(result.plot().geoVerificationStatus()).isEqualTo(GeoVerificationStatus.PENDING);
        assertThat(result.plot().importedAt()).isEqualTo(NOW);
        verify(plotRegistryRepository, never()).update(any());
    }

    @Test
    void sameHousehold_inAnotherUnicodeForm_updatesTheExistingEntry() {
        String nfd = Normalizer.normalize("Hộ Lê Văn A", Normalizer.Form.NFD);
        when(plotRegistryRepository.findBySellerIdAndHouseholdLabel("seller-1", "Hộ Lê Văn A"))
                .thenReturn(Optional.of(existing()));
        when(plotRegistryRepository.update(any())).thenAnswer(inv -> inv.getArgument(0));

        RegisteredPlot result = registrar.register("seller-1", nfd, PlotFixtures.point(), PlotSource.KML_IMPORT,
                null, Province.LAM_DONG);

        assertThat(result.created()).isFalse();
        assertThat(result.plot().registryEntryId()).isEqualTo("plot-1");
        assertThat(result.plot().declaredProvince()).isEqualTo(Province.LAM_DONG);
        // same geometry: the finished check is kept
        assertThat(result.plot().geoVerificationStatus()).isEqualTo(GeoVerificationStatus.UNVERIFIED);
        verify(plotRegistryRepository, never()).create(any());
    }

    @Test
    void concurrentInsertOfTheSameHousehold_isAppliedOnTopOfTheWinner() {
        when(plotRegistryRepository.findBySellerIdAndHouseholdLabel("seller-1", "Hộ Lê Văn A"))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(existing()));
        when(plotRegistryRepository.create(any())).thenThrow(new DataIntegrityViolationException("uk"));
        when(plotRegistryRepository.update(any())).thenAnswer(inv -> inv.getArgument(0));

        RegisteredPlot result = registrar.register("seller-1", "Hộ Lê Văn A", PlotFixtures.point(),
                PlotSource.KML_IMPORT, null, Province.DAK_LAK);

        assertThat(result.created()).isFalse();
        ArgumentCaptor<PlotRegistryEntry> updated = ArgumentCaptor.forClass(PlotRegistryEntry.class);
        verify(plotRegistryRepository).update(updated.capture());
        assertThat(updated.getValue().getRegistryEntryId().value()).isEqualTo("plot-1");
        assertThat(updated.getValue().getImportedAt()).isEqualTo(NOW);
    }
}
