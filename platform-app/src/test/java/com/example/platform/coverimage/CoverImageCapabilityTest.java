package com.example.platform.coverimage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.platform.artifact.domain.ProvenanceRelationType;
import com.example.platform.sandbox.execution.TaskCapability;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.BooleanSupplier;
import org.junit.jupiter.api.Test;

/**
 * Focused contract tests for the media.cover-image capability: vocabulary, capability-independent
 * provider identity, declaration validation and registry fail-closed behaviour. No fake provider
 * result is asserted anywhere (the double reports only routing failures, never a rendered output).
 */
class CoverImageCapabilityTest {

    private static CoverImageContracts.Request request(String format) {
        return new CoverImageContracts.Request(
                "tenant-1", "project-1", "art_subject", 1.5d, format, 640, 80, "cover-key-1");
    }

    /** Minimal provider double used only to exercise registry composition rules. */
    private static final class Provider implements CoverImageCapabilityProvider {
        private final Manifest manifest;

        Provider(String providerId, String... capabilityIds) {
            List<CapabilityDeclaration> declarations = new ArrayList<>();
            for (String capabilityId : capabilityIds) {
                declarations.add(new CapabilityDeclaration(capabilityId, "1.0"));
            }
            this.manifest = new Manifest(providerId, "impl." + providerId, "1.0.0", declarations,
                    "ffmpeg", Set.of("video/mp4"), Set.of("png"), 0d, 86_400d, 16, 8192, 1024L, 60,
                    "sandbox-bwrap", "ffmpeg");
        }

        @Override public Manifest manifest() { return manifest; }

        @Override public Result render(String capabilityId, CoverImageContracts.Request request,
                                       Path inputPath, BooleanSupplier cancelled) {
            throw new AssertionError("provider execution is not part of this contract test");
        }
    }

    @Test
    void vocabularyAndProviderIdentityAreCapabilityIndependent() {
        assertThat(ProvenanceRelationType.valueOf("COVER_OF")).isNotNull();
        assertThat(TaskCapability.valueOf("COVER_IMAGE")).isNotNull();
        assertThat(CoverImageContracts.CAPABILITY).isEqualTo("media.cover-image");
        assertThat(CoverImageContracts.CAPABILITY_VERSION).isEqualTo("1.0");
        // Provider (family) identity is not derived from the capability it serves.
        assertThat(CoverImageContracts.PROVIDER).isEqualTo("platform.ffmpeg");
        assertThat(CoverImageContracts.PROVIDER_IMPLEMENTATION)
                .isEqualTo("ffmpeg.cpu.frame-extract.v1");
        assertThat(CoverImageContracts.PROVIDER).doesNotContain(CoverImageContracts.CAPABILITY);
    }

