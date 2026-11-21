import {
  Inject,
  Injectable,
  Logger,
  Module,
  type OnApplicationShutdown,
  type OnModuleInit,
} from '@nestjs/common';
import { ConfigService } from '@nestjs/config';
import { OBJECT_STORAGE } from '../../application/ports/object-storage.port';
import { failureCode } from '../../application/file-errors';
import type { AppConfig } from '../../config/env.validation';
import { S3ObjectStorage, createS3Client } from './s3-object-storage';

/**
 * Makes sure the bucket exists before the app takes traffic, and fails the
 * startup when object storage is unreachable (like MysqlService for the DB).
 */
@Injectable()
export class StorageLifecycle implements OnModuleInit, OnApplicationShutdown {
  private readonly logger = new Logger(StorageLifecycle.name);

  constructor(
    @Inject(OBJECT_STORAGE) private readonly storage: S3ObjectStorage,
    private readonly config: ConfigService<AppConfig, true>,
  ) {}

  async onModuleInit(): Promise<void> {
    const { endpoint, bucket } = this.config.get('storage', { infer: true });
    let outcome: 'EXISTS' | 'CREATED';
    try {
      outcome = await this.storage.ensureBucket();
    } catch (err) {
      throw new Error(
        `Cannot reach bucket ${bucket} at ${endpoint} (${failureCode(err)})`,
      );
    }
    this.logger.log(
      `Bucket ${bucket} at ${endpoint} ${outcome === 'CREATED' ? 'created' : 'ready'}`,
    );
  }

  onApplicationShutdown(): void {
    this.storage.close();
  }
}

@Module({
  providers: [
    {
      provide: OBJECT_STORAGE,
      inject: [ConfigService],
      useFactory: (config: ConfigService<AppConfig, true>) => {
        const storage = config.get('storage', { infer: true });
        return new S3ObjectStorage(createS3Client(storage), storage.bucket);
      },
    },
    StorageLifecycle,
  ],
  exports: [OBJECT_STORAGE],
})
export class StorageModule {}
