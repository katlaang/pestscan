ALTER TABLE users
    ADD COLUMN IF NOT EXISTS authority_alert_curator BOOLEAN NOT NULL DEFAULT FALSE;

ALTER TABLE users
    DROP CONSTRAINT IF EXISTS chk_users_role;

ALTER TABLE users
    ADD CONSTRAINT chk_users_role
        CHECK (role IN ('SCOUT', 'MANAGER', 'FARM_ADMIN', 'SUPER_ADMIN', 'REGIONAL_ANALYST', 'EDGE_SYNC'));

CREATE INDEX IF NOT EXISTS idx_users_authority_alert_curator
    ON users (authority_alert_curator);

CREATE TABLE IF NOT EXISTS authority_alerts
(
    id UUID PRIMARY KEY DEFAULT public.uuid_generate_v4(),
    version BIGINT NOT NULL DEFAULT 0,
    alert_type VARCHAR(64) NOT NULL,
    severity VARCHAR(32) NOT NULL,
    issuing_authority VARCHAR(255) NOT NULL,
    title VARCHAR(255) NOT NULL,
    message_body VARCHAR(4000) NOT NULL,
    suggested_mitigation VARCHAR(2000) NOT NULL,
    country VARCHAR(100) NOT NULL,
    state VARCHAR(100),
    linked_species VARCHAR(64),
    source_url VARCHAR(1000),
    issued_date DATE NOT NULL,
    expiry_date DATE,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted BOOLEAN NOT NULL DEFAULT FALSE,
    deleted_at TIMESTAMP WITH TIME ZONE,
    sync_status VARCHAR(32) NOT NULL DEFAULT 'SYNCED',

    CONSTRAINT chk_authority_alert_type
        CHECK (alert_type IN ('NEW_DETECTION', 'ADVISORY', 'OUTBREAK', 'QUARANTINE', 'ERADICATION_COMPLETE', 'OTHER')),
    CONSTRAINT chk_authority_alert_severity
        CHECK (severity IN ('ADVISORY', 'WATCH', 'WARNING', 'EMERGENCY')),
    CONSTRAINT chk_authority_alert_linked_species
        CHECK (
            linked_species IS NULL OR linked_species IN (
                'THRIPS',
                'RED_SPIDER_MITE',
                'WHITEFLIES',
                'MEALYBUGS',
                'CATERPILLARS',
                'FALSE_CODLING_MOTH',
                'PEST_OTHER',
                'DOWNY_MILDEW',
                'POWDERY_MILDEW',
                'BOTRYTIS',
                'VERTICILLIUM',
                'BACTERIAL_WILT',
                'DISEASE_OTHER',
                'BENEFICIAL_PP',
                'BENEFICIAL_OTHER'
            )
        ),
    CONSTRAINT chk_authority_alert_dates
        CHECK (expiry_date IS NULL OR expiry_date >= issued_date)
);

CREATE INDEX IF NOT EXISTS idx_authority_alerts_country
    ON authority_alerts (country);

CREATE INDEX IF NOT EXISTS idx_authority_alerts_country_state
    ON authority_alerts (country, state);

CREATE INDEX IF NOT EXISTS idx_authority_alerts_active_severity
    ON authority_alerts (active, severity);

DROP TRIGGER IF EXISTS trg_authority_alerts_updated ON authority_alerts;

CREATE TRIGGER trg_authority_alerts_updated
    BEFORE UPDATE
    ON authority_alerts
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();
