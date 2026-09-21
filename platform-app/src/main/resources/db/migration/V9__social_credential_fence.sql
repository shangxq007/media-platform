-- Non-secret generation for the existing encrypted credential authority.
-- Preserve V1-V8 and all existing rows. No credential material is copied.
ALTER TABLE social_connected_platform
    ADD COLUMN credential_revision BIGINT NOT NULL DEFAULT 1 CHECK (credential_revision > 0);
ALTER TABLE social_post
    ADD COLUMN attempt_credential_revision BIGINT,
    ADD COLUMN attempt_credential_expires_at TIMESTAMP;

-- All legal SQL writers share this generation, including rotation without updated_at changes.
-- A caller cannot reset/forge the generation; same-value writes do not advance it.
CREATE FUNCTION advance_social_credential_revision() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF NEW.access_token_encrypted IS DISTINCT FROM OLD.access_token_encrypted
       OR NEW.refresh_token_encrypted IS DISTINCT FROM OLD.refresh_token_encrypted
       OR NEW.token_expires_at IS DISTINCT FROM OLD.token_expires_at
       OR NEW.status IS DISTINCT FROM OLD.status THEN
        NEW.credential_revision := OLD.credential_revision + 1;
    ELSE
        NEW.credential_revision := OLD.credential_revision;
    END IF;
    RETURN NEW;
END $$;
CREATE TRIGGER social_connected_platform_credential_revision
    BEFORE UPDATE ON social_connected_platform
    FOR EACH ROW EXECUTE FUNCTION advance_social_credential_revision();
