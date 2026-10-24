package com.agricontract.product.infrastructure.persistence.entity;

import com.agricontract.product.domain.model.vo.GeometryType;
import com.agricontract.product.domain.model.vo.VerificationLevel;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.ColumnTransformer;
import org.hibernate.annotations.Immutable;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

// Insert-only copy of a registry plot taken when the listing was created.
@Entity
@Immutable
@Table(name = "listing_plot")
@IdClass(ListingPlotJpaEntity.Key.class)
@Getter @Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ListingPlotJpaEntity {

    @Id
    @Column(name = "listing_id", nullable = false, length = 255)
    private String listingId;

    @Id
    @Column(name = "source_registry_entry_id", nullable = false, length = 255)
    private String sourceRegistryEntryId;

    @Column(name = "household_label", nullable = false, length = 255)
    private String householdLabel;

    @Enumerated(EnumType.STRING)
    @Column(name = "geometry_type", nullable = false, length = 20)
    private GeometryType geometryType;

    @Column(name = "geo_json", nullable = false, columnDefinition = "geometry")
    @ColumnTransformer(
            read = "ST_AsText(geo_json, 'axis-order=long-lat')",
            write = "ST_GeomFromText(?, 4326, 'axis-order=long-lat')")
    private String geometryWkt;

    @Column(name = "geometry_hash", nullable = false, length = 64, columnDefinition = "char(64)")
    private String geometryHash;

    @Column(name = "area_hectares", nullable = false, precision = 10, scale = 4)
    private BigDecimal areaHectares;

    @Enumerated(EnumType.STRING)
    @Column(name = "verification_level", nullable = false, length = 20)
    private VerificationLevel verificationLevel;

    // UTC
    @Column(name = "declared_at", nullable = false)
    private LocalDateTime declaredAt;

    @EqualsAndHashCode
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Key implements Serializable {
        private String listingId;
        private String sourceRegistryEntryId;
    }
}
