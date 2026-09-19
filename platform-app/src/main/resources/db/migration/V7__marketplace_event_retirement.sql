-- These v1 Asset envelopes lack exact Marketplace listing/review/version and actor facts.
-- Preserve original payloads, versions and keys; native Outbox dead-letter is the explicit
-- reconciliation state. Do not manufacture typed approvals from historical generic records.
UPDATE outbox_events SET status='DEAD_LETTER',
    last_error_code='RETIRED_MARKETPLACE_EVENT_SCHEMA',
    last_error_message='Generic Asset publication schema retired. Inspect original facts and explicitly re-admit via Marketplace; no automatic reconstruction.',
    locked_at=null,locked_by=null
WHERE event_type IN ('asset.submitted.review','asset.approved','asset.published','asset.archived')
AND status IN ('PENDING','FAILED','PROCESSING');

-- Fence late/unversioned old listing writers without keeping a compatibility runtime.
-- Existing historical rows remain inactive until explicit re-admission. New owner mutations
-- preserve subject/scope/creator and advance the single listing version exactly once.
CREATE FUNCTION enforce_marketplace_listing_revision() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF OLD.admitted_at IS NOT NULL THEN
        IF NEW.admitted_at IS DISTINCT FROM OLD.admitted_at
            OR NEW.asset_id IS DISTINCT FROM OLD.asset_id
            OR NEW.subject_version IS DISTINCT FROM OLD.subject_version
            OR NEW.tenant_id IS DISTINCT FROM OLD.tenant_id
            OR NEW.project_id IS DISTINCT FROM OLD.project_id
            OR NEW.workspace_id IS DISTINCT FROM OLD.workspace_id
            OR NEW.created_by IS DISTINCT FROM OLD.created_by
            OR NEW.legacy_snapshot IS DISTINCT FROM OLD.legacy_snapshot
            OR NEW.aggregate_version <> OLD.aggregate_version + 1 THEN
            RAISE EXCEPTION 'Marketplace admitted identity is immutable and every mutation must advance its version';
        END IF;
    END IF;
    RETURN NEW;
END $$;
CREATE TRIGGER marketplace_listing_revision BEFORE UPDATE ON marketplace_listing
FOR EACH ROW EXECUTE FUNCTION enforce_marketplace_listing_revision();
ALTER TABLE marketplace_listing ADD CONSTRAINT marketplace_admitted_status CHECK
    (admitted_at IS NULL OR status IN ('DRAFT','READY','PUBLISHED','ARCHIVED'));
