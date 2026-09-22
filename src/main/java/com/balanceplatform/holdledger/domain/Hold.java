package com.balanceplatform.holdledger.domain;

import java.math.BigDecimal;
import java.time.Instant;
import lombok.Builder;
import lombok.Getter;
import lombok.ToString;

/**
 * Domain aggregate for a card hold (ФТ-BALANCE-001 §6, {@code Hold} entity).
 *
 * <p>Hold Ledger is the single source of truth for the {@link HoldStatus} of a hold
 * (NFR-007). The aggregate is treated as immutable: any state transition is performed via
 * {@link #release(HoldStatus, String, String, Instant)}, which returns a new instance,
 * keeping "claim before effect" transitions explicit and easy to reason about (FR-010).
 */
@Getter
@Builder(toBuilder = true)
@ToString
public class Hold {

    /** Business key of the hold; identical to the processing center {@code operationId}. */
    private final String operationId;

    /** Account the hold is placed against. */
    private final String accountId;

    /** Held amount, always positive. */
    private final BigDecimal amount;

    /** ISO-4217 currency code of {@link #amount}. */
    private final String currency;

    /** Tokenized card identifier; never a PAN (NFR-006). */
    private final String cardId;

    /** Retrieval reference number used for matching (FR-007) when {@code relatedOperationId} is absent. */
    private final String rrn;

    /** Direction of the underlying authorization. */
    private final HoldType holdType;

    /** Current lifecycle status; {@link HoldStatus#PENDING} until a terminal status wins. */
    private final HoldStatus status;

    /** Reason for the terminal status, {@code null} while {@link #status} is {@code PENDING}. */
    private final String reason;

    /** ABS document identifier that matched this hold, set only on {@link HoldStatus#MATCHED_IN_ABS}. */
    private final String documentId;

    /** Timestamp of the original authorization from the processing center. */
    private final Instant authorizedAt;

    /** Timestamp at which the hold reached its terminal status, {@code null} while {@code PENDING}. */
    private final Instant resolvedAt;

    /**
     * Computes the point in time at which this hold expires if left {@code PENDING}
     * (ФТ-BALANCE-001 FR-011: {@code expiresAt = authorizedAt + HoldPolicy.HOLD_TTL}).
     *
     * @return the projected expiration instant
     */
    public Instant expiresAt() {
        return authorizedAt.plus(HoldPolicy.HOLD_TTL);
    }

    /**
     * Whether this hold has already reached a terminal status.
     *
     * @return {@code true} if {@link #status} is not {@link HoldStatus#PENDING}
     */
    public boolean isTerminal() {
        return status != HoldStatus.PENDING;
    }

    /**
     * Produces a new {@link Hold} transitioned to a terminal status.
     *
     * <p>Callers must only invoke this on a {@link HoldStatus#PENDING} hold; terminal-state
     * idempotency and conflict handling are the responsibility of the service layer (FR-010),
     * not of this method.
     *
     * @param targetStatus the terminal status to transition to
     * @param reason       human/machine-readable reason for the transition
     * @param documentId   ABS document id, may be {@code null} unless {@code targetStatus} is
     *                     {@link HoldStatus#MATCHED_IN_ABS}
     * @param resolvedAt   timestamp of the transition
     * @return a new, released {@link Hold} instance
     */
    public Hold release(HoldStatus targetStatus, String reason, String documentId, Instant resolvedAt) {
        return this.toBuilder()
                .status(targetStatus)
                .reason(reason)
                .documentId(documentId)
                .resolvedAt(resolvedAt)
                .build();
    }
}
