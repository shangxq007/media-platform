package com.example.platform.policy.api;

import com.example.platform.policy.app.PolicyGovernanceService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/policy/governance")
public class PolicyController {
    private final PolicyGovernanceService service;

    public PolicyController(PolicyGovernanceService service) {
        this.service = service;
    }

    @GetMapping("/overview")
    public Map<String, Object> overview() {
        return service.overview();
    }
}
