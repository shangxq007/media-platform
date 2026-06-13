-- Product Layer tables for workspace, project, template, and AI assistant features

-- Workspace table
CREATE TABLE workspace (
    id VARCHAR(64) PRIMARY KEY,
    name VARCHAR(256) NOT NULL,
    description TEXT,
    owner_id VARCHAR(64) NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL
);

CREATE INDEX ix_workspace_owner_id ON workspace(owner_id);
CREATE INDEX ix_workspace_status ON workspace(status);

-- Workspace members table
CREATE TABLE workspace_member (
    id VARCHAR(64) PRIMARY KEY,
    workspace_id VARCHAR(64) NOT NULL,
    user_id VARCHAR(64) NOT NULL,
    role VARCHAR(32) NOT NULL,
    joined_at TIMESTAMP NOT NULL,
    last_active_at TIMESTAMP,
    CONSTRAINT fk_workspace_member_workspace FOREIGN KEY (workspace_id) REFERENCES workspace(id),
    CONSTRAINT uk_workspace_user UNIQUE (workspace_id, user_id)
);

CREATE INDEX ix_workspace_member_user ON workspace_member(user_id);

-- Project table
CREATE TABLE project (
    id VARCHAR(64) PRIMARY KEY,
    workspace_id VARCHAR(64) NOT NULL,
    name VARCHAR(256) NOT NULL,
    description TEXT,
    owner_id VARCHAR(64) NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'DRAFT',
    timeline_snapshot_id VARCHAR(128),
    template_id VARCHAR(64),
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    CONSTRAINT fk_project_workspace FOREIGN KEY (workspace_id) REFERENCES workspace(id)
);

CREATE INDEX ix_project_workspace ON project(workspace_id);
CREATE INDEX ix_project_owner ON project(owner_id);
CREATE INDEX ix_project_status ON project(status);

-- Timeline template table
CREATE TABLE timeline_template (
    id VARCHAR(64) PRIMARY KEY,
    workspace_id VARCHAR(64),
    name VARCHAR(256) NOT NULL,
    description TEXT,
    category VARCHAR(64),
    creator_id VARCHAR(64) NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'DRAFT',
    timeline_json TEXT,
    effect_keys TEXT,
    metadata TEXT,
    version INT NOT NULL DEFAULT 1,
    parent_template_id VARCHAR(64),
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    CONSTRAINT fk_template_workspace FOREIGN KEY (workspace_id) REFERENCES workspace(id)
);

CREATE INDEX ix_template_workspace ON timeline_template(workspace_id);
CREATE INDEX ix_template_category ON timeline_template(category);
CREATE INDEX ix_template_status ON timeline_template(status);

-- Render preset table
CREATE TABLE render_preset (
    id VARCHAR(64) PRIMARY KEY,
    workspace_id VARCHAR(64),
    name VARCHAR(256) NOT NULL,
    description TEXT,
    creator_id VARCHAR(64) NOT NULL,
    format VARCHAR(32),
    resolution VARCHAR(32),
    profile VARCHAR(64),
    settings TEXT,
    is_default BOOLEAN DEFAULT FALSE,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    CONSTRAINT fk_preset_workspace FOREIGN KEY (workspace_id) REFERENCES workspace(id)
);

CREATE INDEX ix_preset_workspace ON render_preset(workspace_id);

-- Asset library table
CREATE TABLE asset_library (
    id VARCHAR(64) PRIMARY KEY,
    workspace_id VARCHAR(64) NOT NULL,
    name VARCHAR(256) NOT NULL,
    description TEXT,
    type VARCHAR(32) NOT NULL DEFAULT 'GENERAL',
    asset_count INT DEFAULT 0,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    CONSTRAINT fk_library_workspace FOREIGN KEY (workspace_id) REFERENCES workspace(id)
);

CREATE INDEX ix_library_workspace ON asset_library(workspace_id);

-- Render history table
CREATE TABLE render_history (
    id VARCHAR(64) PRIMARY KEY,
    workspace_id VARCHAR(64) NOT NULL,
    project_id VARCHAR(64) NOT NULL,
    render_job_id VARCHAR(64) NOT NULL,
    user_id VARCHAR(64) NOT NULL,
    preset_id VARCHAR(64),
    status VARCHAR(32) NOT NULL DEFAULT 'STARTED',
    output_uri TEXT,
    duration_ms BIGINT DEFAULT 0,
    created_at TIMESTAMP NOT NULL,
    completed_at TIMESTAMP,
    CONSTRAINT fk_history_workspace FOREIGN KEY (workspace_id) REFERENCES workspace(id),
    CONSTRAINT fk_history_project FOREIGN KEY (project_id) REFERENCES project(id)
);

CREATE INDEX ix_history_workspace ON render_history(workspace_id);
CREATE INDEX ix_history_project ON render_history(project_id);
CREATE INDEX ix_history_user ON render_history(user_id);

-- AI suggestion table
CREATE TABLE ai_suggestion (
    id VARCHAR(64) PRIMARY KEY,
    project_id VARCHAR(64) NOT NULL,
    workspace_id VARCHAR(64) NOT NULL,
    type VARCHAR(32) NOT NULL,
    title VARCHAR(256) NOT NULL,
    description TEXT,
    confidence DOUBLE,
    affected_clip_ids TEXT,
    suggested_changes TEXT,
    trace_id VARCHAR(128),
    created_at TIMESTAMP NOT NULL,
    CONSTRAINT fk_suggestion_project FOREIGN KEY (project_id) REFERENCES project(id),
    CONSTRAINT fk_suggestion_workspace FOREIGN KEY (workspace_id) REFERENCES workspace(id)
);

CREATE INDEX ix_suggestion_project ON ai_suggestion(project_id);
CREATE INDEX ix_suggestion_workspace ON ai_suggestion(workspace_id);
