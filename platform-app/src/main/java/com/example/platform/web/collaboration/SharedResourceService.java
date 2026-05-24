package com.example.platform.web.collaboration;

import com.example.platform.identity.app.ProjectRepository;
import com.example.platform.identity.domain.Project;
import com.example.platform.shared.Ids;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class SharedResourceService {

    private static final Logger log = LoggerFactory.getLogger(SharedResourceService.class);

    private final Optional<SharedResourceJdbcRepository> jdbcRepository;
    private final ProjectRepository projectRepository;

    public SharedResourceService(Optional<SharedResourceJdbcRepository> jdbcRepository,
                                 ProjectRepository projectRepository) {
        this.jdbcRepository = jdbcRepository;
        this.projectRepository = projectRepository;
    }

    public Map<String, Object> listSharedResources(String tenantId, String userId) {
        List<Map<String, Object>> sharedProjects = new ArrayList<>();
        List<Map<String, Object>> sharedExports = new ArrayList<>();

        if (jdbcRepository.isPresent() && tenantId != null) {
            String recipient = userId != null ? userId : "anonymous";
            for (SharedResourceJdbcRepository.SharedResourceGrant grant :
                    jdbcRepository.get().findActiveForRecipient(tenantId, recipient)) {
                Map<String, Object> item = grantToMap(grant);
                if ("project".equalsIgnoreCase(grant.resourceType())) {
                    sharedProjects.add(item);
                } else if ("export".equalsIgnoreCase(grant.resourceType())) {
                    sharedExports.add(item);
                }
            }
        }

        if (sharedProjects.isEmpty() && tenantId != null) {
            try {
                for (Project p : projectRepository.findByTenantId(tenantId)) {
                    Map<String, Object> proj = new LinkedHashMap<>();
                    proj.put("id", p.id());
                    proj.put("name", p.name());
                    proj.put("description", p.description());
                    proj.put("status", p.status().name());
                    proj.put("createdAt", p.createdAt() != null ? p.createdAt().toString() : null);
                    proj.put("sharedBy", "workspace");
                    proj.put("permission", "READ");
                    proj.put("type", "project");
                    sharedProjects.add(proj);
                }
            } catch (Exception e) {
                log.warn("Failed to load workspace projects as shared fallback: {}", e.getMessage());
            }
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("sharedProjects", sharedProjects);
        result.put("sharedExports", sharedExports);
        result.put("totalShared", sharedProjects.size() + sharedExports.size());
        return result;
    }

    public SharedResourceJdbcRepository.SharedResourceGrant grantAccess(
            String tenantId, String resourceType, String resourceId, String resourceName,
            String sharedByUserId, String sharedWithUserId, String permission) {
        String type = resourceType != null ? resourceType : "project";
        SharedResourceJdbcRepository.SharedResourceGrant grant =
                new SharedResourceJdbcRepository.SharedResourceGrant(
                        Ids.newId("srg"),
                        tenantId,
                        type,
                        resourceId,
                        resourceName,
                        null,
                        "ACTIVE",
                        sharedByUserId,
                        sharedWithUserId,
                        permission != null ? permission : "READ",
                        "ACTIVE",
                        Instant.now(),
                        null);
        jdbcRepository.ifPresent(r -> r.save(grant));
        return grant;
    }

    public List<Map<String, Object>> listGrantsForTenant(String tenantId, boolean includeRevoked) {
        List<Map<String, Object>> items = new ArrayList<>();
        jdbcRepository.ifPresent(r -> {
            for (SharedResourceJdbcRepository.SharedResourceGrant grant : r.findByTenant(tenantId, includeRevoked)) {
                Map<String, Object> item = grantToMap(grant);
                item.put("grantId", grant.grantId());
                item.put("sharedWithUserId", grant.sharedWithUserId());
                item.put("grantStatus", grant.status());
                items.add(item);
            }
        });
        return items;
    }

    public boolean revokeGrant(String grantId) {
        return jdbcRepository.map(r -> r.revoke(grantId)).orElse(false);
    }

    private Map<String, Object> grantToMap(SharedResourceJdbcRepository.SharedResourceGrant grant) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("id", grant.resourceId());
        item.put("name", grant.resourceName() != null ? grant.resourceName() : grant.resourceId());
        item.put("description", grant.resourceDescription());
        item.put("status", grant.resourceStatus() != null ? grant.resourceStatus() : "ACTIVE");
        item.put("sharedBy", grant.sharedByUserId() != null ? grant.sharedByUserId() : "workspace");
        item.put("permission", grant.permission());
        item.put("createdAt", grant.createdAt().toString());
        item.put("type", grant.resourceType());
        return item;
    }
}
