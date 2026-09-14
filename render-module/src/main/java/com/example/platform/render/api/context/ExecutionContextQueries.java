package com.example.platform.render.api.context;
/** Trusted worker/Workflow reconstruction by exact persisted task scope; not an HTTP authorization bypass. */
public interface ExecutionContextQueries {
    AcceptedExecutionContext get(String tenantId,String projectId,String jobId);
}
