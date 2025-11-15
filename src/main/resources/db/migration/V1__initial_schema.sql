-- Mofo Pest Scouting System - Initial Database Schema
-- Multi-tenant architecture with farm_id on all tables

-- Enable UUID extension
CREATE
EXTENSION IF NOT EXISTS "uuid-ossp";

-- ========================================
-- FARM MANAGEMENT TABLES
-- ========================================

-- Farms table (Tenants)
CREATE TABLE farms
(
    id                  UUID PRIMARY KEY         DEFAULT uuid_generate_v4(),
    name                VARCHAR(255) NOT NULL,
    address             TEXT,
    city                VARCHAR(100),
    province            VARCHAR(100),
    postal_code         VARCHAR(20),
    country             VARCHAR(100)             DEFAULT 'Canada',
    contact_email       VARCHAR(255),
    contact_phone       VARCHAR(50),
    subscription_status VARCHAR(20)  NOT NULL    DEFAULT 'ACTIVE',
    subscription_tier   VARCHAR(50)              DEFAULT 'BASIC',
    billing_email       VARCHAR(255),
    stripe_customer_id  VARCHAR(255),
    created_at          TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at          TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_subscription_status CHECK (subscription_status IN ('ACTIVE', 'SUSPENDED', 'DELETED'))
);

CREATE INDEX idx_farms_subscription_status ON farms (subscription_status);
CREATE INDEX idx_farms_stripe_customer_id ON farms (stripe_customer_id);

-- Sites (Greenhouses, Fields)
CREATE TABLE sites
(
    id         UUID PRIMARY KEY         DEFAULT uuid_generate_v4(),
    farm_id    UUID         NOT NULL REFERENCES farms (id) ON DELETE CASCADE,
    name       VARCHAR(255) NOT NULL,
    site_type  VARCHAR(50)  NOT NULL,
    area       DECIMAL(10, 2),
    latitude   DECIMAL(10, 8),
    longitude  DECIMAL(11, 8),
    is_active  BOOLEAN                  DEFAULT TRUE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_site_type CHECK (site_type IN ('GREENHOUSE', 'FIELD', 'NURSERY', 'OTHER'))
);

CREATE INDEX idx_sites_farm_id ON sites (farm_id);
CREATE INDEX idx_sites_is_active ON sites (is_active);

