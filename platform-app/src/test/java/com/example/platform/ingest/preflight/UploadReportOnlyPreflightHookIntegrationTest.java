package com.example.platform.ingest.preflight;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.example.platform.ingest.contract.*;
import com.example.platform.ingest.experimental.tika.TikaDetectorProvider;
import com.example.platform.ingest.experimental.tika.TikaExperimentalProperties;
import com.example.platform.ingest.preflight.ffprobe.FFprobeMediaMetadataProvider;
import com.example.platform.ingest.preflight.policy.ReportOnlyPreflightPolicyEvaluator;
import com.example.platform.ingest.preflight.persistence.writer.SafePreflightReportPersistenceWriter;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.test.util.ReflectionTestUtils;

class UploadReportOnlyPreflightHookIntegrationTest {

    @SuppressWarnings("unchecked")
    private static <T> ObjectProvider<T> mockProvider(T instance) {
        ObjectProvider<T> op = mock(ObjectProvider.class);
        when(op.getIfAvailable()).thenReturn(instance);
        return op;
    }

    @Test
    void testHookDisabledEvaluatorNotInvoked() {
        var hook = createHook(true, true, false);
        byte[] pngBytes = new byte[]{(byte)0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A};
        var result = hook.maybeEvaluate(pngBytes, "test.png", "image/png", null);
        assertTrue(result.isEmpty());
    }

    @Test
    void testHookEnabledEvaluatorInvoked() {
        var hook = createHook(true, true, true);
        byte[] pngBytes = new byte[]{(byte)0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A};
        var result = hook.maybeEvaluate(pngBytes, "test.png", "image/png", null);
        assertTrue(result.isPresent());
        assertNotEquals(UploadPreflightDecision.REJECT, result.get().decision());
    }

    @Test
    void testNoRejectionFromPolicyEvaluator() {
        var hook = createHook(true, true, true);
        byte[] pngBytes = new byte[]{(byte)0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A};
        var result = hook.maybeEvaluate(pngBytes, "test.txt", "text/plain", null);
        assertTrue(result.isPresent());
        assertNotEquals(UploadPreflightDecision.REJECT, result.get().decision());
    }

    @Test
    void testFailOpenOnError() {
        var hook = new UploadReportOnlyPreflightHook(
                mockProvider(null), mockProvider(null), mockProvider(null));
        ReflectionTestUtils.setField(hook, "integrationEnabled", true);
        ReflectionTestUtils.setField(hook, "failOpen", true);
        var result = hook.maybeEvaluate(new byte[0], "test.txt", "text/plain", null);
        assertTrue(result.isEmpty());
    }

    private UploadReportOnlyPreflightHook createHook(boolean tikaEnabled, boolean ffprobeEnabled, boolean hookEnabled) {
        TikaExperimentalProperties tikaProps = new TikaExperimentalProperties();
        tikaProps.setEnabled(tikaEnabled);
        TikaDetectorProvider tikaProvider = new TikaDetectorProvider(tikaProps);
        FFprobeMediaMetadataProvider ffprobeProvider = new FFprobeMediaMetadataProvider();
        IngestMetadataMerger merger = new IngestMetadataMerger(mockProvider(tikaProvider), mockProvider(ffprobeProvider));
        ReportOnlyPreflightPolicyEvaluator evaluator = new ReportOnlyPreflightPolicyEvaluator();

        var hook = new UploadReportOnlyPreflightHook(
                mockProvider(merger), mockProvider(evaluator), mockProvider(null));
        ReflectionTestUtils.setField(hook, "integrationEnabled", hookEnabled);
        ReflectionTestUtils.setField(hook, "failOpen", true);
        return hook;
    }
}
