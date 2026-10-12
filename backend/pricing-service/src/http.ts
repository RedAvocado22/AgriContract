import { randomUUID } from 'node:crypto';
import { createServer, type IncomingMessage, type Server, type ServerResponse } from 'node:http';
import { MANUAL_PRICE_ROLES, authenticate, requireAnyRole } from './auth.js';
import { HttpError, errorEnvelope } from './errors.js';
import { recordManualPrice, type ManualPriceDeps } from './service.js';

export const MANUAL_PRICE_PATH = '/api/v1/prices/manual';
const MAX_BODY_BYTES = 64 * 1024;

export interface AppDeps extends ManualPriceDeps {
  gatewaySecret: string;
}

/**
 * Pricing-service HTTP surface for this increment. Uses Node's built-in http module.
 * Public read endpoints (GET /api/v1/prices/...) are a separate increment.
 */
export function createApp(deps: AppDeps): Server {
  return createServer((req, res) => {
    void handle(req, res, deps);
  });
}

async function handle(req: IncomingMessage, res: ServerResponse, deps: AppDeps): Promise<void> {
  const path = (req.url ?? '/').split('?')[0] ?? '/';
  const traceId = headerValue(req, 'x-request-id') ?? randomUUID();
  res.setHeader('X-Trace-Id', traceId);

  try {
    if (path === '/health') {
      if (req.method !== 'GET') throw new HttpError(405, 'METHOD_NOT_ALLOWED', 'Use GET');
      return sendJson(res, 200, { status: 'UP' });
    }

    if (path === MANUAL_PRICE_PATH) {
      if (req.method !== 'POST') throw new HttpError(405, 'METHOD_NOT_ALLOWED', 'Use POST');
      const actor = authenticate(req.headers, deps.gatewaySecret);
      requireAnyRole(actor, MANUAL_PRICE_ROLES);
      const body = await readJsonBody(req);
      const result = await recordManualPrice(deps, actor, headerValue(req, 'idempotency-key'), body);
      if (result.replayed) res.setHeader('Idempotent-Replayed', 'true');
      return sendJson(res, 201, result.quote);
    }

    throw new HttpError(404, 'NOT_FOUND', 'No route for this path');
  } catch (err) {
    if (err instanceof HttpError) {
      return sendJson(res, err.status, errorEnvelope(err.status, err.code, err.message, path, traceId));
    }
    deps.logger.error('Unhandled error', {
      path,
      traceId,
      error: err instanceof Error ? err.message : String(err),
    });
    return sendJson(res, 500, errorEnvelope(500, 'INTERNAL_ERROR', 'Unexpected server error', path, traceId));
  }
}

async function readJsonBody(req: IncomingMessage): Promise<unknown> {
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

function sendJson(res: ServerResponse, status: number, body: unknown): void {
  const payload = JSON.stringify(body);
  res.writeHead(status, {
    'Content-Type': 'application/json; charset=utf-8',
    'Content-Length': Buffer.byteLength(payload),
  });
  res.end(payload);
}

function headerValue(req: IncomingMessage, name: string): string | undefined {
  const v = req.headers[name];
  return Array.isArray(v) ? v[0] : v;
}
