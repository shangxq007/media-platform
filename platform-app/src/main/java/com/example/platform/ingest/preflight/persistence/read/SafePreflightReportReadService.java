package com.example.platform.ingest.preflight.persistence.read;

import com.example.platform.ingest.preflight.persistence.SafePreflightReportRecord;
import com.example.platform.ingest.preflight.persistence.SafePreflightReportRecordRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

@Service
public class SafePreflightReportReadService {

    private final SafePreflightReportRecordRepository repository;
    private final SafePreflightReportReadMapper mapper;

    public SafePreflightReportReadService(SafePreflightReportRecordRepository repository,
                                           SafePreflightReportReadMapper mapper) {
        this.repository = repository;
        this.mapper = mapper;
    }

    public SafePreflightReportRecordListResponse listRecords(String tenantId, String projectId,
                                                              String rawMediaProductId, int limit, int offset) {
        List<SafePreflightReportRecord> records;

        if (rawMediaProductId != null) {
            records = repository.findByTenantProjectAndProduct(tenantId, projectId, rawMediaProductId);
        } else {
            records = repository.findByTenantAndProject(tenantId, projectId);
        }

        // Filter expired/deleted
        Instant now = Instant.now();
        records = records.stream()
            .filter(r -> !"DELETED".equals(r.lifecycleState()))
            .filter(r -> !"EXPIRED".equals(r.lifecycleState()))
            .filter(r -> r.expiresAt() == null || r.expiresAt().isAfter(now))
            .collect(Collectors.toList());

        int totalCount = records.size();

        // Apply pagination
        List<SafePreflightReportRecordListItem> items = records.stream()
            .skip(offset)
            .limit(limit)
            .map(mapper::toListItem)
            .collect(Collectors.toList());

        return new SafePreflightReportRecordListResponse(tenantId, projectId, items, totalCount, limit, offset);
    }

    public Optional<SafePreflightReportRecordDetailResponse> getRecord(String tenantId, String projectId, Long recordId) {
        return repository.findByTenantAndProject(tenantId, projectId).stream()
            .filter(r -> r.id().equals(recordId))
            .filter(r -> !"DELETED".equals(r.lifecycleState()))
            .filter(r -> {
                Instant now = Instant.now();
                return r.expiresAt() == null || r.expiresAt().isAfter(now);
            })
            .map(mapper::toDetailResponse)
            .findFirst();
    }
}
