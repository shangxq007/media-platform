package com.example.platform.render.infrastructure.api;

public record PublicOutputSpec(
        String format,
        Integer width,
        Integer height,
        Integer fps
) {}
