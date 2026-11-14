package com.agricontract.product.infrastructure.web;

import com.agricontract.product.application.dto.CreateListingRequest;
import com.agricontract.product.application.dto.CreatedListing;
import com.agricontract.product.application.dto.ListingResponse;
import com.agricontract.product.application.usecase.CreateListingUseCase;
import com.agricontract.product.application.usecase.GetListingUseCase;
import com.agricontract.product.application.usecase.GetSellerListingsUseCase;
import com.agricontract.product.application.usecase.ListActiveListingsUseCase;
import com.agricontract.product.application.usecase.UpdateListingUseCase;
import com.agricontract.product.common.exception.ActorNotEligibleException;
import com.agricontract.product.common.exception.EligibilityUnavailableException;
import com.agricontract.product.common.exception.IdempotencyKeyReusedException;
import com.agricontract.product.common.exception.NotListingSellerException;
import com.agricontract.product.common.exception.OfferedQuantityBelowReservedException;
import com.agricontract.product.domain.model.Listing;
import com.agricontract.product.domain.model.vo.CategoryId;
import com.agricontract.product.domain.model.vo.Commodity;
import com.agricontract.product.domain.model.vo.ListingGoods;
import com.agricontract.product.domain.model.vo.ListingId;
import com.agricontract.product.domain.model.vo.ListingStatus;
import com.agricontract.product.domain.model.vo.ProductId;
import com.agricontract.product.infrastructure.config.SecurityConfig;
import com.agricontract.product.infrastructure.contract.FrozenSchemas;
import com.agricontract.product.testsupport.GoodsFixtures;
import com.agricontract.product.testsupport.PlotFixtures;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Web boundary of createListing and updateListing (supplementary-services-api.yaml): the body is the frozen
 * ListingCreateRequest (unknown field → 400), the response the frozen ListingResponse, errors the shared envelope.
 */
@WebMvcTest(ListingController.class)
@Import(SecurityConfig.class)
@TestPropertySource(properties = {"gateway.internal-secret=gw-secret", "service.internal-secret=svc-secret"})
class CreateListingControllerTest {

    private static final String SELLER = "6f1c2b3a-4d5e-4f60-8a7b-9c0d1e2f3a4b";
    private static final String LISTING = "0b6f5c1e-2d3a-4b4c-9d5e-6f7a8b9c0d1e";
    private static final String PRODUCT = "7e4d2b9c-3a1f-4e85-b6d0-5c2a9f8e3b02";
    private static final String PLOT = "3c2b1a09-8f7e-4d6c-b5a4-938271605f4e";
    private static final String CATEGORY = "9a8b7c6d-5e4f-4a3b-8c2d-1e0f9a8b7c6d";

    private static final String BODY = """
            {"productId":"%s","productName":"Ca phe Robusta","unitOfMeasure":"KG",
             "offeredQuantity":50000,"minOrderQuantity":1000,"askingUnitPrice":"90000.00",
             "registryEntryIds":["%s"],"regionText":"Buon Ma Thuot, Dak Lak",
             "declaredQualitySpec":{"commodity":"COFFEE","type":"ROBUSTA","moisturePercent":12.5,
              "foreignMatterPercent":0.5,"blackBrokenBeansPercent":3.0},
             "packagingDeclaration":null,"warehouseLocation":"Kho HTX Ea Kar","estimatedAvailabilityDate":"2026-12-01",
             "traceabilityEvidencePackageId":null}""".formatted(PRODUCT, PLOT);

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @MockBean private GetListingUseCase getListingUseCase;
    @MockBean private ListActiveListingsUseCase listActiveListingsUseCase;
    @MockBean private CreateListingUseCase createListingUseCase;
    @MockBean private GetSellerListingsUseCase getSellerListingsUseCase;
    @MockBean private UpdateListingUseCase updateListingUseCase;

