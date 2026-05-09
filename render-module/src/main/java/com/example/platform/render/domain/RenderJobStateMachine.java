package com.example.platform.render.domain;

import com.example.platform.shared.web.CommonErrorCode;
import com.example.platform.shared.web.PlatformException;
import java.util.Collections;
import java.util.Map;
import java.util.Set;

public class RenderJobStateMachine {

    static final Map<RenderJobStatus, Set<RenderJobStatus>> VALID_TRANSITIONS = Map.of(
            RenderJobStatus.QUEUED, Set.of(
                    RenderJobStatus.AI_PROCESSING, RenderJobStatus.CANCELLED, RenderJobStatus.REJECTED),
            RenderJobStatus.AI_PROCESSING, Set.of(
                    RenderJobStatus.RENDERING, RenderJobStatus.FAILED, RenderJobStatus.CANCELLED),
            RenderJobStatus.RENDERING, Set.of(
                    RenderJobStatus.COMPLETED, RenderJobStatus.FAILED, RenderJobStatus.CANCELLED),
            RenderJobStatus.COMPLETED, Collections.emptySet(),
            RenderJobStatus.FAILED, Set.of(RenderJobStatus.QUEUED),
            RenderJobStatus.CANCELLED, Collections.emptySet(),
            RenderJobStatus.REJECTED, Collections.emptySet()
    );

    public boolean canTransition(RenderJobStatus from, RenderJobStatus to) {
        if (from == to) {
            return true;
        }
        Set<RenderJobStatus> allowed = VALID_TRANSITIONS.get(from);
        return allowed != null && allowed.contains(to);
    }

    public void validateTransition(RenderJobStatus from, RenderJobStatus to) {
        if (!canTransition(from, to)) {
            throw new PlatformException(
                    CommonErrorCode.CONFLICT,
                    String.format("Invalid state transition from %s to %s", from, to)
            );
        }
    }
}
