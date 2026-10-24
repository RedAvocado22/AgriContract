package com.agricontract.product.application.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

public record CreateProductRequest(
        String productId,
        @NotBlank String name,
        @NotBlank String unit,
        @NotBlank String categoryId,
        @NotEmpty @Size(min = 1, max = 5) List<String> imageUrls,
        // optional season data (design §8c); shown on listings and frozen into contract goods snapshots
        @Min(2000) @Max(2100) Integer cropYear,
        @Size(max = 100) String harvestSeason,
        @Size(max = 20) List<@NotBlank @Size(max = 200) String> certifications
) {}
