package com.example.platform.workflow.execution.infrastructure;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

/**
 * UWEV1-FV1-CRR2: canonical execution clock composition (DEFECT-1 repair).
 *
 * <p>Composition-root principle (CONSTRUCTOR_INJECTION_IS_THE_DEFAULT):
 * {@code WorkflowExecutionService} keeps its single injectable Clock
 * constructor; Spring provides exactly one Clock bean here. Timezone is fixed
 * UTC (no system-default timezone dependency).</p>
 */
@Configuration
public class WorkflowExecutionTimeConfiguration {

    @Bean
    public Clock workflowExecutionClock() {
        return Clock.systemUTC();
    }
}
