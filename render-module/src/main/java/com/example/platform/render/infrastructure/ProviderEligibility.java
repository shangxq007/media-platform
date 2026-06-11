package com.example.platform.render.infrastructure;

import java.util.List;

public class ProviderEligibility {

    public static boolean isEligible(ProviderMetadata metadata, RenderJob job) {
        if (metadata.isDeprecated()) {
            return false;
        }
        if (metadata.isHold() && !"experiment".equals(job.mode()) && !"manual".equals(job.mode())) {
            return false;
        }
        if (metadata.isSpike() && !"manual".equals(job.mode())) {
            return false;
        }
        if (!metadata.autoDispatch() && !"manual".equals(job.mode())) {
            boolean isGrayEligible = (metadata.isPoc() || metadata.isHold())
                    && ("experiment".equals(job.mode()) || "manual".equals(job.mode()));
            if (!isGrayEligible) {
                return false;
            }
        }
        for (String notFor : metadata.notFor()) {
            if (job.requiredCapabilities().contains(notFor)) {
                return false;
            }
        }
        for (String blocked : job.blockedProviders()) {
            if (metadata.name().equals(blocked)) {
                return false;
            }
        }
        for (String required : job.requiredCapabilities()) {
            if (!metadata.canHandleCapability(required)) {
                return false;
            }
        }
        return true;
    }

    public static int scoreProvider(ProviderMetadata metadata, RenderJob job) {
        int score = 0;
        if (metadata.isProduction()) {
            score += 0;
        } else if (metadata.isPoc()) {
            score += 100;
        } else if (metadata.isOptional()) {
            score += 200;
        } else if (metadata.isHold()) {
            score += 300;
        } else if (metadata.isSpike()) {
            score += 400;
        }
        if ("P0".equals(metadata.priority())) {
            score += 0;
        } else if ("P1".equals(metadata.priority())) {
            score += 10;
        } else if ("P2".equals(metadata.priority())) {
            score += 20;
        } else if ("P3".equals(metadata.priority())) {
            score += 30;
        }
        for (String preferred : job.preferredProviders()) {
            if (metadata.name().equals(preferred)) {
                score -= 50;
            }
        }
        return score;
    }
}
