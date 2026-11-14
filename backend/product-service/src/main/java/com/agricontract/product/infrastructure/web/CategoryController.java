package com.agricontract.product.infrastructure.web;

import com.agricontract.product.application.dto.CategoryResponse;
import com.agricontract.product.application.dto.CategorySummary;
import com.agricontract.product.application.dto.ProposeCategoryRequest;
import com.agricontract.product.application.usecase.ListApprovedCategoriesUseCase;
import com.agricontract.product.application.usecase.ProposeCategoryUseCase;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

// Approve/reject are moderation and live in AdminCategoryController (OPERATOR, api-gateway design §3.2).
@RestController
@RequestMapping("/api/v1/categories")
@RequiredArgsConstructor
public class CategoryController {

    private final ProposeCategoryUseCase proposeCategoryUseCase;
    private final ListApprovedCategoriesUseCase listApprovedCategoriesUseCase;

    // listApprovedCategories: any signed-in caller (default authenticated rule), not paged.
    @GetMapping
    public ResponseEntity<List<CategorySummary>> listApproved() {
        return ResponseEntity.ok(listApprovedCategoriesUseCase.execute());
    }

    @PreAuthorize("hasAnyRole('SELLER','BUYER')")
    @PostMapping
    public ResponseEntity<CategoryResponse> propose(
            @RequestBody @Valid ProposeCategoryRequest request,
            @RequestHeader("X-User-Id") String userId) {
        CategoryResponse response = proposeCategoryUseCase.execute(request, userId);
        return ResponseEntity.status(201).body(response);
    }
}
