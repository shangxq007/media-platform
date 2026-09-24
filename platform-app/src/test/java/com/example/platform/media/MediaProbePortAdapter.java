package com.example.platform.media;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/** Real test-classpath component discovered by PlatformApplication scanning. */
@Component
@Profile("v26-negative")
final class MediaProbePortAdapter implements MediaProbePort {}
