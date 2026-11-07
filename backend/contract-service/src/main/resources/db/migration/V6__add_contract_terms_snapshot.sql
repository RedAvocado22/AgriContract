-- #92: Phase 2 coffee terms. terms_snapshot holds the RFC 8785 canonical JSON exactly as hashed (LONGTEXT, not the
-- MySQL JSON type, which re-serialises the text and would break the hash). signed_content_hash = SHA-256 hex of it.
-- Additive: Phase 1 rows keep both NULL; a Phase 2 row always has both.
ALTER TABLE contracts
    ADD COLUMN terms_snapshot      LONGTEXT CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NULL,
    ADD COLUMN signed_content_hash CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NULL,
    ADD CONSTRAINT chk_contracts_terms_hash_pair
        CHECK ((terms_snapshot IS NULL AND signed_content_hash IS NULL)
            OR (terms_snapshot IS NOT NULL AND signed_content_hash REGEXP '^[a-f0-9]{64}$'));
