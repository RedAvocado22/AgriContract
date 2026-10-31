-- Audit of applied role changes (#215, user-service-phase2-design.md §4.2b). Append-only: the application only inserts,
-- in the same transaction as the role update. V8 because V7 belongs to #154 (event outbox).
CREATE TABLE role_change_audit
(
    id             BIGINT AUTO_INCREMENT PRIMARY KEY,
    actor_id       VARCHAR(255) NOT NULL,
    target_user_id VARCHAR(255) NOT NULL,
    old_role       VARCHAR(32)  NOT NULL,
    new_role       VARCHAR(32)  NOT NULL,
    changed_at     DATETIME(6)  NOT NULL
);

-- "Role history of user X" and "what did admin Y change".
CREATE INDEX idx_role_change_audit_target_changed_at ON role_change_audit (target_user_id, changed_at);
CREATE INDEX idx_role_change_audit_actor_changed_at ON role_change_audit (actor_id, changed_at);
