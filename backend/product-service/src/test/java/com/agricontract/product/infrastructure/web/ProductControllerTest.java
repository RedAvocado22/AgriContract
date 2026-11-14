package com.agricontract.product.infrastructure.web;

import com.agricontract.product.application.dto.ProductResponse;
import com.agricontract.product.application.usecase.CreateProductUseCase;
import com.agricontract.product.application.usecase.GetProductUseCase;
import com.agricontract.product.application.usecase.UpdateProductImagesUseCase;
import com.agricontract.product.common.exception.ProductNotFoundException;
import com.agricontract.product.infrastructure.config.SecurityConfig;
import com.agricontract.product.infrastructure.contract.FrozenSchemas;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Boundary of getProduct: public read, bare ProductResponse exactly as frozen, unknown id 404. */
@WebMvcTest(ProductController.class)
@Import(SecurityConfig.class)
@TestPropertySource(properties = {"gateway.internal-secret=gw-secret", "service.internal-secret=svc-secret"})
class ProductControllerTest {

    private static final String PRODUCT_ID = "3f9a1c2e-7b4d-4e8f-9a0b-1c2d3e4f5a6b";

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @MockBean private CreateProductUseCase createProductUseCase;
    @MockBean private UpdateProductImagesUseCase updateProductImagesUseCase;
    @MockBean private GetProductUseCase getProductUseCase;

    private static ProductResponse product(Integer cropYear, String harvestSeason, List<String> certifications) {
        return new ProductResponse(PRODUCT_ID, "Robusta Buôn Ma Thuột", "kg",
                "0b6f7a2e-5c1d-4f4e-9a51-2a8d3c1e9f01", List.of("https://cdn.example.com/p/1.jpg"),
                cropYear, harvestSeason, certifications);
    }

    @Test
    void anonymous_found_is200_withFrozenProductResponse() throws Exception {
        when(getProductUseCase.execute(PRODUCT_ID)).thenReturn(product(2026, "2025-2026", List.of("4C")));

        String body = mockMvc.perform(get("/api/v1/products/" + PRODUCT_ID).header("X-Gateway-Secret", "gw-secret"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.productId").value(PRODUCT_ID))
                .andReturn().getResponse().getContentAsString();

        FrozenSchemas.assertApiSchema(FrozenSchemas.SUPPLEMENTARY_API, "ProductResponse", objectMapper.readTree(body));
    }

    @Test
    void optionalFields_areSentAsNull() throws Exception {
        when(getProductUseCase.execute(PRODUCT_ID)).thenReturn(product(null, null, null));

        String body = mockMvc.perform(get("/api/v1/products/" + PRODUCT_ID).header("X-Gateway-Secret", "gw-secret"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cropYear").value(nullValue()))
                .andReturn().getResponse().getContentAsString();

        JsonNode json = objectMapper.readTree(body);
        assertThat(json.has("harvestSeason")).isTrue();
        assertThat(json.has("certifications")).isTrue();
        FrozenSchemas.assertApiSchema(FrozenSchemas.SUPPLEMENTARY_API, "ProductResponse", json);
    }

    @Test
    void unknownOrNonUuidId_is404() throws Exception {
        for (String id : new String[]{"9e8d7c6b-5a4f-4e3d-8c2b-1a0f9e8d7c6b", "not-a-uuid"}) {
            when(getProductUseCase.execute(id)).thenThrow(new ProductNotFoundException(id));

            mockMvc.perform(get("/api/v1/products/" + id).header("X-Gateway-Secret", "gw-secret"))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("PRODUCT_NOT_FOUND"));
        }
    }
}
