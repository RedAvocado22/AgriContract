import type {
  NotifyCategoryApprovedUseCase,
  NotifyCategoryRejectedUseCase,
  NotifyContractCancelledUseCase,
  NotifyContractDeliveredUseCase,
  NotifyContractDisputedUseCase,
  NotifyContractSignedUseCase,
  NotifyEscrowLockedUseCase,
  NotifyEscrowPenalizedUseCase,
  NotifyEscrowReleasedUseCase,
} from '../../application/use-cases';
import {
  parseCategoryApproved,
  parseCategoryRejected,
  parseContractCancelled,
  parseContractDelivered,
  parseContractDisputed,
  parseContractSigned,
  parseEscrowLocked,
  parseEscrowPenalized,
  parseEscrowReleased,
} from './legacy-payload.parsers';
import type { RoutingKey } from './topology';

/** Injection token of the routing-key -> handler table built by legacyRoutes. */
export const LEGACY_ROUTES = Symbol('LEGACY_ROUTES');

export type LegacyRoutes = Record<RoutingKey, (body: Buffer) => Promise<void>>;

export interface NotificationUseCases {
  contractSigned: NotifyContractSignedUseCase;
  contractDelivered: NotifyContractDeliveredUseCase;
  contractCancelled: NotifyContractCancelledUseCase;
  contractDisputed: NotifyContractDisputedUseCase;
  escrowLocked: NotifyEscrowLockedUseCase;
  escrowReleased: NotifyEscrowReleasedUseCase;
  escrowPenalized: NotifyEscrowPenalizedUseCase;
  categoryApproved: NotifyCategoryApprovedUseCase;
  categoryRejected: NotifyCategoryRejectedUseCase;
}

/**
 * Flat Phase 1 payload -> parser -> use case, per queue. A Record over
 * RoutingKey: the compiler rejects a queue of TOPOLOGY without a route.
 */
export function legacyRoutes(u: NotificationUseCases): LegacyRoutes {
  return {
    'contract.signed': (b) => u.contractSigned.execute(parseContractSigned(b)),
    'contract.delivered': (b) =>
      u.contractDelivered.execute(parseContractDelivered(b)),
    'contract.cancelled': (b) =>
      u.contractCancelled.execute(parseContractCancelled(b)),
    'contract.disputed': (b) =>
      u.contractDisputed.execute(parseContractDisputed(b)),
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
