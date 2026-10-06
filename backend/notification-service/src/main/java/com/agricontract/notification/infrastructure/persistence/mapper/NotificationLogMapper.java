package com.agricontract.notification.infrastructure.persistence.mapper;

import com.agricontract.notification.domain.model.NotificationLog;
import com.agricontract.notification.domain.model.vo.NotificationId;
import com.agricontract.notification.infrastructure.persistence.entity.NotificationLogJpaEntity;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface NotificationLogMapper {

    @Mapping(target = "notificationId", source = "notificationId.value")
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    NotificationLogJpaEntity toJpaEntity(NotificationLog domain);

    default NotificationLog toDomain(NotificationLogJpaEntity entity) {
        return NotificationLog.reconstitute(
                new NotificationId(entity.getNotificationId()),
                entity.getEventId(),
                entity.getUserId(),
                entity.getChannel(),
                entity.getSubject(),
                entity.getBody(),
                entity.getStatus(),
                entity.getRetryCount(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }
}
