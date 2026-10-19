import { Module } from '@nestjs/common';
import { loadConfig } from '../config.js';
import { jsonLogger } from '../logger.js';
import { AnalyticsLifecycle } from './analytics.lifecycle.js';
import { CONFIG, LOGGER } from './tokens.js';

/** Configuration, logger and the resources that ingest events (MySQL, RabbitMQ). */
@Module({
  providers: [
    { provide: CONFIG, useFactory: () => loadConfig() },
    { provide: LOGGER, useValue: jsonLogger },
    AnalyticsLifecycle,
  ],
  exports: [AnalyticsLifecycle, CONFIG, LOGGER],
})
export class AnalyticsModule {}
