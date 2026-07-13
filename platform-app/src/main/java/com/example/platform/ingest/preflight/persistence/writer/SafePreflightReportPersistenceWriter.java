package com.example.platform.ingest.preflight.persistence.writer;

import com.example.platform.ingest.preflight.persistence.SafePreflightReportRecord;
import com.example.platform.ingest.preflight.persistence.SafePreflightReportRecordRepository;
import com.example.platform.ingest.preflight.persistence.contract.SafePreflightPersistenceContractProperties;
import com.example.platform.ingest.preflight.persistence.contract.SafePreflightPersistenceContractValidator;
import com.example.platform.ingest.preflight.persistence.contract.SafePreflightPersistenceMode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import com.example.platform.ingest.preflight.policy.PreflightPolicyDecision;

@Component
public class SafePreflightReportPersistenceWriter {

    private static final Logger log = LoggerFactory.getLogger(SafePreflightReportPersistenceWriter.class);

    private final SafePreflightPersistenceContractProperties config;
    private final SafePreflightPersistenceContractValidator validator;
    private final SafePreflightReportRecordMapper mapper;
    private final SafePreflightReportRecordRepository repository;

    public SafePreflightReportPersistenceWriter(SafePreflightPersistenceContractProperties config,
                                                 SafePreflightPersistenceContractValidator validator,
                                                 SafePreflightReportRecordMapper mapper,
                                                 SafePreflightReportRecordRepository repository) {
        this.config = config;
        this.validator = validator;
        this.mapper = mapper;
        this.repository = repository;
    }

    public SafePreflightPersistenceWriteOutcome writeReportOnlySafeRecord(SafePreflightPersistenceWriteRequest request) {
        // Config gate
        if (config.getMode() == SafePreflightPersistenceMode.DISABLED) {
            return SafePreflightPersistenceWriteOutcome.SKIPPED_DISABLED;
        }

        if (config.getMode() != SafePreflightPersistenceMode.DEV_PREVIEW_EPHEMERAL_ONLY) {
            return SafePreflightPersistenceWriteOutcome.SKIPPED_UNSUPPORTED_MODE;
        }

        // Validate config
        if (!validator.isValid(config)) {
            log.warn("preflight persistence config invalid, skipping write");
            return SafePreflightPersistenceWriteOutcome.SKIPPED_INVALID_INPUT;
        }

        // Validate request
        if (request.safeReport() == null || request.policyResult() == null) {
            return SafePreflightPersistenceWriteOutcome.SKIPPED_INVALID_INPUT;
        }

        // Validate policy decision is not REJECT
        if (request.policyResult().decision() == com.example.platform.ingest.preflight.policy.PreflightPolicyDecision.REJECT) {
            log.warn("preflight persistence REJECT decision not allowed, skipping");
            return SafePreflightPersistenceWriteOutcome.SKIPPED_INVALID_INPUT;
        }

        try {
            SafePreflightReportRecord record = mapper.map(request, config);
            repository.save(record);
            log.info("preflight safe report recorded: tenant={}, project={}", request.tenantId(), request.projectId());
            return SafePreflightPersistenceWriteOutcome.RECORDED;
        } catch (Exception e) {
            log.warn("preflight persistence failed open: {}", e.getMessage());
            return SafePreflightPersistenceWriteOutcome.FAILED_OPEN;
        }
    }
}
