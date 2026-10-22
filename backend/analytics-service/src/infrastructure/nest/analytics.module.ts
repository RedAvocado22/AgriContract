import { Module } from '@nestjs/common';
import { loadConfig } from '../config';
import { jsonLogger } from '../logger';
import { AnalyticsLifecycle } from './analytics.lifecycle';
import { ProjectionsModule } from './projections.module';
import { CONFIG, LOGGER } from './tokens';

/** Configuration, logger and the resources that ingest events (MySQL, RabbitMQ). */
@Module({
  imports: [ProjectionsModule],
  providers: [
    { provide: CONFIG, useFactory: () => loadConfig() },
    { provide: LOGGER, useValue: jsonLogger },
    AnalyticsLifecycle,
  ],
  exports: [AnalyticsLifecycle, CONFIG, LOGGER],
})
export class AnalyticsModule {}
