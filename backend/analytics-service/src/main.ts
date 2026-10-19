import 'reflect-metadata';
import { NestFactory } from '@nestjs/core';
import { loadConfig } from './infrastructure/config.js';
import { jsonLogger } from './infrastructure/logger.js';
import { AppModule } from './infrastructure/nest/app.module.js';

/**
 * Entry point: creates the Nest application and starts listening. Nothing here contacts MySQL or
 * RabbitMQ: AnalyticsLifecycle does that in the background, so /health answers 503 STARTING meanwhile.
 * SIGTERM/SIGINT run the graceful shutdown (AnalyticsLifecycle.beforeApplicationShutdown).
 */
async function bootstrap(): Promise<void> {
  const config = loadConfig();
  const app = await NestFactory.create(AppModule, { logger: ['error', 'warn'] });
  app.enableShutdownHooks(['SIGTERM', 'SIGINT']);
  await app.listen(config.port);
  jsonLogger.info('analytics-service health endpoint listening', { port: config.port });
}

bootstrap().catch((err: unknown) => {
  jsonLogger.error('analytics-service failed to start', { error: err instanceof Error ? err.message : String(err) });
  process.exit(1);
});
