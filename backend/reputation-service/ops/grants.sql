-- Run as a provisioning administrator AFTER Flyway, against a fresh runtime
-- account named reputation_runtime. Create its password through secret tooling.
-- No global/schema grants or inherited roles may be attached to this account.
GRANT SELECT, INSERT ON reputation_db.completed_contract_fact TO 'reputation_runtime'@'%';
GRANT SELECT, INSERT ON reputation_db.dispute_outcome_fact TO 'reputation_runtime'@'%';
-- Issue #113 has no lock/override writers.
GRANT SELECT ON reputation_db.lock_entry TO 'reputation_runtime'@'%';
GRANT SELECT ON reputation_db.lock_override_event TO 'reputation_runtime'@'%';
