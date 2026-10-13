-- A-2: authoritative Native-Pull admission evidence.
--
-- Adds the durable physical host descriptor and the observed resource usage that
-- RequestWorkValidationContext requires but which the pre-V3 schema did not persist
-- (wf_host_resource_snapshot carried static capacity and safety headroom only; no host
-- descriptor existed).
--
-- Compatibility: pre-V3 snapshot rows keep NULL observed-usage columns. Readers MUST treat a
-- missing observation as unavailable (fail-closed) and MUST NOT default it to zero usage.
-- No readiness evidence is added here.

create table wf_physical_host_descriptor (
    physical_host_id varchar(128) primary key,
    host_location varchar(256) not null,
    trust_zone_id varchar(256) not null,
    captured_at timestamptz not null
);

create table wf_physical_host_descriptor_device (
    physical_host_id varchar(128) not null,
    device_id varchar(128) not null,
    device_kind varchar(64) not null check (
        device_kind in ('CPU','GPU','MEDIA_ACCELERATOR','OTHER_ACCELERATOR')),
    device_vendor varchar(256) not null,
    device_model varchar(256) not null,
    primary key (physical_host_id, device_id),
    foreign key (physical_host_id)
        references wf_physical_host_descriptor (physical_host_id) on delete cascade
);

alter table wf_host_resource_snapshot
    add column observed_cpu_utilization_ratio double precision
        check (observed_cpu_utilization_ratio >= 0 and observed_cpu_utilization_ratio <= 1),
    add column observed_memory_used_bytes bigint
        check (observed_memory_used_bytes >= 0),
    add column observed_temporary_storage_used_bytes bigint
        check (observed_temporary_storage_used_bytes >= 0);

alter table wf_host_resource_snapshot_device
    add column observed_compute_utilization_ratio double precision
        check (observed_compute_utilization_ratio >= 0 and observed_compute_utilization_ratio <= 1),
    add column observed_vram_used_bytes bigint
        check (observed_vram_used_bytes >= 0),
    add column observed_encoder_utilization_ratio double precision
        check (observed_encoder_utilization_ratio >= 0 and observed_encoder_utilization_ratio <= 1),
    add column observed_decoder_utilization_ratio double precision
        check (observed_decoder_utilization_ratio >= 0 and observed_decoder_utilization_ratio <= 1);
