import { STATUS_CODES } from 'node:http';
import { IdempotencyConflict, IdempotencyKeyRequired, ValidationFailed } from '../../application/errors';

/**
 * Shared AgriContract error envelope (docs/contracts/error-catalog.md,
 * golden-flow-api.yaml#/components/schemas/ErrorEnvelope).
 */
export interface ErrorEnvelope {
  timestamp: string;
  status: number;
  error: string;
  code: string;
  message: string;
  path: string;
  traceId: string;
}

/** An error that already knows its HTTP response. Only the web layer creates these. */
export class HttpError extends Error {
  constructor(
    readonly status: number,
    readonly code: string,
    message: string,
  ) {
    super(message);
    this.name = 'HttpError';
  }
}

/**
 * The single place where application errors become HTTP status + wire code.
 * Returns undefined for unexpected errors (they become 500).
 */
export function toHttpError(err: unknown): HttpError | undefined {
  if (err instanceof HttpError) return err;
  if (err instanceof IdempotencyKeyRequired) return new HttpError(400, 'IDEMPOTENCY_KEY_REQUIRED', err.message);
  if (err instanceof ValidationFailed) return new HttpError(400, 'VALIDATION_FAILED', err.message);
  // *_STATE_CONFLICT family (error-catalog.md): do not retry the unchanged request.
  if (err instanceof IdempotencyConflict) return new HttpError(409, 'IDEMPOTENCY_KEY_STATE_CONFLICT', err.message);
  return undefined;
}

export function errorEnvelope(status: number, code: string, message: string, path: string, traceId: string): ErrorEnvelope {
  return {
    timestamp: new Date().toISOString(),
    status,
    error: STATUS_CODES[status] ?? 'Error',
    code,
    message,
    path,
    traceId,
  };
}
