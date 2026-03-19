create table if not exists scouting_observation_drafts
(
    id
    uuid
    primary
    key,
    created_at
    timestamp
    not
    null,
    updated_at
    timestamp,
    deleted
    boolean
    not
    null
    default
    false,
    deleted_at
    timestamp,
    sync_status
    varchar
(
    32
) not null,
    version bigint,
    session_id uuid not null references scouting_sessions
(
    id
) on delete cascade,
    session_target_id uuid not null references scouting_session_targets
(
    id
)
  on delete cascade,
    species_code varchar
(
    50
),
    custom_species_id uuid references custom_species_definitions
(
    id
),
    species_identifier varchar
(
    128
) not null,
    bay_index integer not null,
    bay_label varchar
(
    255
),
    bench_index integer not null,
    bench_label varchar
(
    255
),
    spot_index integer not null,
    count_value integer not null,
    notes varchar
(
    2000
),
    client_request_id uuid unique,
    constraint uk_session_draft_cell_species
    unique
(
    session_id,
    session_target_id,
    bay_index,
    bench_index,
    spot_index,
    species_identifier
)
    );

create index if not exists idx_scouting_observation_drafts_session
    on scouting_observation_drafts (session_id);

create index if not exists idx_scouting_observation_drafts_custom_species
    on scouting_observation_drafts (custom_species_id);
