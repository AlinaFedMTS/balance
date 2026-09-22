package com.balanceplatform.holdledger.domain;

/**
 * Lifecycle status of a card hold, as defined in ФТ-BALANCE-001 §0.1.
 *
 * <p>{@link #PENDING} is the only non-terminal status. Once a hold reaches one of the
 * terminal statuses ({@link #MATCHED_IN_ABS}, {@link #REVERSED}, {@link #EXPIRED}) it is
 * immutable: the first terminal status to be persisted wins ("claim before effect", FR-010).
 */
public enum HoldStatus {

    /** Authorization received from the processing center, no matching posting yet. */
    PENDING,

    /** A successful posting was matched to this hold in the core banking system (АБС). */
    MATCHED_IN_ABS,

    /** The processing center sent a reverse for the original authorization. */
    REVERSED,

    /** The hold TTL elapsed (workflow timeout, FR-004) or it was expired by reconciliation (FR-005). */
    EXPIRED
}
