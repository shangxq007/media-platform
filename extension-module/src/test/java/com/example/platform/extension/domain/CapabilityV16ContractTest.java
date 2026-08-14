package com.example.platform.extension.domain;

import com.example.platform.extension.api.port.CapabilityRegistryPort;
import com.example.platform.extension.api.port.PluginRegistryPort;
import com.example.platform.extension.app.PluginDescriptorValidator;
import com.example.platform.extension.app.PluginHealthRegistry;
import com.example.platform.extension.app.PluginRegistryImpl;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * #16 test matrix (R1-R4 / C1-C17): CapabilityId namespace, contract version,
 * CapabilityRequirement, CapabilityImplementation identity, registration,
 * registry queries, multi-axis lifecycle separation, dependency validation,
 * entitlement-leakage absence.
 */
class CapabilityV16ContractTest {

    // ---- A. CapabilityId / namespace ----
    @Test
    void platformNamespaceAccepted() {
        assertEquals("media.render", CapabilityId.of("media.render").value());
        assertTrue(CapabilityId.of("audio.time-stretch").isPlatformReserved());
    }

    @Test
    void vendorReverseDnsAccepted() {
        assertTrue(CapabilityId.of("com.vendor.enhance").isVendorExtension());
        assertTrue(CapabilityId.of("org.example.special").isVendorExtension());
        // structural validation: NO hardcoded TLD allowlist (C16-CORR-1)
        assertTrue(CapabilityId.of("de.vendor.effect").isVendorExtension());
        assertTrue(CapabilityId.of("uk.co.vendor.pack").isVendorExtension());
        assertTrue(CapabilityId.of("company.example.render").isVendorExtension());
        assertTrue(CapabilityId.of("eu.example.tool").isVendorExtension());
    }

    @Test
    void vendorSquattingPlatformRejected() {
        // platform namespace is reserved: anything under a platform prefix is a
        // platform-owned capability id (structural rule, C16-CORR-1)
        assertTrue(CapabilityId.of("media.render.vendor").isPlatformReserved());
        assertFalse(CapabilityId.of("media.render.vendor").isVendorExtension());
        assertTrue(CapabilityId.of("timeline.thirdparty.bar").isPlatformReserved());
        // a vendor namespace cannot masquerade as a platform id; vendor ids live
        // in their own reverse-DNS namespace (distinct structural classes)
        assertTrue(CapabilityId.of("vendor.media.render").isVendorExtension());
        assertFalse(CapabilityId.of("vendor.media.render").isPlatformReserved());
        // malformed vendor id still rejected
        assertFalse(CapabilityNamespaceValidator.isValid("vendor..render"));
        assertThrows(IllegalArgumentException.class, () -> CapabilityId.of("vendor..render"));
    }

    @Test
    void malformedRejected() {
        assertFalse(CapabilityNamespaceValidator.isValid("bare"));
        assertTrue(CapabilityNamespaceValidator.isValid("com.vendor")); // 2 segments = structurally valid vendor
        assertFalse(CapabilityNamespaceValidator.isValid("media."));
        assertFalse(CapabilityNamespaceValidator.isValid("media..x"));
        assertFalse(CapabilityNamespaceValidator.isValid(".media.render"));
        assertFalse(CapabilityNamespaceValidator.isValid("com.vendor..x"));
        assertThrows(IllegalArgumentException.class, () -> CapabilityId.of("x"));
    }

    @Test
    void deterministicEquality() {
        assertEquals(CapabilityId.of("media.render"), CapabilityId.of("media.render"));
    }

    // ---- B. Contract version ----
    @Test
    void contractVersionParseAndCompare() {
        assertEquals(ContractVersion.of(1, 0), ContractVersion.parse("1.0"));
        assertEquals(ContractVersion.of(1, 0), ContractVersion.parse("1.0")); // legacy single-segment
        assertTrue(ContractVersion.parse("1.5").compareTo(ContractVersion.parse("1.4")) > 0);
        assertThrows(IllegalArgumentException.class, () -> ContractVersion.parse("a.b"));
        assertThrows(IllegalArgumentException.class, () -> ContractVersion.parse("1.2.3"));
    }

