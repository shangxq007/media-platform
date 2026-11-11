package com.example.platform.web;

import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.*;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.hamcrest.Matchers.containsString;

class GlobalExceptionHandlerMethodTest {
    @RestController static class ReadOnly {
        @GetMapping("/read-only") Map<String,String> get(){return Map.of("status","read");}
    }
    @Test void aRemovedWriteRouteIsNotMisreportedAsAnInternalFailure() throws Exception {
        var mvc=standaloneSetup(new ReadOnly()).setControllerAdvice(new GlobalExceptionHandler(Optional.empty())).build();
        mvc.perform(post("/read-only").contentType("application/json").content("{}"))
            .andExpect(status().isMethodNotAllowed()).andExpect(header().string("Allow",containsString("GET")))
            .andExpect(jsonPath("$.errorCode").value("METHOD_NOT_ALLOWED"));
    }
}
