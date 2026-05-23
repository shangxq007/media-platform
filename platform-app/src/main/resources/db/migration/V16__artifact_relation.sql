-- Artifact provenance relations (catalog outputs)
CREATE TABLE IF NOT EXISTS artifact_relation (
    id varchar(64) PRIMARY KEY,
    source_artifact_id varchar(64) NOT NULL,
    target_artifact_id varchar(64) NOT NULL,
    relation_type varchar(64) NOT NULL,
    created_at timestamp NOT NULL
);

CREATE INDEX IF NOT EXISTS ix_artifact_relation_source ON artifact_relation (source_artifact_id);
CREATE INDEX IF NOT EXISTS ix_artifact_relation_target ON artifact_relation (target_artifact_id);
