import {
  Controller,
  HttpCode,
  HttpStatus,
  Post,
  Req,
  Res,
  UseFilters,
  UseGuards,
} from '@nestjs/common';
import type { Request, Response } from 'express';
import { DIRECT_UPLOAD_POLICY } from '../../application/ingest-policy';
import { StoreFileUseCase } from '../../application/store-file.use-case';
import type { FileStatus } from '../../domain/stored-file';
import { ErrorEnvelopeFilter } from './error-envelope.filter';
import { InternalSecretGuard } from './internal-secret.guard';
import { parseStoreOnBehalfOfRequest } from './store-on-behalf-of.request';
import { discardUnreadBody } from './unread-body';

/** StoredFileAccepted of supplementary-services-api.yaml. */
export interface StoredFileAccepted {
  fileId: string;
  status: FileStatus;
}

/**
 * POST /internal/v1/files (supplementary-services-api.yaml storeOnBehalfOf,
 * design §3, D60): a business service stores an end-user's file. The body is
 * the raw file, streamed to object storage as it arrives; the channel is
 * DIRECT_UPLOAD whatever the caller says (§2.1).
 *
 * - 202 {fileId, status}: stored and PROCESSING, or a repeat of the same
 *   (X-Acting-User-Id, Idempotency-Key) with the file's current status;
 * - 400 VALIDATION_ERROR, 401 UNAUTHORIZED, 413 FILE_TOO_LARGE;
 * - 503 FILE_STORAGE_UNAVAILABLE / FILE_METADATA_UNAVAILABLE: nothing kept,
 *   safe to retry with the same Idempotency-Key.
 *
 * Not routed by the API gateway (/internal/** is never routed).
 */
@Controller('internal/v1/files')
@UseGuards(InternalSecretGuard)
@UseFilters(ErrorEnvelopeFilter)
export class FilesController {
  constructor(private readonly storeFile: StoreFileUseCase) {}

  @Post()
  @HttpCode(HttpStatus.ACCEPTED)
  async storeOnBehalfOf(
    @Req() req: Request,
    @Res({ passthrough: true }) res: Response,
  ): Promise<StoredFileAccepted> {
    const request = parseStoreOnBehalfOfRequest(req.headers);
    try {
      const file = await this.storeFile.execute(
        {
          body: req,
          contentType: request.contentType,
          contentLength: request.contentLength,
          uploadedBy: request.actingUserId,
          idempotencyKey: request.idempotencyKey,
        },
        DIRECT_UPLOAD_POLICY,
      );
      return { fileId: file.fileId, status: file.status };
    } finally {
      // A repeat, or a store that failed part way, leaves the body unread.
      await discardUnreadBody(req, res);
    }
  }
}