-- Bays (Zones within sites)
CREATE TABLE bays
(
    id         UUID PRIMARY KEY         DEFAULT uuid_generate_v4(),
    site_id    UUID         NOT NULL REFERENCES sites (id) ON DELETE CASCADE,
    name       VARCHAR(255) NOT NULL,
    bay_number INTEGER,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_bays_site_id ON bays (site_id);

-- Benches (Rows within bays)
CREATE TABLE benches
(
    id           UUID PRIMARY KEY         DEFAULT uuid_generate_v4(),
    bay_id       UUID         NOT NULL REFERENCES bays (id) ON DELETE CASCADE,
    name         VARCHAR(255) NOT NULL,
    bench_number INTEGER,
    created_at   TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_benches_bay_id ON benches (bay_id);

-- ========================================
-- USER MANAGEMENT TABLES
-- ========================================

-- Users table
CREATE TABLE users
(
    id         UUID PRIMARY KEY         DEFAULT uuid_generate_v4(),
    farm_id    UUID         REFERENCES farms (id) ON DELETE SET NULL,
    email      VARCHAR(255) NOT NULL UNIQUE,
    password   VARCHAR(255) NOT NULL,
    first_name VARCHAR(100),
    last_name  VARCHAR(100),
    phone_number VARCHAR(50),
    role       VARCHAR(50)  NOT NULL,
    is_enabled BOOLEAN                  DEFAULT TRUE,
    last_login TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_user_role CHECK (role IN ('SCOUT', 'MANAGER', 'FARM_ADMIN', 'SUPER_ADMIN'))
);

CREATE INDEX idx_users_email ON users (email);
CREATE INDEX idx_users_farm_id ON users (farm_id);
CREATE INDEX idx_users_role ON users (role);

-- ========================================
-- SCOUTING TABLES
-- ========================================

-- Scouting Sessions
CREATE TABLE scouting_sessions
(
    id           UUID PRIMARY KEY         DEFAULT uuid_generate_v4(),
    farm_id      UUID NOT NULL REFERENCES farms (id) ON DELETE CASCADE,
    site_id      UUID NOT NULL REFERENCES sites (id) ON DELETE CASCADE,
    scout_id     UUID NOT NULL REFERENCES users (id),
    crop_type    VARCHAR(100),
    crop_variety VARCHAR(100),
    session_date DATE NOT NULL,
    week_number  INTEGER,
    year         INTEGER,
    notes        TEXT,
    latitude     DECIMAL(10, 8),
    longitude    DECIMAL(11, 8),
    device_id    VARCHAR(255),
    created_at   TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at   TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    synced_at    TIMESTAMP WITH TIME ZONE
);

CREATE INDEX idx_sessions_farm_id ON scouting_sessions (farm_id);
CREATE INDEX idx_sessions_site_id ON scouting_sessions (site_id);
CREATE INDEX idx_sessions_scout_id ON scouting_sessions (scout_id);
CREATE INDEX idx_sessions_date ON scouting_sessions (session_date);
CREATE INDEX idx_sessions_week_year ON scouting_sessions (week_number, year);

-- Spot Checks (Sampling locations)
CREATE TABLE spot_checks
(
    id         UUID PRIMARY KEY         DEFAULT uuid_generate_v4(),
    session_id UUID NOT NULL REFERENCES scouting_sessions (id) ON DELETE CASCADE,
    bay_id     UUID REFERENCES bays (id),
    bench_id   UUID REFERENCES benches (id),
    spot_index INTEGER,
    latitude   DECIMAL(10, 8),
    longitude  DECIMAL(11, 8),
    notes      TEXT,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_spot_checks_session_id ON spot_checks (session_id);
CREATE INDEX idx_spot_checks_bay_id ON spot_checks (bay_id);
CREATE INDEX idx_spot_checks_bench_id ON spot_checks (bench_id);

-- Observations (Pest, Disease, Beneficial, Cultural)
CREATE TABLE observations
(
    id               UUID PRIMARY KEY         DEFAULT uuid_generate_v4(),
    spot_check_id    UUID         NOT NULL REFERENCES spot_checks (id) ON DELETE CASCADE,
    observation_type VARCHAR(50)  NOT NULL,
    item_code        VARCHAR(100) NOT NULL,
    item_name        VARCHAR(255),
    count            INTEGER,
    severity         VARCHAR(50),
    notes            TEXT,
    photo_url        TEXT,
    created_at       TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at       TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_observation_type CHECK (observation_type IN ('PEST', 'DISEASE', 'BENEFICIAL', 'CULTURAL')),
    CONSTRAINT chk_severity CHECK (severity IN ('LOW', 'MODERATE', 'HIGH', 'SEVERE'))
);

CREATE INDEX idx_observations_spot_check_id ON observations (spot_check_id);
CREATE INDEX idx_observations_item_code ON observations (item_code);
CREATE INDEX idx_observations_type ON observations (observation_type);
CREATE INDEX idx_observations_severity ON observations (severity);

-- ========================================
-- TREATMENT TABLES
-- ========================================

-- Treatments
CREATE TABLE treatments
(
    id                   UUID PRIMARY KEY         DEFAULT uuid_generate_v4(),
    farm_id              UUID NOT NULL REFERENCES farms (id) ON DELETE CASCADE,
    site_id              UUID NOT NULL REFERENCES sites (id) ON DELETE CASCADE,
    session_id           UUID REFERENCES scouting_sessions (id),
    observation_id       UUID REFERENCES observations (id),
    product_name         VARCHAR(255),
    target_pest          VARCHAR(255),
    application_method   VARCHAR(100),
    dosage               VARCHAR(255),
    treatment_date       DATE NOT NULL,
    applied_by           UUID REFERENCES users (id),
    cost                 DECIMAL(10, 2),
    area_description     TEXT,
    effectiveness_rating INTEGER,
    notes                TEXT,
    created_at           TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at           TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_effectiveness_rating CHECK (effectiveness_rating >= 1 AND effectiveness_rating <= 10)
);

CREATE INDEX idx_treatments_farm_id ON treatments (farm_id);
CREATE INDEX idx_treatments_site_id ON treatments (site_id);
CREATE INDEX idx_treatments_session_id ON treatments (session_id);
CREATE INDEX idx_treatments_observation_id ON treatments (observation_id);
CREATE INDEX idx_treatments_date ON treatments (treatment_date);

-- ========================================
-- ALERT TABLES
-- ========================================

-- Alerts
CREATE TABLE alerts
(
    id               UUID PRIMARY KEY         DEFAULT uuid_generate_v4(),
    farm_id          UUID        NOT NULL REFERENCES farms (id) ON DELETE CASCADE,
    site_id          UUID REFERENCES sites (id),
    observation_id   UUID REFERENCES observations (id),
    alert_type       VARCHAR(50) NOT NULL,
    severity         VARCHAR(50) NOT NULL,
    pest_code        VARCHAR(100),
    pest_name        VARCHAR(255),
    message          TEXT,
    is_resolved      BOOLEAN                  DEFAULT FALSE,
    resolved_at      TIMESTAMP WITH TIME ZONE,
    resolved_by      UUID REFERENCES users (id),
    resolution_notes TEXT,
    created_at       TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_alert_type CHECK (alert_type IN ('THRESHOLD_EXCEEDED', 'RAPID_INCREASE', 'NEW_PEST', 'MANUAL')),
    CONSTRAINT chk_alert_severity CHECK (severity IN ('LOW', 'MEDIUM', 'HIGH', 'CRITICAL'))
);

CREATE INDEX idx_alerts_farm_id ON alerts (farm_id);
CREATE INDEX idx_alerts_site_id ON alerts (site_id);
CREATE INDEX idx_alerts_is_resolved ON alerts (is_resolved);
CREATE INDEX idx_alerts_severity ON alerts (severity);
CREATE INDEX idx_alerts_created_at ON alerts (created_at);

-- ========================================
-- CONFIGURATION TABLES
-- ========================================

-- Threshold Configurations
CREATE TABLE threshold_configs
(
    id                 UUID PRIMARY KEY         DEFAULT uuid_generate_v4(),
    farm_id            UUID         NOT NULL REFERENCES farms (id) ON DELETE CASCADE,
    pest_code          VARCHAR(100) NOT NULL,
    pest_name          VARCHAR(255),
    moderate_threshold INTEGER,
    high_threshold     INTEGER,
    critical_threshold INTEGER,
    created_at         TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at         TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (farm_id, pest_code)
);

CREATE INDEX idx_threshold_configs_farm_id ON threshold_configs (farm_id);

-- ========================================
-- AUDIT LOG TABLE
-- ========================================

-- Audit Log for compliance and tracking
CREATE TABLE audit_log
(
    id          UUID PRIMARY KEY         DEFAULT uuid_generate_v4(),
    farm_id     UUID REFERENCES farms (id),
    user_id     UUID REFERENCES users (id),
    entity_type VARCHAR(100) NOT NULL,
    entity_id   UUID         NOT NULL,
    action      VARCHAR(50)  NOT NULL,
    old_values  JSONB,
    new_values  JSONB,
    ip_address  VARCHAR(50),
    user_agent  TEXT,
    created_at  TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_audit_action CHECK (action IN ('CREATE', 'UPDATE', 'DELETE')
)
    );

CREATE INDEX idx_audit_log_farm_id ON audit_log (farm_id);
CREATE INDEX idx_audit_log_user_id ON audit_log (user_id);
CREATE INDEX idx_audit_log_entity ON audit_log (entity_type, entity_id);
CREATE INDEX idx_audit_log_created_at ON audit_log (created_at);

-- User farm memberships: link users to farms they manage or work on

CREATE TABLE user_farm_memberships
(
    id         UUID PRIMARY KEY         DEFAULT uuid_generate_v4(),
    user_id    UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    farm_id    UUID        NOT NULL REFERENCES farms (id) ON DELETE CASCADE,
    role       VARCHAR(50) NOT NULL,
    is_active  BOOLEAN                  DEFAULT TRUE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_user_farm UNIQUE (user_id, farm_id),
    CONSTRAINT chk_membership_role CHECK (role IN ('SCOUT', 'MANAGER', 'FARM_ADMIN', 'SUPER_ADMIN'))
);

CREATE INDEX idx_user_farm_memberships_user_id ON user_farm_memberships (user_id);
CREATE INDEX idx_user_farm_memberships_farm_id ON user_farm_memberships (farm_id);
CREATE INDEX idx_user_farm_memberships_role ON user_farm_memberships (role);

-- ========================================
-- TRIGGERS FOR UPDATED_AT
-- ========================================

-- Function to update updated_at timestamp
CREATE
OR REPLACE FUNCTION update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at
= CURRENT_TIMESTAMP;
RETURN NEW;
END;
$$
language 'plpgsql';

-- Apply trigger to tables with updated_at
CREATE TRIGGER update_farms_updated_at
    BEFORE UPDATE
    ON farms
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_sites_updated_at
    BEFORE UPDATE
    ON sites
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_users_updated_at
    BEFORE UPDATE
    ON users
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_sessions_updated_at
    BEFORE UPDATE
    ON scouting_sessions
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_spot_checks_updated_at
    BEFORE UPDATE
    ON spot_checks
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_observations_updated_at
    BEFORE UPDATE
    ON observations
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_treatments_updated_at
    BEFORE UPDATE
    ON treatments
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_threshold_configs_updated_at
    BEFORE UPDATE
    ON threshold_configs
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();