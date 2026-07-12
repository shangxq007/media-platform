package com.example.platform.app;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationEnvironmentPreparedEvent;
import org.springframework.boot.context.event.ApplicationPreparedEvent;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.boot.context.event.ApplicationStartedEvent;
import org.springframework.boot.context.event.SpringApplicationEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.context.event.ContextClosedEvent;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.lang.management.ManagementFactory;
import java.lang.management.ThreadInfo;
import java.lang.management.ThreadMXBean;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;

/**
 * Diagnostic listener that logs startup lifecycle events with timestamps
 * to identify where startup hangs occur, especially when S3/R2 storage is enabled.
 *
 * <p>This listener logs:
 * <ul>
 *   <li>Spring application lifecycle events with timestamps</li>
 *   <li>Thread dump on demand (when startup takes too long)</li>
 *   <li>Elapsed time between events</li>
 * </ul>
 *
 * <p>To use: add {@code logging.level.com.example.platform.app.StartupDiagnosticsListener=INFO}
 * to application properties (enabled by default at INFO level).</p>
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class StartupDiagnosticsListener implements ApplicationListener<SpringApplicationEvent> {

    private static final Logger log = LoggerFactory.getLogger(StartupDiagnosticsListener.class);

    private Instant lastEventTime = Instant.now();
    private Instant startupBegin = Instant.now();
    private boolean readyEventFired = false;

    @Override
    public void onApplicationEvent(SpringApplicationEvent event) {
        Instant now = Instant.now();
        Duration sinceLast = Duration.between(lastEventTime, now);
        Duration sinceStart = Duration.between(startupBegin, now);

        if (event instanceof ApplicationEnvironmentPreparedEvent) {
            log.info("[STARTUP-DIAG] ApplicationEnvironmentPreparedEvent (elapsed={}ms, sinceLast={}ms)",
                    sinceStart.toMillis(), sinceLast.toMillis());
        } else if (event instanceof ApplicationPreparedEvent) {
            log.info("[STARTUP-DIAG] ApplicationPreparedEvent (elapsed={}ms, sinceLast={}ms)",
                    sinceStart.toMillis(), sinceLast.toMillis());
        } else if (event instanceof ApplicationStartedEvent) {
            log.info("[STARTUP-DIAG] ApplicationStartedEvent (elapsed={}ms, sinceLast={}ms)",
                    sinceStart.toMillis(), sinceLast.toMillis());
        } else if (event instanceof ApplicationReadyEvent) {
            readyEventFired = true;
            log.info("[STARTUP-DIAG] ApplicationReadyEvent (elapsed={}ms, sinceLast={}ms)",
                    sinceStart.toMillis(), sinceLast.toMillis());
        }

        lastEventTime = now;
    }

    /**
     * Log a thread dump to help diagnose startup hangs.
     * Call this method from a debugger or via JMX when the app appears hung.
     */
    public static void logThreadDump() {
        ThreadMXBean threadMXBean = ManagementFactory.getThreadMXBean();
        ThreadInfo[] threadInfos = threadMXBean.dumpAllThreads(true, true);
        StringBuilder sb = new StringBuilder();
        sb.append("\n=== THREAD DUMP (Startup Diagnostics) ===\n");
        for (ThreadInfo info : threadInfos) {
            sb.append(info.toString());
        }
        sb.append("=== END THREAD DUMP ===\n");
        log.warn(sb.toString());
    }

    /**
     * Log blocked threads that might indicate startup hangs.
     */
    public static void logBlockedThreads() {
        ThreadMXBean threadMXBean = ManagementFactory.getThreadMXBean();
        long[] threadIds = threadMXBean.findDeadlockedThreads();
        if (threadIds != null && threadIds.length > 0) {
            ThreadInfo[] infos = threadMXBean.getThreadInfo(threadIds, true, true);
            StringBuilder sb = new StringBuilder();
            sb.append("\n=== DEADLOCKED THREADS ===\n");
            for (ThreadInfo info : infos) {
                sb.append(info.toString());
            }
            sb.append("=== END DEADLOCKED THREADS ===\n");
            log.error(sb.toString());
        } else {
            log.info("[STARTUP-DIAG] No deadlocked threads detected");
        }
    }
}
