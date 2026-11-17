CREATE TABLE level1_5_assignment_waiting (
    contract_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    milestone_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    reason VARCHAR(20) NOT NULL,
    report_due_business_days INT NOT NULL,
    assigned_by_service VARCHAR(64) NOT NULL,
    initiated_by_actor_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NULL,
    waiting_since TIMESTAMP(6) NOT NULL,
    last_attempt_at TIMESTAMP(6) NOT NULL,
    last_reminder_on DATE NULL,
    PRIMARY KEY (contract_id, milestone_id),
    CONSTRAINT chk_l15_waiting_reason CHECK (reason IN ('NO_ELIGIBLE', 'ALL_EXCLUDED')),
    CONSTRAINT chk_l15_waiting_due CHECK (report_due_business_days BETWEEN 1 AND 30)
);
