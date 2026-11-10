import type {
  NotifyCategoryApprovedUseCase,
  NotifyCategoryRejectedUseCase,
  NotifyEscrowLockedUseCase,
  NotifyEscrowPenalizedUseCase,
  NotifyEscrowReleasedUseCase,
} from '../../application/use-cases';
import {
  parseCategoryApproved,
  parseCategoryRejected,
  parseEscrowLocked,
  parseEscrowPenalized,
  parseEscrowReleased,
} from './legacy-payload.parsers';
import type { LegacyRoutingKey } from './topology';

/** Injection token of the routing-key -> handler table built by legacyRoutes. */
export const LEGACY_ROUTES = Symbol('LEGACY_ROUTES');

export type LegacyRoutes = Record<
  LegacyRoutingKey,
  (body: Buffer) => Promise<void>
>;

export interface NotificationUseCases {
  escrowLocked: NotifyEscrowLockedUseCase;
  escrowReleased: NotifyEscrowReleasedUseCase;
  escrowPenalized: NotifyEscrowPenalizedUseCase;
  categoryApproved: NotifyCategoryApprovedUseCase;
  categoryRejected: NotifyCategoryRejectedUseCase;
}

/**
 * Flat Phase 1 payload -> parser -> use case, per queue. A Record over
 * LegacyRoutingKey: the compiler rejects a Phase 1 queue of TOPOLOGY without a
 * route; its Phase 2 commands are routed by commandRoutes instead.
 */
export function legacyRoutes(u: NotificationUseCases): LegacyRoutes {
  return {
    'escrow.locked': (b) => u.escrowLocked.execute(parseEscrowLocked(b)),
    'escrow.released': (b) => u.escrowReleased.execute(parseEscrowReleased(b)),
    'escrow.penalized': (b) =>
      u.escrowPenalized.execute(parseEscrowPenalized(b)),
    'category.approved': (b) =>
      u.categoryApproved.execute(parseCategoryApproved(b)),
    'category.rejected': (b) =>
      u.categoryRejected.execute(parseCategoryRejected(b)),
  };
}
