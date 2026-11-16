import { randomUUID } from 'node:crypto';
import { pipeline, type Readable } from 'node:stream';
import { newStoredFile, type StoredFile } from '../domain/stored-file';
import { ByteMeter } from './byte-meter';
import {
  FileLengthMismatchError,
  FileMetadataUnavailableError,
  FileServiceError,
  FileTooLargeError,
  InvalidFileLengthError,
  ObjectStorageUnavailableError,
  UnsupportedContentTypeError,
  failureCode,
} from './file-errors';
import { normalizeContentType, type IngestPolicy } from './ingest-policy';
import type { FileRepository } from './ports/file.repository';
import type { ObjectStoragePort } from './ports/object-storage.port';

export interface StoreFileInput {
  /** The bytes, read once as they arrive. */
  body: Readable;
  /** As the caller sent it; normalized and checked against the policy. */
  contentType: string;
  /** The length the caller declared; checked against the cap before reading. */
  contentLength: number;
  /** userId (DIRECT_UPLOAD) or serviceId (SYSTEM_GENERATED), from the entrypoint. */
  uploadedBy: string;
}

export interface StoreLogger {
  warn(message: string): void;
}

/**
 * The core every entrypoint shares (file-service-phase2-design.md §3): checks
 * the policy of the entrypoint, streams the bytes to object storage under a
 * key it generates (the fileId, §2.3), computes the storage hash while they
 * pass, then records the `file` row PROCESSING. The entrypoint decides the
 * policy and so the ingest channel; the caller never does (§2.1).
 *
 * Order: blob first, row second, because the row needs the hash of the bytes
 * actually stored (storage_hash is NOT NULL). A failure in between removes
 * the blob, so a row never points at a missing blob. If that removal fails
 * too, or the process dies in between, a blob without a row is left: §7's
 * orphan cleanup works from rows and cannot see it, so the warning names the
 * fileId (= object key) for a manual delete.
 */
export class StoreFileUseCase {
  constructor(
    private readonly storage: ObjectStoragePort,
    private readonly files: FileRepository,
    private readonly logger: StoreLogger,
  ) {}

  async execute(
    input: StoreFileInput,
    policy: IngestPolicy,
  ): Promise<StoredFile> {
    const contentType = this.checkContentType(input.contentType, policy);
    const declared = input.contentLength;
    // Refused before a byte is read; draining or closing the body is the
    // entrypoint's business.
    if (!Number.isSafeInteger(declared) || declared <= 0) {
      throw new InvalidFileLengthError();
    }
    if (declared > policy.maxBytes) {
      throw new FileTooLargeError(policy.maxBytes);
    }

    const fileId = randomUUID();
    const meter = new ByteMeter(declared);
    let bodyError: Error | undefined;
    pipeline(input.body, meter, (err) => {
      if (err) bodyError = err;
    });

    try {
      await this.storage.put(fileId, meter, {
        contentType,
        contentLength: declared,
      });
    } catch (err) {
      await this.removeBlob(fileId);
      // The body's own failure (length mismatch, client gone) explains the
      // storage error that followed it, so it wins.
      const cause = bodyError ?? err;
      if (cause instanceof FileServiceError) throw cause;
      throw new ObjectStorageUnavailableError(failureCode(cause));
    }
    if (bodyError) {
      // Storage took the bytes, but the body failed after them: do not keep it.
      await this.removeBlob(fileId);
      throw bodyError instanceof FileServiceError
        ? bodyError
        : new FileLengthMismatchError(declared, meter.bytes);
    }

    const file = newStoredFile({
      fileId,
      storageHash: meter.sha256,
      uploadedBy: input.uploadedBy,
      ingestChannel: policy.channel,
      contentType,
      fileSize: meter.bytes,
    });
    try {
      await this.files.insert(file);
    } catch (err) {
      await this.removeBlob(fileId);
      throw new FileMetadataUnavailableError(failureCode(err));
    }
    return file;
  }

  private checkContentType(raw: string, policy: IngestPolicy): string {
    const contentType = normalizeContentType(raw);
    const allowed = policy.contentTypes;
    if (contentType === null || (allowed && !allowed.includes(contentType))) {
      throw new UnsupportedContentTypeError(allowed ?? []);
    }
    return contentType;
  }

  private async removeBlob(fileId: string): Promise<void> {
    try {
      await this.storage.delete(fileId);
    } catch (err) {
      this.logger.warn(
        `File ${fileId}: could not remove the blob of a failed store (${failureCode(err)}); object ${fileId} has no row and must be deleted by hand`,
      );
    }
  }
}
