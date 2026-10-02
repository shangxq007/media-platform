package com.example.platform.frameextract;

import com.example.platform.contract.media.FrameExtractProvider;
import com.example.platform.contract.media.FrameExtractManifest;
import com.example.platform.contract.media.FrameExtractResult;
import com.example.platform.contract.media.FrameExtractCapabilityDeclaration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.platform.contract.media.CoverImageContracts;
import com.example.platform.contract.media.ThumbnailContracts;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.BooleanSupplier;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * N:M production topology at the worker execution adapter: one provider that declares two
 * capabilities is indexed under each, capability-scoped dispatch tells the provider which capability
 * it is executing, and dispatch never crosses providers. Routing only — the double returns an explicit
 * routing marker failure, no frame is fabricated.
 */
class FrameExtractExecutionAdapterTest {

    private static final String SECOND_CAPABILITY = "media.preview";

    @TempDir Path temp;

    /** Records the capability it was asked to execute; never produces a rendered output. */
    private static final class TwoCapabilityProvider implements FrameExtractProvider {

        private final FrameExtractManifest manifest;
        private final List<String> executed = new ArrayList<>();

        TwoCapabilityProvider(String providerId, String... capabilityIds) {
            List<FrameExtractCapabilityDeclaration> declarations = new ArrayList<>();
            for (String capabilityId : capabilityIds) {
                declarations.add(new FrameExtractCapabilityDeclaration(capabilityId, "1.0"));
            }
            this.manifest = new FrameExtractManifest(providerId, "ffmpeg.cpu.frame-extract.v1", "1.0.0",
                    declarations, "ffmpeg", Set.of("video/*"), Set.of("image/png"),
                    0d, 86_400d, 16, 8192, 1024L, 60, "t", "r");
        }

        @Override
        public FrameExtractManifest manifest() {
            return manifest;
        }

        @Override
        public FrameExtractResult render(String capabilityId, Path input, Path workDirectory,
                String imageFormat, Integer width, Integer quality, double timestampSeconds,
                BooleanSupplier cancelled) {
            if (!manifest.supports(capabilityId)) {
                return FrameExtractResult.failure("UNSUPPORTED_CAPABILITY");
            }
            executed.add(capabilityId);
            return FrameExtractResult.failure("CONTRACT-TEST-ROUTING-ONLY");
        }

        List<String> executed() {
            return List.copyOf(executed);
        }
    }

    private FrameExtractExecutionAdapter adapter(FrameExtractProvider... providers) {
        List<FrameExtractProvider> registered = List.of(providers);
        return new FrameExtractExecutionAdapter(registered, temp.toString());
    }

    private static CoverImageContracts.Request coverRequest() {
        return new CoverImageContracts.Request(
                "tenant-1", "project-1", "art_subject", 1.5d, "png", 640, 80, "cover-key-shape");
    }

    private static ThumbnailContracts.Request thumbnailRequest() {
        return new ThumbnailContracts.Request(
                "tenant-1", "project-1", "art_subject", 1.5d, "png", 640, 80, "thumb-key-shape");
    }

    @Test
    void oneProviderDeclaringBothCapabilitiesIsRoutedForEach() {
        var provider = new TwoCapabilityProvider(
                "platform.ffmpeg", CoverImageContracts.CAPABILITY, ThumbnailContracts.CAPABILITY);
        var adapter = adapter(provider);

        assertThat(provider.manifest().capabilities()).hasSize(2);
        assertThat(adapter.providerForCapability(CoverImageContracts.CAPABILITY)).isSameAs(provider);
        assertThat(adapter.providerForCapability(ThumbnailContracts.CAPABILITY)).isSameAs(provider);
        assertThat(adapter.providerFamilyOf(CoverImageContracts.CAPABILITY)).isEqualTo("platform.ffmpeg");
        assertThat(adapter.providerFamilyOf(ThumbnailContracts.CAPABILITY)).isEqualTo("platform.ffmpeg");
        assertThat(adapter.provider()).isSameAs(provider);
    }

    @Test
    void invocationPassesTheExecutingCapabilityToTheProvider() {
        var provider = new TwoCapabilityProvider(
                "platform.ffmpeg", CoverImageContracts.CAPABILITY, ThumbnailContracts.CAPABILITY);
        var adapter = adapter(provider);

        assertThat(adapter.invoke(CoverImageContracts.CAPABILITY, coverRequest(), Path.of("/tmp/in"),
                () -> false).failureCode()).isEqualTo("CONTRACT-TEST-ROUTING-ONLY");
        assertThat(adapter.invoke(thumbnailRequest(), new byte[] {1, 2, 3}, () -> false).failureCode())
                .isEqualTo("CONTRACT-TEST-ROUTING-ONLY");

        assertThat(provider.executed())
                .containsExactly(CoverImageContracts.CAPABILITY, ThumbnailContracts.CAPABILITY);
    }

