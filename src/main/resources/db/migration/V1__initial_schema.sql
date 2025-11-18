-- ============================================================
--  MOFO PEST SCOUTING – MASTER SCHEMA (V1)
--  Fully aligned with all current JPA entities
-- ============================================================

CREATE
EXTENSION IF NOT EXISTS "uuid-ossp";

-- ============================================================
--  BASE ENTITY SUPPORT
--  (All tables include created_at, updated_at, version)
-- ============================================================

CREATE
OR REPLACE FUNCTION update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at
= CURRENT_TIMESTAMP;
RETURN NEW;
END;
$$
LANGUAGE plpgsql;


-- ============================================================
--  USERS (CREATE FIRST – referenced by many tables)
-- ============================================================

CREATE TABLE users
(
    id           UUID PRIMARY KEY         DEFAULT uuid_generate_v4(),
    email        VARCHAR(255) NOT NULL UNIQUE,
    password     VARCHAR(255) NOT NULL,
    first_name   VARCHAR(100),
    last_name    VARCHAR(100),
    phone_number VARCHAR(50),
    role         VARCHAR(50)  NOT NULL,
    is_enabled   BOOLEAN                  DEFAULT TRUE,
    last_login   TIMESTAMP WITH TIME ZONE,
    created_at   TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at   TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    version      BIGINT                   DEFAULT 0,

    CONSTRAINT chk_user_role
        CHECK (role IN ('SCOUT', 'MANAGER', 'FARM_ADMIN', 'SUPER_ADMIN'))
);

CREATE INDEX idx_users_email ON users (email);
CREATE INDEX idx_users_role ON users (role);


-- ============================================================
--  FARMS
-- ============================================================

CREATE TABLE farms
(
    id                            UUID PRIMARY KEY         DEFAULT uuid_generate_v4(),
    version                       BIGINT                   DEFAULT 0,

    farm_tag                      VARCHAR(32) UNIQUE,
    name                          VARCHAR(255)   NOT NULL,
    description                   VARCHAR(500),
    external_id                   VARCHAR(255),

    address                       VARCHAR(255),
    latitude                      DECIMAL(10, 7),
    longitude                     DECIMAL(10, 7),
    city                          VARCHAR(100),
    province                      VARCHAR(100),
    postal_code                   VARCHAR(20),
    country                       VARCHAR(100)             DEFAULT 'Canada',

    owner_id                      UUID           NOT NULL REFERENCES users (id) ON DELETE RESTRICT,
    scout_id                      UUID           REFERENCES users (id) ON DELETE SET NULL,

    contact_name                  VARCHAR(255),
    contact_email                 VARCHAR(255),
    contact_phone                 VARCHAR(50),

    subscription_status           VARCHAR(20)    NOT NULL,
    subscription_tier             VARCHAR(50)    NOT NULL,
    billing_email                 VARCHAR(255),

    licensed_area_hectares        DECIMAL(10, 2) NOT NULL,
    licensed_unit_quota           INTEGER,
    quota_discount_percentage     DECIMAL(5, 2),

    license_expiry_date           DATE,
    license_grace_period_end      DATE,
    license_archived_date         DATE,
    is_archived                   BOOLEAN                  DEFAULT FALSE,
    auto_renew_enabled            BOOLEAN                  DEFAULT FALSE,

    structure_type                VARCHAR(20)    NOT NULL,
    timezone                      VARCHAR(100),

    default_bay_count             INTEGER,
    default_benches_per_bay       INTEGER,
    default_spot_checks_per_bench INTEGER,

    stripe_customer_id            VARCHAR(255),
    stripe_subscription_id        VARCHAR(255),

    created_at                    TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at                    TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT chk_sub_status
        CHECK (subscription_status IN
               ('ACTIVE', 'SUSPENDED', 'DELETED', 'PENDING_ACTIVATION')),

    CONSTRAINT chk_structure_type
        CHECK (structure_type IN ('GREENHOUSE', 'FIELD', 'MIXED', 'OTHER'))
);

CREATE INDEX idx_farms_name ON farms (name);
CREATE INDEX idx_farms_status ON farms (subscription_status);
CREATE INDEX idx_farms_owner ON farms (owner_id);


-- ============================================================
--  GREENHOUSES & FIELD BLOCKS  (sites)
-- ============================================================

