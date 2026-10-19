import { Module, type Provider } from '@nestjs/common';
import { ConfigService } from '@nestjs/config';
import { NotificationSender } from './application/notification-sender';
import {
  NOTIFICATION_SETTINGS,
  type NotificationSettings,
} from './application/notification-settings';
import { EMAIL_PORT, type EmailPort } from './application/ports/email.port';
import {
  NOTIFICATION_LOG_REPOSITORY,
  type NotificationLogRepository,
} from './application/ports/notification-log.repository';
import {
  NotifyCategoryApprovedUseCase,
  NotifyCategoryRejectedUseCase,
  NotifyContractCancelledUseCase,
  NotifyContractDeliveredUseCase,
  NotifyContractDisputedUseCase,
  NotifyContractSignedUseCase,
  NotifyEscrowLockedUseCase,
  NotifyEscrowPenalizedUseCase,
  NotifyEscrowReleasedUseCase,
} from './application/use-cases';
import type { AppConfig } from './config/env.validation';
import {
  NodemailerEmailAdapter,
  mailTransportFactory,
} from './infrastructure/email/nodemailer-email.adapter';
import { DlqMonitor } from './infrastructure/messaging/dlq-monitor';
import {
  LEGACY_ROUTES,
  legacyRoutes,
  type NotificationUseCases,
} from './infrastructure/messaging/legacy-routes';
import { MessagingModule } from './infrastructure/messaging/messaging.module';
import { NotificationEventConsumer } from './infrastructure/messaging/notification-event.consumer';
import { MysqlNotificationLogRepository } from './infrastructure/persistence/notification-log.mysql-repository';
import { PersistenceModule } from './infrastructure/persistence/persistence.module';

/*
 * The application layer (src/application) is plain TypeScript: its classes are
 * built here with factories, from the port tokens and plain settings.
 */

type SenderUseCase = new (sender: NotificationSender) => unknown;

const useCaseOf = (useCase: SenderUseCase): Provider => ({
  provide: useCase,
  inject: [NotificationSender],
  useFactory: (sender: NotificationSender) => new useCase(sender),
});

const USE_CASES = {
  contractSigned: NotifyContractSignedUseCase,
  contractDelivered: NotifyContractDeliveredUseCase,
  contractCancelled: NotifyContractCancelledUseCase,
  contractDisputed: NotifyContractDisputedUseCase,
  escrowLocked: NotifyEscrowLockedUseCase,
  escrowReleased: NotifyEscrowReleasedUseCase,
  escrowPenalized: NotifyEscrowPenalizedUseCase,
  categoryApproved: NotifyCategoryApprovedUseCase,
  categoryRejected: NotifyCategoryRejectedUseCase,
} as const;

/** Phase 1 notifications: 9 RabbitMQ consumers -> use cases -> email + notification_logs. */
@Module({
  imports: [PersistenceModule, MessagingModule],
  providers: [
    {
      provide: NOTIFICATION_LOG_REPOSITORY,
      useClass: MysqlNotificationLogRepository,
    },
    { provide: EMAIL_PORT, useClass: NodemailerEmailAdapter },
    mailTransportFactory,
    {
      provide: NOTIFICATION_SETTINGS,
      inject: [ConfigService],
      useFactory: (config: ConfigService<AppConfig, true>) =>
        ({
          adminEmail: config.get('adminEmail', { infer: true }),
        }) satisfies NotificationSettings,
    },
    {
      provide: NotificationSender,
      inject: [NOTIFICATION_LOG_REPOSITORY, EMAIL_PORT],
      useFactory: (logs: NotificationLogRepository, email: EmailPort) =>
        new NotificationSender(logs, email),
    },
    useCaseOf(NotifyContractSignedUseCase),
    useCaseOf(NotifyContractDeliveredUseCase),
    useCaseOf(NotifyContractCancelledUseCase),
    {
      provide: NotifyContractDisputedUseCase,
      inject: [NotificationSender, NOTIFICATION_SETTINGS],
      useFactory: (
        sender: NotificationSender,
        settings: NotificationSettings,
      ) => new NotifyContractDisputedUseCase(sender, settings),
    },
    useCaseOf(NotifyEscrowLockedUseCase),
    useCaseOf(NotifyEscrowReleasedUseCase),
    useCaseOf(NotifyEscrowPenalizedUseCase),
    useCaseOf(NotifyCategoryApprovedUseCase),
    useCaseOf(NotifyCategoryRejectedUseCase),
    {
      provide: LEGACY_ROUTES,
      inject: Object.values(USE_CASES),
      useFactory: (...instances: unknown[]) =>
        legacyRoutes(
          Object.fromEntries(
            Object.keys(USE_CASES).map((name, i) => [name, instances[i]]),
          ) as unknown as NotificationUseCases,
        ),
    },
    NotificationEventConsumer,
    DlqMonitor,
  ],
})
export class NotificationModule {}
