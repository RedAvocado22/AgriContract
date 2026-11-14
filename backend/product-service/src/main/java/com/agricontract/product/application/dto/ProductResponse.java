package com.agricontract.product.application.dto;

import com.agricontract.product.domain.model.Product;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;

import java.util.List;

@Builder
@JsonInclude(JsonInclude.Include.ALWAYS)
public record ProductResponse(
        String productId,
        String name,
        String unit,
        String categoryId,
        List<String> images,
        Integer cropYear,
        String harvestSeason,
        List<String> certifications
) {
    public static ProductResponse from(Product product) {
        return new ProductResponse(product.getProductId().value(), product.getName(), product.getUnit(),
                product.getCategoryId(), product.getImages(), product.getCropYear(), product.getHarvestSeason(),
                product.getCertifications());
    }
}
