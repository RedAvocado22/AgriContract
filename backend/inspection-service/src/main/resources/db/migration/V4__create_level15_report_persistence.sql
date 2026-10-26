-- main currently has V1; unmerged #160 reserves V2/V3. V4 is unused in both histories.
-- Independently applicable to V1, without copying the assignment lifecycle migrations.
ALTER TABLE level1_5_inspection_assignment
    ADD UNIQUE KEY uk_l15_report_binding (commission_id,contract_id,milestone_id,inspector_id),
    ADD INDEX idx_l15_pending_inspector (inspector_id,ended_at,assigned_at);

-- Only the design's Level 1.5 CONFIRMED snapshot is supported here. No invented draft state.
CREATE TABLE inspection_report (
    report_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL PRIMARY KEY,
    l15_commission_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    contract_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    milestone_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    inspector_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    tier VARCHAR(10) NOT NULL,
    status VARCHAR(20) NOT NULL,
    content JSON NOT NULL,
    normalized_result JSON NOT NULL,
    report_file_hash VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    result_hash VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    report_hash VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    confirmed_at TIMESTAMP(6) NOT NULL,
    created_at TIMESTAMP(6) NOT NULL,
    CONSTRAINT fk_l15_report_assignment FOREIGN KEY
        (l15_commission_id,contract_id,milestone_id,inspector_id)
        REFERENCES level1_5_inspection_assignment (commission_id,contract_id,milestone_id,inspector_id),
    CONSTRAINT chk_l15_report_state CHECK (tier='LEVEL_1_5' AND status='CONFIRMED'),
    CONSTRAINT chk_l15_report_times CHECK (confirmed_at <= created_at),
    CONSTRAINT chk_l15_report_hashes CHECK (
        REGEXP_LIKE(report_file_hash,'^[a-f0-9]{64}$','c') AND
        REGEXP_LIKE(result_hash,'^[a-f0-9]{64}$','c') AND
        REGEXP_LIKE(report_hash,'^[a-f0-9]{64}$','c')),
    INDEX idx_l15_report_pending (l15_commission_id,status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;

-- Immutability is enforced by the insert/read-only repository and final domain identity.
-- No trigger requiring elevated privileges or global binary-log configuration.
