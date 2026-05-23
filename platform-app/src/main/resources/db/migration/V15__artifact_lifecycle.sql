-- Artifact lifecycle: status + tombstone timestamp
ALTER TABLE IF EXISTS artifact ADD COLUMN IF NOT EXISTS status varchar(32) NOT NULL DEFAULT 'ACTIVE';
ALTER TABLE IF EXISTS artifact ADD COLUMN IF NOT EXISTS tombstoned_at timestamp;

CREATE INDEX IF NOT EXISTS ix_artifact_status ON artifact (status);
