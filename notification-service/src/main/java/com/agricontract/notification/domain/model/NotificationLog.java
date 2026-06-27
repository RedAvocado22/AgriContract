package com.agricontract.notification.domain.model;

import com.agricontract.notification.domain.model.vo.NotificationChannel;
import com.agricontract.notification.domain.model.vo.NotificationId;
import com.agricontract.notification.domain.model.vo.NotificationStatus;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
public class NotificationLog {

    private NotificationId notificationId;
    private String eventId;
    private String userId;
    private NotificationChannel channel;
    private String subject;
    private String body;
    private NotificationStatus status;
    private int retryCount;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    private NotificationLog() {}

    public static NotificationLog create(String eventId, String userId,
                                         NotificationChannel channel, String subject, String body) {
        NotificationLog log = new NotificationLog();
        log.notificationId = new NotificationId(UUID.randomUUID().toString());
        log.eventId = eventId;
        log.userId = userId;
        log.channel = channel;
        log.subject = subject;
        log.body = body;
        log.status = NotificationStatus.PENDING;
        log.retryCount = 0;
        return log;
    }

    public static NotificationLog reconstitute(NotificationId notificationId, String eventId,
                                               String userId, NotificationChannel channel,
                                               String subject, String body, NotificationStatus status,
                                               int retryCount, LocalDateTime createdAt, LocalDateTime updatedAt) {
        NotificationLog log = new NotificationLog();
        log.notificationId = notificationId;
        log.eventId = eventId;
        log.userId = userId;
        log.channel = channel;
        log.subject = subject;
        log.body = body;
        log.status = status;
        log.retryCount = retryCount;
        log.createdAt = createdAt;
        log.updatedAt = updatedAt;
        return log;
    }

    public void markSent() {
        this.status = NotificationStatus.SENT;
    }

    public void markFailed() {
        this.retryCount++;
        this.status = NotificationStatus.FAILED;
    }
}
