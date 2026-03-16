alter table farms
    add column license_type varchar(16) not null default 'PAID';
alter table farms
    add column license_start_date date;
alter table farms
    add column license_extension_months integer not null default 0;
alter table farms
    add column license_expiry_notification_sent_at timestamp;

alter table farm_license_history
    add column license_type varchar(16);
alter table farm_license_history
    add column license_start_date date;
alter table farm_license_history
    add column license_extension_months integer;
