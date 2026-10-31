package com.agricontract.user.infrastructure.persistence.repository;

import com.agricontract.user.infrastructure.persistence.entity.RoleChangeAuditJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RoleChangeAuditJpaRepository extends JpaRepository<RoleChangeAuditJpaEntity, Long> {
}
