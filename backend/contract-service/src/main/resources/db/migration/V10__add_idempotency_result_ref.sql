-- #96: a replayed command that created something must answer with the same thing (InitiateSign returns the same
-- otpId). result_ref holds the id of what the first run created; NULL for commands that only change state (#93).
ALTER TABLE idempotency_record
    ADD COLUMN result_ref VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NULL;
