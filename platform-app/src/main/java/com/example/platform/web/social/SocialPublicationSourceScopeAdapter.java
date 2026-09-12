package com.example.platform.web.social;

import com.example.platform.identity.app.ProjectRepository;
import com.example.platform.social.app.SocialProjectScopePort;
import java.util.Objects;
import org.springframework.stereotype.Component;

/** Platform composition adapter over the canonical Project authority. */
@Component
public final class SocialPublicationSourceScopeAdapter implements SocialProjectScopePort {

    private final ProjectRepository projectRepository;

    public SocialPublicationSourceScopeAdapter(ProjectRepository projectRepository) {
        this.projectRepository = Objects.requireNonNull(projectRepository, "projectRepository");
    }

    @Override
    public boolean belongsToTenant(String tenantId, String projectId) {
        return projectRepository.findById(projectId)
                .filter(project -> tenantId.equals(project.tenantId()))
                .isPresent();
    }
}
