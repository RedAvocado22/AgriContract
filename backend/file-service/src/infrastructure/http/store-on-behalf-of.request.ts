import type { IncomingHttpHeaders } from 'node:http';

/**
 * The headers of POST /internal/v1/files (supplementary-services-api.yaml
 * storeOnBehalfOf, D60). The body is the file itself, so everything else
 * comes in headers. X-Internal-Secret is the guard's.
 */
export interface StoreOnBehalfOfRequest {
  /** X-Acting-User-Id, lower case: stored as uploaded_by. */
  actingUserId: string;
  idempotencyKey: string;
  /** As sent; normalized and checked against the whitelist by the store core. */
  contentType: string;
  /** Content-Length; checked against the 10 MiB cap by the store core. */
  contentLength: number;
}

/** 400 VALIDATION_ERROR: one fixed text per bad header, never its value. */
export class StoreOnBehalfOfRequestInvalidError extends Error {
  constructor(readonly problems: readonly string[]) {
    super(`Invalid upload request: ${problems.join('; ')}`);
    this.name = StoreOnBehalfOfRequestInvalidError.name;
  }
}

const UUID = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;

/**
 * Printable ASCII, 1 to 255 characters (the contract's maxLength and the
 * column's): the column is byte-wise ASCII, so a key is stored exactly as
 * sent. Node already strips the spaces around a header value.
 */
const IDEMPOTENCY_KEY = /^[\x20-\x7e]{1,255}$/;

const DIGITS = /^\d+$/;

export function parseStoreOnBehalfOfRequest(
  headers: IncomingHttpHeaders,
): StoreOnBehalfOfRequest {
  const problems: string[] = [];

  const actingUserId = headers['x-acting-user-id'];
  if (typeof actingUserId !== 'string' || !UUID.test(actingUserId)) {
    problems.push('X-Acting-User-Id must be a UUID');
  }

  const idempotencyKey = headers['idempotency-key'];
  if (
    typeof idempotencyKey !== 'string' ||
    !IDEMPOTENCY_KEY.test(idempotencyKey)
  ) {
    problems.push(
      'Idempotency-Key must be 1 to 255 printable ASCII characters',
    );
  }

  const contentLength = declaredLength(headers);
  if (contentLength === null) {
    problems.push('Content-Length is required; a chunked body is not accepted');
  }

  if (problems.length > 0) {
    throw new StoreOnBehalfOfRequestInvalidError(problems);
  }
  return {
    actingUserId: (actingUserId as string).toLowerCase(),
    idempotencyKey: idempotencyKey as string,
    contentType: headers['content-type'] ?? '',
    contentLength: contentLength as number,
  };
}

/**
 * The body length the request declared, or null when it declared none it can
 * be held to (no Content-Length, or a chunked body). A number too long to be
 * exact still counts, as more than any cap: it is a length, not a typo.
 */
export function declaredLength(headers: IncomingHttpHeaders): number | null {
  const raw = headers['content-length'];
  if (headers['transfer-encoding'] !== undefined) return null;
  if (typeof raw !== 'string' || !DIGITS.test(raw)) return null;
  const length = Number(raw);
  return Number.isSafeInteger(length) ? length : Number.MAX_SAFE_INTEGER;
}
