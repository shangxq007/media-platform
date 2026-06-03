package com.example.platform.render.domain.spatial;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record PpmPosition(double x, double y, double width, double height) {}
