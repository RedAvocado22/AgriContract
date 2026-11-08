-- #97: one signature per contract party (signature design §3, §7). A row is written only when the OTP of the
-- challenge otp_id was verified; consent_id is the checkbox consent of that initiate. The contract is SIGNED when
-- both BUYER and SELLER rows exist with the same signed_content_hash. ip_address stays NULL until the gateway
-- forwards a client IP it has checked. Append-only: the triggers refuse UPDATE and DELETE, as for consent_evidence.
CREATE TABLE signature (
    signature_id        CHAR(36)     CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    contract_id         VARCHAR(255) NOT NULL,
    signer_role         VARCHAR(16)  NOT NULL,
    signer_user_id      VARCHAR(255) NOT NULL,
    otp_id              CHAR(36)     CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    consent_id          CHAR(36)     CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    auth_time           TIMESTAMP(6) NOT NULL,
    signed_at           TIMESTAMP(6) NOT NULL,
    signed_content_hash CHAR(64)     CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    ip_address          VARCHAR(45)  NULL,
    PRIMARY KEY (signature_id),
    CONSTRAINT uq_signature_contract_role UNIQUE (contract_id, signer_role),
    CONSTRAINT uq_signature_otp UNIQUE (otp_id),
    CONSTRAINT fk_signature_contract FOREIGN KEY (contract_id) REFERENCES contracts (contract_id),
    CONSTRAINT fk_signature_otp FOREIGN KEY (otp_id) REFERENCES signature_otp (otp_id),
    CONSTRAINT fk_signature_consent FOREIGN KEY (consent_id) REFERENCES consent_evidence (consent_id),
    CONSTRAINT chk_signature_role CHECK (signer_role IN ('BUYER', 'SELLER')),
    CONSTRAINT chk_signature_hash CHECK (signed_content_hash REGEXP '^[a-f0-9]{64}$')
);

CREATE TRIGGER trg_signature_no_update BEFORE UPDATE ON signature
    FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'signature is append-only';

CREATE TRIGGER trg_signature_no_delete BEFORE DELETE ON signature
    FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'signature is append-only';
