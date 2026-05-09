package com.example.platform.sandbox.api;

import com.example.platform.sandbox.app.SandboxRuntimeService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/sandbox/runtime")
public class SandboxController {
    private final SandboxRuntimeService service;

    public SandboxController(SandboxRuntimeService service) {
        this.service = service;
    }

    @GetMapping("/overview")
    public Map<String, Object> overview() {
        return service.overview();
    }
}
