package com.example.platform.render.infrastructure.shaka;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class ShakaCommandFactory {

    public List<String> buildDashPackageCommand(String packagerBin, String inputUri, Path outputDir) {
        List<String> args = new ArrayList<>();
        args.add(packagerBin);
        args.add("in=" + inputUri + ",stream=video,output=" + outputDir.resolve("video.mp4"));
        args.add("in=" + inputUri + ",stream=audio,output=" + outputDir.resolve("audio.mp4"));
        args.add("--mpd_output");
        args.add(outputDir.resolve("stream.mpd").toString());
        args.add("--segment_duration");
        args.add("4");
        return args;
    }
}
