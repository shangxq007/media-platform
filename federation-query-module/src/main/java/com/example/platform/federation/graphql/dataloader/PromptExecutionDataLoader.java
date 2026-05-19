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
public class PromptExecutionDataLoader implements MappedBatchLoader<String, List<Map<String, Object>>> {
    private static final Logger log = LoggerFactory.getLogger(PromptExecutionDataLoader.class);

    private final PromptTemplateService promptTemplateService;

    public PromptExecutionDataLoader(PromptTemplateService promptTemplateService) {
        this.promptTemplateService = promptTemplateService;
    }

    @Override
    public CompletionStage<Map<String, List<Map<String, Object>>>> load(Set<String> keys) {
        log.debug("Batch loading executions for {} prompt templates", keys.size());
        return CompletableFuture.supplyAsync(() -> keys.stream()
                .collect(Collectors.toMap(
                        templateId -> templateId,
                        templateId -> {
                            try {
                                return promptTemplateService.listExecutions(templateId).stream()
                                        .map(e -> Map.<String, Object>of(
                                                "executionId", e.executionId(),
                                                "status", e.status() != null ? e.status().name() : "UNKNOWN",
                                                "riskLevel", e.riskLevel() != null ? e.riskLevel().name() : "LOW",
                                                "costEstimate", e.costEstimate(),
                                                "startedAt", e.startedAt() != null ? e.startedAt().toString() : ""
                                        ))
                                        .collect(Collectors.toList());
                            } catch (Exception e) {
                                return List.of();
                            }
                        }
                )));
    }
}
