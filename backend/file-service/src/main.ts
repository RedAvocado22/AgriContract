import { Logger } from '@nestjs/common';
import { ConfigService } from '@nestjs/config';
import { NestFactory } from '@nestjs/core';
import { AppModule } from './app.module';
import { APP_OPTIONS } from './app.options';
import type { AppConfig } from './config/env.validation';

async function bootstrap() {
  const app = await NestFactory.create(AppModule, APP_OPTIONS);
  // Exit once the shutdown hooks are done: node is PID 1 in the container and
  // the kernel ignores a re-raised signal there (same as notification-service).
  app.enableShutdownHooks([], { useProcessExit: true });
  const port = app
    .get<ConfigService<AppConfig, true>>(ConfigService)
    .get('serverPort', { infer: true });
  await app.listen(port);
  Logger.log(`file-service listening on ${port}`, 'Bootstrap');
}

bootstrap().catch((err: unknown) => {
  Logger.error(
    err instanceof Error ? err.message : String(err),
    err instanceof Error ? err.stack : undefined,
    'Bootstrap',
  );
  process.exit(1);
});
