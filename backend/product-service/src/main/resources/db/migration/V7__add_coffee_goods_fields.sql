-- Coffee goods fields a contract snapshots (#111, design §8b/§8c/§9). All nullable: rows created before Phase 2
-- stay readable; new rows get them from the create use cases.

-- Set on approval. COFFEE is the only Phase 2 value; NULL = approved before this column existed, which cannot
-- back a new product or listing.
ALTER TABLE categories
    ADD COLUMN commodity VARCHAR(20) NULL AFTER status,
    ADD CONSTRAINT chk_categories_commodity CHECK (commodity IS NULL OR commodity = 'COFFEE');

-- Seller-declared season data (§8c); certifications is a JSON array of free-text commitments.
ALTER TABLE products
    ADD COLUMN crop_year      INT          NULL AFTER images,
    ADD COLUMN harvest_season VARCHAR(100) NULL AFTER crop_year,
    ADD COLUMN certifications JSON         NULL AFTER harvest_season;

-- category_id, commodity, crop_year, harvest_season and certifications are copied from the product and its
-- category at listing creation, like product_name, so the listing shows what a contract will snapshot.
-- The declared quality spec is all four columns or none (none = legacyQualitySpecMissing).
ALTER TABLE listings
    ADD COLUMN category_id                      VARCHAR(255)  NULL AFTER product_id,
    ADD COLUMN commodity                        VARCHAR(20)   NULL AFTER category_id,
    ADD COLUMN crop_year                        INT           NULL AFTER commodity,
    ADD COLUMN harvest_season                   VARCHAR(100)  NULL AFTER crop_year,
    ADD COLUMN certifications                   JSON          NULL AFTER harvest_season,
    ADD COLUMN region_text                      VARCHAR(255)  NULL AFTER certifications,
    ADD COLUMN quality_type                     VARCHAR(20)   NULL AFTER region_text,
    ADD COLUMN quality_moisture_percent         DECIMAL(5, 2) NULL AFTER quality_type,
    ADD COLUMN quality_foreign_matter_percent   DECIMAL(5, 2) NULL AFTER quality_moisture_percent,
    ADD COLUMN quality_black_broken_percent     DECIMAL(5, 2) NULL AFTER quality_foreign_matter_percent,
    ADD COLUMN packaging_declaration            VARCHAR(500)  NULL AFTER quality_black_broken_percent,
    ADD COLUMN warehouse_location               VARCHAR(500)  NULL AFTER packaging_declaration,
    ADD COLUMN estimated_availability_date      DATE          NULL AFTER warehouse_location,
    ADD COLUMN traceability_evidence_package_id VARCHAR(36)   NULL AFTER estimated_availability_date,
    ADD CONSTRAINT chk_listings_commodity CHECK (commodity IS NULL OR commodity = 'COFFEE'),
    ADD CONSTRAINT chk_listings_quality_type CHECK (quality_type IS NULL OR quality_type IN ('ARABICA', 'ROBUSTA')),
    -- IS NOT NULL spelled out: a CHECK that evaluates to UNKNOWN (NULL BETWEEN 0 AND 100) counts as satisfied
    ADD CONSTRAINT chk_listings_quality_all_or_none CHECK (
        (quality_type IS NULL AND quality_moisture_percent IS NULL AND quality_foreign_matter_percent IS NULL
            AND quality_black_broken_percent IS NULL)
        OR (quality_type IS NOT NULL
            AND quality_moisture_percent IS NOT NULL AND quality_moisture_percent BETWEEN 0 AND 100
            AND quality_foreign_matter_percent IS NOT NULL AND quality_foreign_matter_percent BETWEEN 0 AND 100
            AND quality_black_broken_percent IS NOT NULL AND quality_black_broken_percent BETWEEN 0 AND 100));
