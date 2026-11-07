package com.agricontract.product.domain.model.vo;

import java.util.List;

/**
 * Answer of the 2020 forest baseline for one plot geometry that lies inside the dataset's coverage (design §2.2.4).
 * The risk level follows from the dataset's own classes, with no threshold chosen here: any natural-forest overlap is
 * evidence of risk, an ambiguous-only overlap is an answer that cannot be called either way.
 */
public record ForestBaselineAssessment(String datasetVersion, List<ForestOverlap> overlaps) {

    public ForestBaselineAssessment {
        if (datasetVersion == null || datasetVersion.isBlank()) {
            throw new IllegalArgumentException("datasetVersion must not be blank");
        }
        overlaps = List.copyOf(overlaps);
    }

    public GeoRiskLevel riskLevel() {
        if (overlaps.stream().anyMatch(o -> o.forestClass() == ForestClass.NATURAL_FOREST)) {
            return GeoRiskLevel.HIGH_RISK;
        }
        return overlaps.isEmpty() ? GeoRiskLevel.LOW_RISK : GeoRiskLevel.INCONCLUSIVE;
    }

    public List<String> matchedBaselineAreaIds() {
        return overlaps.stream().map(ForestOverlap::baselineAreaId).sorted().toList();
    }
}
