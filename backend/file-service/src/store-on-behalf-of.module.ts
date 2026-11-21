import { Module } from '@nestjs/common';
import { FilesController } from './infrastructure/http/files.controller';
import { InternalSecretGuard } from './infrastructure/http/internal-secret.guard';
import { StoreFileModule } from './store-file.module';

/** The DIRECT_UPLOAD entrypoint: POST /internal/v1/files (design §3, D60). */
@Module({
  imports: [StoreFileModule],
  controllers: [FilesController],
  providers: [InternalSecretGuard],
})
export class StoreOnBehalfOfModule {}
