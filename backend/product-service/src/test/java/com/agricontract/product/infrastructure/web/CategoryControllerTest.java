package com.agricontract.product.infrastructure.web;

import com.agricontract.product.application.dto.CategoryResponse;
import com.agricontract.product.application.dto.CategorySummary;
import com.agricontract.product.application.usecase.ApproveCategoryUseCase;
import com.agricontract.product.application.usecase.AssignCategoryCommodityUseCase;
import com.agricontract.product.application.usecase.ListApprovedCategoriesUseCase;
import com.agricontract.product.application.usecase.ProposeCategoryUseCase;
import com.agricontract.product.application.usecase.RejectCategoryUseCase;
import com.agricontract.product.domain.model.vo.CategoryStatus;
import com.agricontract.product.domain.model.vo.Commodity;
import com.agricontract.product.infrastructure.config.SecurityConfig;
import com.agricontract.product.infrastructure.contract.FrozenSchemas;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Boundary of category moderation: approval now requires the commodity the Operator picks, and the one-time
 * mapping of a pre-V7 category lives under the canonical admin prefix with OPERATOR only.
 */
@WebMvcTest({CategoryController.class, AdminCategoryController.class})
@Import(SecurityConfig.class)
@TestPropertySource(properties = {"gateway.internal-secret=gw-secret", "service.internal-secret=svc-secret"})
class CategoryControllerTest {

    private static final String COFFEE = "{\"commodity\":\"COFFEE\"}";
    private static final String APPROVE = "/api/v1/admin/categories/cat-1/approve";
    private static final String REJECT = "/api/v1/admin/categories/cat-1/reject";
    private static final String OLD_APPROVE = "/api/v1/categories/cat-1/approve";
    private static final String OLD_REJECT = "/api/v1/categories/cat-1/reject";
    private static final String ASSIGN = "/api/v1/admin/categories/cat-1/commodity";

    @Autowired private MockMvc mockMvc;
    @MockBean private ProposeCategoryUseCase proposeCategoryUseCase;
    @MockBean private ApproveCategoryUseCase approveCategoryUseCase;
    @MockBean private RejectCategoryUseCase rejectCategoryUseCase;
    @MockBean private AssignCategoryCommodityUseCase assignCategoryCommodityUseCase;
    @MockBean private ListApprovedCategoriesUseCase listApprovedCategoriesUseCase;
    @Autowired private ObjectMapper objectMapper;

    private static MockHttpServletRequestBuilder as(String role, MockHttpServletRequestBuilder request) {
        return request.header("X-Gateway-Secret", "gw-secret")
                .header("X-User-Id", "user-1").header("X-User-Role", role)
                .contentType(MediaType.APPLICATION_JSON);
    }

    private static CategoryResponse approvedCoffee() {
        return CategoryResponse.builder().categoryId("cat-1").name("Robusta").status(CategoryStatus.APPROVED)
                .proposedBy("seller-1").commodity(Commodity.COFFEE).build();
    }

    // ---------- approve / reject (admin prefix, OPERATOR only) ----------

