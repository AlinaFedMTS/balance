package com.balanceplatform.holdledger.exception;

/**
 * Thrown when no hold exists for a given {@code operationId} (FR-011, and the "workflow not
 * found" branch of FR-003).
 */
public class HoldNotFoundException extends RuntimeException {

    /** Machine-readable error code returned to callers as {@code 404}. */
    public static final String CODE = "HOLD_NOT_FOUND";

    /**
     * Creates the exception for a missing hold.
     *
     * @param operationId business key that could not be found
     */
    public HoldNotFoundException(String operationId) {
        super("Hold not found for operationId=" + operationId);
    }
}
