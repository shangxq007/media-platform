package com.example.platform;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.platform.extension.app.ExtensionRegistryService;
import com.example.platform.extension.runtime.PluginRuntimeProviderBinding;
import com.example.platform.render.domain.producer.Producer;
import com.example.platform.render.domain.producer.ProducerContext;
import com.example.platform.render.domain.producer.ProducerResult;
import com.example.platform.render.infrastructure.asset.provider.RemotionProducer;
import com.example.platform.render.infrastructure.renderplan.MLTTool;
import com.example.platform.render.infrastructure.renderplan.RemotionTool;
import com.example.platform.render.infrastructure.renderplan.ToolRouter;
import com.example.platform.security.PhaseZeroContainmentPolicy;
import com.example.platform.security.RuntimeMvcRouteDiscovery;
import com.example.platform.security.RuntimeRoutePolicyVerifier;
import com.example.platform.shared.test.PostgresTestContainerSupport;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles({"test", "preview"})
class MvcRouteInventoryTest extends PostgresTestContainerSupport {

    @Autowired
    private RuntimeMvcRouteDiscovery routeDiscovery;

    @Autowired
    private RuntimeRoutePolicyVerifier routePolicyVerifier;

    @Autowired
    private RequestMappingHandlerMapping requestMappingHandlerMapping;

    @Autowired
    private ApplicationContext applicationContext;

    @Autowired
    private ExtensionRegistryService extensionRegistryService;

    @Test
    void liveApplicationRouteUniverseIsNonEmptyAndCompletelyClassified() throws Exception {
        var discovered = routeDiscovery.discoverApplicationRoutes();
        assertFalse(discovered.isEmpty(),
                "an empty RequestMapping universe cannot establish containment completeness");

        var report = routePolicyVerifier.verify();
        assertEquals(discovered, report.routes());
        assertEquals(report.routeCount(),
                report.classificationCounts().values().stream().mapToLong(Long::longValue).sum());

        for (PhaseZeroContainmentPolicy.Classification classification
                : PhaseZeroContainmentPolicy.Classification.values()) {
            assertTrue(report.classificationCounts().get(classification) > 0,
                    () -> "runtime inventory does not exercise classification " + classification);
        }

        StringBuilder inventory = new StringBuilder()
                .append("ROUTE_COUNT=").append(report.routeCount()).append('\n')
                .append("CLASSIFICATION_COUNTS=").append(report.classificationCounts()).append('\n');
        report.routes().forEach(route -> inventory
                .append(PhaseZeroContainmentPolicy.classify(route.method(), route.path()).orElseThrow())
                .append(" | ")
                .append(route.displayName())
                .append('\n'));
        Files.writeString(Path.of("/tmp/mvc-route-policy-inventory.txt"), inventory.toString());
    }

    @Test
    void promptRoutesHaveOneCanonicalControllerAndNoAmbiguousOrShadowMappings() {
        Set<Class<?>> promptControllers = new HashSet<>();
        Set<Class<?>> shadowControllers = new HashSet<>();
        Map<String, Integer> routeOwners = new HashMap<>();

        requestMappingHandlerMapping.getHandlerMethods().forEach((mapping, handler) -> {
            Class<?> controllerType = handler.getBeanType();
            String packageName = controllerType.getPackageName();
            if (!packageName.equals("com.example.platform.prompt.api")
                    && !packageName.equals("com.example.platform.web.prompt")) {
                return;
            }
            promptControllers.add(controllerType);
            if (packageName.equals("com.example.platform.prompt.api")) {
                shadowControllers.add(controllerType);
            }
            Set<RequestMethod> methods = mapping.getMethodsCondition().getMethods();
            Set<String> methodNames = methods.isEmpty()
                    ? Set.of("ANY")
                    : methods.stream().map(Enum::name).collect(java.util.stream.Collectors.toSet());
            mapping.getPatternValues().forEach(path -> methodNames.forEach(method ->
                    routeOwners.merge(method + " " + path, 1, Integer::sum)));
        });

        long ambiguousRouteCount = routeOwners.values().stream().filter(count -> count > 1).count();
        assertEquals(1, promptControllers.size(), "PROMPT_CONTROLLER_COUNT actual");
        assertEquals(0, ambiguousRouteCount, "AMBIGUOUS_PROMPT_ROUTE_COUNT");
        assertEquals(0, shadowControllers.size(), "SHADOW_PROMPT_CONTROLLER_COUNT");
        assertTrue(routeOwners.containsKey("POST /api/prompts/templates"));
        assertTrue(routeOwners.containsKey("POST /api/mcp/prompts/templates"),
                "the canonical controller must retain explicit MCP behavior");
        assertFalse(routeOwners.containsKey("POST /api/mcp/prompts/executions"),
                "controller consolidation must not expose request-controlled execution authority to MCP");
    }

