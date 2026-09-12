package com.example.platform.identity.api.project;

import com.example.platform.identity.api.dto.ProjectResponse;
import java.util.List;

/** Authenticated Project discovery/resolution. Returned scope is not a credential for later operations. */
public interface ProjectReadQuery {
    List<ProjectResponse> listProjects(String tenantId);
    ProjectResponse getProject(String tenantId, String projectId);
}
