package com.example.platform.extension.app;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CliTemplateResolverTest {

    private final CliTemplateResolver resolver = new CliTemplateResolver();

    @Test
    void replacesPlaceholdersInArgs() {
        assertEquals(
                List.of("-i", "/data/in.mp4", "-f", "null", "-"),
                resolver.resolveArgs(
                        List.of("-i", "{input}", "-f", "null", "-"),
                        Map.of("input", "/data/in.mp4")));
    }

    @Test
    void sameTemplateTokenRepeated() {
        assertEquals(
                List.of("a", "x", "x"),
                resolver.resolveArgs(List.of("a", "{p}", "{p}"), Map.of("p", "x")));
    }

    @Test
    void missingParamThrows() {
        assertThrows(
                IllegalArgumentException.class,
                () -> resolver.resolveArgs(List.of("{missing}"), Map.of()));
    }
}
