package com.agricontract.notification.domain.model;

import com.agricontract.notification.domain.model.vo.NotificationChannel;
import com.agricontract.notification.domain.model.vo.NotificationId;
import com.agricontract.notification.domain.model.vo.NotificationStatus;
import lombok.Getter;

import java.time.LocalDateTime;

// Entity (no complex Aggregate, just an email send log)
// Idempotency: eventId is used as dedup key — prevents sending the same event twice
@Getter
public class NotificationLog {

    private NotificationId notificationId;
    private String eventId;           // dedup key from RabbitMQ message
    private String userId;
    private NotificationChannel channel;
    private String subject;
    private String body;
    private NotificationStatus status;
    private int retryCount;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