    @Test
    void capabilityScopedDispatchDoesNotCrossProviders() {
        var pinned = new TwoCapabilityProvider(
                "platform.ffmpeg", CoverImageContracts.CAPABILITY, ThumbnailContracts.CAPABILITY);
        var otherProvider = new TwoCapabilityProvider(
                "platform.other", CoverImageContracts.CAPABILITY, ThumbnailContracts.CAPABILITY);
        var adapter = adapter(pinned, otherProvider);

        // A second provider declaring the same capabilities is registered but never chosen: the
        // pinned family owns every routed capability, so dispatch never drifts to another family.
        assertThat(adapter.providerForCapability(CoverImageContracts.CAPABILITY)).isSameAs(pinned);
        assertThat(adapter.providerForCapability(ThumbnailContracts.CAPABILITY)).isSameAs(pinned);

        adapter.invoke(thumbnailRequest(), new byte[] {1}, () -> false);

        assertThat(pinned.executed()).containsExactly(ThumbnailContracts.CAPABILITY);
        assertThat(otherProvider.executed()).isEmpty();
    }

    @Test
    void adapterFailsClosedForEmptyDuplicateOrCollapsedIdentity() {
        assertThatThrownBy(() -> new FrameExtractExecutionAdapter(List.of(), temp.toString()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("no frame-extract provider registered");
        assertThatThrownBy(() -> new FrameExtractExecutionAdapter(
                List.of(new TwoCapabilityProvider("platform.ffmpeg", CoverImageContracts.CAPABILITY),
                        new TwoCapabilityProvider("platform.ffmpeg", ThumbnailContracts.CAPABILITY)),
                temp.toString()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("duplicate frame-extract provider");
        assertThatThrownBy(() -> new FrameExtractExecutionAdapter(
                List.of(new TwoCapabilityProvider(
                        "ffmpeg.cpu.frame-extract.v1", "ffmpeg.cpu.frame-extract.v1")),
                temp.toString()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("must differ");
        assertThatThrownBy(() -> new FrameExtractExecutionAdapter(
                List.of(new TwoCapabilityProvider("platform.other", SECOND_CAPABILITY)), temp.toString()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("pinned frame-extract provider is not registered");
    }

    @Test
    void adapterFailsClosedForCapabilitiesThePinnedProviderDoesNotDeclare() {
        assertThatThrownBy(() -> new FrameExtractExecutionAdapter(
                List.of(new TwoCapabilityProvider("platform.ffmpeg", CoverImageContracts.CAPABILITY)),
                temp.toString()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("no registered provider declares capability media.thumbnail");
        assertThatThrownBy(() -> new FrameExtractExecutionAdapter(
                List.of(new TwoCapabilityProvider("platform.ffmpeg", ThumbnailContracts.CAPABILITY)),
                temp.toString()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("no registered provider declares capability media.cover-image");
        assertThatThrownBy(() -> new FrameExtractExecutionAdapter(
                List.of(
                        new TwoCapabilityProvider("platform.ffmpeg", CoverImageContracts.CAPABILITY),
                        new TwoCapabilityProvider("platform.other", ThumbnailContracts.CAPABILITY)),
                temp.toString()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("pinned frame-extract provider platform.ffmpeg"
                        + " does not declare capability media.thumbnail");
    }

    @Test
    void aSecondProviderDeclaringTheSameCapabilitiesStaysRegisteredButNeverDisplacesThePin() {
        var pinned = new TwoCapabilityProvider(
                "platform.ffmpeg", CoverImageContracts.CAPABILITY, ThumbnailContracts.CAPABILITY);
        var secondary = new TwoCapabilityProvider(
                "platform.other", CoverImageContracts.CAPABILITY, ThumbnailContracts.CAPABILITY);
        var adapter = adapter(pinned, secondary);

        assertThat(adapter.provider()).isSameAs(pinned);
        assertThat(adapter.providerForCapability(CoverImageContracts.CAPABILITY)).isSameAs(pinned);
        assertThat(adapter.providerForCapability(ThumbnailContracts.CAPABILITY)).isSameAs(pinned);
        assertThat(adapter.provider("platform.other")).isSameAs(secondary);
    }

    @Test
    void providerFailsClosedWhenAskedForAnUndeclaredCapability() {
        var provider = new TwoCapabilityProvider("platform.ffmpeg", CoverImageContracts.CAPABILITY);

        assertThat(provider.render(SECOND_CAPABILITY, Path.of("/tmp/in"), temp,
                "png", null, null, 0d, () -> false).failureCode())
                .isEqualTo("UNSUPPORTED_CAPABILITY");
        assertThat(provider.executed()).isEmpty();
    }
}
