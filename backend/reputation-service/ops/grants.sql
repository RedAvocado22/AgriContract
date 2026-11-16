-- Run as a provisioning administrator AFTER Flyway, against a fresh runtime
-- account named reputation_runtime. Create its password through secret tooling.
-- No global/schema grants or inherited roles may be attached to this account.
GRANT SELECT, INSERT ON reputation_db.completed_contract_fact TO 'reputation_runtime'@'%';
GRANT SELECT, INSERT ON reputation_db.dispute_outcome_fact TO 'reputation_runtime'@'%';
-- Issue #113 has no lock/override writers.
GRANT SELECT ON reputation_db.lock_entry TO 'reputation_runtime'@'%';
GRANT SELECT ON reputation_db.lock_override_event TO 'reputation_runtime'@'%';
-- #268: projection and shared outbox are mutable; transition receipts are append-only.
GRANT SELECT, INSERT, UPDATE ON reputation_db.user_lock_state TO 'reputation_runtime'@'%';
GRANT SELECT, INSERT ON reputation_db.lock_event_receipt TO 'reputation_runtime'@'%';
GRANT SELECT, INSERT, UPDATE ON reputation_db.event_outbox TO 'reputation_runtime'@'%';
