import { Module } from '@nestjs/common';
import { EMAIL_PORT } from './application/ports/email.port';
import { NOTIFICATION_LOG_REPOSITORY } from './application/ports/notification-log.repository';
import { ProcessNotificationService } from './application/process-notification.service';
import {
  NodemailerEmailAdapter,
  mailTransportFactory,
} from './infrastructure/email/nodemailer-email.adapter';
import { MessagingModule } from './infrastructure/messaging/messaging.module';
import { NotificationEventConsumer } from './infrastructure/messaging/notification-event.consumer';
import { MysqlNotificationLogRepository } from './infrastructure/persistence/notification-log.mysql-repository';
import { PersistenceModule } from './infrastructure/persistence/persistence.module';

/** Phase 1 notifications: 9 RabbitMQ consumers -> handlers -> email + notification_logs. */
@Module({
  imports: [PersistenceModule, MessagingModule],
  providers: [
    ProcessNotificationService,
    {
      provide: NOTIFICATION_LOG_REPOSITORY,
      useClass: MysqlNotificationLogRepository,
    },
    { provide: EMAIL_PORT, useClass: NodemailerEmailAdapter },
    mailTransportFactory,
    NotificationEventConsumer,
  ],
})
export class NotificationModule {}
