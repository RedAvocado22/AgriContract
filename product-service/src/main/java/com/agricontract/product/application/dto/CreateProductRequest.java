package com.agricontract.product.application.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

public record CreateProductRequest(
        String productId,
        @NotBlank String name,
        @NotBlank String unit,
        @NotBlank String categoryId,
        @NotEmpty @Size(min = 1, max = 5) List<String> imageUrls
) {}
