-- Run as a provisioning administrator AFTER Flyway, against a fresh runtime
-- account named bank_runtime. Create its password through secret tooling.
-- No global/schema grants or inherited roles may be attached to this account.
-- The ledger is append-only: no UPDATE or DELETE, ever.
GRANT SELECT, INSERT ON bank_db.ledger_entry TO 'bank_runtime'@'%';
