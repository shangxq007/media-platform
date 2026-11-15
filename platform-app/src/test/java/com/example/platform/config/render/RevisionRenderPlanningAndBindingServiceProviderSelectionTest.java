package com.example.platform.config.render;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.example.platform.execution.compatibility.ProviderCandidate;
import com.example.platform.execution.domain.provider.ProviderBindingPin;
import com.example.platform.execution.domain.provider.ProviderId;
import java.util.List;
import org.junit.jupiter.api.Test;

class RevisionRenderPlanningAndBindingServiceProviderSelectionTest {
    @Test
    void internal_media_choice_preserves_only_the_requested_provider_for_binding() {
        var ffmpeg = candidate("ffmpeg");
        var bmf = candidate("bmf");
        var other = candidate("other");
        var candidates = List.of(ffmpeg, bmf, other);

        assertThat(RevisionRenderPlanningAndBindingService.selectedMediaCandidates(
                candidates, "ffmpeg")).containsExactly(ffmpeg, other);
        assertThat(RevisionRenderPlanningAndBindingService.selectedMediaCandidates(
                candidates, "bmf")).containsExactly(bmf, other);
        assertThatThrownBy(() -> RevisionRenderPlanningAndBindingService.selectedMediaCandidates(
                candidates, "unknown")).isInstanceOf(IllegalArgumentException.class);
    }

    private static ProviderCandidate candidate(String id) {
        var candidate = mock(ProviderCandidate.class);
        var pin = mock(ProviderBindingPin.class);
        when(candidate.bindingPin()).thenReturn(pin);
        when(pin.providerId()).thenReturn(ProviderId.of(id));
        return candidate;
    }
}
