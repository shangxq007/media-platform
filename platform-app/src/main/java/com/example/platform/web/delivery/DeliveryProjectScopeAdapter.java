package com.example.platform.web.delivery;

import com.example.platform.delivery.app.DeliveryProjectScopePort;
import com.example.platform.identity.app.ProjectRepository;
import org.springframework.stereotype.Component;

@Component
public final class DeliveryProjectScopeAdapter implements DeliveryProjectScopePort {
    private final ProjectRepository projects;

    public DeliveryProjectScopeAdapter(ProjectRepository projects) { this.projects = projects; }

    @Override
    public boolean belongsToTenant(String tenantId, String projectId) {
        return projects.findById(projectId).filter(project -> tenantId.equals(project.tenantId())).isPresent();
    }
}
