package com.example.platform.extension.app;

import com.example.platform.extension.domain.ToolRunRequest;
import com.example.platform.extension.domain.ToolRunResult;

public interface ToolRunner {
    ToolRunResult run(ToolRunRequest request);
}
