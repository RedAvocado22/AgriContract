package com.agricontract.product.infrastructure.persistence.entity;

import com.agricontract.product.domain.model.vo.GeoRiskLevel;
import com.agricontract.product.domain.model.vo.GeoVerificationStatus;
import com.agricontract.product.domain.model.vo.GeometryType;
import com.agricontract.product.domain.model.vo.PlotSource;
import com.agricontract.product.domain.model.vo.Province;
import com.agricontract.product.domain.model.vo.VerificationLevel;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.ColumnTransformer;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "plot_registry_entry")
@Getter @Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PlotRegistryEntryJpaEntity {

    @Id
    @Column(name = "registry_entry_id", nullable = false, length = 255)
    private String registryEntryId;

    @Column(name = "seller_id", nullable = false, updatable = false, length = 255)
    private String sellerId;

    @Column(name = "household_label", nullable = false, updatable = false, length = 255)
    private String householdLabel;

    @Enumerated(EnumType.STRING)
    @Column(name = "geometry_type", nullable = false, length = 20)
    private GeometryType geometryType;

    // WKT in Java, GEOMETRY SRID 4326 in MySQL; long-lat order both ways (MySQL defaults to lat-long for 4326)
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
    @Column(nullable = false, length = 20)
    private PlotSource source;

    @Column(name = "accuracy_meters", precision = 6, scale = 2)
    private BigDecimal accuracyMeters;

    @Enumerated(EnumType.STRING)
    @Column(name = "verification_level", nullable = false, length = 20)
    private VerificationLevel verificationLevel;

    @Enumerated(EnumType.STRING)
    @Column(name = "declared_province", nullable = false, length = 50)
    private Province declaredProvince;

    @Enumerated(EnumType.STRING)
    @Column(name = "geo_verification_status", nullable = false, length = 20)
    private GeoVerificationStatus geoVerificationStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "geo_risk_level", length = 20)
    private GeoRiskLevel geoRiskLevel;

    // UTC
    @Column(name = "imported_at", nullable = false)
    private LocalDateTime importedAt;
}
