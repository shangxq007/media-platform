package com.example.platform.render.domain.spatial;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record SafeAreaInsets(double top, double right, double bottom, double left) {}
