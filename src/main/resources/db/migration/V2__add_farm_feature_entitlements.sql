CREATE TABLE farm_feature_entitlements
(
    id          UUID PRIMARY KEY                  DEFAULT uuid_generate_v4(),
    version     BIGINT                   NOT NULL DEFAULT 0,
    farm_id     UUID                     NOT NULL REFERENCES farms (id) ON DELETE CASCADE,
    feature_key VARCHAR(64)              NOT NULL,
    enabled     BOOLEAN                  NOT NULL DEFAULT TRUE,
    created_at  TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted     BOOLEAN                  NOT NULL DEFAULT FALSE,
    deleted_at  TIMESTAMP WITH TIME ZONE,
    sync_status VARCHAR(32)              NOT NULL DEFAULT 'SYNCED',

    CONSTRAINT uk_farm_feature_entitlements_farm_feature UNIQUE (farm_id, feature_key),
    CONSTRAINT chk_farm_feature_entitlements_feature_key CHECK (
        feature_key IN (
                        'AI_PEST_IDENTIFICATION',
                        'DRONE_IMAGE_PROCESSING',
                        'PREDICTIVE_MODELING',
                        'AUTOMATED_PDF_REPORTS',
                        'GIS_HEATMAPS',
                        'AUTOMATED_TREATMENT_RECOMMENDATIONS',
                        'SUPPLY_ORDERING'
            )
        )
);

CREATE INDEX idx_farm_feature_entitlements_farm ON farm_feature_entitlements (farm_id);
CREATE INDEX idx_farm_feature_entitlements_feature ON farm_feature_entitlements (feature_key);

CREATE TRIGGER trg_farm_feature_entitlements_updated
    BEFORE UPDATE
    ON farm_feature_entitlements
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();
