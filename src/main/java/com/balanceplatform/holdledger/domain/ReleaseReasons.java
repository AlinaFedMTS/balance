package com.balanceplatform.holdledger.domain;

/**
 * Well-known {@code reason} codes that Hold Ledger itself assigns when releasing a hold.
 *
 * <p>Reasons originating from the Temporal worker (e.g. a REVERSE reason forwarded from the
 * processing center, or an amount-mismatch note per FR-008) are free-form text supplied by
 * the caller and are therefore not modeled as constants here.
 */
public final class ReleaseReasons {

    /** Hold expired because its TTL elapsed inside the Temporal workflow (FR-004). */
    public static final String TTL_EXPIRED = "TTL_EXPIRED";

    /** Hold expired by the reconciliation safety-net job (FR-005). */
    public static final String STALE_RECONCILIATION = "STALE_RECONCILIATION";

    private ReleaseReasons() {
    }
}
