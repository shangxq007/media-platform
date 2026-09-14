package com.example.platform.identity.api.project;
/** Owner resolution only; consumers must separately authorize and check feature/quota eligibility. */
public interface ProjectScopeQueries {
    ProjectScope resolveForAcceptance(String tenantId, String projectId);
}
