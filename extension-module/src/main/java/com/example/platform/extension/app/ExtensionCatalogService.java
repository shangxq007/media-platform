package com.example.platform.extension.app;

import com.example.platform.extension.domain.ExtensionDefinition;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ExtensionCatalogService {

    public List<String> extensionCodes() {
        return List.of(
                "tool.ffprobe",
                "script.prompt_patch",
                "provider.publish.youtube",
                "provider.third_party.ai_openai",
                "provider.third_party.ai_anthropic",
                "provider.third_party.render_cloud",
                "prompt.extension.custom_template",
                "prompt.extension.render_script",
                "workflow.step.pre_process.custom",
                "workflow.step.post_process.quality_check",
                "scheduler.job.cleanup_stale_data",
                "scheduler.job.reconcile_invoices"
        );
    }

    public List<ExtensionDefinition> listExtensionDefinitions() {
        return List.of(
                new ExtensionDefinition("provider.third_party.ai", "PROVIDER", "Java", "1.0.0"),
                new ExtensionDefinition("provider.third_party.render", "PROVIDER", "Java", "1.0.0"),
                new ExtensionDefinition("prompt.extension.template", "PROMPT_SCRIPT", "Groovy", "1.0.0"),
                new ExtensionDefinition("prompt.extension.render_script", "RENDER_SCRIPT", "JavaScript", "1.0.0"),
                new ExtensionDefinition("workflow.step.custom", "WORKFLOW_STEP", "Java", "1.0.0"),
                new ExtensionDefinition("scheduler.job.custom", "SCHEDULER_JOB", "Java", "1.0.0")
        );
    }
}
