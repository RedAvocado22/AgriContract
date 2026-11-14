package com.agricontract.product.infrastructure.web;

import com.agricontract.product.application.dto.CategoryResponse;
import com.agricontract.product.application.dto.ProposeCategoryRequest;
import com.agricontract.product.application.usecase.ProposeCategoryUseCase;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

// Approve/reject are moderation and live in AdminCategoryController (OPERATOR, api-gateway design §3.2).
@RestController
@RequestMapping("/api/v1/categories")
@RequiredArgsConstructor
public class CategoryController {

    private final ProposeCategoryUseCase proposeCategoryUseCase;

    @PreAuthorize("hasAnyRole('SELLER','BUYER')")
    @PostMapping
    public ResponseEntity<CategoryResponse> propose(
            @RequestBody @Valid ProposeCategoryRequest request,
            @RequestHeader("X-User-Id") String userId) {
        CategoryResponse response = proposeCategoryUseCase.execute(request, userId);
        return ResponseEntity.status(201).body(response);
    }
}
