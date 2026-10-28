-- Phase 1 cancel/confirm-delivery/dispute are removed (milestone-escrow design §3.1, §6). Rows in those states
-- cannot be loaded by the Phase 2 status enum; the platform has no real contract data yet, so they are deleted.
DELETE FROM contracts WHERE status IN ('DELIVERED', 'CANCELLED', 'DISPUTED');

ALTER TABLE contracts
    DROP COLUMN cancel_reason,
    DROP COLUMN cancelled_by;
