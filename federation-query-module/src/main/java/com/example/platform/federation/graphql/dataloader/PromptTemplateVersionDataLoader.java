package com.example.platform.federation.graphql.dataloader;

import com.example.platform.prompt.app.PromptTemplateService;
import org.dataloader.MappedBatchLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.stream.Collectors;

@Component
public class PromptTemplateVersionDataLoader implements MappedBatchLoader<String, List<Map<String, Object>>> {
    private static final Logger log = LoggerFactory.getLogger(PromptTemplateVersionDataLoader.class);

    private final PromptTemplateService promptTemplateService;

    public PromptTemplateVersionDataLoader(PromptTemplateService promptTemplateService) {
        this.promptTemplateService = promptTemplateService;
    }

    @Override
    public CompletionStage<Map<String, List<Map<String, Object>>>> load(Set<String> keys) {
        log.debug("Batch loading versions for {} prompt templates", keys.size());
        return CompletableFuture.supplyAsync(() -> keys.stream()
                .collect(Collectors.toMap(
                        templateId -> templateId,
                        templateId -> {
                            try {
                                return promptTemplateService.listVersions(templateId).stream()
                                        .map(v -> Map.<String, Object>of(
                                                "version", v.promptVersion(),
                                                "templateBody", v.templateBody() != null ? v.templateBody() : "",
                                                "changelog", v.changelog() != null ? v.changelog() : "",
                                                "createdBy", v.createdBy() != null ? v.createdBy() : "system"
                                        ))
                                        .collect(Collectors.toList());
                            } catch (Exception e) {
                                return List.of();
                            }
                        }
                )));
    }
}
