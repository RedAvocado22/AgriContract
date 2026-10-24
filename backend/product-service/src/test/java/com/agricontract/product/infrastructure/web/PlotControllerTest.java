package com.agricontract.product.infrastructure.web;

import com.agricontract.product.application.dto.AddPlotRequest;
import com.agricontract.product.application.dto.PlotImportResult;
import com.agricontract.product.application.dto.PlotResponse;
import com.agricontract.product.application.dto.RegisteredPlot;
import com.agricontract.product.application.usecase.AddPlotManuallyUseCase;
import com.agricontract.product.application.usecase.ImportPlotsFromKmlUseCase;
import com.agricontract.product.domain.model.PlotRegistryEntry;
import com.agricontract.product.domain.model.vo.PlotSource;
import com.agricontract.product.domain.model.vo.Province;
import com.agricontract.product.domain.model.vo.RegistryEntryId;
import com.agricontract.product.infrastructure.config.SecurityConfig;
import com.agricontract.product.testsupport.PlotFixtures;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Boundary of registerPlot: SELLER only, owner from the authenticated principal (never a raw header),
 * Idempotency-Key required, JSON manual pin vs KML by Content-Type, malformed input is 400 not 500.
 */
@WebMvcTest(PlotController.class)
@Import(SecurityConfig.class)
@TestPropertySource(properties = {"gateway.internal-secret=gw-secret", "service.internal-secret=svc-secret"})
class PlotControllerTest {

    private static final String PIN = """
            {"householdLabel":"Ho A","latitude":12.666789,"longitude":108.050123,"areaHectares":1.5,
             "accuracyMeters":8.5,"declaredProvince":"DAK_LAK"}""";
    private static final String KML_BODY = "<kml><Document/></kml>";

    @Autowired private MockMvc mockMvc;
    @MockBean private AddPlotManuallyUseCase addPlotManuallyUseCase;
    @MockBean private ImportPlotsFromKmlUseCase importPlotsFromKmlUseCase;

    private static MockHttpServletRequestBuilder asSeller(MockHttpServletRequestBuilder request) {
        return request.header("X-Gateway-Secret", "gw-secret")
                .header("X-User-Id", "seller-1").header("X-User-Role", "default-roles-agricontract,SELLER")
                .header("Idempotency-Key", "key-1");
    }

    private static PlotResponse plot() {
        return PlotResponse.from(PlotRegistryEntry.register(new RegistryEntryId("plot-1"), "seller-1", "Ho A",
                PlotFixtures.point(), PlotSource.MANUAL_PIN, new BigDecimal("8.5"), Province.DAK_LAK,
                Instant.parse("2026-10-24T03:00:00Z")));
    }

    // ---------- manual pin ----------

    @Test
    void manualPin_new_is201_ownerFromPrincipal() throws Exception {
        when(addPlotManuallyUseCase.execute(any(), any())).thenReturn(new RegisteredPlot(plot(), true));

        mockMvc.perform(asSeller(post("/api/v1/plots")).contentType(MediaType.APPLICATION_JSON).content(PIN))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.registryEntryId").value("plot-1"))
                .andExpect(jsonPath("$.data.geometryType").value("POINT"))
                .andExpect(jsonPath("$.data.geoVerificationStatus").value("PENDING"));

        ArgumentCaptor<AddPlotRequest> request = ArgumentCaptor.forClass(AddPlotRequest.class);
        verify(addPlotManuallyUseCase).execute(eq("seller-1"), request.capture());
        assertThat(request.getValue().declaredProvince()).isEqualTo(Province.DAK_LAK);
        assertThat(request.getValue().accuracyMeters()).isEqualByComparingTo("8.5");
    }

    @Test
    void manualPin_existingHousehold_is200() throws Exception {
        when(addPlotManuallyUseCase.execute(any(), any())).thenReturn(new RegisteredPlot(plot(), false));

        mockMvc.perform(asSeller(post("/api/v1/plots")).contentType(MediaType.APPLICATION_JSON).content(PIN))
                .andExpect(status().isOk());
    }

    @Test
    void manualPin_clientCannotChooseGeometryTypeOrSource() throws Exception {
        String withType = PIN.replace("\"declaredProvince\"", "\"geometryType\":\"POLYGON\",\"declaredProvince\"");

        mockMvc.perform(asSeller(post("/api/v1/plots")).contentType(MediaType.APPLICATION_JSON).content(withType))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(addPlotManuallyUseCase);
    }

    @Test
    void manualPin_unknownProvinceOrMissingArea_is400() throws Exception {
        mockMvc.perform(asSeller(post("/api/v1/plots")).contentType(MediaType.APPLICATION_JSON)
                        .content(PIN.replace("DAK_LAK", "DAKLAK")))
                .andExpect(status().isBadRequest());
        mockMvc.perform(asSeller(post("/api/v1/plots")).contentType(MediaType.APPLICATION_JSON)
                        .content(PIN.replace("\"areaHectares\":1.5,", "")))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(addPlotManuallyUseCase);
    }

