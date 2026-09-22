package com.balanceplatform.holdledger.service;

import com.balanceplatform.holdledger.domain.Hold;
import com.balanceplatform.holdledger.domain.HoldLifecycleEvent;
import com.balanceplatform.holdledger.domain.HoldLifecycleEventType;
import com.balanceplatform.holdledger.domain.HoldPolicy;
import com.balanceplatform.holdledger.domain.HoldStatus;
import com.balanceplatform.holdledger.domain.ReleaseReasons;
import com.balanceplatform.holdledger.dto.CreateHoldRequest;
import com.balanceplatform.holdledger.dto.ReleaseHoldRequest;
import com.balanceplatform.holdledger.exception.ConflictTerminalException;
import com.balanceplatform.holdledger.exception.HoldNotFoundException;
import com.balanceplatform.holdledger.exception.IdempotencyConflictException;
import com.balanceplatform.holdledger.exception.ThresholdTooLowException;
import com.balanceplatform.holdledger.mapper.HoldMapper;
import com.balanceplatform.holdledger.outbox.HoldLifecycleEventPublisher;
import com.balanceplatform.holdledger.repository.HoldRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Business logic for the card hold lifecycle (ФТ-BALANCE-001).
 *
 * <p>Hold Ledger is the single source of truth for a hold's {@link HoldStatus} (NFR-007). This
 * service implements the state transitions described by FR-001 (creation), FR-002/FR-003
 * (release to a terminal status), FR-004/FR-005 (TTL and reconciliation expiration), FR-007
 * (search for identification), FR-010 (idempotency and terminal-state immutability) and FR-011
 * (read access), and publishes every transition through {@link HoldLifecycleEventPublisher}
 * (FR-006).
 *
 * <p>The class is intentionally not {@code final}: Spring applies a CGLIB subclass proxy for
 * {@link Transactional} methods, which requires the class to be extensible.
 */
@Service
public class HoldLedgerService {

    private final HoldRepository holdRepository;
    private final HoldLifecycleEventPublisher eventPublisher;
    private final Clock clock;
    private final int reconciliationBatchSize;

    /**
     * Creates the service.
     *
     * @param holdRepository          persistence port for {@link Hold} aggregates
     * @param eventPublisher          transactional-outbox publisher for lifecycle events
     * @param clock                   clock used to timestamp transitions, injected for testability
     * @param reconciliationBatchSize maximum number of holds expired per {@link #expireStalePendingHolds} call
     */
    public HoldLedgerService(
            HoldRepository holdRepository,
            HoldLifecycleEventPublisher eventPublisher,
            Clock clock,
            @Value("${app.reconciliation.batch-size:500}") int reconciliationBatchSize) {
        this.holdRepository = holdRepository;
        this.eventPublisher = eventPublisher;
        this.clock = clock;
        this.reconciliationBatchSize = reconciliationBatchSize;
    }

    /**
     * Creates a new hold, or returns the existing one if this is an idempotent replay (FR-001, FR-010).
     *
     * @param request validated creation request
     * @return the resulting hold and whether it was newly created
     * @throws IdempotencyConflictException if a hold with the same {@code operationId} already
     *                                       exists with different {@code accountId}/{@code amount}/{@code currency}
     */
    @Transactional
    public HoldCreationResult createHold(CreateHoldRequest request) {
        Optional<Hold> existing = holdRepository.findByOperationId(request.operationId());
        if (existing.isPresent()) {
            Hold hold = existing.get();
            if (isSameCreateRequest(hold, request)) {
                return new HoldCreationResult(hold, false);
            }
            throw new IdempotencyConflictException(request.operationId());
        }

        Hold saved = holdRepository.save(HoldMapper.toNewHold(request));
        eventPublisher.publish(HoldLifecycleEvent.of(HoldLifecycleEventType.HOLD_CREATED, saved, null));
        return new HoldCreationResult(saved, true);
    }

