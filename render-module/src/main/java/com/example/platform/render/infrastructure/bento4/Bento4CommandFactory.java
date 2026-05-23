package com.example.platform.render.infrastructure.bento4;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class Bento4CommandFactory {

    public List<String> buildFragmentCommand(String mp4fragmentBin, String inputUri, String fragmentedOutput) {
        List<String> args = new ArrayList<>();
        args.add(mp4fragmentBin);
        args.add("--fragment-duration");
        args.add("4000");
        args.add("--index");
        args.add("1");
        args.add(inputUri);
        args.add(fragmentedOutput);
        return args;
    }

    public List<String> buildMp4DashCommand(String mp4dashBin, String fragmentedInput, Path outputDir,
                                            String format, boolean includeHls) {
        List<String> args = new ArrayList<>();
        args.add(mp4dashBin);
        args.add("--output-dir");
        args.add(outputDir.toString());
        if (includeHls || "hls".equalsIgnoreCase(format)) {
            args.add("--hls");
        }
        args.add(fragmentedInput);
        return args;
    }
}
