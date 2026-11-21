/*
 * Errors of the store path. Messages name sizes and content types only: never
 * the uploader, the bytes or a storage/driver text (those go to the log as a
 * code, see infrastructure/describe-error.ts).
 */

const SAFE_CODE = /^[A-Za-z][A-Za-z0-9_/.-]{0,63}$/;

/**
 * A failure code that is safe to log and to return: the error's `code` (e.g.
 * ECONNREFUSED, ER_DUP_ENTRY) or the S3 error name (NoSuchBucket) when it
 * looks like one, otherwise its class name. Never the message, which may
 * quote hosts, keys or payload text.
 */
export function failureCode(err: unknown): string {
  const code = (err as { code?: unknown } | null)?.code;
  if (typeof code === 'string' && SAFE_CODE.test(code)) return code;
  // A bare Error often wraps the useful one: connecting to a host tries every
  // address and throws an AggregateError, and SDKs set `cause`.
  if (err instanceof AggregateError && err.errors.length > 0) {
    return failureCode(err.errors[0]);
  }
  if (err instanceof Error && err.cause !== undefined && err.name === 'Error') {
    return failureCode(err.cause);
  }
  if (err instanceof Error && SAFE_CODE.test(err.name)) return err.name;
  return 'UNKNOWN';
}

/** Base class: every error the store path throws on purpose. */
export abstract class FileServiceError extends Error {
  constructor(message: string) {
    super(message);
    this.name = new.target.name;
  }
}

/** 400: the content type is not one the entrypoint accepts. */
export class UnsupportedContentTypeError extends FileServiceError {
  constructor(readonly allowed: readonly string[]) {
    super(`Content type not accepted; allowed: ${allowed.join(', ')}`);
  }
}

/** 400 (413 on the wire): more bytes than the entrypoint accepts, declared or sent. */
export class FileTooLargeError extends FileServiceError {
  constructor(readonly maxBytes: number) {
    super(`File larger than ${maxBytes} bytes`);
  }
}

/** 400: an empty body, or a declared length that is not a positive integer. */
export class InvalidFileLengthError extends FileServiceError {
  constructor() {
    super('File length must be a positive integer');
  }
}

/** 400: the body did not yield the number of bytes it declared. */
export class FileLengthMismatchError extends FileServiceError {
  constructor(
    readonly declared: number,
    readonly received: number,
  ) {
    super(
      received > declared
        ? `File sent more than the ${declared} bytes it declared`
        : `File ended after ${received} of the ${declared} bytes it declared`,
    );
  }
}

/** 503: object storage did not take the bytes; nothing is recorded. */
export class ObjectStorageUnavailableError extends FileServiceError {
  constructor(readonly code: string) {
    super(`Object storage unavailable (${code})`);
  }
}

/** 503: the bytes were stored but the metadata row was not; the blob is removed. */
export class FileMetadataUnavailableError extends FileServiceError {
  constructor(readonly code: string) {
    super(`File metadata could not be recorded (${code})`);
  }
}