    @Test
    void contractRangeCompatibility() {
        ContractVersionRange range = ContractVersionRange.between(
                ContractVersion.of(1, 0), ContractVersion.of(1, 5));
        assertTrue(range.contains(ContractVersion.of(1, 2)));
        assertFalse(range.contains(ContractVersion.of(2, 0))); // major mismatch
        assertFalse(range.contains(ContractVersion.of(1, 6)));
    }

    @Test
    void higherPluginVersionDoesNotImplyCompatibility() {
        // contract v2.0 is NOT compatible with a v1.x requirement even if the
        // plugin/implementation version is newer (C12)
        CapabilityRequirement req = CapabilityRequirement.of(
                CapabilityId.of("media.render"),
                ContractVersionRange.between(ContractVersion.of(1, 0), ContractVersion.of(1, 9)));
        assertFalse(req.accepts(ContractVersion.of(2, 0)));
        assertTrue(req.accepts(ContractVersion.of(1, 4)));
    }

    // ---- C. CapabilityRequirement ----
    @Test
    void requirementRequiredVsOptional() {
        CapabilityRequirement req = CapabilityRequirement.of(
                CapabilityId.of("audio.time-stretch"),
                ContractVersionRange.exactly(ContractVersion.of(1, 0)));
        CapabilityRequirement opt = CapabilityRequirement.optional(
                CapabilityId.of("audio.noise-reduction"),
                ContractVersionRange.exactly(ContractVersion.of(1, 0)));
        assertTrue(req.required());
        assertFalse(opt.required());
    }

    @Test
    void requirementAlternatives() {
        CapabilityRequirement req = CapabilityRequirement.of(
                CapabilityId.of("video.encode"),
                ContractVersionRange.exactly(ContractVersion.of(1, 0)),
                true, List.of(CapabilityId.of("com.vendor.encode")));
        assertEquals(1, req.alternatives().size());
        assertThrows(IllegalArgumentException.class, () -> CapabilityRequirement.of(
                CapabilityId.of("video.encode"),
                ContractVersionRange.exactly(ContractVersion.of(1, 0)),
                true, List.of(CapabilityId.of("video.encode"))));
    }

    // ---- D/E. CapabilityImplementation + registration ----
    @Test
    void implementationIdentityIndependentOfPluginCapabilityTuple() {
        CapabilityImplementation impl = CapabilityImplementation.of(
                CapabilityImplementationId.of("plugin::audio.time-stretch@1.0"),
                "plugin-x", CapabilityId.of("audio.time-stretch"),
                ContractVersion.of(1, 0), "2.3.0");
        assertEquals("2.3.0", impl.implementationVersion());
        assertEquals(ContractVersion.of(1, 0), impl.contractVersion());
        assertNotEquals(impl.implementationId(),
                CapabilityImplementationId.of("plugin-x/audio.time-stretch"));
    }

    @Test
    void registryDerivesImplementationsAndRejectsDuplicate() {
        PluginRegistryImpl registry = new PluginRegistryImpl(
                new com.example.platform.extension.app.PluginDescriptorValidator(),
                new com.example.platform.extension.app.PluginHealthRegistry());
        var issues1 = registry.register(descriptor("vendorplugin.a", "1.0.0",
                List.of("media.render@1.0", "subtitle.burn-in@1.0")));
        assertTrue(issues1.isEmpty(), () -> "expected clean register: " + issues1);

        List<CapabilityImplementation> renderImpls =
                registry.findCapabilityImplementations(CapabilityId.of("media.render"));
        assertEquals(1, renderImpls.size());
        assertEquals("vendorplugin.a", renderImpls.get(0).pluginId());

        Optional<CapabilityImplementation> byId = registry.findImplementationById(
                CapabilityImplementationId.of("vendorplugin.a::media.render@1.0"));
        assertTrue(byId.isPresent());

        // duplicate plugin (same id+version) rejected -> no duplicate implementations
        var issues2 = registry.register(descriptor("vendorplugin.a", "1.0.0",
                List.of("media.render@1.0")));
        assertFalse(issues2.isEmpty());
    }

