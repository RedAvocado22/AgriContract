import { legacyRoutingKeys } from '../infrastructure/messaging/topology';
import { PHASE1_KINDS } from './phase1-notification-kinds';

describe('PHASE1_KINDS', () => {
  const kinds = Object.values(PHASE1_KINDS);

  it('gives each Phase 1 routing key exactly one kind', () => {
    const routingKeys = legacyRoutingKeys();

    expect(kinds.map((k) => k.eventType).sort()).toEqual(
      [...routingKeys].sort(),
    );
  });

  it('uses a distinct notification type per event, never the pre-V2 LEGACY_P1', () => {
    const types = kinds.map((k) => k.notificationType);

    expect(new Set(types).size).toBe(types.length);
    expect(types).not.toContain('LEGACY_P1');
  });

  it('names an explicit template version per kind, never the pre-V2 p1-unknown default', () => {
    const versions = kinds.map((k) => k.templateVersion);

    expect(new Set(versions).size).toBe(versions.length);
    expect(versions).not.toContain('p1-unknown');
    // template_version is VARCHAR(50) (migration V2).
    expect(versions.every((v) => v.length > 0 && v.length <= 50)).toBe(true);
  });

  // CONTRACT_SIGNED, _DELIVERED, _CANCELLED and _DISPUTED went with their
  // queues (#398); rows already stored keep those values.
  it('keeps the stored types: renaming one breaks dedup of events in flight at deploy', () => {
    expect(Object.values(PHASE1_KINDS).map((k) => k.notificationType)).toEqual([
      'ESCROW_LOCKED',
      'ESCROW_RELEASED',
      'ESCROW_PENALIZED',
      'CATEGORY_APPROVED',
      'CATEGORY_REJECTED',
    ]);
  });
});
