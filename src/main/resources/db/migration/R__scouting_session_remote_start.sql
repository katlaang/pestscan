ALTER TABLE scouting_sessions
    ADD COLUMN IF NOT EXISTS remote_start_requested_at TIMESTAMP WITH TIME ZONE;

ALTER TABLE scouting_sessions
    ADD COLUMN IF NOT EXISTS remote_start_requested_by_user_id UUID;

ALTER TABLE scouting_sessions
    ADD COLUMN IF NOT EXISTS remote_start_requested_by_name VARCHAR (255);

ALTER TABLE session_audit_events
DROP
CONSTRAINT IF EXISTS chk_session_audit_action;

ALTER TABLE session_audit_events
    ADD CONSTRAINT chk_session_audit_action
        CHECK (
    action IN (
    'SESSION_CREATED',
    'SESSION_VIEWED',
    'SESSION_EDITED',
    'SESSION_REMOTE_START_REQUESTED',
    'SESSION_STARTED',
    'SESSION_SUBMITTED',
    'SESSION_COMPLETED',
    'SESSION_REOPENED',
    'SESSION_MARKED_INCOMPLETE'
    )
    );
