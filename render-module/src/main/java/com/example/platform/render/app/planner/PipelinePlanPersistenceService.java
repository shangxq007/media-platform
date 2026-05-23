package com.example.platform.render.app.planner;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import java.util.Optional;
import org.jooq.DSLContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import static org.jooq.impl.DSL.field;
import static org.jooq.impl.DSL.table;

@Service
public class PipelinePlanPersistenceService {

    private static final Logger log = LoggerFactory.getLogger(PipelinePlanPersistenceService.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final DSLContext dsl;

    public PipelinePlanPersistenceService(DSLContext dsl) {
        this.dsl = dsl;
    }

    public void savePlan(String jobId, PipelineExecutionPlan plan) {
        try {
            String json = MAPPER.writeValueAsString(plan);
            dsl.update(table("render_job"))
                    .set(field("pipeline_plan_json"), json)
                    .where(field("id").eq(jobId))
                    .execute();
        } catch (Exception e) {
            log.warn("Could not persist pipeline plan for job {}: {}", jobId, e.getMessage());
        }
    }

    public void saveExecutionState(String jobId, Map<String, Object> state) {
        try {
            String json = MAPPER.writeValueAsString(state);
            dsl.update(table("render_job"))
                    .set(field("pipeline_execution_json"), json)
                    .where(field("id").eq(jobId))
                    .execute();
        } catch (Exception e) {
            log.warn("Could not persist pipeline execution state for job {}: {}", jobId, e.getMessage());
        }
    }

    public Optional<Map<String, Object>> loadExecutionState(String jobId) {
        try {
            String json = dsl.select(field("pipeline_execution_json"))
                    .from(table("render_job"))
                    .where(field("id").eq(jobId))
                    .fetchOne(field("pipeline_execution_json"), String.class);
            if (json == null || json.isBlank()) {
                return Optional.empty();
            }
            return Optional.of(MAPPER.readValue(json, new com.fasterxml.jackson.core.type.TypeReference<>() {}));
        } catch (Exception e) {
            log.debug("No pipeline execution state for job {}: {}", jobId, e.getMessage());
            return Optional.empty();
        }
    }

    public Optional<PipelineExecutionPlan> loadPlan(String jobId) {
        try {
            String json = dsl.select(field("pipeline_plan_json"))
                    .from(table("render_job"))
                    .where(field("id").eq(jobId))
                    .fetchOne(field("pipeline_plan_json"), String.class);
            if (json == null || json.isBlank()) {
                return Optional.empty();
            }
            return Optional.of(MAPPER.readValue(json, PipelineExecutionPlan.class));
        } catch (Exception e) {
            log.debug("No pipeline plan for job {}: {}", jobId, e.getMessage());
            return Optional.empty();
        }
    }
}
