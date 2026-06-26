package com.agricontract.product.application.dto;

import lombok.Builder;

@Builder
public record ProductResponse(
        String productId,
        String name,
        String unit,
        String category
) {}
