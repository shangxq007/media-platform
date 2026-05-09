package com.example.platform.prompt.api;

import com.example.platform.prompt.api.dto.RenderPromptRequest;
import com.example.platform.prompt.app.PromptRenderService;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/prompts")
public class PromptController {

    private final PromptRenderService promptRenderService;

    public PromptController(PromptRenderService promptRenderService) {
        this.promptRenderService = promptRenderService;
    }

    @PostMapping("/render")
    public Map<String, String> render(@RequestBody RenderPromptRequest request) {
        return Map.of("rendered", promptRenderService.render(request.templateCode(), request.variables()));
    }
}
