package com.example.platform.web;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;

/**
 * SPA fallback controller: forwards frontend document routes to index.html.
 * Only handles /app/** which is the frontend client-side routing namespace.
 *
 * Excluded namespaces:
 * - /api/** — backend REST API
 * - /dev/** — dev diagnostic routes
 * - /admin/** — admin routes
 * - /actuator/** — management endpoints
 */
@Controller
public class SpaFallbackController {

    @RequestMapping(value = "/app/**")
    public String forwardToFrontend() {
        return "forward:/index.html";
    }
}
