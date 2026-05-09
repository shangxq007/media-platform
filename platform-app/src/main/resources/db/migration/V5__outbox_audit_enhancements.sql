-- Enhance outbox_events with retry tracking, dead-letter support, and idempotency
-- Note: H2-compatible syntax (no IF EXISTS on ALTER TABLE)
alter table outbox_events add column if not exists retry_count int not null default 0;
alter table outbox_events add column if not exists next_attempt_at timestamp;
alter table outbox_events add column if not exists idempotency_key varchar(255);

-- Enhance audit_records with category support
alter table audit_records add column if not exists category varchar(50);
