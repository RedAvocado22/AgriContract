import { STATUS_CODES } from 'node:http';

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
