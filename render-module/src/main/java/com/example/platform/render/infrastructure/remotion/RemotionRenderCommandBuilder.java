package com.example.platform.render.infrastructure.remotion;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class RemotionRenderCommandBuilder {

    private Path remotionBinary = Path.of("npx");
    private String compositionId = "CaptionComposition";
    private Path workingDir;
    private Path outputPath;
    private RemotionInputProps inputProps;
    private String format = "mp4";
    private int width = 1920;
    private int height = 1080;
    private int fps = 30;
    private int concurrency = 1;
    private boolean overwrite = true;

    public RemotionRenderCommandBuilder remotionBinary(Path binary) {
        this.remotionBinary = binary;
        return this;
    }

    public RemotionRenderCommandBuilder compositionId(String id) {
        this.compositionId = id;
        return this;
    }

    public RemotionRenderCommandBuilder workingDir(Path dir) {
        this.workingDir = dir;
        return this;
    }

    public RemotionRenderCommandBuilder outputPath(Path path) {
        this.outputPath = path;
        return this;
    }

    public RemotionRenderCommandBuilder inputProps(RemotionInputProps props) {
        this.inputProps = props;
        return this;
    }

    public RemotionRenderCommandBuilder format(String fmt) {
        this.format = fmt;
        return this;
    }

    public RemotionRenderCommandBuilder width(int w) {
        this.width = w;
        return this;
    }

    public RemotionRenderCommandBuilder height(int h) {
        this.height = h;
        return this;
    }

    public RemotionRenderCommandBuilder fps(int fps) {
        this.fps = fps;
        return this;
    }

    public RemotionRenderCommandBuilder concurrency(int c) {
        this.concurrency = c;
        return this;
    }

    public RemotionRenderCommandBuilder overwrite(boolean v) {
        this.overwrite = v;
        return this;
    }

    public List<String> build() {
        if (outputPath == null) {
            throw new IllegalStateException("outputPath must not be null");
        }

        List<String> validationErrors = RemotionInputPropsValidator.validate(inputProps);
        if (!validationErrors.isEmpty()) {
            throw new IllegalArgumentException("Invalid Remotion input props: " + validationErrors);
        }

        String propsJson = serializeProps();
        if (inputProps != null && inputProps.fontSpecs() != null) {
            for (RemotionFontSpec font : inputProps.fontSpecs()) {
                if (font.sourceUrl() != null && propsJson.contains("\"sourceUrl\":\"" + font.sourceUrl() + "\"")) {
                    throw new IllegalStateException(
                            "sourceUrl must not be serialized. Only subsetUrl (via effectiveUrl) must appear in props.");
                }
            }
        }

        List<String> cmd = new ArrayList<>();
        cmd.add(remotionBinary.toString());
        cmd.add("remotion");
        cmd.add("render");
        cmd.add(compositionId);
        cmd.add(outputPath.toString());
        cmd.add("--props=" + propsJson);
        cmd.add("--format=" + format);
        cmd.add("--width=" + width);
        cmd.add("--height=" + height);
        cmd.add("--fps=" + fps);
        cmd.add("--concurrency=" + concurrency);
        if (overwrite) {
            cmd.add("--overwrite");
        }
        return cmd;
    }

    private String serializeProps() {
        if (inputProps == null) return "{}";
        StringBuilder sb = new StringBuilder("{");
        sb.append("\"compositionWidth\":").append(inputProps.compositionWidth()).append(",");
        sb.append("\"compositionHeight\":").append(inputProps.compositionHeight()).append(",");
        sb.append("\"fps\":").append(inputProps.fps()).append(",");
        sb.append("\"durationInFrames\":").append(inputProps.durationInFrames()).append(",");
        sb.append("\"captions\":").append(serializeCaptions()).append(",");
        sb.append("\"fontSpecs\":").append(serializeFontSpecs());
        sb.append("}");
        return sb.toString();
    }

    private String serializeCaptions() {
        if (inputProps.captions() == null || inputProps.captions().isEmpty()) return "[]";
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < inputProps.captions().size(); i++) {
            RemotionCaption cap = inputProps.captions().get(i);
            if (i > 0) sb.append(",");
            sb.append("{\"id\":\"").append(cap.id()).append("\",");
            sb.append("\"text\":\"").append(escapeJson(cap.text())).append("\",");
            sb.append("\"startTime\":").append(cap.startTime()).append(",");
            sb.append("\"endTime\":").append(cap.endTime()).append(",");
            sb.append("\"hasWordLevelTiming\":").append(cap.hasWordLevelTiming()).append("}");
        }
        sb.append("]");
        return sb.toString();
    }

    private String serializeFontSpecs() {
        if (inputProps.fontSpecs() == null || inputProps.fontSpecs().isEmpty()) return "[]";
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < inputProps.fontSpecs().size(); i++) {
            RemotionFontSpec font = inputProps.fontSpecs().get(i);
            if (i > 0) sb.append(",");
            sb.append("{\"fontFamily\":\"").append(font.fontFamily()).append("\",");
            sb.append("\"weight\":").append(font.weight()).append(",");
            sb.append("\"style\":\"").append(font.style()).append("\",");
            sb.append("\"effectiveUrl\":\"").append(font.effectiveUrl()).append("\",");
            sb.append("\"hash\":\"").append(font.hash()).append("\",");
            sb.append("\"productionSafe\":").append(font.productionSafe()).append("}");
        }
        sb.append("]");
        return sb.toString();
    }

    private String escapeJson(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
