package com.example.platform.render.infrastructure.media;

import com.example.platform.media.domain.probe.MediaProbeObservation;
import com.example.platform.media.domain.probe.MediaProbePort;
import org.springframework.stereotype.Component;

/**
 * Fail-closed platform adapter for the media-domain {@link MediaProbePort}.
 *
 * <p>The former implementation invoked render-owned media probe authority. C3 keeps
 * the generic media boundary available to the application context but performs
 * no probing until a typed provider contribution supplies that capability.</p>
 */
@Component
public class MediaProbePortAdapter implements MediaProbePort {

    static final String ERROR = "TYPED_PROVIDER_PLUGIN_MEDIA_PROBE_REQUIRED";

    @Override
    public MediaProbeObservation probe(String assetUri) {
        return MediaProbeObservation.failed("provider-plugin-unavailable", ERROR);
    }
}
