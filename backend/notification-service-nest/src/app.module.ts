import { Module } from '@nestjs/common';
import { ConfigModule } from '@nestjs/config';
import { validate } from './config/env.validation';
import { PersistenceModule } from './infrastructure/persistence/persistence.module';

@Module({
  imports: [
    // Environment variables only, like the Java service: no .env file.
    ConfigModule.forRoot({ isGlobal: true, ignoreEnvFile: true, validate }),
    PersistenceModule,
  ],
})
export class AppModule {}
