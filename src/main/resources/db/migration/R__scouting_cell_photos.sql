ALTER TABLE scouting_photos
    ADD COLUMN IF NOT EXISTS session_target_id UUID REFERENCES scouting_session_targets (id) ON DELETE SET NULL;

ALTER TABLE scouting_photos
    ADD COLUMN IF NOT EXISTS bay_index INTEGER;

ALTER TABLE scouting_photos
    ADD COLUMN IF NOT EXISTS bay_label VARCHAR (255);

ALTER TABLE scouting_photos
    ADD COLUMN IF NOT EXISTS bench_index INTEGER;

ALTER TABLE scouting_photos
    ADD COLUMN IF NOT EXISTS bench_label VARCHAR (255);

ALTER TABLE scouting_photos
    ADD COLUMN IF NOT EXISTS spot_index INTEGER;

UPDATE scouting_photos p
SET session_target_id = o.session_target_id,
    bay_index         = o.bay_index,
    bay_label         = o.bay_label,
    bench_index       = o.bench_index,
    bench_label       = o.bench_label,
    spot_index        = o.spot_index FROM scouting_observations o
WHERE p.observation_id = o.id
  AND (p.session_target_id IS NULL
   OR p.bay_index IS NULL
   OR p.bench_index IS NULL
   OR p.spot_index IS NULL);

CREATE INDEX IF NOT EXISTS idx_photo_session_cell
    ON scouting_photos (session_id, session_target_id, bay_index, bench_index, spot_index);

ALTER TABLE scouting_photos
DROP
CONSTRAINT IF EXISTS chk_scouting_photos_cell_reference;

ALTER TABLE scouting_photos
    ADD CONSTRAINT chk_scouting_photos_cell_reference
        CHECK (
            (session_target_id IS NULL AND bay_index IS NULL AND bench_index IS NULL AND spot_index IS NULL)
                OR
            (session_target_id IS NOT NULL AND bay_index IS NOT NULL AND bench_index IS NOT NULL AND
             spot_index IS NOT NULL)
            );
