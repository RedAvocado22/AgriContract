import { Module } from '@nestjs/common';
import { ConfigModule } from '@nestjs/config';
import { validate } from './config/env.validation';
import { HealthModule } from './infrastructure/health/health';
import { PersistenceModule } from './infrastructure/persistence/persistence.module';
import { StorageModule } from './infrastructure/storage/storage.module';
import { StoreFileModule } from './store-file.module';

@Module({
  imports: [
    // Environment variables only, like the other services: no .env file.
    ConfigModule.forRoot({ isGlobal: true, ignoreEnvFile: true, validate }),
    PersistenceModule,
    StorageModule,
    HealthModule,
    StoreFileModule,
  ],
})
export class AppModule {}
