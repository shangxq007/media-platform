package com.example.platform.workerfabric.domain;

import java.util.Optional;

/** Current-generation durable success, not backend observation or an arbitrary Artifact row. */
public interface CompletedTaskOutputQuery {
    record Output(String taskId,String attemptId,long generation,String artifactId) {}
    Optional<Output> find(String taskId);
}
