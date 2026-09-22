package com.balanceplatform.holdledger.domain;

import java.time.Duration;

/**
 * Central policy constants for the card hold lifecycle (ФТ-BALANCE-001, NFR-007).
 *
 * <p>{@link #HOLD_TTL} is the single source of truth for the hold time-to-live and must be
 * referenced by both the Temporal workflow timeout (FR-004) and the reconciliation threshold
 * validation (FR-005), rather than being duplicated in configuration.
 */
public final class HoldPolicy {

    /** Maximum lifetime of a {@code PENDING} hold before the workflow expires it (FR-004). */
    public static final Duration HOLD_TTL = Duration.ofDays(7);

    /** Additional safety margin applied on top of {@link #HOLD_TTL} for reconciliation (FR-005). */
    public static final Duration RECONCILIATION_SAFETY_MARGIN = Duration.ofHours(48);

    /** Default reconciliation age threshold: TTL + safety margin = 216 hours (FR-005). */
    public static final Duration RECONCILIATION_THRESHOLD = HOLD_TTL.plus(RECONCILIATION_SAFETY_MARGIN);

    /**
     * Minimum age threshold accepted by the reconciliation endpoint. Requests below this
     * value are rejected with {@code 400 THRESHOLD_TOO_LOW} (FR-005) so that reconciliation
     * can never race ahead of the workflow-driven TTL expiration.
     */
    public static final Duration MIN_RECONCILIATION_THRESHOLD = HOLD_TTL;

    private HoldPolicy() {
    }
}
