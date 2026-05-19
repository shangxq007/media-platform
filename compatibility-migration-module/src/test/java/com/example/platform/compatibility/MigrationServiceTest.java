package com.example.platform.compatibility;

import com.example.platform.compatibility.adapter.*;
import com.example.platform.compatibility.domain.*;
import com.example.platform.compatibility.policy.MigrationPolicyService;
import com.example.platform.compatibility.service.MigrationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class MigrationServiceTest {

    private MigrationService migrationService;
    private JsonPatchMigrationAdapter jsonPatchAdapter;
    private MigrationPolicyService policyService;

    @BeforeEach
    void setUp() {
        jsonPatchAdapter = new JsonPatchMigrationAdapter();
        JavaMigrationAdapter javaAdapter = new JavaMigrationAdapter();
        ExtensionScriptMigrationAdapter scriptAdapter = new ExtensionScriptMigrationAdapter();
        policyService = new MigrationPolicyService();
        migrationService = new MigrationService(jsonPatchAdapter, javaAdapter, scriptAdapter, policyService);
    }

    @Test
    void dryRunTimelineV1ToV2() {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("schemaVersion", "1.0.0");
        Map<String, Object> track = new LinkedHashMap<>();
        track.put("name", "Video 1");
        track.put("type", "video");
        Map<String, Object> clip = new LinkedHashMap<>();
        clip.put("name", "clip_1");
        Map<String, Object> effect = new LinkedHashMap<>();
        effect.put("effectId", "fade_in");
        effect.put("provider", "javacv");
        clip.put("effects", List.of(effect));
        track.put("children", List.of(clip));
        payload.put("tracks", List.of(track));

        VersionedPayload input = new VersionedPayload(
                SchemaFamily.OTIO_TIMELINE, SchemaVersion.of(1, 0, 0),
                payload, Map.of()
        );

        // Test adapter directly (bypass policy for unit test)
        MigrationResult result = jsonPatchAdapter.dryRun(input, SchemaVersion.of(2, 0, 0));

        assertNotNull(result);
        assertNotNull(result.migratedPayload());
        assertEquals("2.0.0", result.migratedPayload().payload().get("schemaVersion"));
    }

    @Test
    void migrateEffectPackV1ToV2() {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("schemaVersion", "1.0.0");
        Map<String, Object> effect = new LinkedHashMap<>();
        effect.put("effectId", "blur");
        effect.put("parameters", Map.of("radius", 2));
        effect.put("renderProviders", List.of("ofx"));
        payload.put("effects", List.of(effect));

        VersionedPayload input = new VersionedPayload(
                SchemaFamily.EFFECT_PACK, SchemaVersion.of(1, 0, 0),
                payload, Map.of()
        );

        MigrationResult result = jsonPatchAdapter.dryRun(input, SchemaVersion.of(2, 0, 0));

        assertNotNull(result.migratedPayload());
        assertEquals("2.0.0", result.migratedPayload().payload().get("schemaVersion"));
        List<Map<String, Object>> effects = (List<Map<String, Object>>) result.migratedPayload().payload().get("effects");
        assertNotNull(effects.get(0).get("effectKey"));
        assertNull(effects.get(0).get("effectId"));
        // parameters -> parameterSchema + defaultValues
        assertNotNull(effects.get(0).get("parameterSchema"));
        assertNotNull(effects.get(0).get("defaultValues"));
        // renderProviders -> providerMappings
        assertNotNull(effects.get(0).get("providerMappings"));
    }

    @Test
    void migrateRenderPresetV1ToV2() {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("schemaVersion", "1.0.0");
        payload.put("resolution", "1920x1080");
        payload.put("codec", "h264");
        payload.put("tier", "PRO");
        payload.put("watermark", true);

        VersionedPayload input = new VersionedPayload(
                SchemaFamily.RENDER_PRESET, SchemaVersion.of(1, 0, 0),
                payload, Map.of()
        );

        MigrationResult result = jsonPatchAdapter.dryRun(input, SchemaVersion.of(2, 0, 0));

        assertNotNull(result.migratedPayload());
        Map<String, Object> migrated = result.migratedPayload().payload();
        assertNull(migrated.get("resolution"));
        assertEquals(1920, migrated.get("width"));
        assertEquals(1080, migrated.get("height"));
        assertEquals("ALWAYS", migrated.get("watermarkPolicy"));
    }

    @Test
    void migrateAnalyticsEventV1ToV2() {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("schemaVersion", "1.0.0");
        payload.put("eventType", "page_view");
        payload.put("ip", "192.168.1.1");
        payload.put("userAgent", "Mozilla/5.0");

        VersionedPayload input = new VersionedPayload(
                SchemaFamily.USER_ANALYTICS_EVENT, SchemaVersion.of(1, 0, 0),
                payload, Map.of()
        );

        MigrationResult result = jsonPatchAdapter.dryRun(input, SchemaVersion.of(2, 0, 0));

        assertNotNull(result.migratedPayload());
        Map<String, Object> migrated = result.migratedPayload().payload();
        assertNull(migrated.get("ip"));
        assertNotNull(migrated.get("ipHash"));
        assertNull(migrated.get("userAgent"));
        assertNotNull(migrated.get("deviceSummary"));
    }

    @Test
    void migratePromptTemplateV1ToV2() {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("schemaVersion", "1.0.0");
        payload.put("rawPrompt", "Hello {{name}}");
        payload.put("variables", List.of("name"));

        VersionedPayload input = new VersionedPayload(
                SchemaFamily.PROMPT_TEMPLATE, SchemaVersion.of(1, 0, 0),
                payload, Map.of()
        );

        MigrationResult result = jsonPatchAdapter.dryRun(input, SchemaVersion.of(2, 0, 0));

        assertNotNull(result.migratedPayload());
        Map<String, Object> migrated = result.migratedPayload().payload();
        assertEquals("Hello {{name}}", migrated.get("templateBody"));
        assertNull(migrated.get("rawPrompt"));
    }

    @Test
    void versionComparisonWorks() {
        SchemaVersion v1 = SchemaVersion.of(1, 0, 0);
        SchemaVersion v2 = SchemaVersion.of(2, 0, 0);
        SchemaVersion v1_1 = SchemaVersion.of(1, 1, 0);

        assertTrue(v1.isBefore(v2));
        assertTrue(v2.isAfter(v1));
        assertTrue(v1_1.isAfter(v1));
        assertEquals(0, v1.compareTo(SchemaVersion.of(1, 0, 0)));
    }

    @Test
    void versionParsingWorks() {
        SchemaVersion v = SchemaVersion.parse("2.1.3");
        assertEquals(2, v.major());
        assertEquals(1, v.minor());
        assertEquals(3, v.patch());
        assertEquals("2.1.3", v.toString());
    }

    @Test
    void schemaVersionRejectsInvalid() {
        assertThrows(IllegalArgumentException.class, () -> SchemaVersion.parse("invalid"));
    }

    @Test
    void policyDecisionBlocksCrossMajorWithoutForce() {
        MigrationPolicyService.MigrationPolicyContext ctx = new MigrationPolicyService.MigrationPolicyContext(
                SchemaFamily.OTIO_TIMELINE, SchemaVersion.of(1, 0, 0), SchemaVersion.of(2, 0, 0),
                "tenant-1", "PRO", false, 1000, false, false, false
        );

        MigrationPolicyService.MigrationPolicyDecision decision = policyService.decide(ctx);

        assertFalse(decision.autoMigratable());
        assertTrue(decision.conflicts().stream().anyMatch(c -> c.conflictCode().equals("CROSS_MAJOR_VERSION")));
    }

    @Test
    void policyDecisionAllowsSimpleMigration() {
        MigrationPolicyService.MigrationPolicyContext ctx = new MigrationPolicyService.MigrationPolicyContext(
                SchemaFamily.OTIO_TIMELINE, SchemaVersion.of(1, 0, 0), SchemaVersion.of(1, 1, 0),
                "tenant-1", "PRO", false, 1000, false, false, false
        );

        MigrationPolicyService.MigrationPolicyDecision decision = policyService.decide(ctx);

        assertTrue(decision.autoMigratable());
        assertEquals("json-patch", decision.selectedAdapter());
    }

    @Test
    void policyDecisionRejectsScriptForNonExperimental() {
        MigrationPolicyService.MigrationPolicyContext ctx = new MigrationPolicyService.MigrationPolicyContext(
                SchemaFamily.OTIO_TIMELINE, SchemaVersion.of(1, 0, 0), SchemaVersion.of(1, 1, 0),
                "tenant-1", "PRO", false, 1000, true, false, false
        );

        MigrationPolicyService.MigrationPolicyDecision decision = policyService.decide(ctx);

        assertFalse(decision.autoMigratable());
    }

    @Test
    void extensionScriptAdapterNotEnabledByDefault() {
        ExtensionScriptMigrationAdapter adapter = new ExtensionScriptMigrationAdapter();
        assertFalse(adapter.supports(SchemaFamily.OTIO_TIMELINE, SchemaVersion.of(1, 0, 0), SchemaVersion.of(2, 0, 0)));
    }

    @Test
    void wasmAdapterNotEnabled() {
        WasmMigrationAdapter adapter = new WasmMigrationAdapter();
        assertFalse(adapter.supports(SchemaFamily.OTIO_TIMELINE, SchemaVersion.of(1, 0, 0), SchemaVersion.of(2, 0, 0)));
        List<MigrationError> errors = adapter.validate(null);
        assertFalse(errors.isEmpty());
    }

    @Test
    void dryRunDoesNotPersistChanges() {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("schemaVersion", "1.0.0");
        payload.put("tracks", List.of());

        VersionedPayload input = new VersionedPayload(
                SchemaFamily.OTIO_TIMELINE, SchemaVersion.of(1, 0, 0),
                new LinkedHashMap<>(payload), Map.of()
        );

        MigrationResult result = jsonPatchAdapter.dryRun(input, SchemaVersion.of(2, 0, 0));

        // Original should be unchanged
        assertEquals("1.0.0", input.schemaVersion().toString());
        // Result should indicate dry run
        assertTrue(result.warnings().stream().anyMatch(w -> w.contains("DRY_RUN")));
    }
}
