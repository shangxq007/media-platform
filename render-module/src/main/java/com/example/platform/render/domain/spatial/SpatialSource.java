package com.example.platform.render.domain.spatial;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record SpatialSource(String space, String assetId) {}
