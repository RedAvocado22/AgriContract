import Fastify, { type FastifyInstance } from 'fastify';
import { type HealthChecks, registerHealthRoute } from './health.js';

export function buildApp(checks: HealthChecks): FastifyInstance {
  const app = Fastify({
    logger: {
      level: 'info',
      redact: {
        paths: ['otpCode', '*.otpCode', 'req.headers["x-internal-secret"]'],
        censor: '[REDACTED]',
      },
    },
  });
  registerHealthRoute(app, checks);
  return app;
}
