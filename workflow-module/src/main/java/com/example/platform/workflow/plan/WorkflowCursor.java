package com.example.platform.workflow.plan;

import java.util.*;

/**
 * Explicit replay/Continue-As-New state. Domain progress is independent of the Temporal adapter.
 */
public final class WorkflowCursor {
    public Frame root;
    public Map<String, Boolean> releases = new TreeMap<>();
    public boolean cancelled;
    public long transitions;
    public String failureStep;

    public WorkflowCursor() {}

    public static final class Frame {
        public String planDigest;
        public String nodeId;
        public String stepId;
        public String item;
        public Map<String, String> values = new TreeMap<>();
        public List<Frame> children = new ArrayList<>();
        public int index;
        public boolean initialized;
        public boolean waitRegistered;
        public boolean done;
        public long deadline;

        public Frame() {}

        public Frame(
                String plan, String node, String step, Map<String, String> values, String item) {
            this.planDigest = plan;
            this.nodeId = node;
            this.stepId = WorkflowStepIdentity.at(node, step);
            this.values = new TreeMap<>(values);
            this.item = item;
        }
    }
}
