-- V21 — Marketplace publication subject identity: Media asset id → canonical Artifact identity.
--
-- Decision: TYPED_ARTIFACT_MARKETPLACE_DECISION_001 (Path 1b) — only the subject identity moves to
-- the Artifact authority. Listing/review facts, review threads, decisions, command ledger and the
-- admission/legacy_snapshot invariants are unchanged.
--
-- Fail closed: the migration aborts when any listing has no canonical Artifact identity derived
-- from the V18/V19 convergence mapping (artifact_media_details.legacy_media_asset_id, unique).
-- No MediaAsset authority is re-admitted and no row is dropped or rewritten beyond the subject id.
do $v21$
declare
    unmapped bigint;
begin
    if exists (select 1 from information_schema.columns
               where table_name = 'marketplace_listing' and column_name = 'asset_id') then
        select count(*) into unmapped
        from marketplace_listing l
        where not exists (
            select 1 from artifact_media_details d where d.legacy_media_asset_id = l.asset_id);
        if unmapped > 0 then
            raise exception 'MARKETPLACE_SUBJECT_UNMAPPED: % marketplace listing(s) lack an Artifact identity', unmapped;
        end if;

        -- The baseline FK/unique bound the marketplace to the retired media table; both are replaced.
        alter table marketplace_listing drop constraint if exists fk_ml_asset;
        alter table marketplace_listing drop constraint if exists uq_ml_asset;
        alter table marketplace_listing rename column asset_id to artifact_id;

        -- The admitted-identity immutability fence (V7) must follow the renamed subject column, and the
        -- backfill below rewrites the subject of admitted rows, so it runs with the fence disabled.
        alter table marketplace_listing disable trigger marketplace_listing_revision;
        create or replace function enforce_marketplace_listing_revision() returns trigger language plpgsql as $v7f$
        BEGIN
            IF OLD.admitted_at IS NOT NULL THEN
                IF NEW.admitted_at IS DISTINCT FROM OLD.admitted_at
                    OR NEW.artifact_id IS DISTINCT FROM OLD.artifact_id
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
        END $v7f$;
        update marketplace_listing l
           set artifact_id = d.artifact_id
          from artifact_media_details d
         where d.legacy_media_asset_id = l.artifact_id;
        alter table marketplace_listing enable trigger marketplace_listing_revision;

        -- The legacy listing version column duplicated the subject version; the Artifact pin is 64 hex.
        alter table marketplace_listing alter column version type varchar(64);
        alter table marketplace_listing add constraint uq_ml_artifact unique (artifact_id);
        alter table marketplace_listing add constraint fk_ml_artifact foreign key (artifact_id) references artifact(id);

        execute 'comment on column marketplace_listing.artifact_id is '
            '''Canonical Artifact subject identity; Media asset id before V21''';
        execute 'comment on column marketplace_listing.subject_version is '
            '''Pinned Artifact content digest (SHA-256); Media version before V21''';
        execute 'comment on column marketplace_review.subject_version is '
            '''Pinned Artifact content digest (SHA-256); Media version before V21''';
    end if;
end $v21$;
