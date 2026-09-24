alter table quota_usage
    alter column usage_value type numeric(38,18) using usage_value::numeric;
alter table quota_usage
    drop constraint if exists quota_usage_usage_value_check;
alter table quota_usage
    add constraint quota_usage_usage_value_check check (usage_value >= 0);
alter table quota_usage_operation
    alter column signed_delta type numeric(38,18) using signed_delta::numeric,
    alter column limit_value type numeric(38,18) using limit_value::numeric,
    alter column usage_before type numeric(38,18) using usage_before::numeric,
    alter column usage_after type numeric(38,18) using usage_after::numeric;
alter table quota_usage_operation
    drop constraint if exists quota_usage_operation_limit_value_check;
alter table quota_usage_operation
    add constraint quota_usage_operation_limit_value_check check (limit_value >= 0);
alter table platform_execution_admission
    add column if not exists quota_charged_units numeric(38,18);
update platform_execution_admission
   set quota_charged_units = quota_units
 where quota_charged = true
   and quota_charged_units is null;
alter table platform_execution_admission
    drop constraint if exists chk_platform_execution_admission_quota_charged_units;
alter table platform_execution_admission
    add constraint chk_platform_execution_admission_quota_charged_units
    check ((quota_charged = false and quota_charged_units is null)
        or (quota_charged = true and quota_charged_units is not null and quota_charged_units >= 0));