    private ResultActions createAsSeller(String body) throws Exception {
        return mockMvc.perform(post("/api/v1/listings")
                .header("Idempotency-Key", "key-1")
                .header("X-Gateway-Secret", "gw-secret")
                .header("X-User-Id", SELLER).header("X-User-Role", "SELLER")
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private ResultActions createAsSeller() throws Exception {
        return createAsSeller(BODY);
    }

    private ResultActions updateAs(String role, String body) throws Exception {
        return mockMvc.perform(put("/api/v1/listings/{id}", LISTING)
                .header("Idempotency-Key", "edit-1")
                .header("X-Gateway-Secret", "gw-secret")
                .header("X-User-Id", SELLER).header("X-User-Role", role)
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }

    // a listing as the service returns it: 50 t offered, 20 t reserved
    private static ListingResponse listing() {
        ListingGoods goods = GoodsFixtures.coffeeGoods();
        ListingGoods withUuidCategory = new ListingGoods(new CategoryId(CATEGORY), Commodity.COFFEE, goods.cropYear(),
                goods.harvestSeason(), goods.certifications(), goods.regionText(), goods.declaredQualitySpec(),
                goods.packagingDeclaration(), goods.warehouseLocation(), goods.estimatedAvailabilityDate(), null);
        return ListingResponse.from(Listing.reconstruct(new ListingId(LISTING), SELLER, new ProductId(PRODUCT),
                "Ca phe Robusta", "https://cdn.test/cover.jpg", new BigDecimal("50000.000"),
                new BigDecimal("30000.000"), new BigDecimal("1000.000"), new BigDecimal("90000.00"),
                ListingStatus.PARTIALLY_COMMITTED, null, Instant.parse("2026-11-14T08:00:00.123456Z"),
                List.of(PlotFixtures.listingPlot(PLOT)), withUuidCategory));
    }

    // ---------- frozen contract ----------

    @Test
    void requestAndResponse_matchTheFrozenSchemas() throws Exception {
        FrozenSchemas.assertApiSchema(FrozenSchemas.SUPPLEMENTARY_API, "ListingCreateRequest",
                objectMapper.readTree(BODY));
        when(createListingUseCase.execute(eq(SELLER), eq("key-1"), any()))
                .thenReturn(new CreatedListing(listing(), true));

        String body = createAsSeller()
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.askingUnitPrice").value("90000.00"))
                .andExpect(jsonPath("$.unitOfMeasure").value("KG"))
                .andExpect(jsonPath("$.status").value("PARTIALLY_COMMITTED"))
                .andReturn().getResponse().getContentAsString();

        FrozenSchemas.assertApiSchema(FrozenSchemas.SUPPLEMENTARY_API, "ListingResponse", objectMapper.readTree(body));
    }

    @Test
    void requestBody_isReadExactly() throws Exception {
        when(createListingUseCase.execute(eq(SELLER), eq("key-1"), any()))
                .thenReturn(new CreatedListing(listing(), true));

        createAsSeller().andExpect(status().isCreated());

        ArgumentCaptor<CreateListingRequest> request = ArgumentCaptor.forClass(CreateListingRequest.class);
        verify(createListingUseCase).execute(eq(SELLER), eq("key-1"), request.capture());
        assertThat(request.getValue().askingUnitPriceValue()).isEqualTo(new BigDecimal("90000.00"));
        assertThat(request.getValue().offeredQuantity()).isEqualByComparingTo("50000");
        assertThat(request.getValue().estimatedAvailabilityDate()).isEqualTo(LocalDate.of(2026, 12, 1));
    }

    @Test
    void unknownField_is400_andNothingRuns() throws Exception {
        for (String extra : List.of("\"quantityAvailable\":10", "\"listingId\":\"" + LISTING + "\"",
                "\"deliveryDeadline\":\"2099-01-15\"", "\"priceFloor\":90000.00")) {
            createAsSeller(BODY.replaceFirst("\\{", "{" + extra + ","))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"));
            updateAs("SELLER", BODY.replaceFirst("\\{", "{" + extra + ","))
                    .andExpect(status().isBadRequest());
        }
        // nested additionalProperties: false
        createAsSeller(BODY.replace("\"type\":\"ROBUSTA\"", "\"type\":\"ROBUSTA\",\"grade\":1"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(createListingUseCase, updateListingUseCase);
    }

    @Test
    void contractFieldRules_are400() throws Exception {
        for (String bad : List.of(
                BODY.replace("\"askingUnitPrice\":\"90000.00\"", "\"askingUnitPrice\":\"90000\""),
                BODY.replace("\"askingUnitPrice\":\"90000.00\"", "\"askingUnitPrice\":\"90000.001\""),
                BODY.replace("\"askingUnitPrice\":\"90000.00\"", "\"askingUnitPrice\":\"100000000.00\""),
                BODY.replace("\"askingUnitPrice\":\"90000.00\"", "\"askingUnitPrice\":90000.00"),
                BODY.replace("\"unitOfMeasure\":\"KG\"", "\"unitOfMeasure\":\"TON\""),
                BODY.replace("\"productName\":\"Ca phe Robusta\",", ""),
                BODY.replace("\"minOrderQuantity\":1000,", ""),
                BODY.replace("\"offeredQuantity\":50000", "\"offeredQuantity\":0"))) {
            createAsSeller(bad).andExpect(status().isBadRequest());
        }
        verifyNoInteractions(createListingUseCase);
    }

    // ---------- createListing: eligibility gate (matrix 21f) ----------

    @Test
    void userServiceUnavailable_is503() throws Exception {
        when(createListingUseCase.execute(eq(SELLER), eq("key-1"), any())).thenThrow(new EligibilityUnavailableException());

        createAsSeller()
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.message").value("Eligibility check is temporarily unavailable"));
    }

    @Test
    void ineligibleSeller_is403() throws Exception {
        when(createListingUseCase.execute(eq(SELLER), eq("key-1"), any())).thenThrow(new ActorNotEligibleException());

        createAsSeller()
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("Account is not eligible for this action"));
    }

    // ---------- createListing: Idempotency-Key + bare body (#424) ----------

    @Test
    void missingIdempotencyKey_is400_withTheSharedErrorEnvelope() throws Exception {
        mockMvc.perform(post("/api/v1/listings")
                        .header("X-Gateway-Secret", "gw-secret")
                        .header("X-User-Id", SELLER).header("X-User-Role", "SELLER")
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"))
                .andExpect(jsonPath("$.traceId").isNotEmpty())
                .andExpect(jsonPath("$.path").value("/api/v1/listings"));
        verifyNoInteractions(createListingUseCase);
    }

    @Test
    void firstRequest_is201_andReplay_is200_bothWithTheBareListing() throws Exception {
        when(createListingUseCase.execute(eq(SELLER), eq("key-1"), any()))
                .thenReturn(new CreatedListing(listing(), true))
                .thenReturn(new CreatedListing(listing(), false));

        createAsSeller()
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.listingId").value(LISTING))
                .andExpect(jsonPath("$.data").doesNotExist())
                .andExpect(jsonPath("$.success").doesNotExist());
        createAsSeller()
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.listingId").value(LISTING));
    }

    @Test
    void sameKeyDifferentBody_is409_idempotencyKeyReused() throws Exception {
        when(createListingUseCase.execute(eq(SELLER), eq("key-1"), any()))
                .thenThrow(new IdempotencyKeyReusedException());

        createAsSeller()
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("IDEMPOTENCY_KEY_REUSED"));
    }

    // HeaderAuthenticationFilter answers before any controller advice; its body is the shared envelope too.
    @Test
    void withoutGatewaySecret_is403_withTheSharedErrorEnvelope() throws Exception {
        mockMvc.perform(post("/api/v1/listings").header("X-Correlation-Id", "corr-1")
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"))
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.traceId").value("corr-1"))
                .andExpect(jsonPath("$.timestamp").isString())
                .andExpect(jsonPath("$.success").doesNotExist());
    }

    private static final String PHASE1_ACTION = "close";

    // The Phase 1 close endpoint is gone (#424 §2): no handler for PUT on that path.
    @Test
    void phase1CloseEndpoint_isGone() throws Exception {
        mockMvc.perform(put("/api/v1/listings/listing-1/{action}", PHASE1_ACTION)
                        .header("X-Gateway-Secret", "gw-secret")
                        .header("X-User-Id", SELLER).header("X-User-Role", "SELLER"))
                .andExpect(status().isNotFound());
    }

    // ---------- updateListing (D48) ----------

    @Test
    void update_byTheOwner_is200_withTheFrozenListingResponse() throws Exception {
        when(updateListingUseCase.execute(eq(SELLER), eq(LISTING), eq("edit-1"), any())).thenReturn(listing());

        String body = updateAs("SELLER", BODY)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.listingId").value(LISTING))
                .andExpect(jsonPath("$.quantityAvailable").value(30000))
                .andReturn().getResponse().getContentAsString();

        FrozenSchemas.assertApiSchema(FrozenSchemas.SUPPLEMENTARY_API, "ListingResponse", objectMapper.readTree(body));
    }

    @Test
    void update_withoutIdempotencyKey_is400() throws Exception {
        mockMvc.perform(put("/api/v1/listings/{id}", LISTING)
                        .header("X-Gateway-Secret", "gw-secret")
                        .header("X-User-Id", SELLER).header("X-User-Role", "SELLER")
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"));
        verifyNoInteractions(updateListingUseCase);
    }

    @Test
    void update_byABuyer_is403() throws Exception {
        updateAs("BUYER", BODY).andExpect(status().isForbidden());
        verifyNoInteractions(updateListingUseCase);
    }

    @Test
    void update_byAnotherSeller_is403_notListingSeller() throws Exception {
        when(updateListingUseCase.execute(eq(SELLER), eq(LISTING), eq("edit-1"), any()))
                .thenThrow(new NotListingSellerException());

        updateAs("SELLER", BODY)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("NOT_LISTING_SELLER"));
    }

    @Test
    void update_belowTheReservedQuantity_is409() throws Exception {
        when(updateListingUseCase.execute(eq(SELLER), eq(LISTING), eq("edit-1"), any()))
                .thenThrow(new OfferedQuantityBelowReservedException(new BigDecimal("20000")));

        updateAs("SELLER", BODY)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("OFFERED_QUANTITY_BELOW_RESERVED"));
    }
}