    @Test
    void finalHostileUnsafeRouteFamiliesAreExactlyDiscoveredAndDisabledContained() {
        Set<String> expected = Set.of(
                "GET /api/audit/compliance/overview",
                "HEAD /api/audit/compliance/overview",
                "GET /api/audit/compliance/records",
                "HEAD /api/audit/compliance/records",
                "POST /api/audit/compliance/records",
                "GET /api/audit/compliance/records/category/{category}",
                "HEAD /api/audit/compliance/records/category/{category}",
                "GET /api/audit/compliance/records/resource",
                "HEAD /api/audit/compliance/records/resource",
                "POST /api/navigation/preview",
                "POST /api/tenants/{tenantId}/projects/{projectId}/upload/raw-media",
                "GET /api/semantic/explain/{jobId}",
                "HEAD /api/semantic/explain/{jobId}",
                "GET /api/semantic/explain/{jobId}/ai",
                "HEAD /api/semantic/explain/{jobId}/ai",
                "GET /api/semantic/status/{jobId}",
                "HEAD /api/semantic/status/{jobId}",
                "GET /api/semantic/cost/{jobId}",
                "HEAD /api/semantic/cost/{jobId}",
                "GET /api/storage/{storageReferenceId}",
                "HEAD /api/storage/{storageReferenceId}",
                "POST /api/feature-flags/evaluate",
                "POST /api/feature-flags/batch-evaluate");

        var unsafeRoutes = routeDiscovery.discoverApplicationRoutes().stream()
                .filter(MvcRouteInventoryTest::isFinalHostileUnsafeRoute)
                .toList();
        Set<String> actual = unsafeRoutes.stream()
                .map(route -> route.method() + " " + route.path())
                .collect(java.util.stream.Collectors.toSet());

        assertEquals(expected, actual,
                "every independently discovered unsafe mapping and alias must remain manifested");
        unsafeRoutes.forEach(route -> assertEquals(
                PhaseZeroContainmentPolicy.Classification.DISABLED_CONTAINED,
                PhaseZeroContainmentPolicy.classify(route.method(), route.path()).orElseThrow(),
                route::displayName));

        assertEquals(PhaseZeroContainmentPolicy.Classification.ADMIN_ONLY,
                PhaseZeroContainmentPolicy.classify(
                                org.springframework.http.HttpMethod.POST,
                                "/api/admin/navigation/preview")
                        .orElseThrow(),
                "the canonical navigation preview route must remain admin-only");
        assertEquals(PhaseZeroContainmentPolicy.Classification.INTERNAL_CONTROL_PLANE,
                PhaseZeroContainmentPolicy.classify(
                                org.springframework.http.HttpMethod.GET,
                                "/api/storage/providers")
                        .orElseThrow(),
                "the exact storage provider registry remains contained as control-plane access");
    }

    @Test
    void fakeAiProvidersAreAbsentAndMissingRealProvidersFailClosed() {
        Set<String> forbiddenFakeProviders = Set.of(
                "VisionProviderExtension",
                "TesseractOcrProviderExtension",
                "EmbeddingProviderExtension",
                "WhisperProducer");
        Map<String, PluginRuntimeProviderBinding> productionVisibleBindings =
                applicationContext.getBeansOfType(PluginRuntimeProviderBinding.class);
        long fakeBindingBeanCount = productionVisibleBindings.values().stream()
                .filter(binding -> forbiddenFakeProviders.contains(binding.getClass().getSimpleName()))
                .count();

        assertTrue(productionVisibleBindings.values().stream()
                        .noneMatch(binding -> forbiddenFakeProviders.contains(binding.getClass().getSimpleName())
                                && "FULLY_TRUSTED".equals(binding.trustLevel().name())),
                "FAKE_PROVIDER_IS_NOT_FULLY_TRUSTED_V1");
        for (String providerKey : Set.of("vision-default", "tesseract", "embedding-default")) {
            assertTrue(extensionRegistryService.getExtension(providerKey).isEmpty(),
                    () -> "MISSING_REAL_PROVIDER_FAILS_CLOSED_V1: " + providerKey);
        }

        Map<String, Producer> producers = applicationContext.getBeansOfType(Producer.class);
        long fakeProducerBeanCount = producers.values().stream()
                .filter(producer -> forbiddenFakeProviders.contains(producer.getClass().getSimpleName()))
                .count();
        assertEquals(0, fakeBindingBeanCount + fakeProducerBeanCount,
                "FAKE_PROVIDER_IS_NOT_PRODUCTION_AVAILABLE_V1: every known fake bean must be absent");
        Set<String> fakeProviderProducerIds = Set.of(
                "vision-default", "tesseract-ocr", "embedding-default", "whisper-asr");
        long fabricatedSuccessPathCount = producers.values().stream()
                .filter(producer -> fakeProviderProducerIds.contains(producer.producerId()))
                .filter(producer -> producer.execute(ProducerContext.of(
                        "phase0-proof", "system", "phase0-proof", List.of(), List.of())).success())
                .count();
        assertEquals(0, fabricatedSuccessPathCount,
                "production fake AI producer paths must fail closed rather than fabricate success");
    }

