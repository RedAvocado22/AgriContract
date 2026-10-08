-- MySQL >= 8.0.16 (enforced CHECK constraints). Inspection-service database only.
-- Approved issue #112 supplement: assignment storage, not an assignment workflow.
CREATE TABLE level1_5_inspection_assignment (
    commission_id           CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    contract_id             CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    milestone_id            CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    inspector_id            CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    assigned_at             TIMESTAMP(6) NOT NULL,
    ended_at                TIMESTAMP(6) NULL,
    assigned_by_service     VARCHAR(64) NOT NULL,
    initiated_by_actor_id   CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NULL,
    active_slot             TINYINT GENERATED ALWAYS AS
                            (CASE WHEN ended_at IS NULL THEN 1 ELSE NULL END) STORED,
    PRIMARY KEY (commission_id),
    UNIQUE KEY uk_l15_active_milestone (contract_id, milestone_id, active_slot),
    CONSTRAINT chk_l15_assignment_time CHECK (ended_at IS NULL OR ended_at >= assigned_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;

-- No cross-service foreign keys. No relationship to level2_inspection_commission.
-- NULL active_slot preserves multiple historical assignments without granting access.
-- End/revoke/reassign mutations and inspector selection are deliberately not implemented.
