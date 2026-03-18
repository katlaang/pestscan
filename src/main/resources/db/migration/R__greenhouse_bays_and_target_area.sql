CREATE TABLE IF NOT EXISTS greenhouse_bays
(
    greenhouse_id
    UUID
    NOT
    NULL
    REFERENCES
    greenhouses
(
    id
) ON DELETE CASCADE,
    position_index INTEGER NOT NULL,
    bay_tag VARCHAR
(
    255
) NOT NULL,
    bed_count INTEGER NOT NULL,
    CONSTRAINT pk_greenhouse_bays PRIMARY KEY
(
    greenhouse_id,
    position_index
),
    CONSTRAINT uk_greenhouse_bays_tag UNIQUE
(
    greenhouse_id,
    bay_tag
),
    CONSTRAINT chk_greenhouse_bays_position CHECK
(
    position_index
    >=
    0
),
    CONSTRAINT chk_greenhouse_bays_bed_count CHECK
(
    bed_count
    >=
    1
)
    );

ALTER TABLE field_blocks
    ADD COLUMN IF NOT EXISTS crop_type VARCHAR (255);

ALTER TABLE scouting_session_targets
    ADD COLUMN IF NOT EXISTS area_hectares DECIMAL (10, 2);
