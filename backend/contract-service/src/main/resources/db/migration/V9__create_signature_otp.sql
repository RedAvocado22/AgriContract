-- #95: OTP challenges (signature design §7). Only HMAC-SHA256(code, pepper) is stored. A challenge is usable while
-- verified_at IS NULL, expires_at > now and attempt_count < max; resend and failed email kill it by setting
-- expires_at = now (no status column). consent_id ties the challenge to the checkbox consent of the same initiate.
CREATE TABLE signature_otp (
    otp_id              CHAR(36)     CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    consent_id          CHAR(36)     CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    contract_id         VARCHAR(255) NOT NULL,
    signer_role         VARCHAR(16)  NOT NULL,
    user_id             VARCHAR(255) NOT NULL,
    auth_time           TIMESTAMP(6) NOT NULL,
    signed_content_hash CHAR(64)     CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    otp_hash            CHAR(64)     CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    sent_at             TIMESTAMP(6) NOT NULL,
    expires_at          TIMESTAMP(6) NOT NULL,
    verified_at         TIMESTAMP(6) NULL,
    attempt_count       INT          NOT NULL DEFAULT 0,
    PRIMARY KEY (otp_id),
    KEY idx_signature_otp_signer (contract_id, user_id, signer_role, signed_content_hash),
    CONSTRAINT fk_signature_otp_consent FOREIGN KEY (consent_id) REFERENCES consent_evidence (consent_id),
    CONSTRAINT fk_signature_otp_contract FOREIGN KEY (contract_id) REFERENCES contracts (contract_id),
    CONSTRAINT chk_signature_otp_role CHECK (signer_role IN ('BUYER', 'SELLER')),
    CONSTRAINT chk_signature_otp_hashes CHECK (signed_content_hash REGEXP '^[a-f0-9]{64}$'
        AND otp_hash REGEXP '^[a-f0-9]{64}$'),
    CONSTRAINT chk_signature_otp_attempts CHECK (attempt_count >= 0)
);
