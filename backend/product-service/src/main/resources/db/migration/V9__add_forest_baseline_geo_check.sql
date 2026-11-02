-- Forest-baseline geo-risk check (design §2.2.4, issue #264). Additive only: plot_registry_entry is unchanged.
--
-- The 2020 forest baseline (Natural Forests 2020 / ForTy 2020 extract) is held locally, per dataset version, and
-- loaded by an operator; nothing here seeds it. Each area is already classified by the dataset, so the check needs no
-- threshold of its own: NATURAL_FOREST overlap -> HIGH_RISK, AMBIGUOUS overlap (intercropping, class boundary)
-- -> INCONCLUSIVE, no overlap inside covered ground -> LOW_RISK.
-- Coverage is explicit: a plot outside every coverage extent of the active dataset gets no result and stays PENDING
-- until the timeout makes it UNVERIFIED. An empty or partial load therefore never reads as "no forest".
CREATE TABLE forest_baseline_coverage
(
    dataset_version VARCHAR(100) NOT NULL,
    coverage_id     VARCHAR(100) NOT NULL,
    extent          GEOMETRY     NOT NULL SRID 4326,

    PRIMARY KEY (dataset_version, coverage_id),
    SPATIAL INDEX sidx_forest_baseline_coverage_extent (extent)
);

CREATE TABLE forest_baseline_area
(
    dataset_version  VARCHAR(100) NOT NULL,
    baseline_area_id VARCHAR(100) NOT NULL,
    forest_class     VARCHAR(20)  NOT NULL,
    geom             GEOMETRY     NOT NULL SRID 4326,

    PRIMARY KEY (dataset_version, baseline_area_id),
    SPATIAL INDEX sidx_forest_baseline_area_geom (geom),
    CONSTRAINT chk_forest_baseline_area_class CHECK (forest_class IN ('NATURAL_FOREST', 'AMBIGUOUS'))
);

-- Append-only record of every check result applied to a plot: which geometry was checked, against which dataset
-- version, and which baseline areas it overlapped. The plot row only keeps the latest level; this keeps why.
CREATE TABLE plot_geo_risk_check
(
    check_id                  BIGINT       NOT NULL AUTO_INCREMENT,
    registry_entry_id         VARCHAR(255) NOT NULL,
    geometry_hash             CHAR(64)     NOT NULL,
    dataset_version           VARCHAR(100) NOT NULL,
    geo_risk_level            VARCHAR(20)  NOT NULL,
    matched_baseline_area_ids JSON         NOT NULL,
    checked_at                DATETIME(6)  NOT NULL,

    PRIMARY KEY (check_id),
    INDEX idx_plot_geo_risk_check_entry (registry_entry_id, checked_at),
    CONSTRAINT fk_plot_geo_risk_check_entry
        FOREIGN KEY (registry_entry_id) REFERENCES plot_registry_entry (registry_entry_id),
    CONSTRAINT chk_plot_geo_risk_check_level CHECK (geo_risk_level IN ('LOW_RISK', 'HIGH_RISK', 'INCONCLUSIVE'))
);
