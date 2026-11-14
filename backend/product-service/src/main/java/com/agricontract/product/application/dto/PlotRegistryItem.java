package com.agricontract.product.application.dto;

import com.agricontract.product.domain.model.PlotRegistryEntry;
import com.agricontract.product.domain.model.vo.GeoRiskLevel;
import com.agricontract.product.domain.model.vo.GeoVerificationStatus;
import com.agricontract.product.domain.model.vo.GeometryType;
import com.agricontract.product.domain.model.vo.PlotSource;
import com.agricontract.product.domain.model.vo.Province;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * listMyPlots item: what the seller needs to pick plots for a listing. No geometry (design §4); the contract also
 * gives a POINT entry no area, so the area a manual pin declared stays out of this list.
 */
@JsonInclude(JsonInclude.Include.ALWAYS)
public record PlotRegistryItem(
        String registryEntryId,
        String householdLabel,
        GeometryType geometryType,
        BigDecimal areaHectares,
        PlotSource source,
        Province declaredProvince,
        GeoVerificationStatus geoVerificationStatus,
        GeoRiskLevel geoRiskLevel,
        Instant importedAt
) {
    public static PlotRegistryItem from(PlotRegistryEntry entry) {
        GeometryType type = entry.getGeometry().type();
        return new PlotRegistryItem(
                entry.getRegistryEntryId().value(),
                entry.getHouseholdLabel(),
                type,
                type == GeometryType.POINT ? null : entry.getGeometry().areaHectares(),
                entry.getSource(),
                entry.getDeclaredProvince(),
                entry.getGeoVerificationStatus(),
                entry.getGeoRiskLevel(),
                entry.getImportedAt()
        );
    }
}
