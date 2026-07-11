package com.agricontract.product.infrastructure.web;

import com.agricontract.product.application.dto.CategoryResponse;
import com.agricontract.product.application.dto.ProposeCategoryRequest;
import com.agricontract.product.application.dto.RejectCategoryRequest;
import com.agricontract.product.application.usecase.ApproveCategoryUseCase;
import com.agricontract.product.application.usecase.ProposeCategoryUseCase;
import com.agricontract.product.application.usecase.RejectCategoryUseCase;
import com.agricontract.product.common.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/categories")
@RequiredArgsConstructor
public class CategoryController {

    private final ProposeCategoryUseCase proposeCategoryUseCase;
    private final ApproveCategoryUseCase approveCategoryUseCase;
    private final RejectCategoryUseCase rejectCategoryUseCase;

    @PreAuthorize("hasAnyRole('SELLER','BUYER')")
    @PostMapping
    public ResponseEntity<ApiResponse<CategoryResponse>> propose(
            @RequestBody @Valid ProposeCategoryRequest request,
            @RequestHeader("X-User-Id") String userId,
            @RequestHeader("X-User-Email") String userEmail) {
        CategoryResponse response = proposeCategoryUseCase.execute(request, userId, userEmail);
        return ResponseEntity.status(201).body(ApiResponse.ok(response));
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PutMapping("/{categoryId}/approve")
    public ResponseEntity<ApiResponse<CategoryResponse>> approve(@PathVariable String categoryId) {
        CategoryResponse response = approveCategoryUseCase.execute(categoryId);
        return ResponseEntity.ok(ApiResponse.ok(response));
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PutMapping("/{categoryId}/reject")
    public ResponseEntity<ApiResponse<CategoryResponse>> reject(
            @PathVariable String categoryId,
            @RequestBody @Valid RejectCategoryRequest request) {
        CategoryResponse response = rejectCategoryUseCase.execute(categoryId, request.reason());
        return ResponseEntity.ok(ApiResponse.ok(response));
    }
}
