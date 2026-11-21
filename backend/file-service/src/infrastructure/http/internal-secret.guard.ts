import {
  Injectable,
  UnauthorizedException,
  type CanActivate,
  type ExecutionContext,
} from '@nestjs/common';
import { ConfigService } from '@nestjs/config';
import { createHash, timingSafeEqual } from 'node:crypto';
import type { Request } from 'express';
import type { AppConfig } from '../../config/env.validation';

export const INTERNAL_SECRET_HEADER = 'x-internal-secret';

/**
 * The caller sends SERVICE_INTERNAL_SECRET in X-Internal-Secret, as every
 * /internal endpoint checks it (same guard as notification-service). A
 * repeated header (Node joins it into "a, b") never matches. Both values are
 * hashed before the constant-time comparison so their lengths do not leak.
 */
@Injectable()
export class InternalSecretGuard implements CanActivate {
  private readonly expected: Buffer;

  constructor(config: ConfigService<AppConfig, true>) {
    this.expected = digest(
      config.get('serviceInternalSecret', { infer: true }),
    );
  }

  canActivate(context: ExecutionContext): boolean {
    const header = context.switchToHttp().getRequest<Request>().headers[
      INTERNAL_SECRET_HEADER
    ];
    if (typeof header !== 'string' || header === '') {
      throw new UnauthorizedException();
    }
    if (!timingSafeEqual(digest(header), this.expected)) {
      throw new UnauthorizedException();
    }
    return true;
  }
}

function digest(value: string): Buffer {
  return createHash('sha256').update(value, 'utf8').digest();
}