    @Test
    void finalHostileExternalStubsHaveZeroSuccessPathsAndFabricateNoArtifacts() {
        RemotionProducer producer = applicationContext.getBean(RemotionProducer.class);
        RemotionTool remotionTool = applicationContext.getBean(RemotionTool.class);
        MLTTool mltTool = applicationContext.getBean(MLTTool.class);

        String remotionNode = "phase0-remotion-" + UUID.randomUUID();
        String mltNode = "phase0-mlt-" + UUID.randomUUID();
        Path remotionOutput = Path.of("/tmp/renderplan-output", remotionNode, "output.mp4");
        Path mltOutput = Path.of("/tmp/renderplan-output", mltNode, "output.mp4");
        assertFalse(Files.exists(remotionOutput));
        assertFalse(Files.exists(mltOutput));

        ProducerResult producerResult = producer.execute(ProducerContext.of(
                "phase0-proof", "system", "phase0-proof", List.of(), List.of()));
        ToolRouter.ToolResult remotionResult = remotionTool.execute(
                remotionNode, "SCENE", Map.of(), Map.of());
        ToolRouter.ToolResult mltResult = mltTool.execute(
                mltNode, "TRANSITION", Map.of(), Map.of());

        long fakeExternalSuccessPathCount = 0;
        if (producerResult.success() || !producerResult.producedProductIds().isEmpty()) {
            fakeExternalSuccessPathCount++;
        }
        if (remotionTool.isAvailable() || remotionResult.success()
                || remotionResult.outputUri() != null || Files.exists(remotionOutput)) {
            fakeExternalSuccessPathCount++;
        }
        if (mltTool.isAvailable() || mltResult.success()
                || mltResult.outputUri() != null || Files.exists(mltOutput)) {
            fakeExternalSuccessPathCount++;
        }

        assertEquals(0, fakeExternalSuccessPathCount, "FAKE_EXTERNAL_SUCCESS_PATH_COUNT");
        assertFalse(producerResult.error().isBlank());
        assertFalse(remotionResult.error().isBlank());
        assertFalse(mltResult.error().isBlank());
        assertFalse(Files.exists(remotionOutput), "Remotion must not fabricate an output artifact");
        assertFalse(Files.exists(mltOutput), "MLT must not fabricate an output artifact");
    }

    private static boolean isFinalHostileUnsafeRoute(
            RuntimeMvcRouteDiscovery.RuntimeRoute route) {
        return switch (route.controller()) {
            case "com.example.platform.audit.api.AuditController" ->
                    route.path().startsWith("/api/audit/compliance/");
            case "com.example.platform.web.navigation.NavigationController" ->
                    route.path().equals("/api/navigation/preview");
            case "com.example.platform.ingest.api.RawMediaUploadController" ->
                    route.path().startsWith("/api/tenants/")
                            && route.path().contains("/upload/");
            case "com.example.platform.render.infrastructure.semantic.SemanticApi" ->
                    route.path().startsWith("/api/semantic/");
            case "com.example.platform.web.assets.StorageRuntimeController" ->
                    route.path().startsWith("/api/storage/");
            case "com.example.platform.policy.featureflag.FeatureFlagController" ->
                    route.path().startsWith("/api/feature-flags/");
            default -> false;
        };
    }
}
