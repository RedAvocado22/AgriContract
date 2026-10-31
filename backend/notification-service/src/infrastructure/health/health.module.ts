import { Module } from '@nestjs/common';
import { TerminusModule } from '@nestjs/terminus';
import { MessagingModule } from '../messaging/messaging.module';
import { PersistenceModule } from '../persistence/persistence.module';
import { HealthController } from './health.controller';
import { DatabaseHealthIndicator, RabbitHealthIndicator } from './indicators';

@Module({
  imports: [
    // Terminus would log an error on every failed check; AmqpService and
    // MysqlService already log the outage itself.
    TerminusModule.forRoot({ logger: false }),
    PersistenceModule,
    MessagingModule,
  ],
  controllers: [HealthController],
  providers: [DatabaseHealthIndicator, RabbitHealthIndicator],
})
export class HealthModule {}
