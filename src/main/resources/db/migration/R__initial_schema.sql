-- ============================================================
--  MOFO PEST SCOUTING - MASTER SCHEMA
-- ============================================================

CREATE
EXTENSION IF NOT EXISTS "uuid-ossp";

-- ============================================================
--  TRIGGER SUPPORT
-- ============================================================

CREATE
OR REPLACE FUNCTION update_updated_at_column()
RETURNS TRIGGER
LANGUAGE plpgsql AS $$
BEGIN
    NEW.updated_at
= CURRENT_TIMESTAMP;
RETURN NEW;
END;
$$;

-- ============================================================
--  USERS
-- ============================================================

CREATE TABLE users
(
    id              UUID PRIMARY KEY                  DEFAULT uuid_generate_v4(),
    email           VARCHAR(255)             NOT NULL,
    password        VARCHAR(255)             NOT NULL,
    first_name      VARCHAR(255),
    last_name       VARCHAR(255),
    phone_number    VARCHAR(50)              NOT NULL,
    country         VARCHAR(100),
    customer_number VARCHAR(100)             NOT NULL,
    role            VARCHAR(50)              NOT NULL,
    is_enabled      BOOLEAN                  NOT NULL DEFAULT TRUE,
    last_login      TIMESTAMP WITH TIME ZONE,
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version         BIGINT                   NOT NULL DEFAULT 0,
    deleted         BOOLEAN                  NOT NULL DEFAULT FALSE,
    deleted_at      TIMESTAMP WITH TIME ZONE,
    sync_status     VARCHAR(32)              NOT NULL DEFAULT 'SYNCED',

    CONSTRAINT uk_users_email UNIQUE (email),
    CONSTRAINT uk_users_customer_number UNIQUE (customer_number),
    CONSTRAINT chk_users_role
        CHECK (role IN ('SCOUT', 'MANAGER', 'FARM_ADMIN', 'SUPER_ADMIN', 'EDGE_SYNC'))
);

CREATE INDEX idx_users_role ON users (role);

CREATE TRIGGER trg_users_updated
    BEFORE UPDATE
    ON users
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

-- ============================================================
--  PASSWORD RESET TOKENS
-- ============================================================

CREATE TABLE password_reset_tokens
(
    id                      UUID PRIMARY KEY                  DEFAULT uuid_generate_v4(),
    version                 BIGINT                   NOT NULL DEFAULT 0,
    user_id                 UUID                     NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    performed_by_user_id    UUID                     REFERENCES users (id) ON DELETE SET NULL,
    token                   VARCHAR(255)             NOT NULL,
    expires_at              TIMESTAMP WITH TIME ZONE NOT NULL,
    used_at                 TIMESTAMP WITH TIME ZONE,
    verification_channel    VARCHAR(50)              NOT NULL,
    caller_name             VARCHAR(255),
    callback_number         VARCHAR(50),
    verification_notes      VARCHAR(2048),
    first_name_confirmation VARCHAR(255),
    last_name_confirmation  VARCHAR(255),
    email_confirmation      VARCHAR(255),
    last_login_verified_on  DATE,
    customer_number_confirmation VARCHAR(255),
    created_at              TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at              TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted                 BOOLEAN                  NOT NULL DEFAULT FALSE,
    deleted_at              TIMESTAMP WITH TIME ZONE,
    sync_status             VARCHAR(32)              NOT NULL DEFAULT 'SYNCED',

    CONSTRAINT uk_password_reset_tokens_token UNIQUE (token),
    CONSTRAINT chk_prt_verification_channel
        CHECK (verification_channel IN ('EMAIL', 'PHONE_CALL'))
);

CREATE INDEX idx_prt_user ON password_reset_tokens (user_id);
CREATE INDEX idx_prt_performed_by ON password_reset_tokens (performed_by_user_id);

CREATE TRIGGER trg_prt_updated
    BEFORE UPDATE
    ON password_reset_tokens
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

-- ============================================================
--  FARMS
-- ============================================================

