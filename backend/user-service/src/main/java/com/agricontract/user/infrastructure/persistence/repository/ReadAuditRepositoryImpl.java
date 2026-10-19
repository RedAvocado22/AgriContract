package com.agricontract.user.infrastructure.persistence.repository;

import com.agricontract.user.domain.exception.ReadAuditUnavailableException;
import com.agricontract.user.domain.model.ReadAuditEntry;
import com.agricontract.user.domain.repository.ReadAuditRepository;
import com.agricontract.user.infrastructure.persistence.mapper.ReadAuditMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class ReadAuditRepositoryImpl implements ReadAuditRepository {

    private final ReadAuditJpaRepository jpaRepository;
    private final ReadAuditMapper mapper;

    @Override
    public void append(ReadAuditEntry entry) {
        try {
            // Flush now: a failed insert must surface before the caller returns the data, not at commit.
            jpaRepository.saveAndFlush(mapper.toJpaEntity(entry));
        } catch (DataAccessException e) {
            throw new ReadAuditUnavailableException(entry.resourceId(), e);
        }
    }
}
