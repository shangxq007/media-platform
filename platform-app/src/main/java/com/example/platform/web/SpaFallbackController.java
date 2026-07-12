package com.example.platform.web;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;

/**
 * SPA fallback controller: forwards frontend document routes to index.html.
 * Excludes API routes, actuator, OpenAPI, and static assets.
 */
@Controller
public class SpaFallbackController {

    @RequestMapping(value = {
        "/app/**",
        "/admin/**",
        "/dev/**"
    })
    public String forwardToFrontend() {
        return "forward:/index.html";
    }
}
