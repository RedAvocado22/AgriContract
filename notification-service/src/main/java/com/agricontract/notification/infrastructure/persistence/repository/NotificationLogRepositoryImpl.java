package com.agricontract.notification.infrastructure.persistence.repository;

import com.agricontract.notification.domain.model.NotificationLog;
import com.agricontract.notification.domain.repository.NotificationLogRepository;
import com.agricontract.notification.infrastructure.persistence.entity.NotificationLogJpaEntity;
import com.agricontract.notification.infrastructure.persistence.mapper.NotificationLogMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class NotificationLogRepositoryImpl implements NotificationLogRepository {

    private final NotificationLogJpaRepository jpaRepository;
    private final NotificationLogMapper mapper;

    @Override
    public NotificationLog save(NotificationLog log) {
        NotificationLogJpaEntity entity = mapper.toJpaEntity(log);
        return mapper.toDomain(jpaRepository.saveAndFlush(entity));
    }

    @Override
    public boolean existsByEventId(String eventId) {
        return jpaRepository.existsByEventId(eventId);
    }

    @Override
    public Optional<NotificationLog> findByEventId(String eventId) {
        return jpaRepository.findByEventId(eventId).map(mapper::toDomain);
    }
}
