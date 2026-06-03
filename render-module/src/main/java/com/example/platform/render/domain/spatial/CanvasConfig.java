package com.example.platform.render.domain.spatial;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record CanvasConfig(
        int width, int height,
        @JsonProperty("widthPpm") int widthPpm,
        @JsonProperty("heightPpm") int heightPpm
) {}
