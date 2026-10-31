package com.agricontract.user.infrastructure.persistence.repository;

import com.agricontract.user.domain.model.RoleChangeAuditEntry;
import com.agricontract.user.domain.repository.RoleChangeAuditRepository;
import com.agricontract.user.infrastructure.persistence.mapper.RoleChangeAuditMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class RoleChangeAuditRepositoryImpl implements RoleChangeAuditRepository {

    private final RoleChangeAuditJpaRepository jpaRepository;
    private final RoleChangeAuditMapper mapper;

    @Override
    public void append(RoleChangeAuditEntry entry) {
        jpaRepository.save(mapper.toJpaEntity(entry));
    }
}
