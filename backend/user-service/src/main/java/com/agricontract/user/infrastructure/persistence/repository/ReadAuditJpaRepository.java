package com.agricontract.user.infrastructure.persistence.repository;

import com.agricontract.user.infrastructure.persistence.entity.ReadAuditJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReadAuditJpaRepository extends JpaRepository<ReadAuditJpaEntity, Long> {
}
