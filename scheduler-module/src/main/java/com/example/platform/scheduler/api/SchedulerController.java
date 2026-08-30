package com.example.platform.scheduler.api;

import com.example.platform.scheduler.app.ScheduleRegistryService;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api")
public class SchedulerController {
    private final ScheduleRegistryService service;

    public SchedulerController(ScheduleRegistryService service) {
        this.service = service;
    }

    @GetMapping("/scheduler/overview")
    public Map<String, Object> overview() {
        return service.overview();
    }
}
