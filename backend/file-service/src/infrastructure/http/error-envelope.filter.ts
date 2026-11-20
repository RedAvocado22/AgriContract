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
import {
  FileLengthMismatchError,
  FileMetadataUnavailableError,
  FileTooLargeError,
  InvalidFileLengthError,
  ObjectStorageUnavailableError,
  UnsupportedContentTypeError,
  failureCode,
} from '../../application/file-errors';
import { StoreOnBehalfOfRequestInvalidError } from './store-on-behalf-of.request';
import { discardUnreadBody } from './unread-body';

/**
 * ErrorEnvelope of supplementary-services-api.yaml ("identical to
 * golden-flow"), docs/contracts/error-catalog.md.
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

interface Mapped {
  status: number;
  code: string;
  message: string;
  /** Safe log line (codes and sizes only), or undefined when nothing is logged. */
  log?: string;
}

/**
 * Turns every error of the internal HTTP routes into an ErrorEnvelope. The
 * store path's messages name sizes and content types only (file-errors.ts),
 * never the uploader, a key or a storage text. It replaces Nest's default
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
    // Answer once the rest of the body is out of the way (see unread-body.ts).
    void discardUnreadBody(req, res).then(() => {
      if (!res.headersSent && !res.destroyed) {
        res.status(mapped.status).json(body);
      }
    });
  }
}

function map(exception: unknown): Mapped {
  if (exception instanceof StoreOnBehalfOfRequestInvalidError) {
    return {
      status: HttpStatus.BAD_REQUEST,
      code: 'VALIDATION_ERROR',
      message: exception.problems.join('; '),
    };
  }
  if (
    exception instanceof UnsupportedContentTypeError ||
    exception instanceof InvalidFileLengthError ||
    exception instanceof FileLengthMismatchError
  ) {
    return {
      status: HttpStatus.BAD_REQUEST,
      code: 'VALIDATION_ERROR',
      message: exception.message,
    };
  }
  if (exception instanceof FileTooLargeError) {
    return {
      status: HttpStatus.PAYLOAD_TOO_LARGE,
      code: 'FILE_TOO_LARGE',
      message: exception.message,
    };
  }
  if (exception instanceof ObjectStorageUnavailableError) {
    return {
      status: HttpStatus.SERVICE_UNAVAILABLE,
      code: 'FILE_STORAGE_UNAVAILABLE',
      message: 'Object storage is unavailable; nothing was kept',
      log: exception.message,
    };
  }
  if (exception instanceof FileMetadataUnavailableError) {
    return {
      status: HttpStatus.SERVICE_UNAVAILABLE,
      code: 'FILE_METADATA_UNAVAILABLE',
      message: 'The file database is unavailable; nothing was kept',
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
  413: 'Payload Too Large',
  500: 'Internal Server Error',
  503: 'Service Unavailable',
};

function reasonPhrase(status: number): string {
  return REASONS[status] ?? (status >= 500 ? 'Server Error' : 'Client Error');
}
