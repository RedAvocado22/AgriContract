import type {
  NotifyCategoryApprovedUseCase,
  NotifyCategoryRejectedUseCase,
} from '../../application/use-cases';
import {
  parseCategoryApproved,
  parseCategoryRejected,
} from './legacy-payload.parsers';
import type { LegacyRoutingKey } from './topology';

/** Injection token of the routing-key -> handler table built by legacyRoutes. */
export const LEGACY_ROUTES = Symbol('LEGACY_ROUTES');

export type LegacyRoutes = Record<
  LegacyRoutingKey,
  (body: Buffer) => Promise<void>
>;

export interface NotificationUseCases {
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
    'category.approved': (b) =>
      u.categoryApproved.execute(parseCategoryApproved(b)),
    'category.rejected': (b) =>
      u.categoryRejected.execute(parseCategoryRejected(b)),
  };
}
