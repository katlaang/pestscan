-- Stable farm URL slugs for farm-scoped routes such as /{slug}/dashboard.

DO
$$
BEGIN
    IF
NOT EXISTS (
        SELECT 1
        FROM information_schema.tables
        WHERE table_schema = 'public'
          AND table_name = 'farms'
    ) THEN
        RETURN;
END IF;

ALTER TABLE farms
    ADD COLUMN IF NOT EXISTS slug VARCHAR (255);
END $$;

WITH normalized AS (SELECT id,
    left (
    COALESCE (
    NULLIF (trim (slug)
   , '')
   , NULLIF (
    regexp_replace(
    regexp_replace(
    regexp_replace(lower (trim (name))
   , '[^a-z0-9[:space:]-]'
   , ''
   , 'g')
   , '[[:space:]]+'
   , '-'
   , 'g'
    )
   , '^-+|-+$'
   , ''
   , 'g'
    )
   , ''
    )
   , 'farm'
    )
   , 255
    ) AS base_slug
   , created_at
FROM farms
    ), ranked AS (
SELECT id, base_slug, row_number() OVER (
    PARTITION BY base_slug
    ORDER BY created_at ASC, id ASC
    ) AS slug_rank
FROM normalized
    ), resolved AS (
SELECT id, CASE
    WHEN slug_rank = 1 THEN base_slug
    ELSE left (base_slug, greatest(1, 255 - length ('-' || slug_rank::text))) || '-' || slug_rank::text
    END AS resolved_slug
FROM ranked
    )
UPDATE farms farm
SET slug = resolved.resolved_slug FROM resolved
WHERE farm.id = resolved.id
  AND farm.slug IS DISTINCT
FROM resolved.resolved_slug;

ALTER TABLE farms
    ALTER COLUMN slug SET NOT NULL;

CREATE UNIQUE INDEX IF NOT EXISTS idx_farms_slug ON farms (slug);
