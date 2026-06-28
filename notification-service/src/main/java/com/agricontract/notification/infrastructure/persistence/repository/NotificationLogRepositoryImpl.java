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
    public boolean existsByEventIdAndUserId(String eventId, String userId) {
        return jpaRepository.existsByEventIdAndUserId(eventId, userId);
    }

    @Override
    public Optional<NotificationLog> findByEventIdAndUserId(String eventId, String userId) {
        return jpaRepository.findByEventIdAndUserId(eventId, userId).map(mapper::toDomain);
    }
}
