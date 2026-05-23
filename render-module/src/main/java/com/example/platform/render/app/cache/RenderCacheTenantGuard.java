package com.example.platform.render.app.cache;

import com.example.platform.storage.domain.BlobStorage;
import com.example.platform.storage.domain.StorageObjectRef;
import java.util.Map;
import java.util.Optional;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import static org.jooq.impl.DSL.field;
import static org.jooq.impl.DSL.table;

/**
 * Tenant isolation for render jobs and remote cache object keys.
 */
@Service
public class RenderCacheTenantGuard {

    private static final Logger log = LoggerFactory.getLogger(RenderCacheTenantGuard.class);

    private final DSLContext dsl;

    public RenderCacheTenantGuard(DSLContext dsl) {
        this.dsl = dsl;
    }

    public record JobTenantContext(String jobId, String tenantId, String projectId) {}

    public JobTenantContext requireJobAccess(String tenantId, String projectId, String jobId) {
        requireJobTenant(tenantId, jobId);
        if (projectId != null && !projectId.isBlank()) {
            Record record = loadJobRecord(jobId);
            String jobProject = record.get(field("project_id", String.class));
            if (!projectId.equals(jobProject)) {
                auditDeny("PROJECT_MISMATCH", tenantId, jobId, projectId, jobProject);
                throw new IllegalArgumentException("Render job not found: " + jobId);
            }
        }
        Record record = loadJobRecord(jobId);
        return new JobTenantContext(
                jobId,
                record.get(field("tenant_id", String.class)),
                record.get(field("project_id", String.class)));
    }

    public void requireJobTenant(String tenantId, String jobId) {
        if (tenantId == null || tenantId.isBlank()) {
            throw new IllegalArgumentException("tenantId is required");
        }
        if (jobId == null || jobId.isBlank()) {
            throw new IllegalArgumentException("jobId is required");
        }
        Record record = loadJobRecord(jobId);
        if (record == null) {
            throw new IllegalArgumentException("Render job not found: " + jobId);
        }
        String jobTenant = record.get(field("tenant_id", String.class));
        if (!tenantId.equals(jobTenant)) {
            auditDeny("TENANT_MISMATCH", tenantId, jobId, null, jobTenant);
            throw new IllegalArgumentException("Render job not found: " + jobId);
        }
    }

    public void requireBaseJobAccess(String tenantId, String projectId, String baseJobId) {
        requireJobAccess(tenantId, projectId, baseJobId);
        log.debug("RENDER_CACHE_BASE_JOB_OK tenant={} project={} baseJobId={}",
                tenantId, projectId, baseJobId);
    }

    /**
     * Ensures a remote storage URI object key is prefixed with {@code tenantId/}.
     */
    public void assertRemoteUriTenantPrefix(String tenantId, String storageUri) {
        if (storageUri == null || storageUri.isBlank() || tenantId == null || tenantId.isBlank()) {
            return;
        }
        Optional<StorageObjectRef> ref = BlobStorage.parseUri(storageUri);
        if (ref.isEmpty()) {
            return;
        }
        String prefix = tenantId + "/";
        if (!ref.get().objectKey().startsWith(prefix)) {
            auditDeny("CACHE_KEY_TENANT", tenantId, null, ref.get().objectKey(), ref.get().bucket());
            throw new IllegalArgumentException("Cache object denied for tenant");
        }
    }

    @SuppressWarnings("unchecked")
    public void assertExecutionStateTenant(String tenantId, Map<String, Object> executionState) {
        if (executionState == null || executionState.isEmpty()) {
            return;
        }
        Object segmentIndex = executionState.get("segmentCacheIndex");
        if (segmentIndex instanceof Map<?, ?> index) {
            index.values().forEach(entry -> assertCacheEntryUri(tenantId, entry));
        }
        Object mezzanine = executionState.get("mezzanineCacheIndex");
        if (mezzanine instanceof Map<?, ?> entry) {
            assertCacheEntryUri(tenantId, entry);
        }
    }

    @SuppressWarnings("unchecked")
    private void assertCacheEntryUri(String tenantId, Object entry) {
        if (!(entry instanceof Map<?, ?> map)) {
            return;
        }
        String remoteUri = stringVal(map.get("remoteUri"));
        if (!remoteUri.isBlank()) {
            assertRemoteUriTenantPrefix(tenantId, remoteUri);
        }
    }

    private Record loadJobRecord(String jobId) {
        Record record = dsl.select(
                        field("id", String.class),
                        field("tenant_id", String.class),
                        field("project_id", String.class))
                .from(table("render_job"))
                .where(field("id").eq(jobId))
                .fetchOne();
        if (record == null) {
            throw new IllegalArgumentException("Render job not found: " + jobId);
        }
        return record;
    }

    private static void auditDeny(String reason, String requestTenant, String jobId,
                                   String detail, String actual) {
        log.warn("RENDER_CACHE_TENANT_DENY reason={} requestTenant={} jobId={} detail={} actual={}",
                reason, requestTenant, jobId, detail, actual);
    }

    private static String stringVal(Object o) {
        return o != null ? String.valueOf(o) : "";
    }
}
