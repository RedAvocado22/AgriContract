import { Module } from '@nestjs/common';
import { HealthController } from '../http/health.controller';
import { AnalyticsModule } from './analytics.module';

@Module({
  imports: [AnalyticsModule],
  controllers: [HealthController],
})
export class AppModule {}
