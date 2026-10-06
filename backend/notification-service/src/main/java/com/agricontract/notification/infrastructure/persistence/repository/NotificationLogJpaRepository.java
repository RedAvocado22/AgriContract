package com.agricontract.notification.infrastructure.persistence.repository;

import com.agricontract.notification.infrastructure.persistence.entity.NotificationLogJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface NotificationLogJpaRepository extends JpaRepository<NotificationLogJpaEntity, Long> {
    boolean existsByEventIdAndUserId(String eventId, String userId);

    Optional<NotificationLogJpaEntity> findByEventIdAndUserId(String eventId, String userId);
}
