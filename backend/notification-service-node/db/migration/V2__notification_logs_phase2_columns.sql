-- Phase 2 metadata (notification-service-phase2-design.md §6).
-- Only adds columns with defaults, so the Phase 1 Java service keeps working after V2.
ALTER TABLE notification_logs
  ADD COLUMN event_type VARCHAR(100) NOT NULL DEFAULT 'LEGACY_P1',
  ADD COLUMN notification_type VARCHAR(100) NOT NULL DEFAULT 'LEGACY_P1',
  ADD COLUMN recipient_email VARCHAR(255) NOT NULL DEFAULT '',
  ADD COLUMN template_version VARCHAR(50) NOT NULL DEFAULT 'p1-unknown',
  ADD COLUMN provider_message_id VARCHAR(255) NULL,
  ADD COLUMN failure_reason TEXT NULL,
  ADD COLUMN sent_at DATETIME(6) NULL;

-- Must run before V3 replaces the unique key: Phase 1 rows sharing an event_id differ only
-- by user_id, so leaving recipient_email = '' would make them collide on the new key.
-- user_id is VARCHAR(255); a value longer than 248 chars does not fit behind the prefix, so it
-- is copied as is (still unique per event_id, since the old key was (event_id, user_id)).
UPDATE notification_logs
   SET recipient_email = CASE WHEN CHAR_LENGTH(user_id) <= 248
                              THEN CONCAT('legacy:', user_id)
                              ELSE user_id END
 WHERE recipient_email = '';
