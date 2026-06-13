package com.example.platform.render.infrastructure.renderplan;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * Tool Router - routes execution to the appropriate tool.
 * 
 * <p>Routing rules:
 * <ul>
 *   <li>clip → FFmpeg</li>
 *   <li>transition → MLT</li>
 *   <li>scene → Remotion</li>
 *   <li>audio → FFmpeg</li>
 * </ul>
 * 
 * <p>NO dynamic routing systems.
 */
@Service
public class ToolRouter {

    private static final Logger log = LoggerFactory.getLogger(ToolRouter.class);

    private final FFmpegTool ffmpegTool;
    private final MLTTool mltTool;
    private final RemotionTool remotionTool;

    public ToolRouter(FFmpegTool ffmpegTool, MLTTool mltTool, RemotionTool remotionTool) {
        this.ffmpegTool = ffmpegTool;
        this.mltTool = mltTool;
        this.remotionTool = remotionTool;
    }

    /**
     * Get the appropriate tool for a node type.
     */
    public RenderTool getTool(RenderPlan.ToolType toolType) {
        return switch (toolType) {
            case FFMPEG -> ffmpegTool;
            case MLT -> mltTool;
            case REMOTION -> remotionTool;
        };
    }

    /**
     * Get tool for a node type.
     */
    public RenderTool getToolForNode(RenderPlan.NodeType nodeType) {
        return switch (nodeType) {
            case CLIP, AUDIO, OUTPUT -> ffmpegTool;
            case TRANSITION -> mltTool;
            case SCENE -> remotionTool;
        };
    }

    // ---------------------------------------------------------------------------
    // Tool Interface
    // ---------------------------------------------------------------------------

    public interface RenderTool {
        ToolResult execute(String nodeId, String nodeType, Map<String, Object> params, Map<String, String> inputs);
        String getName();
        boolean isAvailable();
    }

    public record ToolResult(
            boolean success,
            String outputUri,
            String error,
            long durationMs
    ) {
        public static ToolResult success(String outputUri, long durationMs) {
            return new ToolResult(true, outputUri, null, durationMs);
        }

        public static ToolResult failure(String error) {
            return new ToolResult(false, null, error, 0);
        }
    }
}
