ALTER TABLE scouting_sessions
    ADD COLUMN IF NOT EXISTS observation_timezone VARCHAR (100);

UPDATE scouting_sessions AS session
SET observation_timezone = farm.timezone
FROM farms AS farm
WHERE session.farm_id = farm.id
  AND session.observation_timezone IS NULL
  AND farm.timezone IS NOT NULL
  AND farm.timezone <> '';
