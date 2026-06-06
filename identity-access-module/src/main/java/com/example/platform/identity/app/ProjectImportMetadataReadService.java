package com.example.platform.identity.app;

import com.example.platform.identity.api.dto.ProjectImportedMetadataDetailDto;
import com.example.platform.identity.api.dto.ProjectImportedMetadataDetailDto.AssetMappingEntry;
import com.example.platform.identity.api.dto.ProjectImportedMetadataSummaryDto;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.ZoneOffset;
import java.util.*;

/**
 * Read service for imported project metadata.
 *
 * <p>Tenant-scoped service that exposes persisted import metadata
 * for read/preview purposes. Does not modify runtime state.
 */
@Service
public class ProjectImportMetadataReadService {

    private static final Logger log = LoggerFactory.getLogger(ProjectImportMetadataReadService.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final ProjectImportMetadataRepository repository;
    private final MetadataScrubber scrubber;

    public ProjectImportMetadataReadService(ProjectImportMetadataRepository repository,
                                             MetadataScrubber scrubber) {
        this.repository = repository;
        this.scrubber = scrubber;
    }

    /**
     * Find the latest imported metadata summary for a project.
     */
    public Optional<ProjectImportedMetadataSummaryDto> findLatestByProject(
            String tenantId, String projectId) {
        return repository.findByProjectId(projectId, tenantId)
                .map(this::toSummaryDto);
    }

    /**
     * Find the latest imported metadata detail for a project.
     */
    public Optional<ProjectImportedMetadataDetailDto> findLatestDetailByProject(
            String tenantId, String projectId) {
        return repository.findByProjectId(projectId, tenantId)
                .map(this::toDetailDto);
    }

    /**
     * Find imported metadata by import ID.
     */
    public Optional<ProjectImportedMetadataSummaryDto> findByImportId(
            String tenantId, String importId) {
        return repository.findByImportId(importId)
                .filter(record -> tenantId.equals(record.tenantId()))
                .map(this::toSummaryDto);
    }

    /**
     * Find imported metadata detail by import ID.
     */
    public Optional<ProjectImportedMetadataDetailDto> findDetailByImportId(
            String tenantId, String importId) {
        return repository.findByImportId(importId)
                .filter(record -> tenantId.equals(record.tenantId()))
                .map(this::toDetailDto);
    }

    private ProjectImportedMetadataSummaryDto toSummaryDto(
            ProjectImportMetadataRepository.MetadataRecord record) {
        return new ProjectImportedMetadataSummaryDto(
                record.importId(),
                record.sourceProjectId(),
                record.sourceExportId(),
                record.schemaVersion(),
                isPresent(record.timelineJson()),
                isPresent(record.timelineOtioJson()),
                isPresent(record.renderPlanJson()),
                isPresent(record.spatialPlanJson()),
                isPresent(record.exportProfilesJson()),
                isPresent(record.effectTaxonomyJson()),
                isPresent(record.appliedEffectsJson()),
                isPresent(record.assetMappingJson()),
                true,
                record.createdAt().atOffset(ZoneOffset.UTC).toString()
        );
    }

    private ProjectImportedMetadataDetailDto toDetailDto(
            ProjectImportMetadataRepository.MetadataRecord record) {
        ProjectImportedMetadataSummaryDto summary = toSummaryDto(record);

        JsonNode timeline = parseAndScrub(record.timelineJson());
        JsonNode timelineOtio = parseAndScrub(record.timelineOtioJson());
        JsonNode renderPlan = parseAndScrub(record.renderPlanJson());
        JsonNode spatialPlan = parseAndScrub(record.spatialPlanJson());
        JsonNode exportProfiles = parseAndScrub(record.exportProfilesJson());
        JsonNode effectTaxonomy = parseAndScrub(record.effectTaxonomyJson());
        JsonNode appliedEffects = parseAndScrub(record.appliedEffectsJson());
        Map<String, AssetMappingEntry> assetMapping = parseAssetMapping(record.assetMappingJson());

        return new ProjectImportedMetadataDetailDto(
                summary,
                timeline,
                timelineOtio,
                renderPlan,
                spatialPlan,
                exportProfiles,
                effectTaxonomy,
                appliedEffects,
                assetMapping,
                List.of()
        );
    }

    private JsonNode parseAndScrub(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            String scrubbed = scrubber.scrub(json);
            if (scrubbed == null || scrubbed.isBlank()) {
                return null;
            }
            return MAPPER.readTree(scrubbed);
        } catch (JsonProcessingException e) {
            log.warn("Failed to parse scrubbed JSON", e);
            return null;
        }
    }

    private Map<String, AssetMappingEntry> parseAssetMapping(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            String scrubbed = scrubber.scrub(json);
            if (scrubbed == null || scrubbed.isBlank()) {
                return null;
            }
            Map<String, String> raw = MAPPER.readValue(scrubbed,
                    MAPPER.getTypeFactory().constructMapType(Map.class, String.class, String.class));
            Map<String, AssetMappingEntry> result = new LinkedHashMap<>();
            for (Map.Entry<String, String> entry : raw.entrySet()) {
                result.put(entry.getKey(), new AssetMappingEntry(null, entry.getValue()));
            }
            return result;
        } catch (JsonProcessingException e) {
            log.warn("Failed to parse asset mapping JSON", e);
            return null;
        }
    }

    private boolean isPresent(String value) {
        return value != null && !value.isBlank();
    }
}
