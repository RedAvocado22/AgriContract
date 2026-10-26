package com.agricontract.inspection.domain.model;

import java.math.BigDecimal;
import java.util.List;
import static com.agricontract.inspection.domain.model.HashMaterialValidation.*;

/** Null reasons means absent, not JSON null. Strings retain their committed representation. */
public record InspectionSettlementResultV1(String resultSchemaVersion, BigDecimal measuredQuantityKg,
        BigDecimal acceptedQuantityKg, MeasurementStatus measurementStatus, CoffeeActualMetrics actualQualityMetrics,
        List<String> inconclusiveReasons, String determinedAt) {
    public InspectionSettlementResultV1 {
        require("1.0".equals(resultSchemaVersion), "Unsupported result schema version");
        quantity(measuredQuantityKg);
        quantity(acceptedQuantityKg);
        require(acceptedQuantityKg.compareTo(measuredQuantityKg) <= 0, "Accepted quantity exceeds measured quantity");
        require(measurementStatus != null && actualQualityMetrics != null, "Status and metrics required");
        require(measurementStatus != MeasurementStatus.INCONCLUSIVE || inconclusiveReasons != null,
                "Inconclusive reasons required");
        if (inconclusiveReasons != null) {
            require(!inconclusiveReasons.isEmpty() && inconclusiveReasons.stream().allMatch(s -> s != null && !s.isBlank()),
                    "Nonblank inconclusive reasons required");
            inconclusiveReasons = List.copyOf(inconclusiveReasons);
        }
        timestamp(determinedAt);
    }
}
