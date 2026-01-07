-- ============================================================
--  MOFO PEST SCOUTING – MASTER SCHEMA (V1)
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
    id           UUID PRIMARY KEY                  DEFAULT uuid_generate_v4(),
    email        VARCHAR(255)             NOT NULL UNIQUE,
    password     VARCHAR(255)             NOT NULL,
    first_name   VARCHAR(255),
    last_name    VARCHAR(255),
    phone_number VARCHAR(50)              NOT NULL,
    customer_number VARCHAR(100)          NOT NULL UNIQUE,
    role         VARCHAR(50)              NOT NULL,
    is_enabled   BOOLEAN                  NOT NULL DEFAULT TRUE,
    last_login   TIMESTAMP WITH TIME ZONE,
    created_at   TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at   TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version      BIGINT                   NOT NULL DEFAULT 0,
    deleted    BOOLEAN NOT NULL DEFAULT FALSE,
    deleted_at TIMESTAMP WITH TIME ZONE,
    sync_status VARCHAR(32)             NOT NULL DEFAULT 'SYNCED',
    CONSTRAINT chk_user_role
        CHECK (role IN ('SCOUT', 'MANAGER', 'FARM_ADMIN', 'SUPER_ADMIN', 'EDGE_SYNC'))
);

CREATE INDEX idx_users_email ON users (email);
CREATE INDEX idx_users_customer_number ON users (customer_number);
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
    id                        UUID PRIMARY KEY                  DEFAULT uuid_generate_v4(),
    version                   BIGINT                   NOT NULL DEFAULT 0,

    user_id                   UUID                     NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    performed_by_user_id      UUID                               REFERENCES users (id) ON DELETE SET NULL,

    token                     VARCHAR(255)             NOT NULL UNIQUE,
    expires_at                TIMESTAMP WITH TIME ZONE NOT NULL,
    used_at                   TIMESTAMP WITH TIME ZONE,

    verification_channel      VARCHAR(50)              NOT NULL,

    caller_name               VARCHAR(255),
    callback_number           VARCHAR(50),
    verification_notes        VARCHAR(2048),
    first_name_confirmation   VARCHAR(255),
    last_name_confirmation    VARCHAR(255),
    email_confirmation        VARCHAR(255),
    last_login_verified_on    DATE,
    customer_number_confirmation VARCHAR(255),

    created_at                TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at                TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted                   BOOLEAN                  NOT NULL DEFAULT FALSE,
    deleted_at                TIMESTAMP WITH TIME ZONE,
    sync_status VARCHAR(32)             NOT NULL DEFAULT 'SYNCED',

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
    id                            UUID PRIMARY KEY                  DEFAULT uuid_generate_v4(),
    version                       BIGINT                   NOT NULL DEFAULT 0,

    farm_tag                      VARCHAR(32) UNIQUE,
    name                          VARCHAR(255)             NOT NULL,
    description                   VARCHAR(500),
    external_id                   VARCHAR(36)              UNIQUE NOT NULL,

    address                       VARCHAR(255),
    latitude                      DECIMAL(10, 7),
    longitude                     DECIMAL(10, 7),
    city                          VARCHAR(100),
    province                      VARCHAR(100),
    postal_code                   VARCHAR(20),
    country                       VARCHAR(100)                      DEFAULT 'Canada',

    owner_id                      UUID                     NOT NULL REFERENCES users (id) ON DELETE RESTRICT,
    scout_id                      UUID                     REFERENCES users (id) ON DELETE SET NULL,

    contact_name                  VARCHAR(255),
    contact_email                 VARCHAR(255),
    contact_phone                 VARCHAR(50),

    subscription_status           VARCHAR(20)              NOT NULL,
    subscription_tier             VARCHAR(50)              NOT NULL,
    billing_email                 VARCHAR(255),

    licensed_area_hectares        DECIMAL(10, 2)           NOT NULL,
    licensed_unit_quota           INTEGER,
    quota_discount_percentage     DECIMAL(5, 2),

    license_expiry_date           DATE,
    license_grace_period_end      DATE,
    license_archived_date         DATE,
    is_archived                   BOOLEAN                           DEFAULT FALSE,
    auto_renew_enabled            BOOLEAN                           DEFAULT FALSE,

    structure_type                VARCHAR(20)              NOT NULL,
    timezone                      VARCHAR(100),

    default_bay_count             INTEGER,
    default_benches_per_bay       INTEGER,
    default_spot_checks_per_bench INTEGER,

    stripe_customer_id            VARCHAR(255),
    stripe_subscription_id        VARCHAR(255),

    created_at                    TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at                    TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted    BOOLEAN NOT NULL DEFAULT FALSE,
    deleted_at TIMESTAMP WITH TIME ZONE,
    sync_status VARCHAR(32)             NOT NULL DEFAULT 'SYNCED',

    CONSTRAINT chk_sub_status
        CHECK (subscription_status IN ('PENDING_ACTIVATION', 'ACTIVE', 'SUSPENDED', 'CANCELLED', 'DELETED')),
    CONSTRAINT chk_sub_tier
        CHECK (subscription_tier IN ('BASIC', 'STANDARD', 'PREMIUM')),
    CONSTRAINT chk_structure_type
        CHECK (structure_type IN ('GREENHOUSE', 'FIELD', 'MIXED', 'OTHER'))
);

