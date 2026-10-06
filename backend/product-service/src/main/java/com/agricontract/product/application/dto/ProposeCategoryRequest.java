package com.agricontract.product.application.dto;

import jakarta.validation.constraints.NotBlank;

public record ProposeCategoryRequest(
        String categoryId,
        @NotBlank String name
) {}
