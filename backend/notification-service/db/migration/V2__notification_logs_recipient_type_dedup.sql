-- Phase 2 (#115, design §3.1 and §6): dedup by (event_id, recipient_email, notification_type)
-- instead of (event_id, user_id), plus delivery metadata. Additive: no column is dropped or
-- renamed, and every Phase 1 row is kept.
--
-- user_id is kept as it is (Phase 1 stores the recipient email there) and recipient_user_id
-- is added, nullable, for Phase 2 recipients[].userId. Proposal of the Phase 2 plan §3, waiting
-- for the owner's decision; renaming user_id would be a separate V3.
--
-- Rollback: once V2 has run on an environment, the Java service no longer works there (it
-- inserts without recipient_email/notification_type, so a second recipient of the same event
-- collides on the new unique key). See notification-service-nestjs-migration.md §7.
--
-- MySQL DDL is not transactional: each statement below is atomic, the migration as a whole
-- is not. If a later statement fails, fix the cause, then `flyway repair` and undo the
-- statements that did run before migrating again.

-- 1. New columns. NOT NULL columns get a sentinel DEFAULT so the ALTER runs on a table with
--    data whatever the SQL mode. recipient_email is VARCHAR(320), not 255: the backfill below
--    writes 'legacy:' (7 characters) + user_id (up to 255), which must never be truncated.
ALTER TABLE notification_logs
    ADD COLUMN event_type          VARCHAR(100) NOT NULL DEFAULT 'LEGACY_P1' AFTER event_id,
    ADD COLUMN notification_type   VARCHAR(100) NOT NULL DEFAULT 'LEGACY_P1' AFTER event_type,
    ADD COLUMN recipient_user_id   VARCHAR(36)  NULL                         AFTER user_id,
    ADD COLUMN recipient_email     VARCHAR(320) NOT NULL DEFAULT ''          AFTER recipient_user_id,
    ADD COLUMN template_version    VARCHAR(50)  NOT NULL DEFAULT 'p1-unknown' AFTER body,
    ADD COLUMN provider_message_id VARCHAR(255) NULL                         AFTER retry_count,
    ADD COLUMN failure_reason      TEXT         NULL                         AFTER provider_message_id,
    ADD COLUMN sent_at             DATETIME(6)  NULL                         AFTER failure_reason;

-- 2. Backfill BEFORE the unique key changes. Phase 1 rows of one event differ only by user_id;
--    with recipient_email still '' they would all collide on the new key. The 'legacy:' prefix
--    keeps them apart from Phase 2 rows (a real address never starts with it), and
--    notification_type 'LEGACY_P1' is never written by Phase 2 code, so these rows stay a
--    read-only audit trail. The claim still reads them to skip an email already SENT before V2.
UPDATE notification_logs
   SET recipient_email = CONCAT('legacy:', user_id)
 WHERE recipient_email = '';

-- 3. Swap the unique key in one ALTER, so the table is never without one.
ALTER TABLE notification_logs
    DROP INDEX uq_event_user,
    ADD UNIQUE KEY uq_event_recipient_type (event_id, recipient_email, notification_type);
