import { Module } from '@nestjs/common';
import { ConfigModule } from '@nestjs/config';
import { validate } from './config/env.validation';

@Module({
  imports: [
    // Environment variables only, like the Java service: no .env file.
    ConfigModule.forRoot({ isGlobal: true, ignoreEnvFile: true, validate }),
  ],
})
export class AppModule {}
