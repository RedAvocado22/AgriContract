-- Reputation lock projection (user-service-phase2-design.md §2.2, §6). Additive only.
-- Read by GET /internal/v1/users/{userId}; reputation-service stays the source of truth.
-- last_lock_revision and user_event_idempotency ship with the reputation.locked/unlocked consumer.
ALTER TABLE user_profiles
    ADD COLUMN locked_until DATETIME(6) NULL;
