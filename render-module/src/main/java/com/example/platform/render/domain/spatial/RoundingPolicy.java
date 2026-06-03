package com.example.platform.render.domain.spatial;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record RoundingPolicy(
        boolean edgeBased, String mode, int minPixelSize, boolean clampToFrame
) {}
