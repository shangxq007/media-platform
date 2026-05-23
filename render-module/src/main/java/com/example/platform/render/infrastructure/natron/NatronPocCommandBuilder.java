package com.example.platform.render.infrastructure.natron;

import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class NatronPocCommandBuilder {

    public static final String TOOL_KEY = "natron-poc-render";

    public List<String> buildArgs(NatronPocJob job, boolean fallbackToFfmpeg, String batchScriptPath,
                                  String readerNode, String writerNode) {
        List<String> args = new ArrayList<>();
        args.add("--effect-key");
        args.add(job.effectKey());
        args.add("--input");
        args.add(job.inputLocalPath());
        args.add("--output");
        args.add(job.outputLocalPath());
        args.add("--intensity");
        args.add(String.valueOf(job.intensity()));
        if ("video.natron_color_grade".equals(job.effectKey())) {
            args.add("--saturation");
            args.add(String.valueOf(job.saturation()));
        }
        if (batchScriptPath != null && !batchScriptPath.isBlank()) {
            args.add("--batch-script");
            args.add(batchScriptPath);
            args.add("--reader-node");
            args.add(readerNode);
            args.add("--writer-node");
            args.add(writerNode);
        }
        if (fallbackToFfmpeg) {
            args.add("--fallback-ffmpeg");
        }
        return args;
    }
}
