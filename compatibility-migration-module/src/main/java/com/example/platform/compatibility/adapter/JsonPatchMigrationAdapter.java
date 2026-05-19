package com.example.platform.compatibility.adapter;

import com.example.platform.compatibility.domain.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Migration adapter that applies JSON Patch / JSON Merge Patch transformations.
 */
@Component
public class JsonPatchMigrationAdapter implements MigrationAdapter {
    private static final Logger log = LoggerFactory.getLogger(JsonPatchMigrationAdapter.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Override
    public String adapterKey() {
        return "json-patch";
    }

    @Override
    public boolean supports(SchemaFamily schemaFamily, SchemaVersion sourceVersion, SchemaVersion targetVersion) {
        return sourceVersion.isBefore(targetVersion);
    }

    @Override
    public MigrationPlan plan(VersionedPayload input, SchemaVersion targetVersion) {
        List<MigrationStep> steps = new ArrayList<>();
        SchemaVersion current = input.schemaVersion();

        while (current.isBefore(targetVersion)) {
            SchemaVersion next = computeNextVersion(current, targetVersion);
            steps.add(new MigrationStep(
                    current + "-to-" + next,
                    "Migrate from " + current + " to " + next,
                    adapterKey(),
                    Map.of("patchType", "json-merge-patch", "source", current.toString(), "target", next.toString())
            ));
            current = next;
        }

        return new MigrationPlan(
                UUID.randomUUID().toString(),
                input.schemaFamily(),
                input.schemaVersion(),
                targetVersion,
                steps,
                true,
                steps.size() > 2 ? "HIGH" : "LOW"
        );
    }

    @Override
    public VersionedPayload migrate(VersionedPayload input, MigrationPlan plan) {
        Map<String, Object> currentPayload = new LinkedHashMap<>(input.payload());

        for (MigrationStep step : plan.steps()) {
            log.info("Applying migration step: {}", step.description());
            currentPayload = applyPatch(currentPayload, step);
        }

        return new VersionedPayload(
                input.schemaFamily(),
                plan.targetVersion(),
                currentPayload,
                new LinkedHashMap<>(input.metadata())
        );
    }

    @Override
    public List<MigrationError> validate(VersionedPayload input) {
        List<MigrationError> errors = new ArrayList<>();
        if (input.payload() == null || input.payload().isEmpty()) {
            errors.add(new MigrationError("EMPTY_PAYLOAD", "Payload is empty", null, false));
        }
        if (input.schemaVersion() == null) {
            errors.add(new MigrationError("MISSING_VERSION", "Schema version is missing", null, false));
        }
        return errors;
    }

    @Override
    public MigrationResult dryRun(VersionedPayload input, SchemaVersion targetVersion) {
        MigrationPlan plan = plan(input, targetVersion);
        List<MigrationError> errors = validate(input);

        if (!errors.isEmpty()) {
            return new MigrationResult(
                    UUID.randomUUID().toString(), plan.migrationPlanId(), MigrationStatus.FAILED,
                    input.schemaVersion(), targetVersion, Instant.now(), Instant.now(),
                    null, errors, List.of()
            );
        }

        try {
            VersionedPayload migrated = migrate(input, plan);
            return new MigrationResult(
                    UUID.randomUUID().toString(), plan.migrationPlanId(), MigrationStatus.COMPLETED,
                    input.schemaVersion(), targetVersion, Instant.now(), Instant.now(),
                    migrated, List.of(), List.of("DRY_RUN — no changes persisted")
            );
        } catch (Exception e) {
            return new MigrationResult(
                    UUID.randomUUID().toString(), plan.migrationPlanId(), MigrationStatus.FAILED,
                    input.schemaVersion(), targetVersion, Instant.now(), Instant.now(),
                    null, List.of(new MigrationError("MIGRATION_FAILED", e.getMessage(), null, false)),
                    List.of()
            );
        }
    }

    private Map<String, Object> applyPatch(Map<String, Object> payload, MigrationStep step) {
        Map<String, Object> result = new LinkedHashMap<>(payload);
        String source = (String) step.config().get("source");
        String target = (String) step.config().get("target");

        // Apply known transformations based on schema family and version
        SchemaFamily family = inferFamily(payload);
        result = applyKnownTransformations(result, family, source, target);

        return result;
    }

    private Map<String, Object> applyKnownTransformations(Map<String, Object> payload, SchemaFamily family,
                                                           String source, String target) {
        Map<String, Object> result = new LinkedHashMap<>(payload);

        // Timeline v1 -> v2
        if (family == SchemaFamily.OTIO_TIMELINE && "1.0.0".equals(source) && "2.0.0".equals(target)) {
            result.put("schemaVersion", "2.0.0");
            if (result.containsKey("tracks")) {
                List<Map<String, Object>> tracks = (List<Map<String, Object>>) result.get("tracks");
                for (Map<String, Object> track : tracks) {
                    // Normalize track type
                    String type = (String) track.getOrDefault("type", "video");
                    track.put("type", type.toUpperCase());
                    // Migrate clips
                    if (track.containsKey("children")) {
                        List<Map<String, Object>> clips = (List<Map<String, Object>>) track.get("children");
                        for (Map<String, Object> clip : clips) {
                            if (clip.containsKey("effects")) {
                                List<Map<String, Object>> effects = (List<Map<String, Object>>) clip.get("effects");
                                for (Map<String, Object> effect : effects) {
                                    if (effect.containsKey("effectId")) {
                                        effect.put("effectKey", effect.remove("effectId"));
                                    }
                                    if (effect.containsKey("provider")) {
                                        String provider = (String) effect.remove("provider");
                                        effect.put("providerPreference", List.of(provider));
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Effect pack v1 -> v2
        if (family == SchemaFamily.EFFECT_PACK && "1.0.0".equals(source) && "2.0.0".equals(target)) {
            result.put("schemaVersion", "2.0.0");
            if (result.containsKey("effects")) {
                List<Map<String, Object>> effects = (List<Map<String, Object>>) result.get("effects");
                for (Map<String, Object> effect : effects) {
                    if (effect.containsKey("effectId")) {
                        effect.put("effectKey", effect.remove("effectId"));
                    }
                    if (effect.containsKey("parameters")) {
                        Map<String, Object> params = (Map<String, Object>) effect.remove("parameters");
                        effect.put("parameterSchema", params);
                        effect.put("defaultValues", params);
                    }
                    if (effect.containsKey("renderProviders")) {
                        effect.put("providerMappings", effect.remove("renderProviders"));
                    }
                }
            }
            result.putIfAbsent("compatibility", "2.0");
            result.putIfAbsent("allowedTiers", List.of("FREE", "PRO", "TEAM", "ENTERPRISE"));
        }

        // Render preset v1 -> v2
        if (family == SchemaFamily.RENDER_PRESET && "1.0.0".equals(source) && "2.0.0".equals(target)) {
            result.put("schemaVersion", "2.0.0");
            if (result.containsKey("resolution")) {
                String res = (String) result.remove("resolution");
                String[] parts = res.split("x");
                result.put("width", Integer.parseInt(parts[0]));
                result.put("height", Integer.parseInt(parts[1]));
            }
            if (result.containsKey("codec")) {
                String codec = (String) result.remove("codec");
                result.put("videoCodec", codec);
                result.put("audioCodec", "aac");
            }
            if (result.containsKey("tier")) {
                String tier = (String) result.remove("tier");
                result.put("allowedTiers", List.of(tier));
            }
            if (result.containsKey("watermark")) {
                boolean wm = (Boolean) result.remove("watermark");
                result.put("watermarkPolicy", wm ? "ALWAYS" : "NEVER");
            }
        }

        // Provider capability v1 -> v2
        if (family == SchemaFamily.PROVIDER_CAPABILITY && "1.0.0".equals(source) && "2.0.0".equals(target)) {
            result.put("schemaVersion", "2.0.0");
            if (result.containsKey("supportedEffects")) {
                List<String> effects = (List<String>) result.get("supportedEffects");
                List<Map<String, Object>> effectSupport = new ArrayList<>();
                for (String effect : effects) {
                    effectSupport.add(Map.of("effectKey", effect, "supported", true));
                }
                result.put("effectSupport", effectSupport);
            }
            result.putIfAbsent("maxResolution", "1920x1080");
            result.putIfAbsent("requiresGpu", false);
            result.putIfAbsent("experimental", false);
            result.putIfAbsent("fallbackProviders", List.of());
        }

        // User analytics event v1 -> v2
        if (family == SchemaFamily.USER_ANALYTICS_EVENT && "1.0.0".equals(source) && "2.0.0".equals(target)) {
            result.put("schemaVersion", "2.0.0");
            if (result.containsKey("ip")) {
                String ip = (String) result.remove("ip");
                result.put("ipHash", Integer.toHexString(ip.hashCode()));
            }
            if (result.containsKey("userAgent")) {
                String ua = (String) result.remove("userAgent");
                result.put("deviceSummary", ua.substring(0, Math.min(ua.length(), 100)));
            }
            result.putIfAbsent("consentStatus", "UNKNOWN");
        }

        // Prompt template v1 -> v2
        if (family == SchemaFamily.PROMPT_TEMPLATE && "1.0.0".equals(source) && "2.0.0".equals(target)) {
            result.put("schemaVersion", "2.0.0");
            if (result.containsKey("rawPrompt")) {
                result.put("templateBody", result.remove("rawPrompt"));
            }
            if (result.containsKey("variables")) {
                result.put("variableSchema", result.remove("variables"));
            }
            result.putIfAbsent("promptVersion", "1.0.0");
            result.putIfAbsent("auditRequired", false);
            result.putIfAbsent("patchHistory", List.of());
        }

        return result;
    }

    private SchemaFamily inferFamily(Map<String, Object> payload) {
        if (payload.containsKey("tracks")) return SchemaFamily.OTIO_TIMELINE;
        if (payload.containsKey("effects")) return SchemaFamily.EFFECT_PACK;
        if (payload.containsKey("resolution") || payload.containsKey("width")) return SchemaFamily.RENDER_PRESET;
        if (payload.containsKey("supportedEffects")) return SchemaFamily.PROVIDER_CAPABILITY;
        if (payload.containsKey("eventType")) return SchemaFamily.USER_ANALYTICS_EVENT;
        if (payload.containsKey("rawPrompt") || payload.containsKey("templateBody")) return SchemaFamily.PROMPT_TEMPLATE;
        return SchemaFamily.ARTIFACT_METADATA;
    }

    private SchemaVersion computeNextVersion(SchemaVersion current, SchemaVersion target) {
        // Simple: increment minor version
        if (current.major() == target.major()) {
            return SchemaVersion.of(current.major(), Math.min(current.minor() + 1, target.minor()), 0);
        }
        return target;
    }
}