    @Test
    void manifestDeclaresCapabilityListAndRejectsInvalidDeclarations() {
        var capability = new CoverImageCapabilityProvider.CapabilityDeclaration(
                CoverImageContracts.CAPABILITY, CoverImageContracts.CAPABILITY_VERSION);
        var manifest = new Provider(CoverImageContracts.PROVIDER, CoverImageContracts.CAPABILITY)
                .manifest();

        assertThat(manifest.capabilities()).containsExactly(capability);
        assertThat(manifest.supports(CoverImageContracts.CAPABILITY)).isTrue();
        assertThat(manifest.supports("media.unrelated")).isFalse();

        assertThatThrownBy(() -> new CoverImageCapabilityProvider.Manifest(
                CoverImageContracts.PROVIDER, "impl", "1.0.0", List.of(), "ffmpeg",
                Set.of("video/mp4"), Set.of("png"), 0d, 86_400d, 16, 8192, 1024L, 60, "t", "r"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("at least one capability");
        assertThatThrownBy(() -> new CoverImageCapabilityProvider.Manifest(
                CoverImageContracts.PROVIDER, "impl", "1.0.0",
                List.of(capability, capability), "ffmpeg",
                Set.of("video/mp4"), Set.of("png"), 0d, 86_400d, 16, 8192, 1024L, 60, "t", "r"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("duplicate capability declaration");
        assertThatThrownBy(() -> new CoverImageCapabilityProvider.CapabilityDeclaration(
                " ", "1.0"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void registryFailsClosedWhenNoProviderDeclaresTheCapability() {
        assertThatThrownBy(() -> new CoverImageCapabilityRegistry(List.of()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("no cover-image provider registered");
        assertThatThrownBy(() -> new CoverImageCapabilityRegistry(
                List.of(new Provider("unrelated", "media.unrelated"))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("no registered provider declares capability media.cover-image");
    }

    @Test
    void registryRejectsDuplicateProviderIdentitiesAndInvalidPins() {
        assertThatThrownBy(() -> new CoverImageCapabilityRegistry(List.of(
                new Provider(CoverImageContracts.PROVIDER, CoverImageContracts.CAPABILITY),
                new Provider(CoverImageContracts.PROVIDER, CoverImageContracts.CAPABILITY))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("duplicate cover-image provider");
        assertThatThrownBy(() -> new CoverImageCapabilityRegistry(
                List.of(new Provider(CoverImageContracts.PROVIDER, CoverImageContracts.CAPABILITY)),
                "not.registered"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("pinned cover-image provider is not registered");
        assertThatThrownBy(() -> new CoverImageCapabilityRegistry(
                List.of(new Provider("platform.other", "media.other")),
                "platform.other"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("no registered provider declares capability media.cover-image");
        assertThatThrownBy(() -> new CoverImageCapabilityRegistry(
                List.of(
                        new Provider(CoverImageContracts.PROVIDER, CoverImageContracts.CAPABILITY),
                        new Provider("platform.other", "media.other")),
                "platform.other"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("does not declare capability media.cover-image");
    }

    @Test
    void registryDoesNotSkipAProviderThatDeclaresSeveralCapabilities() {
        var registry = new CoverImageCapabilityRegistry(List.of(
                new Provider("unrelated", "media.unrelated"),
                new Provider(CoverImageContracts.PROVIDER, CoverImageContracts.CAPABILITY)));

        // Both providers stay registered: a provider serving another capability is not skipped.
        assertThat(registry.size()).isEqualTo(2);
        assertThat(registry.providerIds())
                .containsExactlyInAnyOrder(CoverImageContracts.PROVIDER, "unrelated");
        // A provider is indexed under every capability it declares; foreign capabilities are kept,
        // never silently dropped.
        assertThat(registry.capabilities())
                .containsExactlyInAnyOrder(CoverImageContracts.CAPABILITY, "media.unrelated");
        assertThat(registry.provider(CoverImageContracts.CAPABILITY).manifest().providerId())
                .isEqualTo(CoverImageContracts.PROVIDER);
        assertThat(registry.provider(CoverImageContracts.CAPABILITY).manifest().outputFormats())
                .containsExactly("png");
        assertThat(registry.providerByProviderId(CoverImageContracts.PROVIDER)).isPresent();
        assertThat(registry.providerByProviderId("unrelated")).isPresent();
        assertThatThrownBy(() -> registry.provider("media.unserved"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("no registered provider serves capability media.unserved");
    }

    @Test
    void registryResolvesMultipleProvidersPerCapabilityOnlyWithADeploymentPin() {
        var first = new Provider("platform.ffmpeg", CoverImageContracts.CAPABILITY);
        var second = new Provider("platform.other", CoverImageContracts.CAPABILITY);

        assertThatThrownBy(() -> new CoverImageCapabilityRegistry(List.of(first, second)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("served by multiple providers");

        var pinned = new CoverImageCapabilityRegistry(List.of(first, second), "platform.other");
        assertThat(pinned.provider(CoverImageContracts.CAPABILITY).manifest().providerId())
                .isEqualTo("platform.other");
        assertThat(pinned.size()).isEqualTo(2);
        assertThat(pinned.capabilities()).containsExactly(CoverImageContracts.CAPABILITY);
    }

    @Test
    void requestContractRejectsInvalidInputs() {
        assertThat(request("png").subjectArtifactId()).isEqualTo("art_subject");
        assertThatThrownBy(() -> request("gif")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new CoverImageContracts.Request(
                "tenant-1", "project-1", "art_subject", -1d, "png", null, null, "k"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new CoverImageContracts.Request(
                "tenant-1", "project-1", "art_subject", 0d, "png", 8, null, "k"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new CoverImageContracts.Request(
                "tenant-1", "project-1", "art_subject", 0d, "png", null, 0, "k"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new CoverImageContracts.Request(
                "tenant-1", "project-1", " ", 0d, "png", null, null, "k"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
