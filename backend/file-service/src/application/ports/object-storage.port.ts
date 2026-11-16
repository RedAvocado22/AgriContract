import type { Readable } from 'node:stream';

export const OBJECT_STORAGE = Symbol('OBJECT_STORAGE');

/**
 * The S3-compatible bucket (file-service-phase2-design.md §2.3: one flat
 * bucket, object key = fileId). Compose runs SeaweedFS; the design says MinIO.
 */
export interface ObjectStoragePort {
  /**
   * Streams `body` to `key`. `contentLength` is the exact number of bytes the
   * body yields: the object is written in one PUT as the bytes arrive, never
   * buffered whole. If the body errors or ends early, nothing is stored and
   * the promise rejects with the body's error when it had one.
   */
  put(
    key: string,
    body: Readable,
    meta: { contentType: string; contentLength: number },
  ): Promise<void>;

  /** Removes `key`; succeeds when it does not exist. */
  delete(key: string): Promise<void>;
}
