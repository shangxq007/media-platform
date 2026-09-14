package com.example.platform.identity.api.dto;
import jakarta.validation.constraints.NotBlank;
public record CreateProjectRequest(@NotBlank String name, String description, @NotBlank String workspaceId) {
    public CreateProjectRequest(String name,String description) { this(name,description,null); }
}
