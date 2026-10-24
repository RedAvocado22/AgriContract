package com.agricontract.product.application.dto;

import com.agricontract.product.domain.model.PlotRegistryEntry;
import com.agricontract.product.domain.model.vo.GeoRiskLevel;
import com.agricontract.product.domain.model.vo.GeoVerificationStatus;
import com.agricontract.product.domain.model.vo.GeometryType;
import com.agricontract.product.domain.model.vo.PlotSource;
import com.agricontract.product.domain.model.vo.Province;
import com.agricontract.product.domain.model.vo.VerificationLevel;

import java.math.BigDecimal;
import java.time.Instant;

public record PlotResponse(
        String registryEntryId,
        String householdLabel,
        GeometryType geometryType,
        String geometryWkt,
        String geometryHash,
        BigDecimal areaHectares,
        PlotSource source,
        BigDecimal accuracyMeters,
        VerificationLevel verificationLevel,
        Province declaredProvince,
        GeoVerificationStatus geoVerificationStatus,
        GeoRiskLevel geoRiskLevel,
        Instant importedAt
) {
    public static PlotResponse from(PlotRegistryEntry entry) {
        return new PlotResponse(
                entry.getRegistryEntryId().value(),
                entry.getHouseholdLabel(),
                entry.getGeometry().type(),
                entry.getGeometry().wkt(),
                entry.getGeometry().geometryHash(),
                entry.getGeometry().areaHectares(),
                entry.getSource(),
                entry.getAccuracyMeters(),
                entry.getVerificationLevel(),
                entry.getDeclaredProvince(),
                entry.getGeoVerificationStatus(),
                entry.getGeoRiskLevel(),
                entry.getImportedAt()
        );
    }
}
