-- Plot registry (design §2.1/§3): one row per member-household plot, owned by the seller and reused by listings.
-- geo_json holds the geometry itself (GEOMETRY, SRID 4326): MySQL rejects malformed or unclosed rings on insert;
-- self-intersection is checked in the application with JTS because ST_IsValid() does not support SRID 4326.
-- geometry_hash = SHA-256 of the normalized WKT, the plot identity carried into contract goods snapshots.
-- Province cross-check, overlap/yield signals, cadastral extracts and the KML file reference come with later issues.
CREATE TABLE plot_registry_entry
(
    registry_entry_id       VARCHAR(255)  NOT NULL,
    seller_id               VARCHAR(255)  NOT NULL,
    -- accent- and case-sensitive: the default _ai_ci collation would match "Hồ" with "Hổ" and merge two households
    household_label         VARCHAR(255)  COLLATE utf8mb4_0900_as_cs NOT NULL,
    geometry_type           VARCHAR(20)   NOT NULL,
    geo_json                GEOMETRY      NOT NULL SRID 4326,
    geometry_hash           CHAR(64)      NOT NULL,
    area_hectares           DECIMAL(10, 4) NOT NULL,
    source                  VARCHAR(20)   NOT NULL,
    accuracy_meters         DECIMAL(6, 2) NULL,
    verification_level      VARCHAR(20)   NOT NULL DEFAULT 'SELF_DECLARED',
    declared_province       VARCHAR(50)   NOT NULL,
    geo_verification_status VARCHAR(20)   NOT NULL DEFAULT 'PENDING',
    geo_risk_level          VARCHAR(20)   NULL,
    imported_at             DATETIME(6)   NOT NULL,

    PRIMARY KEY (registry_entry_id),
    -- re-import matches on this pair and updates the row in place
    UNIQUE KEY uk_plot_registry_seller_household (seller_id, household_label),
    SPATIAL INDEX sidx_plot_registry_geo (geo_json),
    -- geo check timeout job: PENDING rows imported before a cutoff
    INDEX idx_plot_registry_status_imported (geo_verification_status, imported_at),
    CONSTRAINT chk_plot_registry_geometry_type CHECK (geometry_type IN ('POINT', 'POLYGON')),
    CONSTRAINT chk_plot_registry_source CHECK (source IN ('KML_IMPORT', 'MANUAL_PIN')),
    CONSTRAINT chk_plot_registry_manual_pin_point CHECK (source <> 'MANUAL_PIN' OR geometry_type = 'POINT'),
    CONSTRAINT chk_plot_registry_area CHECK (area_hectares > 0 AND (geometry_type = 'POLYGON' OR area_hectares <= 4)),
    CONSTRAINT chk_plot_registry_verification_level CHECK (verification_level IN ('SELF_DECLARED', 'CADASTRAL_BACKED')),
    CONSTRAINT chk_plot_registry_geo_status CHECK (geo_verification_status IN ('PENDING', 'CHECKED', 'UNVERIFIED')),
    CONSTRAINT chk_plot_registry_geo_risk
        CHECK ((geo_verification_status = 'CHECKED') = (geo_risk_level IS NOT NULL))
);

-- Copy of each registry plot a listing names, taken at listing creation (design §2.4, decided per listing rather
-- than per product so each listing carries exactly its own plots). Never updated: re-importing the registry entry
-- does not change a listing that already exists.
CREATE TABLE listing_plot
(
    listing_id               VARCHAR(255)   NOT NULL,
    source_registry_entry_id VARCHAR(255)   NOT NULL,
    household_label          VARCHAR(255)   NOT NULL,
    geometry_type            VARCHAR(20)    NOT NULL,
    geo_json                 GEOMETRY       NOT NULL SRID 4326,
    geometry_hash            CHAR(64)       NOT NULL,
    area_hectares            DECIMAL(10, 4) NOT NULL,
    verification_level       VARCHAR(20)    NOT NULL,
    declared_at              DATETIME(6)    NOT NULL,

    PRIMARY KEY (listing_id, source_registry_entry_id),
    INDEX idx_listing_plot_registry_entry (source_registry_entry_id),
    CONSTRAINT fk_listing_plot_listing FOREIGN KEY (listing_id) REFERENCES listings (listing_id),
    CONSTRAINT fk_listing_plot_registry_entry
        FOREIGN KEY (source_registry_entry_id) REFERENCES plot_registry_entry (registry_entry_id)
);
