alter table if exists scouting_observations
    add column if not exists local_observation_id varchar (64);

alter table if exists scouting_observations
    add column if not exists observation_type varchar (32);

alter table if exists scouting_observations
    add column if not exists lifecycle_status varchar (32);

alter table if exists scouting_observations
    add column if not exists latitude numeric (10, 7);

alter table if exists scouting_observations
    add column if not exists longitude numeric (10, 7);

alter table if exists scouting_observations
    add column if not exists geometry varchar (4000);

update scouting_observations so
set observation_type = case
                           when cs.category = 'PEST' then 'SUSPECTED_PEST'
                           when cs.category = 'DISEASE' then 'DISEASE_SYMPTOM'
                           else 'OTHER'
    end from custom_species_definitions cs
where so.observation_type is null
  and so.custom_species_id = cs.id;

update scouting_observations
set observation_type = case
                           when species_code in ('THRIPS', 'RED_SPIDER_MITE', 'WHITEFLIES', 'MEALYBUGS',
                                                 'CATERPILLARS', 'FALSE_CODLING_MOTH', 'PEST_OTHER')
                               then 'SUSPECTED_PEST'
                           when species_code in ('DOWNY_MILDEW', 'POWDERY_MILDEW', 'BOTRYTIS', 'VERTICILLIUM',
                                                 'BACTERIAL_WILT', 'DISEASE_OTHER')
                               then 'DISEASE_SYMPTOM'
                           else 'OTHER'
    end
where observation_type is null;

update scouting_observations
set lifecycle_status = case
                           when deleted then 'CLOSED'
                           else 'SYNCED'
    end
where lifecycle_status is null;

update scouting_observations
set local_observation_id = 'OBS-' || upper(substr(replace(id::text, '-', ''), 1, 12))
where local_observation_id is null
   or btrim(local_observation_id) = '';

alter table if exists scouting_observations
alter
column observation_type set not null;

alter table if exists scouting_observations
alter
column lifecycle_status set not null;

create index if not exists idx_scouting_observations_local_id
    on scouting_observations (local_observation_id);

alter table if exists scouting_observation_drafts
    add column if not exists local_observation_id varchar (64);

alter table if exists scouting_observation_drafts
    add column if not exists observation_type varchar (32);

alter table if exists scouting_observation_drafts
    add column if not exists lifecycle_status varchar (32);

alter table if exists scouting_observation_drafts
    add column if not exists latitude numeric (10, 7);

alter table if exists scouting_observation_drafts
    add column if not exists longitude numeric (10, 7);

alter table if exists scouting_observation_drafts
    add column if not exists geometry varchar (4000);

update scouting_observation_drafts sod
set observation_type = case
                           when cs.category = 'PEST' then 'SUSPECTED_PEST'
                           when cs.category = 'DISEASE' then 'DISEASE_SYMPTOM'
                           else 'OTHER'
    end from custom_species_definitions cs
where sod.observation_type is null
  and sod.custom_species_id = cs.id;

update scouting_observation_drafts
set observation_type = case
                           when species_code in ('THRIPS', 'RED_SPIDER_MITE', 'WHITEFLIES', 'MEALYBUGS',
                                                 'CATERPILLARS', 'FALSE_CODLING_MOTH', 'PEST_OTHER')
                               then 'SUSPECTED_PEST'
                           when species_code in ('DOWNY_MILDEW', 'POWDERY_MILDEW', 'BOTRYTIS', 'VERTICILLIUM',
                                                 'BACTERIAL_WILT', 'DISEASE_OTHER')
                               then 'DISEASE_SYMPTOM'
                           else 'OTHER'
    end
where observation_type is null;

update scouting_observation_drafts
set lifecycle_status = case
                           when deleted then 'CLOSED'
                           else 'DRAFT'
    end
where lifecycle_status is null;

update scouting_observation_drafts
set local_observation_id = 'OBS-' || upper(substr(replace(id::text, '-', ''), 1, 12))
where local_observation_id is null
   or btrim(local_observation_id) = '';

alter table if exists scouting_observation_drafts
alter
column observation_type set not null;

alter table if exists scouting_observation_drafts
alter
column lifecycle_status set not null;

create index if not exists idx_scouting_observation_drafts_local_id
    on scouting_observation_drafts (local_observation_id);
