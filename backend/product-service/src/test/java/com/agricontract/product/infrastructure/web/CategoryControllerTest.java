package com.agricontract.product.infrastructure.web;

import com.agricontract.product.application.dto.CategoryResponse;
import com.agricontract.product.application.usecase.ApproveCategoryUseCase;
import com.agricontract.product.application.usecase.AssignCategoryCommodityUseCase;
import com.agricontract.product.application.usecase.ProposeCategoryUseCase;
import com.agricontract.product.application.usecase.RejectCategoryUseCase;
import com.agricontract.product.domain.model.vo.CategoryStatus;
import com.agricontract.product.domain.model.vo.Commodity;
import com.agricontract.product.infrastructure.config.SecurityConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
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
    private static final String APPROVE = "/api/v1/categories/cat-1/approve";
    private static final String ASSIGN = "/api/v1/admin/categories/cat-1/commodity";

    @Autowired private MockMvc mockMvc;
    @MockBean private ProposeCategoryUseCase proposeCategoryUseCase;
    @MockBean private ApproveCategoryUseCase approveCategoryUseCase;
    @MockBean private RejectCategoryUseCase rejectCategoryUseCase;
    @MockBean private AssignCategoryCommodityUseCase assignCategoryCommodityUseCase;

    private static MockHttpServletRequestBuilder as(String role, MockHttpServletRequestBuilder request) {
        return request.header("X-Gateway-Secret", "gw-secret")
                .header("X-User-Id", "user-1").header("X-User-Role", role)
                .contentType(MediaType.APPLICATION_JSON);
    }

    private static CategoryResponse approvedCoffee() {
        return CategoryResponse.builder().categoryId("cat-1").name("Robusta").status(CategoryStatus.APPROVED)
                .proposedBy("seller-1").commodity(Commodity.COFFEE).build();
    }

    // ---------- approve (legacy path, still ADMIN) ----------

    @Test
    void approve_withCommodity_is200() throws Exception {
        when(approveCategoryUseCase.execute("cat-1", Commodity.COFFEE)).thenReturn(approvedCoffee());

        mockMvc.perform(as("ADMIN", put(APPROVE)).content(COFFEE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("APPROVED"))
                .andExpect(jsonPath("$.data.commodity").value("COFFEE"));
    }

    @Test
    void approve_withoutBody_is400() throws Exception {
        mockMvc.perform(as("ADMIN", put(APPROVE)))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(approveCategoryUseCase);
    }

    @Test
    void approve_missingOrUnknownCommodity_is400() throws Exception {
        mockMvc.perform(as("ADMIN", put(APPROVE)).content("{}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(as("ADMIN", put(APPROVE)).content("{\"commodity\":\"RICE\"}"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(approveCategoryUseCase);
    }

    // ---------- assign commodity (admin prefix, OPERATOR) ----------

    @Test
    void assign_asOperator_is200() throws Exception {
        when(assignCategoryCommodityUseCase.execute("cat-1", Commodity.COFFEE)).thenReturn(approvedCoffee());

        mockMvc.perform(as("OPERATOR", put(ASSIGN)).content(COFFEE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.commodity").value("COFFEE"));
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
}