CREATE INDEX idx_farms_name ON farms (name);
CREATE INDEX idx_farms_subscription_status ON farms (subscription_status);
CREATE INDEX idx_farms_owner ON farms (owner_id);
CREATE UNIQUE INDEX idx_farms_farm_tag ON farms (farm_tag);
CREATE UNIQUE INDEX idx_farms_external_id ON farms (external_id);

CREATE TRIGGER trg_farms_updated
    BEFORE UPDATE
    ON farms
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

-- ============================================================
--  USER–FARM MEMBERSHIPS
--  (for managers / scouts assigned to farms)
-- ============================================================

CREATE TABLE user_farm_memberships
(
    id         UUID PRIMARY KEY                  DEFAULT uuid_generate_v4(),
    version    BIGINT                   NOT NULL DEFAULT 0,

    user_id    UUID                     NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    farm_id    UUID                     NOT NULL REFERENCES farms (id) ON DELETE CASCADE,

    role       VARCHAR(50)              NOT NULL,

    is_active  BOOLEAN                  NOT NULL DEFAULT TRUE,

    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted    BOOLEAN NOT NULL DEFAULT FALSE,
    deleted_at TIMESTAMP WITH TIME ZONE,
    sync_status VARCHAR(32)             NOT NULL DEFAULT 'SYNCED',

    CONSTRAINT uk_user_farm UNIQUE (user_id, farm_id)
);



CREATE INDEX idx_ufm_user ON user_farm_memberships (user_id);
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
    is_active             BOOLEAN                  NOT NULL DEFAULT TRUE,

    created_at            TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted    BOOLEAN                  NOT NULL DEFAULT FALSE,
    deleted_at TIMESTAMP WITH TIME ZONE,
    sync_status VARCHAR(32)             NOT NULL DEFAULT 'SYNCED'
);

CREATE INDEX idx_greenhouses_farm ON greenhouses (farm_id);
CREATE INDEX idx_greenhouses_name ON greenhouses (name);

CREATE TRIGGER trg_greenhouses_updated
    BEFORE UPDATE
    ON greenhouses
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

-- Greenhouse bay / bench tags for ElementCollections

CREATE TABLE greenhouse_bay_tags
(
    greenhouse_id UUID         NOT NULL REFERENCES greenhouses (id) ON DELETE CASCADE,
    bay_tag       VARCHAR(255) NOT NULL
);

CREATE INDEX idx_greenhouse_bay_tags_gh ON greenhouse_bay_tags (greenhouse_id);

CREATE TABLE greenhouse_bench_tags
(
    greenhouse_id UUID         NOT NULL REFERENCES greenhouses (id) ON DELETE CASCADE,
    bench_tag     VARCHAR(255) NOT NULL
);

CREATE INDEX idx_greenhouse_bench_tags_gh ON greenhouse_bench_tags (greenhouse_id);

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
    is_active           BOOLEAN                  NOT NULL DEFAULT TRUE,

    created_at          TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted    BOOLEAN                  NOT NULL DEFAULT FALSE,
    deleted_at TIMESTAMP WITH TIME ZONE,
    sync_status VARCHAR(32)             NOT NULL DEFAULT 'SYNCED'
);

CREATE INDEX idx_field_blocks_farm ON field_blocks (farm_id);
CREATE INDEX idx_field_blocks_name ON field_blocks (name);

CREATE TRIGGER trg_field_blocks_updated
    BEFORE UPDATE
    ON field_blocks
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

-- Field block bay tags for ElementCollection on FieldBlock.bayTags

CREATE TABLE field_block_bay_tags
(
    field_block_id UUID         NOT NULL REFERENCES field_blocks (id) ON DELETE CASCADE,
    bay_tag        VARCHAR(255) NOT NULL
);

CREATE INDEX idx_field_block_bay_tags_fb ON field_block_bay_tags (field_block_id);

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

    manager_id                UUID REFERENCES users (id),
    scout_id                  UUID REFERENCES users (id),

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
    completed_at              TIMESTAMP WITH TIME ZONE,
    confirmation_acknowledged BOOLEAN                  NOT NULL DEFAULT FALSE,

    created_at                TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted    BOOLEAN                  NOT NULL DEFAULT FALSE,
    deleted_at TIMESTAMP WITH TIME ZONE,
    sync_status VARCHAR(32)             NOT NULL DEFAULT 'SYNCED'
);

