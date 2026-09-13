package com.example.platform.outbox.api.event;

/** Synchronous transport metadata, independent of every domain's business payload.
 * The durable row ID survives claims/retries. This is not a business-event registry or store. */
public final class OutboxDeliveryContext {
    private static final ThreadLocal<Delivery> CURRENT=new ThreadLocal<>();
    private OutboxDeliveryContext() {}
    public record Delivery(String eventId,String tenantId) {
        public Delivery {
            if(eventId==null || eventId.isBlank() || tenantId==null || tenantId.isBlank())
                throw new IllegalArgumentException("durable delivery identity and tenant required");
        }
    }
    public static Delivery require() {
        var delivery=CURRENT.get();
        if(delivery==null)throw new IllegalStateException("durable Outbox delivery context required");
        return delivery;
    }
    public static void run(Delivery delivery,Runnable dispatch) {
        java.util.Objects.requireNonNull(delivery);java.util.Objects.requireNonNull(dispatch);
        Delivery previous=CURRENT.get();CURRENT.set(delivery);
        try{dispatch.run();}finally{if(previous==null)CURRENT.remove();else CURRENT.set(previous);}
    }
}
