-- Referential integrity for artifact provenance graph
ALTER TABLE IF EXISTS artifact_relation
    ADD CONSTRAINT fk_artifact_relation_source
    FOREIGN KEY (source_artifact_id) REFERENCES artifact (id) ON DELETE RESTRICT;

ALTER TABLE IF EXISTS artifact_relation
    ADD CONSTRAINT fk_artifact_relation_target
    FOREIGN KEY (target_artifact_id) REFERENCES artifact (id) ON DELETE RESTRICT;
