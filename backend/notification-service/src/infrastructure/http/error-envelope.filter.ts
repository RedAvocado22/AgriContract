import {
  Catch,
  HttpException,
  HttpStatus,
  Logger,
  type ArgumentsHost,
  type ExceptionFilter,
} from '@nestjs/common';
import { randomUUID } from 'node:crypto';
import type { Request, Response } from 'express';
import { failureCode } from '../../application/notification-errors';
import { OtpEmailRequestInvalidError } from '../../application/otp/otp-email-request';
import {
  OtpEmailDeliveryError,
  OtpEmailInProgressError,
  OtpEmailLogUnavailableError,
} from '../../application/otp/send-otp-email.use-case';

/** ErrorEnvelope of golden-flow-api.yaml / docs/contracts/error-catalog.md. */
export interface ErrorEnvelope {
  timestamp: string;
  status: number;
  error: string;
  code: string;
  message: string;
  path: string;
  traceId: string;
}

interface Mapped {
  status: number;
  code: string;
  message: string;
  /** Safe log line (ids and codes only), or undefined when nothing is logged. */
  log?: string;
}

/**
 * Turns every error of the internal HTTP routes into an ErrorEnvelope. Messages
 * are fixed texts or field names, never request values: error-catalog.md
 * forbids OTPs and unnecessary PII in `message`. It replaces Nest's default
 * handler, which would log an unknown error's message as it is.
 */
@Catch()
export class ErrorEnvelopeFilter implements ExceptionFilter {
  private readonly logger = new Logger('InternalApi');

  catch(exception: unknown, host: ArgumentsHost): void {
    const http = host.switchToHttp();
    const req = http.getRequest<Request>();
    const res = http.getResponse<Response>();
    const mapped = map(exception);
    const traceId = randomUUID();

    if (mapped.log) {
      const line = `${req.method} ${req.path} -> ${mapped.status} ${mapped.code}: ${mapped.log} [traceId ${traceId}]`;
      if (mapped.status >= 500) this.logger.error(line);
      else this.logger.warn(line);
    }

    const body: ErrorEnvelope = {
      timestamp: new Date().toISOString(),
      status: mapped.status,
      error: reasonPhrase(mapped.status),
      code: mapped.code,
      message: mapped.message,
      path: req.path,
      traceId,
    };
    res.status(mapped.status).json(body);
  }
}

function map(exception: unknown): Mapped {
  if (exception instanceof OtpEmailRequestInvalidError) {
    return {
      status: HttpStatus.BAD_REQUEST,
      code: 'VALIDATION_ERROR',
      message: exception.problems.join('; '),
    };
  }
  if (exception instanceof OtpEmailDeliveryError) {
    return {
      status: HttpStatus.BAD_GATEWAY,
      code: 'OTP_EMAIL_PROVIDER_FAILED',
      message: 'The mail provider did not accept the OTP email',
      log: exception.message,
    };
  }
  if (exception instanceof OtpEmailInProgressError) {
    return {
      status: HttpStatus.SERVICE_UNAVAILABLE,
      code: 'OTP_EMAIL_IN_PROGRESS',
      message: 'Another call is sending this OTP email; retry later',
      log: exception.message,
    };
  }
  if (exception instanceof OtpEmailLogUnavailableError) {
    return {
      status: HttpStatus.SERVICE_UNAVAILABLE,
      code: 'NOTIFICATION_LOG_UNAVAILABLE',
      message: 'The notification log is unavailable; nothing was sent',
      log: exception.message,
    };
  }
  if (exception instanceof HttpException) {
    const status = exception.getStatus();
    if (status === 401) {
      return { status, code: 'UNAUTHORIZED', message: 'Unauthorized' };
    }
    if (status === 404) {
      return { status, code: 'NOT_FOUND', message: 'Not found' };
    }
    if (status < 500) {
      return { status, code: 'BAD_REQUEST', message: reasonPhrase(status) };
    }
  }
  return {
    status: HttpStatus.INTERNAL_SERVER_ERROR,
    code: 'INTERNAL_SERVER_ERROR',
    message: 'Internal server error',
    log: `unexpected error (${failureCode(exception)})`,
  };
}

const REASONS: Record<number, string> = {
  400: 'Bad Request',
  401: 'Unauthorized',
  404: 'Not Found',
  500: 'Internal Server Error',
  502: 'Bad Gateway',
  503: 'Service Unavailable',
};

function reasonPhrase(status: number): string {
  return REASONS[status] ?? (status >= 500 ? 'Server Error' : 'Client Error');
}
