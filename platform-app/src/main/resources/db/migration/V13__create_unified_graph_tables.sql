-- Unified Execution & Economy Graph (UEEG) tables
-- Stores the complete lifecycle graph for each render job

-- Graph metadata
CREATE TABLE unified_request_graph (
    graph_id VARCHAR(128) PRIMARY KEY,
    request_id VARCHAR(128) NOT NULL,
    tenant_id VARCHAR(64) NOT NULL,
    workspace_id VARCHAR(64),
    job_id VARCHAR(64),
    root_node_id VARCHAR(128),
    status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMP NOT NULL,
    completed_at TIMESTAMP,
    
    CONSTRAINT fk_ueeg_job FOREIGN KEY (job_id) REFERENCES render_job(id)
);

CREATE UNIQUE INDEX ix_ueeg_request_id ON unified_request_graph(request_id);
CREATE INDEX ix_ueeg_job_id ON unified_request_graph(job_id);
CREATE INDEX ix_ueeg_tenant_id ON unified_request_graph(tenant_id);
CREATE INDEX ix_ueeg_status ON unified_request_graph(status);

-- Graph nodes
CREATE TABLE unified_graph_node (
    node_id VARCHAR(128) PRIMARY KEY,
    graph_id VARCHAR(128) NOT NULL,
    type VARCHAR(64) NOT NULL,
    subsystem VARCHAR(64) NOT NULL,
    action VARCHAR(64) NOT NULL,
    status VARCHAR(32) NOT NULL,
    data TEXT,
    timestamp TIMESTAMP NOT NULL,
    
    CONSTRAINT fk_node_graph FOREIGN KEY (graph_id) REFERENCES unified_request_graph(graph_id)
);

CREATE INDEX ix_node_graph_id ON unified_graph_node(graph_id);
CREATE INDEX ix_node_type ON unified_graph_node(type);
CREATE INDEX ix_node_subsystem ON unified_graph_node(subsystem);

-- Graph edges (causal links)
CREATE TABLE unified_graph_edge (
    edge_id VARCHAR(128) PRIMARY KEY,
    graph_id VARCHAR(128) NOT NULL,
    source_node_id VARCHAR(128) NOT NULL,
    target_node_id VARCHAR(128) NOT NULL,
    edge_type VARCHAR(32) NOT NULL,
    timestamp TIMESTAMP NOT NULL,
    
    CONSTRAINT fk_edge_graph FOREIGN KEY (graph_id) REFERENCES unified_request_graph(graph_id),
    CONSTRAINT fk_edge_source FOREIGN KEY (source_node_id) REFERENCES unified_graph_node(node_id),
    CONSTRAINT fk_edge_target FOREIGN KEY (target_node_id) REFERENCES unified_graph_node(node_id)
);

CREATE INDEX ix_edge_graph_id ON unified_graph_edge(graph_id);
CREATE INDEX ix_edge_source ON unified_graph_edge(source_node_id);
CREATE INDEX ix_edge_target ON unified_graph_edge(target_node_id);