CREATE INDEX idx_sessions_farm ON scouting_sessions (farm_id);
CREATE INDEX idx_sessions_scout ON scouting_sessions (scout_id);
CREATE INDEX idx_sessions_date ON scouting_sessions (session_date);

CREATE TRIGGER trg_sessions_updated
    BEFORE UPDATE
    ON scouting_sessions
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

-- ============================================================
--  SESSION AUDIT EVENTS
-- ============================================================

CREATE TABLE session_audit_events
(
    id           UUID PRIMARY KEY                  DEFAULT uuid_generate_v4(),
    version      BIGINT                   NOT NULL DEFAULT 0,

    session_id   UUID                     NOT NULL REFERENCES scouting_sessions (id) ON DELETE CASCADE,
    farm_id      UUID                     NOT NULL REFERENCES farms (id) ON DELETE CASCADE,

    action       VARCHAR(64)              NOT NULL,
    actor_name   VARCHAR(255),
    actor_id     UUID,
    actor_email  VARCHAR(255),
    actor_role   VARCHAR(50),
    device_id    VARCHAR(255),
    device_type  VARCHAR(255),
    location     VARCHAR(512),
    comment      VARCHAR(2000),
    occurred_at  TIMESTAMP WITH TIME ZONE NOT NULL,

    created_at   TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at   TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted      BOOLEAN                  NOT NULL DEFAULT FALSE,
    deleted_at   TIMESTAMP WITH TIME ZONE,
    sync_status  VARCHAR(32)              NOT NULL DEFAULT 'PENDING_UPLOAD'
);

CREATE INDEX idx_session_audit_session ON session_audit_events (session_id);
CREATE INDEX idx_session_audit_farm ON session_audit_events (farm_id);
CREATE INDEX idx_session_audit_action ON session_audit_events (action);

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
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted    BOOLEAN                  NOT NULL DEFAULT FALSE,
    deleted_at TIMESTAMP WITH TIME ZONE,
    sync_status VARCHAR(32)             NOT NULL DEFAULT 'SYNCED'
);

CREATE INDEX idx_session_targets_session ON scouting_session_targets (session_id);

CREATE TRIGGER trg_session_targets_updated
    BEFORE UPDATE
    ON scouting_session_targets
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

-- Selected bays/benches for a target (if not "all")

CREATE TABLE scouting_target_bays
(
    target_id UUID         NOT NULL REFERENCES scouting_session_targets (id) ON DELETE CASCADE,
    bay_tag   VARCHAR(255) NOT NULL
);

CREATE INDEX idx_target_bays_target ON scouting_target_bays (target_id);

CREATE TABLE scouting_target_benches
(
    target_id UUID         NOT NULL REFERENCES scouting_session_targets (id) ON DELETE CASCADE,
    bench_tag VARCHAR(255) NOT NULL
);

CREATE INDEX idx_target_benches_target ON scouting_target_benches (target_id);

-- ============================================================
--  OBSERVATIONS
-- ============================================================

CREATE TABLE scouting_observations
(
    id                UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
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

    client_request_id UUID UNIQUE,

    created_at        TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at        TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted           BOOLEAN NOT NULL DEFAULT FALSE,
    deleted_at        TIMESTAMP WITH TIME ZONE,
    sync_status VARCHAR(32)             NOT NULL DEFAULT 'SYNCED',

    CONSTRAINT uk_session_cell_species
        UNIQUE (session_id, session_target_id, bay_index, bench_index, spot_index, species_code)
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
    id           UUID PRIMARY KEY                  DEFAULT uuid_generate_v4(),
    version      BIGINT                   NOT NULL DEFAULT 0,

    session_id   UUID                     NOT NULL REFERENCES scouting_sessions (id) ON DELETE CASCADE,
    observation_id UUID                   REFERENCES scouting_observations (id) ON DELETE SET NULL,
    farm_id      UUID                     NOT NULL,

    local_photo_id VARCHAR(100)           NOT NULL,
    purpose      VARCHAR(255),
    object_key   VARCHAR(500),
    captured_at  TIMESTAMP WITH TIME ZONE,

    created_at   TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at   TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted      BOOLEAN                  NOT NULL DEFAULT FALSE,
    deleted_at   TIMESTAMP WITH TIME ZONE,
    sync_status  VARCHAR(32)              NOT NULL DEFAULT 'SYNCED',

    CONSTRAINT uk_photo_farm_local
        UNIQUE (farm_id, local_photo_id)
);

CREATE INDEX idx_photo_session ON scouting_photos (session_id);
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
    sync_status VARCHAR(32)             NOT NULL DEFAULT 'SYNCED',
    CONSTRAINT pk_session_recommendations PRIMARY KEY (session_id, recommendation_type)
);

CREATE TRIGGER trg_recommendations_updated
    BEFORE UPDATE
    ON session_recommendations
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();
