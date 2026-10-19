import { CanActivate, ExecutionContext, Inject, Injectable } from '@nestjs/common';
import type { IncomingMessage } from 'node:http';
import type { Actor } from '../../application/port';
import { GATEWAY_SECRET } from '../nest/tokens';
import { MANUAL_PRICE_ROLES, authenticate, requireAnyRole } from './auth';

export type ActorRequest = IncomingMessage & { actor?: Actor };

/**
 * Gateway identity (403 GATEWAY_IDENTITY_REQUIRED) and role (403 PRICE_MANUAL_FORBIDDEN) checks. A guard runs
 * before the controller reads the body, so an unauthorized request is never parsed or validated.
 */
@Injectable()
export class ManualPriceGuard implements CanActivate {
  constructor(@Inject(GATEWAY_SECRET) private readonly gatewaySecret: string) {}

  canActivate(context: ExecutionContext): boolean {
    const req = context.switchToHttp().getRequest<ActorRequest>();
    const actor = authenticate(req.headers, this.gatewaySecret);
    requireAnyRole(actor, MANUAL_PRICE_ROLES);
    req.actor = actor;
    return true;
  }
}
