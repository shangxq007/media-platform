alter table platform_execution_admission alter column quota_units type numeric(38,18) using quota_units::numeric;
alter table platform_execution_admission add column if not exists plan_fingerprint varchar(256);
alter table platform_execution_admission add column if not exists plan_facts jsonb;
alter table platform_execution_admission add column if not exists quota_unit varchar(64) not null default 'quota-unit';
alter table platform_execution_admission add column if not exists quota_claimed boolean not null default false;
update platform_execution_admission set plan_fingerprint = 'legacy:' || execution_id where plan_fingerprint is null;
alter table platform_execution_admission add constraint chk_platform_execution_admission_plan_fingerprint
    check (plan_fingerprint is not null and length(plan_fingerprint) > 0);
