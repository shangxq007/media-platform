package com.example.platform.render.api.diagnostics;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;

import java.util.*;

@Component
@ConditionalOnProperty(name = "app.diagnostics.mapping-dump", havingValue = "true")
public class RenderControllerMappingDiagnostics {

    private static final Logger log = LoggerFactory.getLogger(RenderControllerMappingDiagnostics.class);
    private final RequestMappingHandlerMapping handlerMapping;

    public RenderControllerMappingDiagnostics(RequestMappingHandlerMapping handlerMapping) {
        this.handlerMapping = handlerMapping;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void dumpMappings() {
        log.info("=== MAPPING DIAGNOSTIC START ===");
        int previewCount = 0;
        int contentCount = 0;
        int renderCount = 0;
        for (var entry : handlerMapping.getHandlerMethods().entrySet()) {
            RequestMappingInfo info = entry.getKey();
            var handler = entry.getValue();
            String patterns = info.getPatternValues().toString();
            String methods = info.getMethodsCondition().toString();
            String handlerClass = handler.getMethod().getDeclaringClass().getName();
            String handlerMethod = handler.getMethod().getName();
            if (patterns.contains("preview")) {
                log.info("PREVIEW: {} {} -> {}.{}", methods, patterns, handlerClass, handlerMethod);
                previewCount++;
            }
            if (patterns.contains("content")) {
                log.info("CONTENT: {} {} -> {}.{}", methods, patterns, handlerClass, handlerMethod);
                contentCount++;
            }
            if (patterns.contains("render/jobs")) renderCount++;
        }
        log.info("Counts: preview={}, content={}, render/jobs={}", previewCount, contentCount, renderCount);
        try {
            Class<?> rcClass = Class.forName("com.example.platform.render.api.RenderController");
            log.info("ClassLoader: {}", rcClass.getClassLoader());
            log.info("Resource: {}", rcClass.getResource("RenderController.class"));
            List<String> names = new ArrayList<>();
            for (var m : rcClass.getDeclaredMethods()) names.add(m.getName());
            log.info("Methods ({}): {}", names.size(), names);
            log.info("Has uploadPreviewMedia: {}", names.contains("uploadPreviewMedia"));
            log.info("Has getArtifactContent: {}", names.contains("getArtifactContent"));
            for (var m : rcClass.getDeclaredMethods()) {
                if (m.getName().equals("uploadPreviewMedia") || m.getName().equals("getArtifactContent")) {
                    log.info("Method {} annotations: {}", m.getName(), Arrays.toString(m.getAnnotations()));
                }
            }
        } catch (Exception e) {
            log.error("Inspect failed: {}", e.getMessage());
        }
        log.info("=== MAPPING DIAGNOSTIC END ===");
    }
}
