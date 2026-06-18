DO
$$
BEGIN
    IF
EXISTS (
        SELECT 1
        FROM users
        GROUP BY lower(trim(email))
        HAVING count(*) > 1
    ) THEN
        RAISE EXCEPTION 'Cannot normalize user emails because case-insensitive duplicates exist';
END IF;
END
$$;

UPDATE users
SET email = lower(trim(email))
WHERE email <> lower(trim(email));

CREATE UNIQUE INDEX IF NOT EXISTS idx_users_email_lower
    ON users (lower (email));
