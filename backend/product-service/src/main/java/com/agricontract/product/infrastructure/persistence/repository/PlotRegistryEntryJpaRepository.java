package com.agricontract.product.infrastructure.persistence.repository;

import com.agricontract.product.domain.model.vo.GeoVerificationStatus;
import com.agricontract.product.infrastructure.persistence.entity.PlotRegistryEntryJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
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
}
