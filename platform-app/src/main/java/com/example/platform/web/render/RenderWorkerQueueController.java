package com.example.platform.web.render;

import com.example.platform.render.app.RenderWorkerQueueJob;
import com.example.platform.render.app.RenderWorkerQueueService;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/render/worker-queue")
@ConditionalOnProperty(prefix = "app.render.worker-queue", name = "enabled", havingValue = "true")
public class RenderWorkerQueueController {

    private final RenderWorkerQueueService queueService;

    public RenderWorkerQueueController(RenderWorkerQueueService queueService) {
        this.queueService = queueService;
    }

    @GetMapping("/natron")
    public ResponseEntity<Map<String, Object>> natronQueueSnapshot() {
        List<RenderWorkerQueueJob> jobs = queueService.snapshotNatronQueue();
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("workerType", RenderWorkerQueueService.WORKER_TYPE_NATRON);
        body.put("depth", jobs.size());
        body.put("jobs", jobs);
        return ResponseEntity.ok(body);
    }
}
