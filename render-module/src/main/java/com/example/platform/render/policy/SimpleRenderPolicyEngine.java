package com.example.platform.render.policy;

import org.springframework.stereotype.Component;

@Component
public class SimpleRenderPolicyEngine implements RenderPolicyEngine {
    @Override
    public RenderPolicyDecision decide(String profile) {
        return profile.startsWith("social_")
                ? new RenderPolicyDecision("ffmpeg", "NORMAL")
                : new RenderPolicyDecision("mlt", "HIGH");
    }
}