package com.example.platform.render.app;

import com.example.platform.render.domain.*;
import com.example.platform.render.infrastructure.RenderJobRepository;
import com.example.platform.render.app.event.RenderLifecyclePublisher;
import com.example.platform.render.api.event.RenderJobStatusChangedEvent;
import java.time.Instant;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** One Render state-machine/history/typed-fact writer, shared by execution entry points. */
@Service
public class RenderStateTransitions {
    private final RenderJobRepository jobs;
    private final RenderJobStatusHistoryRepository history;
    private final RenderLifecyclePublisher events;
    public RenderStateTransitions(RenderJobRepository jobs,RenderJobStatusHistoryRepository history,RenderLifecyclePublisher events){this.jobs=jobs;this.history=history;this.events=events;}
    @Transactional
    public void transition(String jobId,String projectId,RenderJobStatus from,RenderJobStatus to,String code) {
        new RenderJobStateMachine().validateTransition(from,to);
        var job=jobs.requireJobRecord(jobId);var initiator=RenderJobRepository.initiatorFrom(job);
        if(!projectId.equals(job.get("project_id",String.class)))throw new IllegalArgumentException("Render project mismatch");
        if(jobs.compareAndSetStatus(jobId,projectId,initiator.tenantId(),from.name(),to.name())!=1)throw new IllegalStateException("stale Render transition");
        history.record(jobId,from.name(),to.name(),null,code);
        events.publishEvent(new RenderJobStatusChangedEvent(jobId,projectId,from,to,Instant.now(),initiator));
    }
}
