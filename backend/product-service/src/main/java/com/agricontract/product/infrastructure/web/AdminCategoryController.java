package com.agricontract.product.infrastructure.web;

import com.agricontract.product.application.dto.CategoryCommodityRequest;
import com.agricontract.product.application.dto.CategoryResponse;
import com.agricontract.product.application.usecase.AssignCategoryCommodityUseCase;
import com.agricontract.product.common.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

// Category moderation under the canonical admin prefix (api-gateway design §3), OPERATOR only (data-governance §5.4).
@RestController
@RequestMapping("/api/v1/admin/categories")
@RequiredArgsConstructor
public class AdminCategoryController {

    private final AssignCategoryCommodityUseCase assignCategoryCommodityUseCase;

    // Maps a category approved before V7 (commodity NULL) so it can back products and listings again.
    @PreAuthorize("hasRole('OPERATOR')")
    @PutMapping("/{categoryId}/commodity")
    public ResponseEntity<ApiResponse<CategoryResponse>> assignCommodity(
            @PathVariable String categoryId,
            @RequestBody @Valid CategoryCommodityRequest request) {
        CategoryResponse response = assignCategoryCommodityUseCase.execute(categoryId, request.commodity());
        return ResponseEntity.ok(ApiResponse.ok(response));
    }
}
