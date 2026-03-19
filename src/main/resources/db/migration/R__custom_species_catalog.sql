CREATE TABLE IF NOT EXISTS custom_species_definitions
(
    id
    UUID
    PRIMARY
    KEY
    DEFAULT
    uuid_generate_v4
(
),
    version BIGINT NOT NULL DEFAULT 0,
    farm_id UUID NOT NULL REFERENCES farms
(
    id
) ON DELETE CASCADE,
    category VARCHAR
(
    32
) NOT NULL,
    name VARCHAR
(
    255
) NOT NULL,
    code VARCHAR
(
    120
),
    normalized_name VARCHAR
(
    255
) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted BOOLEAN NOT NULL DEFAULT FALSE,
    deleted_at TIMESTAMP
  WITH TIME ZONE,
      sync_status VARCHAR (32) NOT NULL DEFAULT 'SYNCED'
    );

CREATE INDEX IF NOT EXISTS idx_custom_species_farm
    ON custom_species_definitions (farm_id);

CREATE INDEX IF NOT EXISTS idx_custom_species_farm_category
    ON custom_species_definitions (farm_id, category);

ALTER TABLE custom_species_definitions
DROP
CONSTRAINT IF EXISTS chk_custom_species_category;

ALTER TABLE custom_species_definitions
    ADD CONSTRAINT chk_custom_species_category
        CHECK (category IN ('PEST', 'DISEASE', 'BENEFICIAL'));

ALTER TABLE custom_species_definitions
    ADD COLUMN IF NOT EXISTS code VARCHAR (120);

WITH prepared_codes AS (SELECT c.id,
                               c.farm_id,
                               c.category,
                               COALESCE(
                                       NULLIF(
                                               trim(both '_' FROM
                                                    regexp_replace(upper(c.name), '[^A-Z0-9]+', '_', 'g')),
                                               ''
                                       ),
                                       'CUSTOM_SPECIES'
                               ) AS base_code
                        FROM custom_species_definitions c),
     numbered_codes AS (SELECT id,
                               CASE
                                   WHEN row_number() OVER (PARTITION BY farm_id, category, base_code ORDER BY id) = 1
                   THEN left (base_code, 120)
    ELSE left (base_code, 120 - length ('_' || row_number() OVER (PARTITION BY farm_id, category, base_code ORDER BY id)::text))
    || '_' || row_number() OVER (PARTITION BY farm_id, category, base_code ORDER BY id)::text
END
AS generated_code
    FROM prepared_codes
)
UPDATE custom_species_definitions c
SET code = n.generated_code FROM numbered_codes n
WHERE c.id = n.id
  AND (c.code IS NULL
   OR c.code = '');

ALTER TABLE custom_species_definitions
    ALTER COLUMN code SET NOT NULL;

ALTER TABLE custom_species_definitions
DROP
CONSTRAINT IF EXISTS uk_custom_species_farm_category_name;

ALTER TABLE custom_species_definitions
    ADD CONSTRAINT uk_custom_species_farm_category_name
        UNIQUE (farm_id, category, normalized_name);

ALTER TABLE custom_species_definitions
DROP
CONSTRAINT IF EXISTS uk_custom_species_farm_category_code;

ALTER TABLE custom_species_definitions
    ADD CONSTRAINT uk_custom_species_farm_category_code
        UNIQUE (farm_id, category, code);

DROP TRIGGER IF EXISTS trg_custom_species_updated ON custom_species_definitions;

CREATE TRIGGER trg_custom_species_updated
    BEFORE UPDATE
    ON custom_species_definitions
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TABLE IF NOT EXISTS scouting_session_custom_species
(
    session_id
    UUID
    NOT
    NULL
    REFERENCES
    scouting_sessions
(
    id
) ON DELETE CASCADE,
    custom_species_id UUID NOT NULL REFERENCES custom_species_definitions
(
    id
)
  ON DELETE CASCADE,
    CONSTRAINT pk_scouting_session_custom_species PRIMARY KEY
(
    session_id,
    custom_species_id
)
    );

CREATE INDEX IF NOT EXISTS idx_scouting_session_custom_species_session
    ON scouting_session_custom_species (session_id);

ALTER TABLE scouting_observations
    ADD COLUMN IF NOT EXISTS custom_species_id UUID REFERENCES custom_species_definitions (id) ON DELETE SET NULL;

ALTER TABLE scouting_observations
    ADD COLUMN IF NOT EXISTS species_identifier VARCHAR (128);

UPDATE scouting_observations
SET species_identifier = 'CODE:' || species_code
WHERE species_identifier IS NULL
  AND species_code IS NOT NULL;

ALTER TABLE scouting_observations
    ALTER COLUMN species_identifier SET NOT NULL;

ALTER TABLE scouting_observations
    ALTER COLUMN species_code DROP NOT NULL;

CREATE INDEX IF NOT EXISTS idx_obs_custom_species
    ON scouting_observations (custom_species_id);

ALTER TABLE scouting_observations
DROP
CONSTRAINT IF EXISTS uk_session_cell_species;

ALTER TABLE scouting_observations
    ADD CONSTRAINT uk_session_cell_species
        UNIQUE (session_id, session_target_id, bay_index, bench_index, spot_index, species_identifier);

ALTER TABLE scouting_observations
DROP
CONSTRAINT IF EXISTS chk_scouting_observations_species_code;

ALTER TABLE scouting_observations
    ADD CONSTRAINT chk_scouting_observations_species_code
        CHECK (
            species_code IS NULL OR species_code IN (
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
            );
