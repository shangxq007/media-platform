package com.example.platform.prompt.app;
import com.example.platform.prompt.api.reads.PromptReadQuery;
import com.example.platform.shared.authorization.CanonicalActor;
import com.example.platform.shared.web.*;
import org.springframework.stereotype.Service;
import java.util.List;
@Service
public class PromptReadProjection implements PromptReadQuery {
 private final PromptTemplateService prompts;
 public PromptReadProjection(PromptTemplateService prompts){this.prompts=prompts;}
 private void scope(CanonicalActor a){if(a==null || a.tenantId()==null || a.actorId().isBlank() || !a.tenantId().equals(TenantContext.get()))throw new PlatformException(CommonErrorCode.INSUFFICIENT_PERMISSION,"Prompt read scope mismatch");}
 public Template template(CanonicalActor a,String id){scope(a);var t=prompts.getTemplate(id);return new Template(t.templateId(),t.name(),t.status()==null?null:t.status().name(),t.tags());}
 private Version version(com.example.platform.prompt.domain.PromptTemplateVersion v){return v==null?null:new Version(v.promptVersion(),v.templateBody(),v.changelog(),v.createdBy(),v.createdAt());}
 public List<Version> versions(CanonicalActor a,String id){scope(a);prompts.getTemplate(id);return prompts.listVersions(id).stream().map(this::version).toList();}
 public Version currentVersion(CanonicalActor a,String id){scope(a);return version(prompts.getCurrentVersion(id));}
 private Execution execution(com.example.platform.prompt.domain.PromptExecutionRun e){return new Execution(e.executionId(),e.status()==null?null:e.status().name(),e.riskLevel()==null?null:e.riskLevel().name(),e.costEstimate(),e.startedAt(),e.finishedAt());}
 private boolean visible(CanonicalActor a,com.example.platform.prompt.domain.PromptExecutionRun e){return a.tenantId().equals(e.tenantId()) && (a.actorId().equals(e.userId()) || a.roles().contains("ADMIN"));}
 public List<Execution> executions(CanonicalActor a,String id){scope(a);prompts.getTemplate(id);return prompts.listExecutions(id).stream().filter(e->visible(a,e)).map(this::execution).toList();}
 public List<Execution> executions(CanonicalActor a){scope(a);return prompts.listAllExecutions().stream().filter(e->visible(a,e)).map(this::execution).toList();}
}
