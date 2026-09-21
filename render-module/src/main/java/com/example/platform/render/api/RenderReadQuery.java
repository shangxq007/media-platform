package com.example.platform.render.api;
import java.util.List;
/** Authorized owner projections; the current Identity actor and Project policy are required. */
public interface RenderReadQuery {
 record Job(String id,String projectId,String timelineSnapshotId,String profile,String status) {}
 List<Job> jobs();
 List<Job> jobsForProject(String tenantId,String projectId);
 Job job(String id);
}
