-- System Canonical Model tables
-- Replaces all subsystem graphs with single canonical event system

-- Canonical graph metadata
CREATE TABLE system_canonical_graph (
    graph_id VARCHAR(128) PRIMARY KEY,
    job_id VARCHAR(64) NOT NULL,
    tenant_id VARCHAR(64),
    workspace_id VARCHAR(64),
    status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMP NOT NULL,
    completed_at TIMESTAMP,
    
    CONSTRAINT fk_canonical_job FOREIGN KEY (job_id) REFERENCES render_job(id)
);

CREATE UNIQUE INDEX ix_canonical_job_id ON system_canonical_graph(job_id);
CREATE INDEX ix_canonical_tenant_id ON system_canonical_graph(tenant_id);
CREATE INDEX ix_canonical_status ON system_canonical_graph(status);

-- Canonical events (single source of truth)
CREATE TABLE system_canonical_event (
    event_id VARCHAR(128) PRIMARY KEY,
    graph_id VARCHAR(128) NOT NULL,
    event_type VARCHAR(64) NOT NULL,
    timestamp TIMESTAMP NOT NULL,
    tenant_id VARCHAR(64),
    workspace_id VARCHAR(64),
    job_id VARCHAR(64),
    source_system VARCHAR(64) NOT NULL,
    sequence_number INT NOT NULL,
    payload TEXT,
    
    CONSTRAINT fk_event_graph FOREIGN KEY (graph_id) REFERENCES system_canonical_graph(graph_id)
);

CREATE INDEX ix_event_graph_id ON system_canonical_event(graph_id);
CREATE INDEX ix_event_job_id ON system_canonical_event(job_id);
CREATE INDEX ix_event_type ON system_canonical_event(event_type);
CREATE INDEX ix_event_source ON system_canonical_event(source_system);
CREATE INDEX ix_event_sequence ON system_canonical_event(graph_id, sequence_number);

-- Canonical edges (causal relationships)
CREATE TABLE system_canonical_edge (
    edge_id VARCHAR(128) PRIMARY KEY,
    graph_id VARCHAR(128) NOT NULL,
    source_event_id VARCHAR(128) NOT NULL,
    target_event_id VARCHAR(128) NOT NULL,
    edge_type VARCHAR(32) NOT NULL,
    timestamp TIMESTAMP NOT NULL,
    
    CONSTRAINT fk_edge_graph FOREIGN KEY (graph_id) REFERENCES system_canonical_graph(graph_id),
    CONSTRAINT fk_edge_source FOREIGN KEY (source_event_id) REFERENCES system_canonical_event(event_id),
    CONSTRAINT fk_edge_target FOREIGN KEY (target_event_id) REFERENCES system_canonical_event(event_id)
);

CREATE INDEX ix_edge_graph_id ON system_canonical_edge(graph_id);
CREATE INDEX ix_edge_source ON system_canonical_edge(source_event_id);
CREATE INDEX ix_edge_target ON system_canonical_edge(target_event_id);
