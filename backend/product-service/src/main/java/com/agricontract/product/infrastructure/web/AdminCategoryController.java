package com.agricontract.product.infrastructure.web;

import com.agricontract.product.application.dto.CategoryCommodityRequest;
import com.agricontract.product.application.dto.CategoryResponse;
import com.agricontract.product.application.dto.RejectCategoryRequest;
import com.agricontract.product.application.usecase.ApproveCategoryUseCase;
import com.agricontract.product.application.usecase.AssignCategoryCommodityUseCase;
import com.agricontract.product.application.usecase.RejectCategoryUseCase;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

// Category moderation under the canonical admin prefix (api-gateway design §3), OPERATOR only, no ADMIN fallback
// (data-governance §5.4).
@RestController
@RequestMapping("/api/v1/admin/categories")
@RequiredArgsConstructor
@PreAuthorize("hasRole('OPERATOR')")
public class AdminCategoryController {

    private final ApproveCategoryUseCase approveCategoryUseCase;
    private final RejectCategoryUseCase rejectCategoryUseCase;
    private final AssignCategoryCommodityUseCase assignCategoryCommodityUseCase;

    // The Operator picks the commodity on approval; a body without it is 400.
    @PutMapping("/{categoryId}/approve")
    public ResponseEntity<CategoryResponse> approve(
            @PathVariable String categoryId,
            @RequestBody @Valid CategoryCommodityRequest request) {
        return ResponseEntity.ok(approveCategoryUseCase.execute(categoryId, request.commodity()));
    }

    @PutMapping("/{categoryId}/reject")
    public ResponseEntity<CategoryResponse> reject(
            @PathVariable String categoryId,
            @RequestBody @Valid RejectCategoryRequest request) {
        return ResponseEntity.ok(rejectCategoryUseCase.execute(categoryId, request.reason()));
    }

    // Maps a category approved before V7 (commodity NULL) so it can back products and listings again.
    @PutMapping("/{categoryId}/commodity")
    public ResponseEntity<CategoryResponse> assignCommodity(
            @PathVariable String categoryId,
            @RequestBody @Valid CategoryCommodityRequest request) {
        return ResponseEntity.ok(assignCategoryCommodityUseCase.execute(categoryId, request.commodity()));
    }
}
