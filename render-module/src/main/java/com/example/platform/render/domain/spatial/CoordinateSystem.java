package com.example.platform.render.domain.spatial;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record CoordinateSystem(
        String unit, List<Integer> range, String origin,
        String xDirection, String yDirection, List<String> spaces
) {}
