-- #94: checkbox consent per action (contract-document-consent design §4). Append-only: the triggers below refuse
-- UPDATE and DELETE, so evidence cannot be rewritten even by a bug in the service. A consent alone signs nothing;
-- the Signature row (#97) will reference consent_id. Consents without a signature are normal (abandoned/resent).
CREATE TABLE consent_evidence (
    consent_id             CHAR(36)     CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    action_type            VARCHAR(32)  NOT NULL,
    subject_type           VARCHAR(32)  NOT NULL,
    subject_id             VARCHAR(255) NOT NULL,
    actor_user_id          VARCHAR(255) NOT NULL,
    actor_role             VARCHAR(16)  NOT NULL,
    statement_code         VARCHAR(64)  NOT NULL,
    statement_version      VARCHAR(16)  NOT NULL,
    statement_hash         CHAR(64)     CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    presented_content_hash CHAR(64)     CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    consented_at           TIMESTAMP(6) NOT NULL,
    correlation_id         VARCHAR(64)  NULL,
    PRIMARY KEY (consent_id),
    KEY idx_consent_subject_actor (subject_type, subject_id, actor_user_id),
    CONSTRAINT chk_consent_statement_hash CHECK (statement_hash REGEXP '^[a-f0-9]{64}$'),
    CONSTRAINT chk_consent_presented_hash CHECK (presented_content_hash REGEXP '^[a-f0-9]{64}$')
);

CREATE TRIGGER trg_consent_evidence_no_update BEFORE UPDATE ON consent_evidence
    FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'consent_evidence is append-only';

CREATE TRIGGER trg_consent_evidence_no_delete BEFORE DELETE ON consent_evidence
    FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'consent_evidence is append-only';
