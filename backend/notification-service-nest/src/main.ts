import { Logger } from '@nestjs/common';
import { NestFactory } from '@nestjs/core';
import { AppModule } from './app.module';

async function bootstrap() {
  const app = await NestFactory.create(AppModule);
  app.enableShutdownHooks();
  const port = Number(process.env.SERVER_PORT ?? 8085);
  await app.listen(port);
  Logger.log(`notification-service listening on ${port}`, 'Bootstrap');
}
void bootstrap();