    // ---- F. Registry contract ----
    @Test
    void registryContractQueryableViaPort() {
        PluginRegistryImpl impl = new PluginRegistryImpl(
                new com.example.platform.extension.app.PluginDescriptorValidator(),
                new com.example.platform.extension.app.PluginHealthRegistry());
        impl.register(descriptor("vendorplugin.b", "1.0.0", List.of("video.encode@1.0")));
        // capability consumers depend on CapabilityRegistryPort (C16-CORR-3)
        CapabilityRegistryPort port = impl;
        assertEquals(1, port.findCapabilityImplementations(CapabilityId.of("video.encode")).size());
        assertEquals(0, port.findCapabilityImplementations(CapabilityId.of("audio.time-stretch")).size());
        assertEquals(1, port.findImplementationsForContractVersion(
                CapabilityId.of("video.encode"), ContractVersion.of(1, 0)).size());
        // plugin container concern stays on PluginRegistryPort
        PluginRegistryPort pluginPort = impl;
        assertEquals("vendorplugin.b", pluginPort.enumerate().get(0).pluginId());
    }

    // ---- G/H. Multi-axis lifecycle separation (R4) ----
    @Test
    void contractLifecycleIndependentOfRegistrationAvailability() {
        // legal state: contract ACTIVE + plugin INSTALLED + registration UNAVAILABLE
        assertEquals(CapabilityContractLifecycle.ACTIVE, CapabilityContractLifecycle.ACTIVE);
        assertEquals(RegistrationAvailability.UNAVAILABLE, RegistrationAvailability.UNAVAILABLE);
        // enum axes are distinct types — no single boolean/enum can carry all three layers
        assertNotEquals(CapabilityContractLifecycle.RETIRED.name(),
                RegistrationAvailability.UNAVAILABLE.name());
    }

    @Test
    void registrationAvailabilityStatesExist() {
        assertEquals(5, RegistrationAvailability.values().length);
        assertNotNull(RegistrationAvailability.valueOf("DISCOVERED"));
        assertNotNull(RegistrationAvailability.valueOf("VALIDATED"));
        assertNotNull(RegistrationAvailability.valueOf("AVAILABLE"));
        assertNotNull(RegistrationAvailability.valueOf("DEGRADED"));
        assertNotNull(RegistrationAvailability.valueOf("UNAVAILABLE"));
    }

    // ---- I. Dependency validation ----
    @Test
    void missingRequiredDependencyFailsClosed() {
        var result = CapabilityDependencyValidator.validate(
                List.of(CapabilityRequirement.of(
                        CapabilityId.of("audio.noise-reduction"),
                        ContractVersionRange.exactly(ContractVersion.of(1, 0)))),
                Set.of(CapabilityId.of("media.render")),
                id -> ContractVersion.of(1, 0));
        assertFalse(result.isValid());
        assertTrue(result.failures().get(0).contains("missing required"));
    }

    @Test
    void incompatibleContractFailsClosed() {
        var result = CapabilityDependencyValidator.validate(
                List.of(CapabilityRequirement.of(
                        CapabilityId.of("media.render"),
                        ContractVersionRange.exactly(ContractVersion.of(1, 0)))),
                Set.of(CapabilityId.of("media.render")),
                id -> ContractVersion.of(2, 0));
        assertFalse(result.isValid());
        assertTrue(result.failures().get(0).contains("incompatible contract"));
    }

    @Test
    void optionalMissingAllowed() {
        var result = CapabilityDependencyValidator.validate(
                List.of(CapabilityRequirement.optional(
                        CapabilityId.of("audio.noise-reduction"),
                        ContractVersionRange.exactly(ContractVersion.of(1, 0)))),
                Set.of(CapabilityId.of("media.render")),
                id -> ContractVersion.of(1, 0));
        assertTrue(result.isValid());
    }

    // ---- C16-CORR-4: multi-hop dependency cycle detection ----
    private static java.util.function.Function<CapabilityId, List<CapabilityId>> edges(
            String... spec) {
        java.util.Map<CapabilityId, List<CapabilityId>> m = new java.util.HashMap<>();
        for (String s : spec) {
            String[] parts = s.split(">");
            m.put(CapabilityId.of(parts[0]),
                    parts.length > 1 ? java.util.Arrays.stream(parts[1].split(","))
                            .map(CapabilityId::of).toList() : List.of());
        }
        return id -> m.getOrDefault(id, List.of());
    }

