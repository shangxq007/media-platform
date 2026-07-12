package com.example.platform.ingest.preflight;

import com.example.platform.ingest.contract.*;
import com.example.platform.ingest.preflight.policy.*;
import java.nio.file.Path;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Upload path report-only preflight hook.
 * Disabled by default. Fail-open. Does not reject uploads.
 */
@Component
public class UploadReportOnlyPreflightHook {

    private static final Logger log = LoggerFactory.getLogger(UploadReportOnlyPreflightHook.class);
    private final IngestMetadataMerger merger;
    private final ReportOnlyPreflightPolicyEvaluator policyEvaluator;

    @Value("${ingest.preflight.upload-integration.enabled:false}")
    private boolean integrationEnabled;

    @Value("${ingest.preflight.upload-integration.fail-open:true}")
    private boolean failOpen;

    public UploadReportOnlyPreflightHook(ObjectProvider<IngestMetadataMerger> mergerProvider,
                                          ObjectProvider<ReportOnlyPreflightPolicyEvaluator> evaluatorProvider) {
        this.merger = mergerProvider.getIfAvailable();
        this.policyEvaluator = evaluatorProvider.getIfAvailable();
    }

    /**
     * Evaluate upload in report-only mode. Never rejects. Fail-open on errors.
     */
    public Optional<UploadPreflightResult> maybeEvaluate(byte[] data, String filename,
                                                          String declaredContentType, Path localMediaPath) {
        if (!integrationEnabled || merger == null) {
            return Optional.empty();
        }

        try {
            var result = merger.evaluate(data, filename, declaredContentType, localMediaPath);
            log.info("Preflight report: decision={} warnings={} providers={}",
                result.decision(), result.warnings().size(),
                result.detectorProvenance().stream().map(p -> p.provider().name()).toList());

            // Policy evaluation (internal-only, non-blocking)
            if (policyEvaluator != null) {
                try {
                    var safeReport = SafePreflightReportMapper.fromPreflightResult(result);
                    var policyInput = PreflightPolicyEvaluationInput.fromSafeReport(safeReport);
                    var policyResult = policyEvaluator.evaluateReportOnly(policyInput);
                    log.info("Preflight policy: decision={} findings={} reportOnly={}",
                        policyResult.decision(), policyResult.findings().size(), policyResult.reportOnly());
                } catch (Exception e) {
                    log.warn("Preflight policy failed (fail-open): {}", e.getMessage());
                }
            }

            return Optional.of(result);
        } catch (Exception e) {
            if (failOpen) {
                log.warn("Preflight failed (fail-open): {}", e.getMessage());
                return Optional.empty();
            }
            throw e;
        }
    }
}
