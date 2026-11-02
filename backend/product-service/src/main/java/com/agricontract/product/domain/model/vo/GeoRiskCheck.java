package com.agricontract.product.domain.model.vo;

import java.time.Instant;
import java.util.List;

/**
 * Audit record of one forest-baseline result applied to a plot: the exact geometry checked (by hash), the dataset
 * version and the baseline areas behind the level. Never updated once stored.
 */
public record GeoRiskCheck(RegistryEntryId registryEntryId, String geometryHash, String datasetVersion,
                           GeoRiskLevel riskLevel, List<String> matchedBaselineAreaIds, Instant checkedAt) {
    public GeoRiskCheck {
        matchedBaselineAreaIds = List.copyOf(matchedBaselineAreaIds);
    }
}
