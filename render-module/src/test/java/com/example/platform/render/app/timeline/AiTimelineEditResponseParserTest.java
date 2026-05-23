package com.example.platform.render.app.timeline;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.platform.render.domain.timeline.TimelineExtensionsReader;
import com.example.platform.render.domain.timeline.TimelineScriptParser;
import org.junit.jupiter.api.Test;

class AiTimelineEditResponseParserTest {

    private final TimelineSpecResolver resolver = new TimelineSpecResolver(
            TimelineTestSupport.internalTimelineAdapter(),
            new TimelineScriptParser());

    @Test
    void parsesPatchOperationsDocument() throws Exception {
        String base = """
                {
                  "schemaVersion": "1.0",
                  "id": "tl_test",
                  "revision": 1,
                  "composition": { "tracks": [] },
                  "metadata": {}
                }
                """;
        String ai = """
                {
                  "operations": [
                    { "op": "replace", "path": "/metadata/platform.ai.stub", "value": "yes" }
                  ]
                }
                """;
        var parsed = AiTimelineEditResponseParser.parse(ai, resolver);
        assertTrue(parsed instanceof AiTimelineEditResponseParser.Parsed.PatchOps);
        var ops = ((AiTimelineEditResponseParser.Parsed.PatchOps) parsed).operations();
        assertEquals(1, ops.size());
        assertEquals("replace", ops.get(0).op());
    }
}
