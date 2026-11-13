package com.example.platform.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class SpaFallbackControllerTest {
    @Test void canonicalNleRouteLoadsTheReactApplicationDocument() throws Exception {
        MockMvcBuilders.standaloneSetup(new SpaFallbackController()).build()
                .perform(get("/w/workspace-1/projects/project-1/edit"))
                .andExpect(status().isOk())
                .andExpect(forwardedUrl("/index.html"));
    }
}
