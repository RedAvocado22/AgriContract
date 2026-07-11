package com.agricontract.product.application.dto;

import lombok.Builder;

import java.util.List;

@Builder
public record ProductResponse(
        String productId,
        String name,
        String unit,
        String categoryId,
        List<String> images
) {}
