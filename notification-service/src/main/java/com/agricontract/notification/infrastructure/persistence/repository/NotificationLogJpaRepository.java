package com.agricontract.notification.infrastructure.persistence.repository;

import com.agricontract.notification.domain.model.vo.NotificationStatus;
import com.agricontract.notification.infrastructure.persistence.entity.NotificationLogJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface NotificationLogJpaRepository extends JpaRepository<NotificationLogJpaEntity, Long> {
    boolean existsByEventId(String eventId);
    Optional<NotificationLogJpaEntity> findByEventId(String eventId);
}
