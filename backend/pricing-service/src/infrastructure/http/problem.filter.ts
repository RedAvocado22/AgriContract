import { ArgumentsHost, Catch, ExceptionFilter, HttpException, Inject, NotFoundException } from '@nestjs/common';
import type { IncomingMessage, ServerResponse } from 'node:http';
import type { Logger } from '../../application/port';
import { LOGGER } from '../nest/tokens';
import { HttpError, errorEnvelope, toHttpError } from './errors';
import { traceIdOf } from './request';
import { ALLOWED_METHOD } from './routes';

/**
 * Every error leaves the service as the shared AgriContract error envelope
 * (docs/contracts/error-catalog.md): application errors via toHttpError, Nest routing misses as
 * 404 / 405, anything else as a 500 that never leaks the cause.
 */
@Catch()
export class ProblemFilter implements ExceptionFilter {
  constructor(@Inject(LOGGER) private readonly logger: Logger) {}

  catch(exception: unknown, host: ArgumentsHost): void {
    const http = host.switchToHttp();
    const req = http.getRequest<IncomingMessage>();
    const res = http.getResponse<ServerResponse>();
    const path = (req.url ?? '/').split('?')[0] ?? '/';
    const traceId = traceIdOf(req, res);

    const known = toHttpError(exception) ?? routingError(exception, req.method, path);
    if (known) {
      sendJson(res, known.status, errorEnvelope(known.status, known.code, known.message, path, traceId));
      return;
    }
    this.logger.error('Unhandled error', {
      path,
      traceId,
      error: exception instanceof Error ? exception.message : String(exception),
    });
    sendJson(res, 500, errorEnvelope(500, 'INTERNAL_ERROR', 'Unexpected server error', path, traceId));
  }
}

/** Nest answers an unmatched route (unknown path or wrong method) with NotFoundException. */
function routingError(exception: unknown, method: string | undefined, path: string): HttpError | undefined {
  if (!(exception instanceof HttpException)) return undefined;
  if (!(exception instanceof NotFoundException)) return undefined;
  const allowed = ALLOWED_METHOD[path];
  if (allowed && method !== allowed && !(allowed === 'GET' && method === 'HEAD')) {
    return new HttpError(405, 'METHOD_NOT_ALLOWED', `Use ${allowed}`);
  }
  return new HttpError(404, 'NOT_FOUND', 'No route for this path');
}

function sendJson(res: ServerResponse, status: number, body: unknown): void {
  const payload = JSON.stringify(body);
  res.writeHead(status, {
    'Content-Type': 'application/json; charset=utf-8',
    'Content-Length': Buffer.byteLength(payload),
  });
  res.end(payload);
}
