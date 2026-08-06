package com.example.platform.extension.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.platform.extension.app.PluginDescriptorValidator;
import com.example.platform.extension.app.PluginRegistryImpl;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * GREEN: descriptor validation (frozen contract A090). Reconciled from the
 * authentic RED proof (PluginDescriptorValidationRedTest): the baseline lacked
 * a validated PluginDescriptor authority; these assertions prove the accepted
 * boundary now exists.
 */
class PluginDescriptorValidationTest {

    private static PluginDescriptor validDescriptor() {
        return new PluginDescriptor(
                "media.render.ffmpeg",
                "1.0.0",
                "1",
                "media-platform",
                List.of(capability("media.render"), capability("subtitle.burn-in")),
                List.of(handledObject()),
                InvocationContract.syncOnlyDefault(),
                List.of(new PermissionDescriptor("ffmpeg.execute")),
                ResourceRequirement.ffmpegDefaults(),
                PluginRuntimeRequirement.trustedInProcess(),
                PluginGuarantee.ffmpegDefaults());
    }

    private static CapabilityDescriptor capability(String id) {
        return new CapabilityDescriptor(
                id, "1", "render", "RenderExecutionPlan", "ArtifactReference",
                CapabilityDescriptor.InvocationMode.SYNC_ONLY);
    }

    private static HandledObjectDescriptor handledObject() {
        return new HandledObjectDescriptor(
                "RenderExecutionPlan", "1",
                "com.example.platform.render.domain.timeline.compile.executionplan.RenderExecutionPlan",
                List.of("profile", "timelineSnapshotId"), List.of(),
                HandledObjectDescriptor.TenantBehavior.TENANT_SCOPED);
    }

    @Test
    void validDescriptorAccepted() {
        var issues = new PluginDescriptorValidator().validate(validDescriptor());
        assertTrue(issues.isEmpty(), "valid descriptor must be accepted: " + issues);
    }

    @Test
    void blankPluginIdRejectedPlg001() {
        PluginDescriptor blank = new PluginDescriptor(" ", "1.0.0", "1", "vendor",
                List.of(capability("media.render")), List.of(handledObject()),
                InvocationContract.syncOnlyDefault(), List.of(),
                ResourceRequirement.ffmpegDefaults(),
                PluginRuntimeRequirement.trustedInProcess(), PluginGuarantee.ffmpegDefaults());
        var blankIssues = new PluginDescriptorValidator().validate(blank);
        assertEquals(PluginDiagnosticCode.PLG_001, blankIssues.get(0).code());
        assertEquals("pluginId", blankIssues.get(0).fieldPath());
    }

    @Test
    void invalidPluginIdFormatRejectedPlg001() {
        // Java class name / Spring bean name / implementation key as plugin ID is prohibited.
        PluginDescriptor javaClassNameId = new PluginDescriptor("FfmpegRenderProviderExtension", "1.0.0",
                "1", "vendor", List.of(capability("media.render")), List.of(handledObject()),
                InvocationContract.syncOnlyDefault(), List.of(),
                ResourceRequirement.ffmpegDefaults(),
                PluginRuntimeRequirement.trustedInProcess(), PluginGuarantee.ffmpegDefaults());
        var issues = new PluginDescriptorValidator().validate(javaClassNameId);
        assertEquals(PluginDiagnosticCode.PLG_001, issues.get(0).code());
        assertEquals("pluginId", issues.get(0).fieldPath());
    }

    @Test
    void invalidVersionRejectedPlg002() {
        PluginDescriptor bad = new PluginDescriptor("media.render.ffmpeg", "not-a-semver",
                "1", "vendor", List.of(capability("media.render")), List.of(handledObject()),
                InvocationContract.syncOnlyDefault(), List.of(),
                ResourceRequirement.ffmpegDefaults(),
                PluginRuntimeRequirement.trustedInProcess(), PluginGuarantee.ffmpegDefaults());
        var issues = new PluginDescriptorValidator().validate(bad);
        assertEquals(PluginDiagnosticCode.PLG_002, issues.get(0).code());
    }

