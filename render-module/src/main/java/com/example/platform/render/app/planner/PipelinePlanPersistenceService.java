package com.example.platform.render.app.planner;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
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
            log.debug("Saved pipeline plan for job {}", jobId);
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

    public void updateWaveState(String jobId, int waveIndex, String waveStatus,
                                 List<Map<String, String>> taskResults) {
        try {
            Map<String, Object> state = loadExecutionState(jobId).orElseGet(LinkedHashMap::new);

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> waves = (List<Map<String, Object>>) state.computeIfAbsent(
                    "waves", k -> new ArrayList<Map<String, Object>>());

            // Ensure wave slot exists
            while (waves.size() <= waveIndex) {
                waves.add(new LinkedHashMap<>());
            }

            Map<String, Object> waveState = waves.get(waveIndex);
            waveState.put("index", waveIndex);
            waveState.put("status", waveStatus);
            waveState.put("updatedAt", Instant.now().toString());
            if (taskResults != null) {
                waveState.put("taskResults", taskResults);
            }

            state.put("currentWave", waveIndex);
            state.put("status", deriveOverallStatus(waves));
            state.put("updatedAt", Instant.now().toString());

            saveExecutionState(jobId, state);
            log.debug("Job {} wave {} → {}", jobId, waveIndex, waveStatus);
        } catch (Exception e) {
            log.warn("Could not update wave state for job {}: {}", jobId, e.getMessage());
        }
    }

    public void markPlanCompleted(String jobId) {
        try {
            Map<String, Object> state = loadExecutionState(jobId).orElseGet(LinkedHashMap::new);
            state.put("status", "COMPLETED");
            state.put("completedAt", Instant.now().toString());
            state.put("updatedAt", Instant.now().toString());
            saveExecutionState(jobId, state);
        } catch (Exception e) {
            log.warn("Could not mark plan completed for job {}: {}", jobId, e.getMessage());
        }
    }

    public void markPlanFailed(String jobId, String failedTask, String error) {
        try {
            Map<String, Object> state = loadExecutionState(jobId).orElseGet(LinkedHashMap::new);
            state.put("status", "FAILED");
            state.put("failedTask", failedTask);
            state.put("error", error != null ? error : "");
            state.put("failedAt", Instant.now().toString());
            state.put("updatedAt", Instant.now().toString());
            saveExecutionState(jobId, state);
        } catch (Exception e) {
            log.warn("Could not mark plan failed for job {}: {}", jobId, e.getMessage());
        }
    }

    public Optional<String> getLastCompletedWave(String jobId) {
        try {
            Map<String, Object> state = loadExecutionState(jobId).orElse(null);
            if (state == null) return Optional.empty();
            Object currentWave = state.get("currentWave");
            if (currentWave instanceof Number n) {
                return Optional.of(String.valueOf(n.intValue()));
            }
            return Optional.empty();
        } catch (Exception e) {
            return Optional.empty();
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

    private String deriveOverallStatus(List<Map<String, Object>> waves) {
        boolean anyFailed = waves.stream()
                .anyMatch(w -> "FAILED".equals(w.get("status")));
        if (anyFailed) return "FAILED";

        boolean allCompleted = waves.stream()
                .allMatch(w -> "COMPLETED".equals(w.get("status")));
        if (allCompleted && !waves.isEmpty()) return "COMPLETED";

        return "EXECUTING";
    }
}
