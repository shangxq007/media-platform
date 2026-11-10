package com.example.platform.workflow.temporal.mediatask;

/** Shared identity of a Render-owned durable workflow, independent of execution implementation. */
public final class MediaTaskWorkflowIdentity {
    private MediaTaskWorkflowIdentity() {}
    public static String forJob(String tenant,String job) {
        if(tenant==null||tenant.isBlank()||job==null||!job.matches("[A-Za-z0-9_-]+"))throw new IllegalArgumentException("scoped Render workflow identity required");
        return "render:"+tenant+":"+job;
    }
}
