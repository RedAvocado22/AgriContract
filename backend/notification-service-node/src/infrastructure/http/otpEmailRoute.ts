import { Ajv } from 'ajv';
import addFormatsPlugin from 'ajv-formats';
import type { FastifyError, FastifyInstance, FastifyReply, FastifyRequest } from 'fastify';
import type {
  OtpEmailOutcome,
  OtpEmailRequest,
  SendOtpEmail,
} from '../../application/sendOtpEmail.js';
import { errorCode } from '../../application/sendOtpEmail.js';
import { otpEmailRequestSchema } from '../../contracts/generated/otpEmailRequest.schema.js';
import { sendError } from './errorEnvelope.js';
import { INTERNAL_SECRET_HEADER, isValidInternalSecret } from './internalAuth.js';

export const OTP_EMAIL_PATH = '/internal/v1/notifications/otp-email';

export interface OtpEmailRouteOptions {
  serviceInternalSecret: string;
  sendOtpEmail: Pick<SendOtpEmail, 'execute'>;
  now?: () => Date;
}

// Own Ajv instance: Fastify's default one coerces types and strips unknown properties, which
// would accept what the contract rejects (additionalProperties: false).
const ajv = new Ajv({
  strict: true,
  coerceTypes: false,
  removeAdditional: false,
  useDefaults: false,
});
// ajv-formats is CommonJS; its default export arrives as the module object under NodeNext.
const addFormats = addFormatsPlugin as unknown as typeof addFormatsPlugin.default;
addFormats(ajv, ['email', 'date-time']);
const validateBody = ajv.compile(otpEmailRequestSchema);

const OUTCOME_RESPONSES: Record<
  Exclude<OtpEmailOutcome, 'SENT' | 'ALREADY_SENT'>,
  { code: string; message: string }
> = {
  PREVIOUSLY_FAILED: {
    code: 'OTP_EMAIL_PREVIOUSLY_FAILED',
    message: 'An earlier send for this requestId failed; create a new OTP and requestId',
  },
  IN_PROGRESS: {
    code: 'OTP_EMAIL_IN_PROGRESS',
    message: 'An earlier send for this requestId did not finish; create a new OTP and requestId',
  },
  NOT_SENT_DEADLINE: {
    code: 'OTP_EMAIL_DEADLINE_EXCEEDED',
    message: 'Not sent: the time budget ran out before sending; create a new OTP and requestId',
  },
  PROVIDER_FAILED: {
    code: 'OTP_EMAIL_PROVIDER_FAILED',
    message: 'Mail provider did not accept the message; create a new OTP and requestId',
  },
  DELIVERY_UNKNOWN: {
    code: 'OTP_EMAIL_DELIVERY_UNKNOWN',
    message:
      'Mail provider did not confirm in time and may still deliver; treat as failed and create a new OTP and requestId',
  },
  RESULT_NOT_RECORDED: {
    code: 'OTP_EMAIL_RESULT_NOT_RECORDED',
    message: 'Delivery result could not be recorded; create a new OTP and requestId',
  },
};

/**
 * The contract's x-validation rule, which JSON Schema cannot express. Field lengths (which match
 * the notification_logs columns) are in the schema itself. Returns a message, or null.
 */
function semanticProblem(body: OtpEmailRequest, now: Date): string | null {
  const expiresAt = Date.parse(body.expiresAt);
  if (Number.isNaN(expiresAt) || expiresAt <= now.getTime()) {
    return 'expiresAt must be in the future';
  }
  return null;
}

// Messages are fixed or built from schema paths, never from the request body: a JSON parse
// error message quotes the raw input, which may contain the OTP.
function handleError(error: FastifyError, request: FastifyRequest, reply: FastifyReply): void {
  if (error.validation !== undefined) {
    const issue = error.validation[0];
    const where = issue?.instancePath === '' ? 'body' : `body${issue?.instancePath ?? ''}`;
    void sendError(
      request,
      reply,
      400,
      'VALIDATION_ERROR',
      `${where} ${issue?.message ?? 'is invalid'}`,
    );
    return;
  }
  // The contract documents 400 for every bad request, so oversized bodies and other media types
  // are 400 too rather than Fastify's 413/415.
  const status = error.statusCode ?? 500;
  if (status === 413) {
    void sendError(
      request,
      reply,
      400,
      'MALFORMED_REQUEST',
      'Request body is larger than 4096 bytes',
    );
  } else if (status === 415) {
    void sendError(
      request,
      reply,
      400,
      'MALFORMED_REQUEST',
      'Content-Type must be application/json',
    );
  } else if (status >= 400 && status < 500) {
    void sendError(request, reply, 400, 'MALFORMED_REQUEST', 'Request body is not valid JSON');
  } else {
    request.log.error({ errorCode: errorCode(error) }, 'otp email request failed');
    void sendError(request, reply, 500, 'INTERNAL_ERROR', 'Internal error');
  }
}

export function registerOtpEmailRoute(app: FastifyInstance, options: OtpEmailRouteOptions): void {
  const now = options.now ?? (() => new Date());

  app.post<{ Body: OtpEmailRequest }>(
    OTP_EMAIL_PATH,
    {
      schema: { body: otpEmailRequestSchema },
      validatorCompiler: () => validateBody,
      bodyLimit: 4096,
      errorHandler: handleError,
      // Runs before the body is read, so unauthenticated callers never get it parsed.
      onRequest: async (request, reply) => {
        if (
          !isValidInternalSecret(
            request.headers[INTERNAL_SECRET_HEADER],
            options.serviceInternalSecret,
          )
        ) {
          return sendError(
            request,
            reply,
            401,
            'UNAUTHORIZED',
            'Missing or invalid X-Internal-Secret',
          );
        }
      },
    },
    async (request, reply) => {
      // elapsedTime runs from the start of onRequest, so the budget covers auth and parsing.
      const receivedAt = Date.now() - reply.elapsedTime;
      const problem = semanticProblem(request.body, now());
      if (problem !== null) {
        return sendError(request, reply, 400, 'VALIDATION_ERROR', problem);
      }

      const outcome = await options.sendOtpEmail.execute(request.body, request.log, receivedAt);
      if (outcome === 'SENT' || outcome === 'ALREADY_SENT') {
        // The contract defines no body for 200.
        return reply.code(200).send();
      }
      const { code, message } = OUTCOME_RESPONSES[outcome];
      return sendError(request, reply, 502, code, message);
    },
  );
}
