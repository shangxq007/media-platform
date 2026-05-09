-- =============================================================================
-- V9: Outbox Reliability Enhancements
--
-- Purpose: Add missing columns for full outbox reliability:
--   - max_retries: per-event configurable max retry count (default 3)
--   - last_error_code: machine-readable error code from last failure
--   - last_error_message: human-readable error message from last failure
--   - locked_at: timestamp when event was locked for processing (concurrency guard)
--   - locked_by: identifier of the processor that locked the event
-- =============================================================================

alter table outbox_events add column if not exists max_retries int not null default 3;
alter table outbox_events add column if not exists last_error_code varchar(100);
alter table outbox_events add column if not exists last_error_message text;
alter table outbox_events add column if not exists locked_at timestamp;
alter table outbox_events add column if not exists locked_by varchar(255);

-- Index for finding expired locks (recovery after crash)
create index if not exists ix_outbox_events_locked_at on outbox_events(locked_at);

-- Composite index for efficient dispatch queries (FAILED + next_attempt_at)
create index if not exists ix_outbox_events_status_next_attempt on outbox_events(status, next_attempt_at);
