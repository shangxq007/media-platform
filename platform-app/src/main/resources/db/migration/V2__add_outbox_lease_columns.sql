-- Add lease and retry columns to outbox_events table
-- These columns support distributed locking and retry logic in OutboxEventDispatcher

ALTER TABLE outbox_events
    ADD COLUMN IF NOT EXISTS locked_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS locked_by VARCHAR(100),
    ADD COLUMN IF NOT EXISTS max_retries INTEGER NOT NULL DEFAULT 3;

-- Add index for lease acquisition queries
CREATE INDEX IF NOT EXISTS ix_outbox_events_locked_at ON outbox_events (locked_at) WHERE locked_at IS NOT NULL;
