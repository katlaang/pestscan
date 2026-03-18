ALTER TABLE users
    ADD COLUMN IF NOT EXISTS session_valid_after TIMESTAMP WITH TIME ZONE;

CREATE INDEX IF NOT EXISTS idx_users_session_valid_after ON users (session_valid_after);
