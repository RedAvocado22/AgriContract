package com.agricontract.contract.domain.model.terms;

import java.util.UUID;

/** Goods identity and origin frozen at contract creation. Later listing/plot edits never change it. */
public record CoffeeGoodsTerms(
        String commodity,
        UUID listingId,
        UUID productId,
        UUID categoryId,
        String categoryName,
        String productName,
        String unitOfMeasure,
        Integer cropYear,
        String harvestSeason,
        PlotBackedOriginSnapshot originSnapshot,
        TraceabilitySnapshot traceabilitySnapshot) {
}
