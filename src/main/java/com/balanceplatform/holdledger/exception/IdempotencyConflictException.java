package com.balanceplatform.holdledger.exception;

/**
 * Thrown when {@code POST /internal/v1/holds} is retried with the same {@code operationId} but
 * different {@code accountId}/{@code amount}/{@code currency} (FR-010, rule 1).
 */
public class IdempotencyConflictException extends RuntimeException {

    /** Machine-readable error code returned to callers as {@code 409}. */
    public static final String CODE = "IDEMPOTENCY_CONFLICT";

    /**
     * Creates the exception for a conflicting hold creation request.
     *
     * @param operationId business key of the conflicting request
     */
    public IdempotencyConflictException(String operationId) {
        super("Hold creation request for operationId=" + operationId
                + " conflicts with an already persisted hold with different attributes");
    }
}
