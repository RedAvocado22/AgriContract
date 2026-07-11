package com.agricontract.product.application.dto;

import jakarta.validation.constraints.NotBlank;

public record CreateProductRequest(
        String productId,
        @NotBlank String name,
        @NotBlank String unit,
        String category
) {}
