package com.agricontract.product.infrastructure.persistence.mapper;

import com.agricontract.product.domain.model.PlotRegistryEntry;
import com.agricontract.product.domain.model.vo.ListingPlot;
import com.agricontract.product.domain.model.vo.PlotGeometry;
import com.agricontract.product.domain.model.vo.RegistryEntryId;
import com.agricontract.product.domain.service.PlotGeometries;
import com.agricontract.product.infrastructure.persistence.entity.ListingPlotJpaEntity;
import com.agricontract.product.infrastructure.persistence.entity.PlotRegistryEntryJpaEntity;
import org.mapstruct.Mapper;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

@Mapper(componentModel = "spring")
public interface PlotRegistryMapper {

    default PlotRegistryEntryJpaEntity toJpaEntity(PlotRegistryEntry domain) {
        PlotRegistryEntryJpaEntity entity = new PlotRegistryEntryJpaEntity();
        entity.setRegistryEntryId(domain.getRegistryEntryId().value());
        entity.setSellerId(domain.getSellerId());
        entity.setHouseholdLabel(domain.getHouseholdLabel());
        copyMutable(domain, entity);
        return entity;
    }

    // everything a re-import or the geo check may change; identity and owner never change
    default void copyMutable(PlotRegistryEntry domain, PlotRegistryEntryJpaEntity entity) {
        entity.setGeometryType(domain.getGeometry().type());
        entity.setGeometryWkt(domain.getGeometry().wkt());
        entity.setGeometryHash(domain.getGeometry().geometryHash());
        entity.setAreaHectares(domain.getGeometry().areaHectares());
        entity.setSource(domain.getSource());
        entity.setAccuracyMeters(domain.getAccuracyMeters());
        entity.setVerificationLevel(domain.getVerificationLevel());
        entity.setDeclaredProvince(domain.getDeclaredProvince());
        entity.setGeoVerificationStatus(domain.getGeoVerificationStatus());
        entity.setGeoRiskLevel(domain.getGeoRiskLevel());
        entity.setImportedAt(toUtc(domain.getImportedAt()));
    }

    default PlotRegistryEntry toDomain(PlotRegistryEntryJpaEntity entity) {
        return PlotRegistryEntry.reconstruct(
                new RegistryEntryId(entity.getRegistryEntryId()),
                entity.getSellerId(),
                entity.getHouseholdLabel(),
                new PlotGeometry(entity.getGeometryType(), PlotGeometries.canonicalWkt(entity.getGeometryWkt()),
                        entity.getAreaHectares(), entity.getGeometryHash()),
                entity.getSource(),
                entity.getAccuracyMeters(),
                entity.getVerificationLevel(),
                entity.getDeclaredProvince(),
                entity.getGeoVerificationStatus(),
                entity.getGeoRiskLevel(),
                entity.getImportedAt().toInstant(ZoneOffset.UTC)
        );
    }

    default ListingPlotJpaEntity toJpaEntity(String listingId, ListingPlot plot, Instant declaredAt) {
        return ListingPlotJpaEntity.builder()
                .listingId(listingId)
                .sourceRegistryEntryId(plot.sourceRegistryEntryId().value())
                .householdLabel(plot.householdLabel())
                .geometryType(plot.geometry().type())
                .geometryWkt(plot.geometry().wkt())
                .geometryHash(plot.geometry().geometryHash())
                .areaHectares(plot.geometry().areaHectares())
                .verificationLevel(plot.verificationLevel())
                .declaredAt(toUtc(declaredAt))
                .build();
    }

    default ListingPlot toDomain(ListingPlotJpaEntity entity) {
        return new ListingPlot(
                new RegistryEntryId(entity.getSourceRegistryEntryId()),
                entity.getHouseholdLabel(),
                new PlotGeometry(entity.getGeometryType(), PlotGeometries.canonicalWkt(entity.getGeometryWkt()),
                        entity.getAreaHectares(), entity.getGeometryHash()),
                entity.getVerificationLevel()
        );
    }

    private static LocalDateTime toUtc(Instant instant) {
        return LocalDateTime.ofInstant(instant, ZoneOffset.UTC);
    }
}
