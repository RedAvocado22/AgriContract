import { PHASE1_KINDS } from './phase1-notification-kinds';
import { COMMAND_ROUTING_KEYS } from '../infrastructure/messaging/topology';
import { COMMAND_KINDS } from './notification-command-kinds';

describe('COMMAND_KINDS', () => {
  const kinds = Object.values(COMMAND_KINDS);

  // One command may send several templates (one per terminationType or
  // statusType), each its own kind, so this compares the set of commands.
  it('gives every consumed command a kind, and no kind to another event', () => {
    expect([...new Set(kinds.map((k) => k.eventType))].sort()).toEqual(
      [...COMMAND_ROUTING_KEYS].sort(),
    );
  });

  it('names a notification type no other kind uses', () => {
    const phase1 = Object.values(PHASE1_KINDS).map((k) => k.notificationType);
    const types = kinds.map((k) => k.notificationType);

    expect(new Set([...types, ...phase1]).size).toBe(
      types.length + phase1.length,
    );
    expect(types).not.toContain('LEGACY_P1');
  });

  it('names a template version no other kind uses, inside VARCHAR(50)', () => {
    const phase1 = Object.values(PHASE1_KINDS).map((k) => k.templateVersion);
    const versions = kinds.map((k) => k.templateVersion);

    expect(new Set([...versions, ...phase1]).size).toBe(
      versions.length + phase1.length,
    );
    expect(versions).not.toContain('p1-unknown');
    for (const version of versions) {
      expect(version.length).toBeLessThanOrEqual(50);
      // `/vN`, not the Phase 1 `/p1`: these texts are ours, not frozen Java.
      expect(version).toMatch(/^[a-z0-9-]+\/v\d+$/);
    }
  });

  it('keeps every stored value inside its column', () => {
    for (const kind of kinds) {
      expect(kind.eventType.length).toBeLessThanOrEqual(100);
      expect(kind.notificationType.length).toBeLessThanOrEqual(100);
    }
  });
});
