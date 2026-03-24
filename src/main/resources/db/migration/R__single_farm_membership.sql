-- Allow users to hold memberships in multiple farms while still preventing
-- duplicate membership rows for the same user/farm pair.

DO
$$
BEGIN
    IF
NOT EXISTS (
        SELECT 1
        FROM information_schema.tables
        WHERE table_schema = 'public'
          AND table_name = 'user_farm_memberships'
    ) THEN
        RETURN;
END IF;

DELETE
FROM user_farm_memberships membership USING (
        SELECT id
        FROM (
            SELECT id,
                   row_number() OVER (
                       PARTITION BY user_id, farm_id
                       ORDER BY
                           CASE WHEN is_active THEN 0 ELSE 1 END,
                           updated_at DESC,
                           created_at DESC,
                           id DESC
                   ) AS row_number
            FROM user_farm_memberships
        ) ranked
        WHERE ranked.row_number > 1
    ) duplicates
WHERE membership.id = duplicates.id;

IF
EXISTS (
        SELECT 1
        FROM information_schema.table_constraints
        WHERE table_schema = 'public'
          AND table_name = 'user_farm_memberships'
          AND constraint_name = 'uk_user_farm'
    ) THEN
ALTER TABLE user_farm_memberships
DROP
CONSTRAINT uk_user_farm;
END IF;

    IF
EXISTS (
        SELECT 1
        FROM information_schema.table_constraints
        WHERE table_schema = 'public'
          AND table_name = 'user_farm_memberships'
          AND constraint_name = 'uk_user_membership'
    ) THEN
ALTER TABLE user_farm_memberships
DROP
CONSTRAINT uk_user_membership;
END IF;

    IF
NOT EXISTS (
        SELECT 1
        FROM information_schema.table_constraints
        WHERE table_schema = 'public'
          AND table_name = 'user_farm_memberships'
          AND constraint_name = 'uk_user_farm_membership_pair'
    ) THEN
ALTER TABLE user_farm_memberships
    ADD CONSTRAINT uk_user_farm_membership_pair UNIQUE (user_id, farm_id);
END IF;
END $$;
