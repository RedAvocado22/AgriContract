-- Phase 1 escrow kept money amounts next to bank-service ledger_entry (dual-write) and had its own outbox.
-- Phase 2 escrow keeps deposit/milestone state only (milestone-escrow design §2.3) and uses the shared outbox.
DROP TABLE IF EXISTS escrow_transactions;
DROP TABLE IF EXISTS escrow_domain_events;
DROP TABLE IF EXISTS escrow_accounts;