    @Test
    void unsupportedPlatformApiRejectedPlg003() {
        PluginDescriptor bad = new PluginDescriptor("media.render.ffmpeg", "1.0.0",
                "99", "vendor", List.of(capability("media.render")), List.of(handledObject()),
                InvocationContract.syncOnlyDefault(), List.of(),
                ResourceRequirement.ffmpegDefaults(),
                PluginRuntimeRequirement.trustedInProcess(), PluginGuarantee.ffmpegDefaults());
        var issues = new PluginDescriptorValidator().validate(bad);
        assertEquals(PluginDiagnosticCode.PLG_003, issues.get(0).code());
    }

    @Test
    void emptyCapabilitiesRejectedPlg005() {
        PluginDescriptor bad = new PluginDescriptor("media.render.ffmpeg", "1.0.0",
                "1", "vendor", List.of(), List.of(handledObject()),
                InvocationContract.syncOnlyDefault(), List.of(),
                ResourceRequirement.ffmpegDefaults(),
                PluginRuntimeRequirement.trustedInProcess(), PluginGuarantee.ffmpegDefaults());
        var issues = new PluginDescriptorValidator().validate(bad);
        assertEquals(PluginDiagnosticCode.PLG_005, issues.get(0).code());
    }

    @Test
    void duplicateCapabilityRejectedPlg006() {
        PluginDescriptor bad = new PluginDescriptor("media.render.ffmpeg", "1.0.0",
                "1", "vendor", List.of(capability("media.render"), capability("media.render")),
                List.of(handledObject()),
                InvocationContract.syncOnlyDefault(), List.of(),
                ResourceRequirement.ffmpegDefaults(),
                PluginRuntimeRequirement.trustedInProcess(), PluginGuarantee.ffmpegDefaults());
        var issues = new PluginDescriptorValidator().validate(bad);
        assertEquals(PluginDiagnosticCode.PLG_006, issues.get(0).code());
    }

    @Test
    void unknownPermissionRejectedPlg010() {
        PluginDescriptor bad = new PluginDescriptor("media.render.ffmpeg", "1.0.0",
                "1", "vendor", List.of(capability("media.render")), List.of(handledObject()),
                InvocationContract.syncOnlyDefault(),
                List.of(new PermissionDescriptor("network.egress")),
                ResourceRequirement.ffmpegDefaults(),
                PluginRuntimeRequirement.trustedInProcess(), PluginGuarantee.ffmpegDefaults());
        var issues = new PluginDescriptorValidator().validate(bad);
        assertEquals(PluginDiagnosticCode.PLG_010, issues.get(0).code());
    }

    @Test
    void invalidResourceRejectedPlg011() {
        ResourceRequirement badResource = new ResourceRequirement(
                1, 256, 50, 0, 1024L, 1024L, 200_000L, false, 2048, false, 60_000L);
        PluginDescriptor bad = new PluginDescriptor("media.render.ffmpeg", "1.0.0",
                "1", "vendor", List.of(capability("media.render")), List.of(handledObject()),
                InvocationContract.syncOnlyDefault(), List.of(), badResource,
                PluginRuntimeRequirement.trustedInProcess(), PluginGuarantee.ffmpegDefaults());
        var issues = new PluginDescriptorValidator().validate(bad);
        assertEquals(PluginDiagnosticCode.PLG_011, issues.get(0).code());
    }

    @Test
    void illegalGuaranteeRejectedPlg013() {
        PluginGuarantee illegal = new PluginGuarantee(true, false, java.util.Set.of());
        PluginDescriptor bad = new PluginDescriptor("media.render.ffmpeg", "1.0.0",
                "1", "vendor", List.of(capability("media.render")), List.of(handledObject()),
                InvocationContract.syncOnlyDefault(), List.of(),
                ResourceRequirement.ffmpegDefaults(),
                PluginRuntimeRequirement.trustedInProcess(), illegal);
        var issues = new PluginDescriptorValidator().validate(bad);
        assertEquals(PluginDiagnosticCode.PLG_013, issues.get(0).code());
    }

