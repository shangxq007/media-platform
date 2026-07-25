package com.example.platform.entitlement.infrastructure;

import com.example.platform.entitlement.domain.EntitlementBundle;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.jooq.impl.DSL;

import org.springframework.stereotype.Repository;
import static com.example.platform.typedschema.jooq.generated.tables.EntitlementBundle.ENTITLEMENT_BUNDLE;


@Repository

public class EntitlementBundleRepository {

    private final DSLContext dsl;

    public EntitlementBundleRepository(DSLContext dsl) {
        this.dsl = dsl;
    }

    public void save(EntitlementBundle bundle) {
        LocalDateTime now = LocalDateTime.now();
        dsl.insertInto(ENTITLEMENT_BUNDLE)
                .columns(ENTITLEMENT_BUNDLE.ID, ENTITLEMENT_BUNDLE.BUNDLE_KEY, ENTITLEMENT_BUNDLE.NAME, ENTITLEMENT_BUNDLE.DESCRIPTION,
                        ENTITLEMENT_BUNDLE.STATUS, ENTITLEMENT_BUNDLE.ALLOWED_PROVIDERS, ENTITLEMENT_BUNDLE.ALLOWED_PRESETS,
                        ENTITLEMENT_BUNDLE.GPU_ALLOWED, ENTITLEMENT_BUNDLE.REMOTE_WORKER_ALLOWED, ENTITLEMENT_BUNDLE.CUSTOM_FONTS_ALLOWED,
                        ENTITLEMENT_BUNDLE.MAX_SUBTITLE_TRACKS, ENTITLEMENT_BUNDLE.MAX_CONCURRENT_JOBS,
                        ENTITLEMENT_BUNDLE.MONTHLY_RENDER_MINUTES, ENTITLEMENT_BUNDLE.STORAGE_LIMIT_BYTES,
                        ENTITLEMENT_BUNDLE.WATERMARK_REQUIRED, ENTITLEMENT_BUNDLE.PRIORITY_QUEUE_ALLOWED,
                        ENTITLEMENT_BUNDLE.BETA_EFFECTS_ALLOWED, ENTITLEMENT_BUNDLE.PROMPT_EXECUTION_LIMIT,
                        ENTITLEMENT_BUNDLE.EXTENSION_EXECUTION_ALLOWED, ENTITLEMENT_BUNDLE.API_ACCESS_ALLOWED,
                        ENTITLEMENT_BUNDLE.MCP_ACCESS_ALLOWED, ENTITLEMENT_BUNDLE.CREATED_AT, ENTITLEMENT_BUNDLE.UPDATED_AT)
                .values(bundle.id(), bundle.bundleKey(), bundle.name(), bundle.description(),
                        bundle.status(), DSL.val(bundle.allowedProviders(), ENTITLEMENT_BUNDLE.ALLOWED_PROVIDERS), DSL.val(bundle.allowedPresets(), ENTITLEMENT_BUNDLE.ALLOWED_PRESETS),
                        bundle.gpuAllowed(), bundle.remoteWorkerAllowed(), bundle.customFontsAllowed(),
                        bundle.maxSubtitleTracks(), bundle.maxConcurrentJobs(),
                        bundle.monthlyRenderMinutes(), bundle.storageLimitBytes(),
                        bundle.watermarkRequired(), bundle.priorityQueueAllowed(),
                        bundle.betaEffectsAllowed(), bundle.promptExecutionLimit(),
                        bundle.extensionExecutionAllowed(), bundle.apiAccessAllowed(),
                        bundle.mcpAccessAllowed(), now, now)
                .execute();
    }

    public Optional<EntitlementBundle> findByKey(String bundleKey) {
        return dsl.select()
                .from(ENTITLEMENT_BUNDLE)
                .where(ENTITLEMENT_BUNDLE.BUNDLE_KEY.eq(bundleKey))
                .fetchOptional(this::mapRecord);
    }

    public List<EntitlementBundle> findAllActive() {
        return dsl.select()
                .from(ENTITLEMENT_BUNDLE)
                .where(ENTITLEMENT_BUNDLE.STATUS.eq("ACTIVE"))
                .orderBy(ENTITLEMENT_BUNDLE.CREATED_AT.desc())
                .fetch(this::mapRecord);
    }

