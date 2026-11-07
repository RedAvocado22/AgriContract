package com.agricontract.product.application.dto;

import com.agricontract.product.domain.model.vo.Commodity;
import com.agricontract.product.domain.model.vo.GeometryType;
import com.agricontract.product.domain.model.vo.VerificationLevel;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

/**
 * Bare body of GET /internal/v1/listings/{listingId}/contract-snapshot (supplementary API ListingContractSnapshot):
 * the golden-flow GoodsTermsSnapshot without traceabilitySnapshot, stored by contract-service as goodsTerms unchanged.
 * cropYear and harvestSeason are always written, also when null, because the contract requires both keys.
 */
@JsonInclude(JsonInclude.Include.ALWAYS)
public record ListingContractSnapshotResponse(
        Commodity commodity,
        String listingId,
        String productId,
        String categoryId,
        String categoryName,
        String productName,
        String unitOfMeasure,
        Integer cropYear,
        String harvestSeason,
        OriginSnapshot originSnapshot
) {
    public static final String UNIT_OF_MEASURE = "KG";

    public record OriginSnapshot(String regionText, List<PlotRef> plotRefs) {
        public OriginSnapshot {
            plotRefs = List.copyOf(plotRefs);
        }
    }

    public record PlotRef(
            String registryEntryId,
            String householdLabel,
            GeometryType geometryType,
            String geometryHash,
            VerificationLevel verificationLevel
    ) {
    }
}
