package com.agricontract.product.application.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

public record UpdateProductImagesRequest(
        @NotEmpty @Size(min = 1, max = 5) List<String> imageUrls
) {}
