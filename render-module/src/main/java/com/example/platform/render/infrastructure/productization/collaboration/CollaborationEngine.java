package com.example.platform.render.infrastructure.productization.collaboration;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Collaboration Engine for real-time multi-user timeline editing.
 * 
 * <p>Uses Operational Transform (OT) for conflict resolution.
 * Supports live cursor tracking and change diff streaming.
 */
public class CollaborationEngine {

    /**
     * Create a new collaboration session.
     */
    public CollaborationSession createSession(String sessionId, String projectId, String hostUserId) {
        return new CollaborationSession(
                sessionId, projectId, hostUserId,
                List.of(), List.of(), Map.of(),
                SessionStatus.ACTIVE, Instant.now(), Instant.now()
        );
    }

    /**
     * Join a collaboration session.
     */
    public CollaborationSession joinSession(CollaborationSession session, String userId) {
        List<SessionParticipant> newParticipants = new java.util.ArrayList<>(session.participants());
        newParticipants.add(new SessionParticipant(userId, Instant.now(), null, null));
        return new CollaborationSession(
                session.sessionId(), session.projectId(), session.hostUserId(),
                List.copyOf(newParticipants), session.operations(), session.cursors(),
                SessionStatus.ACTIVE, session.startedAt(), Instant.now()
        );
    }

    /**
     * Apply an operation to the session.
     */
    public CollaborationSession applyOperation(CollaborationSession session, Operation operation) {
        List<Operation> newOps = new java.util.ArrayList<>(session.operations());
        newOps.add(operation);
        return new CollaborationSession(
                session.sessionId(), session.projectId(), session.hostUserId(),
                session.participants(), List.copyOf(newOps), session.cursors(),
                SessionStatus.ACTIVE, session.startedAt(), Instant.now()
        );
    }

    /**
     * Update cursor position for a user.
     */
    public CollaborationSession updateCursor(CollaborationSession session, String userId, CursorPosition position) {
        Map<String, CursorPosition> newCursors = new java.util.HashMap<>(session.cursors());
        newCursors.put(userId, position);
        return new CollaborationSession(
                session.sessionId(), session.projectId(), session.hostUserId(),
                session.participants(), session.operations(), Map.copyOf(newCursors),
                SessionStatus.ACTIVE, session.startedAt(), Instant.now()
        );
    }

    /**
     * Transform an operation against another (OT core).
     */
    public Operation transformOperation(Operation op1, Operation op2) {
        // Simplified OT - in production would be full operational transform
        if (op1.type() == OperationType.INSERT && op2.type() == OperationType.INSERT) {
            if (op1.position() <= op2.position()) {
                return op1;
            }
            return new Operation(
                    op1.operationId(), op1.userId(),
                    op1.type(), op1.position() + op2.length(),
                    op1.content(), op1.length(), Instant.now()
            );
        }
        return op1;
    }

    /**
     * Merge conflicting operations.
     */
    public List<Operation> mergeOperations(List<Operation> local, List<Operation> remote) {
        // Simplified merge - in production would use full OT algorithm
        List<Operation> merged = new java.util.ArrayList<>(local);
        merged.addAll(remote);
        return merged;
    }

    // ---------------------------------------------------------------------------
    // Inner Types
    // ---------------------------------------------------------------------------

    public record CollaborationSession(
            String sessionId,
            String projectId,
            String hostUserId,
            List<SessionParticipant> participants,
            List<Operation> operations,
            Map<String, CursorPosition> cursors,
            SessionStatus status,
            Instant startedAt,
            Instant lastActivityAt
    ) {}

    public record SessionParticipant(
            String userId,
            Instant joinedAt,
            Instant lastSeenAt,
            String cursorColor
    ) {}

    public record Operation(
            String operationId,
            String userId,
            OperationType type,
            int position,
            String content,
            int length,
            Instant timestamp
    ) {}

    public enum OperationType {
        INSERT,
        DELETE,
        MOVE,
        TRANSFORM,
        ATTRIBUTE_CHANGE
    }

    public record CursorPosition(
            int trackIndex,
            int clipIndex,
            double timePosition,
            String selectedElementId
    ) {}

    public enum SessionStatus {
        ACTIVE,
        PAUSED,
        ENDED
    }
}
