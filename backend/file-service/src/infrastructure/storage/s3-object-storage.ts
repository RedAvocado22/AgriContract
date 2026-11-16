import type { Readable } from 'node:stream';
import {
  CreateBucketCommand,
  DeleteObjectCommand,
  HeadBucketCommand,
  PutObjectCommand,
  S3Client,
  S3ServiceException,
} from '@aws-sdk/client-s3';
import type { ObjectStoragePort } from '../../application/ports/object-storage.port';
import type { AppConfig } from '../../config/env.validation';

/** Connect and socket-inactivity limits: a dead store fails a request, never hangs it. */
const CONNECTION_TIMEOUT_MS = 2_000;
const SOCKET_TIMEOUT_MS = 30_000;

const ignore = (): void => undefined;
const QUIET_SDK_LOGGER = {
  debug: ignore,
  info: ignore,
  warn: ignore,
  error: ignore,
};

export function createS3Client(storage: AppConfig['storage']): S3Client {
  return new S3Client({
    endpoint: storage.endpoint,
    region: storage.region,
    credentials: {
      accessKeyId: storage.accessKeyId,
      secretAccessKey: storage.secretAccessKey,
    },
    // SeaweedFS (and MinIO) serve buckets by path, not by virtual host.
    forcePathStyle: true,
    // The SDK's default flexible checksums send a stream as aws-chunked with a
    // trailing CRC32, which not every S3-compatible store accepts; the storage
    // hash is file-service's own (ByteMeter), so plain signed PUTs are enough.
    requestChecksumCalculation: 'WHEN_REQUIRED',
    responseChecksumValidation: 'WHEN_REQUIRED',
    // Without a logger of its own the SDK falls back to console.warn for every
    // aborted streaming PUT. The store path already reports each failure as a
    // code (StoreFileUseCase), so the SDK stays quiet.
    logger: QUIET_SDK_LOGGER,
    requestHandler: {
      connectionTimeout: CONNECTION_TIMEOUT_MS,
      requestTimeout: SOCKET_TIMEOUT_MS,
      // Without it the SDK only logs a warning and the request goes on.
      throwOnRequestTimeout: true,
    },
  });
}

/**
 * ObjectStoragePort on one flat bucket (file-service-phase2-design.md §2.3).
 * `put` is a single PutObject whose body is the stream itself, with its exact
 * Content-Length: the SDK sends the bytes as they come, so nothing is held in
 * memory beyond the socket's buffers. No multipart upload: the largest file
 * any entrypoint accepts (20MB, EMAIL_INTAKE) is far below S3's 5GB PUT limit.
 */
export class S3ObjectStorage implements ObjectStoragePort {
  constructor(
    private readonly client: S3Client,
    private readonly bucket: string,
  ) {}

  async put(
    key: string,
    body: Readable,
    meta: { contentType: string; contentLength: number },
  ): Promise<void> {
    // A body that fails part way (too short, caller gone) only stops feeding
    // the request: the server would keep waiting for the rest of the declared
    // Content-Length until the socket timeout. Abort the request instead, so
    // nothing is stored and the failure comes back at once.
    const abort = new AbortController();
    const onBodyError = () => abort.abort();
    body.once('error', onBodyError);
    try {
      await this.client.send(
        new PutObjectCommand({
          Bucket: this.bucket,
          Key: key,
          Body: body,
          ContentLength: meta.contentLength,
          ContentType: meta.contentType,
        }),
        { abortSignal: abort.signal },
      );
    } finally {
      body.off('error', onBodyError);
    }
  }

  async delete(key: string): Promise<void> {
    // S3 DeleteObject succeeds for a key that does not exist.
    await this.client.send(
      new DeleteObjectCommand({ Bucket: this.bucket, Key: key }),
    );
  }

  /** Closes the client's sockets (shutdown). */
  close(): void {
    this.client.destroy();
  }

  /** Throws when the bucket cannot be reached (health check). */
  async ping(): Promise<void> {
    await this.client.send(new HeadBucketCommand({ Bucket: this.bucket }));
  }

  /**
   * Creates the bucket when it is missing (startup). Idempotent: an existing
   * bucket owned by this identity is left as it is.
   */
  async ensureBucket(): Promise<'EXISTS' | 'CREATED'> {
    try {
      await this.ping();
      return 'EXISTS';
    } catch (err) {
      if (!isNotFound(err)) throw err;
    }
    try {
      await this.client.send(new CreateBucketCommand({ Bucket: this.bucket }));
    } catch (err) {
      // Created by another instance in between.
      if (
        err instanceof S3ServiceException &&
        err.name === 'BucketAlreadyOwnedByYou'
      ) {
        return 'EXISTS';
      }
      throw err;
    }
    return 'CREATED';
  }
}

function isNotFound(err: unknown): boolean {
  return (
    err instanceof S3ServiceException &&
    (err.name === 'NotFound' ||
      err.name === 'NoSuchBucket' ||
      err.$metadata?.httpStatusCode === 404)
  );
}
