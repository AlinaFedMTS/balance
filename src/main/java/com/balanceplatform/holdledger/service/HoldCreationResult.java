package com.balanceplatform.holdledger.service;

import com.balanceplatform.holdledger.domain.Hold;

/**
 * Outcome of {@link HoldLedgerService#createHold}, distinguishing a brand-new hold from an
 * idempotent replay so the controller can pick {@code 201} vs {@code 200} (FR-001, FR-010).
 *
 * @param hold    resulting hold, either newly persisted or the pre-existing one
 * @param created {@code true} if a new hold was created by this call, {@code false} if it was
 *                an idempotent replay of an already-persisted hold
 */
public record HoldCreationResult(Hold hold, boolean created) {
}
