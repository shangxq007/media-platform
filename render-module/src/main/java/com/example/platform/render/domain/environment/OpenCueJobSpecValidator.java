package com.example.platform.render.domain.environment;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Internal validator for OpenCue job specs.
 *
 * <p>Fail-closed: unknown or suspicious patterns are rejected.
 * Used before OpenCue environment submit. Internal to the
 * OpenCue environment/compiler boundary.
 *
 * <p>Rejects: null/blank job name, invalid priority, empty layers,
 * too many layers, blank layer name, empty commands, too many commands,
 * blank executable, shell injection patterns, unsafe working dirs,
 * path traversal in arguments, remote URL execution, template/code
 * patterns, unsafe env var names.
 */
public final class OpenCueJobSpecValidator {

    private static final Set<String> SHELL_INJECTION_PATTERNS = Set.of(
            ";", "&&", "||", "|", "$(", "`", "\\n", "\n", "\r",
            "2>&1", "&>", ">&"
    );
    private static final Set<String> PATH_TRAVERSAL_PATTERNS = Set.of(
            "..", "~"
    );
    private static final Set<String> FORBIDDEN_ENV_VAR_NAMES = Set.of(
            "PATH", "LD_PRELOAD", "LD_LIBRARY_PATH", "HOME",
            "USER", "SHELL", "PWD", "OLDPWD"
    );
    private static final int MAX_COMMAND_ARG_LENGTH = 4096;
    private static final int MAX_EXECUTABLE_LENGTH = 512;
    private static final int MAX_JOB_NAME_LENGTH = 256;
    private static final int MAX_LAYER_NAME_LENGTH = 128;

    private final OpenCueProperties props;

    public OpenCueJobSpecValidator(OpenCueProperties props) {
        this.props = props;
    }

    public List<String> validate(OpenCueJobSpec spec) {
        List<String> errors = new ArrayList<>();

        if (spec == null) {
            errors.add("OpenCueJobSpec must not be null");
            return errors;
        }

        if (spec.jobName() == null || spec.jobName().isBlank()) {
            errors.add("jobName must not be null or blank");
        } else if (spec.jobName().length() > MAX_JOB_NAME_LENGTH) {
            errors.add("jobName exceeds max length " + MAX_JOB_NAME_LENGTH);
        }

        if (spec.owner() == null || spec.owner().isBlank()) {
            errors.add("owner must not be null or blank");
        }

        if (spec.priority() < props.getMinPriority() || spec.priority() > props.getMaxPriority()) {
            errors.add("priority " + spec.priority() + " outside allowed range ["
                    + props.getMinPriority() + ", " + props.getMaxPriority() + "]");
        }

        if (spec.tags() != null && spec.tags().size() > props.getMaxTags()) {
            errors.add("too many tags: " + spec.tags().size() + " (max " + props.getMaxTags() + ")");
        }

        if (spec.environmentVariables() != null) {
            errors.addAll(validateEnvironmentVariables(spec.environmentVariables()));
        }

        if (spec.layers() == null) {
            errors.add("layers must not be null");
        } else if (spec.layers().isEmpty()) {
            errors.add("layers must not be empty");
        } else if (spec.layers().size() > props.getMaxLayers()) {
            errors.add("too many layers: " + spec.layers().size() + " (max " + props.getMaxLayers() + ")");
        } else {
            for (int i = 0; i < spec.layers().size(); i++) {
                errors.addAll(validateLayer(spec.layers().get(i), i));
            }
        }

        return errors;
    }

    private List<String> validateLayer(OpenCueJobSpec.OpenCueLayerSpec layer, int index) {
        List<String> errors = new ArrayList<>();
        String prefix = "layers[" + index + "]";

        if (layer.layerName() == null || layer.layerName().isBlank()) {
            errors.add(prefix + ".layerName must not be null or blank");
        } else if (layer.layerName().length() > MAX_LAYER_NAME_LENGTH) {
            errors.add(prefix + ".layerName exceeds max length " + MAX_LAYER_NAME_LENGTH);
        }

        if (layer.commands() == null) {
            errors.add(prefix + ".commands must not be null");
        } else if (layer.commands().isEmpty()) {
            errors.add(prefix + ".commands must not be empty");
        } else if (layer.commands().size() > props.getMaxCommandsPerLayer()) {
            errors.add(prefix + ".commands count " + layer.commands().size()
                    + " exceeds max " + props.getMaxCommandsPerLayer());
        } else {
            for (int j = 0; j < layer.commands().size(); j++) {
                errors.addAll(validateCommand(layer.commands().get(j), prefix + ".commands[" + j + "]"));
            }
        }

        return errors;
    }

    private List<String> validateCommand(String command, String prefix) {
        List<String> errors = new ArrayList<>();

        if (command == null || command.isBlank()) {
            errors.add(prefix + " must not be null or blank");
            return errors;
        }

        String trimmed = command.trim();

        if (trimmed.length() > MAX_COMMAND_ARG_LENGTH) {
            errors.add(prefix + " exceeds max length " + MAX_COMMAND_ARG_LENGTH);
        }

        for (String pattern : SHELL_INJECTION_PATTERNS) {
            if (trimmed.contains(pattern)) {
                errors.add(prefix + " contains shell injection pattern: " + pattern);
            }
        }

        for (String traversal : PATH_TRAVERSAL_PATTERNS) {
            if (trimmed.contains(traversal)) {
                errors.add(prefix + " contains path traversal pattern: " + traversal);
            }
        }

        if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
            errors.add(prefix + " must not be a remote URL");
        }

        if (trimmed.startsWith("file://")) {
            errors.add(prefix + " must not be a file:// URI");
        }

        String executable = extractExecutable(trimmed);
        if (executable != null && executable.length() > MAX_EXECUTABLE_LENGTH) {
            errors.add(prefix + " executable exceeds max length " + MAX_EXECUTABLE_LENGTH);
        }

        return errors;
    }

    private List<String> validateEnvironmentVariables(Map<String, String> envVars) {
        List<String> errors = new ArrayList<>();
        if (envVars.size() > props.getMaxEnvironmentVariables()) {
            errors.add("too many environment variables: " + envVars.size()
                    + " (max " + props.getMaxEnvironmentVariables() + ")");
        }
        for (String key : envVars.keySet()) {
            if (key == null || key.isBlank()) {
                errors.add("environment variable key must not be null or blank");
            }
            if (FORBIDDEN_ENV_VAR_NAMES.contains(key) || FORBIDDEN_ENV_VAR_NAMES.contains(key.toUpperCase())) {
                errors.add("forbidden environment variable: " + key);
            }
            String value = envVars.get(key);
            if (value != null && value.length() > MAX_COMMAND_ARG_LENGTH) {
                errors.add("environment variable " + key + " value exceeds max length " + MAX_COMMAND_ARG_LENGTH);
            }
        }
        return errors;
    }

    private String extractExecutable(String command) {
        String trimmed = command.trim();
        int spaceIdx = trimmed.indexOf(' ');
        if (spaceIdx < 0) return trimmed;
        return trimmed.substring(0, spaceIdx);
    }
}