    @Test
    void approve_withCommodity_is200() throws Exception {
        when(approveCategoryUseCase.execute("cat-1", Commodity.COFFEE)).thenReturn(approvedCoffee());

        mockMvc.perform(as("OPERATOR", put(APPROVE)).content(COFFEE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"))
                .andExpect(jsonPath("$.commodity").value("COFFEE"));
    }

    @Test
    void approve_withoutBody_is400() throws Exception {
        mockMvc.perform(as("OPERATOR", put(APPROVE)))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(approveCategoryUseCase);
    }

    @Test
    void approve_missingOrUnknownCommodity_is400() throws Exception {
        mockMvc.perform(as("OPERATOR", put(APPROVE)).content("{}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(as("OPERATOR", put(APPROVE)).content("{\"commodity\":\"RICE\"}"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(approveCategoryUseCase);
    }

    @Test
    void approveOrReject_asAdminOrSeller_is403() throws Exception {
        for (String role : new String[]{"ADMIN", "SELLER"}) {
            mockMvc.perform(as(role, put(APPROVE)).content(COFFEE))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("FORBIDDEN"));
            mockMvc.perform(as(role, put(REJECT)).content("{\"reason\":\"duplicate\"}"))
                    .andExpect(status().isForbidden());
        }
        verifyNoInteractions(approveCategoryUseCase, rejectCategoryUseCase);
    }

    @Test
    void reject_asOperator_is200() throws Exception {
        when(rejectCategoryUseCase.execute("cat-1", "duplicate")).thenReturn(CategoryResponse.builder()
                .categoryId("cat-1").status(CategoryStatus.REJECTED).build());

        mockMvc.perform(as("OPERATOR", put(REJECT)).content("{\"reason\":\"duplicate\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REJECTED"));
    }

    @Test
    void phase1Paths_areGone() throws Exception {
        for (String role : new String[]{"ADMIN", "OPERATOR"}) {
            mockMvc.perform(as(role, put(OLD_APPROVE)).content(COFFEE))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("NOT_FOUND"));
            mockMvc.perform(as(role, put(OLD_REJECT)).content("{\"reason\":\"duplicate\"}"))
                    .andExpect(status().isNotFound());
        }
        verifyNoInteractions(approveCategoryUseCase, rejectCategoryUseCase);
    }

    // ---------- assign commodity (admin prefix, OPERATOR) ----------


    @Test
    void assign_asOperator_is200() throws Exception {
        when(assignCategoryCommodityUseCase.execute("cat-1", Commodity.COFFEE)).thenReturn(approvedCoffee());

        mockMvc.perform(as("OPERATOR", put(ASSIGN)).content(COFFEE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.commodity").value("COFFEE"));
    }

    @Test
    void assign_asAdminOrSeller_is403() throws Exception {
        mockMvc.perform(as("ADMIN", put(ASSIGN)).content(COFFEE))
                .andExpect(status().isForbidden());
        mockMvc.perform(as("SELLER", put(ASSIGN)).content(COFFEE))
                .andExpect(status().isForbidden());
        verifyNoInteractions(assignCategoryCommodityUseCase);
    }

    @Test
    void assign_alreadyMapped_is409() throws Exception {
        when(assignCategoryCommodityUseCase.execute(any(), any()))
                .thenThrow(new IllegalStateException("Category commodity is already assigned"));

        mockMvc.perform(as("OPERATOR", put(ASSIGN)).content(COFFEE))
                .andExpect(status().isConflict());
    }

    @Test
    void assign_missingOrUnknownCommodity_is400() throws Exception {
        mockMvc.perform(as("OPERATOR", put(ASSIGN)).content("{}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(as("OPERATOR", put(ASSIGN)).content("{\"commodity\":\"CASHEW\"}"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(assignCategoryCommodityUseCase);
    }

    // ---------- listApprovedCategories (any signed-in caller) ----------

    @Test
    void listApproved_isBareArrayOfFrozenCategorySummary_legacyCommodityNull() throws Exception {
        when(listApprovedCategoriesUseCase.execute()).thenReturn(List.of(
                new CategorySummary("0b6f7a2e-5c1d-4f4e-9a51-2a8d3c1e9f01", "Cà phê", Commodity.COFFEE),
                new CategorySummary("7d4c1b9a-3e2f-4a6b-8c5d-1f0e9a8b7c02", "Hồ tiêu", null)));

        for (String role : new String[]{"BUYER", "SELLER", "OPERATOR"}) {
            String body = mockMvc.perform(as(role, get("/api/v1/categories")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[0].commodity").value("COFFEE"))
                    .andExpect(jsonPath("$[1].commodity").value(nullValue()))
                    .andReturn().getResponse().getContentAsString();

            JsonNode array = objectMapper.readTree(body);
            assertThat(array.isArray()).isTrue();
            assertThat(array.get(1).has("commodity")).as("null commodity is sent, not dropped").isTrue();
            for (JsonNode item : array) {
                FrozenSchemas.assertApiSchema(FrozenSchemas.SUPPLEMENTARY_API, "CategorySummary", item);
            }
        }
    }

    @Test
    void listApproved_withoutSignedInCaller_isRefused() throws Exception {
        mockMvc.perform(get("/api/v1/categories").header("X-Gateway-Secret", "gw-secret"))
                .andExpect(status().is4xxClientError());
        verifyNoInteractions(listApprovedCategoriesUseCase);
    }
}
