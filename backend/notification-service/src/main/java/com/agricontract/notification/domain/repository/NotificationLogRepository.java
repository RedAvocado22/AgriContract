package com.agricontract.notification.domain.repository;

import com.agricontract.notification.domain.model.NotificationLog;

import java.util.Optional;

public interface NotificationLogRepository {
    NotificationLog save(NotificationLog log);

    boolean existsByEventIdAndUserId(String eventId, String userId);

    Optional<NotificationLog> findByEventIdAndUserId(String eventId, String userId);
}
