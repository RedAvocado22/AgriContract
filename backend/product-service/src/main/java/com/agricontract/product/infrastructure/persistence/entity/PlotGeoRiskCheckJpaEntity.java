package com.agricontract.product.infrastructure.persistence.entity;

import com.agricontract.product.domain.model.vo.GeoRiskLevel;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.List;

// Append-only: inserted once per applied geo check, never updated or deleted.
@Entity
@Table(name = "plot_geo_risk_check")
@Getter @Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PlotGeoRiskCheckJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "check_id")
    private Long checkId;

    @Column(name = "registry_entry_id", nullable = false, updatable = false, length = 255)
    private String registryEntryId;

    @Column(name = "geometry_hash", nullable = false, updatable = false, length = 64, columnDefinition = "char(64)")
    private String geometryHash;

    @Column(name = "dataset_version", nullable = false, updatable = false, length = 100)
    private String datasetVersion;

    @Enumerated(EnumType.STRING)
    @Column(name = "geo_risk_level", nullable = false, updatable = false, length = 20)
    private GeoRiskLevel geoRiskLevel;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "matched_baseline_area_ids", nullable = false, updatable = false, columnDefinition = "json")
    private List<String> matchedBaselineAreaIds;

    // UTC
    @Column(name = "checked_at", nullable = false, updatable = false)
    private LocalDateTime checkedAt;
}
