package com.example.platform.render.infrastructure.renderplan;

import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * MLT tool implementation.
 * 
 * <p>Handles transition processing.
 */
@Component
public class MLTTool implements ToolRouter.RenderTool {

    static final String UNAVAILABLE_REASON =
            "MLT tool is unavailable until a governed execution implementation is configured";

    @Override
    public ToolRouter.ToolResult execute(String nodeId, String nodeType, Map<String, Object> params, Map<String, String> inputs) {
        return ToolRouter.ToolResult.failure(UNAVAILABLE_REASON);
    }

    @Override
    public String getName() {
        return "mlt";
    }

    @Override
    public boolean isAvailable() {
        return false;
    }
}
