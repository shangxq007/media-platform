package com.example.platform.prompt.app;

import com.example.platform.prompt.domain.PromptTemplate;
import com.example.platform.prompt.domain.PromptVersion;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
public class PromptRenderService {

    private static final Pattern VARIABLE_PATTERN = Pattern.compile("\\{\\{([^}]+)}}");

    private final Map<String, PromptTemplate> templatesById = new ConcurrentHashMap<>();
    private final Map<String, PromptTemplate> templatesByCode = new ConcurrentHashMap<>();
    private final Map<String, List<PromptVersion>> versionsByTemplateId = new ConcurrentHashMap<>();
    private final AtomicLong templateSeq = new AtomicLong(0);
    private final AtomicLong versionSeq = new AtomicLong(0);

    /**
     * Renders a prompt template by code with variable substitution.
     * Variables in the template use {{name}} syntax.
     */
    public String render(String templateCode, Map<String, Object> variables) {
        PromptTemplate template = templatesByCode.get(templateCode);
        if (template == null) {
            throw new IllegalArgumentException("Unknown prompt template: " + templateCode);
        }
        String content = template.content();
        if (variables == null || variables.isEmpty()) {
            return content;
        }
        Matcher matcher = VARIABLE_PATTERN.matcher(content);
        StringBuilder result = new StringBuilder();
        int end = 0;
        while (matcher.find()) {
            result.append(content, end, matcher.start());
            String key = matcher.group(1).trim();
            Object value = variables.get(key);
            result.append(value != null ? value.toString() : "");
            end = matcher.end();
        }
        result.append(content.substring(end));
        return result.toString();
    }

    /**
     * Creates a new prompt template.
     */
    public PromptTemplate createTemplate(String code, String content, List<String> variables) {
        if (templatesByCode.containsKey(code)) {
            throw new IllegalArgumentException("Template code already exists: " + code);
        }
        String id = "pt-" + templateSeq.incrementAndGet();
        List<String> vars = variables != null ? List.copyOf(variables) : List.of();
        PromptTemplate template = new PromptTemplate(id, code, content, vars, "ACTIVE");
        templatesById.put(id, template);
        templatesByCode.put(code, template);
        versionsByTemplateId.put(id, new ArrayList<>());
        return template;
    }

    /**
     * Creates a new version for an existing template.
     */
    public PromptVersion createVersion(String templateId, String content, String changelog) {
        PromptTemplate template = templatesById.get(templateId);
        if (template == null) {
            throw new IllegalArgumentException("Unknown template id: " + templateId);
        }
        List<PromptVersion> versions = versionsByTemplateId.computeIfAbsent(
                templateId, k -> new ArrayList<>());
        int nextVersion = versions.size() + 1;
        String id = "pv-" + versionSeq.incrementAndGet();
        PromptVersion version = new PromptVersion(id, templateId, nextVersion, content, changelog);
        versions.add(version);
        return version;
    }

    public Optional<PromptTemplate> findTemplateByCode(String code) {
        return Optional.ofNullable(templatesByCode.get(code));
    }

    public Optional<PromptTemplate> findTemplateById(String id) {
        return Optional.ofNullable(templatesById.get(id));
    }

    public List<PromptTemplate> listTemplates() {
        return List.copyOf(templatesById.values());
    }

    public List<PromptVersion> listVersions(String templateId) {
        return List.copyOf(versionsByTemplateId.getOrDefault(templateId, List.of()));
    }
}
