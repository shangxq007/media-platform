-- Marketplace is the sole active publication/review authority. Existing rows are retained.
ALTER TABLE marketplace_listing ADD COLUMN workspace_id varchar(64);
ALTER TABLE marketplace_listing ADD COLUMN subject_version varchar(64);
ALTER TABLE marketplace_listing ADD COLUMN aggregate_version bigint NOT NULL DEFAULT 0;
ALTER TABLE marketplace_listing ADD COLUMN created_by varchar(128);
ALTER TABLE marketplace_listing ADD COLUMN updated_by varchar(128);
ALTER TABLE marketplace_listing ADD COLUMN admitted_at timestamptz;
ALTER TABLE marketplace_listing ADD COLUMN published_at timestamptz;
ALTER TABLE marketplace_listing ADD COLUMN legacy_snapshot jsonb;
UPDATE marketplace_listing SET legacy_snapshot=to_jsonb(marketplace_listing)-'legacy_snapshot';
ALTER TABLE marketplace_listing ADD CONSTRAINT marketplace_admitted_scope CHECK
    (admitted_at IS NULL OR (tenant_id IS NOT NULL AND project_id IS NOT NULL AND workspace_id IS NOT NULL
    AND subject_version IS NOT NULL AND created_by IS NOT NULL AND aggregate_version>0));
ALTER TABLE marketplace_listing ADD CONSTRAINT marketplace_workspace_fk FOREIGN KEY(workspace_id) REFERENCES workspace(id);

CREATE TABLE marketplace_review (
    id varchar(64) PRIMARY KEY,
    listing_id varchar(64) NOT NULL REFERENCES marketplace_listing(id),
    tenant_id varchar(64) NOT NULL,
    workspace_id varchar(64) NOT NULL,
    project_id varchar(64) NOT NULL,
    subject_version varchar(64) NOT NULL,
    author_id varchar(128) NOT NULL,
    title varchar(256) NOT NULL,
    description text,
    status varchar(32) NOT NULL CHECK(status IN ('OPEN','APPROVED','CHANGES_REQUESTED','REJECTED')),
    aggregate_version bigint NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now()
);
CREATE TABLE marketplace_review_decision (
    id varchar(64) PRIMARY KEY,
    review_id varchar(64) NOT NULL REFERENCES marketplace_review(id),
    actor_json text NOT NULL,
    decision varchar(32) NOT NULL,
    aggregate_version bigint NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now()
);
CREATE TABLE marketplace_review_thread (
    id varchar(64) PRIMARY KEY,
    review_id varchar(64) NOT NULL REFERENCES marketplace_review(id),
    resolved boolean NOT NULL DEFAULT false,
    resolved_by varchar(128)
);
CREATE TABLE marketplace_review_comment (
    id varchar(64) PRIMARY KEY,
    review_id varchar(64) NOT NULL REFERENCES marketplace_review(id),
    thread_id varchar(64) NOT NULL REFERENCES marketplace_review_thread(id),
    author_id varchar(128) NOT NULL,
    content text NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now()
);
CREATE TABLE marketplace_command (
    tenant_id varchar(64) NOT NULL,
    command_id varchar(128) NOT NULL,
    request_digest varchar(64) NOT NULL,
    actor_json text NOT NULL,
    result_json text NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY(tenant_id,command_id)
);
-- Permission definitions do not grant any role access. Identity role assignment remains authoritative.
INSERT INTO permission(id,permission_key,name,resource_type,created_at) VALUES
 ('perm-marketplace-manage','marketplace.manage','Manage Marketplace listing','PROJECT',now()),
 ('perm-marketplace-review','marketplace.review','Decide Marketplace review','PROJECT',now()),
 ('perm-marketplace-publish','marketplace.publish','Publish Marketplace listing','PROJECT',now());
-- Historical ASSET timeline_review/thread/comment/decision rows remain byte-for-byte evidence.
-- Marketplace never interprets those approvals as admission; Timeline live queries exclude them.

-- Retained coordination history is not permission to execute the retired two-handler path.
-- Payloads and metadata remain untouched for operator inspection; no replacement work is invented.
UPDATE platform_task SET status='FAILED',error_message='MARKETPLACE_OWNER_MIGRATION: retired preparation intent; re-admit through Marketplace',updated_at=now()
WHERE job_id IN (SELECT id FROM platform_job WHERE job_type='MARKETPLACE_PREPARE')
AND status IN ('PENDING','RUNNING','RETRY');
UPDATE platform_job SET status='FAILED',updated_at=now()
WHERE job_type='MARKETPLACE_PREPARE' AND status IN ('PENDING','RUNNING','RETRY');
