-- PR-owned V4 follows the merged assignment lifecycle and eventing migrations V1/V2/V3.
ALTER TABLE level1_5_inspection_assignment
    ADD UNIQUE KEY uk_l15_report_binding (commission_id,contract_id,milestone_id,inspector_id),
    ADD INDEX idx_l15_pending_inspector (inspector_id,ended_at,assigned_at);

-- Full two-tier storage shape; Level 2 workflows remain outside this migration.
CREATE TABLE inspection_report (
    report_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL PRIMARY KEY,
    l15_commission_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NULL,
    contract_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NULL,
    milestone_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NULL,
    inspector_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NULL,
    tier VARCHAR(10) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'CONFIRMED',
    content JSON NOT NULL,
    normalized_result JSON NULL,
    report_file_hash VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    result_hash VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NULL,
    report_hash VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NULL,
    external_org VARCHAR(255) NULL,
    confirmed_by_operator_id CHAR(36) NULL,
    received_via VARCHAR(50) NULL,
    ingestion_source VARCHAR(20) NULL,
    spf_dkim_result VARCHAR(20) NULL,
    -- Issue #266 adds fk_report_l2_commission when level2_inspection_commission exists.
    commission_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NULL,
    external_verification_status VARCHAR(20) NULL,
    external_verification_checked_at TIMESTAMP(6) NULL,
    confirmed_at TIMESTAMP(6) NULL,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    UNIQUE KEY uk_report_l15_commission (l15_commission_id),
    CONSTRAINT fk_report_l15_assignment FOREIGN KEY
        (l15_commission_id,contract_id,milestone_id,inspector_id)
        REFERENCES level1_5_inspection_assignment (commission_id,contract_id,milestone_id,inspector_id),
    CONSTRAINT chk_report_tier CHECK (tier IN ('LEVEL_1_5', 'LEVEL_2')),
    CONSTRAINT chk_report_l15_required CHECK (
        tier <> 'LEVEL_1_5' OR (
            contract_id IS NOT NULL AND milestone_id IS NOT NULL
            AND inspector_id IS NOT NULL AND l15_commission_id IS NOT NULL
            AND status = 'CONFIRMED' AND confirmed_at IS NOT NULL
            AND normalized_result IS NOT NULL AND result_hash IS NOT NULL AND report_hash IS NOT NULL
            AND external_org IS NULL AND commission_id IS NULL)),
    CONSTRAINT chk_l15_report_times CHECK (tier <> 'LEVEL_1_5' OR confirmed_at <= created_at),
    CONSTRAINT chk_l15_report_hashes CHECK (
        REGEXP_LIKE(report_file_hash,'^[a-f0-9]{64}$','c') AND
        (result_hash IS NULL OR REGEXP_LIKE(result_hash,'^[a-f0-9]{64}$','c')) AND
        (report_hash IS NULL OR REGEXP_LIKE(report_hash,'^[a-f0-9]{64}$','c'))),
    INDEX idx_l15_report_pending (l15_commission_id,status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;

-- Immutability is enforced by the insert/read-only repository and final domain identity.
-- No trigger requiring elevated privileges or global binary-log configuration.
