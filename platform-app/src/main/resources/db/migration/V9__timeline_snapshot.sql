-- Timeline snapshots: editor / OTIO JSON persisted for render jobs
CREATE TABLE IF NOT EXISTS timeline_snapshot (
    id              varchar(64)  PRIMARY KEY,
    project_id      varchar(64)  NOT NULL,
    tenant_id       varchar(64),
    payload_json    clob         NOT NULL,
    schema_version  varchar(32)  DEFAULT '2.0.0',
    created_at      timestamp    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_timeline_snapshot_project ON timeline_snapshot (project_id);
