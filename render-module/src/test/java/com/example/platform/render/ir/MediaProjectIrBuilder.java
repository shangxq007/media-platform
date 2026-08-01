package com.example.platform.render.ir;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Builder for constructing {@link MediaProjectIr} instances in tests.
 * Not part of the production API — used only by tests.
 */
public class MediaProjectIrBuilder {
    private String schemaVersion;
    private Project project;
    private List<AssetVersionRef> assets = List.of();
    private Timeline timeline;
    private List<OutputSpec> outputs = List.of();
    private List<ArtifactDeclaration> artifacts = List.of();
    private Map<String, Object> extensions;

    public MediaProjectIrBuilder schemaVersion(String v) { this.schemaVersion = v; return this; }
    public MediaProjectIrBuilder project(Project p) { this.project = p; return this; }
    public MediaProjectIrBuilder assets(List<AssetVersionRef> a) { this.assets = new ArrayList<>(a); return this; }
    public MediaProjectIrBuilder addAsset(AssetVersionRef a) {
        if (this.assets.isEmpty() || this.assets.getClass() == List.of().getClass())
            this.assets = new ArrayList<>(this.assets);
        this.assets.add(a); return this;
    }
    public MediaProjectIrBuilder timeline(Timeline t) { this.timeline = t; return this; }
    public MediaProjectIrBuilder outputs(List<OutputSpec> o) { this.outputs = new ArrayList<>(o); return this; }
    public MediaProjectIrBuilder artifacts(List<ArtifactDeclaration> a) { this.artifacts = new ArrayList<>(a); return this; }
    public MediaProjectIrBuilder addArtifact(ArtifactDeclaration a) {
        if (this.artifacts.isEmpty() || this.artifacts.getClass() == List.of().getClass())
            this.artifacts = new ArrayList<>(this.artifacts);
        this.artifacts.add(a); return this;
    }
    public MediaProjectIrBuilder extensions(Map<String, Object> e) { this.extensions = e; return this; }

    public MediaProjectIr build() {
        return new MediaProjectIr(
            schemaVersion, project,
            assets.isEmpty() ? List.of() : List.copyOf(assets),
            timeline,
            outputs.isEmpty() ? List.of() : List.copyOf(outputs),
            artifacts.isEmpty() ? List.of() : List.copyOf(artifacts),
            extensions != null ? new LinkedHashMap<>(extensions) : null
        );
    }
}
