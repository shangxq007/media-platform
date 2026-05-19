package com.example.platform.federation.nlq.app;

import com.example.platform.shared.audit.AuditPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class NlqAuditTest {

    private AuditPort auditPort;
    private QueryAuditService auditService;

    @BeforeEach
    void setUp() {
        auditPort = mock(AuditPort.class);
        auditService = new QueryAuditService(auditPort);
    }

    @Test
    void auditPreviewRecordsCorrectAction() {
        auditService.auditPreview("user-1", "tenant-1",
            "Show render jobs", List.of("render_jobs_report_view"), "LOW", "ALLOWED");

        verify(auditPort).record(
            eq("USER"), eq("NLQ_PREVIEW"), eq("nlq"),
            eq("nlq_query"), isNull(), any(Map.class));
    }

    @Test
    void auditExecuteRecordsCorrectAction() {
        auditService.auditExecute("user-1", "tenant-1", "qry-abc",
            "hash123", List.of("render_jobs_report_view"), 50, 120, "LOW", "SUCCESS");

        verify(auditPort).record(
            eq("USER"), eq("NLQ_EXECUTE"), eq("nlq"),
            eq("nlq_query"), eq("qry-abc"), any(Map.class));
    }

    @Test
    void auditExplainRecordsCorrectAction() {
        auditService.auditExplain("user-1", "tenant-1",
            "Show render jobs", "This query counts render jobs...");

        verify(auditPort).record(
            eq("USER"), eq("NLQ_EXPLAIN"), eq("nlq"),
            eq("nlq_query"), isNull(), any(Map.class));
    }

    @Test
    void auditAccessDeniedRecordsCorrectAction() {
        auditService.auditAccessDenied("user-1", "tenant-1",
            "Show all data", "SQL_UNSAFE");

        verify(auditPort).record(
            eq("USER"), eq("NLQ_ACCESS_DENIED"), eq("nlq"),
            eq("nlq_query"), isNull(), any(Map.class));
    }

    @Test
    void auditReportCreatedRecordsCorrectAction() {
        auditService.auditReportCreated("user-1", "tenant-1", "rpt-1", "My Report");

        verify(auditPort).record(
            eq("USER"), eq("NLQ_REPORT_CREATE"), eq("nlq"),
            eq("nlq_report"), eq("rpt-1"), any(Map.class));
    }

    @Test
    void auditReportExecutedRecordsCorrectAction() {
        auditService.auditReportExecuted("user-1", "tenant-1", "rpt-1", 250);

        verify(auditPort).record(
            eq("USER"), eq("NLQ_REPORT_EXECUTE"), eq("nlq"),
            eq("nlq_report"), eq("rpt-1"), any(Map.class));
    }

    @Test
    void auditReportArchivedRecordsCorrectAction() {
        auditService.auditReportArchived("user-1", "tenant-1", "rpt-1");

        verify(auditPort).record(
            eq("USER"), eq("NLQ_REPORT_ARCHIVE"), eq("nlq"),
            eq("nlq_report"), eq("rpt-1"), any(Map.class));
    }

    @Test
    void auditDatasetListedRecordsCorrectAction() {
        auditService.auditDatasetListed("user-1", "tenant-1", 5);

        verify(auditPort).record(
            eq("USER"), eq("NLQ_DATASET_LIST"), eq("nlq"),
            eq("nlq_query"), isNull(), any(Map.class));
    }

    @Test
    void auditChartSuggestionsRecordsCorrectAction() {
        auditService.auditChartSuggestions("user-1", "tenant-1", 3);

        verify(auditPort).record(
            eq("USER"), eq("NLQ_CHART_SUGGEST"), eq("nlq"),
            eq("nlq_query"), isNull(), any(Map.class));
    }

    @Test
    void auditPreviewPayloadContainsQuestionLength() {
        auditService.auditPreview("user-1", "tenant-1",
            "Show render jobs", List.of(), "LOW", "ALLOWED");

        verify(auditPort).record(any(), any(), any(), any(), any(),
            argThat((Map<String, Object> payload) ->
                payload.containsKey("questionLength") &&
                payload.get("questionLength") instanceof Integer &&
                (Integer) payload.get("questionLength") == "Show render jobs".length()));
    }

    @Test
    void auditExecutePayloadContainsRowCount() {
        auditService.auditExecute("user-1", "tenant-1", "qry-1",
            "hash", List.of(), 42, 100, "LOW", "SUCCESS");

        verify(auditPort).record(any(), any(), any(), any(), any(),
            argThat((Map<String, Object> payload) ->
                payload.containsKey("rowCount") &&
                payload.get("rowCount").equals(42)));
    }
}
