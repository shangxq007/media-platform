package com.example.platform.entitlement.infrastructure;

import static org.jooq.impl.DSL.field;
import static org.jooq.impl.DSL.table;

import com.example.platform.entitlement.domain.EntitlementBundle;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Repository;

@Repository
@ConditionalOnBean(DSLContext.class)
public class EntitlementBundleRepository {

    private final DSLContext dsl;

    public EntitlementBundleRepository(DSLContext dsl) {
        this.dsl = dsl;
    }

    public void save(EntitlementBundle bundle) {
        OffsetDateTime now = OffsetDateTime.now();
        dsl.insertInto(table("ENTITLEMENT_BUNDLE"))
                .columns(field("ID"), field("BUNDLE_KEY"), field("NAME"), field("DESCRIPTION"),
                        field("STATUS"), field("ALLOWED_PROVIDERS"), field("ALLOWED_PRESETS"),
                        field("GPU_ALLOWED"), field("REMOTE_WORKER_ALLOWED"), field("CUSTOM_FONTS_ALLOWED"),
                        field("MAX_SUBTITLE_TRACKS"), field("MAX_CONCURRENT_JOBS"),
                        field("MONTHLY_RENDER_MINUTES"), field("STORAGE_LIMIT_BYTES"),
                        field("WATERMARK_REQUIRED"), field("PRIORITY_QUEUE_ALLOWED"),
                        field("BETA_EFFECTS_ALLOWED"), field("PROMPT_EXECUTION_LIMIT"),
                        field("EXTENSION_EXECUTION_ALLOWED"), field("API_ACCESS_ALLOWED"),
                        field("MCP_ACCESS_ALLOWED"), field("CREATED_AT"), field("UPDATED_AT"))
                .values(bundle.id(), bundle.bundleKey(), bundle.name(), bundle.description(),
                        bundle.status(), bundle.allowedProviders(), bundle.allowedPresets(),
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
                .from(table("ENTITLEMENT_BUNDLE"))
                .where(field("BUNDLE_KEY").eq(bundleKey))
                .fetchOptional(this::mapRecord);
    }

    public List<EntitlementBundle> findAllActive() {
        return dsl.select()
                .from(table("ENTITLEMENT_BUNDLE"))
                .where(field("STATUS").eq("ACTIVE"))
                .orderBy(field("CREATED_AT").desc())
                .fetch(this::mapRecord);
    }

    public void update(EntitlementBundle bundle) {
        dsl.update(table("ENTITLEMENT_BUNDLE"))
                .set(field("NAME"), bundle.name())
                .set(field("DESCRIPTION"), bundle.description())
                .set(field("STATUS"), bundle.status())
                .set(field("ALLOWED_PROVIDERS"), bundle.allowedProviders())
                .set(field("ALLOWED_PRESETS"), bundle.allowedPresets())
                .set(field("GPU_ALLOWED"), bundle.gpuAllowed())
                .set(field("REMOTE_WORKER_ALLOWED"), bundle.remoteWorkerAllowed())
                .set(field("CUSTOM_FONTS_ALLOWED"), bundle.customFontsAllowed())
                .set(field("MAX_SUBTITLE_TRACKS"), bundle.maxSubtitleTracks())
                .set(field("MAX_CONCURRENT_JOBS"), bundle.maxConcurrentJobs())
                .set(field("MONTHLY_RENDER_MINUTES"), bundle.monthlyRenderMinutes())
                .set(field("STORAGE_LIMIT_BYTES"), bundle.storageLimitBytes())
                .set(field("WATERMARK_REQUIRED"), bundle.watermarkRequired())
                .set(field("PRIORITY_QUEUE_ALLOWED"), bundle.priorityQueueAllowed())
                .set(field("BETA_EFFECTS_ALLOWED"), bundle.betaEffectsAllowed())
                .set(field("PROMPT_EXECUTION_LIMIT"), bundle.promptExecutionLimit())
                .set(field("EXTENSION_EXECUTION_ALLOWED"), bundle.extensionExecutionAllowed())
                .set(field("API_ACCESS_ALLOWED"), bundle.apiAccessAllowed())
                .set(field("MCP_ACCESS_ALLOWED"), bundle.mcpAccessAllowed())
                .set(field("UPDATED_AT"), OffsetDateTime.now())
                .where(field("BUNDLE_KEY").eq(bundle.bundleKey()))
                .execute();
    }

    private EntitlementBundle mapRecord(Record r) {
        return new EntitlementBundle(
                r.get(field("ID"), String.class),
                r.get(field("BUNDLE_KEY"), String.class),
                r.get(field("NAME"), String.class),
                r.get(field("DESCRIPTION"), String.class),
                r.get(field("STATUS"), String.class),
                r.get(field("ALLOWED_PROVIDERS"), String.class),
                r.get(field("ALLOWED_PRESETS"), String.class),
                r.get(field("GPU_ALLOWED", Boolean.class)) != null && r.get(field("GPU_ALLOWED", Boolean.class)),
                r.get(field("REMOTE_WORKER_ALLOWED", Boolean.class)) != null && r.get(field("REMOTE_WORKER_ALLOWED", Boolean.class)),
                r.get(field("CUSTOM_FONTS_ALLOWED", Boolean.class)) != null && r.get(field("CUSTOM_FONTS_ALLOWED", Boolean.class)),
                r.get(field("MAX_SUBTITLE_TRACKS"), Integer.class),
                r.get(field("MAX_CONCURRENT_JOBS"), Integer.class),
                r.get(field("MONTHLY_RENDER_MINUTES"), Long.class),
                r.get(field("STORAGE_LIMIT_BYTES"), Long.class),
                r.get(field("WATERMARK_REQUIRED", Boolean.class)) != null && r.get(field("WATERMARK_REQUIRED", Boolean.class)),
                r.get(field("PRIORITY_QUEUE_ALLOWED", Boolean.class)) != null && r.get(field("PRIORITY_QUEUE_ALLOWED", Boolean.class)),
                r.get(field("BETA_EFFECTS_ALLOWED", Boolean.class)) != null && r.get(field("BETA_EFFECTS_ALLOWED", Boolean.class)),
                r.get(field("PROMPT_EXECUTION_LIMIT"), Long.class),
                r.get(field("EXTENSION_EXECUTION_ALLOWED", Boolean.class)) != null && r.get(field("EXTENSION_EXECUTION_ALLOWED", Boolean.class)),
                r.get(field("API_ACCESS_ALLOWED", Boolean.class)) != null && r.get(field("API_ACCESS_ALLOWED", Boolean.class)),
                r.get(field("MCP_ACCESS_ALLOWED", Boolean.class)) != null && r.get(field("MCP_ACCESS_ALLOWED", Boolean.class)),
                toInstant(r.get(field("CREATED_AT"), OffsetDateTime.class)),
                toInstant(r.get(field("UPDATED_AT"), OffsetDateTime.class))
        );
    }

    private Instant toInstant(OffsetDateTime odt) {
        return odt != null ? odt.toInstant() : null;
    }
}
