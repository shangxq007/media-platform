package com.example.platform.extension.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ToolExecutionRequestTest {

    @Test
    void preservesExactImmutableArgvIncludingNullableElements() {
        List<String> args = new ArrayList<>();
        args.add(null);
        args.add("-version");

        ToolExecutionRequest request = ToolExecutionRequest.withTimeout("ffmpeg", args, 2_000);
        args.set(1, "changed");

        assertNull(request.args().getFirst());
        assertEquals("-version", request.args().get(1));
        assertThrows(UnsupportedOperationException.class, () -> request.args().add("extra"));
    }

    @Test
    void timeoutCopyPreservesEveryOtherFieldAndDefensiveCopiesInputs() {
        List<String> args = new ArrayList<>(List.of("-i", "input.mp4"));
        Map<String, String> environment = new LinkedHashMap<>();
        environment.put("LANG", "C");
        ToolExecutionRequest original = new ToolExecutionRequest(
                "ffmpeg", args, environment, "/workspace", 1_000);

        ToolExecutionRequest copied = original.withTimeout(2_000);
        args.set(1, "changed.mp4");
        environment.put("PATH", "/bin");

        assertEquals("ffmpeg", copied.toolKey());
        assertEquals(List.of("-i", "input.mp4"), copied.args());
        assertEquals(Map.of("LANG", "C"), copied.environment());
        assertEquals("/workspace", copied.workingDirectory());
        assertEquals(2_000, copied.timeoutMillis());
        assertThrows(UnsupportedOperationException.class,
                () -> copied.environment().put("PATH", "/bin"));
    }
}
