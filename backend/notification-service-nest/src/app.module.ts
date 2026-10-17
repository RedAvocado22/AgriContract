import { Module } from '@nestjs/common';
import { ConfigModule } from '@nestjs/config';
import { validate } from './config/env.validation';
import { HealthModule } from './infrastructure/health/health.module';
import { MessagingModule } from './infrastructure/messaging/messaging.module';
import { PersistenceModule } from './infrastructure/persistence/persistence.module';
import { NotificationModule } from './notification.module';

@Module({
  imports: [
    // Environment variables only, like the Java service: no .env file.
    ConfigModule.forRoot({ isGlobal: true, ignoreEnvFile: true, validate }),
    PersistenceModule,
    MessagingModule,
    HealthModule,
    NotificationModule,
  ],
})
export class AppModule {}
