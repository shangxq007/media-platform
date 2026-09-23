-- Preserve V11 drafts; published snapshots have independent immutable identities.
CREATE TABLE composition_version (
    tenant_id varchar(255) NOT NULL,
    workspace_id varchar(255) NOT NULL,
    kind varchar(32) NOT NULL CHECK (kind IN ('WORKFLOW', 'APPLICATION')),
    composition_id varchar(255) NOT NULL,
    version varchar(64) NOT NULL,
    definition jsonb NOT NULL,
    published_at timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY (tenant_id, workspace_id, kind, composition_id, version)
);

CREATE FUNCTION composition_version_immutable() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    RAISE EXCEPTION 'Published composition versions are immutable';
END;
$$;
CREATE TRIGGER composition_version_no_mutation BEFORE UPDATE OR DELETE ON composition_version
    FOR EACH ROW EXECUTE FUNCTION composition_version_immutable();