    @Test
    void trustRejectedPlg016() {
        PluginDescriptor bad = new PluginDescriptor("media.render.ffmpeg", "1.0.0",
                "1", "vendor", List.of(capability("media.render")), List.of(handledObject()),
                InvocationContract.syncOnlyDefault(), List.of(),
                ResourceRequirement.ffmpegDefaults(),
                new PluginRuntimeRequirement(PluginRuntimeRequirement.RuntimeMode.TRUSTED_IN_PROCESS,
                        PluginRuntimeRequirement.ExecutionEnvironment.LOCAL_PROCESS,
                        ExtensionTrustLevel.UNTRUSTED),
                PluginGuarantee.ffmpegDefaults());
        var issues = new PluginDescriptorValidator().validate(bad);
        assertEquals(PluginDiagnosticCode.PLG_016, issues.get(0).code());
    }

    @Test
    void implementationUnavailablePlg014() {
        PluginDescriptorValidator withCheck = new PluginDescriptorValidator(id -> false);
        var issues = withCheck.validate(validDescriptor());
        assertEquals(PluginDiagnosticCode.PLG_014, issues.get(0).code());
    }

    @Test
    void stableValidationOrder() {
        // pluginId blank AND version invalid AND api unsupported: codes appear in
        // frozen order PLG-001, PLG-002, PLG-003.
        PluginDescriptor bad = new PluginDescriptor(" ", "bad", "99", "vendor",
                List.of(capability("media.render")), List.of(handledObject()),
                InvocationContract.syncOnlyDefault(), List.of(),
                ResourceRequirement.ffmpegDefaults(),
                PluginRuntimeRequirement.trustedInProcess(), PluginGuarantee.ffmpegDefaults());
        var issues = new PluginDescriptorValidator().validate(bad);
        assertEquals(List.of(PluginDiagnosticCode.PLG_001, PluginDiagnosticCode.PLG_002,
                PluginDiagnosticCode.PLG_003),
                issues.stream().map(PluginDescriptorValidationIssue::code).toList());
    }

    @Test
    void invalidRegistrationZeroMutationAndSubsequentValidSucceeds() {
        PluginRegistryImpl registry = new PluginRegistryImpl(
                new PluginDescriptorValidator(), new com.example.platform.extension.app.PluginHealthRegistry());
        PluginDescriptor invalid = new PluginDescriptor(" ", "1.0.0", "1", "vendor",
                List.of(capability("media.render")), List.of(handledObject()),
                InvocationContract.syncOnlyDefault(), List.of(),
                ResourceRequirement.ffmpegDefaults(),
                PluginRuntimeRequirement.trustedInProcess(), PluginGuarantee.ffmpegDefaults());
        var issues = registry.register(invalid);
        assertEquals(PluginDiagnosticCode.PLG_001, issues.get(0).code());
        assertTrue(registry.enumerate().isEmpty(), "zero mutation after rejection");
        // Subsequent valid registration succeeds (failure does not poison registry).
        var okIssues = registry.register(validDescriptor());
        assertTrue(okIssues.isEmpty());
        assertEquals(1, registry.enumerate().size());
    }

    @Test
    void descriptorJsonSerializable() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        String json = mapper.writeValueAsString(validDescriptor());
        assertTrue(json.contains("\"pluginId\":\"media.render.ffmpeg\""));
        assertTrue(json.contains("\"capabilityId\":\"media.render\""));
        PluginDescriptor roundTrip = mapper.readValue(json, PluginDescriptor.class);
        assertEquals(validDescriptor(), roundTrip);
    }
}