    public void update(EntitlementBundle bundle) {
        dsl.update(ENTITLEMENT_BUNDLE)
                .set(ENTITLEMENT_BUNDLE.NAME, bundle.name())
                .set(ENTITLEMENT_BUNDLE.DESCRIPTION, bundle.description())
                .set(ENTITLEMENT_BUNDLE.STATUS, bundle.status())
                .set(ENTITLEMENT_BUNDLE.ALLOWED_PROVIDERS, DSL.val(bundle.allowedProviders(), ENTITLEMENT_BUNDLE.ALLOWED_PROVIDERS))
                .set(ENTITLEMENT_BUNDLE.ALLOWED_PRESETS, DSL.val(bundle.allowedPresets(), ENTITLEMENT_BUNDLE.ALLOWED_PRESETS))
                .set(ENTITLEMENT_BUNDLE.GPU_ALLOWED, bundle.gpuAllowed())
                .set(ENTITLEMENT_BUNDLE.REMOTE_WORKER_ALLOWED, bundle.remoteWorkerAllowed())
                .set(ENTITLEMENT_BUNDLE.CUSTOM_FONTS_ALLOWED, bundle.customFontsAllowed())
                .set(ENTITLEMENT_BUNDLE.MAX_SUBTITLE_TRACKS, bundle.maxSubtitleTracks())
                .set(ENTITLEMENT_BUNDLE.MAX_CONCURRENT_JOBS, bundle.maxConcurrentJobs())
                .set(ENTITLEMENT_BUNDLE.MONTHLY_RENDER_MINUTES, bundle.monthlyRenderMinutes())
                .set(ENTITLEMENT_BUNDLE.STORAGE_LIMIT_BYTES, bundle.storageLimitBytes())
                .set(ENTITLEMENT_BUNDLE.WATERMARK_REQUIRED, bundle.watermarkRequired())
                .set(ENTITLEMENT_BUNDLE.PRIORITY_QUEUE_ALLOWED, bundle.priorityQueueAllowed())
                .set(ENTITLEMENT_BUNDLE.BETA_EFFECTS_ALLOWED, bundle.betaEffectsAllowed())
                .set(ENTITLEMENT_BUNDLE.PROMPT_EXECUTION_LIMIT, bundle.promptExecutionLimit())
                .set(ENTITLEMENT_BUNDLE.EXTENSION_EXECUTION_ALLOWED, bundle.extensionExecutionAllowed())
                .set(ENTITLEMENT_BUNDLE.API_ACCESS_ALLOWED, bundle.apiAccessAllowed())
                .set(ENTITLEMENT_BUNDLE.MCP_ACCESS_ALLOWED, bundle.mcpAccessAllowed())
                .set(ENTITLEMENT_BUNDLE.UPDATED_AT, LocalDateTime.now())
                .where(ENTITLEMENT_BUNDLE.BUNDLE_KEY.eq(bundle.bundleKey()))
                .execute();
    }

    private EntitlementBundle mapRecord(Record r) {
        return new EntitlementBundle(
                r.get(ENTITLEMENT_BUNDLE.ID, String.class),
                r.get(ENTITLEMENT_BUNDLE.BUNDLE_KEY, String.class),
                r.get(ENTITLEMENT_BUNDLE.NAME, String.class),
                r.get(ENTITLEMENT_BUNDLE.DESCRIPTION, String.class),
                r.get(ENTITLEMENT_BUNDLE.STATUS, String.class),
                r.get(ENTITLEMENT_BUNDLE.ALLOWED_PROVIDERS, String.class),
                r.get(ENTITLEMENT_BUNDLE.ALLOWED_PRESETS, String.class),
                r.get(ENTITLEMENT_BUNDLE.GPU_ALLOWED) != null && r.get(ENTITLEMENT_BUNDLE.GPU_ALLOWED),
                r.get(ENTITLEMENT_BUNDLE.REMOTE_WORKER_ALLOWED) != null && r.get(ENTITLEMENT_BUNDLE.REMOTE_WORKER_ALLOWED),
                r.get(ENTITLEMENT_BUNDLE.CUSTOM_FONTS_ALLOWED) != null && r.get(ENTITLEMENT_BUNDLE.CUSTOM_FONTS_ALLOWED),
                r.get(ENTITLEMENT_BUNDLE.MAX_SUBTITLE_TRACKS, Integer.class),
                r.get(ENTITLEMENT_BUNDLE.MAX_CONCURRENT_JOBS, Integer.class),
                r.get(ENTITLEMENT_BUNDLE.MONTHLY_RENDER_MINUTES, Long.class),
                r.get(ENTITLEMENT_BUNDLE.STORAGE_LIMIT_BYTES, Long.class),
                r.get(ENTITLEMENT_BUNDLE.WATERMARK_REQUIRED) != null && r.get(ENTITLEMENT_BUNDLE.WATERMARK_REQUIRED),
                r.get(ENTITLEMENT_BUNDLE.PRIORITY_QUEUE_ALLOWED) != null && r.get(ENTITLEMENT_BUNDLE.PRIORITY_QUEUE_ALLOWED),
                r.get(ENTITLEMENT_BUNDLE.BETA_EFFECTS_ALLOWED) != null && r.get(ENTITLEMENT_BUNDLE.BETA_EFFECTS_ALLOWED),
                r.get(ENTITLEMENT_BUNDLE.PROMPT_EXECUTION_LIMIT, Long.class),
                r.get(ENTITLEMENT_BUNDLE.EXTENSION_EXECUTION_ALLOWED) != null && r.get(ENTITLEMENT_BUNDLE.EXTENSION_EXECUTION_ALLOWED),
                r.get(ENTITLEMENT_BUNDLE.API_ACCESS_ALLOWED) != null && r.get(ENTITLEMENT_BUNDLE.API_ACCESS_ALLOWED),
                r.get(ENTITLEMENT_BUNDLE.MCP_ACCESS_ALLOWED) != null && r.get(ENTITLEMENT_BUNDLE.MCP_ACCESS_ALLOWED),
                toInstant(r.get(ENTITLEMENT_BUNDLE.CREATED_AT, LocalDateTime.class)),
                toInstant(r.get(ENTITLEMENT_BUNDLE.UPDATED_AT, LocalDateTime.class))
        );
    }

    private Instant toInstant(LocalDateTime odt) {
        return odt != null ? odt.toInstant(java.time.ZoneOffset.UTC) : null;
    }
}
