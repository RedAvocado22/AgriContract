import { Logger } from '@nestjs/common';
import { ConfigService } from '@nestjs/config';
import { NestFactory } from '@nestjs/core';
import { AppModule } from './app.module';
import type { AppConfig } from './config/env.validation';

async function bootstrap() {
  const app = await NestFactory.create(AppModule);
  app.enableShutdownHooks();
  const port = app
    .get<ConfigService<AppConfig, true>>(ConfigService)
    .get('serverPort', { infer: true });
  await app.listen(port);
  Logger.log(`notification-service listening on ${port}`, 'Bootstrap');
}

bootstrap().catch((err: unknown) => {
  Logger.error(
    err instanceof Error ? err.message : String(err),
    err instanceof Error ? err.stack : undefined,
    'Bootstrap',
  );
  process.exit(1);
});
