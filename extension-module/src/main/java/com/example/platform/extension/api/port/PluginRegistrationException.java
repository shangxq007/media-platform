package com.example.platform.extension.api.port;

import com.example.platform.extension.domain.PluginDescriptorValidationIssue;
import java.util.List;

public final class PluginRegistrationException extends RuntimeException {
    private final List<PluginDescriptorValidationIssue> issues;
    public PluginRegistrationException(List<PluginDescriptorValidationIssue> issues) {
        super(issues.toString());
        this.issues = List.copyOf(issues);
    }
    public List<PluginDescriptorValidationIssue> issues() { return issues; }
}
