package com.example.platform.render.app.planner;

/** Task types in a pipeline execution DAG. */
public enum PipelineTaskType {
    EXTERNAL_RENDER,
    SEGMENT_RENDER,
    MLT_MULTITRACK,
    EFFECTS,
    SUBTITLES,
    SKIA_OVERLAY,
    FINAL_COMPOSE,
    TRANSCODE,
    ENCODE,
    PACKAGING,
    QA
}
