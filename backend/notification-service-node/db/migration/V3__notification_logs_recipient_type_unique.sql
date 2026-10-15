-- Dedup key becomes (event_id, recipient_email, notification_type) (design §3.1, §6).
-- After V3 the Java service must not run again: it writes recipient_email = '' and
-- notification_type = 'LEGACY_P1', so a second mail for the same event_id would hit this key.

-- Same backfill as V2, repeated for rows Java wrote if it was rolled back to between V2 and V3.
-- No-op when nothing has recipient_email = ''.
UPDATE notification_logs
   SET recipient_email = CASE WHEN CHAR_LENGTH(user_id) <= 248
                              THEN CONCAT('legacy:', user_id)
                              ELSE user_id END
 WHERE recipient_email = '';

ALTER TABLE notification_logs DROP INDEX uq_event_user;
CREATE UNIQUE INDEX uq_event_recipient_type
  ON notification_logs(event_id, recipient_email, notification_type);
