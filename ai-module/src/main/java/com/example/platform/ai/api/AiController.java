package com.example.platform.ai.api;

import com.example.platform.ai.api.dto.ChatRequestDto;
import com.example.platform.ai.api.dto.ChatResponseDto;
import com.example.platform.ai.api.dto.GenerateScriptRequestDto;
import com.example.platform.ai.api.dto.GenerateScriptResponseDto;
import com.example.platform.ai.app.AiGatewayService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/ai")
public class AiController {
    private final AiGatewayService service;

    public AiController(AiGatewayService service) {
        this.service = service;
    }

    @PostMapping("/chat")
    public ChatResponseDto chat(@Valid @RequestBody ChatRequestDto request) {
        var result = service.chat(request.capability(), request.prompt());
        return new ChatResponseDto(result.provider(), result.model(), result.content());
    }

    @PostMapping("/generate-script")
    public GenerateScriptResponseDto generateScript(@Valid @RequestBody GenerateScriptRequestDto request) {
        var result = service.generateScript(request.prompt(), request.profile());
        return new GenerateScriptResponseDto(
                result.scriptContent(),
                result.modelUsed(),
                result.tokensUsed(),
                result.generatedAt());
    }
}
