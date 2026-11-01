-- Reputation lock consumer (#260, user-service-phase2-design.md §2.2, §6). Additive only.
-- V8 is taken by role_change_audit (#317); merge #315 (V7) and #317 (V8) first.

-- Ordering for reputation.locked/unlocked: an event applies only when its lockRevision is greater than this,
-- in the same UPDATE that writes locked_until. Persisted, so an older event arriving after a restart is still ignored.
ALTER TABLE user_profiles
    ADD COLUMN last_lock_revision BIGINT NOT NULL DEFAULT 0;

-- Consumer dedupe from backend/shared/java-eventing/src/main/resources/db/eventing/V1__create_eventing_tables.sql.
-- Replaces the user_event_idempotency table sketched in design §6: one dedupe schema across services.
CREATE TABLE processed_event (
    consumer_name VARCHAR(100) NOT NULL,
    event_id      CHAR(36)     NOT NULL,
    event_type    VARCHAR(100) NOT NULL,
    processed_at  DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (consumer_name, event_id)
);
