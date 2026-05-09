package com.example.platform.extension.app;

import com.example.platform.extension.domain.ExtensionDefinition;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ExtensionCatalogService {
    public List<String> extensionCodes() {
        return List.of("tool.ffprobe", "script.prompt_patch", "provider.publish.youtube");
    }
}
