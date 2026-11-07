package com.agricontract.product.infrastructure.persistence.repository;

import com.agricontract.product.domain.model.vo.GeoRiskLevel;
import com.agricontract.product.domain.model.vo.GeoVerificationStatus;
import com.agricontract.product.infrastructure.persistence.entity.PlotRegistryEntryJpaEntity;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface PlotRegistryEntryJpaRepository extends JpaRepository<PlotRegistryEntryJpaEntity, String> {

    Optional<PlotRegistryEntryJpaEntity> findBySellerIdAndHouseholdLabel(String sellerId, String householdLabel);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            UPDATE PlotRegistryEntryJpaEntity p
            SET p.geoVerificationStatus = :target
            WHERE p.geoVerificationStatus = :expected
              AND p.importedAt < :cutoff
            """)
    int transitionImportedBefore(@Param("expected") GeoVerificationStatus expected,
                                 @Param("target") GeoVerificationStatus target,
                                 @Param("cutoff") LocalDateTime cutoff);

    // never attempted first, then least recently attempted, then oldest import
    @Query("""
            SELECT p FROM PlotRegistryEntryJpaEntity p
            WHERE p.geoVerificationStatus = :status
            ORDER BY p.geoCheckAttemptedAt ASC NULLS FIRST, p.importedAt ASC
            """)
    List<PlotRegistryEntryJpaEntity> findGeoCheckQueue(@Param("status") GeoVerificationStatus status, Pageable pageable);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            UPDATE PlotRegistryEntryJpaEntity p
            SET p.geoCheckAttemptedAt = :attemptedAt
            WHERE p.registryEntryId IN :registryEntryIds
              AND p.geoVerificationStatus = :expected
            """)
    int markGeoCheckAttempted(@Param("registryEntryIds") Collection<String> registryEntryIds,
                              @Param("expected") GeoVerificationStatus expected,
                              @Param("attemptedAt") LocalDateTime attemptedAt);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            UPDATE PlotRegistryEntryJpaEntity p
            SET p.geoVerificationStatus = :target, p.geoRiskLevel = :riskLevel
            WHERE p.registryEntryId = :registryEntryId
              AND p.geoVerificationStatus = :expected
              AND p.geometryHash = :geometryHash
            """)
    int applyGeoRisk(@Param("registryEntryId") String registryEntryId,
                     @Param("geometryHash") String geometryHash,
                     @Param("expected") GeoVerificationStatus expected,
                     @Param("target") GeoVerificationStatus target,
                     @Param("riskLevel") GeoRiskLevel riskLevel);
}
