import { Module } from '@nestjs/common';
import { HealthController } from '../http/health.controller.js';
import { AnalyticsModule } from './analytics.module.js';

@Module({
  imports: [AnalyticsModule],
  controllers: [HealthController],
})
export class AppModule {}
