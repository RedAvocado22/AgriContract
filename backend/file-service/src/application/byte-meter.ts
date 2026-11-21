import { createHash } from 'node:crypto';
import { Transform, type TransformCallback } from 'node:stream';
import { FileLengthMismatchError } from './file-errors';

/**
 * Passes the bytes through unchanged while it counts them and feeds a SHA-256,
 * so the storage hash and the real size come from the bytes that were stored,
 * in the same single pass.
 *
 * It fails the stream (FileLengthMismatchError) as soon as the body yields one
 * byte more than declared, and at the end if it yielded fewer. The declared
 * length is already within the entrypoint's cap, so this is also the cap
 * enforced while streaming: a body that lies about its length is cut off at
 * the first extra chunk, never read to the end.
 *
 * It holds back the latest chunk until the next one arrives or the body ends
 * with exactly the declared length. Storage is sent a Content-Length equal to
 * the declared length, so it commits the object once it has that many bytes:
 * without the hold-back, a body longer than declared would hand over the full
 * declared length before its extra chunk showed up, and the object would be
 * committed while the store was failing (a race the blob removal cannot win).
 * With it, storage never gets the last bytes of a body that turns out too
 * long or broken. Memory stays bounded by one chunk (64 KiB from a socket).
 */
export class ByteMeter extends Transform {
  private readonly hash = createHash('sha256');
  private received = 0;
  private held: Buffer | null = null;
  private digest: string | null = null;

  constructor(private readonly declared: number) {
    super();
  }

  /** Bytes read from the body so far (some of them may still be held back). */
  get bytes(): number {
    return this.received;
  }

  /** SHA-256 hex of every byte, once the body has ended with the declared length. */
  get sha256(): string {
    if (this.digest === null) {
      throw new Error('ByteMeter: the body has not ended yet');
    }
    return this.digest;
  }

  override _transform(
    chunk: Buffer,
    _encoding: BufferEncoding,
    callback: TransformCallback,
  ): void {
    this.received += chunk.length;
    if (this.received > this.declared) {
      this.held = null;
      callback(new FileLengthMismatchError(this.declared, this.received));
      return;
    }
    this.hash.update(chunk);
    const previous = this.held;
    this.held = chunk;
    callback(null, previous ?? undefined);
  }

  override _flush(callback: TransformCallback): void {
    if (this.received !== this.declared) {
      this.held = null;
      callback(new FileLengthMismatchError(this.declared, this.received));
      return;
    }
    this.digest = this.hash.digest('hex');
    const last = this.held;
    this.held = null;
    callback(null, last ?? undefined);
  }
}
