-- P1-1A.4 / WORKER_READINESS_EVIDENCE_V1: authoritative, incarnation-bound worker readiness.
--
-- Environment readiness and sandbox readiness are two INDEPENDENT signals (owner ruling, option (i));
-- neither endorses the other, and neither is inferred from the other. A record is valid only while it
-- is not expired AND its host/runtime incarnations match the current registration.
--
-- Compatibility: additive only. Pre-existing rows are unaffected; no existing table is altered and no
-- historical migration is touched. Readers must treat an absent or expired row as fail-closed.

create table wf_host_readiness (
    physical_host_id varchar(128) not null,
    physical_host_incarnation_id varchar(128) not null,
    worker_runtime_id varchar(128) not null,
    worker_runtime_incarnation_id varchar(128) not null,
    environment_readiness varchar(32) not null check (
        environment_readiness in ('AVAILABLE','UNAVAILABLE','UNKNOWN')),
    sandbox_readiness varchar(32) not null check (
        sandbox_readiness in ('AVAILABLE','UNAVAILABLE','UNKNOWN')),
    snapshot_generation bigint not null check (snapshot_generation > 0),
    captured_at timestamptz not null,
    valid_until timestamptz not null,
    primary key (physical_host_id, physical_host_incarnation_id),
    foreign key (physical_host_id, physical_host_incarnation_id)
        references wf_host_registration (physical_host_id, physical_host_incarnation_id),
    check (valid_until > captured_at)
);
