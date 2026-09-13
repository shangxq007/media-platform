package com.example.platform.media.app;
import com.example.platform.media.api.*;
import com.example.platform.media.infrastructure.persistence.JooqMediaAssetRepository;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
@Service
public class MediaAssetService implements MediaAssets {
    private final JooqMediaAssetRepository repository;
    private final MediaAuthorization authorization;
    public MediaAssetService(JooqMediaAssetRepository repository, MediaAuthorization authorization) {
        this.repository = repository; this.authorization = authorization;
    }
    public void requireRegistrationScope(String tenant, String project) {
        authorization.require(tenant, project, true);
    }
    @Transactional
    public Asset register(String tenant, String project, String key, String type, String filename, Long length, String checksum) {
        authorization.require(tenant, project, true);
        return repository.register(tenant, project, key, type, filename, length, checksum);
    }
    public Optional<Asset> findById(String tenant, String id) {
        var asset = repository.findById(tenant, id);
        asset.ifPresent(a -> authorization.require(tenant, a.projectId(), false));
        return asset;
    }
    public List<Asset> listByProject(String tenant, String project) {
        authorization.require(tenant, project, false);
        return repository.listByProject(tenant, project);
    }
    @Transactional
    public boolean delete(String tenant, String project, String id, String version) {
        authorization.require(tenant, project, true);
        return repository.delete(tenant, project, id, version);
    }
    @Transactional
    public void updatePublishStatus(String tenant, String project, String id, String expectedStatus, String status) {
        authorization.require(tenant, project, true);
        repository.updatePublishStatus(tenant, project, id, expectedStatus, status);
    }
}
