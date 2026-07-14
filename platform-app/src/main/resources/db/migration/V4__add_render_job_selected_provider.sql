-- Add selected_provider column to render_job for Provider selection persistence
ALTER TABLE render_job ADD COLUMN selected_provider VARCHAR(128);
