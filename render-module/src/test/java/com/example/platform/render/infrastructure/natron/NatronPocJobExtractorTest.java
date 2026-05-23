package com.example.platform.render.infrastructure.natron;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.platform.render.domain.timeline.TimelineScriptParser;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class NatronPocJobExtractorTest {

    @Test
    void extractsNatronPocEffectAndInput(@TempDir Path tempDir) throws Exception {
        Path input = tempDir.resolve("clip.mp4");
        Files.writeString(input, "fake");

        String script = """
                {
                  "tracks": [{
                    "id": "v1",
                    "type": "VIDEO",
                    "clips": [{
                      "id": "c1",
                      "media_reference": "file://%s",
                      "effects": [{
                        "effectKey": "video.natron_vignette",
                        "parameters": { "intensity": 0.7 }
                      }]
                    }]
                  }]
                }
                """.formatted(input.toString().replace("\\", "/"));

        NatronPocJobExtractor extractor = new NatronPocJobExtractor(new TimelineScriptParser());
        var job = extractor.extract(script, List.of("video.natron_vignette", "video.natron_color_grade"),
                tempDir.toString(), tempDir.resolve("out.mp4").toString());

        assertTrue(job.isPresent());
        assertEquals("video.natron_vignette", job.get().effectKey());
        assertEquals(input.toString(), job.get().inputLocalPath());
        assertEquals(0.7, job.get().intensity(), 0.001);
    }
}
