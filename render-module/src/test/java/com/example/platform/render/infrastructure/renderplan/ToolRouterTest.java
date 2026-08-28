package com.example.platform.render.infrastructure.renderplan;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class ToolRouterTest {

    private final MLTTool mltTool = new MLTTool();
    private final RemotionTool remotionTool = new RemotionTool();
    private final ToolRouter router = new ToolRouter(mltTool, remotionTool);

    @Test
    void preservesMltAndRemotionRouting() {
        assertSame(mltTool, router.getTool(RenderPlanIr.ToolType.MLT));
        assertSame(remotionTool, router.getTool(RenderPlanIr.ToolType.REMOTION));
        assertSame(mltTool, router.getToolForNode(RenderPlanIr.NodeType.TRANSITION));
        assertSame(remotionTool, router.getToolForNode(RenderPlanIr.NodeType.SCENE));
    }

    @Test
    void legacyFfmpegRoutesFailClosed() {
        assertThrows(IllegalStateException.class,
                () -> router.getTool(RenderPlanIr.ToolType.FFMPEG));
        assertThrows(IllegalStateException.class,
                () -> router.getToolForNode(RenderPlanIr.NodeType.CLIP));
        assertThrows(IllegalStateException.class,
                () -> router.getToolForNode(RenderPlanIr.NodeType.AUDIO));
        assertThrows(IllegalStateException.class,
                () -> router.getToolForNode(RenderPlanIr.NodeType.OUTPUT));
    }
}
