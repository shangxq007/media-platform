package com.example.platform.storage.contract.replica;
import java.io.Serializable;
import java.util.*;
public final class StorageReplicaStateMachine implements Serializable {
    private static final long serialVersionUID = 1L;
    private static final Map<ReplicaState, Set<ReplicaState>> TRANSITIONS = new EnumMap<>(ReplicaState.class);
    static {
        TRANSITIONS.put(ReplicaState.PENDING, EnumSet.of(ReplicaState.UPLOADING, ReplicaState.DELETING));
        TRANSITIONS.put(ReplicaState.UPLOADING, EnumSet.of(ReplicaState.VERIFYING, ReplicaState.FAILED, ReplicaState.DELETING));
        TRANSITIONS.put(ReplicaState.VERIFYING, EnumSet.of(ReplicaState.AVAILABLE, ReplicaState.FAILED, ReplicaState.DELETING));
        TRANSITIONS.put(ReplicaState.AVAILABLE, EnumSet.of(ReplicaState.QUARANTINED, ReplicaState.DELETING));
        TRANSITIONS.put(ReplicaState.QUARANTINED, EnumSet.of(ReplicaState.DELETING, ReplicaState.DELETED));
        TRANSITIONS.put(ReplicaState.FAILED, EnumSet.of(ReplicaState.UPLOADING, ReplicaState.DELETING));
        TRANSITIONS.put(ReplicaState.DELETING, EnumSet.of(ReplicaState.DELETED, ReplicaState.FAILED));
        TRANSITIONS.put(ReplicaState.DELETED, EnumSet.noneOf(ReplicaState.class));
    }
    private StorageReplicaStateMachine() {}
    public static boolean canTransition(ReplicaState from, ReplicaState to) {
        Set<ReplicaState> allowed = TRANSITIONS.get(from);
        return allowed != null && allowed.contains(to);
    }
    public static String describeIllegalTransition(ReplicaState from, ReplicaState to) {
        return "Illegal replica state transition: " + from + " -> " + to;
    }
}
