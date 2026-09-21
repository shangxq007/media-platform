package com.example.platform.prompt.api.reads;
import com.example.platform.shared.authorization.CanonicalActor;
import java.time.OffsetDateTime;
import java.util.List;
public interface PromptReadQuery {
 record Template(String templateId,String name,String status,List<String> tags) { public Template { tags=List.copyOf(tags); } }
 record Version(String promptVersion,String templateBody,String changelog,String createdBy,OffsetDateTime createdAt) {}
 record Execution(String executionId,String status,String riskLevel,double costEstimate,OffsetDateTime startedAt,OffsetDateTime finishedAt) {}
 Template template(CanonicalActor actor,String id);
 List<Version> versions(CanonicalActor actor,String id);
 Version currentVersion(CanonicalActor actor,String id);
 List<Execution> executions(CanonicalActor actor,String id);
 List<Execution> executions(CanonicalActor actor);
}
