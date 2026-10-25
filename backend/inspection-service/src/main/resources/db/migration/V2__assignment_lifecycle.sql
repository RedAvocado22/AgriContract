-- Preserve V1 rows: no invented deadline for historical/manual scaffold fixtures.
ALTER TABLE level1_5_inspection_assignment
    ADD COLUMN report_due_business_days INT NULL,
    ADD COLUMN report_due_at TIMESTAMP(6) NULL,
    ADD CONSTRAINT chk_l15_deadline CHECK (
        (report_due_business_days IS NULL AND report_due_at IS NULL) OR
        (report_due_business_days IS NOT NULL AND report_due_at IS NOT NULL
         AND report_due_business_days BETWEEN 1 AND 30 AND report_due_at >= assigned_at)),
    ADD INDEX idx_l15_timeout (active_slot, report_due_at),
    ADD INDEX idx_l15_previous_inspectors (contract_id, milestone_id, inspector_id);

-- Stable local lock even when no assignment exists. No cross-service foreign keys.
CREATE TABLE level1_5_assignment_lock (
    contract_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    milestone_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    PRIMARY KEY (contract_id, milestone_id)
) ENGINE=InnoDB;
