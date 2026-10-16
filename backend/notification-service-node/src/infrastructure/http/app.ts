import Fastify, { type FastifyInstance } from 'fastify';
import type { DestinationStream } from 'pino';
import { type HealthChecks, registerHealthRoute } from './health.js';
import { type OtpEmailRouteOptions, registerOtpEmailRoute } from './otpEmailRoute.js';

export interface AppOptions {
  otpEmail?: OtpEmailRouteOptions;
  /** Log destination; tests pass one to inspect what was written. */
  logStream?: DestinationStream;
}

// Request bodies are never logged. These paths catch the OTP and the secret if an object that
// holds them is ever passed to the logger by mistake.
const REDACT_PATHS = [
  'otpCode',
  '*.otpCode',
  '*.*.otpCode',
  'req.body.otpCode',
  'request.body.otpCode',
  'req.headers["x-internal-secret"]',
  '*.headers["x-internal-secret"]',
];

export function buildApp(checks: HealthChecks, options: AppOptions = {}): FastifyInstance {
  const app = Fastify({
    logger: {
      level: 'info',
      redact: { paths: REDACT_PATHS, censor: '[REDACTED]' },
      ...(options.logStream === undefined ? {} : { stream: options.logStream }),
    },
  });
  registerHealthRoute(app, checks);
  if (options.otpEmail !== undefined) {
    registerOtpEmailRoute(app, options.otpEmail);
  }
  return app;
}
