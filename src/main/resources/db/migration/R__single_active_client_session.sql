ALTER TABLE users
    ADD COLUMN IF NOT EXISTS active_client_session_id VARCHAR (128);

ALTER TABLE users
    ADD COLUMN IF NOT EXISTS active_session_started_at TIMESTAMP WITH TIME ZONE;

CREATE INDEX IF NOT EXISTS idx_users_active_client_session_id ON users (active_client_session_id);
