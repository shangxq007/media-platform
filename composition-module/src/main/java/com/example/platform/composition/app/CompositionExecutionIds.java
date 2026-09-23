package com.example.platform.composition.app;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
public final class CompositionExecutionIds {
    private CompositionExecutionIds() {}
    public static String of(String tenant, String workspace, String key) { return "cmp-exec-" + UUID.nameUUIDFromBytes((tenant + "\0" + workspace + "\0" + key).getBytes(StandardCharsets.UTF_8)); }
}
