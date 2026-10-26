package com.agricontract.product.application.usecase;

import com.agricontract.product.application.dto.PlotImportResult;
import com.agricontract.product.application.dto.PlotResponse;
import com.agricontract.product.application.dto.RegisteredPlot;
import com.agricontract.product.domain.model.PlotRegistryEntry;
import com.agricontract.product.domain.model.vo.GeometryType;
import com.agricontract.product.domain.model.vo.PlotGeometry;
import com.agricontract.product.domain.model.vo.PlotSource;
import com.agricontract.product.domain.model.vo.Province;
import com.agricontract.product.domain.model.vo.RegistryEntryId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ImportPlotsFromKmlUseCaseImplTest {

    private static final String SELLER = "seller-1";

    @Mock private PlotRegistrar plotRegistrar;

    private ImportPlotsFromKmlUseCaseImpl useCase;

    @BeforeEach
    void setUp() {
        useCase = new ImportPlotsFromKmlUseCaseImpl(plotRegistrar);
    }

    private static byte[] kml(String placemarks) {
        return ("<kml xmlns=\"http://www.opengis.net/kml/2.2\"><Document>" + placemarks + "</Document></kml>")
                .getBytes(StandardCharsets.UTF_8);
    }

    private static String point(String name, String area) {
        String extended = area == null ? ""
                : "<ExtendedData><Data name=\"area_ha\"><value>" + area + "</value></Data></ExtendedData>";
        return "<Placemark><name>" + name + "</name>" + extended
                + "<Point><coordinates>108.05,12.66</coordinates></Point></Placemark>";
    }

    private static RegisteredPlot stored(String label, PlotGeometry geometry, boolean created) {
        PlotRegistryEntry entry = PlotRegistryEntry.register(new RegistryEntryId("id-" + label), SELLER, label,
                geometry, PlotSource.KML_IMPORT, null, Province.DAK_LAK, Instant.parse("2026-10-24T00:00:00Z"));
        return new RegisteredPlot(PlotResponse.from(entry), created);
    }

    @Test
    void goodPlacemarksAreStored_badOnesAreReportedByName() {
        when(plotRegistrar.register(eq(SELLER), anyString(), any(), eq(PlotSource.KML_IMPORT), isNull(),
                eq(Province.DAK_LAK)))
                .thenAnswer(inv -> stored(inv.getArgument(1), inv.getArgument(2), !"Ho B".equals(inv.getArgument(1))));

        PlotImportResult result = useCase.execute(SELLER, kml(
                point("Ho A", "1.5")
                        + point("Ho B", "2")
                        + point("No area", null)
                        + point("Too big", "4.5")
                        + "<Placemark><name>Outside</name><ExtendedData><Data name=\"area_ha\"><value>1</value></Data>"
                        + "</ExtendedData><Point><coordinates>12.66,108.05</coordinates></Point></Placemark>"
                        + point("Ho A", "1.5")), Province.DAK_LAK);

        assertThat(result.created()).extracting(PlotResponse::householdLabel).containsExactly("Ho A");
        assertThat(result.updated()).extracting(PlotResponse::householdLabel).containsExactly("Ho B");
        assertThat(result.rejected()).extracting(PlotImportResult.Rejection::placemark)
                .containsExactly("No area", "Too big", "Outside", "Ho A");
        assertThat(result.rejected().get(0).reason()).contains("area_ha");
        assertThat(result.rejected().get(1).reason()).contains("POLYGON");
        assertThat(result.rejected().get(3).reason()).contains("more than once");
        verify(plotRegistrar, times(2)).register(any(), any(), any(), any(), any(), any());
    }

    @Test
    void labelTooLong_orAreaTooLarge_isRejectedPerPlacemark_andTheRestOfTheFileGoesIn() {
        when(plotRegistrar.register(any(), any(), any(), any(), any(), any()))
                .thenAnswer(inv -> stored(inv.getArgument(1), inv.getArgument(2), true));
        String longName = "Ho " + "a".repeat(300);

        PlotImportResult result = useCase.execute(SELLER, kml(
                point("Ho A", "1")
                        + point(longName, "1")
                        + """
                        <Placemark><name>Huge</name><Polygon><outerBoundaryIs><LinearRing><coordinates>
                        107.0,12.0 108.0,12.0 108.0,13.0 107.0,13.0 107.0,12.0
                        </coordinates></LinearRing></outerBoundaryIs></Polygon></Placemark>"""
                        + point("Ho B", "1")), Province.DAK_LAK);

        assertThat(result.created()).extracting(PlotResponse::householdLabel).containsExactly("Ho A", "Ho B");
        assertThat(result.rejected()).extracting(PlotImportResult.Rejection::placemark)
                .containsExactly(longName, "Huge");
        assertThat(result.rejected().get(0).reason()).contains("255");
        assertThat(result.rejected().get(1).reason()).contains("check the coordinates");
        verify(plotRegistrar, times(2)).register(any(), any(), any(), any(), any(), any());
    }

    @Test
    void polygonPlacemark_isRegisteredWithItsComputedArea() {
        when(plotRegistrar.register(any(), any(), any(), any(), any(), any()))
                .thenAnswer(inv -> stored(inv.getArgument(1), inv.getArgument(2), true));

        useCase.execute(SELLER, kml("""
                <Placemark><name>Ho D</name><Polygon><outerBoundaryIs><LinearRing><coordinates>
                108.05,12.66 108.053,12.66 108.053,12.663 108.05,12.663 108.05,12.66
                </coordinates></LinearRing></outerBoundaryIs></Polygon></Placemark>"""), Province.GIA_LAI);

        ArgumentCaptor<PlotGeometry> geometry = ArgumentCaptor.forClass(PlotGeometry.class);
        verify(plotRegistrar).register(eq(SELLER), eq("Ho D"), geometry.capture(), eq(PlotSource.KML_IMPORT),
                isNull(), eq(Province.GIA_LAI));
        assertThat(geometry.getValue().type()).isEqualTo(GeometryType.POLYGON);
        assertThat(geometry.getValue().areaHectares()).isGreaterThan(new BigDecimal("10"));
    }

    @Test
    void fileWithoutPlacemarks_orWithoutProvince_isRefused() {
        assertThatThrownBy(() -> useCase.execute(SELLER, kml(""), Province.DAK_LAK))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("no Placemark");
        assertThatThrownBy(() -> useCase.execute(SELLER, kml(point("Ho A", "1")), null))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("declaredProvince");
        verifyNoInteractions(plotRegistrar);
    }

    @Test
    void tooManyPlacemarks_isRefusedBeforeAnyIsStored() {
        String many = point("Ho", "1").repeat(ImportPlotsFromKmlUseCaseImpl.MAX_PLACEMARKS + 1);

        assertThatThrownBy(() -> useCase.execute(SELLER, kml(many), Province.DAK_LAK))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("split");
        verifyNoInteractions(plotRegistrar);
    }
}