    /**
     * Releases a hold into a terminal status (FR-002, FR-003, FR-004, FR-005), applying
     * "claim before effect" idempotency (FR-010, rule 2): a repeat of the same terminal status
     * is a no-op, and a different terminal status than the one already persisted is rejected.
     *
     * @param operationId business key of the hold to release
     * @param request     validated release request
     * @return the resulting hold and whether a state transition actually occurred
     * @throws HoldNotFoundException       if no hold exists for {@code operationId}
     * @throws ConflictTerminalException   if the hold is already terminal with a different status
     * @throws IllegalArgumentException    if {@code request.status()} is {@link HoldStatus#PENDING}
     */
    @Transactional
    public HoldReleaseResult release(String operationId, ReleaseHoldRequest request) {
        HoldStatus targetStatus = request.status();
        if (targetStatus == HoldStatus.PENDING) {
            throw new IllegalArgumentException("Release target status must be terminal, got PENDING");
        }

        Hold hold = holdRepository.findByOperationId(operationId)
                .orElseThrow(() -> new HoldNotFoundException(operationId));

        if (hold.getStatus() == HoldStatus.PENDING) {
            Hold released = hold.release(targetStatus, request.reason(), request.documentId(), Instant.now(clock));
            Hold saved = holdRepository.save(released);
            eventPublisher.publish(HoldLifecycleEvent.of(eventTypeFor(targetStatus), saved, request.documentId()));
            return new HoldReleaseResult(saved, true);
        }

        if (hold.getStatus() == targetStatus) {
            return new HoldReleaseResult(hold, false);
        }

        throw new ConflictTerminalException(operationId, hold.getStatus(), targetStatus);
    }

    /**
     * Retrieves a hold by its business key (FR-011).
     *
     * @param operationId business key of the hold
     * @return the hold
     * @throws HoldNotFoundException if no hold exists for {@code operationId}
     */
    @Transactional(readOnly = true)
    public Hold getHold(String operationId) {
        return holdRepository.findByOperationId(operationId)
                .orElseThrow(() -> new HoldNotFoundException(operationId));
    }

    /**
     * Searches holds by optional criteria, used by the Temporal worker for identification when
     * {@code relatedOperationId} is absent (FR-007) and for general read access (FR-011).
     *
     * @param rrn       optional RRN filter
     * @param accountId optional account filter
     * @param status    optional status filter
     * @return matching holds
     */
    @Transactional(readOnly = true)
    public List<Hold> searchHolds(String rrn, String accountId, HoldStatus status) {
        return holdRepository.search(rrn, accountId, status);
    }

    /**
     * Expires {@code PENDING} holds authorized more than {@code olderThanHours} ago (FR-005),
     * as a safety net for holds whose Temporal workflow was lost.
     *
     * @param olderThanHours age threshold in hours; must be at least
     *                       {@code HoldPolicy.MIN_RECONCILIATION_THRESHOLD}
     * @return the holds expired by this invocation
     * @throws ThresholdTooLowException if {@code olderThanHours} is below the minimum threshold
     */
    @Transactional
    public ExpireStaleResult expireStalePendingHolds(int olderThanHours) {
        long minimumHours = HoldPolicy.MIN_RECONCILIATION_THRESHOLD.toHours();
        if (olderThanHours < minimumHours) {
            throw new ThresholdTooLowException(olderThanHours, minimumHours);
        }

        Instant threshold = Instant.now(clock).minus(Duration.ofHours(olderThanHours));
        List<Hold> staleHolds = holdRepository.findPendingAuthorizedBefore(threshold, reconciliationBatchSize);

        List<Hold> expired = new ArrayList<>();
        for (Hold hold : staleHolds) {
            Hold released = hold.release(HoldStatus.EXPIRED, ReleaseReasons.STALE_RECONCILIATION, null, Instant.now(clock));
            Hold saved = holdRepository.save(released);
            eventPublisher.publish(HoldLifecycleEvent.of(HoldLifecycleEventType.HOLD_EXPIRED, saved, null));
            expired.add(saved);
        }
        return new ExpireStaleResult(expired);
    }

    private boolean isSameCreateRequest(Hold hold, CreateHoldRequest request) {
        return Objects.equals(hold.getAccountId(), request.accountId())
                && Objects.equals(0, hold.getAmount().compareTo(request.amount()))
                && Objects.equals(hold.getCurrency(), request.currency());
    }

    private HoldLifecycleEventType eventTypeFor(HoldStatus targetStatus) {
        return switch (targetStatus) {
            case MATCHED_IN_ABS -> HoldLifecycleEventType.HOLD_MATCHED;
            case REVERSED -> HoldLifecycleEventType.HOLD_REVERSED;
            case EXPIRED -> HoldLifecycleEventType.HOLD_EXPIRED;
            case PENDING -> throw new IllegalArgumentException("PENDING is not a terminal status");
        };
    }
}
