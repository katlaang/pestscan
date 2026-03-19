create table if not exists public.scouting_session_species
(
    session_id
    uuid
    not
    null,
    species_code
    varchar
(
    50
)
    );

create index if not exists idx_scouting_session_species_session
    on public.scouting_session_species (session_id);
