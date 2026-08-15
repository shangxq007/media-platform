package com.example.platform.render.domain.revisioncommand;

/**
 * REVISION_COMMAND_MODEL_V1: typed command exception.
 */
public class RevisionCommandException extends RuntimeException {

    private final RevisionCommandErrorCode code;

    public RevisionCommandException(RevisionCommandErrorCode code, String message) {
        super(message);
        this.code = code;
    }

    public RevisionCommandErrorCode code() {
        return code;
    }
}
