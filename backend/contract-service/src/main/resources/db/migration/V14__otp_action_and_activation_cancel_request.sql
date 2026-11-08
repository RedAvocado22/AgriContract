-- D42 (#234): the parties' activation cancel requests.
-- 1. An OTP confirms one consented action: signing, or a cancel request. Rows before V14 are signing codes.
ALTER TABLE signature_otp
    ADD COLUMN action_type VARCHAR(32) NOT NULL DEFAULT 'SIGN_CONTRACT' AFTER consent_id,
    ADD CONSTRAINT chk_signature_otp_action CHECK (action_type IN ('SIGN_CONTRACT', 'CANCEL_ACTIVATION'));

-- 2. One row per party request. A request stands while withdrawn_at is NULL; a withdrawal is recorded, never
-- deleted, and a later request is a new row. At most one standing request per party (generated column + unique).
CREATE TABLE activation_cancel_request (
    request_id      CHAR(36)     CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    contract_id     VARCHAR(255) NOT NULL,
    party_role      VARCHAR(8)   NOT NULL,
    user_id         VARCHAR(255) NOT NULL,
    otp_id          CHAR(36)     CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    requested_at    TIMESTAMP(6) NOT NULL,
    withdrawn_at    TIMESTAMP(6) NULL,
    standing_role   VARCHAR(8)   GENERATED ALWAYS AS (IF(withdrawn_at IS NULL, party_role, NULL)) STORED,
    PRIMARY KEY (request_id),
    CONSTRAINT uq_activation_cancel_standing UNIQUE (contract_id, standing_role),
    CONSTRAINT uq_activation_cancel_otp UNIQUE (otp_id),
    CONSTRAINT fk_activation_cancel_contract FOREIGN KEY (contract_id) REFERENCES contracts (contract_id),
    CONSTRAINT fk_activation_cancel_otp FOREIGN KEY (otp_id) REFERENCES signature_otp (otp_id),
    CONSTRAINT chk_activation_cancel_role CHECK (party_role IN ('BUYER', 'SELLER')),
    CONSTRAINT chk_activation_cancel_withdrawn CHECK (withdrawn_at IS NULL OR withdrawn_at >= requested_at)
);
