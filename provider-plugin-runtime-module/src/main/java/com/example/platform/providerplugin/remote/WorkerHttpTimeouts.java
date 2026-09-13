package com.example.platform.providerplugin.remote;
import java.time.Duration;
import java.util.Objects;
/** HTTP execution policy: connection/header bounds plus idle and total output-read bounds. */
public record WorkerHttpTimeouts(Duration connect,Duration headers,Duration idleRead,Duration overall) {
    public WorkerHttpTimeouts {
        for(Duration value:new Duration[]{connect,headers,idleRead,overall}) {
            Objects.requireNonNull(value);if(value.isZero()||value.isNegative())throw new IllegalArgumentException("positive HTTP timeouts required");
            value.toNanos();
        }
        if(connect.compareTo(overall)>0 || headers.compareTo(overall)>0 || idleRead.compareTo(overall)>0)
            throw new IllegalArgumentException("phase timeout cannot exceed overall execution/read bound");
    }
    public static WorkerHttpTimeouts boundedBy(Duration overall) {
        Objects.requireNonNull(overall);
        return new WorkerHttpTimeouts(min(overall,Duration.ofSeconds(10)),overall,min(overall,Duration.ofSeconds(30)),overall);
    }
    private static Duration min(Duration a,Duration b){return a.compareTo(b)<=0?a:b;}
}
