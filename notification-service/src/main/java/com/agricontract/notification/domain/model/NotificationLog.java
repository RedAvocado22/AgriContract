package com.agricontract.notification.domain.model;

import com.agricontract.notification.domain.model.vo.NotificationChannel;
import com.agricontract.notification.domain.model.vo.NotificationId;
import com.agricontract.notification.domain.model.vo.NotificationStatus;
import lombok.Getter;

import java.time.LocalDateTime;

// Entity (không có Aggregate phức tạp, chỉ là log gửi email)
// Idempotency: dùng eventId làm dedup key — không gửi 2 lần cùng event
@Getter
public class NotificationLog {

    private NotificationId notificationId;
    private String eventId;           // dedup key từ RabbitMQ message
    private String userId;
    private NotificationChannel channel;
    private String subject;
    private String body;
    private NotificationStatus status;
    private int retryCount;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
