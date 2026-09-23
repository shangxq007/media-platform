-- Immutable OpenFeature evaluation snapshots used for durable workflow replay.
-- Append only: a snapshot id is content addressed and may never be replaced.
create table if not exists feature_flag_evaluation_snapshot (
    snapshot_id varchar(128) primary key,
    provider_revision varchar(128) not null,
    tenant_id varchar(200) not null,
    workspace_id varchar(200),
    captured_at timestamptz not null,
    decisions_json jsonb not null
);

create index if not exists ix_feature_flag_snapshot_scope
    on feature_flag_evaluation_snapshot(tenant_id, workspace_id, captured_at);

create or replace function reject_feature_flag_snapshot_mutation()
returns trigger language plpgsql as $$
begin
    raise exception 'feature flag evaluation snapshots are immutable';
end;
$$;

drop trigger if exists trg_feature_flag_snapshot_immutable on feature_flag_evaluation_snapshot;
create trigger trg_feature_flag_snapshot_immutable
before update or delete on feature_flag_evaluation_snapshot
for each row execute function reject_feature_flag_snapshot_mutation();
