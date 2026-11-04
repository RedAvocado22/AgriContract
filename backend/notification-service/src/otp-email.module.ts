import { Logger, Module } from '@nestjs/common';
import { ConfigService } from '@nestjs/config';
import { SendOtpEmailUseCase } from './application/otp/send-otp-email.use-case';
import { OTP_EMAIL_PORT, type EmailPort } from './application/ports/email.port';
import {
  NOTIFICATION_LOG_REPOSITORY,
  type NotificationLogRepository,
} from './application/ports/notification-log.repository';
import type { AppConfig } from './config/env.validation';
import {
  CREATE_MAIL_TRANSPORT,
  NodemailerEmailAdapter,
  OTP_SMTP_TIMEOUTS,
  mailTransportFactory,
  type CreateMailTransport,
} from './infrastructure/email/nodemailer-email.adapter';
import { InternalSecretGuard } from './infrastructure/http/internal-secret.guard';
import { OtpEmailController } from './infrastructure/http/otp-email.controller';
import { MysqlNotificationLogRepository } from './infrastructure/persistence/notification-log.mysql-repository';
import { PersistenceModule } from './infrastructure/persistence/persistence.module';

/**
 * Phase 2 synchronous OTP email (#114): POST /internal/v1/notifications/otp-email
 * -> SendOtpEmailUseCase -> notification_logs + SMTP with short timeouts.
 * Independent of RabbitMQ.
 */
@Module({
  imports: [PersistenceModule],
  controllers: [OtpEmailController],
  providers: [
    {
      provide: NOTIFICATION_LOG_REPOSITORY,
      useClass: MysqlNotificationLogRepository,
    },
    mailTransportFactory,
    {
      provide: OTP_EMAIL_PORT,
      inject: [ConfigService, CREATE_MAIL_TRANSPORT],
      useFactory: (
        config: ConfigService<AppConfig, true>,
        create: CreateMailTransport,
      ) => new NodemailerEmailAdapter(config, create, OTP_SMTP_TIMEOUTS),
    },
    {
      provide: SendOtpEmailUseCase,
      inject: [NOTIFICATION_LOG_REPOSITORY, OTP_EMAIL_PORT],
      useFactory: (logs: NotificationLogRepository, email: EmailPort) =>
        new SendOtpEmailUseCase(
          logs,
          email,
          new Logger(SendOtpEmailUseCase.name),
        ),
    },
    InternalSecretGuard,
  ],
})
export class OtpEmailModule {}
