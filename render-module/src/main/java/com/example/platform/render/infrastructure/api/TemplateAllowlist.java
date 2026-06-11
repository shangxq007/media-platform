package com.example.platform.render.infrastructure.api;

import java.util.Set;

public interface TemplateAllowlist {
    boolean isAllowed(String templateId);
}
