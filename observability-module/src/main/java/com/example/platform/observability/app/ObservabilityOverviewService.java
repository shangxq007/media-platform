package com.example.platform.observability.app;

import com.example.platform.shared.logging.TraceKeys;
import java.time.OffsetDateTime;
import java.util.List;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
public class ObservabilityOverviewService {
    public Map<String, Object> overview() {
        return Map.of(
                "module", "observability-module",
                "status", "active",
                "description", "系统可观测性模块，提供 trace/request 关联与基础可观测约定。",
                "timestamp", OffsetDateTime.now().toString(),
                "traceKeys", List.of(
                        TraceKeys.TRACE_ID,
                        TraceKeys.REQUEST_ID,
                        TraceKeys.TENANT_ID,
                        TraceKeys.PROJECT_ID,
                        TraceKeys.JOB_ID,
                        TraceKeys.WORKFLOW_ID
                ),
                "headers", List.of(
                        PlatformTraceCorrelationFilter.TRACE_HEADER,
                        PlatformTraceCorrelationFilter.REQUEST_HEADER
                )
        );
    }
}
