package com.example.platform.render.app.dto;
import jakarta.validation.constraints.NotBlank;
public record CreateRenderJobRequest(@NotBlank String projectId,@NotBlank String timelineSnapshotId,@NotBlank String profile,
        String workspaceId,String allocationMode) {
    public CreateRenderJobRequest(String projectId,String timelineSnapshotId,String profile){this(projectId,timelineSnapshotId,profile,null,null);}
}
