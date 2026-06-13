-- Artifact DAG tables for versioned, immutable artifact graph storage

CREATE TABLE artifact_node (
    id VARCHAR(128) PRIMARY KEY,
    job_id VARCHAR(64) NOT NULL,
    type VARCHAR(32) NOT NULL,
    uri TEXT NOT NULL,
    parent_artifact_ids TEXT,
    version INT NOT NULL DEFAULT 1,
    hash VARCHAR(128),
    metadata TEXT,
    created_at TIMESTAMP NOT NULL,
    
    CONSTRAINT fk_artifact_node_job FOREIGN KEY (job_id) REFERENCES render_job(id)
);

CREATE INDEX ix_artifact_node_job_id ON artifact_node(job_id);
CREATE INDEX ix_artifact_node_hash ON artifact_node(hash);
CREATE INDEX ix_artifact_node_type ON artifact_node(type);

CREATE TABLE artifact_graph (
    graph_id VARCHAR(128) PRIMARY KEY,
    job_id VARCHAR(64) NOT NULL,
    root_artifact_id VARCHAR(128),
    version INT NOT NULL DEFAULT 1,
    created_at TIMESTAMP NOT NULL,
    
    CONSTRAINT fk_artifact_graph_job FOREIGN KEY (job_id) REFERENCES render_job(id),
    CONSTRAINT fk_artifact_graph_root FOREIGN KEY (root_artifact_id) REFERENCES artifact_node(id)
);

CREATE INDEX ix_artifact_graph_job_id ON artifact_graph(job_id);
