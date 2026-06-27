package com.agricontract.product.application.dto;

import jakarta.validation.constraints.NotBlank;

public record CreateProductRequest(
        @NotBlank String name,
        @NotBlank String unit,
        String category
) {}
