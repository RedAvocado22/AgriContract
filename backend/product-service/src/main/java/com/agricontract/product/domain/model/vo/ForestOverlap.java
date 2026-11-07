package com.agricontract.product.domain.model.vo;

// One baseline area a plot geometry intersects.
public record ForestOverlap(String baselineAreaId, ForestClass forestClass) {
    public ForestOverlap {
        if (baselineAreaId == null || baselineAreaId.isBlank()) {
            throw new IllegalArgumentException("baselineAreaId must not be blank");
        }
        if (forestClass == null) {
            throw new IllegalArgumentException("forestClass must not be null");
        }
    }
}
