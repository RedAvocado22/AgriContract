package com.agricontract.notification.domain.repository;

import com.agricontract.notification.domain.model.NotificationLog;

import java.util.Optional;

public interface NotificationLogRepository {
    NotificationLog save(NotificationLog log);
    boolean existsByEventId(String eventId);
    Optional<NotificationLog> findByEventId(String eventId);
}