CREATE TABLE farms
(
    id                                  UUID PRIMARY KEY                  DEFAULT uuid_generate_v4(),
    version                             BIGINT                   NOT NULL DEFAULT 0,
    farm_tag                            VARCHAR(32)              NOT NULL,
    name                                VARCHAR(255)             NOT NULL,
    description                         VARCHAR(500),
    external_id                         VARCHAR(36)              NOT NULL,
    address                             VARCHAR(255),
    latitude                            DECIMAL(10, 7),
    longitude                           DECIMAL(10, 7),
    city                                VARCHAR(100),
    province                            VARCHAR(100),
    postal_code                         VARCHAR(20),
    country                             VARCHAR(100)                      DEFAULT 'Canada',
    owner_id                            UUID                     NOT NULL REFERENCES users (id) ON DELETE RESTRICT,
    scout_id                            UUID                     REFERENCES users (id) ON DELETE SET NULL,
    contact_name                        VARCHAR(255),
    contact_email                       VARCHAR(255),
    contact_phone                       VARCHAR(50),
    subscription_status                 VARCHAR(20)              NOT NULL,
    subscription_tier                   VARCHAR(50)              NOT NULL,
    billing_email                       VARCHAR(255),
    license_reference                   VARCHAR(64),
    license_type                        VARCHAR(16)              NOT NULL DEFAULT 'PAID',
    license_start_date                  DATE,
    license_extension_months            INTEGER                  NOT NULL DEFAULT 0,
    licensed_area_hectares              DECIMAL(10, 2)           NOT NULL,
    licensed_unit_quota                 INTEGER,
    quota_discount_percentage           DECIMAL(5, 2),
    license_expiry_date                 DATE,
    license_grace_period_end            DATE,
    license_archived_date               DATE,
    is_archived                         BOOLEAN                  NOT NULL DEFAULT FALSE,
    auto_renew_enabled                  BOOLEAN                  NOT NULL DEFAULT FALSE,
    license_expiry_notification_sent_at TIMESTAMP WITH TIME ZONE,
    structure_type                      VARCHAR(20)              NOT NULL,
    timezone                            VARCHAR(100),
    default_bay_count                   INTEGER,
    default_benches_per_bay             INTEGER,
    default_spot_checks_per_bench       INTEGER,
    stripe_customer_id                  VARCHAR(255),
    stripe_subscription_id              VARCHAR(255),
    created_at                          TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at                          TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted                             BOOLEAN                  NOT NULL DEFAULT FALSE,
    deleted_at                          TIMESTAMP WITH TIME ZONE,
    sync_status                         VARCHAR(32)              NOT NULL DEFAULT 'SYNCED',

    CONSTRAINT uk_farms_farm_tag UNIQUE (farm_tag),
    CONSTRAINT uk_farms_external_id UNIQUE (external_id),
    CONSTRAINT uk_farms_license_reference UNIQUE (license_reference),
    CONSTRAINT chk_farms_subscription_status
        CHECK (subscription_status IN ('PENDING_ACTIVATION', 'ACTIVE', 'SUSPENDED', 'CANCELLED', 'DELETED')),
    CONSTRAINT chk_farms_subscription_tier
        CHECK (subscription_tier IN ('BASIC', 'STANDARD', 'PREMIUM')),
    CONSTRAINT chk_farms_license_type
        CHECK (license_type IN ('TRIAL', 'PAID')),
    CONSTRAINT chk_farms_structure_type
        CHECK (structure_type IN ('GREENHOUSE', 'FIELD', 'OTHER')),
    CONSTRAINT chk_farms_license_extension_months
        CHECK (license_extension_months >= 0),
    CONSTRAINT chk_farms_licensed_area
        CHECK (licensed_area_hectares >= 0),
    CONSTRAINT chk_farms_licensed_unit_quota
        CHECK (licensed_unit_quota IS NULL OR licensed_unit_quota >= 0),
    CONSTRAINT chk_farms_quota_discount_percentage
        CHECK (
            quota_discount_percentage IS NULL
                OR (quota_discount_percentage >= 0 AND quota_discount_percentage <= 100)
            ),
    CONSTRAINT chk_farms_license_dates
        CHECK (
            license_grace_period_end IS NULL
                OR license_expiry_date IS NULL
                OR license_grace_period_end >= license_expiry_date
            ),
    CONSTRAINT chk_farms_archive_dates
        CHECK (
            license_archived_date IS NULL
                OR license_grace_period_end IS NULL
                OR license_archived_date >= license_grace_period_end
            )
);

CREATE INDEX idx_farms_name ON farms (name);
CREATE INDEX idx_farms_subscription_status ON farms (subscription_status);
CREATE INDEX idx_farms_owner ON farms (owner_id);
CREATE INDEX idx_farms_scout ON farms (scout_id);

CREATE TRIGGER trg_farms_updated
    BEFORE UPDATE
    ON farms
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

-- ============================================================
--  USER-FARM MEMBERSHIPS
-- ============================================================

CREATE TABLE user_farm_memberships
(
    id          UUID PRIMARY KEY                  DEFAULT uuid_generate_v4(),
    version     BIGINT                   NOT NULL DEFAULT 0,
    user_id     UUID                     NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    farm_id     UUID                     NOT NULL REFERENCES farms (id) ON DELETE CASCADE,
    role        VARCHAR(50)              NOT NULL,
    is_active   BOOLEAN                  NOT NULL DEFAULT TRUE,
    created_at  TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted     BOOLEAN                  NOT NULL DEFAULT FALSE,
    deleted_at  TIMESTAMP WITH TIME ZONE,
    sync_status VARCHAR(32)              NOT NULL DEFAULT 'SYNCED',

    CONSTRAINT uk_user_farm UNIQUE (user_id, farm_id),
    CONSTRAINT chk_user_farm_membership_role
        CHECK (role IN ('SCOUT', 'MANAGER', 'FARM_ADMIN', 'SUPER_ADMIN', 'EDGE_SYNC'))
);

CREATE INDEX idx_ufm_farm ON user_farm_memberships (farm_id);

CREATE TRIGGER trg_ufm_updated
    BEFORE UPDATE
    ON user_farm_memberships
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