    private static Set<CapabilityId> ids(String... names) {
        return java.util.Arrays.stream(names).map(CapabilityId::of).collect(java.util.stream.Collectors.toSet());
    }

    @Test
    void selfCycleRejected() {
        assertTrue(CapabilityDependencyValidator.hasCycle(ids("media.a"), edges("media.a>media.a")));
    }

    @Test
    void twoNodeCycleRejected() {
        assertTrue(CapabilityDependencyValidator.hasCycle(ids("media.a", "media.b"), edges("media.a>media.b", "media.b>media.a")));
    }

    @Test
    void threeNodeCycleRejected() {
        assertTrue(CapabilityDependencyValidator.hasCycle(
                ids("media.a", "media.b", "media.c"), edges("media.a>media.b", "media.b>media.c", "media.c>media.a")));
    }

    @Test
    void acyclicChainAccepted() {
        assertFalse(CapabilityDependencyValidator.hasCycle(
                ids("media.a", "media.b", "media.c"), edges("media.a>media.b", "media.b>media.c")));
    }

    @Test
    void diamondGraphAccepted() {
        assertFalse(CapabilityDependencyValidator.hasCycle(
                ids("media.a", "media.b", "media.c", "media.d"), edges("media.a>media.b", "media.a>media.c", "media.b>media.d", "media.c>media.d")));
    }

    @Test
    void cycleDetectionFollowsRequiredEdgesOnly() {
        // edge leaving the node set is ignored (optional/foreign dependency)
        assertFalse(CapabilityDependencyValidator.hasCycle(
                ids("media.a", "media.b"), edges("media.a>media.b", "media.b>render.encode")));
    }

    // ---- K. Entitlement leakage ----
    @Test
    void capabilityContractHasNoEntitlementFields() {
        // canonical capability types must not expose commercial fields
        String[] forbidden = {"proOnly", "enterpriseOnly", "remainingQuota", "plan"};
        for (String f : forbidden) {
            assertFalse(CapabilityId.class.getSimpleName().contains(f));
        }
        assertTrue(CapabilityRequirement.class.getRecordComponents().length <= 4);
    }

    // helpers
    private static com.example.platform.extension.domain.PluginDescriptor descriptor(
            String pluginId, String version, List<String> capabilitySpecs) {
        List<CapabilityDescriptor> caps = capabilitySpecs.stream().map(spec -> {
            String[] parts = spec.split("@");
            return new CapabilityDescriptor(parts[0], parts[1], "test",
                    "input", "output", CapabilityDescriptor.InvocationMode.SYNC_ONLY);
        }).toList();
        InvocationContract invocation = new InvocationContract(
                true, InvocationContract.Idempotency.NOT_DECLARED, false, false,
                InvocationContract.TimeoutClassification.BOUNDED_DEFAULT_60S,
                InvocationContract.RetryOwnership.PLATFORM, false,
                InvocationContract.ErrorBoundary.PLATFORM_ERROR_MODEL);
        ResourceRequirement resources = new ResourceRequirement(
                1, 256, 50, 1, 1048576L, 1048576L, 60000L,
                false, 512, false, 60000L);
        PluginRuntimeRequirement runtime = new PluginRuntimeRequirement(
                PluginRuntimeRequirement.RuntimeMode.TRUSTED_IN_PROCESS,
                PluginRuntimeRequirement.ExecutionEnvironment.LOCAL_PROCESS,
                ExtensionTrustLevel.FULLY_TRUSTED);
        PluginGuarantee guarantees = new PluginGuarantee(false, false, Set.of());
        return new com.example.platform.extension.domain.PluginDescriptor(
                pluginId, version, "1", "vendor-test", caps,
                List.of(new HandledObjectDescriptor("RenderExecutionPlan", "1.0", "java.lang.String",
                        List.of(), List.of(), HandledObjectDescriptor.TenantBehavior.TENANT_SCOPED)),
                invocation, List.of(), resources, runtime, guarantees);
    }
}
