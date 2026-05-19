package com.example.platform.federation.nlq;

import com.example.platform.shared.web.ErrorCode;

public enum NlqErrorCode implements ErrorCode {
    NLQ_DISABLED("NLQ-400-001", "Natural language query is disabled", 400),
    NLQ_SQL_UNSAFE("NLQ-400-002", "Generated SQL failed safety validation", 400),
    NLQ_SQL_OPERATION_NOT_ALLOWED("NLQ-400-003", "SQL operation not allowed", 400),
    NLQ_SQL_SCOPE_MISSING("NLQ-400-004", "Query missing required scope conditions", 400),
    NLQ_SQL_LIMIT_REQUIRED("NLQ-400-005", "Query must include a LIMIT clause", 400),
    NLQ_SQL_TOO_COMPLEX("NLQ-400-006", "Query exceeds complexity threshold", 400),
    NLQ_QUERY_TIMEOUT("NLQ-408-001", "Query execution timed out", 408),
    NLQ_QUERY_TOO_EXPENSIVE("NLQ-402-001", "Query cost exceeds allowed threshold", 402),
    NLQ_QUERY_ACCESS_DENIED("NLQ-403-001", "Access denied for requested datasets", 403),
    NLQ_REPORT_NOT_FOUND("NLQ-404-001", "Report not found", 404),
    NLQ_AI_PROVIDER_UNAVAILABLE("NLQ-503-001", "AI provider unavailable, using fallback", 503),
    NLQ_PREVIEW_EXPIRED("NLQ-410-001", "Query preview has expired", 410),
    NLQ_EXECUTION_REQUIRES_CONFIRMATION("NLQ-425-001", "Execution requires explicit confirmation", 425);

    private final String code;
    private final String title;
    private final int status;

    NlqErrorCode(String code, String title, int status) {
        this.code = code;
        this.title = title;
        this.status = status;
    }

    @Override
    public String code() { return code; }
    @Override
    public String title() { return title; }
    @Override
    public int status() { return status; }
}