-- ============================================================
--  GREENHOUSES
-- ============================================================

CREATE TABLE greenhouses
(
    id                    UUID PRIMARY KEY                  DEFAULT uuid_generate_v4(),
    version               BIGINT                   NOT NULL DEFAULT 0,
    farm_id               UUID                     NOT NULL REFERENCES farms (id) ON DELETE CASCADE,
    name                  VARCHAR(255)             NOT NULL,
    description           VARCHAR(1000),
    bay_count             INTEGER,
    benches_per_bay       INTEGER,
    spot_checks_per_bench INTEGER,
    area_hectares         DECIMAL(10, 2),
    is_active             BOOLEAN                  NOT NULL DEFAULT TRUE,
    created_at            TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at            TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted               BOOLEAN                  NOT NULL DEFAULT FALSE,
    deleted_at            TIMESTAMP WITH TIME ZONE,
    sync_status           VARCHAR(32)              NOT NULL DEFAULT 'SYNCED',

    CONSTRAINT chk_greenhouses_bay_count
        CHECK (bay_count IS NULL OR bay_count >= 0),
    CONSTRAINT chk_greenhouses_benches_per_bay
        CHECK (benches_per_bay IS NULL OR benches_per_bay >= 0),
    CONSTRAINT chk_greenhouses_spot_checks_per_bench
        CHECK (spot_checks_per_bench IS NULL OR spot_checks_per_bench >= 0),
    CONSTRAINT chk_greenhouses_area_hectares
        CHECK (area_hectares IS NULL OR area_hectares >= 0)
);

CREATE INDEX idx_greenhouses_farm ON greenhouses (farm_id);
CREATE INDEX idx_greenhouses_name ON greenhouses (name);

CREATE TRIGGER trg_greenhouses_updated
    BEFORE UPDATE
    ON greenhouses
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TABLE greenhouse_bay_tags
(
    greenhouse_id UUID         NOT NULL REFERENCES greenhouses (id) ON DELETE CASCADE,
    bay_tag       VARCHAR(255) NOT NULL,
    CONSTRAINT pk_greenhouse_bay_tags PRIMARY KEY (greenhouse_id, bay_tag)
);

CREATE TABLE greenhouse_bench_tags
(
    greenhouse_id UUID         NOT NULL REFERENCES greenhouses (id) ON DELETE CASCADE,
    bench_tag     VARCHAR(255) NOT NULL,
    CONSTRAINT pk_greenhouse_bench_tags PRIMARY KEY (greenhouse_id, bench_tag)
);

-- ============================================================
--  FIELD BLOCKS
-- ============================================================

CREATE TABLE field_blocks
(
    id                  UUID PRIMARY KEY                  DEFAULT uuid_generate_v4(),
    version             BIGINT                   NOT NULL DEFAULT 0,
    farm_id             UUID                     NOT NULL REFERENCES farms (id) ON DELETE CASCADE,
    name                VARCHAR(255)             NOT NULL,
    description         VARCHAR(500),
    bay_count           INTEGER,
    spot_checks_per_bay INTEGER,
    area_hectares       DECIMAL(10, 2),
    is_active           BOOLEAN                  NOT NULL DEFAULT TRUE,
    created_at          TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted             BOOLEAN                  NOT NULL DEFAULT FALSE,
    deleted_at          TIMESTAMP WITH TIME ZONE,
    sync_status         VARCHAR(32)              NOT NULL DEFAULT 'SYNCED',

    CONSTRAINT chk_field_blocks_bay_count
        CHECK (bay_count IS NULL OR bay_count >= 0),
    CONSTRAINT chk_field_blocks_spot_checks_per_bay
        CHECK (spot_checks_per_bay IS NULL OR spot_checks_per_bay >= 0),
    CONSTRAINT chk_field_blocks_area_hectares
        CHECK (area_hectares IS NULL OR area_hectares >= 0)
);

CREATE INDEX idx_field_blocks_farm ON field_blocks (farm_id);
CREATE INDEX idx_field_blocks_name ON field_blocks (name);

CREATE TRIGGER trg_field_blocks_updated
    BEFORE UPDATE
    ON field_blocks
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TABLE field_block_bay_tags
(
    field_block_id UUID         NOT NULL REFERENCES field_blocks (id) ON DELETE CASCADE,
    bay_tag        VARCHAR(255) NOT NULL,
    CONSTRAINT pk_field_block_bay_tags PRIMARY KEY (field_block_id, bay_tag)
);

