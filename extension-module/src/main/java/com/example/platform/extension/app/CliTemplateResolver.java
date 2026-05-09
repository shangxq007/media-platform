package com.example.platform.extension.app;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class CliTemplateResolver {

    private static final Pattern PLACEHOLDER = Pattern.compile("\\{([^{}]+)}");

    /**
     * Substitutes {@code {name}} in each template string using {@code params}. Missing keys throw.
     */
    public List<String> resolveArgs(List<String> argTemplates, Map<String, String> params) {
        List<String> resolved = new ArrayList<>(argTemplates.size());
        for (String template : argTemplates) {
            resolved.add(resolveOne(template, params));
        }
        return resolved;
    }

    private static String resolveOne(String template, Map<String, String> params) {
        if (template == null) {
            return "";
        }
        Matcher matcher = PLACEHOLDER.matcher(template);
        StringBuilder out = new StringBuilder();
        int end = 0;
        while (matcher.find()) {
            out.append(template, end, matcher.start());
            String key = matcher.group(1).trim();
            if (key.isEmpty()) {
                throw new IllegalArgumentException("empty placeholder in CLI arg template");
            }
            if (!params.containsKey(key) || params.get(key) == null) {
                throw new IllegalArgumentException("missing parameter for placeholder: {" + key + "}");
            }
            String value = params.get(key);
            if (value.indexOf('\u0000') >= 0) {
                throw new IllegalArgumentException("invalid parameter value for: " + key);
            }
            out.append(value);
            end = matcher.end();
        }
        out.append(template.substring(end));
        return out.toString();
    }
}
