import 'reflect-metadata';
import { NestFactory } from '@nestjs/core';
import { loadConfig } from './infrastructure/config';
import { jsonLogger } from './infrastructure/logger';
import { AppModule } from './infrastructure/nest/app.module';

/**
 * Entry point: creates the Nest application and starts listening. Nothing here contacts MySQL or Redis
 * (mysql2 connects on the first query, Redis in the background). bodyParser is off on purpose: the
 * controller reads the body after the guard has authorized the request.
 * SIGTERM/SIGINT close the HTTP server, then the MySQL pool and Redis (PricingShutdown).
 */
async function bootstrap(): Promise<void> {
  const config = loadConfig();
  const app = await NestFactory.create(AppModule, { bodyParser: false, logger: ['error', 'warn'] });
  app.enableShutdownHooks(['SIGTERM', 'SIGINT']);
  await app.listen(config.port);
  jsonLogger.info('pricing-service listening', { port: config.port });
}

bootstrap().catch((err: unknown) => {
  jsonLogger.error('pricing-service failed to start', { error: err instanceof Error ? err.message : String(err) });
  process.exit(1);
});