CREATE TABLE greenhouses
(
    id                    UUID PRIMARY KEY         DEFAULT uuid_generate_v4(),
    version               BIGINT                   DEFAULT 0,
    farm_id               UUID         NOT NULL REFERENCES farms (id) ON DELETE CASCADE,

    name                  VARCHAR(255) NOT NULL,
    bay_count             INTEGER,
    benches_per_bay       INTEGER,
    spot_checks_per_bench INTEGER,

    created_at            TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at            TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);


CREATE TABLE field_blocks
(
    id                  UUID PRIMARY KEY         DEFAULT uuid_generate_v4(),
    version             BIGINT                   DEFAULT 0,
    farm_id             UUID         NOT NULL REFERENCES farms (id) ON DELETE CASCADE,

    name                VARCHAR(255) NOT NULL,
    description         VARCHAR(500),
    bay_count           INTEGER,
    spot_checks_per_bay INTEGER,
    active              BOOLEAN                  DEFAULT TRUE,

    created_at          TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at          TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);


-- ============================================================
--  SCOUTING SESSIONS
-- ============================================================

CREATE TABLE scouting_sessions
(
    id                        UUID PRIMARY KEY         DEFAULT uuid_generate_v4(),
    version                   BIGINT                   DEFAULT 0,

    farm_id                   UUID NOT NULL REFERENCES farms (id) ON DELETE CASCADE,
    greenhouse_id             UUID REFERENCES greenhouses (id) ON DELETE SET NULL,
    field_block_id            UUID REFERENCES field_blocks (id) ON DELETE SET NULL,

    manager_id                UUID REFERENCES users (id),
    scout_id                  UUID REFERENCES users (id),

    session_date              DATE NOT NULL,
    week_number               INTEGER,
    year                      INTEGER,

    crop_type                 VARCHAR(100),
    crop_variety              VARCHAR(100),
    weather                   VARCHAR(255),
    notes                     TEXT,

    started_at                TIMESTAMP WITH TIME ZONE,
    completed_at              TIMESTAMP WITH TIME ZONE,

    confirmation_acknowledged BOOLEAN                  DEFAULT FALSE,

    created_at                TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at                TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_sessions_farm ON scouting_sessions (farm_id);
CREATE INDEX idx_sessions_scout ON scouting_sessions (scout_id);


-- ============================================================
--  OBSERVATIONS
-- ============================================================

CREATE TABLE scouting_observations
(
    id          UUID PRIMARY KEY         DEFAULT uuid_generate_v4(),
    version     BIGINT                   DEFAULT 0,

    session_id  UUID        NOT NULL REFERENCES scouting_sessions (id) ON DELETE CASCADE,
    category    VARCHAR(50) NOT NULL,
    count       INTEGER,
    bay_index   INTEGER,
    bench_index INTEGER,
    notes       TEXT,

    created_at  TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at  TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT chk_obs_category CHECK (category IN ('PEST', 'DISEASE', 'BENEFICIAL', 'CULTURAL'))
);

CREATE INDEX idx_obs_session ON scouting_observations (session_id);
CREATE INDEX idx_obs_category ON scouting_observations (category);


-- ============================================================
--  RECOMMENDATIONS
-- ============================================================

CREATE TABLE recommendations
(
    id         UUID PRIMARY KEY         DEFAULT uuid_generate_v4(),
    version    BIGINT                   DEFAULT 0,

    session_id UUID        NOT NULL REFERENCES scouting_sessions (id) ON DELETE CASCADE,
    type       VARCHAR(50) NOT NULL,
    message    TEXT,

    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);


-- ============================================================
--  FINAL: apply updated_at triggers
-- ============================================================

CREATE TRIGGER trg_users_updated
    BEFORE UPDATE
    ON users
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER trg_farms_updated
    BEFORE UPDATE
    ON farms
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER trg_greenhouses_updated
    BEFORE UPDATE
    ON greenhouses
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER trg_field_blocks_updated
    BEFORE UPDATE
    ON field_blocks
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER trg_sessions_updated
    BEFORE UPDATE
    ON scouting_sessions
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER trg_obs_updated
    BEFORE UPDATE
    ON scouting_observations
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER trg_recommendations_updated
    BEFORE UPDATE
    ON recommendations
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();
