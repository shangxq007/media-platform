package com.example.platform.identity.app;
import com.example.platform.identity.api.project.*;
import com.example.platform.identity.domain.*;
import com.example.platform.identity.infrastructure.WorkspaceRepository;
import com.example.platform.shared.web.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
@Service
public class ProjectScopeService implements ProjectScopeQueries {
    private final ProjectRepository projects;private final WorkspaceRepository workspaces;
    public ProjectScopeService(ProjectRepository projects,WorkspaceRepository workspaces){this.projects=projects;this.workspaces=workspaces;}
    /** Explicit verified relationship import; no inferred/default Workspace or reparenting. */
    @Transactional
    public ProjectScope bindUnmappedProject(com.example.platform.shared.authorization.CanonicalActor operator,String tenant,String project,String workspace) {
        if(operator==null||!operator.isSystem()||!"system:identity-provisioning".equals(operator.actorId()))throw new PlatformException(CommonErrorCode.INSUFFICIENT_PERMISSION,"Scope provisioning denied");
        workspaces.lockById(workspace).filter(w->tenant.equals(w.tenantId())&&w.status()==Workspace.WorkspaceStatus.ACTIVE).orElseThrow();
        var prior=projects.findByIdAndTenant(project,tenant).orElseThrow();
        if(prior.workspaceId()!=null&&!workspace.equals(prior.workspaceId()))throw new PlatformException(CommonErrorCode.CONFLICT,"Project already bound");
        if(prior.workspaceId()==null) projects.bindWorkspaceIfUnmapped(project,tenant,workspace);
        var current=projects.findByIdAndTenant(project,tenant).orElseThrow();
        if(!workspace.equals(current.workspaceId()))throw new PlatformException(CommonErrorCode.CONFLICT,"Concurrent Project mapping differs");
        return new ProjectScope(tenant,workspace,project);
    }

    @Transactional
    public ProjectScope resolveForAcceptance(String tenant,String project) {
        var p=projects.findByIdAndTenant(project,tenant).orElseThrow(()->new PlatformException(CommonErrorCode.INSUFFICIENT_PERMISSION,"Project scope unavailable"));
        if(p.status()!=Project.ProjectStatus.ACTIVE||p.workspaceId()==null)throw new PlatformException(CommonErrorCode.CONFLICT,"Project Workspace mapping unresolved");
        // Lock shared with membership mutation; outer submission transaction retains it until commit.
        workspaces.lockById(p.workspaceId()).filter(w->tenant.equals(w.tenantId())&&w.status()==Workspace.WorkspaceStatus.ACTIVE)
                .orElseThrow(()->new PlatformException(CommonErrorCode.INSUFFICIENT_PERMISSION,"Workspace scope unavailable"));
        return new ProjectScope(tenant,p.workspaceId(),project);
    }
}
