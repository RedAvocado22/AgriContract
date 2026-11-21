import { Logger, Module } from '@nestjs/common';
import {
  FILE_REPOSITORY,
  type FileRepository,
} from './application/ports/file.repository';
import {
  OBJECT_STORAGE,
  type ObjectStoragePort,
} from './application/ports/object-storage.port';
import { StoreFileUseCase } from './application/store-file.use-case';
import { PersistenceModule } from './infrastructure/persistence/persistence.module';
import { StorageModule } from './infrastructure/storage/storage.module';

/**
 * The store core shared by every entrypoint (file-service-phase2-design.md §3).
 * The entrypoints themselves (StoreOnBehalfOf, store(), email intake) import
 * this module and pass their own IngestPolicy.
 */
@Module({
  imports: [PersistenceModule, StorageModule],
  providers: [
    {
      provide: StoreFileUseCase,
      inject: [OBJECT_STORAGE, FILE_REPOSITORY],
      useFactory: (storage: ObjectStoragePort, files: FileRepository) =>
        new StoreFileUseCase(storage, files, new Logger(StoreFileUseCase.name)),
    },
  ],
  exports: [StoreFileUseCase],
})
export class StoreFileModule {}
