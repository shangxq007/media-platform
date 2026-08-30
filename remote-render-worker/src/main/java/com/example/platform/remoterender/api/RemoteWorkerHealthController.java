package com.example.platform.remoterender.api;

import java.util.Map;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

/** Side-effect-free unauthenticated process-health probe. */
@RestController
public final class RemoteWorkerHealthController {

    @RequestMapping(path = "/healthz", method = {RequestMethod.GET, RequestMethod.HEAD})
    public Map<String, String> health() {
        return Map.of("status", "UP");
    }
}
