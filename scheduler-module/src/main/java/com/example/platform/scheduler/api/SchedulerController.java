package com.example.platform.scheduler.api;

import com.example.platform.scheduler.app.ScheduleRegistryService;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1")
public class SchedulerController {
    private final ScheduleRegistryService service;

    public SchedulerController(ScheduleRegistryService service) {
        this.service = service;
    }

    @PostMapping("/internal/scheduler/run/{jobKey}")
    public Map<String, String> runJob(@PathVariable String jobKey) {
        return Map.of("jobKey", jobKey, "status", "TRIGGERED");
    }

    @GetMapping("/scheduler/overview")
    public Map<String, Object> overview() {
        return service.overview();
    }
}
