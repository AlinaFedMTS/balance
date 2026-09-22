package com.balanceplatform.holdledger.exception;

/**
 * Thrown when {@code POST /internal/v1/holds/expire-stale} is invoked with
 * {@code olderThanHours} below {@code HoldPolicy.MIN_RECONCILIATION_THRESHOLD} (FR-005).
 */
public class ThresholdTooLowException extends RuntimeException {

    /** Machine-readable error code returned to callers as {@code 400}. */
    public static final String CODE = "THRESHOLD_TOO_LOW";

    /**
     * Creates the exception for a rejected reconciliation threshold.
     *
     * @param requestedHours threshold requested by the caller
     * @param minimumHours   minimum threshold accepted by Hold Ledger
     */
    public ThresholdTooLowException(int requestedHours, long minimumHours) {
        super("olderThanHours=" + requestedHours + " is below the minimum allowed threshold of "
                + minimumHours + " hours");
    }
}
