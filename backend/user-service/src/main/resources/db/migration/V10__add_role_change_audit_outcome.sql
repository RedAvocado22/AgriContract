-- #261: role_change_audit also records refused OPERATOR <-> ARBITRATOR assignments, with the actor who tried.
-- Additive: rows written before V10 were all applied changes. For a REJECTED_* row new_role is the requested role
-- and changed_at is when the attempt was refused; the profile's role did not change.
ALTER TABLE role_change_audit
    ADD COLUMN outcome VARCHAR(32) NOT NULL DEFAULT 'APPLIED';
