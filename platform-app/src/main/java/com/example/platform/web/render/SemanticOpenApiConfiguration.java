package com.example.platform.web.render;

@org.springframework.context.annotation.Configuration(proxyBeanMethods = false)
class SemanticOpenApiConfiguration {
    @org.springframework.context.annotation.Bean
    org.springdoc.core.customizers.GlobalOpenApiCustomizer semanticBatchRequestSchema() {
        return api -> {
            if (api.getPaths() == null) return;
            for (var entry : api.getPaths().entrySet()) {
                if (!entry.getKey().matches(".*/timeline-operations/batch/v2/(preview|apply)")) continue;
                var post = entry.getValue().getPost();
                if (post == null || post.getRequestBody() == null) continue;
                var json = post.getRequestBody().getContent().get("application/json");
                if (json != null) json.setSchema(io.swagger.v3.core.util.Json31.mapper().convertValue(
                        com.example.platform.web.render.TimelineBatchSchema.batch(), io.swagger.v3.oas.models.media.Schema.class));
            }
        };
    }

}
