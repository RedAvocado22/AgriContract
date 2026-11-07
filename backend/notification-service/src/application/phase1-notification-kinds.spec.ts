import { TOPOLOGY } from '../infrastructure/messaging/topology';
import { PHASE1_KINDS } from './phase1-notification-kinds';

describe('PHASE1_KINDS', () => {
  const kinds = Object.values(PHASE1_KINDS);

  it('gives each Phase 1 routing key exactly one kind', () => {
    const routingKeys = TOPOLOGY.flatMap((row) => [...row.routingKeys]);

    expect(kinds.map((k) => k.eventType).sort()).toEqual(
      [...routingKeys].sort(),
    );
  });

  it('uses a distinct notification type per event, never the pre-V2 LEGACY_P1', () => {
    const types = kinds.map((k) => k.notificationType);

    expect(new Set(types).size).toBe(types.length);
    expect(types).not.toContain('LEGACY_P1');
  });

  it('keeps the stored types: renaming one breaks dedup of events in flight at deploy', () => {
    expect(Object.values(PHASE1_KINDS).map((k) => k.notificationType)).toEqual([
      'CONTRACT_SIGNED',
      'CONTRACT_DELIVERED',
      'CONTRACT_CANCELLED',
      'CONTRACT_DISPUTED',
      'ESCROW_LOCKED',
      'ESCROW_RELEASED',
      'ESCROW_PENALIZED',
      'CATEGORY_APPROVED',
      'CATEGORY_REJECTED',
    ]);
  });
});
