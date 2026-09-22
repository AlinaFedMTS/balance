package com.balanceplatform.holdledger.service;

import com.balanceplatform.holdledger.domain.Hold;

/**
 * Outcome of {@link HoldLedgerService#release}, distinguishing an actual state transition from
 * an idempotent no-op (FR-010, rule 2).
 *
 * @param hold         resulting hold state
 * @param stateChanged {@code true} if the hold transitioned and a lifecycle event was
 *                      published, {@code false} if the requested status already matched the
 *                      persisted one and the call was a no-op
 */
public record HoldReleaseResult(Hold hold, boolean stateChanged) {
}
