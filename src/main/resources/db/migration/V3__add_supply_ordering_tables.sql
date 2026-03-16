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

    CONSTRAINT chk_supply_order_status CHECK (
        status IN ('DRAFT', 'SUBMITTED', 'CANCELLED')
        )
);

CREATE INDEX idx_supply_order_requests_farm ON supply_order_requests (farm_id);
CREATE INDEX idx_supply_order_requests_requested_by ON supply_order_requests (requested_by_user_id);
CREATE INDEX idx_supply_order_requests_status ON supply_order_requests (status);

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
    sync_status         VARCHAR(32)              NOT NULL DEFAULT 'SYNCED'
);

CREATE INDEX idx_supply_order_items_request ON supply_order_items (order_request_id);
CREATE INDEX idx_supply_order_items_sku ON supply_order_items (sku);

CREATE TRIGGER trg_supply_order_items_updated
    BEFORE UPDATE
    ON supply_order_items
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();
