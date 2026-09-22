package com.balanceplatform.holdledger.exception;

import com.balanceplatform.holdledger.domain.HoldStatus;

/**
 * Thrown when a release request targets a terminal status different from the one already
 * persisted for the hold (FR-010, rule 2). The caller (Temporal worker) treats this as a
 * no-op, not a failure.
 */
public class ConflictTerminalException extends RuntimeException {

    /** Machine-readable error code returned to callers as {@code 409}. */
    public static final String CODE = "CONFLICT_TERMINAL";

    /**
     * Creates the exception for a conflicting release request.
     *
     * @param operationId    business key of the hold
     * @param currentStatus  terminal status already persisted
     * @param requestedStatus terminal status requested by the caller
     */
    public ConflictTerminalException(String operationId, HoldStatus currentStatus, HoldStatus requestedStatus) {
        super("Hold " + operationId + " is already " + currentStatus
                + ", cannot release as " + requestedStatus);
    }
}
