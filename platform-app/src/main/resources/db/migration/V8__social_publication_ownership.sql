-- Durable publication ownership. Released rows and bindings are preserved.
ALTER TABLE social_post
    ADD COLUMN publication_attempt_id VARCHAR(36),
    ADD COLUMN dispatch_started_at TIMESTAMP,
    ADD COLUMN attempt_project_id VARCHAR(64),
    ADD COLUMN attempt_account_id VARCHAR(64),
    ADD COLUMN attempt_binding_version BIGINT;
-- Historical failed/in-flight outcomes cannot safely be redispatched.
UPDATE social_post SET status='UNRESOLVED', error_code='LEGACY_IN_FLIGHT',
    error_message='Historical publication outcome requires reconciliation'
WHERE status IN ('PUBLISHING','FAILED');

ALTER TABLE social_post ADD CONSTRAINT ck_social_dispatch_owned CHECK (
    dispatch_started_at IS NULL OR publication_attempt_id IS NOT NULL);
