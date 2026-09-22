package com.balanceplatform.holdledger.service;

import com.balanceplatform.holdledger.domain.Hold;
import java.util.List;

/**
 * Outcome of {@link HoldLedgerService#expireStalePendingHolds} (FR-005).
 *
 * @param expiredHolds holds expired by this invocation, possibly empty
 */
public record ExpireStaleResult(List<Hold> expiredHolds) {
}
