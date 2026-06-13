-- Add trace_id column to render_job for provider runtime observability
ALTER TABLE render_job ADD COLUMN trace_id VARCHAR(128);

CREATE INDEX ix_render_job_trace_id ON render_job(trace_id);