-- ============================================================
--  FARM FEATURE ENTITLEMENTS
-- ============================================================

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
    CONSTRAINT chk_farm_feature_entitlements_feature_key
        CHECK (
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

CREATE INDEX idx_farm_feature_entitlements_feature ON farm_feature_entitlements (feature_key);

CREATE TRIGGER trg_farm_feature_entitlements_updated
    BEFORE UPDATE
    ON farm_feature_entitlements
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

-- ============================================================
--  LICENSE PROPERTIES
--  Super-admin managed pricing inputs for license generation.
-- ============================================================

CREATE TABLE license_properties
(
    id                       UUID PRIMARY KEY                  DEFAULT uuid_generate_v4(),
    version                  BIGINT                   NOT NULL DEFAULT 0,
    property_name            VARCHAR(100)             NOT NULL,
    description              VARCHAR(500),
    base_license_per_hectare DECIMAL(12, 2)           NOT NULL,
    license_number_length    INTEGER                  NOT NULL DEFAULT 12,
    currency_code            VARCHAR(8)               NOT NULL DEFAULT 'USD',
    is_active                BOOLEAN                  NOT NULL DEFAULT TRUE,
    effective_from           DATE                     NOT NULL DEFAULT CURRENT_DATE,
    effective_to             DATE,
    created_by_user_id       UUID                     REFERENCES users (id) ON DELETE SET NULL,
    last_updated_by_user_id  UUID                     REFERENCES users (id) ON DELETE SET NULL,
    created_at               TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at               TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted                  BOOLEAN                  NOT NULL DEFAULT FALSE,
    deleted_at               TIMESTAMP WITH TIME ZONE,
    sync_status              VARCHAR(32)              NOT NULL DEFAULT 'SYNCED',

    CONSTRAINT uk_license_properties_name UNIQUE (property_name),
    CONSTRAINT chk_license_properties_base_per_hectare
        CHECK (base_license_per_hectare > 0),
    CONSTRAINT chk_license_properties_number_length
        CHECK (license_number_length = 12),
    CONSTRAINT chk_license_properties_effective_dates
        CHECK (effective_to IS NULL OR effective_to >= effective_from)
);

CREATE INDEX idx_license_properties_active ON license_properties (is_active);
CREATE INDEX idx_license_properties_effective_from ON license_properties (effective_from);

CREATE TRIGGER trg_license_properties_updated
    BEFORE UPDATE
    ON license_properties
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

-- ============================================================
--  LICENSES
--  Independently queryable license records generated for farms.
-- ============================================================

CREATE TABLE licenses
(
    id                               UUID PRIMARY KEY                  DEFAULT uuid_generate_v4(),
    version                          BIGINT                   NOT NULL DEFAULT 0,
    farm_id                          UUID                     NOT NULL REFERENCES farms (id) ON DELETE CASCADE,
    license_property_id              UUID                     NOT NULL REFERENCES license_properties (id) ON DELETE RESTRICT,
    license_number                   CHAR(12)                 NOT NULL,
    license_reference                VARCHAR(64),
    is_generated                     BOOLEAN                  NOT NULL DEFAULT FALSE,
    is_current                       BOOLEAN                  NOT NULL DEFAULT TRUE,
    generated_at                     TIMESTAMP WITH TIME ZONE,
    generated_by_user_id             UUID                     REFERENCES users (id) ON DELETE SET NULL,
    licensed_area_hectares           DECIMAL(10, 2)           NOT NULL,
    effective_licensed_area_hectares DECIMAL(10, 2)           NOT NULL,
    base_license_per_hectare         DECIMAL(12, 2)           NOT NULL,
    computed_license_value           DECIMAL(18, 2)           NOT NULL,
    currency_code                    VARCHAR(8)               NOT NULL DEFAULT 'USD',
    license_type                     VARCHAR(16),
    subscription_status              VARCHAR(20),
    subscription_tier                VARCHAR(50),
    issued_on                        DATE                     NOT NULL DEFAULT CURRENT_DATE,
    start_date                       DATE,
    expiry_date                      DATE,
    grace_period_end                 DATE,
    archived_date                    DATE,
    notes                            VARCHAR(2000),
    created_at                       TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at                       TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted                          BOOLEAN                  NOT NULL DEFAULT FALSE,
    deleted_at                       TIMESTAMP WITH TIME ZONE,
    sync_status                      VARCHAR(32)              NOT NULL DEFAULT 'SYNCED',

    CONSTRAINT uk_licenses_license_number UNIQUE (license_number),
    CONSTRAINT uk_licenses_license_reference UNIQUE (license_reference),
    CONSTRAINT chk_licenses_license_number_format
        CHECK (license_number ~ '^[0-9]{12}$'
) ,
    CONSTRAINT chk_licenses_generated_state
        CHECK (
            (is_generated = FALSE AND generated_at IS NULL AND generated_by_user_id IS NULL)
            OR (is_generated = TRUE AND generated_at IS NOT NULL AND generated_by_user_id IS NOT NULL)
        ),
    CONSTRAINT chk_licenses_licensed_area
        CHECK (licensed_area_hectares >= 0),
    CONSTRAINT chk_licenses_effective_licensed_area
        CHECK (effective_licensed_area_hectares >= 0),
    CONSTRAINT chk_licenses_base_per_hectare
        CHECK (base_license_per_hectare > 0),
    CONSTRAINT chk_licenses_computed_value
        CHECK (computed_license_value >= 0),
    CONSTRAINT chk_licenses_license_type
        CHECK (license_type IS NULL OR license_type IN ('TRIAL', 'PAID')),
    CONSTRAINT chk_licenses_subscription_status
        CHECK (
            subscription_status IS NULL
            OR subscription_status IN ('PENDING_ACTIVATION', 'ACTIVE', 'SUSPENDED', 'CANCELLED', 'DELETED')
        ),
    CONSTRAINT chk_licenses_subscription_tier
        CHECK (
            subscription_tier IS NULL
            OR subscription_tier IN ('BASIC', 'STANDARD', 'PREMIUM')
        ),
    CONSTRAINT chk_licenses_date_order
        CHECK (
            (start_date IS NULL OR expiry_date IS NULL OR expiry_date >= start_date)
            AND (grace_period_end IS NULL OR expiry_date IS NULL OR grace_period_end >= expiry_date)
            AND (archived_date IS NULL OR grace_period_end IS NULL OR archived_date >= grace_period_end)
        )
);

CREATE INDEX idx_licenses_farm ON licenses (farm_id);
CREATE INDEX idx_licenses_property ON licenses (license_property_id);
CREATE INDEX idx_licenses_generated_by ON licenses (generated_by_user_id);
CREATE INDEX idx_licenses_generated_at ON licenses (generated_at);

CREATE UNIQUE INDEX idx_licenses_current_farm
    ON licenses (farm_id) WHERE is_current = TRUE AND deleted = FALSE;

CREATE TRIGGER trg_licenses_updated
    BEFORE UPDATE
    ON licenses
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

ALTER TABLE farms
    ADD COLUMN current_license_id UUID REFERENCES licenses (id) ON DELETE SET NULL;

CREATE INDEX idx_farms_current_license ON farms (current_license_id);

-- ============================================================
--  FARM LICENSE HISTORY
-- ============================================================

CREATE TABLE farm_license_history
(
    id                               UUID PRIMARY KEY                  DEFAULT uuid_generate_v4(),
    version                          BIGINT                   NOT NULL DEFAULT 0,
    farm_id                          UUID                     NOT NULL REFERENCES farms (id) ON DELETE CASCADE,
    license_id                       UUID                     REFERENCES licenses (id) ON DELETE SET NULL,
    license_property_id              UUID                     REFERENCES license_properties (id) ON DELETE SET NULL,
    license_number                   CHAR(12),
    license_reference                VARCHAR(64)              NOT NULL,
    action                           VARCHAR(32)              NOT NULL,
    license_type                     VARCHAR(16),
    license_start_date               DATE,
    license_extension_months         INTEGER,
    subscription_status              VARCHAR(20)              NOT NULL,
    subscription_tier                VARCHAR(50)              NOT NULL,
    billing_email                    VARCHAR(255),
    licensed_area_hectares           DECIMAL(10, 2)           NOT NULL,
    quota_discount_percentage        DECIMAL(5, 2),
    effective_licensed_area_hectares DECIMAL(10, 2)           NOT NULL,
    base_license_per_hectare         DECIMAL(12, 2),
    computed_license_value           DECIMAL(18, 2),
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

    CONSTRAINT chk_farm_license_history_action
        CHECK (action IN ('GENERATED', 'UPDATED', 'EXPIRY_NOTICE_QUEUED')
) ,
    CONSTRAINT chk_farm_license_history_license_number
        CHECK (license_number IS NULL OR license_number ~ '^[0-9]{12}$'),
    CONSTRAINT chk_farm_license_history_license_type
        CHECK (license_type IS NULL OR license_type IN ('TRIAL', 'PAID')),
    CONSTRAINT chk_farm_license_history_license_extension_months
        CHECK (license_extension_months IS NULL OR license_extension_months >= 0),
    CONSTRAINT chk_farm_license_history_status
        CHECK (subscription_status IN ('PENDING_ACTIVATION', 'ACTIVE', 'SUSPENDED', 'CANCELLED', 'DELETED')),
    CONSTRAINT chk_farm_license_history_tier
        CHECK (subscription_tier IN ('BASIC', 'STANDARD', 'PREMIUM')),
    CONSTRAINT chk_farm_license_history_licensed_area
        CHECK (licensed_area_hectares >= 0),
    CONSTRAINT chk_farm_license_history_effective_area
        CHECK (effective_licensed_area_hectares >= 0),
    CONSTRAINT chk_farm_license_history_base_per_hectare
        CHECK (base_license_per_hectare IS NULL OR base_license_per_hectare > 0),
    CONSTRAINT chk_farm_license_history_computed_value
        CHECK (computed_license_value IS NULL OR computed_license_value >= 0),
    CONSTRAINT chk_farm_license_history_quota_discount
        CHECK (
            quota_discount_percentage IS NULL
            OR (quota_discount_percentage >= 0 AND quota_discount_percentage <= 100)
        ),
    CONSTRAINT chk_farm_license_history_license_dates
        CHECK (
            license_grace_period_end IS NULL
            OR license_expiry_date IS NULL
            OR license_grace_period_end >= license_expiry_date
        ),
    CONSTRAINT chk_farm_license_history_archive_dates
        CHECK (
            license_archived_date IS NULL
            OR license_grace_period_end IS NULL
            OR license_archived_date >= license_grace_period_end
        )
);

CREATE INDEX idx_farm_license_history_reference ON farm_license_history (license_reference);
CREATE INDEX idx_farm_license_history_farm_created_at ON farm_license_history (farm_id, created_at DESC);
CREATE INDEX idx_farm_license_history_license ON farm_license_history (license_id);
CREATE INDEX idx_farm_license_history_license_number ON farm_license_history (license_number);

CREATE TRIGGER trg_farm_license_history_updated
    BEFORE UPDATE
    ON farm_license_history
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

-- ============================================================
--  SCOUTING SESSIONS
-- ============================================================

CREATE TABLE scouting_sessions
(
    id                        UUID PRIMARY KEY                  DEFAULT uuid_generate_v4(),
    version                   BIGINT                   NOT NULL DEFAULT 0,
    farm_id                   UUID                     NOT NULL REFERENCES farms (id) ON DELETE CASCADE,
    greenhouse_id             UUID                     REFERENCES greenhouses (id) ON DELETE SET NULL,
    field_block_id            UUID                     REFERENCES field_blocks (id) ON DELETE SET NULL,
    manager_id                UUID                     REFERENCES users (id) ON DELETE SET NULL,
    scout_id                  UUID                     REFERENCES users (id) ON DELETE SET NULL,
    session_date              DATE                     NOT NULL,
    week_number               INTEGER,
    crop_type                 VARCHAR(255),
    crop_variety              VARCHAR(255),
    weather                   VARCHAR(255),
    notes                     VARCHAR(2000),
    temperature_celsius       DECIMAL(10, 2),
    relative_humidity_percent DECIMAL(10, 2),
    observation_time          TIME,
    weather_notes             VARCHAR(2000),
    status                    VARCHAR(20)              NOT NULL DEFAULT 'DRAFT',
    started_at                TIMESTAMP WITH TIME ZONE,
    submitted_at              TIMESTAMP WITH TIME ZONE,
    completed_at              TIMESTAMP WITH TIME ZONE,
    reopen_comment            VARCHAR(2000),
    confirmation_acknowledged BOOLEAN                  NOT NULL DEFAULT FALSE,
    created_at                TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at                TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted                   BOOLEAN                  NOT NULL DEFAULT FALSE,
    deleted_at                TIMESTAMP WITH TIME ZONE,
    sync_status               VARCHAR(32)              NOT NULL DEFAULT 'SYNCED',

    CONSTRAINT chk_scouting_sessions_status
        CHECK (status IN
               ('DRAFT', 'NEW', 'IN_PROGRESS', 'SUBMITTED', 'REOPENED', 'COMPLETED', 'INCOMPLETE', 'CANCELLED')),
    CONSTRAINT chk_scouting_sessions_single_structure
        CHECK (greenhouse_id IS NULL OR field_block_id IS NULL)
);

CREATE INDEX idx_sessions_farm_date ON scouting_sessions (farm_id, session_date);
CREATE INDEX idx_sessions_farm_status ON scouting_sessions (farm_id, status);
CREATE INDEX idx_sessions_farm_updated_at ON scouting_sessions (farm_id, updated_at);
CREATE INDEX idx_sessions_farm_scout_status ON scouting_sessions (farm_id, scout_id, status);

CREATE TRIGGER trg_sessions_updated
    BEFORE UPDATE
    ON scouting_sessions
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

-- ============================================================
--  SESSION AUDIT EVENTS
-- ============================================================

CREATE TABLE session_audit_events
(
    id          UUID PRIMARY KEY                  DEFAULT uuid_generate_v4(),
    version     BIGINT                   NOT NULL DEFAULT 0,
    session_id  UUID                     NOT NULL REFERENCES scouting_sessions (id) ON DELETE CASCADE,
    farm_id     UUID                     NOT NULL REFERENCES farms (id) ON DELETE CASCADE,
    action      VARCHAR(64)              NOT NULL,
    actor_name  VARCHAR(255),
    actor_id    UUID,
    actor_email VARCHAR(255),
    actor_role  VARCHAR(50),
    device_id   VARCHAR(255),
    device_type VARCHAR(255),
    location    VARCHAR(512),
    comment     VARCHAR(2000),
    occurred_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_at  TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted     BOOLEAN                  NOT NULL DEFAULT FALSE,
    deleted_at  TIMESTAMP WITH TIME ZONE,
    sync_status VARCHAR(32)              NOT NULL DEFAULT 'PENDING_UPLOAD',

    CONSTRAINT chk_session_audit_action
        CHECK (
            action IN (
            'SESSION_CREATED',
            'SESSION_VIEWED',
            'SESSION_EDITED',
            'SESSION_STARTED',
            'SESSION_SUBMITTED',
            'SESSION_COMPLETED',
            'SESSION_REOPENED',
            'SESSION_MARKED_INCOMPLETE'
            )
) ,
    CONSTRAINT chk_session_audit_actor_role
        CHECK (
            actor_role IS NULL
            OR actor_role IN ('SCOUT', 'MANAGER', 'FARM_ADMIN', 'SUPER_ADMIN', 'EDGE_SYNC')
        )
);

CREATE INDEX idx_session_audit_session ON session_audit_events (session_id);
CREATE INDEX idx_session_audit_farm ON session_audit_events (farm_id);
CREATE INDEX idx_session_audit_action ON session_audit_events (action);
CREATE INDEX idx_session_audit_occurred_at ON session_audit_events (occurred_at);

CREATE TRIGGER trg_session_audit_updated
    BEFORE UPDATE
    ON session_audit_events
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

-- ============================================================
--  SESSION TARGETS
-- ============================================================

CREATE TABLE scouting_session_targets
(
    id                  UUID PRIMARY KEY                  DEFAULT uuid_generate_v4(),
    version             BIGINT                   NOT NULL DEFAULT 0,
    session_id          UUID                     NOT NULL REFERENCES scouting_sessions (id) ON DELETE CASCADE,
    greenhouse_id       UUID                     REFERENCES greenhouses (id) ON DELETE SET NULL,
    field_block_id      UUID                     REFERENCES field_blocks (id) ON DELETE SET NULL,
    include_all_bays    BOOLEAN                  NOT NULL DEFAULT TRUE,
    include_all_benches BOOLEAN                  NOT NULL DEFAULT TRUE,
    created_at          TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted             BOOLEAN                  NOT NULL DEFAULT FALSE,
    deleted_at          TIMESTAMP WITH TIME ZONE,
    sync_status         VARCHAR(32)              NOT NULL DEFAULT 'SYNCED',

    CONSTRAINT chk_session_targets_structure
        CHECK (
            (greenhouse_id IS NOT NULL AND field_block_id IS NULL)
                OR (greenhouse_id IS NULL AND field_block_id IS NOT NULL)
            )
);

CREATE INDEX idx_session_targets_session ON scouting_session_targets (session_id);

CREATE TRIGGER trg_session_targets_updated
    BEFORE UPDATE
    ON scouting_session_targets
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TABLE scouting_target_bays
(
    target_id UUID         NOT NULL REFERENCES scouting_session_targets (id) ON DELETE CASCADE,
    bay_tag   VARCHAR(255) NOT NULL,
    CONSTRAINT pk_scouting_target_bays PRIMARY KEY (target_id, bay_tag)
);

CREATE TABLE scouting_target_benches
(
    target_id UUID         NOT NULL REFERENCES scouting_session_targets (id) ON DELETE CASCADE,
    bench_tag VARCHAR(255) NOT NULL,
    CONSTRAINT pk_scouting_target_benches PRIMARY KEY (target_id, bench_tag)
);

-- ============================================================
--  OBSERVATIONS
-- ============================================================

CREATE TABLE scouting_observations
(
    id                UUID PRIMARY KEY                  DEFAULT uuid_generate_v4(),
    version           BIGINT                   NOT NULL DEFAULT 0,
    session_id        UUID                     NOT NULL REFERENCES scouting_sessions (id) ON DELETE CASCADE,
    session_target_id UUID                     NOT NULL REFERENCES scouting_session_targets (id) ON DELETE CASCADE,
    species_code      VARCHAR(50)              NOT NULL,
    bay_index         INTEGER                  NOT NULL,
    bay_label         VARCHAR(255),
    bench_index       INTEGER                  NOT NULL,
    bench_label       VARCHAR(255),
    spot_index        INTEGER                  NOT NULL,
    count_value       INTEGER                  NOT NULL,
    notes             VARCHAR(2000),
    client_request_id UUID,
    created_at        TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at        TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted           BOOLEAN                  NOT NULL DEFAULT FALSE,
    deleted_at        TIMESTAMP WITH TIME ZONE,
    sync_status       VARCHAR(32)              NOT NULL DEFAULT 'SYNCED',

    CONSTRAINT uk_obs_client_request_id UNIQUE (client_request_id),
    CONSTRAINT uk_session_cell_species
        UNIQUE (session_id, session_target_id, bay_index, bench_index, spot_index, species_code),
    CONSTRAINT chk_scouting_observations_species_code
        CHECK (
            species_code IN (
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
                             'BENEFICIAL_PP'
                )
            )
);

CREATE INDEX idx_obs_session ON scouting_observations (session_id);
CREATE INDEX idx_obs_species ON scouting_observations (species_code);

CREATE TRIGGER trg_obs_updated
    BEFORE UPDATE
    ON scouting_observations
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

-- ============================================================
--  SCOUTING PHOTOS
-- ============================================================

CREATE TABLE scouting_photos
(
    id             UUID PRIMARY KEY                  DEFAULT uuid_generate_v4(),
    version        BIGINT                   NOT NULL DEFAULT 0,
    session_id     UUID                     NOT NULL REFERENCES scouting_sessions (id) ON DELETE CASCADE,
    observation_id UUID                     REFERENCES scouting_observations (id) ON DELETE SET NULL,
    farm_id        UUID                     NOT NULL REFERENCES farms (id) ON DELETE CASCADE,
    local_photo_id VARCHAR(100)             NOT NULL,
    purpose        VARCHAR(255),
    object_key     VARCHAR(500),
    captured_at    TIMESTAMP WITH TIME ZONE,
    created_at     TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at     TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted        BOOLEAN                  NOT NULL DEFAULT FALSE,
    deleted_at     TIMESTAMP WITH TIME ZONE,
    sync_status    VARCHAR(32)              NOT NULL DEFAULT 'SYNCED',

    CONSTRAINT uk_photo_farm_local UNIQUE (farm_id, local_photo_id)
);

CREATE INDEX idx_photo_session ON scouting_photos (session_id);
CREATE INDEX idx_photo_farm ON scouting_photos (farm_id);
CREATE INDEX idx_photo_local_id ON scouting_photos (local_photo_id);

CREATE TRIGGER trg_photos_updated
    BEFORE UPDATE
    ON scouting_photos
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

-- ============================================================
--  SESSION RECOMMENDATIONS
-- ============================================================

CREATE TABLE session_recommendations
(
    session_id          UUID                     NOT NULL REFERENCES scouting_sessions (id) ON DELETE CASCADE,
    recommendation_type VARCHAR(50)              NOT NULL,
    recommendation      VARCHAR(2000),
    created_at          TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    sync_status         VARCHAR(32)              NOT NULL DEFAULT 'SYNCED',

    CONSTRAINT pk_session_recommendations PRIMARY KEY (session_id, recommendation_type),
    CONSTRAINT chk_session_recommendation_type
        CHECK (recommendation_type IN ('BIOLOGICAL_CONTROL', 'CHEMICAL_SPRAYS', 'OTHER_METHODS'))
);

CREATE TRIGGER trg_recommendations_updated
    BEFORE UPDATE
    ON session_recommendations
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

-- ============================================================
--  SUPPLY ORDERING
-- ============================================================

CREATE TABLE supply_order_requests
(
    id                   UUID PRIMARY KEY                  DEFAULT uuid_generate_v4(),
    version              BIGINT                   NOT NULL DEFAULT 0,
    farm_id              UUID                     NOT NULL REFERENCES farms (id) ON DELETE CASCADE,
    requested_by_user_id UUID                     NOT NULL REFERENCES users (id) ON DELETE RESTRICT,
    requested_by_name    VARCHAR(255)             NOT NULL,
    vendor_name          VARCHAR(255),
    status               VARCHAR(32)              NOT NULL,
    currency_code        VARCHAR(8)               NOT NULL DEFAULT 'USD',
    estimated_total      DECIMAL(12, 2)           NOT NULL DEFAULT 0,
    notes                VARCHAR(2000),
    submitted_at         TIMESTAMP WITH TIME ZONE,
    created_at           TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at           TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted              BOOLEAN                  NOT NULL DEFAULT FALSE,
    deleted_at           TIMESTAMP WITH TIME ZONE,
    sync_status          VARCHAR(32)              NOT NULL DEFAULT 'SYNCED',

    CONSTRAINT chk_supply_order_status
        CHECK (status IN ('DRAFT', 'SUBMITTED', 'CANCELLED')),
    CONSTRAINT chk_supply_order_estimated_total
        CHECK (estimated_total >= 0)
);

CREATE INDEX idx_supply_order_requests_requested_by ON supply_order_requests (requested_by_user_id);
CREATE INDEX idx_supply_order_requests_status ON supply_order_requests (status);
CREATE INDEX idx_supply_order_requests_farm_created_at ON supply_order_requests (farm_id, created_at DESC);

CREATE TRIGGER trg_supply_order_requests_updated
    BEFORE UPDATE
    ON supply_order_requests
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TABLE supply_order_items
(
    id                  UUID PRIMARY KEY                  DEFAULT uuid_generate_v4(),
    version             BIGINT                   NOT NULL DEFAULT 0,
    order_request_id    UUID                     NOT NULL REFERENCES supply_order_requests (id) ON DELETE CASCADE,
    sku                 VARCHAR(100)             NOT NULL,
    item_name           VARCHAR(255)             NOT NULL,
    unit_of_measure     VARCHAR(32)              NOT NULL,
    quantity            DECIMAL(12, 2)           NOT NULL,
    unit_price          DECIMAL(12, 2)           NOT NULL,
    line_total          DECIMAL(12, 2)           NOT NULL,
    rationale           VARCHAR(2000),
    source_species_code VARCHAR(255),
    created_at          TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted             BOOLEAN                  NOT NULL DEFAULT FALSE,
    deleted_at          TIMESTAMP WITH TIME ZONE,
    sync_status         VARCHAR(32)              NOT NULL DEFAULT 'SYNCED',

    CONSTRAINT chk_supply_order_items_quantity
        CHECK (quantity >= 0),
    CONSTRAINT chk_supply_order_items_unit_price
        CHECK (unit_price >= 0),
    CONSTRAINT chk_supply_order_items_line_total
        CHECK (line_total >= 0)
);

CREATE INDEX idx_supply_order_items_request ON supply_order_items (order_request_id);
CREATE INDEX idx_supply_order_items_sku ON supply_order_items (sku);

CREATE TRIGGER trg_supply_order_items_updated
    BEFORE UPDATE
    ON supply_order_items
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();
