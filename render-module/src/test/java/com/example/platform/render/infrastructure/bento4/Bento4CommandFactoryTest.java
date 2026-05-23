package com.example.platform.render.infrastructure.bento4;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

class Bento4CommandFactoryTest {

    private final Bento4CommandFactory factory = new Bento4CommandFactory();

    @Test
    void buildFragmentCommandIncludesInputAndOutput() {
        List<String> args = factory.buildFragmentCommand("mp4fragment", "/in.mp4", "/out-frag.mp4");
        assertEquals("mp4fragment", args.get(0));
        assertTrue(args.contains("/in.mp4"));
        assertTrue(args.contains("/out-frag.mp4"));
    }

    @Test
    void buildMp4DashCommandUsesOutputDir() {
        List<String> args = factory.buildMp4DashCommand(
                "mp4dash", "/frag.mp4", Path.of("/packaged"), "dash", false);
        assertEquals("mp4dash", args.get(0));
        assertTrue(args.contains("--output-dir"));
        assertTrue(args.contains("/packaged"));
    }
}
