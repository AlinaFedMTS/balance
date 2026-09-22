package com.balanceplatform.holdledger.domain;

/**
 * Type discriminator for events published to {@code hold.lifecycle.events} (ФТ-BALANCE-001 §6).
 */
public enum HoldLifecycleEventType {

    /** Emitted when a hold is created in {@link HoldStatus#PENDING} (FR-001). */
    HOLD_CREATED,

    /** Emitted when a hold is released as {@link HoldStatus#MATCHED_IN_ABS} (FR-002). */
    HOLD_MATCHED,

    /** Emitted when a hold is released as {@link HoldStatus#REVERSED} (FR-003). */
    HOLD_REVERSED,

    /** Emitted when a hold is released as {@link HoldStatus#EXPIRED} (FR-004, FR-005). */
    HOLD_EXPIRED
}
