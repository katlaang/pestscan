ALTER TABLE farms
    ADD COLUMN license_reference VARCHAR(64);

CREATE UNIQUE INDEX IF NOT EXISTS idx_farms_license_reference ON farms (license_reference);

ALTER TABLE greenhouses
    ADD COLUMN area_hectares DECIMAL(10, 2);

ALTER TABLE field_blocks
    ADD COLUMN area_hectares DECIMAL(10, 2);

CREATE TABLE farm_license_history
(
    id                               UUID PRIMARY KEY                  DEFAULT uuid_generate_v4(),
    version                          BIGINT                   NOT NULL DEFAULT 0,
    farm_id                          UUID                     NOT NULL REFERENCES farms (id) ON DELETE CASCADE,
    license_reference                VARCHAR(64)              NOT NULL,
    action                           VARCHAR(32)              NOT NULL,
    subscription_status              VARCHAR(20)              NOT NULL,
    subscription_tier                VARCHAR(50)              NOT NULL,
    billing_email                    VARCHAR(255),
    licensed_area_hectares           DECIMAL(10, 2)           NOT NULL,
    quota_discount_percentage        DECIMAL(5, 2),
    effective_licensed_area_hectares DECIMAL(10, 2)           NOT NULL,
    license_expiry_date              DATE,
    license_grace_period_end         DATE,
    license_archived_date            DATE,
    auto_renew_enabled               BOOLEAN,
    is_archived                      BOOLEAN,
    actor_user_id                    UUID,
    actor_email                      VARCHAR(255),
    notes                            VARCHAR(2000),
    created_at                       TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at                       TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted                          BOOLEAN                  NOT NULL DEFAULT FALSE,
    deleted_at                       TIMESTAMP WITH TIME ZONE,
    sync_status                      VARCHAR(32)              NOT NULL DEFAULT 'SYNCED',

    CONSTRAINT chk_farm_license_history_action CHECK (
        action IN ('GENERATED', 'UPDATED')
) ,
    CONSTRAINT chk_farm_license_history_status CHECK (
        subscription_status IN ('PENDING_ACTIVATION', 'ACTIVE', 'SUSPENDED', 'CANCELLED', 'DELETED')
    ),
    CONSTRAINT chk_farm_license_history_tier CHECK (
        subscription_tier IN ('BASIC', 'STANDARD', 'PREMIUM')
    )
);

CREATE INDEX idx_farm_license_history_farm ON farm_license_history (farm_id);
CREATE INDEX idx_farm_license_history_reference ON farm_license_history (license_reference);

CREATE TRIGGER trg_farm_license_history_updated
    BEFORE UPDATE
    ON farm_license_history
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();
