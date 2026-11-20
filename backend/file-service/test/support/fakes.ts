import { Readable } from 'node:stream';
import {
  IdempotencyKeyTakenError,
  type FileRepository,
} from '../../src/application/ports/file.repository';
import type { ObjectStoragePort } from '../../src/application/ports/object-storage.port';
import type { StoredFile } from '../../src/domain/stored-file';

/** In-memory bucket. Reads the body like S3 would and keeps what it received. */
export class FakeObjectStorage implements ObjectStoragePort {
  readonly objects = new Map<
    string,
    { bytes: Buffer; contentType: string; contentLength: number }
  >();
  readonly deleted: string[] = [];
  /** Fails `put` with this error after the body is read (or before, see failBeforeRead). */
  putError: Error | null = null;
  failBeforeRead = false;
  deleteError: Error | null = null;
  /** Called with each chunk as it arrives, to observe streaming. */
  onChunk: (chunk: Buffer) => void = () => undefined;

  async put(
    key: string,
    body: Readable,
    meta: { contentType: string; contentLength: number },
  ): Promise<void> {
    if (this.putError && this.failBeforeRead) {
      body.destroy();
      throw this.putError;
    }
    const chunks: Buffer[] = [];
    for await (const chunk of body as AsyncIterable<Buffer>) {
      this.onChunk(chunk);
      chunks.push(chunk);
    }
    if (this.putError) throw this.putError;
    this.objects.set(key, { bytes: Buffer.concat(chunks), ...meta });
  }

  delete(key: string): Promise<void> {
    this.deleted.push(key);
    if (this.deleteError) return Promise.reject(this.deleteError);
    this.objects.delete(key);
    return Promise.resolve();
  }
}

/** The `file` table, with its unique (uploadedBy, idempotencyKey) index. */
export class InMemoryFileRepository implements FileRepository {
  readonly rows = new Map<string, StoredFile>();
  insertError: Error | null = null;
  findError: Error | null = null;
  /** Runs just before insert checks the index, to let a concurrent store win. */
  beforeInsert: (file: StoredFile) => void = () => undefined;

  insert(file: StoredFile): Promise<void> {
    this.beforeInsert(file);
    if (this.insertError) return Promise.reject(this.insertError);
    if (this.rows.has(file.fileId)) {
      return Promise.reject(new Error('duplicate fileId'));
    }
    if (
      file.idempotencyKey !== null &&
      this.byKey(file.uploadedBy, file.idempotencyKey)
    ) {
      return Promise.reject(new IdempotencyKeyTakenError());
    }
    this.rows.set(file.fileId, { ...file });
    return Promise.resolve();
  }

  findById(fileId: string): Promise<StoredFile | null> {
    if (this.findError) return Promise.reject(this.findError);
    return Promise.resolve(this.rows.get(fileId) ?? null);
  }

  findByIdempotencyKey(
    uploadedBy: string,
    idempotencyKey: string,
  ): Promise<StoredFile | null> {
    if (this.findError) return Promise.reject(this.findError);
    const row = this.byKey(uploadedBy, idempotencyKey);
    return Promise.resolve(row ? { ...row } : null);
  }

  private byKey(uploadedBy: string, key: string): StoredFile | undefined {
    return [...this.rows.values()].find(
      (r) => r.uploadedBy === uploadedBy && r.idempotencyKey === key,
    );
  }
}

/** `size` bytes in chunks of `chunkSize`, produced only when read; logs each chunk it makes. */
export function lazyBody(
  size: number,
  chunkSize = 64 * 1024,
  produced: (index: number) => void = () => undefined,
): Readable {
  let sent = 0;
  let index = 0;
  return new Readable({
    read() {
      if (sent >= size) {
        this.push(null);
        return;
      }
      const length = Math.min(chunkSize, size - sent);
      const chunk = Buffer.alloc(length, index % 251);
      sent += length;
      produced(index++);
      this.push(chunk);
    },
  });
}
