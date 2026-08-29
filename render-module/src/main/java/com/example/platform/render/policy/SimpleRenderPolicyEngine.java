package com.example.platform.render.policy;

import org.springframework.stereotype.Service;

@Service
public class SimpleRenderPolicyEngine implements RenderPolicyEngine {
    @Override
    public RenderPolicyDecision decide(String profile) {
        // Social profiles need later typed-plugin binding; policy does not
        // invent an executable backend identity for that unbound state.
        return profile.startsWith("social_")
                ? new RenderPolicyDecision(null, "NORMAL")
                : new RenderPolicyDecision("mlt", "HIGH");
    }
}
