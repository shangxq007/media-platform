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
