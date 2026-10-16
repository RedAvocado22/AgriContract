import { STATUS_CODES } from 'node:http';
import type { FastifyReply, FastifyRequest } from 'fastify';

/** ErrorEnvelope in golden-flow-api.yaml. */
export interface ErrorEnvelope {
  timestamp: string;
  status: number;
  error: string;
  code: string;
  message: string;
  path: string;
  traceId: string;
}

export function sendError(
  request: FastifyRequest,
  reply: FastifyReply,
  status: number,
  code: string,
  message: string,
): FastifyReply {
  const body: ErrorEnvelope = {
    timestamp: new Date().toISOString(),
    status,
    error: STATUS_CODES[status] ?? 'Error',
    code,
    message,
    path: request.url.split('?')[0] ?? request.url,
    traceId: request.id,
  };
  return reply.code(status).send(body);
}