    @Test
    void geometryRejectedByTheDomain_is400() throws Exception {
        when(addPlotManuallyUseCase.execute(any(), any()))
                .thenThrow(new IllegalArgumentException("Coordinate is outside Viet Nam"));

        mockMvc.perform(asSeller(post("/api/v1/plots")).contentType(MediaType.APPLICATION_JSON).content(PIN))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Coordinate is outside Viet Nam"));
    }

    @Test
    void withoutIdempotencyKey_is400() throws Exception {
        mockMvc.perform(post("/api/v1/plots").header("X-Gateway-Secret", "gw-secret")
                        .header("X-User-Id", "seller-1").header("X-User-Role", "SELLER")
                        .contentType(MediaType.APPLICATION_JSON).content(PIN))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(addPlotManuallyUseCase);
    }

    // ---------- who may register ----------

    @Test
    void buyer_isForbidden() throws Exception {
        mockMvc.perform(post("/api/v1/plots").header("X-Gateway-Secret", "gw-secret")
                        .header("X-User-Id", "buyer-1").header("X-User-Role", "BUYER").header("Idempotency-Key", "k")
                        .contentType(MediaType.APPLICATION_JSON).content(PIN))
                .andExpect(status().isForbidden());
        verifyNoInteractions(addPlotManuallyUseCase);
    }

    @Test
    void internalSecretWithAForgedSellerId_isForbidden() throws Exception {
        // no principal behind the service secret, so a header naming a seller must not create plots for them
        mockMvc.perform(post("/api/v1/plots").header("X-Internal-Secret", "svc-secret")
                        .header("X-User-Id", "seller-1").header("X-User-Role", "SELLER").header("Idempotency-Key", "k")
                        .contentType(MediaType.APPLICATION_JSON).content(PIN))
                .andExpect(status().isForbidden());
        verifyNoInteractions(addPlotManuallyUseCase);
    }

    @Test
    void withoutGatewaySecret_isForbidden() throws Exception {
        mockMvc.perform(post("/api/v1/plots").header("X-User-Id", "seller-1").header("X-User-Role", "SELLER")
                        .header("Idempotency-Key", "k").contentType(MediaType.APPLICATION_JSON).content(PIN))
                .andExpect(status().isForbidden());
        verifyNoInteractions(addPlotManuallyUseCase);
    }

    // ---------- KML ----------

    @Test
    void kml_newHouseholds_is201_withRejectedPlacemarksListed() throws Exception {
        when(importPlotsFromKmlUseCase.execute(eq("seller-1"), any(), eq(Province.GIA_LAI))).thenReturn(
                new PlotImportResult(List.of(plot()), List.of(),
                        List.of(new PlotImportResult.Rejection("Ho X", "Invalid polygon: Self-intersection"))));

        mockMvc.perform(asSeller(post("/api/v1/plots")).param("declaredProvince", "GIA_LAI")
                        .contentType(PlotController.KML).content(KML_BODY))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.created[0].registryEntryId").value("plot-1"))
                .andExpect(jsonPath("$.data.rejected[0].placemark").value("Ho X"));

        ArgumentCaptor<byte[]> body = ArgumentCaptor.forClass(byte[].class);
        verify(importPlotsFromKmlUseCase).execute(eq("seller-1"), body.capture(), eq(Province.GIA_LAI));
        assertThat(new String(body.getValue(), StandardCharsets.UTF_8)).isEqualTo(KML_BODY);
    }

    @Test
    void kml_onlyUpdates_is200_andNothingStored_is400() throws Exception {
        when(importPlotsFromKmlUseCase.execute(any(), any(), any()))
                .thenReturn(new PlotImportResult(List.of(), List.of(plot()), List.of()))
                .thenReturn(new PlotImportResult(List.of(), List.of(),
                        List.of(new PlotImportResult.Rejection("Ho X", "bad"))));

        mockMvc.perform(asSeller(post("/api/v1/plots")).param("declaredProvince", "DAK_LAK")
                        .contentType(PlotController.KML).content(KML_BODY))
                .andExpect(status().isOk());
        mockMvc.perform(asSeller(post("/api/v1/plots")).param("declaredProvince", "DAK_LAK")
                        .contentType(PlotController.KML).content(KML_BODY))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.data.rejected[0].reason").value("bad"));
    }

    @Test
    void kml_withoutOrWithBadProvince_is400() throws Exception {
        mockMvc.perform(asSeller(post("/api/v1/plots")).contentType(PlotController.KML).content(KML_BODY))
                .andExpect(status().isBadRequest());
        mockMvc.perform(asSeller(post("/api/v1/plots")).param("declaredProvince", "SAI_GON")
                        .contentType(PlotController.KML).content(KML_BODY))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(importPlotsFromKmlUseCase);
    }

    @Test
    void kml_over5Mb_is400_withoutCallingTheUseCase() throws Exception {
        byte[] big = new byte[PlotController.MAX_KML_BYTES + 1];

        mockMvc.perform(asSeller(post("/api/v1/plots")).param("declaredProvince", "DAK_LAK")
                        .contentType(PlotController.KML).content(big))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(importPlotsFromKmlUseCase);
    }

    @Test
    void otherContentType_is415() throws Exception {
        mockMvc.perform(asSeller(post("/api/v1/plots")).contentType(MediaType.TEXT_PLAIN).content("x"))
                .andExpect(status().isUnsupportedMediaType());
    }
}
