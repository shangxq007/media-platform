package com.example.platform.ffmpeg;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.platform.providerplugin.ProviderPluginContribution;
import com.example.platform.workerfabric.domain.ProviderResourceProfile;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/**
 * P2-5b-2a-2a-2-R4: the contribution declares its resource footprint, and the interface default is
 * fail-closed (no declaration).
 */
class FfmpegProviderResourceProfileTest {

    @Test
    void interfaceDeclaresAFailClosedDefault() throws Exception {
        assertThat(ProviderPluginContribution.class.getMethod("resourceProfile").isDefault())
                .isTrue();
        assertThat(ProviderPluginContribution.class.getMethod("resourceProfile").getReturnType())
                .isEqualTo(Optional.class);
    }

    @Test
    void ffmpegDeclaresTheOwnerApprovedBoundedFootprint() {
        Optional<ProviderResourceProfile> declared = new FfmpegProviderPluginContribution().resourceProfile();

        assertThat(declared).isPresent();
        ProviderResourceProfile profile = declared.orElseThrow();
        assertThat(profile.cpuMillicores()).isEqualTo(2000L);
        assertThat(profile.memoryBytes()).isEqualTo(1073741824L);
        assertThat(profile.temporaryStorageBytes()).isEqualTo(2147483648L);
        assertThat(profile.deviceDemands()).isEmpty();
        assertThat(profile.deviceDemands()).isEqualTo(Map.of());
    }
}
