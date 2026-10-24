package com.agricontract.product.domain.model;

import com.agricontract.product.domain.model.vo.GeoRiskLevel;
import com.agricontract.product.domain.model.vo.GeoVerificationStatus;
import com.agricontract.product.domain.model.vo.PlotGeometry;
import com.agricontract.product.domain.model.vo.PlotSource;
import com.agricontract.product.domain.model.vo.Province;
import com.agricontract.product.domain.model.vo.RegistryEntryId;
import com.agricontract.product.domain.model.vo.VerificationLevel;
import com.agricontract.product.domain.service.PlotGeometries;
import com.agricontract.product.testsupport.PlotFixtures;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

class PlotRegistryEntryTest {

    private static final Instant T0 = Instant.parse("2026-10-24T01:00:00Z");
    private static final Instant T1 = Instant.parse("2026-10-25T01:00:00Z");

    private static PlotGeometry polygon() {
        return PlotGeometries.polygon(List.of(new double[]{108.05, 12.66}, new double[]{108.053, 12.66},
                new double[]{108.053, 12.663}, new double[]{108.05, 12.66}), List.of());
    }

    private static PlotRegistryEntry checked() {
        return PlotRegistryEntry.reconstruct(new RegistryEntryId("plot-1"), "seller-1", "Ho A", PlotFixtures.point(),
                PlotSource.KML_IMPORT, null, VerificationLevel.SELF_DECLARED, Province.DAK_LAK,
                GeoVerificationStatus.CHECKED, GeoRiskLevel.LOW_RISK, T0);
    }

    @Test
    void register_startsPendingSelfDeclared_andCannotBackAListingYet() {
        PlotRegistryEntry entry = PlotRegistryEntry.register(new RegistryEntryId("plot-1"), "seller-1", "  Ho A ",
                PlotFixtures.point(), PlotSource.MANUAL_PIN, new BigDecimal("8.5"), Province.DAK_LAK, T0);

        assertThat(entry.getHouseholdLabel()).isEqualTo("Ho A");
        assertThat(entry.getGeoVerificationStatus()).isEqualTo(GeoVerificationStatus.PENDING);
        assertThat(entry.getVerificationLevel()).isEqualTo(VerificationLevel.SELF_DECLARED);
        assertThat(entry.getImportedAt()).isEqualTo(T0);
        assertThat(entry.canBackListing()).isFalse();
    }

    @Test
    void manualPin_canOnlyDeclareAPoint() {
        assertThatThrownBy(() -> PlotRegistryEntry.register(new RegistryEntryId("plot-1"), "seller-1", "Ho A",
                polygon(), PlotSource.MANUAL_PIN, null, Province.DAK_LAK, T0))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("POINT");
    }

    @Test
    void accuracy_isOnlyForAManualPin() {
        assertThatThrownBy(() -> PlotRegistryEntry.register(new RegistryEntryId("plot-1"), "seller-1", "Ho A",
                polygon(), PlotSource.KML_IMPORT, new BigDecimal("5"), Province.DAK_LAK, T0))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void reimport_sameGeometry_keepsTheFinishedGeoCheck() {
        PlotRegistryEntry entry = checked();

        entry.reimport(PlotFixtures.point(), PlotSource.KML_IMPORT, null, Province.LAM_DONG, T1);

        assertThat(entry.getGeoVerificationStatus()).isEqualTo(GeoVerificationStatus.CHECKED);
        assertThat(entry.getGeoRiskLevel()).isEqualTo(GeoRiskLevel.LOW_RISK);
        assertThat(entry.getDeclaredProvince()).isEqualTo(Province.LAM_DONG);
        assertThat(entry.getImportedAt()).isEqualTo(T1);
    }

    @Test
    void reimport_changedGeometry_restartsTheGeoCheck() {
        PlotRegistryEntry entry = checked();

        entry.reimport(polygon(), PlotSource.KML_IMPORT, null, Province.DAK_LAK, T1);

        assertThat(entry.getGeometry()).isEqualTo(polygon());
        assertThat(entry.getGeoVerificationStatus()).isEqualTo(GeoVerificationStatus.PENDING);
        assertThat(entry.getGeoRiskLevel()).isNull();
        assertThat(entry.canBackListing()).isFalse();
    }

    @Test
    void unverifiedAndChecked_canBackAListing() {
        assertThat(checked().canBackListing()).isTrue();
        PlotRegistryEntry unverified = PlotRegistryEntry.reconstruct(new RegistryEntryId("plot-2"), "seller-1",
                "Ho B", PlotFixtures.point(), PlotSource.KML_IMPORT, null, VerificationLevel.SELF_DECLARED,
                Province.DAK_LAK, GeoVerificationStatus.UNVERIFIED, null, T0);
        assertThat(unverified.canBackListing()).isTrue();
    }

    @Test
    void normalizeLabel_makesNfcAndNfdEqual_butKeepsDiacritics() {
        String nfd = java.text.Normalizer.normalize("Hộ Lê Văn A", java.text.Normalizer.Form.NFD);

        assertThat(nfd).isNotEqualTo("Hộ Lê Văn A");
        assertThat(PlotRegistryEntry.normalizeLabel(nfd)).isEqualTo("Hộ Lê Văn A");
        assertThat(PlotRegistryEntry.normalizeLabel("Hồ")).isNotEqualTo(PlotRegistryEntry.normalizeLabel("Hổ"));
    }

    @Test
    void ownership() {
        assertThat(checked().isOwnedBy("seller-1")).isTrue();
        assertThat(checked().isOwnedBy("seller-2")).isFalse();
    }
}
