import { timingSafeEqual } from 'node:crypto';
import type { IncomingHttpHeaders } from 'node:http';
import type { Actor } from '../../application/port.js';
import { HttpError } from './errors.js';

/** Roles allowed to record a manual price: OPERATOR only (design change of 12/10, PR #158). ADMIN is not allowed. */
export const MANUAL_PRICE_ROLES = ['OPERATOR'] as const;

/**
 * Identity comes from the API Gateway, same contract as the Java services:
 * the gateway validates the Keycloak JWT, strips Authorization and injects
 * X-User-Id / X-User-Role (comma-separated realm roles) plus X-Gateway-Secret.
 * A request without the right gateway secret is rejected (403), like the Java
 * HeaderAuthenticationFilter.
 */
export function authenticate(headers: IncomingHttpHeaders, gatewaySecret: string): Actor {
  const secret = single(headers['x-gateway-secret']);
  if (!secret || !safeEqual(secret, gatewaySecret)) {
    throw new HttpError(403, 'GATEWAY_IDENTITY_REQUIRED', 'Request must come through the API gateway');
  }
  const userId = single(headers['x-user-id'])?.trim();
  const roleHeader = single(headers['x-user-role']);
  if (!userId || !roleHeader) {
    throw new HttpError(403, 'GATEWAY_IDENTITY_REQUIRED', 'Missing authenticated user identity');
  }
  const roles = roleHeader
    .split(',')
    .map((r) => r.trim().toUpperCase())
    .filter((r) => r.length > 0);
  return { userId, roles };
}

export function requireAnyRole(actor: Actor, allowed: readonly string[]): void {
  if (!actor.roles.some((r) => allowed.includes(r))) {
    throw new HttpError(403, 'PRICE_MANUAL_FORBIDDEN', `Requires one of roles: ${allowed.join(', ')}`);
  }
}

function single(value: string | string[] | undefined): string | undefined {
  return Array.isArray(value) ? value[0] : value;
}

function safeEqual(a: string, b: string): boolean {
  const ab = Buffer.from(a, 'utf8');
  const bb = Buffer.from(b, 'utf8');
  return ab.length === bb.length && timingSafeEqual(ab, bb);
}
