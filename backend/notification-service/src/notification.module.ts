import { Logger, Module, type Provider } from '@nestjs/common';
import { NotificationSender } from './application/notification-sender';
import { EMAIL_PORT, type EmailPort } from './application/ports/email.port';
import {
  NOTIFICATION_LOG_REPOSITORY,
  type NotificationLogRepository,
} from './application/ports/notification-log.repository';
import {
  NotifyCategoryApprovedUseCase,
  NotifyCategoryRejectedUseCase,
  NotifyActivationCancelRequestedUseCase,
  NotifyContractActivationFailedUseCase,
  NotifyContractAnchorUseCase,
  NotifyContractTerminatedUseCase,
  NotifyUserKycResultUseCase,
  NotifyUserLockChangedUseCase,
} from './application/use-cases';
import {
  NodemailerEmailAdapter,
  mailTransportFactory,
} from './infrastructure/email/nodemailer-email.adapter';
import {
  COMMAND_ROUTES,
  commandRoutes,
} from './infrastructure/messaging/command-routes';
import { DlqMonitor } from './infrastructure/messaging/dlq-monitor';
import {
  LEGACY_ROUTES,
  legacyRoutes,
  type NotificationUseCases,
} from './infrastructure/messaging/legacy-routes';
import { MessagingModule } from './infrastructure/messaging/messaging.module';
import {
  NotificationEventConsumer,
  SHUTDOWN_CONTROLLER,
} from './infrastructure/messaging/notification-event.consumer';
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
  categoryApproved: NotifyCategoryApprovedUseCase,
  categoryRejected: NotifyCategoryRejectedUseCase,
} as const;

/**
 * Phase 1 notifications (2 flat category events) and the Phase 2 commands:
 * one RabbitMQ consumer per queue -> use case -> email + notification_logs.
 */
@Module({
  imports: [PersistenceModule, MessagingModule],
  providers: [
    {
      provide: NOTIFICATION_LOG_REPOSITORY,
      useClass: MysqlNotificationLogRepository,
    },
    { provide: EMAIL_PORT, useClass: NodemailerEmailAdapter },
    mailTransportFactory,
    { provide: SHUTDOWN_CONTROLLER, useFactory: () => new AbortController() },
    {
      provide: NotificationSender,
      inject: [NOTIFICATION_LOG_REPOSITORY, EMAIL_PORT, SHUTDOWN_CONTROLLER],
      useFactory: (
        logs: NotificationLogRepository,
        email: EmailPort,
        shutdown: AbortController,
      ) =>
        new NotificationSender(logs, email, {
          logger: new Logger(NotificationSender.name),
          shutdown: shutdown.signal,
        }),
    },
    useCaseOf(NotifyCategoryApprovedUseCase),
    useCaseOf(NotifyCategoryRejectedUseCase),
    useCaseOf(NotifyContractAnchorUseCase),
    useCaseOf(NotifyActivationCancelRequestedUseCase),
    useCaseOf(NotifyContractActivationFailedUseCase),
    useCaseOf(NotifyContractTerminatedUseCase),
    useCaseOf(NotifyUserKycResultUseCase),
    useCaseOf(NotifyUserLockChangedUseCase),
    {
      provide: COMMAND_ROUTES,
      inject: [
        NotifyContractAnchorUseCase,
        NotifyActivationCancelRequestedUseCase,
        NotifyContractActivationFailedUseCase,
        NotifyContractTerminatedUseCase,
        NotifyUserKycResultUseCase,
        NotifyUserLockChangedUseCase,
      ],
      useFactory: (
        contractAnchor: NotifyContractAnchorUseCase,
        activationCancelRequested: NotifyActivationCancelRequestedUseCase,
        contractActivationFailed: NotifyContractActivationFailedUseCase,
        contractTerminated: NotifyContractTerminatedUseCase,
        userKycResult: NotifyUserKycResultUseCase,
        userLockChanged: NotifyUserLockChangedUseCase,
      ) =>
        commandRoutes({
          contractAnchor,
          activationCancelRequested,
          contractActivationFailed,
          contractTerminated,
          userKycResult,
          userLockChanged,
        }),
    },
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
