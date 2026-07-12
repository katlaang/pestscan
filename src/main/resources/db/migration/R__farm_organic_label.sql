-- Compatibility repeatable migration kept for environments that applied the
-- original Flyway description before the file was renamed.
ALTER TABLE farms
    ADD COLUMN IF NOT EXISTS organic BOOLEAN NOT NULL DEFAULT FALSE;
