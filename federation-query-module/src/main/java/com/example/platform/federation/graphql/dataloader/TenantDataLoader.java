package com.example.platform.federation.graphql.dataloader;

import com.example.platform.identity.app.TenantRepository;
import com.example.platform.identity.domain.Tenant;
import org.dataloader.MappedBatchLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.stream.Collectors;

@Component
public class TenantDataLoader implements MappedBatchLoader<String, Map<String, Object>> {
    private static final Logger log = LoggerFactory.getLogger(TenantDataLoader.class);

    private final TenantRepository tenantRepository;

    public TenantDataLoader(TenantRepository tenantRepository) {
        this.tenantRepository = tenantRepository;
    }

    @Override
    public CompletionStage<Map<String, Map<String, Object>>> load(Set<String> keys) {
        log.debug("Batch loading {} tenants", keys.size());
        return CompletableFuture.supplyAsync(() -> keys.stream()
                .collect(Collectors.toMap(
                        tenantId -> tenantId,
                        tenantId -> {
                            try {
                                Optional<Tenant> tenant = tenantRepository.findById(tenantId);
                                return tenant.map(t -> Map.<String, Object>of(
                                        "id", t.id() != null ? t.id() : tenantId,
                                        "name", t.name() != null ? t.name() : "unknown",
                                        "status", t.status() != null ? t.status().name() : "ACTIVE"
                                )).orElseGet(() -> Map.<String, Object>of("id", tenantId));
                            } catch (Exception e) {
                                return Map.<String, Object>of("id", tenantId);
                            }
                        }
                )));
    }
}
