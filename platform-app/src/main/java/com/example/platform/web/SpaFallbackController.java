package com.example.platform.web;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;

/**
 * SPA fallback controller: forwards frontend document routes to index.html.
 * Handles the legacy /app/** shell and canonical workspace document routes.
 *
 * Excluded namespaces:
 * - /api/** — backend REST API
 * - /dev/** — dev diagnostic routes
 * - /admin/** — admin routes
 * - /actuator/** — management endpoints
 */
@Controller
public class SpaFallbackController {

    @RequestMapping(value = {"/app/**", "/w/**"})
    public String forwardToFrontend() {
        return "forward:/index.html";
    }
}
