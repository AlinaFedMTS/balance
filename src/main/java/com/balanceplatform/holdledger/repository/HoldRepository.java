package com.balanceplatform.holdledger.repository;

import com.balanceplatform.holdledger.domain.Hold;
import com.balanceplatform.holdledger.domain.HoldStatus;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Persistence port for {@link Hold} aggregates.
 *
 * <p>[ASSUMPTION] ФТ-BALANCE-001 describes the Hold Ledger HTTP contract (CONTRACT-hold-ledger-v1)
 * but not its storage technology. This interface is the port the service layer depends on;
 * a concrete adapter (e.g. JPA/JDBC-backed, with {@code FOR UPDATE SKIP LOCKED} semantics for
 * the outbox per FR-006) is outside the scope of this task and must be provided separately.
 */
public interface HoldRepository {

    /**
     * Finds a hold by its business key.
     *
     * @param operationId hold business key
     * @return the hold, if present
     */
    Optional<Hold> findByOperationId(String operationId);

    /**
     * Finds holds in {@link HoldStatus#PENDING} matching the given RRN and account, used for
     * identifying a hold when {@code relatedOperationId} is absent (FR-007).
     *
     * @param rrn       retrieval reference number
     * @param accountId account identifier
     * @return zero, one (unambiguous match) or more (ambiguous match) pending holds
     */
    List<Hold> findPendingByRrnAndAccountId(String rrn, String accountId);

    /**
     * Searches holds by optional criteria (FR-011).
     *
     * @param rrn       optional RRN filter
     * @param accountId optional account filter
     * @param status    optional status filter
     * @return matching holds
     */
    List<Hold> search(String rrn, String accountId, HoldStatus status);

    /**
     * Persists a new hold or the new state of an existing one.
     *
     * @param hold hold to persist
     * @return the persisted hold
     */
    Hold save(Hold hold);

    /**
     * Finds a batch of {@link HoldStatus#PENDING} holds authorized before the given instant,
     * used by the reconciliation safety net (FR-005).
     *
     * @param threshold cutoff instant; only holds authorized strictly before it are returned
     * @param limit     maximum batch size
     * @return a batch of stale pending holds, possibly empty
     */
    List<Hold> findPendingAuthorizedBefore(Instant threshold, int limit);
}
