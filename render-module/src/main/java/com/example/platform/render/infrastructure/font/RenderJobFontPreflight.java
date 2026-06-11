package com.example.platform.render.infrastructure.font;

import com.example.platform.render.infrastructure.RenderJob;
import java.util.List;
import java.util.Set;

public interface RenderJobFontPreflight {

    FontPreflightResult preflight(RenderJob job);

    Set<String> collectFontAssetIds(RenderJob job);
}
