package com.example.platform.coverimage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.BooleanSupplier;
import org.junit.jupiter.api.Test;

/**
 * Model-A shape test for the cover slice: one provider serves <em>two</em> capabilities, is indexed
 * under each of them (never skipped for a capability it declares), and capability-scoped dispatch
 * tells the provider which capability it is executing.
 *
 * <p>Routing only — no provider output is asserted. The double returns an explicit routing marker
 * failure and records the capability ids it was asked to execute; no rendered cover is fabricated.
 */
class CoverImageProviderCapabilityShapeTest {

    private static final String SECOND_CAPABILITY = "media.cover-preview";

    /** Records the capability it was asked to execute; never produces a rendered output. */
    private static final class TwoCapabilityProvider implements CoverImageCapabilityProvider {

        private final Manifest manifest;
        private final List<String> executed = new ArrayList<>();

        TwoCapabilityProvider(String providerId, String... capabilityIds) {
            List<CapabilityDeclaration> declarations = new ArrayList<>();
            for (String capabilityId : capabilityIds) {
                declarations.add(new CapabilityDeclaration(capabilityId, "1.0"));
            }
            this.manifest = new Manifest(providerId, "ffmpeg.cpu.frame-extract.v1", "1.0.0",
                    declarations, "ffmpeg", Set.of("video/mp4"), Set.of("png", "jpeg"), 0d, 86_400d,
                    16, 8192, 1024L, 60, "sandbox-bwrap", "ffmpeg");
        }

        @Override
        public Manifest manifest() {
            return manifest;
        }

        @Override
        public Result render(String capabilityId, CoverImageContracts.Request request,
                             Path inputPath, BooleanSupplier cancelled) {
            if (!manifest.supports(capabilityId)) {
                return Result.failure("UNSUPPORTED_CAPABILITY");
            }
            executed.add(capabilityId);
            return Result.failure("CONTRACT-TEST-ROUTING-ONLY");
        }

        List<String> executed() {
            return List.copyOf(executed);
        }
    }

    private static CoverImageContracts.Request request() {
        return new CoverImageContracts.Request(
                "tenant-1", "project-1", "art_subject", 1.5d, "png", 640, 80, "cover-key-shape");
    }

    @Test
    void oneProviderDeclaringTwoCapabilitiesIsIndexedUnderBoth() {
        var provider = new TwoCapabilityProvider(
                CoverImageContracts.PROVIDER, CoverImageContracts.CAPABILITY, SECOND_CAPABILITY);
        var registry = new CoverImageCapabilityRegistry(List.of(provider));

        assertThat(provider.manifest().capabilities()).hasSize(2);
        assertThat(registry.size()).isEqualTo(1);
        assertThat(registry.capabilities())
                .containsExactlyInAnyOrder(CoverImageContracts.CAPABILITY, SECOND_CAPABILITY);
        assertThat(registry.provider(CoverImageContracts.CAPABILITY)).isSameAs(provider);
        assertThat(registry.provider(SECOND_CAPABILITY)).isSameAs(provider);
    }

    @Test
    void invocationPassesTheExecutingCapabilityToTheProvider() {
        var provider = new TwoCapabilityProvider(
                CoverImageContracts.PROVIDER, CoverImageContracts.CAPABILITY, SECOND_CAPABILITY);
        var registry = new CoverImageCapabilityRegistry(List.of(provider));

        assertThat(registry.invoke(CoverImageContracts.CAPABILITY, request(), Path.of("/tmp/in"),
                () -> false).failureCode()).isEqualTo("CONTRACT-TEST-ROUTING-ONLY");
        assertThat(registry.invoke(SECOND_CAPABILITY, request(), Path.of("/tmp/in"),
                () -> false).failureCode()).isEqualTo("CONTRACT-TEST-ROUTING-ONLY");

        assertThat(provider.executed())
                .containsExactly(CoverImageContracts.CAPABILITY, SECOND_CAPABILITY);
    }

    @Test
    void capabilityScopedDispatchDoesNotCrossProviders() {
        var coverProvider = new TwoCapabilityProvider(
                CoverImageContracts.PROVIDER, CoverImageContracts.CAPABILITY);
        var otherProvider = new TwoCapabilityProvider("platform.other", SECOND_CAPABILITY);
        var registry = new CoverImageCapabilityRegistry(List.of(coverProvider, otherProvider));

        assertThat(registry.provider(CoverImageContracts.CAPABILITY)).isSameAs(coverProvider);
        assertThat(registry.provider(SECOND_CAPABILITY)).isSameAs(otherProvider);

        registry.invoke(SECOND_CAPABILITY, request(), Path.of("/tmp/in"), () -> false);

        assertThat(otherProvider.executed()).containsExactly(SECOND_CAPABILITY);
        assertThat(coverProvider.executed()).isEmpty();
    }

    @Test
    void providerThatDoesNotDeclareTheSliceCapabilityCannotServeIt() {
        assertThatThrownBy(() -> new CoverImageCapabilityRegistry(
                List.of(new TwoCapabilityProvider("platform.other", SECOND_CAPABILITY))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("no registered provider declares capability media.cover-image");
    }

    @Test
    void providerFailsClosedWhenAskedForAnUndeclaredCapability() {
        var provider = new TwoCapabilityProvider(
                CoverImageContracts.PROVIDER, CoverImageContracts.CAPABILITY);

        assertThat(provider.render(SECOND_CAPABILITY, request(), Path.of("/tmp/in"), () -> false)
                .failureCode()).isEqualTo("UNSUPPORTED_CAPABILITY");
        assertThat(provider.executed()).isEmpty();
    }
}
