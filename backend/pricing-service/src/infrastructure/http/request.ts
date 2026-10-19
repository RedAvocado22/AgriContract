import { randomUUID } from 'node:crypto';
import type { IncomingMessage, ServerResponse } from 'node:http';
import { HttpError } from './errors';

const MAX_BODY_BYTES = 64 * 1024;

export function headerValue(req: IncomingMessage, name: string): string | undefined {
  const v = req.headers[name];
  return Array.isArray(v) ? v[0] : v;
}

/** The trace id of this request (X-Request-Id or a new UUID), also returned as X-Trace-Id. Same value on every call. */
export function traceIdOf(req: IncomingMessage, res: ServerResponse): string {
  const existing = res.getHeader('X-Trace-Id');
  if (typeof existing === 'string') return existing;
  const traceId = headerValue(req, 'x-request-id') ?? randomUUID();
  res.setHeader('X-Trace-Id', traceId);
  return traceId;
}

/**
 * Reads the JSON body itself (the app is created with bodyParser: false) so that authorization, which
 * runs in a guard, always comes before any body check (415 / 413 / 400).
 */
export async function readJsonBody(req: IncomingMessage): Promise<unknown> {
  const contentType = headerValue(req, 'content-type') ?? '';
  if (!contentType.toLowerCase().startsWith('application/json')) {
    throw new HttpError(415, 'UNSUPPORTED_MEDIA_TYPE', 'Content-Type must be application/json');
  }
  const chunks: Buffer[] = [];
  let size = 0;
  for await (const chunk of req) {
    const buf = chunk as Buffer;
    size += buf.length;
    if (size > MAX_BODY_BYTES) throw new HttpError(413, 'PAYLOAD_TOO_LARGE', 'Request body is too large');
    chunks.push(buf);
  }
  try {
    return JSON.parse(Buffer.concat(chunks).toString('utf8'));
  } catch {
    throw new HttpError(400, 'VALIDATION_FAILED', 'Request body is not valid JSON');
  }
}
