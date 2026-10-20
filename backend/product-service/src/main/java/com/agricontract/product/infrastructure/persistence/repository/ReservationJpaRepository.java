package com.agricontract.product.infrastructure.persistence.repository;

import com.agricontract.product.domain.model.vo.ReservationStatus;
import com.agricontract.product.infrastructure.persistence.entity.ReservationJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Optional;

public interface ReservationJpaRepository extends JpaRepository<ReservationJpaEntity, Long> {
    Optional<ReservationJpaEntity> findByContractId(String contractId);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            UPDATE ReservationJpaEntity r
            SET r.status = :target, r.updatedAt = :now
            WHERE r.contractId = :contractId
              AND r.status = :expected
            """)
    int transition(@Param("contractId") String contractId,
                   @Param("expected") ReservationStatus expected,
                   @Param("target") ReservationStatus target,
                   @Param("now") LocalDateTime now);
}
